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

    @Test
    fun `addContactRequest starts a contact as pending outgoing`() = runBlocking {
        val jamiId = "jami:${UUID.randomUUID()}"
        repository.addContactRequest(jamiId, "Alice")

        val contacts = repository.allContacts.first()
        val contact = contacts.single { it.jamiId == jamiId }
        assertEquals(ContactStatus.PENDING_OUTGOING, contact.status)
    }

    @Test
    fun `acceptContactRequest transitions pending incoming to confirmed`() = runBlocking {
        val jamiId = "jami:${UUID.randomUUID()}"
        val incoming = Contact(jamiId = jamiId, displayName = "Bob", status = ContactStatus.PENDING_INCOMING)
        dao.insertOrUpdateContact(org.meshly.app.data.local.ContactEntity.fromDomain(incoming))

        repository.acceptContactRequest(incoming)

        val contact = repository.allContacts.first().single { it.jamiId == jamiId }
        assertEquals(ContactStatus.CONFIRMED, contact.status)
    }

    @Test
    fun `removeContact deletes the contact from the list`() = runBlocking {
        val jamiId = "jami:${UUID.randomUUID()}"
        repository.addContactRequest(jamiId, "Carol")
        assertTrue(repository.allContacts.first().any { it.jamiId == jamiId })

        repository.removeContact(jamiId)

        assertTrue(repository.allContacts.first().none { it.jamiId == jamiId })
    }

    @Test
    fun `blockContact marks a confirmed contact as blocked without deleting it`() = runBlocking {
        val jamiId = "jami:${UUID.randomUUID()}"
        val confirmed = Contact(jamiId = jamiId, displayName = "Dave", status = ContactStatus.CONFIRMED)
        dao.insertOrUpdateContact(org.meshly.app.data.local.ContactEntity.fromDomain(confirmed))

        repository.blockContact(jamiId)

        val contact = repository.allContacts.first().single { it.jamiId == jamiId }
        assertEquals(ContactStatus.BLOCKED, contact.status)
    }

    @Test
    fun `unblockContact restores a blocked contact to confirmed`() = runBlocking {
        val jamiId = "jami:${UUID.randomUUID()}"
        val blocked = Contact(jamiId = jamiId, displayName = "Erin", status = ContactStatus.BLOCKED)
        dao.insertOrUpdateContact(org.meshly.app.data.local.ContactEntity.fromDomain(blocked))

        repository.unblockContact(blocked)

        val contact = repository.allContacts.first().single { it.jamiId == jamiId }
        assertEquals(ContactStatus.CONFIRMED, contact.status)
    }
}
