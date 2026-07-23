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

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import org.meshly.app.daemontox.ToxBridge
import org.meshly.app.daemontox.ToxDaemonEvent
import org.meshly.app.daemontox.fromHex
import org.meshly.app.daemontox.toHex
import org.meshly.app.data.local.ContactDao
import org.meshly.app.data.local.ContactEntity
import org.meshly.app.data.model.Contact
import org.meshly.app.data.model.ContactStatus
import org.meshly.app.data.model.PresenceStatus

/**
 * Real c-toxcore-backed contact repository - see [Contact]'s doc for why `toxId` means different
 * things (76-hex address vs. 64-hex public key) depending on [ContactStatus], and why
 * `friendNumber` (not `toxId`) is what every native friend-scoped call actually needs.
 *
 * "Blocking" has no protocol-level equivalent in Tox (no client-side ignore is visible to the
 * peer, and the friendship itself isn't touched) - [blockContact]/[unblockContact] only flip the
 * local [ContactStatus] so the UI hides/shows the contact; the underlying Tox friendship (and
 * `friendNumber`) is left alone so unblocking doesn't need to re-add anyone.
 */
class ContactRepository(
    private val context: Context,
    private val contactDao: ContactDao
) {
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        ToxBridge.events
            .onEach { event -> handleEvent(event) }
            .launchIn(repositoryScope)
    }

    val allContacts: Flow<List<Contact>> = contactDao.getAllContacts().map { entities ->
        entities.map { it.toDomain() }
    }

    private suspend fun handleEvent(event: ToxDaemonEvent) {
        when (event) {
            is ToxDaemonEvent.FriendRequestReceived -> {
                val toxId = event.publicKey.toHex()
                if (contactDao.getContactById(toxId) == null) {
                    val requestText = runCatching { String(event.message, Charsets.UTF_8) }.getOrDefault("")
                    val displayName = requestText.ifBlank { toxId.take(12) }
                    val contact = Contact(toxId = toxId, displayName = displayName, status = ContactStatus.PENDING_INCOMING)
                    contactDao.insertOrUpdateContact(ContactEntity.fromDomain(contact))
                }
            }
            is ToxDaemonEvent.FriendConnectionStatusChanged -> {
                contactDao.getContactByFriendNumber(event.friendNumber)?.let { entity ->
                    val presence = if (event.connectionStatus != 0) PresenceStatus.ONLINE else PresenceStatus.OFFLINE
                    contactDao.updatePresence(entity.toxId, presence.name)
                    // A friend connecting is proof the friendship is mutual (the peer has us in
                    // their list too) - Tox has no separate "they accepted" signal beyond this,
                    // so a PENDING_OUTGOING contact that ever comes online graduates to CONFIRMED
                    // here. Without this, a contact we added first would stay stuck as pending
                    // forever, with no chat/call access, even once real and connected.
                    if (event.connectionStatus != 0 && entity.status == ContactStatus.PENDING_OUTGOING.name) {
                        contactDao.updateStatus(entity.toxId, ContactStatus.CONFIRMED.name)
                    }
                }
            }
            is ToxDaemonEvent.FriendNameChanged -> {
                contactDao.getContactByFriendNumber(event.friendNumber)?.let { entity ->
                    val name = runCatching { String(event.name, Charsets.UTF_8) }.getOrDefault("")
                    if (name.isNotBlank()) {
                        contactDao.insertOrUpdateContact(entity.copy(displayName = name))
                    }
                }
            }
            else -> Unit
        }
    }

    /**
     * Adds a contact purely by their exact Tox ID (FR-2.1) - there is no username/name-service
     * search in plain Tox, so [toxId] must be the peer's full 76-char hex address pasted or
     * scanned elsewhere. [displayName] is only a local placeholder shown until the real name (if
     * any) arrives via [ToxDaemonEvent.FriendNameChanged]. A blank [requestMessage] is replaced
     * with a default, since `tox_friend_add` rejects a fully empty message
     * (`TOX_ERR_FRIEND_ADD_NO_MESSAGE`).
     */
    suspend fun addContactRequest(toxId: String, displayName: String, requestMessage: String? = null) {
        val message = requestMessage?.trim().let { if (it.isNullOrBlank()) DEFAULT_REQUEST_MESSAGE else it }
        val friendNumber = ToxBridge.addFriend(toxId, message)
        val contact = Contact(
            toxId = toxId.take(64),
            displayName = displayName,
            status = ContactStatus.PENDING_OUTGOING,
            friendNumber = friendNumber
        )
        contactDao.insertOrUpdateContact(ContactEntity.fromDomain(contact))
        ToxSavedataStore.persistNow(context)
    }

    /** [contact.toxId] here is the peer's 64-char hex public key (see [Contact]'s doc). */
    suspend fun acceptContactRequest(contact: Contact) {
        val friendNumber = ToxBridge.addFriendNorequest(contact.toxId)
        val updated = contact.copy(status = ContactStatus.CONFIRMED, friendNumber = friendNumber)
        contactDao.insertOrUpdateContact(ContactEntity.fromDomain(updated))
        ToxSavedataStore.persistNow(context)
    }

    /** FR-2.5. `tox_friend_delete` does NOT notify the peer - Tox has no "unfriend" notification by design. */
    suspend fun removeContact(toxId: String) {
        contactDao.getContactById(toxId)?.friendNumber?.let { ToxBridge.deleteFriend(it) }
        contactDao.deleteContact(toxId)
        ToxSavedataStore.persistNow(context)
    }

    /** See this class's top-of-file doc on why blocking is purely a local status flip. */
    suspend fun blockContact(toxId: String) {
        contactDao.updateStatus(toxId, ContactStatus.BLOCKED.name)
    }

    suspend fun unblockContact(contact: Contact) {
        contactDao.updateStatus(contact.toxId, ContactStatus.CONFIRMED.name)
    }

    companion object {
        private const val DEFAULT_REQUEST_MESSAGE = "Hi! Let's connect on Meshly."
    }
}
