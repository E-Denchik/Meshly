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

package org.meshly.app.ui.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.meshly.app.MeshlyApplication
import org.meshly.app.data.model.Account
import org.meshly.app.data.repository.AccountRepository
import java.io.File
import java.io.IOException

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val accountRepository = AccountRepository(application)
    private val prefs = application.getSharedPreferences("meshly_network_settings", Context.MODE_PRIVATE)

    val account: StateFlow<Account?> = accountRepository.currentAccount

    private val _upnpEnabled = MutableStateFlow(prefs.getBoolean(KEY_UPNP, true))
    val upnpEnabled: StateFlow<Boolean> = _upnpEnabled.asStateFlow()

    private val _turnEnabled = MutableStateFlow(prefs.getBoolean(KEY_TURN, true))
    val turnEnabled: StateFlow<Boolean> = _turnEnabled.asStateFlow()

    fun setUpnpEnabled(enabled: Boolean) {
        _upnpEnabled.value = enabled
        prefs.edit().putBoolean(KEY_UPNP, enabled).apply()
        accountRepository.updateNetworkSettings(upnpEnabled = enabled)
    }

    fun setTurnEnabled(enabled: Boolean) {
        _turnEnabled.value = enabled
        prefs.edit().putBoolean(KEY_TURN, enabled).apply()
        accountRepository.updateNetworkSettings(turnEnabled = enabled)
    }

    fun exportAccount(password: String): String = accountRepository.exportAccountBackup(password)

    fun addBootstrapNode(node: String) = accountRepository.addBootstrapNode(node)

    fun removeBootstrapNode(node: String) = accountRepository.removeBootstrapNode(node)

    /**
     * Logs out of this identity: wipes local chat/contact history (it belongs to the identity
     * being logged out of, and would otherwise leak into whichever account logs in next on this
     * device) and clears the stored account. Irreversible without a previously exported backup.
     */
    suspend fun logout() {
        withContext(Dispatchers.IO) {
            (getApplication<Application>() as MeshlyApplication).database.clearAllTables()
        }
        accountRepository.logout()
    }

    /**
     * Captures this process's own logcat output (no special permission needed to read your own
     * app's log lines) into a file under the app's cache dir, and returns a `content://` URI for
     * it via [FileProvider] so it can be shared/saved through any app. Returns null on I/O
     * failure. Once the real libjami daemon exists, its native logs should be appended here too.
     */
    suspend fun exportDiagnosticLogs(): Uri? = withContext(Dispatchers.IO) {
        val context = getApplication<Application>()
        try {
            val diagnosticsDir = File(context.cacheDir, "diagnostics").apply { mkdirs() }
            val logFile = File(diagnosticsDir, "meshly_diagnostics_${System.currentTimeMillis()}.log")
            val process = Runtime.getRuntime().exec(arrayOf("logcat", "-d", "-v", "time"))
            process.inputStream.bufferedReader().use { reader ->
                logFile.bufferedWriter().use { writer ->
                    reader.forEachLine { line -> writer.appendLine(line) }
                }
            }
            process.waitFor()
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", logFile)
        } catch (e: IOException) {
            null
        }
    }

    companion object {
        private const val KEY_UPNP = "upnp_enabled"
        private const val KEY_TURN = "turn_enabled"
    }
}
