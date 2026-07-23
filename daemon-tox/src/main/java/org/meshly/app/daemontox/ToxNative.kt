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
 * `tox_*`/`toxav_*` function from `native/upstream/c-toxcore/{toxcore/tox.h,
 * toxav/toxav.h}`.
 *
 * `handle`/`avHandle` throughout are opaque `Tox*`/`ToxAV*` native pointers
 * boxed as `Long`. See `tox_jni.c`'s top-of-file doc for the callback
 * dispatch design ([toxRegisterCallbacks]/[toxavRegisterCallbacks] bind a
 * [ToxCallbackAdapter] instance that every `tox_callback_*`/`toxav_callback_*`
 * trampoline calls back into).
 */
internal object ToxNative {

    /**
     * `Tox *tox_new(const Tox_Options *options, Tox_Err_New *error);`
     * (tox.h line 504, confirmed). Builds real options (UDP/IPv6/local
     * discovery/hole punching enabled) instead of passing NULL. If
     * [savedata] is non-null and non-empty, it's loaded as
     * `TOX_SAVEDATA_TYPE_TOX_SAVE` (tox_options.h line 59, confirmed) to
     * restore an existing identity instead of generating a new one -- pass
     * null/empty on first-ever account creation. Returns the new instance
     * as an opaque handle, or 0 on failure.
     */
    external fun toxNew(savedata: ByteArray?): Long

    /**
     * `void tox_kill(Tox *tox);` (tox.h line 513, confirmed). Releases the
     * native instance; `handle` is invalid after this call.
     */
    external fun toxKill(handle: Long)

    /**
     * `size_t tox_get_savedata_size(const Tox *tox);` (tox.h line 523,
     * confirmed). Byte length [toxGetSavedata] will return.
     */
    external fun toxGetSavedataSize(handle: Long): Int

    /**
     * `void tox_get_savedata(const Tox *tox, uint8_t savedata[]);` (tox.h
     * line 533, confirmed). Persist the returned bytes (keys, friend list,
     * name, ...) so [toxNew] can restore this exact identity later.
     */
    external fun toxGetSavedata(handle: Long): ByteArray

    /**
     * Binds [adapter] and registers every `tox_callback_*` handler
     * (`tox_callback_self_connection_status`, `_friend_request`,
     * `_friend_message`, `_friend_read_receipt`, `_friend_connection_status`,
     * `_friend_name`, `_friend_status_message`, `_friend_status`,
     * `_friend_typing`) so their events start flowing into [adapter]'s
     * `on*` methods once [toxIterate] starts running. Call once, before the
     * first [toxIterate].
     */
    external fun toxRegisterCallbacks(handle: Long, adapter: ToxCallbackAdapter)

    /**
     * `bool tox_bootstrap(Tox *tox, const char *host, uint16_t port, const
     * Tox_Dht_Id public_key, Tox_Err_Bootstrap *error);` (tox.h line 584,
     * confirmed). `publicKey` must be exactly `TOX_DHT_ID_SIZE` (32) bytes
     * (tox.h lines 234/238, confirmed) -- the DHT node's public key, not the
     * caller's own.
     */
    external fun toxBootstrap(handle: Long, host: String, port: Int, publicKey: ByteArray): Boolean

    /**
     * `bool tox_add_tcp_relay(Tox *tox, const char *host, uint16_t port,
     * const Tox_Dht_Id public_key, Tox_Err_Bootstrap *error);` (tox.h line
     * 600, confirmed). Registers a TCP relay for onion routing - distinct
     * from [toxBootstrap]'s UDP-only DHT bootstrap. Needed so friend/DHT
     * rendezvous still works on networks that block or degrade outbound UDP.
     */
    external fun toxAddTcpRelay(handle: Long, host: String, port: Int, publicKey: ByteArray): Boolean

