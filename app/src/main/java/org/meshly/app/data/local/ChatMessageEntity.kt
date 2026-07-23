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

package org.meshly.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import org.meshly.app.data.model.ChatMessage
import org.meshly.app.data.model.MessageStatus

@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey val id: String,
    val conversationId: String,
    val senderToxId: String,
    val text: String,
    val timestamp: Long,
    val status: String,
    val attachmentPath: String?,
    val isIncoming: Boolean
) {
    fun toDomain(): ChatMessage {
        return ChatMessage(
            id = id,
            conversationId = conversationId,
            senderToxId = senderToxId,
            text = text,
            timestamp = timestamp,
            status = try { MessageStatus.valueOf(status) } catch (e: Exception) { MessageStatus.SENT },
            attachmentPath = attachmentPath,
            isIncoming = isIncoming
        )
    }

    companion object {
        fun fromDomain(message: ChatMessage): ChatMessageEntity {
            return ChatMessageEntity(
                id = message.id,
                conversationId = message.conversationId,
                senderToxId = message.senderToxId,
                text = message.text,
                timestamp = message.timestamp,
                status = message.status.name,
                attachmentPath = message.attachmentPath,
                isIncoming = message.isIncoming
            )
        }
    }
}
