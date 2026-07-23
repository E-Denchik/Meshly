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
 * Raw JNI entry points implemented by hand in `tox_jni.c`, one per real
 * `tox_*` function from `native/upstream/c-toxcore/toxcore/tox.h`.
 *
 * Unlike the removed jami-daemon scaffold's `net.jami.daemon.JamiService`
 * (SWIG-generated, so its Java surface could be trusted to match the C++
 * source exactly), every signature below was hand-transcribed from tox.h --
 * each KDoc cites the exact function name and line number read from the
 * actual checked-out `native/upstream/c-toxcore` submodule in this pass, or
 * says explicitly if it wasn't cross-checked that way.
 *
 * `handle` throughout is a `Tox*` native pointer, boxed as a `Long` (see
 * `tox_jni.c`'s top-of-file doc for why this needs to be an opaque handle
 * rather than a SWIG-style generated wrapper object).
 */
internal object ToxNative {

    /**
     * `Tox *tox_new(const Tox_Options *options, Tox_Err_New *error);`
     * (tox.h line 504, confirmed). Returns the new instance as an opaque
     * handle, or 0 if `tox_new` returned NULL (failure, e.g. `Tox_Err_New`
     * values `TOX_ERR_NEW_MALLOC`/`TOX_ERR_NEW_PORT_ALLOC`/... -- tox.h
     * lines 426-487, confirmed). This scaffold's `tox_jni.c` implementation
     * always passes `options = NULL` (tox_new's documented default-options
     * behavior, tox.h lines 497-498) -- a real implementation should accept
     * `Tox_Options` (proxy settings, IPv6, save-data to restore from
     * `tox_options_get_savedata_data`/`_length`, ...) as parameters here
     * instead.
     */
    external fun toxNew(): Long

    /**
     * `void tox_kill(Tox *tox);` (tox.h line 513, confirmed). Releases the
     * native instance; `handle` is invalid after this call.
     */
    external fun toxKill(handle: Long)

    /**
     * `bool tox_bootstrap(Tox *tox, const char *host, uint16_t port, const
     * Tox_Dht_Id public_key, Tox_Err_Bootstrap *error);` (tox.h line 584,
     * confirmed). `publicKey` must be exactly `TOX_DHT_ID_SIZE` (32) bytes
     * (tox.h lines 234/238, confirmed) -- the DHT node's public key, not the
     * caller's own.
     */
    external fun toxBootstrap(handle: Long, host: String, port: Int, publicKey: ByteArray): Boolean

    /**
     * `void tox_self_get_address(const Tox *tox, Tox_Address address);`
     * (tox.h line 698, confirmed). Returns exactly `TOX_ADDRESS_SIZE` (38)
     * bytes (tox.h line 277, confirmed): the 32-byte public key + 4-byte
     * nospam + 2-byte checksum this account's Tox ID is made of (tox.h line
     * 271, confirmed) -- this is the string a peer needs to add this
     * account as a friend, once hex-encoded (not done here; tox.h's own doc
     * at lines 691-692 notes the raw bytes are "not in human-readable
     * format").
     */
    external fun toxSelfGetAddress(handle: Long): ByteArray

    /**
     * `uint32_t tox_iteration_interval(const Tox *tox);` (tox.h line 672,
     * confirmed). Milliseconds to sleep before the next [toxIterate] call
     * for optimal performance -- not a fixed constant, must be re-queried
     * each loop iteration per tox.h's own doc (lines 668-671).
     */
    external fun toxIterationInterval(handle: Long): Int

    /**
     * `void tox_iterate(Tox *tox, void *user_data);` (tox.h line 680,
     * confirmed). The main network/event loop step; must be called
     * repeatedly, sleeping [toxIterationInterval] milliseconds between
     * calls (tox.h lines 674-680). All `tox_callback_*` callbacks fire from
     * within this call, on whatever thread calls it.
     */
    external fun toxIterate(handle: Long)

    /**
     * `Tox_Friend_Number tox_friend_add(Tox *tox, const Tox_Address
     * address, const uint8_t message[], size_t length, Tox_Err_Friend_Add
     * *error);` (tox.h lines 935-938, confirmed). `address` is the target's
     * full 38-byte Tox address (see [toxSelfGetAddress]'s doc), not just
     * their 32-byte public key -- adding by bare public key without sending
     * a request is a separate real function, `tox_friend_add_norequest`
     * (tox.h lines 958-959, confirmed present but not wrapped in this
     * scaffolding pass). Returns the new friend number
     * (`Tox_Friend_Number` is `uint32_t`, tox.h line 856, confirmed) as an
     * `Int`, or an unspecified value on failure per tox_friend_add's own
     * doc (tox.h line 933) -- check the JNI layer's `Tox_Err_Friend_Add`
     * out-param for the real success/failure signal once it's threaded
     * through (not yet, in this scaffold's `tox_jni.c`).
     */
    external fun toxFriendAdd(handle: Long, address: ByteArray, message: ByteArray): Int

    /**
     * `bool tox_friend_delete(Tox *tox, Tox_Friend_Number friend_number,
     * Tox_Err_Friend_Delete *error);` (tox.h line 989, confirmed). Does NOT
     * notify the friend (tox.h lines 981-983, confirmed) -- Tox has no
     * "unfriend" notification to the other side by design.
     */
    external fun toxFriendDelete(handle: Long, friendNumber: Int): Boolean

    /**
     * `Tox_Friend_Message_Id tox_friend_send_message(Tox *tox,
     * Tox_Friend_Number friend_number, Tox_Message_Type type, const
     * uint8_t message[], size_t length, Tox_Err_Friend_Send_Message
     * *error);` (tox.h lines 1443-1445, confirmed). This scaffold's
     * `tox_jni.c` hardcodes `type = TOX_MESSAGE_TYPE_NORMAL` -- the other
     * real value, `TOX_MESSAGE_TYPE_ACTION` (tox.h's `Tox_Message_Type`
     * enum, lines 403-416, confirmed) is for "/me" style action messages
     * and isn't threaded through yet. Returns the message id
     * (`Tox_Friend_Message_Id` is `uint32_t`, tox.h line 1417, confirmed):
     * matched against a later `friend_read_receipt` callback
     * (tox.h lines 1453-1464, confirmed) to know the friend actually
     * received it.
     */
    external fun toxFriendSendMessage(handle: Long, friendNumber: Int, message: ByteArray): Int

    /**
     * `Tox_Connection tox_friend_get_connection_status(const Tox *tox,
     * Tox_Friend_Number friend_number, Tox_Err_Friend_Query *error);`
     * (tox.h lines 1283-1284, confirmed). Marked `@deprecated` upstream in
     * favor of tracking the `friend_connection_status` callback client-side
     * instead (tox.h lines 1280-1281, confirmed) -- kept here as a
     * synchronous getter anyway, matching [toxSelfGetConnectionStatus].
     * `Tox_Connection` is a 3-value enum: `TOX_CONNECTION_NONE` = 0,
     * `_TCP` = 1, `_UDP` = 2 (tox.h lines 605-633, confirmed) -- returned
     * as an `Int` matching those ordinals.
     */
    external fun toxFriendGetConnectionStatus(handle: Long, friendNumber: Int): Int

    /**
     * `Tox_Connection tox_self_get_connection_status(const Tox *tox);`
     * (tox.h line 646, confirmed). Also marked `@deprecated` upstream (tox.h
     * lines 643-644, confirmed) for the same reason as
     * [toxFriendGetConnectionStatus] -- see that doc for the `Tox_Connection`
     * value mapping.
     */
    external fun toxSelfGetConnectionStatus(handle: Long): Int
}
