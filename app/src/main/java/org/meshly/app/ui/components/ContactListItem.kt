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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import org.meshly.app.ui.theme.Spacing

/**
 * One avatar+name+subtitle row, shared by every contact-ish list in the app (`ChatsListScreen`,
 * `ContactListScreen`, `CallsScreen`) - previously each screen reimplemented its own near-
 * identical `ConversationRow`/`ContactRow`/`DialRow`, which had let the Tox ID subtitle drift
 * out of sync between them (different truncation, one missing `ToxIdCompact`'s monospace font
 * entirely, etc). Only the trailing content (unread badge, accept/reject buttons, call icons)
 * still varies per screen, via [trailing].
 */
@Composable
fun ContactListItem(
    displayName: String,
    toxId: String,
    modifier: Modifier = Modifier,
    avatarSize: Dp = AvatarSize.Small,
    showOnlineIndicator: Boolean = false,
    onlineContentDescription: String? = null,
    /** Replaces the Tox ID subtitle entirely when non-null (e.g. `CallsScreen`'s "offline"
     *  hint) - for anything other than showing the ID itself. */
    subtitleOverride: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit = {}
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f, fill = false)) {
            Avatar(
                name = displayName,
                seed = toxId,
                size = avatarSize,
                showOnlineIndicator = showOnlineIndicator,
                onlineContentDescription = onlineContentDescription
            )
            Column(modifier = Modifier.padding(start = Spacing.md)) {
                Text(
                    displayName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                when {
                    subtitleOverride != null -> Text(
                        subtitleOverride,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    // A freshly-sent outgoing request has no nickname yet, so displayName IS
                    // toxId (see ContactViewModel.addContactRequest) - showing the same string
                    // twice in one row was never useful.
                    displayName != toxId -> ToxIdCompact(toxId, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, content = trailing)
    }
}
