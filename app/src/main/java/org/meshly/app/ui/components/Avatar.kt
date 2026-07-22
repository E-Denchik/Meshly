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

package org.meshly.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.meshly.app.ui.theme.AvatarPalette
import kotlin.math.abs

/**
 * A circular initials avatar with a color deterministically derived from [seed] (typically the
 * contact's Jami ID) so the same person always renders the same color everywhere in the app,
 * without needing a real profile picture - which pure P2P identities don't have by default.
 */
@Composable
fun Avatar(
    name: String,
    seed: String,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    showOnlineIndicator: Boolean = false,
    onlineContentDescription: String? = null
) {
    val background = avatarColorFor(seed)
    val initial = name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"

    Box(modifier = modifier.size(size)) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(background),
            contentAlignment = Alignment.Center
        ) {
            Text(
                initial,
                color = Color.White,
                fontSize = (size.value * 0.4).sp
            )
        }
        if (showOnlineIndicator) {
            val dotSize = size * 0.32f
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(dotSize + 3.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface)
                    .clearAndSetSemantics { }
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(dotSize)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.tertiary)
                    .let {
                        if (onlineContentDescription != null) {
                            it.semantics { contentDescription = onlineContentDescription }
                        } else {
                            it
                        }
                    }
            )
        }
    }
}

private fun avatarColorFor(seed: String): Color {
    val index = abs(seed.hashCode()) % AvatarPalette.size
    return AvatarPalette[index]
}
