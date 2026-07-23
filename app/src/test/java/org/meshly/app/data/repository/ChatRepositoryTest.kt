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

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.meshly.app.data.local.ContactEntity
import org.meshly.app.data.model.ChatMessage
import org.meshly.app.data.model.Contact
import org.meshly.app.data.model.ContactStatus
import org.meshly.app.data.model.MessageStatus
import org.meshly.app.data.model.PresenceStatus
import org.meshly.app.fakes.FakeChatMessageDao
import org.meshly.app.fakes.FakeContactDao
import java.util.UUID

class ChatRepositoryTest {

    private val chatDao = FakeChatMessageDao()
    private val contactDao = FakeContactDao()
    private val repository = ChatRepository(chatDao, contactDao)

    private fun fakeToxId(): String =
        UUID.randomUUID().toString().replace("-", "").let { (it + it).take(76) }

    private suspend fun seedOnlineContact(toxId: String) {
        contactDao.insertOrUpdateContact(
            ContactEntity.fromDomain(
                Contact(
                    toxId = toxId,
                    displayName = "Peer",
                    status = ContactStatus.CONFIRMED,
                    presence = PresenceStatus.ONLINE
                )
            )
        )
    }

    @Test
    fun `sendMessage lands as sent after the sending to sent hand-off when the peer is online`() = runBlocking {
        val conversationId = fakeToxId()
        seedOnlineContact(conversationId)

        repository.sendMessage(conversationId, "hi there")

        val messages = repository.getMessagesForConversation(conversationId).first()
        val stored = messages.single()
        assertEquals("hi there", stored.text)
        assertEquals(MessageStatus.SENT, stored.status)
    }

    @Test
    fun `sendMessage fails immediately when the peer is not online`() = runBlocking {
        val conversationId = fakeToxId()

        repository.sendMessage(conversationId, "are you there?")

        val messages = repository.getMessagesForConversation(conversationId).first()
        val stored = messages.single()
        assertEquals(MessageStatus.FAILED, stored.status)
    }

    @Test
    fun `receiveMessage persists an incoming message and can later be marked delivered`() = runBlocking {
        val conversationId = fakeToxId()
        val incoming = ChatMessage(
            id = UUID.randomUUID().toString(),
            conversationId = conversationId,
            senderToxId = fakeToxId(),
            text = "incoming text",
            status = MessageStatus.DELIVERED,
            isIncoming = true
        )

        repository.receiveMessage(incoming)

        val stored = repository.getMessagesForConversation(conversationId).first().single()
        assertEquals(MessageStatus.DELIVERED, stored.status)
        assertEquals(true, stored.isIncoming)
    }
}
