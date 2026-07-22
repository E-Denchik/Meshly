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

package org.meshly.app.daemon

import net.jami.daemon.StringMap

/**
 * Mirrors the map `JamiService.getContacts`/`getContactDetails` return. Keys are taken verbatim
 * from `Contact::toMap()` (src/jamidht/jami_contact.h) plus the `id` key both
 * `JamiAccount::getContacts` and `ContactList::getContactDetails` inject on top
 * (src/jamidht/jamiaccount.cpp / contact_list.cpp) — every returned map already includes `id`,
 * there's no need to pass the uri in separately.
 *
 * - `id` — the contact's URI / Jami ID
 * - `added` / `removed` — epoch seconds, as decimal strings
 * - `conversationId` — swarm conversation id backing this 1:1 contact, empty if none yet
 * - `confirmed` — only present (as the literal string "true") once the contact is active
 * - `banned` — only present (as the literal string "true") once the contact is banned
 *
 * NOTE: whether SWIG's generated `StringMap.get(key)` returns null or throws for a missing key
 * (relevant for `confirmed`/`banned`, which are only present conditionally) hasn't been confirmed
 * against a real generated build — see PHASE2_BUILD.md's uncertainty list. `getOrNull` below
 * assumes a Map-like `get` that returns null; adjust if the real generated class throws instead.
 */
data class RealContact(
    val uri: String,
    val addedAtEpochSeconds: Long,
    val removedAtEpochSeconds: Long,
    val conversationId: String,
    val confirmed: Boolean,
    val banned: Boolean
) {
    companion object {
        fun fromStringMap(map: StringMap): RealContact =
            RealContact(
                uri = map.get("id").orEmpty(),
                addedAtEpochSeconds = map.get("added")?.toLongOrNull() ?: 0L,
                removedAtEpochSeconds = map.get("removed")?.toLongOrNull() ?: 0L,
                conversationId = map.get("conversationId").orEmpty(),
                confirmed = map.get("confirmed") == "true",
                banned = map.get("banned") == "true"
            )
    }
}

/**
 * Mirrors the map `JamiService.getTrustRequests` returns — a pending incoming contact/friend
 * request. Keys from `libjami::Account::TrustRequest` (src/jami/account_const.h): "from",
 * "received", "payload", "conversationId". Distinct key set from [RealContact] — trust requests
 * are not yet contacts.
 */
data class RealTrustRequest(
    val fromUri: String,
    val receivedAtEpochSeconds: Long,
    val conversationId: String
) {
    companion object {
        fun fromStringMap(map: StringMap): RealTrustRequest =
            RealTrustRequest(
                fromUri = map.get("from").orEmpty(),
                receivedAtEpochSeconds = map.get("received")?.toLongOrNull() ?: 0L,
                conversationId = map.get("conversationId").orEmpty()
            )
    }
}
