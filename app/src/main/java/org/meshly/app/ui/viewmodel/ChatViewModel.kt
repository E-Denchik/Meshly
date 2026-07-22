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

package org.meshly.app.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.meshly.app.MeshlyApplication
import org.meshly.app.data.model.ChatMessage
import org.meshly.app.data.repository.ChatRepository

class ChatViewModel(application: Application) : AndroidViewModel(application) {
    private val chatRepository = ChatRepository(
        (application as MeshlyApplication).database.chatMessageDao()
    )

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private var activeConversationId: String? = null

    fun setConversationId(conversationId: String) {
        activeConversationId = conversationId
        viewModelScope.launch {
            chatRepository.getMessagesForConversation(conversationId).collectLatest { list ->
                _messages.value = list
            }
        }
    }

    fun sendMessage(text: String, attachmentPath: String? = null) {
        val convId = activeConversationId ?: return
        viewModelScope.launch {
            chatRepository.sendMessage(convId, text, attachmentPath)
        }
    }
}
