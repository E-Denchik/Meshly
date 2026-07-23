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
import org.junit.Assert.assertTrue
import org.junit.Test
import org.meshly.app.data.model.Contact
import org.meshly.app.data.model.ContactStatus
import org.meshly.app.fakes.FakeContactDao
import java.util.UUID

class ContactRepositoryTest {

    private val dao = FakeContactDao()
    private val repository = ContactRepository(dao)

    private fun fakeToxId(): String =
        UUID.randomUUID().toString().replace("-", "").let { (it + it).take(76) }

    @Test
    fun `addContactRequest starts a contact as pending outgoing`() = runBlocking {
        val toxId = fakeToxId()
        repository.addContactRequest(toxId, "Alice")

        val contacts = repository.allContacts.first()
        val contact = contacts.single { it.toxId == toxId }
        assertEquals(ContactStatus.PENDING_OUTGOING, contact.status)
    }

    @Test
    fun `acceptContactRequest transitions pending incoming to confirmed`() = runBlocking {
        val toxId = fakeToxId()
        val incoming = Contact(toxId = toxId, displayName = "Bob", status = ContactStatus.PENDING_INCOMING)
        dao.insertOrUpdateContact(org.meshly.app.data.local.ContactEntity.fromDomain(incoming))

        repository.acceptContactRequest(incoming)

        val contact = repository.allContacts.first().single { it.toxId == toxId }
        assertEquals(ContactStatus.CONFIRMED, contact.status)
    }

    @Test
    fun `removeContact deletes the contact from the list`() = runBlocking {
        val toxId = fakeToxId()
        repository.addContactRequest(toxId, "Carol")
        assertTrue(repository.allContacts.first().any { it.toxId == toxId })

        repository.removeContact(toxId)

        assertTrue(repository.allContacts.first().none { it.toxId == toxId })
    }

    @Test
    fun `blockContact marks a confirmed contact as blocked without deleting it`() = runBlocking {
        val toxId = fakeToxId()
        val confirmed = Contact(toxId = toxId, displayName = "Dave", status = ContactStatus.CONFIRMED)
        dao.insertOrUpdateContact(org.meshly.app.data.local.ContactEntity.fromDomain(confirmed))

        repository.blockContact(toxId)

        val contact = repository.allContacts.first().single { it.toxId == toxId }
        assertEquals(ContactStatus.BLOCKED, contact.status)
    }

    @Test
    fun `unblockContact restores a blocked contact to confirmed`() = runBlocking {
        val toxId = fakeToxId()
        val blocked = Contact(toxId = toxId, displayName = "Erin", status = ContactStatus.BLOCKED)
        dao.insertOrUpdateContact(org.meshly.app.data.local.ContactEntity.fromDomain(blocked))

        repository.unblockContact(blocked)

        val contact = repository.allContacts.first().single { it.toxId == toxId }
        assertEquals(ContactStatus.CONFIRMED, contact.status)
    }
}
