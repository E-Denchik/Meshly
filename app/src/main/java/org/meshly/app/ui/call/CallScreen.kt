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

package org.meshly.app.ui.call

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.camera.view.PreviewView
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.MutableStateFlow
import org.meshly.app.R
import org.meshly.app.data.model.CallState
import org.meshly.app.data.model.CallType
import org.meshly.app.media.VideoCallSession
import org.meshly.app.ui.components.Avatar
import org.meshly.app.ui.components.AvatarSize
import org.meshly.app.ui.theme.CallSurfaceColors
import org.meshly.app.ui.theme.Spacing
import org.meshly.app.ui.viewmodel.CallViewModel

@Composable
fun CallScreen(
    peerToxId: String,
    peerDisplayName: String,
    callType: CallType,
    isOutgoing: Boolean,
    onCallEnded: () -> Unit,
    viewModel: CallViewModel = viewModel()
) {
    val activeCall by viewModel.activeCall.collectAsStateWithLifecycle()

    LaunchedEffect(peerToxId) {
        if (isOutgoing) {
            viewModel.placeCall(peerToxId, peerDisplayName, callType)
            // Real signaling from here: toxav_call() was just sent by placeCall(), and
            // activeCall.state transitions (DIALING -> CONNECTED/ENDED) come from the peer's
            // real answer/reject via CallRepository's CallInviteReceived/CallStateChanged
            // handling - nothing simulated here anymore.
        }
    }

    LaunchedEffect(activeCall?.state) {
        if (activeCall?.state == CallState.ENDED) {
            onCallEnded()
        }
    }

    val session = activeCall
    val friendNumber = session?.callId?.toIntOrNull()

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val videoSession = remember(friendNumber) {
        if (callType == CallType.VIDEO && friendNumber != null) {
            VideoCallSession(context, lifecycleOwner, friendNumber)
        } else {
            null
        }
    }
    DisposableEffect(videoSession) {
        onDispose { videoSession?.stop() }
    }
    LaunchedEffect(session?.isCameraOn) {
        videoSession?.setCameraEnabled(session?.isCameraOn == true)
    }
    LaunchedEffect(session?.state) {
        videoSession?.setCallConnected(session?.state == CallState.CONNECTED)
    }

    val remoteFrameFlow = remember(videoSession) { videoSession?.remoteFrame ?: MutableStateFlow(null) }
    val remoteFrame by remoteFrameFlow.collectAsStateWithLifecycle()

    Scaffold { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(CallSurfaceColors.background)
        ) {
            Column(
                modifier = Modifier.fillMaxSize().padding(Spacing.xl),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    val showingRemoteVideo = callType == CallType.VIDEO && remoteFrame != null

                    if (showingRemoteVideo) {
                        Image(
                            bitmap = remoteFrame!!.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize()
                        )
                        Column(
                            modifier = Modifier.align(Alignment.TopStart).padding(Spacing.lg),
                            horizontalAlignment = Alignment.Start
                        ) {
                            Text(peerDisplayName, style = MaterialTheme.typography.titleMedium, color = CallSurfaceColors.onSurface)
                            Text(callStateLabel(session?.state), style = MaterialTheme.typography.bodySmall, color = CallSurfaceColors.onSurfaceMuted)
                        }
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Avatar(name = peerDisplayName, seed = peerToxId, size = AvatarSize.Large)
                            Spacer(Modifier.height(Spacing.xl))
                            Text(
                                peerDisplayName,
                                style = MaterialTheme.typography.headlineSmall,
                                color = CallSurfaceColors.onSurface
                            )
                            Spacer(Modifier.height(Spacing.sm))
                            AnimatedContent(
                                targetState = session?.state,
                                transitionSpec = { fadeIn() togetherWith fadeOut() },
                                label = "call-state-label"
                            ) { state ->
                                Text(
                                    callStateLabel(state),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = CallSurfaceColors.onSurfaceMuted
                                )
                            }
                            if (callType == CallType.VIDEO && session?.state == CallState.CONNECTED) {
                                Spacer(Modifier.height(Spacing.lg))
                                Text(
                                    stringResource(R.string.video_preview_placeholder),
                                    color = CallSurfaceColors.onSurfaceFaint
                                )
                            }
                        }
                    }

                    if (callType == CallType.VIDEO && friendNumber != null) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(Spacing.md)
                                .size(width = 100.dp, height = 140.dp)
                                .clip(RoundedCornerShape(12.dp))
                        ) {
                            AndroidView(
                                factory = { ctx ->
                                    PreviewView(ctx).also { previewView ->
                                        videoSession?.start(previewView, frontCamera = session?.isFrontCamera != false)
                                    }
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                            if (session?.isCameraOn == false) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(CallSurfaceColors.background.copy(alpha = 0.85f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Filled.VideocamOff,
                                        contentDescription = stringResource(R.string.content_desc_local_camera_preview),
                                        tint = CallSurfaceColors.onSurfaceMuted
                                    )
                                }
                            }
                        }
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xl),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledTonalIconButton(onClick = { viewModel.toggleMute() }) {
                        Icon(
                            imageVector = if (session?.isMuted == true) Icons.Filled.MicOff else Icons.Filled.Mic,
                            contentDescription = stringResource(R.string.content_desc_toggle_mute)
                        )
                    }

                    if (callType == CallType.VIDEO) {
                        FilledTonalIconButton(onClick = { viewModel.toggleCamera() }) {
                            Icon(
                                imageVector = if (session?.isCameraOn == true) Icons.Filled.Videocam else Icons.Filled.VideocamOff,
                                contentDescription = stringResource(R.string.content_desc_toggle_camera)
                            )
                        }
                        FilledTonalIconButton(onClick = {
                            viewModel.flipCamera()
                            videoSession?.flipCamera()
                        }) {
                            Icon(Icons.Filled.Cameraswitch, contentDescription = stringResource(R.string.content_desc_flip_camera))
                        }
                    }

                    FilledIconButton(
                        onClick = {
                            session?.let { viewModel.hangUpCall(it.callId) }
                            onCallEnded()
                        },
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(Icons.Filled.CallEnd, contentDescription = stringResource(R.string.content_desc_hang_up))
                    }
                }
            }
        }
    }
}

@Composable
private fun callStateLabel(state: CallState?): String = when (state) {
    CallState.DIALING -> stringResource(R.string.call_state_dialing)
    CallState.INCOMING -> stringResource(R.string.call_state_incoming)
    CallState.CONNECTED -> stringResource(R.string.call_state_connected)
    CallState.ENDED -> stringResource(R.string.call_state_ended)
    CallState.IDLE, null -> stringResource(R.string.call_state_connecting)
}
