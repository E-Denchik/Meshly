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

package org.meshly.app.fakes

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.meshly.app.data.local.ChatMessageDao
import org.meshly.app.data.local.ChatMessageEntity

/** In-memory [ChatMessageDao] test double so repository tests don't need a real Room database. */
class FakeChatMessageDao : ChatMessageDao {
    private val state = MutableStateFlow<List<ChatMessageEntity>>(emptyList())

    override fun getMessagesForConversation(conversationId: String): StateFlow<List<ChatMessageEntity>> =
        MutableStateFlow(state.value.filter { it.conversationId == conversationId })

    override suspend fun insertMessage(message: ChatMessageEntity) {
        state.value = state.value.filterNot { it.id == message.id } + message
    }

    override suspend fun updateMessageStatus(id: String, status: String) {
        state.value = state.value.map { if (it.id == id) it.copy(status = status) else it }
    }

    override suspend fun clearHistory(conversationId: String) {
        state.value = state.value.filterNot { it.conversationId == conversationId }
    }

    fun snapshot(conversationId: String): List<ChatMessageEntity> =
        state.value.filter { it.conversationId == conversationId }
}
