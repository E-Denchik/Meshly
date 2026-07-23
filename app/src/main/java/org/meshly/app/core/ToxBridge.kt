/*
 * Copyright (C) 2026 The Meshly Project Authors
 *
 * This file is part of Meshly, a decentralized peer-to-peer messenger
 * built on top of Tox (c-toxcore + ToxAV).
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

sealed class ToxEvent {
    data class AccountCreated(val account: Account) : ToxEvent()
    data class AccountLoaded(val account: Account) : ToxEvent()
    data class ContactRequestReceived(val contact: Contact) : ToxEvent()
    data class ContactStatusChanged(val toxId: String, val status: ContactStatus) : ToxEvent()
    data class PresenceChanged(val toxId: String, val presence: PresenceStatus) : ToxEvent()
    data class MessageReceived(val message: ChatMessage) : ToxEvent()
    data class MessageStateChanged(val messageId: String, val status: MessageStatus) : ToxEvent()
    data class IncomingCall(val session: CallSession) : ToxEvent()
    data class CallStateChanged(val callId: String, val state: CallState) : ToxEvent()
}

/**
 * Stage 1's mock/stub engine. It never calls into native c-toxcore/ToxAV (Phase 2), but it does
 * simulate a live peer on the other end of every confirmed contact: presence flicker,
 * delivery-state changes, occasional auto-replies and incoming calls. That simulation is what
 * makes the app feel bidirectional in the UI instead of "everything you do into a void" - see
 * README's Phase 1 status note. None of this pretends to be real networking; it's just enough
 * state-machine behavior to exercise every screen end-to-end.
 *
 * Unlike Jami's OpenDHT-backed store-and-forward delivery, plain Tox has no offline message
 * queue: both peers must be online simultaneously for a message to succeed. This bridge honors
 * that constraint by only attempting delivery simulation while the target peer's simulated
 * presence is ONLINE; see [sendTextMessage].
 */
class ToxBridge private constructor() {

    private val scope = CoroutineScope(Dispatchers.Default)

    private val _events = MutableSharedFlow<ToxEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<ToxEvent> = _events.asSharedFlow()

    private val _currentAccount = MutableStateFlow<Account?>(null)
    val currentAccount: StateFlow<Account?> = _currentAccount.asStateFlow()

    private val _activeCall = MutableStateFlow<CallSession?>(null)
    val activeCall: StateFlow<CallSession?> = _activeCall.asStateFlow()

    private var isNativeLoaded = false

    /** Peers currently simulated as "live" (presence flicker, occasional incoming calls). */
    private val presenceJobs = mutableMapOf<String, Job>()

    /** Latest simulated presence per peer, so [sendTextMessage] can decide DELIVERED vs FAILED. */
    private val simulatedPresence = mutableMapOf<String, PresenceStatus>()

