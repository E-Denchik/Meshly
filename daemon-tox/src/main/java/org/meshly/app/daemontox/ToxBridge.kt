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
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Real c-toxcore/ToxAV engine, replacing [org.meshly.app.core.ToxBridge]'s
 * Phase 1 mock now that :daemon-tox is actually built (see
 * /PHASE2_BUILD_TOX.md) and wired into :app.
 *
 * NOTE: this is a DIFFERENT class from `org.meshly.app.core.ToxBridge` (the
 * Phase 1 mock, in the `:app` module's own package) despite the identical
 * simple name -- this one lives in package `org.meshly.app.daemontox`
 * inside the `:daemon-tox` module.
 *
 * Every native call below is copied from the real c-toxcore/ToxAV source
 * under native/upstream/c-toxcore (toxcore/tox.h, toxav/toxav.h), not
 * guessed -- see [ToxNative]'s per-function KDoc for exact citations.
 *
 * Threading contract: [iterate] and [avIterate] MUST always be called from
 * the same single thread (see `tox_jni.c`'s top-of-file doc on the shared-
 * JNIEnv callback dispatch design) -- the caller (`ToxDaemonService` in
 * `:app`) is responsible for driving both from one dedicated
 * single-threaded coroutine dispatcher, never `Dispatchers.IO`/`Default`'s
 * pooled threads.
 */
object ToxBridge {

    init {
        System.loadLibrary("toxcore-jni")
    }

    private val _events = MutableSharedFlow<ToxDaemonEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<ToxDaemonEvent> = _events.asSharedFlow()

    private val callbackAdapter = ToxCallbackAdapter(_events)

    /** Opaque `Tox*` handle from [ToxNative.toxNew], or null before [startDaemon] has run. */
    var handle: Long? = null
        private set

    /** Opaque `ToxAV*` handle from [ToxNative.toxavNew], or null before [startAv] has run. */
    var avHandle: Long? = null
        private set

    /**
     * Creates the Tox instance ([ToxNative.toxNew], wrapping `tox_new`),
     * restoring [savedata] if provided (an existing identity) or generating
     * a fresh one otherwise, and registers callbacks
     * ([ToxNative.toxRegisterCallbacks]) so events start flowing into
     * [events] once [iterate] starts running. Does NOT start the iterate
     * loop itself, and does NOT create the ToxAV instance -- call [startAv]
     * separately once this returns (ToxAV requires an already-created Tox
     * instance, toxav.h line 137).
     */
    @Synchronized
    fun startDaemon(savedata: ByteArray? = null) {
        if (handle != null) return
        val h = ToxNative.toxNew(savedata)
        check(h != 0L) { "tox_new failed (see Tox_Err_New; not yet threaded through to Kotlin)" }
        handle = h
        ToxNative.toxRegisterCallbacks(h, callbackAdapter)
    }

    /** Maps to `tox_kill` (see [ToxNative.toxKill]'s doc). Also tears down ToxAV if running. */
    @Synchronized
    fun stopDaemon() {
        avHandle?.let { ToxNative.toxavKill(it) }
        avHandle = null
        val h = handle ?: return
        ToxNative.toxKill(h)
        handle = null
    }

    /**
     * Creates the ToxAV instance ([ToxNative.toxavNew]) and registers its
     * callbacks ([ToxNative.toxavRegisterCallbacks]). Must be called after
     * [startDaemon]. Idempotent.
     */
    @Synchronized
    fun startAv() {
        if (avHandle != null) return
        val av = ToxNative.toxavNew(requireHandle())
        check(av != 0L) { "toxav_new failed (see Toxav_Err_New; not yet threaded through to Kotlin)" }
        avHandle = av
        ToxNative.toxavRegisterCallbacks(av, callbackAdapter)
    }

    /** See [ToxNative.toxGetSavedataSize]/[ToxNative.toxGetSavedata]'s docs. Persist this to restore the identity later via [startDaemon]. */
    fun getSavedata(): ByteArray = ToxNative.toxGetSavedata(requireHandle())

    /** See [ToxNative.toxBootstrap]'s doc. */
    fun bootstrap(host: String, port: Int, publicKey: ByteArray): Boolean =
        ToxNative.toxBootstrap(requireHandle(), host, port, publicKey)

    /** See [ToxNative.toxAddTcpRelay]'s doc. Call alongside [bootstrap] so onion routing has a
     *  TCP fallback path on networks that block or degrade UDP. */
    fun addTcpRelay(host: String, port: Int, publicKey: ByteArray): Boolean =
        ToxNative.toxAddTcpRelay(requireHandle(), host, port, publicKey)

    /** Raw 38-byte Tox address (see [ToxNative.toxSelfGetAddress]'s doc). */
    fun getSelfAddress(): ByteArray = ToxNative.toxSelfGetAddress(requireHandle())

    /** [getSelfAddress] hex-encoded, matching the app's 76-char Tox ID string format. */
    fun getSelfToxId(): String = getSelfAddress().toHex()

    /** See [ToxNative.toxIterationInterval]'s doc. */
    fun iterationIntervalMs(): Int = ToxNative.toxIterationInterval(requireHandle())

    /** See [ToxNative.toxavIterationInterval]'s doc; only valid after [startAv]. */
    fun avIterationIntervalMs(): Int = ToxNative.toxavIterationInterval(requireAvHandle())

    /**
     * Runs one `tox_iterate` step (see [ToxNative.toxIterate]'s doc). The
     * caller (`ToxDaemonService`) is responsible for calling this
     * repeatedly, sleeping [iterationIntervalMs] between calls, always from
     * the same thread as [avIterate] (see this object's threading contract
     * doc above).
     */
    fun iterate() = ToxNative.toxIterate(requireHandle())

    /** Runs one `toxav_iterate` step (see [ToxNative.toxavIterate]'s doc); only valid after [startAv]. */
    fun avIterate() = ToxNative.toxavIterate(requireAvHandle())

    /** Adds a friend by their full 76-char hex Tox ID, sending [message] as the friend request text. Returns the new friend number. */
    fun addFriend(toxId: String, message: String): Int =
        ToxNative.toxFriendAdd(requireHandle(), toxId.fromHex(), message.toByteArray(Charsets.UTF_8))

    /** Accepts an already-received friend request by the peer's 64-char hex public key (see [ToxNative.toxFriendAddNorequest]'s doc). */
    fun addFriendNorequest(publicKeyHex: String): Int =
        ToxNative.toxFriendAddNorequest(requireHandle(), publicKeyHex.fromHex())

    /** See [ToxNative.toxFriendDelete]'s doc. */
    fun deleteFriend(friendNumber: Int): Boolean = ToxNative.toxFriendDelete(requireHandle(), friendNumber)

    /** See [ToxNative.toxFriendSendMessage]'s doc. `messageType` 0 = normal, 1 = action ("/me ..."). Returns the message id used to match a later read-receipt event. */
    fun sendMessage(friendNumber: Int, text: String, messageType: Int = 0): Int =
        ToxNative.toxFriendSendMessage(requireHandle(), friendNumber, messageType, text.toByteArray(Charsets.UTF_8))

    /** See [ToxNative.toxFriendGetConnectionStatus]'s doc. */
    fun friendConnectionStatus(friendNumber: Int): Int =
        ToxNative.toxFriendGetConnectionStatus(requireHandle(), friendNumber)

    /** See [ToxNative.toxSelfGetConnectionStatus]'s doc. */
    fun selfConnectionStatus(): Int = ToxNative.toxSelfGetConnectionStatus(requireHandle())

    // --- ToxAV calls ----------------------------------------------------------------------

    /** See [ToxNative.toxavCall]'s doc. Bit rates in kbit/sec; 0 disables that media type. */
    fun call(friendNumber: Int, audioBitRate: Int, videoBitRate: Int): Boolean =
        ToxNative.toxavCall(requireAvHandle(), friendNumber, audioBitRate, videoBitRate)

    /** See [ToxNative.toxavAnswer]'s doc. */
    fun answer(friendNumber: Int, audioBitRate: Int, videoBitRate: Int): Boolean =
        ToxNative.toxavAnswer(requireAvHandle(), friendNumber, audioBitRate, videoBitRate)

    /** See [ToxNative.toxavCallControl]'s doc for the `control` ordinal mapping. */
    fun callControl(friendNumber: Int, control: Int): Boolean =
        ToxNative.toxavCallControl(requireAvHandle(), friendNumber, control)

    /** See [ToxNative.toxavAudioSetBitRate]'s doc. */
    fun setAudioBitRate(friendNumber: Int, bitRateKbps: Int): Boolean =
        ToxNative.toxavAudioSetBitRate(requireAvHandle(), friendNumber, bitRateKbps)

    /** See [ToxNative.toxavVideoSetBitRate]'s doc. */
    fun setVideoBitRate(friendNumber: Int, bitRateKbps: Int): Boolean =
        ToxNative.toxavVideoSetBitRate(requireAvHandle(), friendNumber, bitRateKbps)

    /** See [ToxNative.toxavAudioSendFrame]'s doc. */
    fun sendAudioFrame(friendNumber: Int, pcm: ShortArray, sampleCount: Int, channels: Int, samplingRate: Int): Boolean =
        ToxNative.toxavAudioSendFrame(requireAvHandle(), friendNumber, pcm, sampleCount, channels, samplingRate)

    /** See [ToxNative.toxavVideoSendFrame]'s doc. Planar YUV420. */
    fun sendVideoFrame(friendNumber: Int, width: Int, height: Int, y: ByteArray, u: ByteArray, v: ByteArray): Boolean =
        ToxNative.toxavVideoSendFrame(requireAvHandle(), friendNumber, width, height, y, u, v)

    private fun requireHandle(): Long =
        checkNotNull(handle) { "ToxBridge.startDaemon() must be called before using the Tox instance" }

    private fun requireAvHandle(): Long =
        checkNotNull(avHandle) { "ToxBridge.startAv() must be called before using the ToxAV instance" }
}

private val HEX_CHARS = "0123456789abcdef".toCharArray()

fun ByteArray.toHex(): String {
    val out = CharArray(size * 2)
    for (i in indices) {
        val v = this[i].toInt() and 0xFF
        out[i * 2] = HEX_CHARS[v ushr 4]
        out[i * 2 + 1] = HEX_CHARS[v and 0x0F]
    }
    return String(out)
}

fun String.fromHex(): ByteArray {
    require(length % 2 == 0) { "hex string must have an even length" }
    val out = ByteArray(length / 2)
    for (i in out.indices) {
        val hi = Character.digit(this[i * 2], 16)
        val lo = Character.digit(this[i * 2 + 1], 16)
        require(hi >= 0 && lo >= 0) { "invalid hex character in \"$this\"" }
        out[i] = ((hi shl 4) or lo).toByte()
    }
    return out
}
