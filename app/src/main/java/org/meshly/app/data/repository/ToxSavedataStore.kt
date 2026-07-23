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
import android.util.Base64
import org.meshly.app.daemontox.ToxBridge

/**
 * Persists the real c-toxcore savedata blob (`tox_get_savedata`/`tox_options_set_savedata_data`
 * - see [org.meshly.app.daemontox.ToxNative]'s docs) so this device's identity, keys, and friend
 * list survive an app restart. Shared between [AccountRepository] (loads it at startup, saves it
 * right after first account creation) and [ContactRepository] (re-saves it after any
 * friend-list-changing operation, since the friend list itself lives inside this same blob).
 */
internal object ToxSavedataStore {
    private const val PREFS_NAME = "meshly_account"
    private const val KEY_SAVEDATA = "tox_savedata"

    fun load(context: Context): ByteArray? {
        val encoded = prefs(context).getString(KEY_SAVEDATA, null) ?: return null
        return Base64.decode(encoded, Base64.NO_WRAP)
    }

    /** Re-reads the current savedata from the running [ToxBridge] instance and persists it. */
    fun persistNow(context: Context) {
        if (ToxBridge.handle == null) return
        persistBytes(context, ToxBridge.getSavedata())
    }

    /** Persists an already-known savedata blob directly (e.g. one just restored via account import). */
    fun persistBytes(context: Context, bytes: ByteArray) {
        prefs(context).edit()
            .putString(KEY_SAVEDATA, Base64.encodeToString(bytes, Base64.NO_WRAP))
            .apply()
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