    init {
        try {
            System.loadLibrary("toxcore")
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

    fun createAccount(nickname: String? = null): Account {
        val account = Account(
            toxId = generateToxId(),
            nickname = nickname
        )
        _currentAccount.value = account
        scope.launch {
            _events.emit(ToxEvent.AccountCreated(account))
        }
        return account
    }

    fun restoreAccount(account: Account) {
        _currentAccount.value = account
        scope.launch {
            _events.emit(ToxEvent.AccountLoaded(account))
        }
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
        simulatedPresence.clear()
        _activeCall.value = null
        _currentAccount.value = null
    }

    fun sendTextMessage(
        messageId: String,
        toToxId: String,
        text: String,
        attachmentPath: String? = null
    ): ChatMessage {
        val myToxId = _currentAccount.value?.toxId ?: "local_me"
        val message = ChatMessage(
            id = messageId,
            conversationId = toToxId,
            senderToxId = myToxId,
            text = text,
            timestamp = System.currentTimeMillis(),
            status = MessageStatus.SENT,
            attachmentPath = attachmentPath,
            isIncoming = false
        )
        if (isNativeLoaded) {
            nativeSendMessage(toToxId, text)
        } else {
            simulateMessageDelivery(messageId, toToxId)
        }
        return message
    }

    /**
     * Simulates the peer's device receiving the message, and sometimes replying. Plain Tox has
     * no offline message queue, so this only progresses to DELIVERED when the peer's simulated
     * presence is currently ONLINE; otherwise the send fails immediately, with no queued/delayed
     * retry.
     */
    private fun simulateMessageDelivery(messageId: String, peerToxId: String) {
        if (simulatedPresence[peerToxId] != PresenceStatus.ONLINE) {
            scope.launch {
                _events.emit(ToxEvent.MessageStateChanged(messageId, MessageStatus.FAILED))
            }
            return
        }
        scope.launch {
            delay(Random.nextLong(400, 1_200))
            _events.emit(ToxEvent.MessageStateChanged(messageId, MessageStatus.DELIVERED))

            if (Random.nextFloat() < 0.75f) {
                delay(Random.nextLong(1_000, 3_000))
                val reply = ChatMessage(
                    id = UUID.randomUUID().toString(),
                    conversationId = peerToxId,
                    senderToxId = peerToxId,
                    text = AUTO_REPLIES.random(),
                    timestamp = System.currentTimeMillis(),
                    status = MessageStatus.DELIVERED,
                    isIncoming = true
                )
                _events.emit(ToxEvent.MessageReceived(reply))
            }
        }
    }

    /** Sends an outgoing contact request; simulates the peer accepting it a few seconds later. */
    fun addContactRequest(peerToxId: String, displayName: String, requestMessage: String? = null) {
        scope.launch {
            delay(Random.nextLong(2_500, 6_000))
            confirmContact(peerToxId, displayName)
        }
    }

    /** Simulates an incoming friend request arriving from a peer, for the Requests tab to handle. */
    fun simulateIncomingContactRequest(peerToxId: String, displayName: String) {
        scope.launch {
            val contact = Contact(
                toxId = peerToxId,
                displayName = displayName,
                status = ContactStatus.PENDING_INCOMING,
                presence = PresenceStatus.ONLINE
            )
            _events.emit(ToxEvent.ContactRequestReceived(contact))
        }
    }

    /** Marks a contact confirmed (peer accepted us, or we accepted them) and starts presence simulation. */
    fun confirmContact(peerToxId: String, displayName: String) {
        scope.launch {
            _events.emit(ToxEvent.ContactStatusChanged(peerToxId, ContactStatus.CONFIRMED))
        }
        startPresenceSimulation(peerToxId, displayName)
    }

    /** Stops simulating a peer as live once they're no longer a contact. */
    fun forgetPeer(peerToxId: String) {
        presenceJobs.remove(peerToxId)?.cancel()
        simulatedPresence.remove(peerToxId)
    }

    private fun startPresenceSimulation(peerToxId: String, displayName: String) {
        presenceJobs[peerToxId]?.cancel()
        presenceJobs[peerToxId] = scope.launch {
            while (isActive) {
                simulatedPresence[peerToxId] = PresenceStatus.ONLINE
                _events.emit(ToxEvent.PresenceChanged(peerToxId, PresenceStatus.ONLINE))

                if (_activeCall.value == null && Random.nextFloat() < 0.12f) {
                    delay(Random.nextLong(2_000, 6_000))
                    simulateIncomingCall(peerToxId, displayName)
                }

                delay(Random.nextLong(15_000, 30_000))
                simulatedPresence[peerToxId] = PresenceStatus.OFFLINE
                _events.emit(ToxEvent.PresenceChanged(peerToxId, PresenceStatus.OFFLINE))
                delay(Random.nextLong(20_000, 45_000))
            }
        }
    }

    private fun simulateIncomingCall(peerToxId: String, peerDisplayName: String) {
        if (_activeCall.value != null) return
        val callId = UUID.randomUUID().toString()
        val session = CallSession(
            callId = callId,
            peerToxId = peerToxId,
            peerDisplayName = peerDisplayName,
            callType = if (Random.nextBoolean()) CallType.VIDEO else CallType.AUDIO,
            state = CallState.INCOMING,
            startTime = System.currentTimeMillis()
        )
        _activeCall.value = session
        scope.launch {
            _events.emit(ToxEvent.IncomingCall(session))
        }
    }

    fun placeCall(peerToxId: String, peerDisplayName: String, type: CallType): CallSession {
        val callId = UUID.randomUUID().toString()
        val session = CallSession(
            callId = callId,
            peerToxId = peerToxId,
            peerDisplayName = peerDisplayName,
            callType = type,
            state = CallState.DIALING,
            startTime = System.currentTimeMillis()
        )
        _activeCall.value = session
        scope.launch {
            _events.emit(ToxEvent.CallStateChanged(callId, CallState.DIALING))
        }
        return session
    }

    fun acceptCall(callId: String) {
        val current = _activeCall.value ?: return
        val updated = current.copy(state = CallState.CONNECTED)
        _activeCall.value = updated
        scope.launch {
            _events.emit(ToxEvent.CallStateChanged(callId, CallState.CONNECTED))
        }
    }

    fun hangUpCall(callId: String) {
        val current = _activeCall.value ?: return
        val updated = current.copy(state = CallState.ENDED)
        _activeCall.value = updated
        scope.launch {
            _events.emit(ToxEvent.CallStateChanged(callId, CallState.ENDED))
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

    /** Generates a plausible 76-hex-char Tox ID (32-byte public key + 4-byte nospam + 2-byte
     *  checksum, hex-encoded). Not a real keypair - just enough entropy/shape to exercise the
     *  UI and storage layers until Phase 2 wires up real c-toxcore key generation. */
    private fun generateToxId(): String {
        val hex = buildString {
            repeat(3) { append(UUID.randomUUID().toString().replace("-", "")) }
        }
        return hex.take(76)
    }

    // Native JNI Declarations
    private external fun nativeStartDaemon()
    private external fun nativeSendMessage(toToxId: String, text: String)

    companion object {
        @Volatile
        private var instance: ToxBridge? = null

        fun getInstance(): ToxBridge {
            return instance ?: synchronized(this) {
                instance ?: ToxBridge().also { instance = it }
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
