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

package org.meshly.app.fakes

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.meshly.app.data.local.ContactDao
import org.meshly.app.data.local.ContactEntity

/** In-memory [ContactDao] test double so repository tests don't need a real Room database. */
class FakeContactDao : ContactDao {
    private val state = MutableStateFlow<List<ContactEntity>>(emptyList())

    override fun getAllContacts(): StateFlow<List<ContactEntity>> = state

    override fun getContactsByStatus(status: String) =
        MutableStateFlow(state.value.filter { it.status == status })

    override suspend fun getContactById(toxId: String): ContactEntity? =
        state.value.firstOrNull { it.toxId == toxId }

    override suspend fun countContacts(): Int = state.value.size

    override suspend fun insertOrUpdateContact(contact: ContactEntity) {
        state.value = state.value.filterNot { it.toxId == contact.toxId } + contact
    }

    override suspend fun updateStatus(toxId: String, status: String) {
        state.value = state.value.map { if (it.toxId == toxId) it.copy(status = status) else it }
    }

    override suspend fun updatePresence(toxId: String, presence: String) {
        state.value = state.value.map { if (it.toxId == toxId) it.copy(presence = presence) else it }
    }

    override suspend fun deleteContact(toxId: String) {
        state.value = state.value.filterNot { it.toxId == toxId }
    }
}
