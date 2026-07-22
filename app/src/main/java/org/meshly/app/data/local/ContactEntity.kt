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

package org.meshly.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import org.meshly.app.data.model.Contact
import org.meshly.app.data.model.ContactStatus
import org.meshly.app.data.model.PresenceStatus

@Entity(tableName = "contacts")
data class ContactEntity(
    @PrimaryKey val jamiId: String,
    val displayName: String,
    val username: String?,
    val status: String,
    val presence: String,
    val addedAt: Long
) {
    fun toDomain(): Contact {
        return Contact(
            jamiId = jamiId,
            displayName = displayName,
            username = username,
            status = try { ContactStatus.valueOf(status) } catch (e: Exception) { ContactStatus.CONFIRMED },
            presence = try { PresenceStatus.valueOf(presence) } catch (e: Exception) { PresenceStatus.OFFLINE },
            addedAt = addedAt
        )
    }

    companion object {
        fun fromDomain(contact: Contact): ContactEntity {
            return ContactEntity(
                jamiId = contact.jamiId,
                displayName = contact.displayName,
                username = contact.username,
                status = contact.status.name,
                presence = contact.presence.name,
                addedAt = contact.addedAt
            )
        }
    }
}
