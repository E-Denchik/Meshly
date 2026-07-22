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
 * `PresenceCallback.newBuddyNotification`'s `status` int. Verbatim from
 * `JamiAccount::PresenceState` (src/jamidht/jamiaccount.h) — the JamiAccount-side tracked-buddy
 * presence, which is what matters for Meshly (a Jami/"RING"-type-only app); the older
 * SIP-account presence subsystem (`getSubscriptions`/`setSubscriptions`, "Buddy"/"Status"/
 * "LineStatus" keys, see [RealSubscription]) is legacy plumbing this app is unlikely to touch.
 */
enum class RealPresenceState(val wireValue: Int) {
    DISCONNECTED(0),
    AVAILABLE(1),
    CONNECTED(2);

    /** Buckets onto org.meshly.app.data.model.PresenceStatus's 3 cases without depending on :app. */
    fun toSimplified(): SimplifiedPresence = when (this) {
        DISCONNECTED -> SimplifiedPresence.OFFLINE
        AVAILABLE, CONNECTED -> SimplifiedPresence.ONLINE
    }

    companion object {
        fun fromWireValue(value: Int): RealPresenceState =
            entries.firstOrNull { it.wireValue == value } ?: DISCONNECTED
    }
}

enum class SimplifiedPresence { ONLINE, OFFLINE }

/**
 * Mirrors one entry of `getSubscriptions(accountId)`'s returned map — this is the legacy
 * SIP-account presence subsystem's shape, keyed by `libjami::Presence::BUDDY_KEY`/`STATUS_KEY`/
 * `LINESTATUS_KEY` (src/jami/presence_const.h), with `STATUS_KEY`'s value being the literal
 * strings `ONLINE_KEY`/`OFFLINE_KEY` ("Online"/"Offline") rather than a JamiAccount-style int.
 * For a Jami-only app, prefer [RealPresenceState] via `newBuddyNotification`/`subscribeBuddy`
 * instead — this type exists mainly for completeness since `getSubscriptions` is part of the
 * JNI-exposed API either way (src/client/presencemanager.cpp shows it branches on account type
 * internally: SIPAccount uses real subscriptions, JamiAccount reuses tracked-buddy presence).
 */
data class RealSubscription(val buddyUri: String, val online: Boolean, val lineStatus: String) {
    companion object {
        fun fromStringMap(map: StringMap): RealSubscription =
            RealSubscription(
                buddyUri = map.get("Buddy").orEmpty(),
                online = map.get("Status") == "Online",
                lineStatus = map.get("LineStatus").orEmpty()
            )
    }
}
