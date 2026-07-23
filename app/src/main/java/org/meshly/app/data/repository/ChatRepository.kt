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
import org.meshly.app.daemontox.ToxBridge
import org.meshly.app.daemontox.ToxDaemonEvent
import org.meshly.app.data.local.ChatMessageDao
import org.meshly.app.data.local.ChatMessageEntity
import org.meshly.app.data.local.ContactDao
import org.meshly.app.data.model.ChatMessage
import org.meshly.app.data.model.MessageStatus
import org.meshly.app.data.model.PresenceStatus

/**
 * Real c-toxcore-backed chat repository. c-toxcore identifies a message by a small
 * `Tox_Friend_Message_Id` (`uint32_t`) scoped per-friend, not a UUID - this repository composes
 * Room's own `id` column as `"<friendNumber>:<nativeMessageId>"` so a later
 * [ToxDaemonEvent.FriendReadReceipt] (which only carries `friendNumber`/`messageId`, both raw
 * ints) can be matched back to the right row without a separate in-memory correlation table.
 */
class ChatRepository(
    private val chatMessageDao: ChatMessageDao,
    private val contactDao: ContactDao
) {
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        ToxBridge.events
            .onEach { event -> handleEvent(event) }
            .launchIn(repositoryScope)
    }

    fun getMessagesForConversation(conversationId: String): Flow<List<ChatMessage>> {
        return chatMessageDao.getMessagesForConversation(conversationId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    private suspend fun handleEvent(event: ToxDaemonEvent) {
        when (event) {
            is ToxDaemonEvent.FriendMessageReceived -> {
                val entity = contactDao.getContactByFriendNumber(event.friendNumber) ?: return
                val text = runCatching { String(event.message, Charsets.UTF_8) }.getOrDefault("")
                val message = ChatMessage(
                    id = "${event.friendNumber}:incoming:${System.nanoTime()}",
                    conversationId = entity.toxId,
                    senderToxId = entity.toxId,
                    text = text,
                    status = MessageStatus.DELIVERED,
                    isIncoming = true
                )
                chatMessageDao.insertMessage(ChatMessageEntity.fromDomain(message))
            }
            is ToxDaemonEvent.FriendReadReceipt -> {
                chatMessageDao.updateMessageStatus(
                    "${event.friendNumber}:${event.messageId}",
                    MessageStatus.DELIVERED.name
                )
            }
            else -> Unit
        }
    }

    /**
     * Sends a text message. Plain Tox has no offline/store-and-forward delivery, so unlike a
     * DHT-queued send, this checks the peer's currently known presence first: if they're not
     * ONLINE right now, the message is marked FAILED immediately rather than being handed to
     * `tox_friend_send_message`, which would itself reject it
     * (`TOX_ERR_FRIEND_SEND_MESSAGE_FRIEND_NOT_CONNECTED`) anyway.
     */
    suspend fun sendMessage(conversationId: String, text: String, attachmentPath: String? = null): ChatMessage {
        val contact = contactDao.getContactById(conversationId)
        val friendNumber = contact?.friendNumber
        val peerOnline = contact?.presence == PresenceStatus.ONLINE.name

        if (friendNumber == null || !peerOnline) {
            val failed = ChatMessage(
                id = "local:${System.nanoTime()}",
                conversationId = conversationId,
                senderToxId = "local_me",
                text = text,
                status = MessageStatus.FAILED,
                attachmentPath = attachmentPath,
                isIncoming = false
            )
            chatMessageDao.insertMessage(ChatMessageEntity.fromDomain(failed))
            return failed
        }

        val nativeMessageId = ToxBridge.sendMessage(friendNumber, text)
        val sent = ChatMessage(
            id = "$friendNumber:$nativeMessageId",
            conversationId = conversationId,
            senderToxId = "local_me",
            text = text,
            status = MessageStatus.SENT,
            attachmentPath = attachmentPath,
            isIncoming = false
        )
        chatMessageDao.insertMessage(ChatMessageEntity.fromDomain(sent))
        return sent
    }
}
