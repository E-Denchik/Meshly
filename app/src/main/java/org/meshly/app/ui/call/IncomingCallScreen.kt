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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.meshly.app.R
import org.meshly.app.data.model.CallType
import org.meshly.app.ui.components.Avatar
import org.meshly.app.ui.components.AvatarSize
import org.meshly.app.ui.theme.CallSurfaceColors
import org.meshly.app.ui.theme.Spacing

@Composable
fun IncomingCallScreen(
    peerToxId: String,
    peerDisplayName: String,
    callType: CallType,
    onAccept: () -> Unit,
    onReject: () -> Unit
) {
    Scaffold { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(CallSurfaceColors.background)
        ) {
            Column(
                modifier = Modifier.fillMaxSize().padding(Spacing.xxl),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Avatar(name = peerDisplayName, seed = peerToxId, size = AvatarSize.Large)
                    Spacer(Modifier.height(Spacing.xl))
                    Text(
                        peerDisplayName,
                        style = MaterialTheme.typography.headlineMedium,
                        color = CallSurfaceColors.onSurface
                    )
                    Text(
                        stringResource(
                            if (callType == CallType.VIDEO) R.string.incoming_video_call else R.string.incoming_audio_call
                        ),
                        style = MaterialTheme.typography.bodyLarge,
                        color = CallSurfaceColors.onSurfaceMuted
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(64.dp),
                    modifier = Modifier.padding(bottom = Spacing.xxl)
                ) {
                    FilledIconButton(
                        onClick = onReject,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(Icons.Filled.CallEnd, contentDescription = stringResource(R.string.content_desc_reject))
                    }
                    FilledIconButton(
                        onClick = onAccept,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.tertiary
                        )
                    ) {
                        Icon(Icons.Filled.Call, contentDescription = stringResource(R.string.content_desc_accept))
                    }
                }
            }
        }
    }
}
