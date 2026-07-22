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

package org.meshly.app.ui.chat

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
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.meshly.app.data.model.CallType
import org.meshly.app.data.model.ChatMessage
import org.meshly.app.data.model.MessageStatus
import org.meshly.app.ui.viewmodel.ChatViewModel

@Composable
fun ChatScreen(
    peerJamiId: String,
    peerDisplayName: String,
    onBack: () -> Unit,
    onStartCall: (CallType) -> Unit,
    viewModel: ChatViewModel = viewModel()
) {
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    var draft by remember { mutableStateOf("") }

    LaunchedEffect(peerJamiId) {
        viewModel.setConversationId(peerJamiId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(peerDisplayName) },
                actions = {
                    IconButton(onClick = { onStartCall(CallType.AUDIO) }) {
                        Icon(Icons.Filled.Call, contentDescription = "Audio call")
                    }
                    IconButton(onClick = { onStartCall(CallType.VIDEO) }) {
                        Icon(Icons.Filled.Videocam, contentDescription = "Video call")
                    }
                }
            )
        },
        bottomBar = {
            Row(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { /* file attachment picker: Phase 2 */ }) {
                    Icon(Icons.Filled.AttachFile, contentDescription = "Attach file")
                }
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Message") }
                )
                IconButton(
                    onClick = {
                        if (draft.isNotBlank()) {
                            viewModel.sendMessage(draft)
                            draft = ""
                        }
                    }
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            reverseLayout = true,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(messages.asReversed(), key = { it.id }) { message ->
                MessageBubble(message)
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
                Text(
                    text = message.text,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
            if (!message.isIncoming) {
                Row(
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp)
                ) {
                    Icon(
                        imageVector = message.status.toIcon(),
                        contentDescription = message.status.name,
                        modifier = Modifier.padding(end = 4.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private fun MessageStatus.toIcon() = when (this) {
    MessageStatus.SENDING -> Icons.Filled.Schedule
    MessageStatus.SENT -> Icons.Filled.Done
    MessageStatus.DELIVERED, MessageStatus.READ -> Icons.Filled.DoneAll
    MessageStatus.FAILED -> Icons.Filled.Schedule
}
