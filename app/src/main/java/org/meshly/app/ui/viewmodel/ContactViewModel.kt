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

package org.meshly.app.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.meshly.app.MeshlyApplication
import org.meshly.app.data.model.Contact
import org.meshly.app.data.repository.ContactRepository

class ContactViewModel(application: Application) : AndroidViewModel(application) {
    private val contactRepository = ContactRepository(
        (application as MeshlyApplication).database.contactDao()
    )

    val contacts: StateFlow<List<Contact>> = contactRepository.allContacts.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    init {
        viewModelScope.launch {
            contactRepository.seedDemoIncomingRequestIfEmpty()
        }
    }

    fun addContactRequest(toxId: String, displayName: String, requestMessage: String? = null) {
        viewModelScope.launch {
            contactRepository.addContactRequest(toxId, displayName, requestMessage)
        }
    }

    fun acceptRequest(contact: Contact) {
        viewModelScope.launch {
            contactRepository.acceptContactRequest(contact)
        }
    }

    fun removeContact(toxId: String) {
        viewModelScope.launch {
            contactRepository.removeContact(toxId)
        }
    }

    fun blockContact(toxId: String) {
        viewModelScope.launch {
            contactRepository.blockContact(toxId)
        }
    }

    fun unblockContact(contact: Contact) {
        viewModelScope.launch {
            contactRepository.unblockContact(contact)
        }
    }
}
