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

package org.meshly.app.daemontox

/**
 * A snapshot of one friend's known state, assembled client-side from
 * [ToxDaemonEvent] callbacks (`friend_name`/`friend_status_message`/
 * `friend_connection_status`/`friend_status`) rather than returned as a
 * single struct by any one native call -- unlike the removed jami-daemon
 * scaffold's `RealContact` (which mapped a single `getContactDetails`
 * `StringMap` directly), tox.h has no equivalent "get everything about this
 * friend" call: each property is queried (or pushed via callback)
 * separately (`tox_friend_get_name`/`_status_message`/
 * `_connection_status`/`_status`, all in `toxcore/tox.h`'s "Friend list
 * queries" section).
 *
 * IMPORTANT, confirmed from tox.h (lines 918-923): `friendNumber` is only
 * stable for the lifetime of one running `Tox*` instance. After saving
 * state and reloading it, friend numbers may be reassigned -- deleting a
 * friend creates a gap that the next `tox_friend_add`/`_norequest` call
 * fills. A real contacts repository must key persisted contact records by
 * the friend's [publicKey] (stable, cryptographic identity), never by
 * [friendNumber] (ephemeral, session-local) -- `tox_friend_get_public_key`
 * (tox.h, not individually cited here) is the real call to resolve one from
 * the other.
 */
data class ToxFriendInfo(
    val friendNumber: Int,
    val publicKey: ByteArray,
    val name: ByteArray = ByteArray(0),
    val statusMessage: ByteArray = ByteArray(0),
    /** `Tox_Connection` ordinal -- see [ToxNative.toxFriendGetConnectionStatus]'s doc. */
    val connectionStatus: Int = 0,
    /** `Tox_User_Status` ordinal: NONE=0, AWAY=1, BUSY=2 (tox.h lines 376-395, confirmed). */
    val userStatus: Int = 0
)
