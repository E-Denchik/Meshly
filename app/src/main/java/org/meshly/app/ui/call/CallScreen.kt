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

package org.meshly.app.ui.call

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import org.meshly.app.data.model.CallState
import org.meshly.app.data.model.CallType
import org.meshly.app.ui.viewmodel.CallViewModel

@Composable
fun CallScreen(
    peerJamiId: String,
    peerDisplayName: String,
    callType: CallType,
    isOutgoing: Boolean,
    onCallEnded: () -> Unit,
    viewModel: CallViewModel = viewModel()
) {
    val activeCall by viewModel.activeCall.collectAsStateWithLifecycle()

    LaunchedEffect(peerJamiId) {
        if (isOutgoing) {
            val session = viewModel.placeCall(peerJamiId, peerDisplayName, callType)
            // Mock stage: no real peer signaling yet, simulate the callee answering
            // so the UI can be exercised end-to-end without native libjami.
            delay(1500)
            viewModel.acceptCall(session.callId)
        }
    }

    LaunchedEffect(activeCall?.state) {
        if (activeCall?.state == CallState.ENDED) {
            onCallEnded()
        }
    }

    val session = activeCall
    Scaffold { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color(0xFF121212))
        ) {
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        peerDisplayName,
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color.White
                    )
                    Text(
                        session?.state?.name?.lowercase() ?: "connecting",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                }

                if (callType == CallType.VIDEO && session?.isCameraOn == true) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Video preview surface (Phase 2: libjami video sink)", color = Color.White.copy(alpha = 0.5f))
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledTonalIconButton(onClick = { viewModel.toggleMute() }) {
                        Icon(
                            imageVector = if (session?.isMuted == true) Icons.Filled.MicOff else Icons.Filled.Mic,
                            contentDescription = "Toggle mute"
                        )
                    }

                    if (callType == CallType.VIDEO) {
                        FilledTonalIconButton(onClick = { viewModel.toggleCamera() }) {
                            Icon(
                                imageVector = if (session?.isCameraOn == true) Icons.Filled.Videocam else Icons.Filled.VideocamOff,
                                contentDescription = "Toggle camera"
                            )
                        }
                        FilledTonalIconButton(onClick = { viewModel.flipCamera() }) {
                            Icon(Icons.Filled.Cameraswitch, contentDescription = "Flip camera")
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
                        Icon(Icons.Filled.CallEnd, contentDescription = "Hang up")
                    }
                }
            }
        }
    }
}
