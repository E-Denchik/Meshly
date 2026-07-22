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

    // NOTE: `received` is `time_t` on the C++ side. SWIG's default typemap for time_t
    // hasn't been confirmed against a real generated build yet (likely `long`, matches
    // here) — verify this signature against the SWIG-generated ConfigurationCallback.java
    // once bin/jni/make-swig.sh has actually been run (see /PHASE2_BUILD.md).
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
}

// The remaining four director interfaces (Presence, DataTransfer, Video, Conversation,
// NetworkService) are required arguments to JamiService.init(...) but Meshly doesn't
// consume their signals yet. Empty subclasses are enough since every method in the
// upstream .i files has a default no-op body.
internal class MeshlyPresenceCallback : PresenceCallback()
internal class MeshlyDataTransferCallback : DataTransferCallback()
internal class MeshlyVideoCallback : VideoCallback()
internal class MeshlyConversationCallback : ConversationCallback()
internal class MeshlyNetworkServiceCallback : NetworkServiceCallback()
