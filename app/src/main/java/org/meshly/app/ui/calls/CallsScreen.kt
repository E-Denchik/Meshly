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

package org.meshly.app.ui.calls

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.meshly.app.R
import org.meshly.app.data.model.CallType
import org.meshly.app.data.model.Contact
import org.meshly.app.data.model.ContactStatus
import org.meshly.app.ui.components.Avatar
import org.meshly.app.ui.components.EmptyState
import org.meshly.app.ui.viewmodel.CallViewModel
import org.meshly.app.ui.viewmodel.ContactViewModel

@Composable
fun CallsScreen(
    modifier: Modifier = Modifier,
    onDial: (jamiId: String, displayName: String, callType: CallType) -> Unit,
    contactViewModel: ContactViewModel = viewModel(),
    callViewModel: CallViewModel = viewModel()
) {
    val contacts by contactViewModel.contacts.collectAsStateWithLifecycle()
    val activeCall by callViewModel.activeCall.collectAsStateWithLifecycle()
    val callableContacts = contacts.filter { it.status == ContactStatus.CONFIRMED }

    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text(stringResource(R.string.calls_title)) }) }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            activeCall?.let { session ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
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
                            modifier = Modifier.padding(start = 12.dp)
                        )
                    }
                }
            }

            if (callableContacts.isEmpty()) {
                EmptyState(icon = Icons.Filled.Call, text = stringResource(R.string.calls_empty))
            } else {
                LazyColumn {
                    items(callableContacts, key = { it.jamiId }) { contact ->
                        DialRow(contact, onDial)
                    }
                }
            }
        }
    }
}

@Composable
private fun DialRow(
    contact: Contact,
    onDial: (jamiId: String, displayName: String, callType: CallType) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f, fill = false)) {
            Avatar(name = contact.displayName, seed = contact.jamiId, size = 40.dp)
            Column(modifier = Modifier.padding(start = 12.dp)) {
                Text(contact.displayName, style = MaterialTheme.typography.titleMedium)
                Text(
                    contact.jamiId,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
        Row {
            IconButton(onClick = { onDial(contact.jamiId, contact.displayName, CallType.AUDIO) }) {
                Icon(Icons.Filled.Call, contentDescription = stringResource(R.string.content_desc_audio_call))
            }
            IconButton(onClick = { onDial(contact.jamiId, contact.displayName, CallType.VIDEO) }) {
                Icon(Icons.Filled.Videocam, contentDescription = stringResource(R.string.content_desc_video_call))
            }
        }
    }
}
