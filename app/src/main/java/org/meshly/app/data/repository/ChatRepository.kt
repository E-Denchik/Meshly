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

package org.meshly.app.data.repository

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import org.meshly.app.core.ToxBridge
import org.meshly.app.core.ToxEvent
import org.meshly.app.data.local.ChatMessageDao
import org.meshly.app.data.local.ChatMessageEntity
import org.meshly.app.data.local.ContactDao
import org.meshly.app.data.model.ChatMessage
import org.meshly.app.data.model.MessageStatus
import org.meshly.app.data.model.PresenceStatus
import java.util.UUID

class ChatRepository(
    private val chatMessageDao: ChatMessageDao,
    private val contactDao: ContactDao,
    private val toxBridge: ToxBridge = ToxBridge.getInstance()
) {
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        toxBridge.events
            .onEach { event -> handleEvent(event) }
            .launchIn(repositoryScope)
    }

    fun getMessagesForConversation(conversationId: String): Flow<List<ChatMessage>> {
        return chatMessageDao.getMessagesForConversation(conversationId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    private suspend fun handleEvent(event: ToxEvent) {
        when (event) {
            is ToxEvent.MessageReceived -> receiveMessage(event.message)
            is ToxEvent.MessageStateChanged -> {
                chatMessageDao.updateMessageStatus(event.messageId, event.status.name)
            }
            else -> Unit
        }
    }

    /**
     * Sends a text message. Plain Tox has no offline/store-and-forward delivery, so unlike
     * Jami's DHT-queued sends, this checks the peer's currently known presence first: if they're
     * not ONLINE right now, the message is marked FAILED immediately instead of optimistically
     * going through a SENDING -> SENT hand-off that could never actually be delivered.
     */
    suspend fun sendMessage(conversationId: String, text: String, attachmentPath: String? = null): ChatMessage {
        val pendingId = UUID.randomUUID().toString()
        val peerOnline = contactDao.getContactById(conversationId)?.presence == PresenceStatus.ONLINE.name

        val pendingMessage = ChatMessage(
            id = pendingId,
            conversationId = conversationId,
            senderToxId = "local_me",
            text = text,
            status = if (peerOnline) MessageStatus.SENDING else MessageStatus.FAILED,
            attachmentPath = attachmentPath,
            isIncoming = false
        )
        chatMessageDao.insertMessage(ChatMessageEntity.fromDomain(pendingMessage))

        if (!peerOnline) {
            return pendingMessage
        }

        toxBridge.sendTextMessage(pendingId, conversationId, text, attachmentPath)
        chatMessageDao.updateMessageStatus(pendingId, MessageStatus.SENT.name)
        return pendingMessage.copy(status = MessageStatus.SENT)
    }

    suspend fun receiveMessage(message: ChatMessage) {
        chatMessageDao.insertMessage(ChatMessageEntity.fromDomain(message))
    }
}
