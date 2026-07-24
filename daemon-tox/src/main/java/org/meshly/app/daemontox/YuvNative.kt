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

package org.meshly.app.daemontox

/**
 * Native YUV420/I420 pixel-format conversions for the call video pipeline
 * ([org.meshly.app.media.YuvFrameConverter] in `:app`). Colocated here (not in `:app`) purely
 * because `:daemon-tox` is the only module with NDK/CMake build wiring - this has nothing to
 * do with the Tox protocol, unlike everything else in this package. See `yuv_convert.c`'s
 * top-of-file doc for why these run in C rather than Kotlin (per-pixel conversion loops at
 * video frame rates were the dominant source of visible call lag).
 */
object YuvNative {

    init {
        System.loadLibrary("toxcore-jni")
    }

    /**
     * Rotates a single YUV byte plane ([width] x [height]) by [rotationDegrees] (90/180/270 -
     * 0 isn't supported, callers should just reuse [src] directly in that case) into [dst],
     * which must already be sized correctly: `width*height` bytes, same as [src] (rotation
     * doesn't change element count, only their arrangement).
     */
    external fun rotatePlane(src: ByteArray, width: Int, height: Int, rotationDegrees: Int, dst: ByteArray)

    /**
     * Converts a planar YUV420 frame into ARGB_8888 pixels, written directly into [out]
     * (`width*height` ints) - caller-owned and reused across frames so rendering incoming
     * video doesn't allocate on the hot path. `yStride`/`uStride`/`vStride` may be negative
     * (see [ToxDaemonEvent.VideoFrameReceived]'s doc); only their magnitude is used.
     */
    external fun yuv420ToArgb(
        width: Int,
        height: Int,
        y: ByteArray,
        u: ByteArray,
        v: ByteArray,
        yStride: Int,
        uStride: Int,
        vStride: Int,
        out: IntArray
    )
}
