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
import net.jami.daemon.IntVect
import net.jami.daemon.NetworkServiceCallback
import net.jami.daemon.PresenceCallback
import net.jami.daemon.StringMap
import net.jami.daemon.SwarmMessageVect
import net.jami.daemon.UintVect
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

/**
 * Bridges libjami's [VideoCallback] into [RealJamiEvent] -- except `getCameraInfo`, which is
 * answered synchronously via [RealJamiBridge.cameraProvider] instead of the event flow (see
 * RealVideoDevice.kt's doc for why).
 *
 * `formats`/`sizes`/`rates` are assumed `IntVect`/`UintVect` (the `%template`s jni_interface.i
 * declares for `vector<int32_t>`/`vector<uint32_t>`), filled the same index/size/`add()` way as
 * `VectMap`/`StringVect` elsewhere in this codebase -- not confirmed against a real generated
 * build, since `getCameraInfo`'s director signature (`std::vector<int> *`, a raw pointer, not the
 * `int32_t`/`uint32_t` vector aliases used by those templates) is the one place in the whole API
 * surface this scaffolding pass couldn't cross-check as confidently as everything else.
 */
internal class MeshlyVideoCallback(
    private val events: MutableSharedFlow<RealJamiEvent>
) : VideoCallback() {

    override fun getCameraInfo(device: String, formats: IntVect, sizes: UintVect, rates: UintVect) {
        val info = RealJamiBridge.cameraProvider.getCameraInfo(device)
        info.formats.forEach { formats.add(it) }
        info.sizes.forEach { sizes.add(it) }
        info.rates.forEach { rates.add(it) }
    }

    override fun setParameters(device: String, format: Int, width: Int, height: Int, rate: Int) {
        events.tryEmit(RealJamiEvent.VideoSetParameters(device, format, width, height, rate))
    }

    override fun setBitrate(device: String, bitrate: Int) {
        events.tryEmit(RealJamiEvent.VideoSetBitrate(device, bitrate))
    }

    override fun requestKeyFrame(camid: String) {
        events.tryEmit(RealJamiEvent.VideoRequestKeyFrame(camid))
    }

    override fun startCapture(camid: String) {
        events.tryEmit(RealJamiEvent.VideoStartCapture(camid))
    }

    override fun stopCapture(camid: String) {
        events.tryEmit(RealJamiEvent.VideoStopCapture(camid))
    }

    override fun decodingStarted(id: String, shmPath: String, w: Int, h: Int, isMixer: Boolean) {
        events.tryEmit(RealJamiEvent.VideoDecodingStarted(id, shmPath, w, h, isMixer))
    }

    override fun decodingStopped(id: String, shmPath: String, isMixer: Boolean) {
        events.tryEmit(RealJamiEvent.VideoDecodingStopped(id, shmPath, isMixer))
    }
}

/**
 * Bridges libjami's [ConversationCallback] into [RealJamiEvent]. See RealConversation.kt's
 * top-level note: swarm conversations, not the account-message API, are very likely the real
 * path a 1:1 or group chat feature should use.
 */
