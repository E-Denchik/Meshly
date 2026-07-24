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

package org.meshly.app.media

import android.graphics.Bitmap
import androidx.camera.core.ImageProxy
import kotlin.math.abs

/** Tightly-packed planar I420 (`y.size == width*height`, `u.size == v.size ==
 *  (width/2)*(height/2)`, no per-row padding) - the exact layout
 *  `toxav_video_send_frame` expects (see `ToxNative.toxavVideoSendFrame`'s doc). */
data class I420Frame(val width: Int, val height: Int, val y: ByteArray, val u: ByteArray, val v: ByteArray)

/**
 * Pure YUV420/I420 <-> pixel conversions used by the call video pipeline. No Android
 * lifecycle/camera state lives here - [VideoCallSession] owns capture/CameraX, this just
 * converts buffers, so it's trivially testable in isolation.
 */
object YuvFrameConverter {

    /**
     * Converts a CameraX `YUV_420_888` [ImageProxy] into a tightly-packed [I420Frame], rotated
     * by [rotationDegrees] (from `imageInfo.rotationDegrees`) so the frame the peer receives is
     * upright regardless of how the device/sensor is oriented - CameraX's `ImageAnalysis`
     * doesn't rotate pixel data for you, only reports the needed rotation.
     *
     * `YUV_420_888`'s U/V planes are frequently semi-planar on real devices (`pixelStride == 2`,
     * i.e. interleaved like NV21) rather than the fully-planar layout ToxAV needs - [copyPlane]
     * de-interleaves by stepping `pixelStride` bytes at a time when it isn't 1.
     */
    fun imageProxyToI420(image: ImageProxy, rotationDegrees: Int): I420Frame {
        val srcWidth = image.width
        val srcHeight = image.height
        val planes = image.planes

        val yRaw = ByteArray(srcWidth * srcHeight)
        copyPlane(planes[0].buffer, planes[0].rowStride, planes[0].pixelStride, srcWidth, srcHeight, yRaw)

        val chromaWidth = srcWidth / 2
        val chromaHeight = srcHeight / 2
        val uRaw = ByteArray(chromaWidth * chromaHeight)
        val vRaw = ByteArray(chromaWidth * chromaHeight)
        copyPlane(planes[1].buffer, planes[1].rowStride, planes[1].pixelStride, chromaWidth, chromaHeight, uRaw)
        copyPlane(planes[2].buffer, planes[2].rowStride, planes[2].pixelStride, chromaWidth, chromaHeight, vRaw)

        val yFinal = rotatePlane(yRaw, srcWidth, srcHeight, rotationDegrees)
        val uFinal = rotatePlane(uRaw, chromaWidth, chromaHeight, rotationDegrees)
        val vFinal = rotatePlane(vRaw, chromaWidth, chromaHeight, rotationDegrees)

        val rotated90or270 = rotationDegrees == 90 || rotationDegrees == 270
        val finalWidth = if (rotated90or270) srcHeight else srcWidth
        val finalHeight = if (rotated90or270) srcWidth else srcHeight

        return I420Frame(finalWidth, finalHeight, yFinal, uFinal, vFinal)
    }

    private fun copyPlane(
        buffer: java.nio.ByteBuffer,
        rowStride: Int,
        pixelStride: Int,
        width: Int,
        height: Int,
        out: ByteArray
    ) {
        val dup = buffer.duplicate()
        val rowBuf = ByteArray(rowStride)
        var outPos = 0
        for (row in 0 until height) {
            dup.position(row * rowStride)
            val available = dup.remaining().coerceAtMost(rowStride)
            dup.get(rowBuf, 0, available)
            if (pixelStride == 1) {
                System.arraycopy(rowBuf, 0, out, outPos, width)
                outPos += width
            } else {
                var srcIdx = 0
                repeat(width) {
                    out[outPos++] = rowBuf[srcIdx]
                    srcIdx += pixelStride
                }
            }
        }
    }

    private fun rotatePlane(src: ByteArray, width: Int, height: Int, rotationDegrees: Int): ByteArray =
        when (rotationDegrees) {
            90 -> ByteArray(width * height).also { dst ->
                var i = 0
                for (x in 0 until width) {
                    for (y in height - 1 downTo 0) {
                        dst[i++] = src[y * width + x]
                    }
                }
            }
            180 -> ByteArray(width * height).also { dst ->
                for (i in src.indices) dst[i] = src[src.size - 1 - i]
            }
            270 -> ByteArray(width * height).also { dst ->
                var i = 0
                for (x in width - 1 downTo 0) {
                    for (y in 0 until height) {
                        dst[i++] = src[y * width + x]
                    }
                }
            }
            else -> src
        }

    /**
     * Renders a planar YUV420 frame (as delivered by [org.meshly.app.daemontox.ToxDaemonEvent.VideoFrameReceived])
     * into [target], reallocating it only if [width]/[height] changed since the last call.
     * `yStride`/`uStride`/`vStride` can be negative for bottom-up source images (see that
     * event's doc) - only their magnitude is needed here since the byte arrays we're handed are
     * already laid out row-major forward by the JNI layer (`tox_jni.c`'s `cb_video_receive_frame`).
     * Standard BT.601 integer YUV->RGB conversion, processed row by row (chroma sampled every
     * 2x2 luma block).
     */
    fun renderToBitmap(
        width: Int,
        height: Int,
        y: ByteArray,
        u: ByteArray,
        v: ByteArray,
        yStride: Int,
        uStride: Int,
        vStride: Int,
        target: Bitmap?
    ): Bitmap {
        val bitmap = if (target != null && target.width == width && target.height == height) {
            target
        } else {
            target?.recycle()
            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        }

        val yRowStride = abs(yStride).coerceAtLeast(width)
        val uRowStride = abs(uStride).coerceAtLeast(width / 2)
        val vRowStride = abs(vStride).coerceAtLeast(width / 2)

        val pixels = IntArray(width * height)
        for (row in 0 until height) {
            val yRowOffset = row * yRowStride
            val chromaRow = row / 2
            val uRowOffset = chromaRow * uRowStride
            val vRowOffset = chromaRow * vRowStride
            var outOffset = row * width
            for (col in 0 until width) {
                val yValue = (y[yRowOffset + col].toInt() and 0xFF) - 16
                val uValue = (u[uRowOffset + col / 2].toInt() and 0xFF) - 128
                val vValue = (v[vRowOffset + col / 2].toInt() and 0xFF) - 128

                val r = ((1192 * yValue + 1634 * vValue) shr 10).coerceIn(0, 255)
                val g = ((1192 * yValue - 833 * vValue - 400 * uValue) shr 10).coerceIn(0, 255)
                val b = ((1192 * yValue + 2066 * uValue) shr 10).coerceIn(0, 255)

                pixels[outOffset++] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
        }
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return bitmap
    }
}