    /**
     * `void tox_self_get_address(const Tox *tox, Tox_Address address);`
     * (tox.h line 698, confirmed). Returns exactly `TOX_ADDRESS_SIZE` (38)
     * bytes (tox.h line 277, confirmed): the 32-byte public key + 4-byte
     * nospam + 2-byte checksum this account's Tox ID is made of (tox.h line
     * 271, confirmed) -- this is the string a peer needs to add this
     * account as a friend, once hex-encoded.
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
     * calls. All `tox_callback_*` callbacks fire from within this call, on
     * whatever thread calls it -- [toxIterate] and [toxavIterate] MUST
     * always be called from the same thread (see `tox_jni.c`'s top-of-file
     * doc on the shared-JNIEnv callback dispatch design).
     */
    external fun toxIterate(handle: Long)

    /**
     * `Tox_Friend_Number tox_friend_add(Tox *tox, const Tox_Address
     * address, const uint8_t message[], size_t length, Tox_Err_Friend_Add
     * *error);` (tox.h lines 935-938, confirmed). `address` is the target's
     * full 38-byte Tox address (see [toxSelfGetAddress]'s doc). Returns the
     * new friend number (`Tox_Friend_Number` is `uint32_t`, tox.h line 856,
     * confirmed) as an `Int`, or an unspecified value on failure.
     */
    external fun toxFriendAdd(handle: Long, address: ByteArray, message: ByteArray): Int

