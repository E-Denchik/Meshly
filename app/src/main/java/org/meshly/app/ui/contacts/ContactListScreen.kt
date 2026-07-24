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

package org.meshly.app.ui.contacts

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
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
import org.meshly.app.ui.viewmodel.ContactViewModel

/** A Tox ID is a 76-char hex string (32-byte public key + 4-byte nospam + 2-byte checksum);
 *  unlike Jami there is no name-service search, so this is the only accepted add-contact format. */
private val TOX_ID_REGEX = Regex("^[0-9a-fA-F]{76}$")

private fun isValidToxId(query: String): Boolean = TOX_ID_REGEX.matches(query)

@Composable
fun ContactListScreen(
    modifier: Modifier = Modifier,
    onOpenChat: (toxId: String, displayName: String) -> Unit = { _, _ -> },
    onCall: (toxId: String, displayName: String, callType: CallType) -> Unit = { _, _, _ -> },
    viewModel: ContactViewModel = viewModel()
) {
    val contacts by viewModel.contacts.collectAsStateWithLifecycle()
    var selectedTab by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    var requestMessage by remember { mutableStateOf("") }
    var searchError by remember { mutableStateOf<String?>(null) }

    val errorInvalidFormat = stringResource(R.string.contact_search_error_invalid)
    val errorDuplicate = stringResource(R.string.contact_search_error_duplicate)
    val offlineHint = stringResource(R.string.call_target_offline_hint)
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        result.contents?.let { scanned ->
            searchQuery = scanned.trim()
            searchError = null
        }
    }

    val confirmed = contacts.filter { it.status == ContactStatus.CONFIRMED }
    val requests = contacts.filter {
        it.status == ContactStatus.PENDING_INCOMING || it.status == ContactStatus.PENDING_OUTGOING
    }
    val blocked = contacts.filter { it.status == ContactStatus.BLOCKED }

    Scaffold(
        modifier = modifier,
        topBar = { MeshlyTopBar(title = stringResource(R.string.contacts_title)) },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.sm)) {
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
                            scanLauncher.launch(
                                ScanOptions()
                                    .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                                    .setBeepEnabled(false)
                                    .setOrientationLocked(false)
                            )
                        }
                    ) {
                        Icon(Icons.Filled.QrCodeScanner, contentDescription = stringResource(R.string.content_desc_scan_qr))
                    }
                    IconButton(
                        onClick = {
                            val trimmed = searchQuery.trim()
                            searchError = when {
                                trimmed.isBlank() -> null
                                !isValidToxId(trimmed) -> errorInvalidFormat
                                contacts.any { it.toxId == trimmed } -> errorDuplicate
                                else -> {
                                    viewModel.addContactRequest(trimmed, trimmed, requestMessage.ifBlank { null })
                                    searchQuery = ""
                                    requestMessage = ""
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
                OutlinedTextField(
                    value = requestMessage,
                    onValueChange = { requestMessage = it },
                    label = { Text(stringResource(R.string.contact_request_message_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm)
                )
            }

            ScrollableTabRow(selectedTabIndex = selectedTab, edgePadding = Spacing.lg) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0; searchError = null },
                    text = { Text(stringResource(R.string.tab_contacts_count, confirmed.size), maxLines = 1, softWrap = false) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1; searchError = null },
                    text = { Text(stringResource(R.string.tab_requests_count, requests.size), maxLines = 1, softWrap = false) }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2; searchError = null },
                    text = { Text(stringResource(R.string.tab_blocked_count, blocked.size), maxLines = 1, softWrap = false) }
                )
            }

            Crossfade(targetState = selectedTab, label = "contact-tab") { tab ->
                val tabList = when (tab) {
                    0 -> confirmed
                    1 -> requests
                    else -> blocked
                }
                if (tabList.isEmpty()) {
                    EmptyState(
                        icon = when (tab) {
                            0 -> Icons.Filled.People
                            1 -> Icons.Filled.HourglassEmpty
                            else -> Icons.Filled.Block
                        },
                        text = when (tab) {
                            0 -> stringResource(R.string.contacts_empty)
                            1 -> stringResource(R.string.requests_empty)
                            else -> stringResource(R.string.blocked_empty)
                        }
                    )
                } else {
                    LazyColumn {
                        items(tabList, key = { it.toxId }) { contact ->
                            ContactRow(
                                contact = contact,
                                onAccept = { viewModel.acceptRequest(contact) },
                                onRemove = { viewModel.removeContact(contact.toxId) },
                                onBlock = { viewModel.blockContact(contact.toxId) },
                                onUnblock = { viewModel.unblockContact(contact) },
                                onOpenChat = { onOpenChat(contact.toxId, contact.displayName) },
                                onCall = { callType -> onCall(contact.toxId, contact.displayName, callType) },
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
}

@Composable
private fun ContactRow(
    contact: Contact,
    onAccept: () -> Unit,
    onRemove: () -> Unit,
    onBlock: () -> Unit,
    onUnblock: () -> Unit,
    onOpenChat: () -> Unit,
    onCall: (CallType) -> Unit,
    onOfflineCallAttempt: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }

    ContactListItem(
        displayName = contact.displayName,
        toxId = contact.toxId,
        modifier = modifier,
        onClick = if (contact.status == ContactStatus.CONFIRMED) onOpenChat else null
    ) {
        when (contact.status) {
            ContactStatus.PENDING_INCOMING -> {
                IconButton(onClick = onAccept) {
                    Icon(Icons.Filled.Check, contentDescription = stringResource(R.string.content_desc_accept))
                }
                IconButton(onClick = onRemove) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.content_desc_reject))
                }
            }
            ContactStatus.CONFIRMED -> {
                val isOnline = contact.presence == PresenceStatus.ONLINE
                Box {
                    IconButton(onClick = { onCall(CallType.AUDIO) }, enabled = isOnline) {
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
                    IconButton(onClick = { onCall(CallType.VIDEO) }, enabled = isOnline) {
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
