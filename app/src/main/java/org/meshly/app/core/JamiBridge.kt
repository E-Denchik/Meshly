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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
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
import kotlin.random.Random

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

/**
 * Stage 1's mock/stub engine. It never calls into native libjami (Phase 2), but it does simulate
 * a live peer on the other end of every confirmed contact: presence flicker, delivery/read
 * receipts, occasional auto-replies and incoming calls. That simulation is what makes the app
 * feel bidirectional in the UI instead of "everything you do into a void" - see README's Phase 1
 * status note. None of this pretends to be real networking; it's just enough state-machine
 * behavior to exercise every screen end-to-end.
 */
class JamiBridge private constructor() {

    private val scope = CoroutineScope(Dispatchers.Default)

    private val _events = MutableSharedFlow<JamiEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<JamiEvent> = _events.asSharedFlow()

    private val _currentAccount = MutableStateFlow<Account?>(null)
    val currentAccount: StateFlow<Account?> = _currentAccount.asStateFlow()

    private val _activeCall = MutableStateFlow<CallSession?>(null)
    val activeCall: StateFlow<CallSession?> = _activeCall.asStateFlow()

    private var isNativeLoaded = false

    /** Peers currently simulated as "live" (presence flicker, occasional incoming calls). */
    private val presenceJobs = mutableMapOf<String, Job>()

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

    fun updateAccountSettings(upnpEnabled: Boolean? = null, turnEnabled: Boolean? = null) {
        val current = _currentAccount.value ?: return
        _currentAccount.value = current.copy(
            upnpEnabled = upnpEnabled ?: current.upnpEnabled,
            turnEnabled = turnEnabled ?: current.turnEnabled
        )
    }

    fun updateBootstrapNodes(nodes: List<String>) {
        val current = _currentAccount.value ?: return
        _currentAccount.value = current.copy(bootstrapNodes = nodes)
    }

    /** Logs out of the current identity: stops all peer presence simulation and drops the
     *  in-memory account, so the app falls back to onboarding. Does not touch on-disk state -
     *  that's [org.meshly.app.data.repository.AccountRepository.logout]'s job. */
    fun logout() {
        presenceJobs.values.forEach { it.cancel() }
        presenceJobs.clear()
        _activeCall.value = null
        _currentAccount.value = null
    }

    fun sendTextMessage(
        messageId: String,
        toJamiId: String,
        text: String,
        attachmentPath: String? = null
    ): ChatMessage {
        val myJamiId = _currentAccount.value?.jamiId ?: "local_me"
        val message = ChatMessage(
            id = messageId,
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
        } else {
            simulateMessageDeliveryAndReply(messageId, toJamiId)
        }
        return message
    }

    /** Simulates the peer's device receiving, then reading, the message - and sometimes replying. */
    private fun simulateMessageDeliveryAndReply(messageId: String, peerJamiId: String) {
        scope.launch {
            delay(Random.nextLong(400, 1_200))
            _events.emit(JamiEvent.MessageStateChanged(messageId, MessageStatus.DELIVERED))

            if (Random.nextFloat() < 0.75f) {
                delay(Random.nextLong(1_000, 3_000))
                _events.emit(JamiEvent.MessageStateChanged(messageId, MessageStatus.READ))

                delay(Random.nextLong(800, 2_500))
                val reply = ChatMessage(
                    id = UUID.randomUUID().toString(),
                    conversationId = peerJamiId,
                    senderJamiId = peerJamiId,
                    text = AUTO_REPLIES.random(),
                    timestamp = System.currentTimeMillis(),
                    status = MessageStatus.DELIVERED,
                    isIncoming = true
                )
                _events.emit(JamiEvent.MessageReceived(reply))
            }
        }
    }

    /** Sends an outgoing contact request; simulates the peer accepting it a few seconds later. */
    fun addContactRequest(peerJamiId: String, displayName: String) {
        scope.launch {
            delay(Random.nextLong(2_500, 6_000))
            confirmContact(peerJamiId, displayName)
        }
    }

    /** Simulates an incoming friend request arriving from a peer, for the Requests tab to handle. */
    fun simulateIncomingContactRequest(peerJamiId: String, displayName: String) {
        scope.launch {
            val contact = Contact(
                jamiId = peerJamiId,
                displayName = displayName,
                status = ContactStatus.PENDING_INCOMING,
                presence = PresenceStatus.ONLINE
            )
            _events.emit(JamiEvent.ContactRequestReceived(contact))
        }
    }

    /** Marks a contact confirmed (peer accepted us, or we accepted them) and starts presence simulation. */
    fun confirmContact(peerJamiId: String, displayName: String) {
        scope.launch {
            _events.emit(JamiEvent.ContactStatusChanged(peerJamiId, ContactStatus.CONFIRMED))
        }
        startPresenceSimulation(peerJamiId, displayName)
    }

    /** Stops simulating a peer as live once they're no longer a contact. */
    fun forgetPeer(peerJamiId: String) {
        presenceJobs.remove(peerJamiId)?.cancel()
    }

    private fun startPresenceSimulation(peerJamiId: String, displayName: String) {
        presenceJobs[peerJamiId]?.cancel()
        presenceJobs[peerJamiId] = scope.launch {
            while (isActive) {
                _events.emit(JamiEvent.PresenceChanged(peerJamiId, PresenceStatus.ONLINE))

                if (_activeCall.value == null && Random.nextFloat() < 0.12f) {
                    delay(Random.nextLong(2_000, 6_000))
                    simulateIncomingCall(peerJamiId, displayName)
                }

                delay(Random.nextLong(15_000, 30_000))
                _events.emit(JamiEvent.PresenceChanged(peerJamiId, PresenceStatus.OFFLINE))
                delay(Random.nextLong(20_000, 45_000))
            }
        }
    }

    private fun simulateIncomingCall(peerJamiId: String, peerDisplayName: String) {
        if (_activeCall.value != null) return
        val callId = UUID.randomUUID().toString()
        val session = CallSession(
            callId = callId,
            peerJamiId = peerJamiId,
            peerDisplayName = peerDisplayName,
            callType = if (Random.nextBoolean()) CallType.VIDEO else CallType.AUDIO,
            state = CallState.INCOMING,
            startTime = System.currentTimeMillis()
        )
        _activeCall.value = session
        scope.launch {
            _events.emit(JamiEvent.IncomingCall(session))
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

    /** FR-4.5 (optional): mock toggle only - real screen sharing needs a MediaProjection capture
     *  session encoded into the daemon's outgoing media, which doesn't exist without libjami. */
    fun toggleScreenShare(): Boolean {
        val current = _activeCall.value ?: return false
        val newSharing = !current.isScreenSharing
        _activeCall.value = current.copy(isScreenSharing = newSharing)
        return newSharing
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

        private val AUTO_REPLIES = listOf(
            "Got it, thanks!",
            "Sounds good.",
            "Let me get back to you on that.",
            "👍",
            "Interesting, tell me more.",
            "On it.",
            "Can we talk later today?",
            "Received, thank you."
        )
    }
}
