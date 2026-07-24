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

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.meshly.app.daemontox.ToxBridge
import org.meshly.app.daemontox.ToxDaemonEvent
import org.meshly.app.data.local.ContactDao
import org.meshly.app.data.model.CallSession
import org.meshly.app.data.model.CallState
import org.meshly.app.data.model.CallType
import org.meshly.app.media.AudioCallEngine

/**
 * Real ToxAV-backed call repository: call *signaling* (ring, accept/reject, connect/end state)
 * is genuinely wired to `toxav_call`/`toxav_answer`/`toxav_call_control` and really reaches the
 * peer over the network. `callId` is the real `Tox_Friend_Number` (stringified) -
 * [org.meshly.app.data.model.CallSession] only ever needs to track one call at a time so this is
 * simpler than inventing a separate UUID-per-call scheme.
 *
 * Owns [AudioCallEngine] (mic capture -> [ToxBridge.sendAudioFrame], speaker playback <-
 * [ToxDaemonEvent.AudioFrameReceived]) headlessly at the repository level, started/stopped with
 * the call itself rather than tied to any screen - a real phone call must keep carrying audio
 * even with the screen off. Video capture/rendering is deliberately NOT here: it's owned by
 * [org.meshly.app.ui.call.CallScreen] itself (see [org.meshly.app.media.VideoCallSession]'s doc
 * for why - it needs a visible surface for local self-preview, so there's no "video call with
 * the screen off" case to support, unlike audio).
 *
 * This class must be a single app-lifetime instance (see [org.meshly.app.MeshlyApplication]'s
 * `callRepository`), not constructed fresh per-`ViewModel` - otherwise a `CallInviteReceived`
 * event fired by [org.meshly.app.service.ToxDaemonService] before this repository's
 * [ToxBridge.events] subscription exists is lost (no replay buffer), leaving [_activeCall] null
 * when the user taps Accept and making [acceptCall] silently no-op.
 */
class CallRepository(context: Context, private val contactDao: ContactDao) {
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val audioEngine = AudioCallEngine(context.applicationContext)

    private val _activeCall = MutableStateFlow<CallSession?>(null)
    val activeCall: StateFlow<CallSession?> = _activeCall.asStateFlow()

    init {
        ToxBridge.events
            .onEach { event -> handleEvent(event) }
            .launchIn(repositoryScope)
    }

    private fun handleEvent(event: ToxDaemonEvent) {
        when (event) {
            is ToxDaemonEvent.CallInviteReceived -> repositoryScope.launch {
                val contact = contactDao.getContactByFriendNumber(event.friendNumber) ?: return@launch
                _activeCall.value = CallSession(
                    callId = event.friendNumber.toString(),
                    peerToxId = contact.toxId,
                    peerDisplayName = contact.displayName,
                    callType = if (event.videoEnabled) CallType.VIDEO else CallType.AUDIO,
                    state = CallState.INCOMING
                )
            }
            is ToxDaemonEvent.CallStateChanged -> {
                val current = _activeCall.value ?: return
                if (current.callId != event.friendNumber.toString()) return
                val bitmask = event.stateBitmask
                when {
                    bitmask and (STATE_ERROR or STATE_FINISHED) != 0 -> {
                        _activeCall.value = null
                        audioEngine.stop()
                    }
                    bitmask and (STATE_SENDING_A or STATE_SENDING_V or STATE_ACCEPTING_A or STATE_ACCEPTING_V) != 0 -> {
                        // AudioCallEngine.start() is idempotent (no-ops if already running), so
                        // this is safe to call again even if acceptCall() already started it
                        // optimistically - this branch is what covers the *outgoing*-call path,
                        // where nothing else starts it before the peer actually answers.
                        audioEngine.start(event.friendNumber, defaultSpeakerphoneOn = current.callType == CallType.VIDEO)
                        audioEngine.setMuted(current.isMuted)
                        _activeCall.value = current.copy(state = CallState.CONNECTED)
                    }
                    else -> Unit
                }
            }
            else -> Unit
        }
    }

