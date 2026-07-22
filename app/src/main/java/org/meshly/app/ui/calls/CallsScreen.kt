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
import androidx.compose.material3.Card
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.meshly.app.data.model.CallType
import org.meshly.app.data.model.Contact
import org.meshly.app.data.model.ContactStatus
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
        topBar = { TopAppBar(title = { Text("Calls") }) }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            activeCall?.let { session ->
                Card(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(
                        "Ongoing ${session.callType.name.lowercase()} call with ${session.peerDisplayName}",
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }

            if (callableContacts.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "Add a confirmed contact to place a call.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
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
        Column {
            Text(contact.displayName, style = MaterialTheme.typography.titleMedium)
            Text(
                contact.jamiId,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Row {
            IconButton(onClick = { onDial(contact.jamiId, contact.displayName, CallType.AUDIO) }) {
                Icon(Icons.Filled.Call, contentDescription = "Audio call")
            }
            IconButton(onClick = { onDial(contact.jamiId, contact.displayName, CallType.VIDEO) }) {
                Icon(Icons.Filled.Videocam, contentDescription = "Video call")
            }
        }
    }
}
