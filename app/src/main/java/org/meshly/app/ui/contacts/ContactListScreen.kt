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

package org.meshly.app.ui.contacts

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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.meshly.app.data.model.Contact
import org.meshly.app.data.model.ContactStatus
import org.meshly.app.ui.viewmodel.ContactViewModel

@Composable
fun ContactListScreen(
    modifier: Modifier = Modifier,
    viewModel: ContactViewModel = viewModel()
) {
    val contacts by viewModel.contacts.collectAsStateWithLifecycle()
    var selectedTab by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }

    val confirmed = contacts.filter { it.status == ContactStatus.CONFIRMED }
    val requests = contacts.filter {
        it.status == ContactStatus.PENDING_INCOMING || it.status == ContactStatus.PENDING_OUTGOING
    }

    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text("Contacts") }) }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text("Jami ID or username") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = {
                        if (searchQuery.isNotBlank()) {
                            viewModel.addContactRequest(searchQuery, searchQuery)
                            searchQuery = ""
                        }
                    }
                ) {
                    Icon(Icons.Filled.PersonAdd, contentDescription = "Add contact")
                }
            }

            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Contacts (${confirmed.size})") }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Requests (${requests.size})") }
                )
            }

            val visibleList = if (selectedTab == 0) confirmed else requests
            if (visibleList.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        if (selectedTab == 0) "No contacts yet." else "No pending requests.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else {
                LazyColumn {
                    items(visibleList, key = { it.jamiId }) { contact ->
                        ContactRow(
                            contact = contact,
                            onAccept = { viewModel.acceptRequest(contact) },
                            onRemove = { viewModel.removeContact(contact.jamiId) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ContactRow(contact: Contact, onAccept: () -> Unit, onRemove: () -> Unit) {
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
        if (contact.status == ContactStatus.PENDING_INCOMING) {
            Row {
                IconButton(onClick = onAccept) {
                    Icon(Icons.Filled.Check, contentDescription = "Accept")
                }
                IconButton(onClick = onRemove) {
                    Icon(Icons.Filled.Close, contentDescription = "Reject")
                }
            }
        } else if (contact.status == ContactStatus.CONFIRMED) {
            IconButton(onClick = onRemove) {
                Icon(Icons.Filled.Close, contentDescription = "Remove")
            }
        }
    }
}
