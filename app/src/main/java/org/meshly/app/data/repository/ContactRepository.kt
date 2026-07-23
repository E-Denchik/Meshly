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
import org.meshly.app.data.local.ContactDao
import org.meshly.app.data.local.ContactEntity
import org.meshly.app.data.model.Contact
import org.meshly.app.data.model.ContactStatus
import org.meshly.app.data.model.PresenceStatus

class ContactRepository(
    private val contactDao: ContactDao,
    private val toxBridge: ToxBridge = ToxBridge.getInstance()
) {
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        toxBridge.events
            .onEach { event -> handleEvent(event) }
            .launchIn(repositoryScope)
    }

    val allContacts: Flow<List<Contact>> = contactDao.getAllContacts().map { entities ->
        entities.map { it.toDomain() }
    }

    private suspend fun handleEvent(event: ToxEvent) {
        when (event) {
            is ToxEvent.ContactRequestReceived -> {
                contactDao.insertOrUpdateContact(ContactEntity.fromDomain(event.contact))
            }
            is ToxEvent.ContactStatusChanged -> {
                contactDao.updateStatus(event.toxId, event.status.name)
            }
            is ToxEvent.PresenceChanged -> {
                contactDao.updatePresence(event.toxId, event.presence.name)
            }
            else -> Unit
        }
    }

    /** Adds a contact purely by their exact Tox ID (FR-2.1) - there is no username/name-service
     *  search in plain Tox, so [toxId] must be the peer's full ID pasted or scanned elsewhere. */
    suspend fun addContactRequest(toxId: String, displayName: String, requestMessage: String? = null) {
        val contact = Contact(
            toxId = toxId,
            displayName = displayName,
            status = ContactStatus.PENDING_OUTGOING
        )
        contactDao.insertOrUpdateContact(ContactEntity.fromDomain(contact))
        toxBridge.addContactRequest(toxId, displayName, requestMessage)
    }

    suspend fun acceptContactRequest(contact: Contact) {
        val updated = contact.copy(status = ContactStatus.CONFIRMED, presence = PresenceStatus.ONLINE)
        contactDao.insertOrUpdateContact(ContactEntity.fromDomain(updated))
        toxBridge.confirmContact(contact.toxId, contact.displayName)
    }

    suspend fun removeContact(toxId: String) {
        contactDao.deleteContact(toxId)
        toxBridge.forgetPeer(toxId)
    }

    /** Blocks a contact (FR-2.5): keeps the local record so it can be unblocked later, but stops
     *  presence/message simulation for that peer and hides it from the confirmed/requests lists. */
    suspend fun blockContact(toxId: String) {
        contactDao.updateStatus(toxId, ContactStatus.BLOCKED.name)
        toxBridge.forgetPeer(toxId)
    }

    suspend fun unblockContact(contact: Contact) {
        contactDao.updateStatus(contact.toxId, ContactStatus.CONFIRMED.name)
        toxBridge.confirmContact(contact.toxId, contact.displayName)
    }

    /** Seeds one simulated incoming friend request for fresh installs, so the Requests tab isn't
     *  permanently empty until the user manually adds someone who then requests back. */
    suspend fun seedDemoIncomingRequestIfEmpty() {
        if (contactDao.countContacts() == 0) {
            val demo = DEMO_CONTACTS.random()
            toxBridge.simulateIncomingContactRequest(demo.first, demo.second)
        }
    }

    companion object {
        private val DEMO_CONTACTS = listOf(
            "a".repeat(76) to "Nadia Petrova",
            "b".repeat(76) to "Igor Volkov",
            "c".repeat(76) to "Sara Lindqvist"
        )
    }
}
