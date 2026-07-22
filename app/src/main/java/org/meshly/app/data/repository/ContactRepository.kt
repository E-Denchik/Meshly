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

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.meshly.app.core.JamiBridge
import org.meshly.app.data.local.ContactDao
import org.meshly.app.data.local.ContactEntity
import org.meshly.app.data.model.Contact
import org.meshly.app.data.model.ContactStatus

class ContactRepository(
    private val contactDao: ContactDao,
    private val jamiBridge: JamiBridge = JamiBridge.getInstance()
) {
    val allContacts: Flow<List<Contact>> = contactDao.getAllContacts().map { entities ->
        entities.map { it.toDomain() }
    }

    suspend fun addContactRequest(jamiId: String, displayName: String) {
        val contact = Contact(
            jamiId = jamiId,
            displayName = displayName,
            status = ContactStatus.PENDING_OUTGOING
        )
        contactDao.insertOrUpdateContact(ContactEntity.fromDomain(contact))
        jamiBridge.addContactRequest(jamiId, displayName)
    }

    suspend fun acceptContactRequest(contact: Contact) {
        val updated = contact.copy(status = ContactStatus.CONFIRMED)
        contactDao.insertOrUpdateContact(ContactEntity.fromDomain(updated))
    }

    suspend fun removeContact(jamiId: String) {
        contactDao.deleteContact(jamiId)
    }
}