    suspend fun placeCall(peerToxId: String, peerDisplayName: String, type: CallType): CallSession {
        val friendNumber = requireNotNull(contactDao.getContactById(peerToxId)?.friendNumber) {
            "Cannot call $peerToxId: not a confirmed Tox friend (no friendNumber)"
        }
        ToxBridge.call(friendNumber, AUDIO_BIT_RATE, if (type == CallType.VIDEO) VIDEO_BIT_RATE else 0)
        val session = CallSession(
            callId = friendNumber.toString(),
            peerToxId = peerToxId,
            peerDisplayName = peerDisplayName,
            callType = type,
            state = CallState.DIALING
        )
        _activeCall.value = session
        return session
    }

    fun acceptCall(callId: String) {
        val friendNumber = callId.toIntOrNull() ?: return
        val current = _activeCall.value ?: return
        ToxBridge.answer(friendNumber, AUDIO_BIT_RATE, if (current.callType == CallType.VIDEO) VIDEO_BIT_RATE else 0)
        audioEngine.start(friendNumber, defaultSpeakerphoneOn = current.callType == CallType.VIDEO)
        _activeCall.value = current.copy(state = CallState.CONNECTED)
    }

    fun hangUpCall(callId: String) {
        callId.toIntOrNull()?.let { friendNumber ->
            runCatching { ToxBridge.callControl(friendNumber, CONTROL_CANCEL) }
        }
        audioEngine.stop()
        _activeCall.value = null
    }

    fun toggleMute(): Boolean {
        val current = _activeCall.value ?: return false
        val friendNumber = current.callId.toIntOrNull() ?: return current.isMuted
        val nowMuted = !current.isMuted
        ToxBridge.callControl(friendNumber, if (nowMuted) CONTROL_MUTE_AUDIO else CONTROL_UNMUTE_AUDIO)
        audioEngine.setMuted(nowMuted)
        _activeCall.value = current.copy(isMuted = nowMuted)
        return nowMuted
    }

    fun toggleCamera(): Boolean {
        val current = _activeCall.value ?: return false
        val friendNumber = current.callId.toIntOrNull() ?: return current.isCameraOn
        val nowOn = !current.isCameraOn
        ToxBridge.callControl(friendNumber, if (nowOn) CONTROL_SHOW_VIDEO else CONTROL_HIDE_VIDEO)
        _activeCall.value = current.copy(isCameraOn = nowOn)
        return nowOn
    }

    /** Front/back camera selection is a local capture-side decision (which physical camera to
     *  read from), not part of ToxAV's own signaling - purely local state here. */
    fun flipCamera(): Boolean {
        val current = _activeCall.value ?: return false
        val nowFront = !current.isFrontCamera
        _activeCall.value = current.copy(isFrontCamera = nowFront)
        return nowFront
    }

    companion object {
        private const val AUDIO_BIT_RATE = 32 // kbit/sec
        private const val VIDEO_BIT_RATE = 500 // kbit/sec

        // Toxav_Call_Control ordinals (toxav.h lines 424-459, confirmed)
        private const val CONTROL_CANCEL = 2
        private const val CONTROL_MUTE_AUDIO = 3
        private const val CONTROL_UNMUTE_AUDIO = 4
        private const val CONTROL_HIDE_VIDEO = 5
        private const val CONTROL_SHOW_VIDEO = 6

        // Toxav_Friend_Call_State bitmask values (toxav.h lines 355-390, confirmed)
        private const val STATE_ERROR = 1
        private const val STATE_FINISHED = 2
        private const val STATE_SENDING_A = 4
        private const val STATE_SENDING_V = 8
        private const val STATE_ACCEPTING_A = 16
        private const val STATE_ACCEPTING_V = 32
    }
}
