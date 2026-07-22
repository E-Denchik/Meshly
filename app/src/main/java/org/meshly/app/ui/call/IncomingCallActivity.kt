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

package org.meshly.app.ui.call

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import org.meshly.app.data.model.CallType
import org.meshly.app.ui.theme.MeshlyTheme
import org.meshly.app.ui.viewmodel.CallViewModel

class IncomingCallActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showOverLockScreen()

        val peerJamiId = intent.getStringExtra(EXTRA_JAMI_ID).orEmpty()
        val peerDisplayName = intent.getStringExtra(EXTRA_DISPLAY_NAME).orEmpty()
        val callId = intent.getStringExtra(EXTRA_CALL_ID).orEmpty()
        val callType = CallType.valueOf(intent.getStringExtra(EXTRA_CALL_TYPE) ?: CallType.AUDIO.name)

        setContent {
            MeshlyTheme {
                val callViewModel: CallViewModel = viewModel()
                var accepted by remember { mutableStateOf(false) }

                if (!accepted) {
                    IncomingCallScreen(
                        peerDisplayName = peerDisplayName,
                        callTypeLabel = callType.name.lowercase(),
                        onAccept = {
                            callViewModel.acceptCall(callId)
                            accepted = true
                        },
                        onReject = {
                            callViewModel.hangUpCall(callId)
                            finish()
                        }
                    )
                } else {
                    CallScreen(
                        peerJamiId = peerJamiId,
                        peerDisplayName = peerDisplayName,
                        callType = callType,
                        isOutgoing = false,
                        onCallEnded = { finish() },
                        viewModel = callViewModel
                    )
                }
            }
        }
    }

    private fun showOverLockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }
    }

    companion object {
        const val EXTRA_JAMI_ID = "extra_jami_id"
        const val EXTRA_DISPLAY_NAME = "extra_display_name"
        const val EXTRA_CALL_ID = "extra_call_id"
        const val EXTRA_CALL_TYPE = "extra_call_type"
    }
}
