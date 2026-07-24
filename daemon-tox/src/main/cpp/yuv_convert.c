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

/*
 * YUV420/I420 pixel-format conversions for the call video pipeline
 * (org.meshly.app.media.YuvFrameConverter in :app, called via
 * org.meshly.app.daemontox.YuvNative). Colocated in :daemon-tox purely because this is the
 * only module with NDK/CMake build wiring - not a Tox protocol concern, unlike everything else
 * in this directory.
 *
 * Native rather than Kotlin: both the per-pixel YUV->RGB conversion (rendering incoming video)
 * and the plane rotation (orienting outgoing camera frames upright) are tight, scatter/gather
 * pixel loops running once per video frame - at 640x480@15fps that's ~2.3M pixel operations/sec
 * per direction. Doing that in interpreted/JIT'd Kotlin was the dominant source of visible call
 * lag (frames queuing up behind slow conversion); plain compiled C handles it comfortably.
 */

#include <jni.h>
#include <stdint.h>

JNIEXPORT void JNICALL
Java_org_meshly_app_daemontox_YuvNative_rotatePlane(
    JNIEnv *env, jclass clazz,
    jbyteArray src_array, jint width, jint height, jint rotation_degrees,
    jbyteArray dst_array
) {
    (void) clazz;
    jbyte *src = (*env)->GetByteArrayElements(env, src_array, NULL);
    jbyte *dst = (*env)->GetByteArrayElements(env, dst_array, NULL);
    if (src == NULL || dst == NULL) {
        if (src != NULL) (*env)->ReleaseByteArrayElements(env, src_array, src, JNI_ABORT);
        if (dst != NULL) (*env)->ReleaseByteArrayElements(env, dst_array, dst, JNI_ABORT);
        return;
    }

    const int32_t w = (int32_t) width;
    const int32_t h = (int32_t) height;

    if (rotation_degrees == 90) {
        int32_t i = 0;
        for (int32_t x = 0; x < w; x++) {
            for (int32_t y = h - 1; y >= 0; y--) {
                dst[i++] = src[y * w + x];
            }
        }
    } else if (rotation_degrees == 180) {
        const int32_t total = w * h;
        for (int32_t i = 0; i < total; i++) {
            dst[i] = src[total - 1 - i];
        }
    } else if (rotation_degrees == 270) {
        int32_t i = 0;
        for (int32_t x = w - 1; x >= 0; x--) {
            for (int32_t y = 0; y < h; y++) {
                dst[i++] = src[y * w + x];
            }
        }
    }
    /* rotation_degrees == 0: caller shouldn't invoke this at all (see YuvFrameConverter.kt),
     * but leave dst untouched rather than guess if it somehow does. */

    (*env)->ReleaseByteArrayElements(env, src_array, src, JNI_ABORT);
    (*env)->ReleaseByteArrayElements(env, dst_array, dst, 0);
}

JNIEXPORT void JNICALL
Java_org_meshly_app_daemontox_YuvNative_yuv420ToArgb(
    JNIEnv *env, jclass clazz,
    jint width, jint height,
    jbyteArray y_array, jbyteArray u_array, jbyteArray v_array,
    jint y_stride, jint u_stride, jint v_stride,
    jintArray out_array
) {
    (void) clazz;
    jbyte *y = (*env)->GetByteArrayElements(env, y_array, NULL);
    jbyte *u = (*env)->GetByteArrayElements(env, u_array, NULL);
    jbyte *v = (*env)->GetByteArrayElements(env, v_array, NULL);
    jint *out = (*env)->GetIntArrayElements(env, out_array, NULL);
    if (y == NULL || u == NULL || v == NULL || out == NULL) {
        if (y != NULL) (*env)->ReleaseByteArrayElements(env, y_array, y, JNI_ABORT);
        if (u != NULL) (*env)->ReleaseByteArrayElements(env, u_array, u, JNI_ABORT);
        if (v != NULL) (*env)->ReleaseByteArrayElements(env, v_array, v, JNI_ABORT);
        if (out != NULL) (*env)->ReleaseIntArrayElements(env, out_array, out, JNI_ABORT);
        return;
    }

    const int32_t w = (int32_t) width;
    const int32_t h = (int32_t) height;

    int32_t y_row_stride = (int32_t) y_stride;
    if (y_row_stride < 0) y_row_stride = -y_row_stride;
    if (y_row_stride < w) y_row_stride = w;

    int32_t u_row_stride = (int32_t) u_stride;
    if (u_row_stride < 0) u_row_stride = -u_row_stride;
    if (u_row_stride < w / 2) u_row_stride = w / 2;

    int32_t v_row_stride = (int32_t) v_stride;
    if (v_row_stride < 0) v_row_stride = -v_row_stride;
    if (v_row_stride < w / 2) v_row_stride = w / 2;

    for (int32_t row = 0; row < h; row++) {
        const uint8_t *y_row = (const uint8_t *) y + (size_t) row * y_row_stride;
        const uint8_t *u_row = (const uint8_t *) u + (size_t) (row / 2) * u_row_stride;
        const uint8_t *v_row = (const uint8_t *) v + (size_t) (row / 2) * v_row_stride;
        jint *out_row = out + (size_t) row * w;

        for (int32_t col = 0; col < w; col++) {
            const int32_t y_value = (int32_t) y_row[col] - 16;
            const int32_t u_value = (int32_t) u_row[col / 2] - 128;
            const int32_t v_value = (int32_t) v_row[col / 2] - 128;

            int32_t r = (1192 * y_value + 1634 * v_value) >> 10;
            int32_t g = (1192 * y_value - 833 * v_value - 400 * u_value) >> 10;
            int32_t b = (1192 * y_value + 2066 * u_value) >> 10;

            if (r < 0) r = 0; else if (r > 255) r = 255;
            if (g < 0) g = 0; else if (g > 255) g = 255;
            if (b < 0) b = 0; else if (b > 255) b = 255;

            out_row[col] = (jint) (0xFF000000u | ((uint32_t) r << 16) | ((uint32_t) g << 8) | (uint32_t) b);
        }
    }

    (*env)->ReleaseByteArrayElements(env, y_array, y, JNI_ABORT);
    (*env)->ReleaseByteArrayElements(env, u_array, u, JNI_ABORT);
    (*env)->ReleaseByteArrayElements(env, v_array, v, JNI_ABORT);
    (*env)->ReleaseIntArrayElements(env, out_array, out, 0);
}
