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

package org.meshly.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ContactDao {
    @Query("SELECT * FROM contacts ORDER BY displayName ASC")
    fun getAllContacts(): Flow<List<ContactEntity>>

    @Query("SELECT * FROM contacts WHERE status = :status")
    fun getContactsByStatus(status: String): Flow<List<ContactEntity>>

    @Query("SELECT * FROM contacts WHERE jamiId = :jamiId LIMIT 1")
    suspend fun getContactById(jamiId: String): ContactEntity?

    @Query("SELECT COUNT(*) FROM contacts")
    suspend fun countContacts(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateContact(contact: ContactEntity)

    @Query("UPDATE contacts SET status = :status WHERE jamiId = :jamiId")
    suspend fun updateStatus(jamiId: String, status: String)

    @Query("UPDATE contacts SET presence = :presence WHERE jamiId = :jamiId")
    suspend fun updatePresence(jamiId: String, presence: String)

    @Query("DELETE FROM contacts WHERE jamiId = :jamiId")
    suspend fun deleteContact(jamiId: String)
}
