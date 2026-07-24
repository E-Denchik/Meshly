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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.meshly.app.MeshlyApplication
import org.meshly.app.R
import org.meshly.app.daemontox.ToxBridge
import org.meshly.app.daemontox.ToxDaemonEvent
import org.meshly.app.data.model.CallType
import org.meshly.app.data.repository.ToxSavedataStore
import org.meshly.app.ui.MainActivity
import org.meshly.app.ui.call.IncomingCallActivity
import java.util.concurrent.Executors
import kotlin.math.min

class ToxDaemonService : Service() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(serviceJob)

    /**
     * A dedicated single thread, not a shared pool - [ToxBridge.iterate]/[ToxBridge.avIterate]
     * MUST always run on the same OS thread (see `tox_jni.c`'s top-of-file doc on the shared-
     * JNIEnv callback dispatch design); `Dispatchers.IO`/`Default` hop across pooled threads
     * between suspension points, which would silently break that contract.
     */
    private val toxThread = Executors.newSingleThreadExecutor { r -> Thread(r, "tox-iterate") }
    private val toxDispatcher = toxThread.asCoroutineDispatcher()

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, createNotification())
        // ToxBridge.startDaemon()/startAv() already ran in MeshlyApplication.onCreate - this
        // service only owns driving the iterate loop and turning events into notifications.
        startIterateLoop()
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
        toxThread.shutdown()
    }

    /** Drives `tox_iterate`/`toxav_iterate` repeatedly, sleeping the shorter of the two
     *  requested intervals between steps, and periodically persists savedata so friend-list/
     *  connection-state changes survive a restart even without an explicit user action. */
    private fun startIterateLoop() {
        serviceScope.launch(toxDispatcher) {
            var sincePersist = 0L
            while (isActive) {
                ToxBridge.iterate()
                if (ToxBridge.avHandle != null) {
                    ToxBridge.avIterate()
                }
                val interval = min(ToxBridge.iterationIntervalMs(), ToxBridge.avIterationIntervalMs().takeIf { ToxBridge.avHandle != null } ?: Int.MAX_VALUE)
                delay(interval.toLong().coerceAtLeast(1))
                sincePersist += interval
                if (sincePersist >= SAVEDATA_PERSIST_INTERVAL_MS) {
                    sincePersist = 0
                    // Not called inline: tox_get_savedata (mutex-protected in c-toxcore, see
                    // tox.c - safe to call off this thread) plus the Base64 encode and
                    // SharedPreferences write this triggers took long enough in practice to
                    // stall this loop for multiple seconds every 30s - which starves
                    // toxav_iterate() of CPU time and was the actual cause of periodic
                    // AudioTrack underruns and multi-second video-frame gaps during calls
                    // (confirmed live: AudioFlinger "pause because of UNDERRUN" logs lined up
                    // with this interval). Firing it on a separate dispatcher keeps this loop's
                    // own iterate/delay cadence unaffected by however long persistence takes.
                    launch(Dispatchers.IO) { ToxSavedataStore.persistNow(applicationContext) }
                }
            }
        }
    }

    private fun observeIncomingCalls() {
        ToxBridge.events
            .filterIsInstance<ToxDaemonEvent.CallInviteReceived>()
            .onEach { event -> showIncomingCallFullScreenIntent(event) }
            .launchIn(serviceScope)
    }

    private suspend fun showIncomingCallFullScreenIntent(event: ToxDaemonEvent.CallInviteReceived) {
        val app = applicationContext as MeshlyApplication
        // CallRepository rejects (CONTROL_CANCEL) any invite that arrives while a call is
        // already active rather than accepting it - mirror that decision here so a second
        // IncomingCallActivity never pops on top of the call already in progress.
        if (app.callRepository.activeCall.value != null) return
        val contactDao = app.database.contactDao()
        val contact = contactDao.getContactByFriendNumber(event.friendNumber) ?: return
        val callId = event.friendNumber.toString()
        val callType = if (event.videoEnabled) CallType.VIDEO else CallType.AUDIO

        val fullScreenIntent = Intent(this, IncomingCallActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(IncomingCallActivity.EXTRA_TOX_ID, contact.toxId)
            putExtra(IncomingCallActivity.EXTRA_DISPLAY_NAME, contact.displayName)
            putExtra(IncomingCallActivity.EXTRA_CALL_ID, callId)
            putExtra(IncomingCallActivity.EXTRA_CALL_TYPE, callType.name)
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            this,
            callId.hashCode(),
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val titleRes = if (callType == CallType.VIDEO) {
            R.string.notification_incoming_video_call
        } else {
            R.string.notification_incoming_audio_call
        }

        val notification = NotificationCompat.Builder(this, MeshlyApplication.CHANNEL_CALL_ID)
            .setContentTitle(getString(titleRes))
            .setContentText(contact.displayName)
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setContentIntent(fullScreenPendingIntent)
            .setAutoCancel(true)
            .build()

        getSystemService(NotificationManager::class.java).notify(callId.hashCode(), notification)
    }

    /** Posts a heads-up notification for an incoming direct message (FR-5.1), no FCM involved -
     *  the event arrives straight from [ToxBridge]'s own event flow. Tapping it deep-links into
     *  that conversation via [MainActivity]'s deep-link extras. */
    private fun observeIncomingMessages() {
        ToxBridge.events
            .filterIsInstance<ToxDaemonEvent.FriendMessageReceived>()
            .onEach { event -> showMessageNotification(event) }
            .launchIn(serviceScope)
    }

    private suspend fun showMessageNotification(event: ToxDaemonEvent.FriendMessageReceived) {
        val contactDao = (applicationContext as MeshlyApplication).database.contactDao()
        val contact = contactDao.getContactByFriendNumber(event.friendNumber) ?: return
        val text = runCatching { String(event.message, Charsets.UTF_8) }.getOrDefault("")

        val contentIntent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(MainActivity.EXTRA_DEEPLINK_TOX_ID, contact.toxId)
            putExtra(MainActivity.EXTRA_DEEPLINK_DISPLAY_NAME, contact.displayName)
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            contact.toxId.hashCode(),
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, MeshlyApplication.CHANNEL_MESSAGE_ID)
            .setContentTitle(contact.displayName)
            .setContentText(text.ifBlank { getString(R.string.notification_message_attachment_fallback) })
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        getSystemService(NotificationManager::class.java).notify(contact.toxId.hashCode(), notification)
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
        private const val SAVEDATA_PERSIST_INTERVAL_MS = 30_000L
    }
}
