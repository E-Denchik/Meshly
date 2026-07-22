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
            loadPersistedBootstrapNodes()?.let { jamiBridge.updateBootstrapNodes(it) }
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

    fun updateNetworkSettings(upnpEnabled: Boolean? = null, turnEnabled: Boolean? = null) {
        jamiBridge.updateAccountSettings(upnpEnabled, turnEnabled)
    }

    /**
     * Logs out of the identity stored on this device. Without an exported backup archive+
     * password, this identity cannot be recovered afterwards - there is no server to fetch it
     * back from, by design (NFT-1). Clears every persisted preference (jami_id, username,
     * bootstrap nodes) so [hasAccount] reports false and onboarding starts fresh.
     */
    fun logout() {
        prefs.edit().clear().apply()
        jamiBridge.logout()
    }

    /** Adds a manual OpenDHT bootstrap node (FR-6.1); no-op if blank or already present. */
    fun addBootstrapNode(node: String) {
        val trimmed = node.trim()
        if (trimmed.isEmpty()) return
        val current = currentAccount.value ?: return
        if (current.bootstrapNodes.contains(trimmed)) return
        val updated = current.bootstrapNodes + trimmed
        jamiBridge.updateBootstrapNodes(updated)
        persistBootstrapNodes(updated)
    }

    /**
     * Removes a bootstrap node, refusing to drop the last one - NFT-6 calls for at least one
     * node to survive at all times so the app never loses every entry point to the DHT.
     */
    fun removeBootstrapNode(node: String) {
        val current = currentAccount.value ?: return
        if (current.bootstrapNodes.size <= 1) return
        val updated = current.bootstrapNodes - node
        jamiBridge.updateBootstrapNodes(updated)
        persistBootstrapNodes(updated)
    }

    private fun persistBootstrapNodes(nodes: List<String>) {
        prefs.edit().putString(KEY_BOOTSTRAP_NODES, nodes.joinToString(",")).apply()
    }

    private fun loadPersistedBootstrapNodes(): List<String>? =
        prefs.getString(KEY_BOOTSTRAP_NODES, null)?.split(",")?.filter { it.isNotBlank() }

    fun exportAccountBackup(password: String): String {
        val account = currentAccount.value ?: return ""
        return "$BACKUP_PREFIX|${account.jamiId}|${account.username.orEmpty()}|${password.hashCode()}"
    }

    /**
     * Parses a bundle produced by [exportAccountBackup] and, if the password matches, restores
     * that identity (rather than fabricating a brand-new random one, which is what a "mock
     * import" that ignores its input would do). Returns null on a malformed bundle or wrong
     * password, mirroring how a real libjami archive import would reject a bad passphrase.
     */
    fun importAccountBackup(backupPayload: String, password: String): Account? {
        val parts = backupPayload.split("|")
        if (parts.size != 4 || parts[0] != BACKUP_PREFIX) return null

        val (_, jamiId, usernameRaw, passwordHash) = parts
        if (passwordHash.toIntOrNull() != password.hashCode()) return null
        if (jamiId.isBlank()) return null

        val username = usernameRaw.ifEmpty { null }
        val account = Account(
            jamiId = jamiId,
            username = username,
            isRegisteredOnNameServer = username != null
        )
        jamiBridge.restoreAccount(account)
        prefs.edit()
            .putString("jami_id", account.jamiId)
            .putString("username", account.username)
            .putBoolean("is_registered", account.isRegisteredOnNameServer)
            .apply()
        return account
    }

    companion object {
        private const val BACKUP_PREFIX = "MESHLY_BACKUP_V1"
        private const val KEY_BOOTSTRAP_NODES = "bootstrap_nodes"
    }
}
