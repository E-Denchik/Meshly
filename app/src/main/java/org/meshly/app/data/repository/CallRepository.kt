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

package org.meshly.app.data.repository

import kotlinx.coroutines.flow.StateFlow
import org.meshly.app.core.ToxBridge
import org.meshly.app.data.model.CallSession
import org.meshly.app.data.model.CallType

class CallRepository(
    private val toxBridge: ToxBridge = ToxBridge.getInstance()
) {
    val activeCall: StateFlow<CallSession?> = toxBridge.activeCall

    fun placeCall(peerToxId: String, peerDisplayName: String, type: CallType): CallSession {
        return toxBridge.placeCall(peerToxId, peerDisplayName, type)
    }

    fun acceptCall(callId: String) {
        toxBridge.acceptCall(callId)
    }

    fun hangUpCall(callId: String) {
        toxBridge.hangUpCall(callId)
    }

    fun toggleMute(): Boolean = toxBridge.toggleMute()

    fun toggleCamera(): Boolean = toxBridge.toggleCamera()

    fun flipCamera(): Boolean = toxBridge.flipCamera()
}
