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

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.meshly.app.R

/**
 * A Tox ID (64-hex public key once a friendship is confirmed, 76-hex full address before that -
 * see [org.meshly.app.data.model.Contact]'s doc) is opaque hex, not prose - both display
 * variants below use [FontFamily.Monospace] (unused anywhere else in the app until now) so hex
 * digits stay visually distinguishable instead of proportional-font kerning making 0/O or 1/l
 * ambiguous.
 */

/**
 * Compact, truncated, single-line reference for list-row subtitles and other secondary contexts
 * - not copyable (the row itself is usually already tappable to open a chat/call, and a
 * full-length ID would dominate a row meant to be scanned quickly). For displaying/sharing a
 * complete ID, use [CopyableToxId] instead.
 */
@Composable
fun ToxIdCompact(
    toxId: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodySmall,
    color: Color = LocalContentColor.current
) {
    Text(
        toxId.truncatedToxId(),
        modifier = modifier,
        style = style.copy(fontFamily = FontFamily.Monospace),
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

/**
 * Full Tox ID with a copy-to-clipboard action, for "this is *your* identity and you need to
 * share it" contexts (own-account settings, the QR dialog, onboarding's own-ID step) - the only
 * places a complete, un-truncated ID actually needs to be visible. Also selectable by long-press
 * ([SelectionContainer]) for anyone who'd rather select+copy manually.
 */
@Composable
fun CopyableToxId(toxId: String, modifier: Modifier = Modifier) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val copiedMessage = stringResource(R.string.tox_id_copied)

    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        SelectionContainer(modifier = Modifier.weight(1f, fill = false)) {
            Text(
                toxId,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
            )
        }
        CopyToxIdButton(
            onCopy = {
                clipboard.setText(AnnotatedString(toxId))
                showCopiedToast(context, copiedMessage)
            },
            modifier = Modifier.padding(start = 4.dp)
        )
    }
}

@Composable
private fun CopyToxIdButton(onCopy: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(onClick = onCopy, modifier = modifier) {
        Icon(Icons.Filled.ContentCopy, contentDescription = stringResource(R.string.content_desc_copy_tox_id))
    }
}

private fun showCopiedToast(context: Context, message: String) {
    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
}

private fun String.truncatedToxId(): String =
    if (length <= 20) this else "${take(8)}…${takeLast(8)}"
