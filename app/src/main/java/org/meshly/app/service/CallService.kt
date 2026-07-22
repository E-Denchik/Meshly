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
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import org.meshly.app.MeshlyApplication
import org.meshly.app.R
import org.meshly.app.data.model.CallType
import org.meshly.app.ui.call.IncomingCallActivity

class CallService : Service() {

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val peerName = intent?.getStringExtra(EXTRA_PEER_NAME).orEmpty()
        val callType = CallType.valueOf(intent?.getStringExtra(EXTRA_CALL_TYPE) ?: CallType.AUDIO.name)

        startForeground(NOTIFICATION_ID, createCallNotification(peerName, callType))
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createCallNotification(peerName: String, callType: CallType): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, IncomingCallActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        val titleRes = if (callType == CallType.VIDEO) {
            R.string.call_notification_title_video
        } else {
            R.string.call_notification_title_audio
        }

        return NotificationCompat.Builder(this, MeshlyApplication.CHANNEL_CALL_ID)
            .setContentTitle(getString(titleRes))
            .setContentText(getString(R.string.call_notification_text, peerName))
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .build()
    }

    companion object {
        private const val NOTIFICATION_ID = 1002
        const val EXTRA_PEER_NAME = "extra_peer_name"
        const val EXTRA_CALL_TYPE = "extra_call_type"
    }
}
