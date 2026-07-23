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
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Runs against a Robolectric-simulated Context because [AccountRepository] persists the Tox ID
 * keypair reference in real Android SharedPreferences. Robolectric gives each test method an
 * isolated app sandbox, so no state leaks between tests here.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AccountRepositoryTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    private val validBootstrapNode =
        "bootstrap.example.org:33445:1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef1234abcd"

    @Test
    fun `hasAccount is false before any account is created`() {
        val repository = AccountRepository(context)
        assertFalse(repository.hasAccount())
    }

    @Test
    fun `loadOrInitAccount creates and persists a tox id keypair on first launch`() {
        val repository = AccountRepository(context)

        val account = repository.loadOrInitAccount()

        assertEquals(76, account.toxId.length)
        assertTrue(repository.hasAccount())
    }

    @Test
    fun `loadOrInitAccount reuses the persisted tox id across app restarts`() {
        val firstRepository = AccountRepository(context)
        val created = firstRepository.loadOrInitAccount()

        val secondRepository = AccountRepository(context)
        val reloaded = secondRepository.loadOrInitAccount()

        assertEquals(created.toxId, reloaded.toxId)
        assertEquals(created.toxId, secondRepository.currentAccount.value?.toxId)
    }

    @Test
    fun `exportAccountBackup embeds the tox id`() {
        val repository = AccountRepository(context)
        repository.loadOrInitAccount()

        val bundle = repository.exportAccountBackup("correct horse battery staple")

        assertTrue(bundle.isNotEmpty())
        assertTrue(bundle.contains(repository.currentAccount.value?.toxId.orEmpty()))
    }

    @Test
    fun `addBootstrapNode appends a valid node and persists it across restarts`() {
        val repository = AccountRepository(context)
        repository.loadOrInitAccount()

        repository.addBootstrapNode(validBootstrapNode)

        assertTrue(
            repository.currentAccount.value?.bootstrapNodes?.contains(validBootstrapNode) == true
        )

        val secondRepository = AccountRepository(context)
        secondRepository.loadOrInitAccount()

        assertTrue(
            secondRepository.currentAccount.value?.bootstrapNodes?.contains(validBootstrapNode) == true
        )
    }

    @Test
    fun `addBootstrapNode ignores a node that is not a host-port-key triple`() {
        val repository = AccountRepository(context)
        repository.loadOrInitAccount()
        val before = repository.currentAccount.value?.bootstrapNodes.orEmpty()

        repository.addBootstrapNode("not-a-valid-node:33445")

        assertEquals(before, repository.currentAccount.value?.bootstrapNodes)
    }

    @Test
    fun `removeBootstrapNode refuses to drop the last remaining node`() {
        val repository = AccountRepository(context)
        repository.loadOrInitAccount()

        val nodes = repository.currentAccount.value?.bootstrapNodes.orEmpty()
        nodes.drop(1).forEach { repository.removeBootstrapNode(it) }
        assertEquals(1, repository.currentAccount.value?.bootstrapNodes?.size)

        val lastNode = repository.currentAccount.value?.bootstrapNodes?.first()!!
        repository.removeBootstrapNode(lastNode)

        assertEquals(1, repository.currentAccount.value?.bootstrapNodes?.size)
        assertEquals(lastNode, repository.currentAccount.value?.bootstrapNodes?.first())
    }

    @Test
    fun `logout clears the persisted account so hasAccount is false again`() {
        val repository = AccountRepository(context)
        repository.loadOrInitAccount()
        assertTrue(repository.hasAccount())

        repository.logout()

        assertFalse(repository.hasAccount())
        assertNull(repository.currentAccount.value)
    }
}
