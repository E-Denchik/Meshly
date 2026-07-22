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
import org.meshly.app.data.local.ContactDao
import org.meshly.app.data.local.ContactEntity
import org.meshly.app.data.model.Contact
import org.meshly.app.data.model.ContactStatus
import org.meshly.app.data.model.PresenceStatus

class ContactRepository(
    private val contactDao: ContactDao,
    private val jamiBridge: JamiBridge = JamiBridge.getInstance()
) {
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        jamiBridge.events
            .onEach { event -> handleEvent(event) }
            .launchIn(repositoryScope)
    }

    val allContacts: Flow<List<Contact>> = contactDao.getAllContacts().map { entities ->
        entities.map { it.toDomain() }
    }

    private suspend fun handleEvent(event: JamiEvent) {
        when (event) {
            is JamiEvent.ContactRequestReceived -> {
                contactDao.insertOrUpdateContact(ContactEntity.fromDomain(event.contact))
            }
            is JamiEvent.ContactStatusChanged -> {
                contactDao.updateStatus(event.jamiId, event.status.name)
            }
            is JamiEvent.PresenceChanged -> {
                contactDao.updatePresence(event.jamiId, event.presence.name)
            }
            else -> Unit
        }
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
        val updated = contact.copy(status = ContactStatus.CONFIRMED, presence = PresenceStatus.ONLINE)
        contactDao.insertOrUpdateContact(ContactEntity.fromDomain(updated))
        jamiBridge.confirmContact(contact.jamiId, contact.displayName)
    }

    suspend fun removeContact(jamiId: String) {
        contactDao.deleteContact(jamiId)
        jamiBridge.forgetPeer(jamiId)
    }

    /** Blocks a contact (FR-2.5): keeps the local record so it can be unblocked later, but stops
     *  presence/message simulation for that peer and hides it from the confirmed/requests lists. */
    suspend fun blockContact(jamiId: String) {
        contactDao.updateStatus(jamiId, ContactStatus.BLOCKED.name)
        jamiBridge.forgetPeer(jamiId)
    }

    suspend fun unblockContact(contact: Contact) {
        contactDao.updateStatus(contact.jamiId, ContactStatus.CONFIRMED.name)
        jamiBridge.confirmContact(contact.jamiId, contact.displayName)
    }

    /** Seeds one simulated incoming friend request for fresh installs, so the Requests tab isn't
     *  permanently empty until the user manually adds someone who then requests back. */
    suspend fun seedDemoIncomingRequestIfEmpty() {
        if (contactDao.countContacts() == 0) {
            val demo = DEMO_CONTACTS.random()
            jamiBridge.simulateIncomingContactRequest(demo.first, demo.second)
        }
    }

    companion object {
        private val DEMO_CONTACTS = listOf(
            "jami:" + "a".repeat(40) to "Nadia Petrova",
            "jami:" + "b".repeat(40) to "Igor Volkov",
            "jami:" + "c".repeat(40) to "Sara Lindqvist"
        )
    }
}
