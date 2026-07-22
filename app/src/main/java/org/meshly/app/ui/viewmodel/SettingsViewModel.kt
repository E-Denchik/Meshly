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
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.meshly.app.data.model.Account
import org.meshly.app.data.repository.AccountRepository

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
    }

    fun setTurnEnabled(enabled: Boolean) {
        _turnEnabled.value = enabled
        prefs.edit().putBoolean(KEY_TURN, enabled).apply()
    }

    fun exportAccount(password: String): String = accountRepository.exportAccountBackup(password)

    fun exportDiagnosticLogs(): String {
        // Stage 1 mock: real diagnostics will surface libjami daemon logs.
        return "meshly_diagnostics_${System.currentTimeMillis()}.log"
    }

    companion object {
        private const val KEY_UPNP = "upnp_enabled"
        private const val KEY_TURN = "turn_enabled"
    }
}
