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

package org.meshly.app.ui.chat

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.meshly.app.R
import org.meshly.app.data.model.CallType
import org.meshly.app.data.model.ChatMessage
import org.meshly.app.data.model.MessageStatus
import org.meshly.app.data.model.PresenceStatus
import org.meshly.app.ui.viewmodel.ChatViewModel
import org.meshly.app.ui.viewmodel.ContactViewModel

@Composable
fun ChatScreen(
    peerToxId: String,
    peerDisplayName: String,
    onBack: () -> Unit,
    onStartCall: (CallType) -> Unit,
    viewModel: ChatViewModel = viewModel(),
    contactViewModel: ContactViewModel = viewModel()
) {
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val contacts by contactViewModel.contacts.collectAsStateWithLifecycle()
    val peerPresence = contacts.firstOrNull { it.toxId == peerToxId }?.presence ?: PresenceStatus.UNKNOWN
    var draft by remember { mutableStateOf("") }
    var pendingAttachment by remember { mutableStateOf<Uri?>(null) }
    val context = LocalContext.current

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: SecurityException) {
                // Some providers (e.g. camera roll on certain OEMs) don't support persistable
                // grants; the URI is still usable for the current process lifetime either way.
            }
            pendingAttachment = uri
        }
    }

    LaunchedEffect(peerToxId) {
        viewModel.setConversationId(peerToxId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(peerDisplayName) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.content_desc_back))
                    }
                },
                actions = {
                    IconButton(onClick = { onStartCall(CallType.AUDIO) }) {
                        Icon(Icons.Filled.Call, contentDescription = stringResource(R.string.content_desc_audio_call))
                    }
                    IconButton(onClick = { onStartCall(CallType.VIDEO) }) {
                        Icon(Icons.Filled.Videocam, contentDescription = stringResource(R.string.content_desc_video_call))
                    }
                }
            )
        },
        bottomBar = {
            Column(modifier = Modifier.fillMaxWidth()) {
                pendingAttachment?.let { uri ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.AutoMirrored.Filled.InsertDriveFile, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                        Text(
                            uri.lastPathSegment ?: stringResource(R.string.attachment_fallback_name),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { pendingAttachment = null }) {
                            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.content_desc_remove_attachment))
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { filePicker.launch("*/*") }) {
                        Icon(Icons.Filled.AttachFile, contentDescription = stringResource(R.string.content_desc_attach_file))
                    }
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text(stringResource(R.string.message_placeholder)) }
                    )
                    IconButton(
                        onClick = {
                            if (draft.isNotBlank() || pendingAttachment != null) {
                                viewModel.sendMessage(draft, pendingAttachment?.toString())
                                draft = ""
                                pendingAttachment = null
                            }
                        }
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = stringResource(R.string.content_desc_send))
                    }
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (peerPresence != PresenceStatus.ONLINE) {
                Surface(color = MaterialTheme.colorScheme.errorContainer) {
                    Text(
                        stringResource(R.string.chat_peer_offline_banner),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize().weight(1f),
                reverseLayout = true,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(messages.asReversed(), key = { it.id }) { message ->
                    MessageBubble(message)
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(message: ChatMessage) {
    val bubbleColor = if (message.isIncoming) {
        MaterialTheme.colorScheme.surfaceVariant
    } else {
        MaterialTheme.colorScheme.primaryContainer
    }

    Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp)) {
        Column(
            modifier = Modifier
                .align(if (message.isIncoming) Alignment.CenterStart else Alignment.CenterEnd)
        ) {
            Surface(
                color = bubbleColor,
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    message.attachmentPath?.let { path ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.AutoMirrored.Filled.InsertDriveFile,
                                contentDescription = stringResource(R.string.content_desc_attachment),
                                modifier = Modifier.padding(end = 6.dp)
                            )
                            Text(
                                Uri.parse(path).lastPathSegment ?: stringResource(R.string.attachment_fallback_name),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                    if (message.text.isNotBlank()) {
                        Text(text = message.text)
                    }
                }
            }
            if (!message.isIncoming) {
                Row(
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp)
                ) {
                    Icon(
                        imageVector = message.status.toIcon(),
                        contentDescription = stringResource(message.status.toLabelRes()),
                        modifier = Modifier.padding(end = 4.dp),
                        tint = if (message.status == MessageStatus.FAILED) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
        }
    }
}

private fun MessageStatus.toIcon() = when (this) {
    MessageStatus.SENDING -> Icons.Filled.Schedule
    MessageStatus.SENT -> Icons.Filled.Done
    MessageStatus.DELIVERED -> Icons.Filled.DoneAll
    MessageStatus.FAILED -> Icons.Filled.ErrorOutline
}

private fun MessageStatus.toLabelRes() = when (this) {
    MessageStatus.SENDING -> R.string.message_status_sending
    MessageStatus.SENT -> R.string.message_status_sent
    MessageStatus.DELIVERED -> R.string.message_status_delivered
    MessageStatus.FAILED -> R.string.message_status_failed
}
