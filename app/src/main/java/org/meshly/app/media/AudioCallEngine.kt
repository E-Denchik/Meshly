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

package org.meshly.app.media

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlin.concurrent.thread
import org.meshly.app.daemontox.ToxBridge
import org.meshly.app.daemontox.ToxDaemonEvent

/**
 * Headless mic-capture/speaker-playback engine for one active call, owned by [org.meshly.app.data.repository.CallRepository]
 * (not any screen) - see that class's doc for why: a real phone call must keep carrying audio
 * even if the screen turns off, unlike video which is tied to [org.meshly.app.ui.call.CallScreen]
 * being visible. [start]/[stop] are cheap to call repeatedly; both are idempotent.
 *
 * Capture and playback each run on their own dedicated `Thread`/coroutine, never the tox
 * iterate thread ([org.meshly.app.service.ToxDaemonService]'s `tox-iterate` thread) - calling
 * [ToxBridge.sendAudioFrame] from any JVM thread is safe (see `tox_jni.c`'s top-of-file doc:
 * the shared-thread requirement only applies to `toxIterate`/`toxavIterate` themselves, which
 * this class never touches).
 */
class AudioCallEngine(private val context: Context) {

    @Volatile private var running = false
    @Volatile var isMuted = false
        private set

    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private var trackChannels = -1
    private var trackSampleRate = -1

    private var audioManager: AudioManager? = null
    private var focusRequest: AudioFocusRequest? = null

    private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var playbackJob: Job? = null

    /** Starts mic capture (if `RECORD_AUDIO` is granted - best-effort, matches
     *  `MainActivity.requestRuntimePermissions()`'s existing best-effort request) and speaker
     *  playback for [friendNumber], and switches [android.media.AudioManager] into call mode.
     *  [defaultSpeakerphoneOn] should be true for video calls, false for audio-only. */
    fun start(friendNumber: Int, defaultSpeakerphoneOn: Boolean) {
        if (running) return
        running = true
        setupAudioRouting(defaultSpeakerphoneOn)
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            startCapture(friendNumber)
        }
        startPlayback(friendNumber)
    }

    fun stop() {
        if (!running) return
        running = false
        audioRecord?.let { record ->
            runCatching { record.stop() }
            runCatching { record.release() }
        }
        audioRecord = null
        playbackJob?.cancel()
        playbackJob = null
        audioTrack?.let { track ->
            runCatching { track.stop() }
            runCatching { track.release() }
        }
        audioTrack = null
        trackChannels = -1
        trackSampleRate = -1
        teardownAudioRouting()
    }

    fun setMuted(muted: Boolean) {
        isMuted = muted
    }

    private fun startCapture(friendNumber: Int) {
        val minBufferBytes = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (minBufferBytes <= 0) return
        val record = try {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                maxOf(minBufferBytes, FRAME_SAMPLES * 2 * 4)
            )
        } catch (e: SecurityException) {
            return
        } catch (e: IllegalArgumentException) {
            return
        }
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            return
        }
        audioRecord = record
        record.startRecording()
        thread(name = "call-audio-capture") {
            val buffer = ShortArray(FRAME_SAMPLES)
            try {
                while (running) {
                    val read = record.read(buffer, 0, buffer.size)
                    if (read < 0) break
                    if (read == 0) continue
                    if (!isMuted) {
                        runCatching { ToxBridge.sendAudioFrame(friendNumber, buffer, read, CHANNELS, SAMPLE_RATE) }
                    }
                }
            } catch (e: Exception) {
                if (running) {
                    android.util.Log.e("AudioCallEngine", "capture loop failed unexpectedly", e)
                }
                // Otherwise expected: stop() concurrently stopped/released the AudioRecord mid-read.
            }
        }
    }

    private fun startPlayback(friendNumber: Int) {
        playbackJob = engineScope.launch {
            ToxBridge.events
                .filterIsInstance<ToxDaemonEvent.AudioFrameReceived>()
                .filter { it.friendNumber == friendNumber }
                .collect { frame -> playFrame(frame) }
        }
    }

    private fun playFrame(frame: ToxDaemonEvent.AudioFrameReceived) {
        runCatching {
            playFrameOrThrow(frame)
        }.onFailure { e -> android.util.Log.e("AudioCallEngine", "playback failed", e) }
    }

    private fun playFrameOrThrow(frame: ToxDaemonEvent.AudioFrameReceived) {
        if (audioTrack == null || trackChannels != frame.channels || trackSampleRate != frame.samplingRate) {
            audioTrack?.let { runCatching { it.stop() }; runCatching { it.release() } }
            val channelMask = if (frame.channels >= 2) AudioFormat.CHANNEL_OUT_STEREO else AudioFormat.CHANNEL_OUT_MONO
            val minBufferBytes = AudioTrack.getMinBufferSize(frame.samplingRate, channelMask, AudioFormat.ENCODING_PCM_16BIT)
            if (minBufferBytes <= 0) return
            val attributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            val format = AudioFormat.Builder()
                .setSampleRate(frame.samplingRate)
                .setChannelMask(channelMask)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .build()
            audioTrack = AudioTrack(
                attributes,
                format,
                maxOf(minBufferBytes, frame.sampleCount * frame.channels * 2),
                AudioTrack.MODE_STREAM,
                AudioManager.AUDIO_SESSION_ID_GENERATE
            )
            trackChannels = frame.channels
            trackSampleRate = frame.samplingRate
            audioTrack?.play()
        }
        audioTrack?.write(frame.pcm, 0, frame.sampleCount * frame.channels)
    }

    private fun setupAudioRouting(speakerphoneOn: Boolean) {
        val manager = context.getSystemService(AudioManager::class.java) ?: return
        audioManager = manager
        manager.mode = AudioManager.MODE_IN_COMMUNICATION
        @Suppress("DEPRECATION")
        manager.isSpeakerphoneOn = speakerphoneOn
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(attributes)
                .build()
            focusRequest = request
            manager.requestAudioFocus(request)
        } else {
            @Suppress("DEPRECATION")
            manager.requestAudioFocus(null, AudioManager.STREAM_VOICE_CALL, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
        }
    }

    private fun teardownAudioRouting() {
        val manager = audioManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let { manager.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            manager.abandonAudioFocus(null)
        }
        @Suppress("DEPRECATION")
        manager.isSpeakerphoneOn = false
        manager.mode = AudioManager.MODE_NORMAL
        audioManager = null
        focusRequest = null
    }

    companion object {
        private const val SAMPLE_RATE = 48000
        private const val CHANNELS = 1
        private const val FRAME_DURATION_MS = 20
        private const val FRAME_SAMPLES = SAMPLE_RATE * FRAME_DURATION_MS / 1000
    }
}
