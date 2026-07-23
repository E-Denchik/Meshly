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

package org.meshly.app.data.model

enum class ContactStatus {
    PENDING_OUTGOING,
    PENDING_INCOMING,
    CONFIRMED,
    BLOCKED
}

enum class PresenceStatus {
    OFFLINE,
    ONLINE,
    UNKNOWN
}

/**
 * @property toxId For [ContactStatus.PENDING_INCOMING]/[ContactStatus.CONFIRMED]/
 *   [ContactStatus.BLOCKED] contacts, the peer's 64-char hex public key (`TOX_PUBLIC_KEY_SIZE`,
 *   32 bytes) -- the only identifier real Tox still has for an established friend. For
 *   [ContactStatus.PENDING_OUTGOING] (added by us, not yet accepted), this is the full 76-char
 *   hex address (`TOX_ADDRESS_SIZE`, 38 bytes: pubkey+nospam+checksum) originally entered/scanned,
 *   since that's what `tox_friend_add` was actually called with.
 * @property friendNumber The real `Tox_Friend_Number` (`uint32_t`) c-toxcore assigned via
 *   `tox_friend_add`/`tox_friend_add_norequest`, needed for every friend-scoped native call
 *   (send message, connection status, calls). Null only for [ContactStatus.PENDING_INCOMING]
 *   (their request arrived via the `friend_request` callback but we haven't called
 *   `tox_friend_add_norequest` yet, so c-toxcore doesn't have a friend-list entry for them).
 */
data class Contact(
    val toxId: String,
    val displayName: String,
    val status: ContactStatus = ContactStatus.CONFIRMED,
    val presence: PresenceStatus = PresenceStatus.UNKNOWN,
    val addedAt: Long = System.currentTimeMillis(),
    val friendNumber: Int? = null
)
