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

import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * Receives native Tox/ToxAV callbacks and turns them into [ToxDaemonEvent]
 * emissions. This is architecturally DIFFERENT from the removed jami-daemon
 * scaffold's `JamiCallbackAdapter.kt`, and that difference is the point of
 * this file's doc, not an oversight:
 *
 * jami-daemon's SWIG binding generates real Java "director" base classes
 * (`Callback`, `ConfigurationCallback`, ...) that a Kotlin class could
 * directly `extend` and `override fun` on -- SWIG's C++ glue handled
 * routing a virtual-method call from C++ into the JVM automatically.
 *
 * c-toxcore has no such mechanism: `tox_callback_friend_message(Tox *tox,
 * tox_friend_message_cb *callback)` (tox.h line 1508, confirmed) takes a
 * plain C function pointer, not an object. The standard hand-written-JNI
 * pattern to bridge that into Kotlin (documented in `tox_jni.c`'s doc
 * comment, and NOT yet actually implemented in this scaffolding pass) is:
 *
 * 1. When the Tox instance is created (or in a dedicated
 *    `toxRegisterCallbacks` JNI function, not yet written), the C code
 *    calls `(*env)->NewGlobalRef(env, thiz)` on the Kotlin object that
 *    should receive callbacks (this object) and stores that global ref
 *    somewhere reachable from the static C callback functions -- typically
 *    alongside the `Tox*` handle, e.g. in a small native-side struct.
 * 2. The C code calls e.g. `tox_callback_friend_message(tox,
 *    my_static_c_friend_message_cb)`, where `my_static_c_friend_message_cb`
 *    is a plain C function matching `tox_friend_message_cb`'s signature.
 * 3. When c-toxcore invokes that C callback (from inside [ToxNative.toxIterate]),
 *    the C function looks up the stored global ref and calls
 *    `(*env)->CallVoidMethod(env, dispatcher, onFriendMessageMethodID, ...)`
 *    -- `onFriendMessageMethodID` obtained once via `GetMethodID` and
 *    cached, matching this object's [onFriendMessage] method below.
 * 4. This object's methods just wrap the raw args into a [ToxDaemonEvent]
 *    and emit it onto the shared flow -- no native code lives here, all the
 *    JNI plumbing is on the C side in step 1-3 (not yet written).
 *
 * Each method below is named/shaped to match the `tox_*_cb`/`toxav_*_cb`
 * typedef it corresponds to -- see [ToxDaemonEvent]'s doc for the exact
 * tox.h/toxav.h citations, not repeated here to avoid duplication.
 */
internal class ToxCallbackAdapter(private val events: MutableSharedFlow<ToxDaemonEvent>) {

    fun onSelfConnectionStatus(status: Int) {
        events.tryEmit(ToxDaemonEvent.SelfConnectionStatusChanged(status))
    }

    fun onFriendRequest(publicKey: ByteArray, message: ByteArray) {
        events.tryEmit(ToxDaemonEvent.FriendRequestReceived(publicKey, message))
    }

    fun onFriendMessage(friendNumber: Int, messageType: Int, message: ByteArray) {
        events.tryEmit(ToxDaemonEvent.FriendMessageReceived(friendNumber, messageType, message))
    }

    fun onFriendReadReceipt(friendNumber: Int, messageId: Int) {
        events.tryEmit(ToxDaemonEvent.FriendReadReceipt(friendNumber, messageId))
    }

    fun onFriendConnectionStatus(friendNumber: Int, connectionStatus: Int) {
        events.tryEmit(ToxDaemonEvent.FriendConnectionStatusChanged(friendNumber, connectionStatus))
    }

    fun onFriendName(friendNumber: Int, name: ByteArray) {
        events.tryEmit(ToxDaemonEvent.FriendNameChanged(friendNumber, name))
    }

    fun onFriendStatusMessage(friendNumber: Int, statusMessage: ByteArray) {
        events.tryEmit(ToxDaemonEvent.FriendStatusMessageChanged(friendNumber, statusMessage))
    }

    fun onFriendStatus(friendNumber: Int, status: Int) {
        events.tryEmit(ToxDaemonEvent.FriendUserStatusChanged(friendNumber, status))
    }

    fun onFriendTyping(friendNumber: Int, isTyping: Boolean) {
        events.tryEmit(ToxDaemonEvent.FriendTypingChanged(friendNumber, isTyping))
    }

    // --- ToxAV -----------------------------------------------------------------------------

    fun onCallInvite(friendNumber: Int, audioEnabled: Boolean, videoEnabled: Boolean) {
        events.tryEmit(ToxDaemonEvent.CallInviteReceived(friendNumber, audioEnabled, videoEnabled))
    }

    fun onCallState(friendNumber: Int, stateBitmask: Int) {
        events.tryEmit(ToxDaemonEvent.CallStateChanged(friendNumber, stateBitmask))
    }

    fun onAudioBitRate(friendNumber: Int, bitRateKbps: Int) {
        events.tryEmit(ToxDaemonEvent.AudioBitRateSuggested(friendNumber, bitRateKbps))
    }

    fun onVideoBitRate(friendNumber: Int, bitRateKbps: Int) {
        events.tryEmit(ToxDaemonEvent.VideoBitRateSuggested(friendNumber, bitRateKbps))
    }

    fun onAudioFrame(friendNumber: Int, pcm: ShortArray, sampleCount: Int, channels: Int, samplingRate: Int) {
        events.tryEmit(ToxDaemonEvent.AudioFrameReceived(friendNumber, pcm, sampleCount, channels, samplingRate))
    }

    fun onVideoFrame(
        friendNumber: Int,
        width: Int,
        height: Int,
        y: ByteArray,
        u: ByteArray,
        v: ByteArray,
        yStride: Int,
        uStride: Int,
        vStride: Int
    ) {
        events.tryEmit(
            ToxDaemonEvent.VideoFrameReceived(friendNumber, width, height, y, u, v, yStride, uStride, vStride)
        )
    }
}
