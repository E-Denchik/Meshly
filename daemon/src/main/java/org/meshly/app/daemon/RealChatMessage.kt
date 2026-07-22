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

import net.jami.daemon.Message

/**
 * Mirrors `libjami::Message` (native/upstream/jami-daemon/bin/jni/configurationmanager.i, lines
 * 76-81): a plain struct with `from`, `payloads` (MIME-type-keyed, same shape [RealJamiBridge]
 * sends with `sendTextMessage`), and `received` (uint64_t epoch; unit not confirmed against a
 * real daemon build — kept raw/unconverted here rather than guessing seconds vs. milliseconds).
 *
 * `payloads.entrySet()` is used rather than a made-up iteration API: jni_interface.i's own
 * `%typemap(javacode) map<string, string>` block implements `StringMap`'s `toNative()` helper
 * with exactly `for (Entry<String, String> e : entrySet())`, which only compiles if the
 * SWIG-generated `StringMap` implements `java.util.Map<String, String>` — so `.entrySet()`,
 * `.get()`, and `.put()` are real, not guessed (unlike `VectMap`, which is index/size-based, see
 * RealJamiBridge.getContacts' note).
 */
data class RealChatMessage(
    val from: String,
    val payloads: Map<String, String>,
    val receivedRaw: Long
) {
    companion object {
        fun fromMessage(message: Message): RealChatMessage =
            RealChatMessage(
                from = message.from,
                payloads = message.payloads.entries.associate { it.key to it.value },
                receivedRaw = message.received
            )
    }
}
