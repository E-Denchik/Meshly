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

package org.meshly.app.daemon

/**
 * Answer to `VideoCallback.getCameraInfo(device, formats, sizes, rates)`. Unlike every other
 * callback in this file, `getCameraInfo` is architecturally different: it's the daemon asking
 * the app to *synchronously fill in* three output collections during the callback itself (the
 * C++ side takes `std::vector<int> *`/`std::vector<unsigned> *` out-pointers, not a return
 * value) — it cannot be modeled as a fire-and-forget entry in [RealJamiEvent]'s `SharedFlow` the
 * way every other signal in this codebase is, because whatever a `SharedFlow` collector does
 * happens later/async, after the native call has already returned with empty vectors.
 *
 * `formats` are `AVPixelFormat`/Android `ImageFormat` integer codes (see `AndroidFormatToAVFormat`
 * in videomanager.i for the handful this daemon build understands: NV21, YUV_420_888,
 * YUV_422_888, FLEX_RGB_888, FLEX_RGBA_8888), `sizes` are packed width/height pairs, `rates` are
 * frame rates — none of the three encodings were confirmed further than "some list of ints" since
 * that requires camera-capture code this scaffolding doesn't include yet (see [RealCameraProvider]'s doc).
 */
data class RealCameraInfo(
    val formats: List<Int> = emptyList(),
    val sizes: List<Int> = emptyList(),
    val rates: List<Int> = emptyList()
)

/**
 * Plugged in by whatever Android camera layer (CameraX/Camera2) eventually backs Meshly's video
 * calls — that layer is real platform capture code, out of scope for this JNI-contract scaffolding
 * pass. Until something sets [RealJamiBridge.cameraProvider], [MeshlyVideoCallback.getCameraInfo]
 * answers with empty lists, which is a legitimate (if useless) answer, not a crash.
 */
fun interface RealCameraProvider {
    fun getCameraInfo(device: String): RealCameraInfo
}
