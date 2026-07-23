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

package org.meshly.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import org.meshly.app.service.ToxDaemonService
import org.meshly.app.ui.navigation.MeshlyNavHost
import org.meshly.app.ui.theme.MeshlyTheme

class MainActivity : ComponentActivity() {

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }

    /** Set when the activity is opened from an incoming-message notification's tap target. */
    private var pendingChatDeepLink by mutableStateOf<Pair<String, String>?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        pendingChatDeepLink = extractDeepLink(intent)
        requestRuntimePermissions()
        startDaemonService()

        setContent {
            MeshlyTheme {
                MeshlyNavHost(
                    pendingChatDeepLink = pendingChatDeepLink,
                    onDeepLinkConsumed = { pendingChatDeepLink = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingChatDeepLink = extractDeepLink(intent)
    }

    private fun extractDeepLink(intent: Intent?): Pair<String, String>? {
        val toxId = intent?.getStringExtra(EXTRA_DEEPLINK_TOX_ID) ?: return null
        val displayName = intent.getStringExtra(EXTRA_DEEPLINK_DISPLAY_NAME) ?: toxId
        return toxId to displayName
    }

    private fun startDaemonService() {
        val intent = Intent(this, ToxDaemonService::class.java)
        ContextCompat.startForegroundService(this, intent)
    }

    private fun requestRuntimePermissions() {
        val permissions = mutableListOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    companion object {
        const val EXTRA_DEEPLINK_TOX_ID = "deep_link_tox_id"
        const val EXTRA_DEEPLINK_DISPLAY_NAME = "deep_link_display_name"
    }
}
