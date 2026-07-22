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
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.StateFlow
import org.meshly.app.data.model.Account
import org.meshly.app.data.repository.AccountRepository

class OnboardingViewModel(application: Application) : AndroidViewModel(application) {
    private val accountRepository = AccountRepository(application)
    val account: StateFlow<Account?> = accountRepository.currentAccount

    fun hasAccount(): Boolean = accountRepository.hasAccount()

    fun initOrLoad() {
        accountRepository.loadOrInitAccount()
    }

    fun createAccount(username: String?) {
        accountRepository.createAccount(username)
    }

    /** Returns true if the archive+password round-tripped to a restored identity. */
    fun importAccount(backupPayload: String, password: String): Boolean {
        return accountRepository.importAccountBackup(backupPayload, password) != null
    }

    fun exportAccount(password: String): String {
        return accountRepository.exportAccountBackup(password)
    }
}
