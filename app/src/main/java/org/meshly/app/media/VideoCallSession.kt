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

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Size
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch
import org.meshly.app.daemontox.ToxBridge
import org.meshly.app.daemontox.ToxDaemonEvent

/**
 * Screen-lifetime camera capture/send + remote frame rendering for one call's video, owned by
 * [org.meshly.app.ui.call.CallScreen]'s composition (bound to its [LifecycleOwner]) - unlike
 * [AudioCallEngine], which is deliberately headless at the repository level. Video inherently
 * needs a visible surface for local self-preview, so there's no case to support where video
 * capture should keep running with the screen off; tying it to the screen's own lifecycle also
 * means CameraX unbinds automatically when that lifecycle is destroyed, on top of the explicit
 * [stop] this class exposes.
 */
class VideoCallSession(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val friendNumber: Int
) {
    private val sessionScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val analysisExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var boundPreviewView: PreviewView? = null
    private var remoteFrameJob: Job? = null

    // Reused across frames (see YuvFrameConverter's doc) - the analyzer callback is always the
    // same single thread (analysisExecutor), and the remote-frame collector below is likewise
    // always the same coroutine, so neither buffer set needs synchronization.
    private val sendBuffers = YuvFrameConverter.SendBuffers()
    private val receiveBuffers = YuvFrameConverter.ReceiveBuffers()

    @Volatile private var isFrontCamera = true
    @Volatile private var cameraEnabled = true
    // Local self-preview binds (and shows) the camera as soon as the screen is up, so the user
    // can see themselves while dialing/ringing - matches other messengers. But actually
    // *transmitting* frames before the call is connected is pointless: toxav_video_send_frame
    // has no active AV session to send into yet, so every call fails (logged, and the wasted
    // YUV conversion work runs for nothing) until the peer actually answers. Gated on the same
    // signal CallRepository already uses to start/stop AudioCallEngine, so video now follows
    // the same "only transmit once actually connected" rule audio already did.
    @Volatile private var callConnected = false
    @Volatile private var lastSendTimeMs = 0L
    @Volatile private var started = false

    private val _remoteFrame = MutableStateFlow<Bitmap?>(null)
    val remoteFrame: StateFlow<Bitmap?> = _remoteFrame.asStateFlow()

    /** Binds the camera to [previewView] for local self-view and starts sending frames, and
     *  starts collecting/rendering the peer's incoming frames into [remoteFrame]. Idempotent -
     *  callers (e.g. an `AndroidView` factory, re-invoked on every recomposition that recreates
     *  the underlying view) can call this more than once safely; only the first call acts. */
    fun start(previewView: PreviewView, frontCamera: Boolean = true) {
        if (started) return
        started = true
        isFrontCamera = frontCamera
        bindCamera(previewView)
        remoteFrameJob = sessionScope.launch {
            ToxBridge.events
                .filterIsInstance<ToxDaemonEvent.VideoFrameReceived>()
                .filter { it.friendNumber == friendNumber }
                // If rendering ever falls behind the incoming frame rate, always jump to the
                // latest frame instead of working through a backlog in arrival order - without
                // this, a slow stretch would show up as growing, ever-increasing lag rather
                // than just briefly-stale video that catches back up on its own.
                .conflate()
                .collect { frame ->
                    val bitmap = YuvFrameConverter.renderToBitmap(
                        frame.width, frame.height, frame.y, frame.u, frame.v,
                        frame.yStride, frame.uStride, frame.vStride, receiveBuffers
                    )
                    _remoteFrame.value = bitmap
                }
        }
    }

    fun stop() {
        remoteFrameJob?.cancel()
        remoteFrameJob = null
        cameraProvider?.unbindAll()
        cameraProvider = null
        boundPreviewView = null
        sessionScope.cancel()
        analysisExecutor.shutdown()
    }

    /** Stops transmitting frames without unbinding the camera (avoids rebind churn on quick
     *  toggles) - the peer simply stops receiving new video until re-enabled. */
    fun setCameraEnabled(enabled: Boolean) {
        cameraEnabled = enabled
    }

    /** Call whenever the call's [org.meshly.app.data.model.CallState] changes - only
     *  [org.meshly.app.data.model.CallState.CONNECTED] actually allows sending; every other
     *  state (dialing, ringing, ended) just skips the analyzer's frame-send step entirely. */
    fun setCallConnected(connected: Boolean) {
        callConnected = connected
    }

    fun flipCamera() {
        isFrontCamera = !isFrontCamera
        boundPreviewView?.let { bindCamera(it) }
    }

    private fun bindCamera(previewView: PreviewView) {
        boundPreviewView = previewView
        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener({
            val provider = providerFuture.get()
            cameraProvider = provider
            provider.unbindAll()

            val previewUseCase = Preview.Builder().build().also {
                it.surfaceProvider = previewView.surfaceProvider
            }

            val resolutionSelector = ResolutionSelector.Builder()
                .setResolutionStrategy(
                    ResolutionStrategy(Size(640, 480), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER)
                )
                .build()
            val analysisUseCase = ImageAnalysis.Builder()
                .setResolutionSelector(resolutionSelector)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { analysis ->
                    analysis.setAnalyzer(analysisExecutor) { imageProxy ->
                        try {
                            if (cameraEnabled && callConnected) {
                                val now = SystemClock.elapsedRealtime()
                                if (now - lastSendTimeMs >= MIN_FRAME_INTERVAL_MS) {
                                    lastSendTimeMs = now
                                    val i420 = YuvFrameConverter.imageProxyToI420(imageProxy, imageProxy.imageInfo.rotationDegrees, sendBuffers)
                                    val ok = ToxBridge.sendVideoFrame(friendNumber, i420.width, i420.height, i420.y, i420.u, i420.v)
                                    if (!ok) {
                                        android.util.Log.w("VideoCallSession", "sendVideoFrame failed for friend=$friendNumber ${i420.width}x${i420.height}")
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("VideoCallSession", "analyzer frame processing failed", e)
                        } finally {
                            imageProxy.close()
                        }
                    }
                }

            val selector = if (isFrontCamera) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
            camera = runCatching {
                provider.bindToLifecycle(lifecycleOwner, selector, previewUseCase, analysisUseCase)
            }.getOrNull()
        }, ContextCompat.getMainExecutor(context))
    }

    companion object {
        private const val MIN_FRAME_INTERVAL_MS = 66L // ~15fps cap, independent of sensor fps
    }
}
