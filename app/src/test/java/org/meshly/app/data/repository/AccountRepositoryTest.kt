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
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Runs against a Robolectric-simulated Context because [AccountRepository] persists the Jami ID
 * keypair reference in real Android SharedPreferences. Robolectric gives each test method an
 * isolated app sandbox, so no state leaks between tests here.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AccountRepositoryTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun `hasAccount is false before any account is created`() {
        val repository = AccountRepository(context)
        assertFalse(repository.hasAccount())
    }

    @Test
    fun `loadOrInitAccount creates and persists a jami id keypair on first launch`() {
        val repository = AccountRepository(context)

        val account = repository.loadOrInitAccount()

        assertTrue(account.jamiId.startsWith("jami:"))
        assertTrue(repository.hasAccount())
    }

    @Test
    fun `loadOrInitAccount reuses the persisted jami id across app restarts`() {
        val firstRepository = AccountRepository(context)
        val created = firstRepository.loadOrInitAccount()

        val secondRepository = AccountRepository(context)
        val reloaded = secondRepository.loadOrInitAccount()

        assertEquals(created.jamiId, reloaded.jamiId)
        assertEquals(created.jamiId, secondRepository.currentAccount.value?.jamiId)
    }

    @Test
    fun `exportAccountBackup embeds the jami id`() {
        val repository = AccountRepository(context)
        repository.loadOrInitAccount()

        val bundle = repository.exportAccountBackup("correct horse battery staple")

        assertTrue(bundle.isNotEmpty())
        assertTrue(bundle.contains(repository.currentAccount.value?.jamiId.orEmpty()))
    }
}