    /**
     * `Tox_Friend_Number tox_friend_add_norequest(Tox *tox, const
     * Tox_Public_Key public_key, Tox_Err_Friend_Add *error);` (tox.h lines
     * 958-959, confirmed). Used to accept an incoming friend request
     * (already seen via [ToxCallbackAdapter.onFriendRequest]) without
     * sending a new request of the app's own. `publicKey` is 32 bytes
     * (`TOX_PUBLIC_KEY_SIZE`), not the 38-byte address [toxFriendAdd] takes.
     */
    external fun toxFriendAddNorequest(handle: Long, publicKey: ByteArray): Int

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
     * *error);` (tox.h lines 1443-1445, confirmed). `messageType` is 0
     * (`TOX_MESSAGE_TYPE_NORMAL`) or 1 (`TOX_MESSAGE_TYPE_ACTION`, "/me"
     * style messages) -- tox.h's `Tox_Message_Type` enum, lines 403-416,
     * confirmed. Returns the message id (`Tox_Friend_Message_Id` is
     * `uint32_t`, tox.h line 1417, confirmed): matched against a later
     * `friend_read_receipt` callback to know the friend actually received
     * it.
     */
    external fun toxFriendSendMessage(handle: Long, friendNumber: Int, messageType: Int, message: ByteArray): Int

    /**
     * `Tox_Connection tox_friend_get_connection_status(const Tox *tox,
     * Tox_Friend_Number friend_number, Tox_Err_Friend_Query *error);`
     * (tox.h lines 1283-1284, confirmed). `Tox_Connection` is a 3-value
     * enum: `TOX_CONNECTION_NONE` = 0, `_TCP` = 1, `_UDP` = 2 (tox.h lines
     * 605-633, confirmed).
     */
    external fun toxFriendGetConnectionStatus(handle: Long, friendNumber: Int): Int

    /**
     * `Tox_Connection tox_self_get_connection_status(const Tox *tox);`
     * (tox.h line 646, confirmed).
     */
    external fun toxSelfGetConnectionStatus(handle: Long): Int

    // --- ToxAV (native/upstream/c-toxcore/toxav/toxav.h) --------------------------------------

    /**
     * `ToxAV *toxav_new(Tox *tox, Toxav_Err_New *error);` (toxav.h line
     * 137, confirmed). Must be created after [toxNew]; returns an opaque
     * `ToxAV*` handle, or 0 on failure.
     */
    external fun toxavNew(toxHandle: Long): Long

    /** `void toxav_kill(ToxAV *av);` (toxav.h line 146, confirmed). */
    external fun toxavKill(avHandle: Long)

    /**
     * Binds [adapter] (shared/idempotent with [toxRegisterCallbacks]) and
     * registers every `toxav_callback_*` handler (`_call`, `_call_state`,
     * `_audio_bit_rate`, `_video_bit_rate`, `_audio_receive_frame`,
     * `_video_receive_frame`). Call once, before the first [toxavIterate].
     */
    external fun toxavRegisterCallbacks(avHandle: Long, adapter: ToxCallbackAdapter)

    /**
     * `uint32_t toxav_iteration_interval(const ToxAV *av);` (toxav.h line
     * 164, confirmed).
     */
    external fun toxavIterationInterval(avHandle: Long): Int

    /**
     * `void toxav_iterate(ToxAV *av);` (toxav.h line 171, confirmed). MUST
     * be called from the same thread as [toxIterate] (see `tox_jni.c`'s
     * top-of-file doc).
     */
    external fun toxavIterate(avHandle: Long)

    /**
     * `bool toxav_call(ToxAV *av, Tox_Friend_Number friend_number, uint32_t
     * audio_bit_rate, uint32_t video_bit_rate, Toxav_Err_Call *error);`
     * (toxav.h line 272, confirmed). Bit rates are kbit/sec; 0 disables
     * that media type for the call.
     */
    external fun toxavCall(avHandle: Long, friendNumber: Int, audioBitRate: Int, videoBitRate: Int): Boolean

    /**
     * `bool toxav_answer(ToxAV *av, Tox_Friend_Number friend_number,
     * uint32_t audio_bit_rate, uint32_t video_bit_rate, Toxav_Err_Answer
     * *error);` (toxav.h line 341, confirmed).
     */
    external fun toxavAnswer(avHandle: Long, friendNumber: Int, audioBitRate: Int, videoBitRate: Int): Boolean

    /**
     * `bool toxav_call_control(ToxAV *av, Tox_Friend_Number friend_number,
     * Toxav_Call_Control control, Toxav_Err_Call_Control *error);` (toxav.h
     * line 503, confirmed). `control` matches `Toxav_Call_Control`'s
     * ordinals: RESUME=0, PAUSE=1, CANCEL=2, MUTE_AUDIO=3, UNMUTE_AUDIO=4,
     * HIDE_VIDEO=5, SHOW_VIDEO=6.
     */
    external fun toxavCallControl(avHandle: Long, friendNumber: Int, control: Int): Boolean

    /**
     * `bool toxav_audio_send_frame(ToxAV *av, Tox_Friend_Number
     * friend_number, const int16_t pcm[], size_t sample_count, uint8_t
     * channels, uint32_t sampling_rate, Toxav_Err_Send_Frame *error);`
     * (toxav.h line 614, confirmed). `pcm.size` must equal
     * `sampleCount * channels`. Valid `samplingRate`s: 8000/12000/16000/
     * 24000/48000; valid frame durations: 2.5/5/10/20/40/60ms.
     */
    external fun toxavAudioSendFrame(
        avHandle: Long,
        friendNumber: Int,
        pcm: ShortArray,
        sampleCount: Int,
        channels: Int,
        samplingRate: Int
    ): Boolean

    /**
     * `bool toxav_video_send_frame(ToxAV *av, Tox_Friend_Number
     * friend_number, uint16_t width, uint16_t height, const uint8_t y[],
     * const uint8_t u[], const uint8_t v[], Toxav_Err_Send_Frame *error);`
     * (toxav.h line 661, confirmed). Planar YUV420: `y.size == width *
     * height`, `u.size == v.size == (width/2) * (height/2)`.
     */
    external fun toxavVideoSendFrame(
        avHandle: Long,
        friendNumber: Int,
        width: Int,
        height: Int,
        y: ByteArray,
        u: ByteArray,
        v: ByteArray
    ): Boolean
}
