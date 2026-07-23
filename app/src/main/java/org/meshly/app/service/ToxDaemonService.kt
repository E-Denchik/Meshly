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
import org.meshly.app.core.ToxBridge
import org.meshly.app.core.ToxEvent
import org.meshly.app.data.model.CallType
import org.meshly.app.data.model.ChatMessage
import org.meshly.app.ui.MainActivity
import org.meshly.app.ui.call.IncomingCallActivity

class ToxDaemonService : Service() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(serviceJob)

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, createNotification())
        ToxBridge.getInstance().startDaemon()
        observeIncomingCalls()
        observeIncomingMessages()
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
        ToxBridge.getInstance().events
            .filterIsInstance<ToxEvent.IncomingCall>()
            .onEach { event -> showIncomingCallFullScreenIntent(event) }
            .launchIn(serviceScope)
    }

    private fun showIncomingCallFullScreenIntent(event: ToxEvent.IncomingCall) {
        val session = event.session
        val fullScreenIntent = Intent(this, IncomingCallActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(IncomingCallActivity.EXTRA_TOX_ID, session.peerToxId)
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

        val titleRes = if (session.callType == CallType.VIDEO) {
            R.string.notification_incoming_video_call
        } else {
            R.string.notification_incoming_audio_call
        }

        val notification = NotificationCompat.Builder(this, MeshlyApplication.CHANNEL_CALL_ID)
            .setContentTitle(getString(titleRes))
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

    /** Posts a heads-up notification for an incoming direct message (FR-5.1), no FCM involved -
     *  the event arrives straight from [ToxBridge]'s own event flow. Tapping it deep-links into
     *  that conversation via [MainActivity]'s deep-link extras. */
    private fun observeIncomingMessages() {
        ToxBridge.getInstance().events
            .filterIsInstance<ToxEvent.MessageReceived>()
            .onEach { event -> showMessageNotification(event.message) }
            .launchIn(serviceScope)
    }

    private suspend fun showMessageNotification(message: ChatMessage) {
        val contactDao = (applicationContext as MeshlyApplication).database.contactDao()
        val displayName = contactDao.getContactById(message.senderToxId)?.displayName ?: message.senderToxId

        val contentIntent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(MainActivity.EXTRA_DEEPLINK_TOX_ID, message.senderToxId)
            putExtra(MainActivity.EXTRA_DEEPLINK_DISPLAY_NAME, displayName)
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            message.senderToxId.hashCode(),
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, MeshlyApplication.CHANNEL_MESSAGE_ID)
            .setContentTitle(displayName)
            .setContentText(message.text.ifBlank { getString(R.string.notification_message_attachment_fallback) })
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        getSystemService(NotificationManager::class.java).notify(message.senderToxId.hashCode(), notification)
    }

    private fun createNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, MeshlyApplication.CHANNEL_DAEMON_ID)
            .setContentTitle(getString(R.string.notification_daemon_title))
            .setContentText(getString(R.string.notification_daemon_text))
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
