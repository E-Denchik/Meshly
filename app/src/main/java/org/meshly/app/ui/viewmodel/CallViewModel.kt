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
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.StateFlow
import org.meshly.app.data.model.CallSession
import org.meshly.app.data.model.CallType
import org.meshly.app.data.repository.CallRepository
import org.meshly.app.service.CallService

class CallViewModel(application: Application) : AndroidViewModel(application) {
    private val callRepository = CallRepository()
    val activeCall: StateFlow<CallSession?> = callRepository.activeCall

    fun placeCall(peerToxId: String, peerDisplayName: String, type: CallType): CallSession {
        val session = callRepository.placeCall(peerToxId, peerDisplayName, type)
        startCallService(session)
        return session
    }

    fun acceptCall(callId: String) {
        callRepository.acceptCall(callId)
        activeCall.value?.let { startCallService(it) }
    }

    fun hangUpCall(callId: String) {
        callRepository.hangUpCall(callId)
        stopCallService()
    }

    fun toggleMute() = callRepository.toggleMute()
    fun toggleCamera() = callRepository.toggleCamera()
    fun flipCamera() = callRepository.flipCamera()

    private fun startCallService(session: CallSession) {
        val context = getApplication<Application>()
        val intent = Intent(context, CallService::class.java).apply {
            putExtra(CallService.EXTRA_PEER_NAME, session.peerDisplayName)
            putExtra(CallService.EXTRA_CALL_TYPE, session.callType.name)
        }
        context.startForegroundService(intent)
    }

    private fun stopCallService() {
        val context = getApplication<Application>()
        context.stopService(Intent(context, CallService::class.java))
    }
}
