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

// NOTE: This file only compiles once the :daemon module has actually been
// built for real (see /PHASE2_BUILD.md) — that native build is what generates
// net.jami.daemon.JamiServiceJNI and the Callback/ConfigurationCallback/etc.
// director base classes these adapters extend. Until then this file is dead
// code: the module isn't in settings.gradle.kts, so nothing tries to compile it.
//
// Callback method names/signatures below are copied verbatim from the real
// interface definitions in native/upstream/jami-daemon/bin/jni/{callmanager,
// configurationmanager}.i — not guessed.

import kotlinx.coroutines.flow.MutableSharedFlow
import net.jami.daemon.Blob
import net.jami.daemon.Callback
import net.jami.daemon.ConfigurationCallback
import net.jami.daemon.ConversationCallback
import net.jami.daemon.DataTransferCallback
import net.jami.daemon.NetworkServiceCallback
import net.jami.daemon.PresenceCallback
import net.jami.daemon.StringMap
import net.jami.daemon.VectMap
import net.jami.daemon.VideoCallback

/**
 * Bridges libjami's [Callback] (call/message signals) director callbacks into [RealJamiEvent].
 */
internal class MeshlyCallCallback(
    private val events: MutableSharedFlow<RealJamiEvent>
) : Callback() {

    override fun incomingCall(
        accountId: String,
        callId: String,
        from: String,
        mediaList: VectMap
    ) {
        events.tryEmit(RealJamiEvent.IncomingCall(accountId, callId, from, mediaList))
    }

    override fun callStateChanged(accountId: String, callId: String, state: String, detailCode: Int) {
        events.tryEmit(RealJamiEvent.CallStateChanged(accountId, callId, state, detailCode))
    }

    override fun incomingMessage(
        accountId: String,
        callId: String,
        from: String,
        messages: StringMap
    ) {
        events.tryEmit(RealJamiEvent.IncomingCallMessage(accountId, callId, from, messages))
    }

    override fun newCall(accountId: String, callId: String, to: String) {
        events.tryEmit(RealJamiEvent.NewCall(accountId, callId, to))
    }

    // NOTE: real Callback signature, only callId — no accountId. Not a typo here.
    override fun peerHold(callId: String, holding: Boolean) {
        events.tryEmit(RealJamiEvent.PeerHoldChanged(callId, holding))
    }

    override fun audioMuted(callId: String, muted: Boolean) {
        events.tryEmit(RealJamiEvent.RemoteAudioMutedChanged(callId, muted))
    }

    override fun videoMuted(callId: String, muted: Boolean) {
        events.tryEmit(RealJamiEvent.RemoteVideoMutedChanged(callId, muted))
    }

    override fun mediaNegotiationStatus(callId: String, event: String, mediaList: VectMap) {
        events.tryEmit(RealJamiEvent.MediaNegotiationStatus(callId, event, mediaList))
    }

    override fun mediaChangeRequested(accountId: String, callId: String, mediaList: VectMap) {
        events.tryEmit(RealJamiEvent.MediaChangeRequested(accountId, callId, mediaList))
    }
}

/**
 * Bridges libjami's [ConfigurationCallback] (account/contact/message signals) into [RealJamiEvent].
 */
