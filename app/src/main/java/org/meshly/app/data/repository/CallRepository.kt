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

package org.meshly.app.data.repository

import kotlinx.coroutines.flow.StateFlow
import org.meshly.app.core.JamiBridge
import org.meshly.app.data.model.CallSession
import org.meshly.app.data.model.CallType

class CallRepository(
    private val jamiBridge: JamiBridge = JamiBridge.getInstance()
) {
    val activeCall: StateFlow<CallSession?> = jamiBridge.activeCall

    fun placeCall(peerJamiId: String, peerDisplayName: String, type: CallType): CallSession {
        return jamiBridge.placeCall(peerJamiId, peerDisplayName, type)
    }

    fun acceptCall(callId: String) {
        jamiBridge.acceptCall(callId)
    }

    fun hangUpCall(callId: String) {
        jamiBridge.hangUpCall(callId)
    }

    fun toggleMute(): Boolean = jamiBridge.toggleMute()

    fun toggleCamera(): Boolean = jamiBridge.toggleCamera()

    fun flipCamera(): Boolean = jamiBridge.flipCamera()
}
