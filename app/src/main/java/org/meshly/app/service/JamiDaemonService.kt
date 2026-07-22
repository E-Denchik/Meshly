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

package org.meshly.app.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.meshly.app.MeshlyApplication
import org.meshly.app.R
import org.meshly.app.core.JamiBridge
import org.meshly.app.core.JamiEvent
import org.meshly.app.ui.MainActivity
import org.meshly.app.ui.call.IncomingCallActivity

class JamiDaemonService : Service() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(serviceJob)

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, createNotification())
        JamiBridge.getInstance().startDaemon()
        observeIncomingCalls()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
    }

    private fun observeIncomingCalls() {
        JamiBridge.getInstance().events
            .filterIsInstance<JamiEvent.IncomingCall>()
            .onEach { event -> showIncomingCallFullScreenIntent(event) }
            .launchIn(serviceScope)
    }

    private fun showIncomingCallFullScreenIntent(event: JamiEvent.IncomingCall) {
        val session = event.session
        val fullScreenIntent = Intent(this, IncomingCallActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(IncomingCallActivity.EXTRA_JAMI_ID, session.peerJamiId)
            putExtra(IncomingCallActivity.EXTRA_DISPLAY_NAME, session.peerDisplayName)
            putExtra(IncomingCallActivity.EXTRA_CALL_ID, session.callId)
            putExtra(IncomingCallActivity.EXTRA_CALL_TYPE, session.callType.name)
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            this,
            session.callId.hashCode(),
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, MeshlyApplication.CHANNEL_CALL_ID)
            .setContentTitle("Incoming ${session.callType.name.lowercase()} call")
            .setContentText(session.peerDisplayName)
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setContentIntent(fullScreenPendingIntent)
            .setAutoCancel(true)
            .build()

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(session.callId.hashCode(), notification)
    }

    private fun createNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, MeshlyApplication.CHANNEL_DAEMON_ID)
            .setContentTitle("Meshly P2P Core")
            .setContentText("Connected to decentralized OpenDHT network")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    companion object {
        private const val NOTIFICATION_ID = 1001
    }
}
