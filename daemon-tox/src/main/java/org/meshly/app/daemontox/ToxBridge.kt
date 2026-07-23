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
 * Phase 1 mock once :daemon-tox is actually built (see
 * /PHASE2_BUILD_TOX.md) and wired into :app.
 *
 * NOTE: this is a DIFFERENT class from `org.meshly.app.core.ToxBridge` (the
 * Phase 1 mock, in the `:app` module's own package) despite the identical
 * simple name -- this one lives in package `org.meshly.app.daemontox`
 * inside the `:daemon-tox` module, which isn't wired into `:app`'s
 * dependencies, so there's no import collision in practice. Matches the
 * removed jami-daemon scaffold's `RealJamiBridge` vs. the mock's
 * `JamiBridge` naming pattern, just with both sides now sharing the same
 * simple name since the Phase 1 mock was renamed to `ToxBridge` too.
 *
 * Every native call below is copied from the real c-toxcore/ToxAV source
 * under native/upstream/c-toxcore (toxcore/tox.h, toxav/toxav.h), not
 * guessed -- see [ToxNative]'s per-function KDoc for exact citations.
 */
object ToxBridge {

    init {
        System.loadLibrary("toxcore-jni")
    }

    private val _events = MutableSharedFlow<ToxDaemonEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<ToxDaemonEvent> = _events.asSharedFlow()

    private val callbackAdapter = ToxCallbackAdapter(_events)

    /**
     * Opaque `Tox*` handle from [ToxNative.toxNew], or null before
     * [startDaemon] has run. Exposed as `internal` rather than `private`
     * only so a future test/inspection hook in this module can read it;
     * :app never sees this since :daemon-tox isn't a dependency.
     */
    internal var handle: Long? = null
        private set

    /**
     * Creates the Tox instance ([ToxNative.toxNew], wrapping `tox_new` --
     * see that KDoc) and would register callbacks via a
     * `toxRegisterCallbacks`-style JNI call against [callbackAdapter] (not
     * yet implemented in `tox_jni.c`, see [ToxCallbackAdapter]'s doc for
     * the intended mechanism). Does NOT start the iterate loop itself --
     * that's a separate concern (a coroutine or dedicated thread calling
     * [ToxNative.toxIterate] on a [ToxNative.toxIterationInterval] cadence,
     * not scaffolded here since it needs a real coroutine
     * scope/lifecycle owner from the app layer to be wired up correctly).
     */
    @Synchronized
    fun startDaemon() {
        if (handle != null) return
        handle = ToxNative.toxNew()
        // TODO once tox_jni.c grows a toxRegisterCallbacks entry point:
        // ToxNative.toxRegisterCallbacks(handle!!, callbackAdapter)
    }

    /** Maps to `tox_kill` (see [ToxNative.toxKill]'s doc). */
    @Synchronized
    fun stopDaemon() {
        val h = handle ?: return
        ToxNative.toxKill(h)
        handle = null
    }

    /** See [ToxNative.toxBootstrap]'s doc. */
    fun bootstrap(host: String, port: Int, publicKey: ByteArray): Boolean =
        ToxNative.toxBootstrap(requireHandle(), host, port, publicKey)

    /** See [ToxNative.toxSelfGetAddress]'s doc. */
    fun getSelfAddress(): ByteArray = ToxNative.toxSelfGetAddress(requireHandle())

    /** See [ToxNative.toxIterationInterval]'s doc. */
    fun iterationIntervalMs(): Int = ToxNative.toxIterationInterval(requireHandle())

    /**
     * Runs one `tox_iterate` step (see [ToxNative.toxIterate]'s doc). The
     * caller (not scaffolded here -- needs a real coroutine loop from the
     * app layer) is responsible for calling this repeatedly, sleeping
     * [iterationIntervalMs] between calls.
     */
    fun iterate() = ToxNative.toxIterate(requireHandle())

    /** See [ToxNative.toxFriendAdd]'s doc. */
    fun addFriend(address: ByteArray, message: ByteArray): Int =
        ToxNative.toxFriendAdd(requireHandle(), address, message)

    /** See [ToxNative.toxFriendDelete]'s doc. */
    fun deleteFriend(friendNumber: Int): Boolean = ToxNative.toxFriendDelete(requireHandle(), friendNumber)

    /** See [ToxNative.toxFriendSendMessage]'s doc. */
    fun sendMessage(friendNumber: Int, message: ByteArray): Int =
        ToxNative.toxFriendSendMessage(requireHandle(), friendNumber, message)

    /** See [ToxNative.toxFriendGetConnectionStatus]'s doc. */
    fun friendConnectionStatus(friendNumber: Int): Int =
        ToxNative.toxFriendGetConnectionStatus(requireHandle(), friendNumber)

    /** See [ToxNative.toxSelfGetConnectionStatus]'s doc. */
    fun selfConnectionStatus(): Int = ToxNative.toxSelfGetConnectionStatus(requireHandle())

    private fun requireHandle(): Long =
        checkNotNull(handle) { "ToxBridge.startDaemon() must be called before using the Tox instance" }
}
