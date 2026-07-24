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

package org.meshly.app.ui.chats

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.meshly.app.R
import org.meshly.app.data.model.ContactStatus
import org.meshly.app.data.model.PresenceStatus
import org.meshly.app.ui.components.AvatarSize
import org.meshly.app.ui.components.ContactListItem
import org.meshly.app.ui.components.EmptyState
import org.meshly.app.ui.components.MeshlyTopBar
import org.meshly.app.ui.viewmodel.ContactViewModel

@Composable
fun ChatsListScreen(
    modifier: Modifier = Modifier,
    onOpenChat: (toxId: String, displayName: String) -> Unit,
    viewModel: ContactViewModel = viewModel()
) {
    val contacts by viewModel.contacts.collectAsStateWithLifecycle()
    val conversations = contacts.filter { it.status == ContactStatus.CONFIRMED }

    Scaffold(
        modifier = modifier,
        topBar = { MeshlyTopBar(title = stringResource(R.string.chats_title)) }
    ) { padding ->
        if (conversations.isEmpty()) {
            EmptyState(
                icon = Icons.Filled.Forum,
                text = stringResource(R.string.chats_empty),
                modifier = Modifier.padding(padding)
            )
        } else {
            val onlineDesc = stringResource(R.string.content_desc_online)
            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
                items(conversations, key = { it.toxId }) { contact ->
                    Column(modifier = Modifier.animateItem()) {
                        ContactListItem(
                            displayName = contact.displayName,
                            toxId = contact.toxId,
                            avatarSize = AvatarSize.Medium,
                            showOnlineIndicator = contact.presence == PresenceStatus.ONLINE,
                            onlineContentDescription = onlineDesc,
                            onClick = { onOpenChat(contact.toxId, contact.displayName) }
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    }
                }
            }
        }
    }
}
