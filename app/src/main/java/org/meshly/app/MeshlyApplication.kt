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

package org.meshly.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import org.meshly.app.daemontox.ToxBridge
import org.meshly.app.data.local.AppDatabase
import org.meshly.app.data.repository.CallRepository
import org.meshly.app.data.repository.ChatRepository
import org.meshly.app.data.repository.ContactRepository
import org.meshly.app.data.repository.ToxSavedataStore

class MeshlyApplication : Application() {

    val database by lazy { AppDatabase.getInstance(this) }

    /**
     * App-process-lifetime singletons, not per-screen instances. [ChatRepository]/
     * [ContactRepository]/[CallRepository] each subscribe to [ToxBridge.events] in their `init`
     * block to persist incoming messages/friend requests/calls - that subscription has to exist
     * for as long as the daemon can receive events, not just while the matching screen happens
     * to be on-screen. Constructing a fresh repository per-ViewModel (the original shape here)
     * meant an incoming message or friend request arriving while the user was on some other
     * screen was emitted to a `MutableSharedFlow` with zero subscribers and silently lost
     * forever - verified live: a real message showed delivered+read on the sender's side (that
     * ack is automatic at the Tox core level, independent of the app) but never appeared in the
     * recipient's chat history because no [ChatRepository] instance was alive to store it. The
     * same bug applied to [CallRepository]: a `CallInviteReceived` event fired by
     * `ToxDaemonService` (which launches `IncomingCallActivity` independently) before a fresh
     * per-Activity `CallRepository` existed meant `acceptCall()` had no session to act on and
     * silently no-opped. Held here instead so every ViewModel/Activity shares the one instance
     * created at first access and kept alive by the Application object itself.
     */
    val chatRepository by lazy { ChatRepository(database.chatMessageDao(), database.contactDao()) }
    val contactRepository by lazy { ContactRepository(this, database.contactDao()) }
    val callRepository by lazy { CallRepository(this, database.contactDao()) }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        // Starts the real Tox instance immediately at process start, restoring persisted
        // savedata if any exists (a returning user) or letting tox_new generate a fresh identity
        // in-place (a first run) - see AccountRepository's top-of-file doc for why this ordering
        // makes hasAccount()/loadOrInitAccount()/createAccount() all work correctly regardless of
        // which one runs first. The actual tox_iterate/toxav_iterate loop is driven by
        // ToxDaemonService, not here - this only creates the instance.
        ToxBridge.startDaemon(ToxSavedataStore.load(this))
        ToxBridge.startAv()
        // Force these to construct now, not on first ViewModel access - see their doc above on
        // why an event arriving before the subscription exists is lost, not just delayed.
        chatRepository
        contactRepository
        callRepository
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

            val messageChannel = NotificationChannel(
                CHANNEL_MESSAGE_ID,
                getString(R.string.notification_channel_message_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = getString(R.string.notification_channel_message_desc)
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(daemonChannel)
            notificationManager.createNotificationChannel(callChannel)
            notificationManager.createNotificationChannel(messageChannel)
        }
    }

    companion object {
        const val CHANNEL_DAEMON_ID = "meshly_daemon_channel"
        const val CHANNEL_CALL_ID = "meshly_call_channel"
        const val CHANNEL_MESSAGE_ID = "meshly_message_channel"
    }
}
