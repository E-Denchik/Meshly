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

package org.meshly.app.ui.calls

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import org.meshly.app.R
import org.meshly.app.data.model.CallType
import org.meshly.app.data.model.Contact
import org.meshly.app.data.model.ContactStatus
import org.meshly.app.data.model.PresenceStatus
import org.meshly.app.ui.components.ContactListItem
import org.meshly.app.ui.components.EmptyState
import org.meshly.app.ui.components.MeshlyTopBar
import org.meshly.app.ui.theme.Spacing
import org.meshly.app.ui.viewmodel.CallViewModel
import org.meshly.app.ui.viewmodel.ContactViewModel

@Composable
fun CallsScreen(
    modifier: Modifier = Modifier,
    onDial: (toxId: String, displayName: String, callType: CallType) -> Unit,
    contactViewModel: ContactViewModel = viewModel(),
    callViewModel: CallViewModel = viewModel()
) {
    val contacts by contactViewModel.contacts.collectAsStateWithLifecycle()
    val activeCall by callViewModel.activeCall.collectAsStateWithLifecycle()
    val callableContacts = contacts.filter { it.status == ContactStatus.CONFIRMED }
    val offlineHint = stringResource(R.string.call_target_offline_hint)
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        modifier = modifier,
        topBar = { MeshlyTopBar(title = stringResource(R.string.calls_title)) },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            activeCall?.let { session ->
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth().padding(Spacing.lg),
                    colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
                ) {
                    Row(
                        modifier = Modifier.padding(Spacing.lg),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (session.callType == CallType.VIDEO) Icons.Filled.Videocam else Icons.Filled.Call,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        val ongoingText = if (session.callType == CallType.VIDEO) {
                            stringResource(R.string.ongoing_video_call_with, session.peerDisplayName)
                        } else {
                            stringResource(R.string.ongoing_audio_call_with, session.peerDisplayName)
                        }
                        Text(
                            ongoingText,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.padding(start = Spacing.md)
                        )
                    }
                }
            }

            if (callableContacts.isEmpty()) {
                EmptyState(icon = Icons.Filled.Call, text = stringResource(R.string.calls_empty))
            } else {
                LazyColumn {
                    items(callableContacts, key = { it.toxId }) { contact ->
                        DialRow(
                            contact = contact,
                            onDial = onDial,
                            onOfflineCallAttempt = {
                                coroutineScope.launch { snackbarHostState.showSnackbar(offlineHint) }
                            },
                            modifier = Modifier.animateItem()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DialRow(
    contact: Contact,
    onDial: (toxId: String, displayName: String, callType: CallType) -> Unit,
    onOfflineCallAttempt: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isOnline = contact.presence == PresenceStatus.ONLINE
    ContactListItem(
        displayName = contact.displayName,
        toxId = contact.toxId,
        modifier = modifier,
        subtitleOverride = if (isOnline) null else stringResource(R.string.call_target_offline_hint)
    ) {
        Box {
            IconButton(onClick = { onDial(contact.toxId, contact.displayName, CallType.AUDIO) }, enabled = isOnline) {
                Icon(Icons.Filled.Call, contentDescription = stringResource(R.string.content_desc_audio_call))
            }
            if (!isOnline) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onOfflineCallAttempt
                        )
                )
            }
        }
        Box {
            IconButton(onClick = { onDial(contact.toxId, contact.displayName, CallType.VIDEO) }, enabled = isOnline) {
                Icon(Icons.Filled.Videocam, contentDescription = stringResource(R.string.content_desc_video_call))
            }
            if (!isOnline) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onOfflineCallAttempt
                        )
                )
            }
        }
    }
}