internal class MeshlyConversationCallback(
    private val events: MutableSharedFlow<RealJamiEvent>
) : ConversationCallback() {

    override fun swarmLoaded(id: Int, accountId: String, conversationId: String, messages: SwarmMessageVect) {
        val mapped = (0 until messages.size()).map { RealSwarmMessage.fromSwarmMessage(messages[it]) }
        events.tryEmit(RealJamiEvent.SwarmLoaded(id, accountId, conversationId, mapped))
    }

    override fun messagesFound(id: Int, accountId: String, conversationId: String, messages: VectMap) {
        events.tryEmit(RealJamiEvent.MessagesFound(id, accountId, conversationId, messages))
    }

    override fun swarmMessageReceived(accountId: String, conversationId: String, message: net.jami.daemon.SwarmMessage) {
        events.tryEmit(
            RealJamiEvent.SwarmMessageReceived(accountId, conversationId, RealSwarmMessage.fromSwarmMessage(message))
        )
    }

    override fun swarmMessageUpdated(accountId: String, conversationId: String, message: net.jami.daemon.SwarmMessage) {
        events.tryEmit(
            RealJamiEvent.SwarmMessageUpdated(accountId, conversationId, RealSwarmMessage.fromSwarmMessage(message))
        )
    }

    override fun reactionAdded(accountId: String, conversationId: String, messageId: String, reaction: StringMap) {
        events.tryEmit(
            RealJamiEvent.ReactionAdded(accountId, conversationId, messageId, reaction.entries.associate { it.key to it.value })
        )
    }

    override fun reactionRemoved(accountId: String, conversationId: String, messageId: String, reactionId: String) {
        events.tryEmit(RealJamiEvent.ReactionRemoved(accountId, conversationId, messageId, reactionId))
    }

    override fun conversationProfileUpdated(accountId: String, conversationId: String, profile: StringMap) {
        events.tryEmit(
            RealJamiEvent.ConversationProfileUpdated(accountId, conversationId, profile.entries.associate { it.key to it.value })
        )
    }

    override fun conversationRequestReceived(accountId: String, conversationId: String, metadatas: StringMap) {
        events.tryEmit(
            RealJamiEvent.ConversationRequestReceived(
                accountId,
                conversationId,
                metadatas.entries.associate { it.key to it.value }
            )
        )
    }

    override fun conversationRequestDeclined(accountId: String, conversationId: String) {
        events.tryEmit(RealJamiEvent.ConversationRequestDeclined(accountId, conversationId))
    }

    override fun conversationReady(accountId: String, conversationId: String) {
        events.tryEmit(RealJamiEvent.ConversationReady(accountId, conversationId))
    }

    override fun conversationRemoved(accountId: String, conversationId: String) {
        events.tryEmit(RealJamiEvent.ConversationRemoved(accountId, conversationId))
    }

    override fun conversationMemberEvent(accountId: String, conversationId: String, memberUri: String, event: Int) {
        events.tryEmit(
            RealJamiEvent.ConversationMemberEvent(
                accountId,
                conversationId,
                memberUri,
                RealConversationMemberEvent.fromWireValue(event)
            )
        )
    }

    override fun onConversationError(accountId: String, conversationId: String, code: Int, what: String) {
        events.tryEmit(RealJamiEvent.ConversationError(accountId, conversationId, code, what))
    }

    override fun conversationPreferencesUpdated(accountId: String, conversationId: String, preferences: StringMap) {
        events.tryEmit(
            RealJamiEvent.ConversationPreferencesUpdated(
                accountId,
                conversationId,
                preferences.entries.associate { it.key to it.value }
            )
        )
    }
}

/**
 * Bridges libjami's [NetworkServiceCallback] into [RealJamiEvent]. Niche/experimental
 * peer-service-tunnel feature -- see RealJamiEvent.kt's doc on these three events.
 */
internal class MeshlyNetworkServiceCallback(
    private val events: MutableSharedFlow<RealJamiEvent>
) : NetworkServiceCallback() {

    // `requestId` is `uint32_t` and `localPort` is `uint16_t` on the C++ side; no explicit `%apply`
    // override for either exists in jni_interface.i (unlike uint64_t/time_t), so both are assumed
    // to fall back to SWIG's un-overridden defaults (`int` for unsigned int, `short` for unsigned
    // short) here as `Int` -- not confirmed against a real generated build, and `localPort` in
    // particular could plausibly come out as `Short` instead.
    override fun peerServicesReceived(
        requestId: Int,
        accountId: String,
        peerId: String,
        status: Int,
        servicesJson: String
    ) {
        events.tryEmit(RealJamiEvent.PeerServicesReceived(requestId, accountId, peerId, status, servicesJson))
    }

    override fun serviceTunnelOpened(accountId: String, tunnelId: String, localPort: Int) {
        events.tryEmit(RealJamiEvent.ServiceTunnelOpened(accountId, tunnelId, localPort))
    }

    override fun serviceTunnelClosed(accountId: String, tunnelId: String, reason: String) {
        events.tryEmit(RealJamiEvent.ServiceTunnelClosed(accountId, tunnelId, reason))
    }
}
