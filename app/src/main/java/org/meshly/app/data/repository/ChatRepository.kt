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

package org.meshly.app.data.repository

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import org.meshly.app.core.JamiBridge
import org.meshly.app.core.JamiEvent
import org.meshly.app.data.local.ChatMessageDao
import org.meshly.app.data.local.ChatMessageEntity
import org.meshly.app.data.model.ChatMessage
import org.meshly.app.data.model.MessageStatus
import java.util.UUID

class ChatRepository(
    private val chatMessageDao: ChatMessageDao,
    private val jamiBridge: JamiBridge = JamiBridge.getInstance()
) {
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        jamiBridge.events
            .onEach { event -> handleEvent(event) }
            .launchIn(repositoryScope)
    }

    fun getMessagesForConversation(conversationId: String): Flow<List<ChatMessage>> {
        return chatMessageDao.getMessagesForConversation(conversationId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    private suspend fun handleEvent(event: JamiEvent) {
        when (event) {
            is JamiEvent.MessageReceived -> receiveMessage(event.message)
            is JamiEvent.MessageStateChanged -> {
                chatMessageDao.updateMessageStatus(event.messageId, event.status.name)
            }
            else -> Unit
        }
    }

    suspend fun sendMessage(conversationId: String, text: String, attachmentPath: String? = null): ChatMessage {
        val pendingId = UUID.randomUUID().toString()
        val pendingMessage = ChatMessage(
            id = pendingId,
            conversationId = conversationId,
            senderJamiId = "local_me",
            text = text,
            status = MessageStatus.SENDING,
            attachmentPath = attachmentPath,
            isIncoming = false
        )
        chatMessageDao.insertMessage(ChatMessageEntity.fromDomain(pendingMessage))

        jamiBridge.sendTextMessage(pendingId, conversationId, text, attachmentPath)
        chatMessageDao.updateMessageStatus(pendingId, MessageStatus.SENT.name)
        return pendingMessage.copy(status = MessageStatus.SENT)
    }

    suspend fun receiveMessage(message: ChatMessage) {
        chatMessageDao.insertMessage(ChatMessageEntity.fromDomain(message))
    }
}
