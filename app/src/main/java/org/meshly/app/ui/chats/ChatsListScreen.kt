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

package org.meshly.app.ui.chats

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.meshly.app.data.model.Contact
import org.meshly.app.data.model.ContactStatus
import org.meshly.app.data.model.PresenceStatus
import org.meshly.app.ui.viewmodel.ContactViewModel

@Composable
fun ChatsListScreen(
    modifier: Modifier = Modifier,
    onOpenChat: (jamiId: String, displayName: String) -> Unit,
    viewModel: ContactViewModel = viewModel()
) {
    val contacts by viewModel.contacts.collectAsStateWithLifecycle()
    val conversations = contacts.filter { it.status == ContactStatus.CONFIRMED }

    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text("Chats") }) }
    ) { padding ->
        if (conversations.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "No conversations yet. Add a contact to start chatting.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
                items(conversations, key = { it.jamiId }) { contact ->
                    ConversationRow(contact) { onOpenChat(contact.jamiId, contact.displayName) }
                }
            }
        }
    }
}

@Composable
private fun ConversationRow(contact: Contact, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Text(contact.displayName.take(1).uppercase())
        }
        Column(modifier = Modifier.padding(top = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(contact.displayName, style = MaterialTheme.typography.titleMedium)
                if (contact.presence == PresenceStatus.ONLINE) {
                    Icon(
                        imageVector = Icons.Filled.Circle,
                        contentDescription = "Online",
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.padding(start = 6.dp).size(10.dp)
                    )
                }
            }
            Text(
                contact.jamiId,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
