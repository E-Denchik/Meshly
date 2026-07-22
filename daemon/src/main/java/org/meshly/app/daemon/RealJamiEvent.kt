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
    ) : RealJamiEvent()

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
}
