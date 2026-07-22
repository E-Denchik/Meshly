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

package org.meshly.app.data.repository

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.StateFlow
import org.meshly.app.core.JamiBridge
import org.meshly.app.data.model.Account

class AccountRepository(private val context: Context) {
    private val jamiBridge = JamiBridge.getInstance()
    private val prefs: SharedPreferences = context.getSharedPreferences("meshly_account", Context.MODE_PRIVATE)

    val currentAccount: StateFlow<Account?> = jamiBridge.currentAccount

    fun hasAccount(): Boolean = prefs.contains("jami_id")

    fun loadOrInitAccount(): Account {
        val savedJamiId = prefs.getString("jami_id", null)
        val savedUsername = prefs.getString("username", null)

        return if (savedJamiId != null) {
            val account = Account(
                jamiId = savedJamiId,
                username = savedUsername,
                isRegisteredOnNameServer = prefs.getBoolean("is_registered", false)
            )
            jamiBridge.restoreAccount(account)
            account
        } else {
            createAccount(null)
        }
    }

    fun createAccount(username: String?): Account {
        val account = jamiBridge.createAccount(username)
        prefs.edit()
            .putString("jami_id", account.jamiId)
            .putString("username", account.username)
            .putBoolean("is_registered", account.isRegisteredOnNameServer)
            .apply()
        return account
    }

    fun exportAccountBackup(password: String): String {
        val account = currentAccount.value ?: return ""
        return "EXPORTED_KEY_BUNDLE_${account.jamiId}_HASH_${password.hashCode()}"
    }
}
