/*
 * Copyright (C) 2026 The Meshly Project Authors
 *
 * This file is part of Meshly, a decentralized peer-to-peer messenger
 * built on top of GNU Jami's core engine (libjami).
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package org.meshly.app.core

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.meshly.app.data.model.Account
import org.meshly.app.data.model.CallSession
import org.meshly.app.data.model.CallState
import org.meshly.app.data.model.CallType
import org.meshly.app.data.model.ChatMessage
import org.meshly.app.data.model.Contact
import org.meshly.app.data.model.ContactStatus
import org.meshly.app.data.model.MessageStatus
import org.meshly.app.data.model.PresenceStatus
import java.util.UUID

sealed class JamiEvent {
    data class AccountCreated(val account: Account) : JamiEvent()
    data class AccountLoaded(val account: Account) : JamiEvent()
    data class ContactRequestReceived(val contact: Contact) : JamiEvent()
    data class ContactStatusChanged(val jamiId: String, val status: ContactStatus) : JamiEvent()
    data class PresenceChanged(val jamiId: String, val presence: PresenceStatus) : JamiEvent()
    data class MessageReceived(val message: ChatMessage) : JamiEvent()
    data class MessageStateChanged(val messageId: String, val status: MessageStatus) : JamiEvent()
    data class IncomingCall(val session: CallSession) : JamiEvent()
    data class CallStateChanged(val callId: String, val state: CallState) : JamiEvent()
}

class JamiBridge private constructor() {

    private val scope = CoroutineScope(Dispatchers.Default)

    private val _events = MutableSharedFlow<JamiEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<JamiEvent> = _events.asSharedFlow()

    private val _currentAccount = MutableStateFlow<Account?>(null)
    val currentAccount: StateFlow<Account?> = _currentAccount.asStateFlow()

    private val _activeCall = MutableStateFlow<CallSession?>(null)
    val activeCall: StateFlow<CallSession?> = _activeCall.asStateFlow()

    private var isNativeLoaded = false

    init {
        try {
            System.loadLibrary("jami")
            isNativeLoaded = true
        } catch (e: UnsatisfiedLinkError) {
            isNativeLoaded = false
        }
    }

    fun isNativeEngineAvailable(): Boolean = isNativeLoaded

    fun startDaemon() {
        if (isNativeLoaded) {
            nativeStartDaemon()
        }
    }

    fun createAccount(username: String? = null): Account {
        val generatedJamiId = "jami:" + UUID.randomUUID().toString().replace("-", "").take(40)
        val account = Account(
            jamiId = generatedJamiId,
            username = username,
            isRegisteredOnNameServer = !username.isNullOrEmpty()
        )
        _currentAccount.value = account
        scope.launch {
            _events.emit(JamiEvent.AccountCreated(account))
        }
        return account
    }

    fun restoreAccount(account: Account) {
        _currentAccount.value = account
        scope.launch {
            _events.emit(JamiEvent.AccountLoaded(account))
        }
    }

    fun sendTextMessage(toJamiId: String, text: String, attachmentPath: String? = null): ChatMessage {
        val myJamiId = _currentAccount.value?.jamiId ?: "local_me"
        val message = ChatMessage(
            id = UUID.randomUUID().toString(),
            conversationId = toJamiId,
            senderJamiId = myJamiId,
            text = text,
            timestamp = System.currentTimeMillis(),
            status = MessageStatus.SENT,
            attachmentPath = attachmentPath,
            isIncoming = false
        )
        if (isNativeLoaded) {
            nativeSendMessage(toJamiId, text)
        }
        return message
    }

    fun addContactRequest(peerJamiId: String, displayName: String) {
        val contact = Contact(
            jamiId = peerJamiId,
            displayName = displayName,
            status = ContactStatus.PENDING_OUTGOING,
            presence = PresenceStatus.UNKNOWN
        )
        scope.launch {
            _events.emit(JamiEvent.ContactRequestReceived(contact))
        }
    }

    fun placeCall(peerJamiId: String, peerDisplayName: String, type: CallType): CallSession {
        val callId = UUID.randomUUID().toString()
        val session = CallSession(
            callId = callId,
            peerJamiId = peerJamiId,
            peerDisplayName = peerDisplayName,
            callType = type,
            state = CallState.DIALING,
            startTime = System.currentTimeMillis()
        )
        _activeCall.value = session
        scope.launch {
            _events.emit(JamiEvent.CallStateChanged(callId, CallState.DIALING))
        }
        return session
    }

    fun acceptCall(callId: String) {
        val current = _activeCall.value ?: return
        val updated = current.copy(state = CallState.CONNECTED)
        _activeCall.value = updated
        scope.launch {
            _events.emit(JamiEvent.CallStateChanged(callId, CallState.CONNECTED))
        }
    }

    fun hangUpCall(callId: String) {
        val current = _activeCall.value ?: return
        val updated = current.copy(state = CallState.ENDED)
        _activeCall.value = updated
        scope.launch {
            _events.emit(JamiEvent.CallStateChanged(callId, CallState.ENDED))
        }
        _activeCall.value = null
    }

    fun toggleMute(): Boolean {
        val current = _activeCall.value ?: return false
        val newMute = !current.isMuted
        _activeCall.value = current.copy(isMuted = newMute)
        return newMute
    }

    fun toggleCamera(): Boolean {
        val current = _activeCall.value ?: return false
        val newCamera = !current.isCameraOn
        _activeCall.value = current.copy(isCameraOn = newCamera)
        return newCamera
    }

    fun flipCamera(): Boolean {
        val current = _activeCall.value ?: return false
        val newFront = !current.isFrontCamera
        _activeCall.value = current.copy(isFrontCamera = newFront)
        return newFront
    }

    // Native JNI Declarations
    private external fun nativeStartDaemon()
    private external fun nativeSendMessage(toJamiId: String, text: String)

    companion object {
        @Volatile
        private var instance: JamiBridge? = null

        fun getInstance(): JamiBridge {
            return instance ?: synchronized(this) {
                instance ?: JamiBridge().also { instance = it }
            }
        }
    }
}
