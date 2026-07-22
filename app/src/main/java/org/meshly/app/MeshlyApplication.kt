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

package org.meshly.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import org.meshly.app.core.JamiBridge
import org.meshly.app.data.local.AppDatabase

class MeshlyApplication : Application() {

    val database by lazy { AppDatabase.getInstance(this) }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        JamiBridge.getInstance().startDaemon()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val daemonChannel = NotificationChannel(
                CHANNEL_DAEMON_ID,
                getString(R.string.notification_channel_daemon_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_daemon_desc)
            }

            val callChannel = NotificationChannel(
                CHANNEL_CALL_ID,
                getString(R.string.notification_channel_call_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = getString(R.string.notification_channel_call_desc)
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(daemonChannel)
            notificationManager.createNotificationChannel(callChannel)
        }
    }

    companion object {
        const val CHANNEL_DAEMON_ID = "meshly_daemon_channel"
        const val CHANNEL_CALL_ID = "meshly_call_channel"
    }
}
