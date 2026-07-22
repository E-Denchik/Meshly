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

package org.meshly.app.daemon

import net.jami.daemon.StringMap
import net.jami.daemon.VectMap

/**
 * Raw libjami signals surfaced by [MeshlyCallCallback] / [MeshlyConfigurationCallback], in terms
 * of libjami's own vocabulary (accountId, not jamiId; string-encoded states, not enums).
 *
 * This is deliberately NOT [org.meshly.app.core.JamiEvent] from the :app module's Phase 1 mock —
 * :daemon doesn't depend on :app. Mapping RealJamiEvent -> JamiEvent (accountId -> jamiId lookup,
 * string state -> enum, etc.) is the next wiring step once this module is actually built; see
 * /PHASE2_BUILD.md "Wiring RealJamiBridge into the app" section.
 */
sealed class RealJamiEvent {
    data class RegistrationStateChanged(val accountId: String, val state: String, val code: Int) : RealJamiEvent()

    data class IncomingCall(
        val accountId: String,
        val callId: String,
        val from: String,
        val mediaList: VectMap
    ) : RealJamiEvent()

    data class CallStateChanged(
        val accountId: String,
        val callId: String,
        val state: String,
        val detailCode: Int
    ) : RealJamiEvent() {
        /** [state] parsed against the known `libjami::Call::StateEvent` values; see [RealCallState]. */
        val parsedState: RealCallState get() = RealCallState.fromWireValue(state)
    }

    /**
     * A call object was created daemon-side for an outgoing call (accepted/incoming calls are
     * signaled via [IncomingCall] instead). Maps to `Callback.newCall(accountId, callId, to)`.
     */
    data class NewCall(val accountId: String, val callId: String, val to: String) : RealJamiEvent()

    /**
     * The peer put the call on hold (or took it off hold). Maps to `Callback.peerHold(callId,
     * holding)` — note this signal carries only `callId`, no `accountId` (unlike almost every
     * other call signal), matching the real `Callback` interface exactly.
     */
    data class PeerHoldChanged(val callId: String, val holding: Boolean) : RealJamiEvent()

    /** Remote peer's mute state changed. Maps to `Callback.audioMuted(callId, muted)`. */
    data class RemoteAudioMutedChanged(val callId: String, val muted: Boolean) : RealJamiEvent()

    /** Remote peer's camera mute state changed. Maps to `Callback.videoMuted(callId, muted)`. */
    data class RemoteVideoMutedChanged(val callId: String, val muted: Boolean) : RealJamiEvent()

    /**
     * Result of ICE/media negotiation for a call. `event` is one of libjami's
     * `MediaNegotiationStatusEvents` (src/jami/media_const.h): `NEGOTIATION_SUCCESS` /
     * `NEGOTIATION_FAIL`. Maps to `Callback.mediaNegotiationStatus(callId, event, mediaList)`.
     */
    data class MediaNegotiationStatus(val callId: String, val event: String, val mediaList: VectMap) : RealJamiEvent()

    /**
     * The peer wants to change the call's media (e.g. escalate an audio call to video). Maps to
     * `Callback.mediaChangeRequested(accountId, callId, mediaList)` — answer it via
     * [RealJamiBridge.answerMediaChangeRequest].
     */
    data class MediaChangeRequested(val accountId: String, val callId: String, val mediaList: VectMap) : RealJamiEvent()

    data class IncomingCallMessage(
        val accountId: String,
        val callId: String,
        val from: String,
        val messages: StringMap
    ) : RealJamiEvent()

    data class IncomingAccountMessage(
        val accountId: String,
        val from: String,
        val messageId: String,
        val payload: StringMap
    ) : RealJamiEvent()

    data class AccountMessageStatusChanged(
        val accountId: String,
        val conversationId: String,
        val peer: String,
        val messageId: String,
        val state: Int
    ) : RealJamiEvent()

    data class IncomingTrustRequest(val accountId: String, val conversationId: String, val from: String) : RealJamiEvent()

    data class ContactAdded(val accountId: String, val uri: String, val confirmed: Boolean) : RealJamiEvent()

    data class ContactRemoved(val accountId: String, val uri: String, val banned: Boolean) : RealJamiEvent()

    /** Async result of [RealJamiBridge.registerName]. `state` is libjami's raw int result code. */
    data class NameRegistrationEnded(val accountId: String, val state: Int, val name: String) : RealJamiEvent()

    /** Peer typing indicator. `state` is 0/1 (not typing/typing) per `setIsComposing`'s bool param. */
    data class ComposingStatusChanged(
        val accountId: String,
        val conversationId: String,
        val from: String,
        val state: Int
    ) : RealJamiEvent()

    // --- Presence (PresenceCallback) -----------------------------------------------------------

    /**
     * A tracked buddy's presence changed. Maps to `PresenceCallback.newBuddyNotification(
     * accountId, buddyUri, status, lineStatus)` — `status` is [RealPresenceState], the signal
     * that actually matters for a Jami-only app (see [RealPresenceState]'s doc for why
     * `getSubscriptions`/[RealSubscription] is legacy-SIP plumbing by comparison).
     */
    data class BuddyPresenceChanged(
        val accountId: String,
        val buddyUri: String,
        val status: RealPresenceState,
        val lineStatus: String
    ) : RealJamiEvent()

    /** Maps to `PresenceCallback.subscriptionStateChanged(accountId, buddyUri, state)`. */
    data class SubscriptionStateChanged(val accountId: String, val buddyUri: String, val state: Int) : RealJamiEvent()

    /** SIP presence server pushed us a new subscription request. Legacy-SIP signal, see above. */
    data class NewServerSubscriptionRequest(val remote: String) : RealJamiEvent()

    /** SIP presence server error. Legacy-SIP signal, see [BuddyPresenceChanged]'s doc. */
    data class PresenceServerError(val accountId: String, val error: String, val message: String) : RealJamiEvent()

    /** Local-network (mDNS) peer discovery notification. */
    data class NearbyPeerNotification(
        val accountId: String,
        val buddyUri: String,
        val state: Int,
        val displayName: String
    ) : RealJamiEvent()

    // --- Data transfer (DataTransferCallback) --------------------------------------------------

    /**
     * File transfer progress/state change. Maps to `DataTransferCallback.dataTransferEvent(
     * accountId, conversationId, interactionId, fileId, eventCode)`; `eventCode` is
     * [RealDataTransferEventCode].
     */
    data class DataTransferEvent(
        val accountId: String,
        val conversationId: String,
        val interactionId: String,
        val fileId: String,
        val eventCode: RealDataTransferEventCode
    ) : RealJamiEvent()
}
