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

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import org.meshly.app.R

/**
 * Renders [content] (typically the account's own 76-char Tox ID) as a black-and-white QR code,
 * so it can be scanned by another device running the same add-by-QR flow. Encoding is
 * synchronous - a 512x512 QR bitmap is cheap enough to compute inline via [remember], no
 * coroutine hop needed.
 */
@Composable
fun QrCodeImage(content: String, modifier: Modifier = Modifier, sizePx: Int = 512) {
    val bitmap = remember(content, sizePx) { content.toQrBitmap(sizePx) }
    Image(
        bitmap = bitmap.asImageBitmap(),
        // A generic description, not the raw ID - a screen reader shouldn't read out a 76-char
        // hex string just to announce "there's a QR code here" (the ID itself is already shown
        // as accessible text nearby via CopyableToxId, wherever this is used).
        contentDescription = stringResource(R.string.tox_id_qr_content_description),
        modifier = modifier.size(240.dp)
    )
}

private fun String.toQrBitmap(sizePx: Int): Bitmap {
    val matrix = QRCodeWriter().encode(this, BarcodeFormat.QR_CODE, sizePx, sizePx)
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    for (x in 0 until sizePx) {
        for (y in 0 until sizePx) {
            bitmap.setPixel(x, y, if (matrix.get(x, y)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt())
        }
    }
    return bitmap
}
