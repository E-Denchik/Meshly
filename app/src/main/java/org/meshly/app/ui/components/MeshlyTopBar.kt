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

package org.meshly.app.ui.components

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import org.meshly.app.R

/**
 * The app's one [TopAppBar] shape, used by every top-level screen (the 4 bottom-tab roots and
 * [org.meshly.app.ui.chat.ChatScreen]) instead of each declaring its own - before this, all of
 * them independently rebuilt the same bare `TopAppBar(title = { Text(...) })`, so title styling
 * and back-button treatment had quietly drifted (only `ChatScreen` had a back arrow at all).
 * Call screens ([org.meshly.app.ui.call.CallScreen]/`IncomingCallScreen`) deliberately don't use
 * this - their headerless, full-bleed dark surface is a different, intentional pattern.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeshlyTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    TopAppBar(
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.content_desc_back))
                }
            }
        },
        actions = actions
    )
}
