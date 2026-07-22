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

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import org.meshly.app.ui.viewmodel.ContactViewModel

/** FR-2.1: a full Jami ID is "jami:" + a 40-char hex public key hash; a registered username
 *  follows the Jami name-service's own charset/length rules (letters, digits, `_.-`, 3-32 chars). */
private val JAMI_ID_REGEX = Regex("^jami:[0-9a-fA-F]{40}$")
private val USERNAME_REGEX = Regex("^[a-zA-Z0-9_.-]{3,32}$")

private fun isValidJamiIdOrUsername(query: String): Boolean =
    JAMI_ID_REGEX.matches(query) || USERNAME_REGEX.matches(query)

@Composable
fun ContactListScreen(
    modifier: Modifier = Modifier,
    onOpenChat: (jamiId: String, displayName: String) -> Unit = { _, _ -> },
    onCall: (jamiId: String, displayName: String, callType: CallType) -> Unit = { _, _, _ -> },
    viewModel: ContactViewModel = viewModel()
) {
    val contacts by viewModel.contacts.collectAsStateWithLifecycle()
    var selectedTab by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    var searchError by remember { mutableStateOf<String?>(null) }

    val errorInvalidFormat = stringResource(R.string.contact_search_error_invalid)
    val errorDuplicate = stringResource(R.string.contact_search_error_duplicate)

    val confirmed = contacts.filter { it.status == ContactStatus.CONFIRMED }
    val requests = contacts.filter {
        it.status == ContactStatus.PENDING_INCOMING || it.status == ContactStatus.PENDING_OUTGOING
    }
    val blocked = contacts.filter { it.status == ContactStatus.BLOCKED }

    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text(stringResource(R.string.contacts_title)) }) }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it; searchError = null },
                        label = { Text(stringResource(R.string.contact_search_label)) },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        isError = searchError != null,
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = {
                            val trimmed = searchQuery.trim()
                            searchError = when {
                                trimmed.isBlank() -> null
                                !isValidJamiIdOrUsername(trimmed) -> errorInvalidFormat
                                contacts.any { it.jamiId == trimmed } -> errorDuplicate
                                else -> {
                                    viewModel.addContactRequest(trimmed, trimmed)
                                    searchQuery = ""
                                    null
                                }
                            }
                        }
                    ) {
                        Icon(Icons.Filled.PersonAdd, contentDescription = stringResource(R.string.content_desc_add_contact))
                    }
                }
                searchError?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }

            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text(stringResource(R.string.tab_contacts_count, confirmed.size)) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text(stringResource(R.string.tab_requests_count, requests.size)) }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text(stringResource(R.string.tab_blocked_count, blocked.size)) }
                )
            }

            val visibleList = when (selectedTab) {
                0 -> confirmed
                1 -> requests
                else -> blocked
            }
            if (visibleList.isEmpty()) {
                EmptyState(
                    icon = when (selectedTab) {
                        0 -> Icons.Filled.People
                        1 -> Icons.Filled.HourglassEmpty
                        else -> Icons.Filled.Block
                    },
                    text = when (selectedTab) {
                        0 -> stringResource(R.string.contacts_empty)
                        1 -> stringResource(R.string.requests_empty)
                        else -> stringResource(R.string.blocked_empty)
                    }
                )
            } else {
                LazyColumn {
                    items(visibleList, key = { it.jamiId }) { contact ->
                        ContactRow(
                            contact = contact,
                            onAccept = { viewModel.acceptRequest(contact) },
                            onRemove = { viewModel.removeContact(contact.jamiId) },
                            onBlock = { viewModel.blockContact(contact.jamiId) },
                            onUnblock = { viewModel.unblockContact(contact) },
                            onOpenChat = { onOpenChat(contact.jamiId, contact.displayName) },
                            onCall = { callType -> onCall(contact.jamiId, contact.displayName, callType) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ContactRow(
    contact: Contact,
    onAccept: () -> Unit,
    onRemove: () -> Unit,
    onBlock: () -> Unit,
    onUnblock: () -> Unit,
    onOpenChat: () -> Unit,
    onCall: (CallType) -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .let { if (contact.status == ContactStatus.CONFIRMED) it.clickable(onClick = onOpenChat) else it }
            .padding(horizontal = 16.dp, vertical = 12.dp),
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
        when (contact.status) {
            ContactStatus.PENDING_INCOMING -> Row {
                IconButton(onClick = onAccept) {
                    Icon(Icons.Filled.Check, contentDescription = stringResource(R.string.content_desc_accept))
                }
                IconButton(onClick = onRemove) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.content_desc_reject))
                }
            }
            ContactStatus.CONFIRMED -> Row {
                IconButton(onClick = { onCall(CallType.AUDIO) }) {
                    Icon(Icons.Filled.Call, contentDescription = stringResource(R.string.content_desc_audio_call))
                }
                IconButton(onClick = { onCall(CallType.VIDEO) }) {
                    Icon(Icons.Filled.Videocam, contentDescription = stringResource(R.string.content_desc_video_call))
                }
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.content_desc_more_options))
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_block)) },
                            leadingIcon = { Icon(Icons.Filled.Block, contentDescription = null) },
                            onClick = { showMenu = false; onBlock() }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_remove)) },
                            leadingIcon = { Icon(Icons.Filled.Close, contentDescription = null) },
                            onClick = { showMenu = false; onRemove() }
                        )
                    }
                }
            }
            ContactStatus.BLOCKED -> TextButton(onClick = onUnblock) {
                Text(stringResource(R.string.action_unblock))
            }
            else -> Unit
        }
    }
}
