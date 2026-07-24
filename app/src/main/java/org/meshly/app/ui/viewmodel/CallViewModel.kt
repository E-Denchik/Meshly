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

package org.meshly.app.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.StateFlow
import org.meshly.app.MeshlyApplication
import org.meshly.app.data.model.CallSession
import org.meshly.app.data.model.CallType

/**
 * Thin pass-through to [org.meshly.app.data.repository.CallRepository] - the repository (an
 * app-lifetime singleton) owns the call's foreground-service notification lifecycle directly,
 * since it's the only thing guaranteed to be around for every way a call can end, including a
 * remote hangup/error that never goes through any of this ViewModel's methods.
 */
class CallViewModel(application: Application) : AndroidViewModel(application) {
    private val callRepository = (application as MeshlyApplication).callRepository
    val activeCall: StateFlow<CallSession?> = callRepository.activeCall

    /** `null` means a call was already active and nothing was placed - see
     *  [org.meshly.app.data.repository.CallRepository.placeCall]'s doc for why. */
    suspend fun placeCall(peerToxId: String, peerDisplayName: String, type: CallType): CallSession? =
        callRepository.placeCall(peerToxId, peerDisplayName, type)

    fun acceptCall(callId: String) = callRepository.acceptCall(callId)
    fun hangUpCall(callId: String) = callRepository.hangUpCall(callId)
    fun toggleMute() = callRepository.toggleMute()
    fun toggleSpeaker() = callRepository.toggleSpeaker()
    fun toggleCamera() = callRepository.toggleCamera()
    fun flipCamera() = callRepository.flipCamera()
}