internal class MeshlyConfigurationCallback(
    private val events: MutableSharedFlow<RealJamiEvent>
) : ConfigurationCallback() {

    override fun registrationStateChanged(accountId: String, state: String, code: Int, detailStr: String) {
        events.tryEmit(RealJamiEvent.RegistrationStateChanged(accountId, state, code))
    }

    override fun incomingAccountMessage(
        accountId: String,
        from: String,
        messageId: String,
        payload: StringMap
    ) {
        events.tryEmit(RealJamiEvent.IncomingAccountMessage(accountId, from, messageId, payload))
    }

    override fun accountMessageStatusChanged(
        accountId: String,
        conversationId: String,
        peer: String,
        messageId: String,
        state: Int
    ) {
        events.tryEmit(RealJamiEvent.AccountMessageStatusChanged(accountId, conversationId, peer, messageId, state))
    }

    // `received` is `time_t` on the C++ side, confirmed as `long` here: jni_interface.i has
    // `%apply uint64_t { time_t };` followed by `%apply int64_t { uint64_t };`, and SWIG's
    // default Java mapping for int64_t is `long`.
    override fun incomingTrustRequest(
        accountId: String,
        conversationId: String,
        from: String,
        payload: Blob,
        received: Long
    ) {
        events.tryEmit(RealJamiEvent.IncomingTrustRequest(accountId, conversationId, from))
    }

    override fun contactAdded(accountId: String, uri: String, confirmed: Boolean) {
        events.tryEmit(RealJamiEvent.ContactAdded(accountId, uri, confirmed))
    }

    override fun contactRemoved(accountId: String, uri: String, banned: Boolean) {
        events.tryEmit(RealJamiEvent.ContactRemoved(accountId, uri, banned))
    }

    override fun nameRegistrationEnded(accountId: String, state: Int, name: String) {
        events.tryEmit(RealJamiEvent.NameRegistrationEnded(accountId, state, name))
    }

    override fun composingStatusChanged(accountId: String, convId: String, from: String, state: Int) {
        events.tryEmit(RealJamiEvent.ComposingStatusChanged(accountId, convId, from, state))
    }
}

/**
 * Bridges libjami's [PresenceCallback] into [RealJamiEvent]. See [RealPresenceState]'s doc for
 * why `newBuddyNotification` is the signal that matters here, not the legacy SIP-presence ones.
 */
internal class MeshlyPresenceCallback(
    private val events: MutableSharedFlow<RealJamiEvent>
) : PresenceCallback() {

    override fun newBuddyNotification(accountId: String, buddyUri: String, status: Int, lineStatus: String) {
        events.tryEmit(
            RealJamiEvent.BuddyPresenceChanged(accountId, buddyUri, RealPresenceState.fromWireValue(status), lineStatus)
        )
    }

    override fun subscriptionStateChanged(accountId: String, buddyUri: String, state: Int) {
        events.tryEmit(RealJamiEvent.SubscriptionStateChanged(accountId, buddyUri, state))
    }

    override fun newServerSubscriptionRequest(remote: String) {
        events.tryEmit(RealJamiEvent.NewServerSubscriptionRequest(remote))
    }

    override fun serverError(accountId: String, error: String, msg: String) {
        events.tryEmit(RealJamiEvent.PresenceServerError(accountId, error, msg))
    }

    override fun nearbyPeerNotification(accountId: String, buddyUri: String, state: Int, displayname: String) {
        events.tryEmit(RealJamiEvent.NearbyPeerNotification(accountId, buddyUri, state, displayname))
    }
}

/** Bridges libjami's [DataTransferCallback] into [RealJamiEvent]. */
internal class MeshlyDataTransferCallback(
    private val events: MutableSharedFlow<RealJamiEvent>
) : DataTransferCallback() {

    override fun dataTransferEvent(
        accountId: String,
        conversationId: String,
        interactionId: String,
        fileId: String,
        eventCode: Int
    ) {
        events.tryEmit(
            RealJamiEvent.DataTransferEvent(
                accountId,
                conversationId,
                interactionId,
                fileId,
                RealDataTransferEventCode.fromWireValue(eventCode)
            )
        )
    }
}

// The remaining three director interfaces (Video, Conversation, NetworkService) are required
// arguments to JamiService.init(...) but Meshly doesn't consume their signals yet. Empty
// subclasses are enough since every method in the upstream .i files has a default no-op body.
internal class MeshlyVideoCallback : VideoCallback()
internal class MeshlyConversationCallback : ConversationCallback()
internal class MeshlyNetworkServiceCallback : NetworkServiceCallback()
