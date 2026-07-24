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
import android.content.Intent
import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
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
import org.meshly.app.service.CallService

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
    private val appContext = context.applicationContext
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val audioEngine = AudioCallEngine(appContext)

    private val _activeCall = MutableStateFlow<CallSession?>(null)
    val activeCall: StateFlow<CallSession?> = _activeCall.asStateFlow()

    // hangUpCall() and the remote ERROR/FINISHED branch below deliberately leave _activeCall
    // holding an ENDED session (not null) so CallScreen/IncomingCallActivity's own state-change
    // effects reliably notice and navigate away - see those call sites' comments. That means a
    // plain null-check is the wrong test for "is a call already in progress": an ENDED session is
    // exactly as free to overwrite as no session at all, it just hasn't been GC'd from the
    // StateFlow yet.
    private fun isBusy(): Boolean = _activeCall.value?.state?.let { it != CallState.ENDED } == true

    // ToxAV's own bwcontroller.c only ever calls back to *lower* a bit rate on measured packet
    // loss (see callback_bwc() in toxav.c: `call->video_bit_rate - (call->video_bit_rate *
    // loss)`) - there is no native "network's fine again, raise it back" signal. Left alone, one
    // bad patch of network permanently caps quality for the rest of the call. This job is what
    // claws it back: after a quiet stretch with no further loss-triggered downgrade, it nudges
    // both rates back up in small steps toward their original ceiling.
    private var bitRateRecoveryJob: Job? = null
    @Volatile private var currentAudioBitRateKbps = AUDIO_BIT_RATE
    @Volatile private var currentVideoBitRateKbps = 0
    @Volatile private var lastBitRateChangeAtMs = 0L

    init {
        ToxBridge.events
            .onEach { event -> handleEvent(event) }
            .launchIn(repositoryScope)
    }

    private fun handleEvent(event: ToxDaemonEvent) {
        when (event) {
            is ToxDaemonEvent.CallInviteReceived -> repositoryScope.launch {
                if (isBusy()) {
                    // Already on a call - reject rather than silently clobbering it.
                    // CallSession only ever tracks one call at a time (see class doc), and
                    // overwriting it here would orphan the in-progress call: its Tox-level
                    // session would stay open with no local UI left pointing at it, while the
                    // screen now shows this new caller's INCOMING state instead. Sending CANCEL
                    // gives the second caller an immediate "busy" rather than a call that just
                    // rings forever.
                    runCatching { ToxBridge.callControl(event.friendNumber, CONTROL_CANCEL) }
                    return@launch
                }
                val contact = contactDao.getContactByFriendNumber(event.friendNumber) ?: run {
                    // Unconfirmed/unknown caller - reject explicitly instead of just dropping the
                    // invite, so they get a prompt rejection instead of ringing with no answer.
                    runCatching { ToxBridge.callControl(event.friendNumber, CONTROL_CANCEL) }
                    return@launch
                }
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
                        // A peer-initiated hangup/error reaches us only through this bitmask, not
                        // through hangUpCall() - so this is the one place that must independently
                        // tear everything down (audio, the ongoing-call notification, and the
                        // screen). Transitioning through ENDED rather than straight to null lets
                        // CallScreen's own state-change effect notice and navigate away; going
                        // straight to null (the previous behavior) left the screen frozen forever
                        // since nothing was ever watching for "session disappeared".
                        audioEngine.stop()
                        stopCallService()
                        stopBitRateRecovery()
                        _activeCall.value = current.copy(state = CallState.ENDED)
                    }
                    bitmask and (STATE_SENDING_A or STATE_SENDING_V or STATE_ACCEPTING_A or STATE_ACCEPTING_V) != 0 -> {
                        // AudioCallEngine.start() is idempotent (no-ops if already running), so
                        // this is safe to call again even if acceptCall() already started it
                        // optimistically - this branch is what covers the *outgoing*-call path,
                        // where nothing else starts it before the peer actually answers.
                        val speakerOn = current.callType == CallType.VIDEO
                        audioEngine.start(event.friendNumber, defaultSpeakerphoneOn = speakerOn)
                        audioEngine.setMuted(current.isMuted)
                        _activeCall.value = current.copy(state = CallState.CONNECTED, isSpeakerOn = speakerOn)
                        startBitRateRecovery(event.friendNumber, current.callType)
                    }
                    else -> Unit
                }
            }
            is ToxDaemonEvent.AudioBitRateSuggested -> {
                val current = _activeCall.value ?: return
                if (current.callId != event.friendNumber.toString()) return
                // ToxAV only fires this when it has actually measured the network getting too
                // saturated for the current rate (toxav.h's own doc on this callback, confirmed)
                // - applying it is what lets the call back off and recover instead of continuing
                // to push more data than the link can carry, which is what "never stabilizes"
                // looks like from the network's side.
                currentAudioBitRateKbps = event.bitRateKbps
                lastBitRateChangeAtMs = SystemClock.elapsedRealtime()
                runCatching { ToxBridge.setAudioBitRate(event.friendNumber, event.bitRateKbps) }
            }
            is ToxDaemonEvent.VideoBitRateSuggested -> {
                val current = _activeCall.value ?: return
                if (current.callId != event.friendNumber.toString()) return
                currentVideoBitRateKbps = event.bitRateKbps
                lastBitRateChangeAtMs = SystemClock.elapsedRealtime()
                runCatching { ToxBridge.setVideoBitRate(event.friendNumber, event.bitRateKbps) }
            }
            else -> Unit
        }
    }

    /** Returns `null` (placing nothing) if a call is already active - [CallSession] only ever
     *  tracks one call at a time, so overwriting it here mid-call would orphan the existing
     *  Tox-level call session (never cancelled) while [AudioCallEngine.start] - idempotent, so a
     *  no-op if audio is already running - would keep routing audio to the *old* friend even
     *  though the UI now shows the new one. Callers must check for `null` and back out instead
     *  of assuming a session was placed. */
    suspend fun placeCall(peerToxId: String, peerDisplayName: String, type: CallType): CallSession? {
        if (isBusy()) {
            return null
        }
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
        startCallService(session)
        return session
    }

    fun acceptCall(callId: String) {
        val friendNumber = callId.toIntOrNull() ?: return
        val current = _activeCall.value ?: return
        ToxBridge.answer(friendNumber, AUDIO_BIT_RATE, if (current.callType == CallType.VIDEO) VIDEO_BIT_RATE else 0)
        val speakerOn = current.callType == CallType.VIDEO
        audioEngine.start(friendNumber, defaultSpeakerphoneOn = speakerOn)
        _activeCall.value = current.copy(state = CallState.CONNECTED, isSpeakerOn = speakerOn)
        startCallService(current)
        startBitRateRecovery(friendNumber, current.callType)
    }

    fun hangUpCall(callId: String) {
        val current = _activeCall.value
        callId.toIntOrNull()?.let { friendNumber ->
            runCatching { ToxBridge.callControl(friendNumber, CONTROL_CANCEL) }
        }
        audioEngine.stop()
        stopCallService()
        stopBitRateRecovery()
        // Same ENDED-not-null reasoning as the remote-hangup branch above: whatever screen is
        // showing this call (CallScreen or IncomingCallScreen) reacts to the state change itself,
        // so this method doesn't need its own separate "tell the UI to close" callback.
        _activeCall.value = current?.takeIf { it.callId == callId }?.copy(state = CallState.ENDED)
    }

    /** Starts the AIMD-style recovery ramp described at [bitRateRecoveryJob]'s declaration -
     *  every [BITRATE_RECOVERY_CHECK_INTERVAL_MS] it checks whether at least
     *  [BITRATE_RECOVERY_QUIET_PERIOD_MS] has passed since the last change (either a downgrade
     *  from ToxAV or a previous recovery step) and, if so, steps each rate that's still below its
     *  original ceiling up by [BITRATE_RECOVERY_STEP_FRACTION] of that ceiling. Safe to call
     *  again on the same call (e.g. [acceptCall] then the confirming [handleEvent] transition) -
     *  it just restarts the ramp from the current rates rather than resetting them. */
    private fun startBitRateRecovery(friendNumber: Int, callType: CallType) {
        if (bitRateRecoveryJob != null) return
        if (currentVideoBitRateKbps == 0) {
            currentVideoBitRateKbps = if (callType == CallType.VIDEO) VIDEO_BIT_RATE else 0
        }
        lastBitRateChangeAtMs = SystemClock.elapsedRealtime()
        bitRateRecoveryJob = repositoryScope.launch {
            while (isActive) {
                delay(BITRATE_RECOVERY_CHECK_INTERVAL_MS)
                val quietForMs = SystemClock.elapsedRealtime() - lastBitRateChangeAtMs
                if (quietForMs < BITRATE_RECOVERY_QUIET_PERIOD_MS) continue

                var changed = false
                if (currentAudioBitRateKbps < AUDIO_BIT_RATE) {
                    currentAudioBitRateKbps = (currentAudioBitRateKbps + AUDIO_BIT_RATE / BITRATE_RECOVERY_STEP_FRACTION)
                        .coerceAtMost(AUDIO_BIT_RATE)
                    runCatching { ToxBridge.setAudioBitRate(friendNumber, currentAudioBitRateKbps) }
                    changed = true
                }
                if (currentVideoBitRateKbps in 1 until VIDEO_BIT_RATE) {
                    currentVideoBitRateKbps = (currentVideoBitRateKbps + VIDEO_BIT_RATE / BITRATE_RECOVERY_STEP_FRACTION)
                        .coerceAtMost(VIDEO_BIT_RATE)
                    runCatching { ToxBridge.setVideoBitRate(friendNumber, currentVideoBitRateKbps) }
                    changed = true
                }
                if (changed) {
                    lastBitRateChangeAtMs = SystemClock.elapsedRealtime()
                }
            }
        }
    }

    private fun stopBitRateRecovery() {
        bitRateRecoveryJob?.cancel()
        bitRateRecoveryJob = null
        currentAudioBitRateKbps = AUDIO_BIT_RATE
        currentVideoBitRateKbps = 0
    }

    /** Started at both call-initiation points (placing a call, accepting one) so the persistent
     *  "ongoing call" notification appears immediately - not gated on [CallState.CONNECTED],
     *  since a dialing/ringing call is already something the OS should let the user get back to.
     *  Stopped from every path that can end a call ([hangUpCall] and the remote
     *  ERROR/FINISHED branch of [handleEvent]) so it can never outlive the call, regardless of
     *  which side ended it. */
    private fun startCallService(session: CallSession) {
        val intent = Intent(appContext, CallService::class.java).apply {
            putExtra(CallService.EXTRA_PEER_NAME, session.peerDisplayName)
            putExtra(CallService.EXTRA_CALL_TYPE, session.callType.name)
        }
        appContext.startForegroundService(intent)
    }

    private fun stopCallService() {
        appContext.stopService(Intent(appContext, CallService::class.java))
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

    /** Local audio-routing choice only - unlike [CONTROL_MUTE_AUDIO], ToxAV has no signaling
     *  concept of "speaker vs earpiece", so there's nothing to send the peer here. */
    fun toggleSpeaker(): Boolean {
        val current = _activeCall.value ?: return false
        val nowOn = !current.isSpeakerOn
        audioEngine.setSpeakerphoneOn(nowOn)
        _activeCall.value = current.copy(isSpeakerOn = nowOn)
        return nowOn
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
        // Was 500 - real mobile/Wi-Fi links commonly can't sustain that from the first packet,
        // and since callback_bwc() in toxav.c only ever lowers the rate on loss (never raises it
        // back on its own - see bitRateRecoveryJob's doc), starting too high meant several rounds
        // of "send too much, lose packets, get told to drop" before the call settled anywhere.
        // Starting lower means fewer of those rounds; the recovery ramp claws back up to whatever
        // the link actually supports if it turns out to have more headroom.
        private const val VIDEO_BIT_RATE = 300 // kbit/sec
        private const val BITRATE_RECOVERY_CHECK_INTERVAL_MS = 3_000L
        private const val BITRATE_RECOVERY_QUIET_PERIOD_MS = 8_000L
        private const val BITRATE_RECOVERY_STEP_FRACTION = 5 // ~20% of the ceiling per step

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
