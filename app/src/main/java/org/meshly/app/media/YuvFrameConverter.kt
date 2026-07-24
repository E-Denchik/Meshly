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
import org.meshly.app.daemontox.YuvNative

/** Tightly-packed planar I420 (`y.size == width*height`, `u.size == v.size ==
 *  (width/2)*(height/2)`, no per-row padding) - the exact layout
 *  `toxav_video_send_frame` expects (see `ToxNative.toxavVideoSendFrame`'s doc). The backing
 *  arrays are [YuvFrameConverter.SendBuffers]-owned scratch space, valid only until the next
 *  [YuvFrameConverter.imageProxyToI420] call - callers must finish using one frame (e.g. hand
 *  it to `toxav_video_send_frame`) before requesting the next. */
data class I420Frame(val width: Int, val height: Int, val y: ByteArray, val u: ByteArray, val v: ByteArray)

/**
 * Pure YUV420/I420 <-> pixel conversions used by the call video pipeline. No Android
 * lifecycle/camera state lives here - [VideoCallSession] owns capture/CameraX, this just
 * converts buffers, so it's trivially testable in isolation.
 *
 * Both directions take caller-owned, reused scratch buffers ([SendBuffers]/[ReceiveBuffers])
 * instead of allocating fresh arrays per frame - at video frame rates, per-frame allocation
 * churn was a real source of GC pressure and visible call lag (a growing backlog of stale,
 * queued frames as processing fell behind arrival rate). The actual pixel math runs in native
 * code ([YuvNative]) for the same reason: interpreted/JIT'd Kotlin per-pixel loops couldn't
 * keep up in real time at 640x480@15fps.
 */
object YuvFrameConverter {

    /** Scratch buffers for [imageProxyToI420], one instance per capture session. Resized only
     *  when the camera's frame dimensions actually change (they don't, frame to frame), not on
     *  every call. */
    class SendBuffers {
        var y = ByteArray(0)
        var u = ByteArray(0)
        var v = ByteArray(0)
        var rotatedY = ByteArray(0)
        var rotatedU = ByteArray(0)
        var rotatedV = ByteArray(0)
    }

    /** Scratch buffers for [renderToBitmap], one instance per remote-video render target. */
    class ReceiveBuffers {
        var pixels = IntArray(0)
        var bitmap: Bitmap? = null
    }

    /**
     * Converts a CameraX `YUV_420_888` [ImageProxy] into a tightly-packed [I420Frame] (backed
     * by [buffers]' scratch arrays), rotated by [rotationDegrees] (from
     * `imageInfo.rotationDegrees`) so the frame the peer receives is upright regardless of how
     * the device/sensor is oriented - CameraX's `ImageAnalysis` doesn't rotate pixel data for
     * you, only reports the needed rotation.
     *
     * `YUV_420_888`'s U/V planes are frequently semi-planar on real devices (`pixelStride == 2`,
     * i.e. interleaved like NV21) rather than the fully-planar layout ToxAV needs - [copyPlane]
     * de-interleaves by stepping `pixelStride` bytes at a time when it isn't 1.
     */
    fun imageProxyToI420(image: ImageProxy, rotationDegrees: Int, buffers: SendBuffers): I420Frame {
        val srcWidth = image.width
        val srcHeight = image.height
        val planes = image.planes
        val chromaWidth = srcWidth / 2
        val chromaHeight = srcHeight / 2

        buffers.y = buffers.y.resized(srcWidth * srcHeight)
        buffers.u = buffers.u.resized(chromaWidth * chromaHeight)
        buffers.v = buffers.v.resized(chromaWidth * chromaHeight)

        copyPlane(planes[0].buffer, planes[0].rowStride, planes[0].pixelStride, srcWidth, srcHeight, buffers.y)
        copyPlane(planes[1].buffer, planes[1].rowStride, planes[1].pixelStride, chromaWidth, chromaHeight, buffers.u)
        copyPlane(planes[2].buffer, planes[2].rowStride, planes[2].pixelStride, chromaWidth, chromaHeight, buffers.v)

        if (rotationDegrees == 0) {
            return I420Frame(srcWidth, srcHeight, buffers.y, buffers.u, buffers.v)
        }

        buffers.rotatedY = buffers.rotatedY.resized(srcWidth * srcHeight)
        buffers.rotatedU = buffers.rotatedU.resized(chromaWidth * chromaHeight)
        buffers.rotatedV = buffers.rotatedV.resized(chromaWidth * chromaHeight)

        YuvNative.rotatePlane(buffers.y, srcWidth, srcHeight, rotationDegrees, buffers.rotatedY)
        YuvNative.rotatePlane(buffers.u, chromaWidth, chromaHeight, rotationDegrees, buffers.rotatedU)
        YuvNative.rotatePlane(buffers.v, chromaWidth, chromaHeight, rotationDegrees, buffers.rotatedV)

        val rotated90or270 = rotationDegrees == 90 || rotationDegrees == 270
        val finalWidth = if (rotated90or270) srcHeight else srcWidth
        val finalHeight = if (rotated90or270) srcWidth else srcHeight

        return I420Frame(finalWidth, finalHeight, buffers.rotatedY, buffers.rotatedU, buffers.rotatedV)
    }

    private fun ByteArray.resized(size: Int): ByteArray = if (this.size == size) this else ByteArray(size)

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

    /**
     * Renders a planar YUV420 frame (as delivered by [org.meshly.app.daemontox.ToxDaemonEvent.VideoFrameReceived])
     * into [buffers]' reused `Bitmap`/pixel array, reallocating either only if [width]/[height]
     * changed since the last call. `yStride`/`uStride`/`vStride` can be negative for bottom-up
     * source images (see that event's doc) - only their magnitude is needed here since the byte
     * arrays we're handed are already laid out row-major forward by the JNI layer
     * (`tox_jni.c`'s `cb_video_receive_frame`). BT.601 YUV->RGB conversion runs natively (see
     * [YuvNative.yuv420ToArgb]'s doc).
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
        buffers: ReceiveBuffers
    ): Bitmap {
        val existing = buffers.bitmap
        val bitmap = if (existing != null && existing.width == width && existing.height == height) {
            existing
        } else {
            existing?.recycle()
            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        }
        buffers.bitmap = bitmap

        if (buffers.pixels.size != width * height) {
            buffers.pixels = IntArray(width * height)
        }

        YuvNative.yuv420ToArgb(width, height, y, u, v, yStride, uStride, vStride, buffers.pixels)
        bitmap.setPixels(buffers.pixels, 0, width, 0, 0, width, height)
        return bitmap
    }
}
