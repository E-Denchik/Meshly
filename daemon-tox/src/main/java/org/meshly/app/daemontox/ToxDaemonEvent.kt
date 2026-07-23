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
 * Raw Tox/ToxAV signals surfaced by [ToxCallbackAdapter], in terms of Tox's
 * own vocabulary (friend numbers, not stable contact ids -- see
 * [ToxFriendInfo]'s doc for why that matters).
 *
 * This is deliberately NOT [org.meshly.app.core.ToxEvent] from the :app
 * module's Phase 1 mock -- :daemon-tox doesn't depend on :app. Mapping
 * ToxDaemonEvent -> whatever the mock's event type is (friend number ->
 * stable contact id lookup, etc.) is the next wiring step once this module
 * is actually built; see PHASE2_BUILD_TOX.md.
 *
 * Every callback typedef cited below was read from the real checked-out
 * `native/upstream/c-toxcore` submodule's `toxcore/tox.h` /
 * `toxav/toxav.h` in this pass (line numbers included) -- not guessed.
 */
sealed class ToxDaemonEvent {

    // --- Core connectivity (tox.h) --------------------------------------------------------

    /**
     * `typedef void tox_self_connection_status_cb(Tox *tox, Tox_Connection
     * connection_status, void *user_data);` (tox.h line 651, confirmed).
     * `status` matches `Tox_Connection`'s ordinals: NONE=0, TCP=1, UDP=2
     * (tox.h lines 605-633, confirmed) -- see [ToxNative.toxSelfGetConnectionStatus]'s doc.
     */
    data class SelfConnectionStatusChanged(val status: Int) : ToxDaemonEvent()

    // --- Friend requests & friend list (tox.h) --------------------------------------------

    /**
     * `typedef void tox_friend_request_cb(Tox *tox, const Tox_Public_Key
     * public_key, const uint8_t message[], size_t length, void
     * *user_data);` (tox.h lines 1477-1480, confirmed). `publicKey` is
     * `TOX_PUBLIC_KEY_SIZE` (32) bytes (tox.h line 212, confirmed) -- the
     * sender's long-term public key, NOT their 38-byte address; there is no
     * friend number yet at this point since the request hasn't been
     * accepted (accepting means calling `tox_friend_add_norequest`, not
     * wrapped in [ToxNative] yet -- see that doc).
     */
    data class FriendRequestReceived(val publicKey: ByteArray, val message: ByteArray) : ToxDaemonEvent()

    /**
     * `typedef void tox_friend_message_cb(Tox *tox, Tox_Friend_Number
     * friend_number, Tox_Message_Type type, const uint8_t message[],
     * size_t length, void *user_data);` (tox.h lines 1497-1499, confirmed).
     * `messageType` matches `Tox_Message_Type`'s ordinals (tox.h lines
     * 403-416, confirmed): NORMAL=0, ACTION=1.
     */
    data class FriendMessageReceived(
        val friendNumber: Int,
        val messageType: Int,
        val message: ByteArray
    ) : ToxDaemonEvent()

    /**
     * `typedef void tox_friend_read_receipt_cb(Tox *tox, Tox_Friend_Number
     * friend_number, Tox_Friend_Message_Id message_id, void *user_data);`
     * (tox.h lines 1453-1454, confirmed). Fires when the friend's client
     * confirms receipt of a message sent via
     * [ToxNative.toxFriendSendMessage] -- `messageId` is that call's return
     * value.
     */
    data class FriendReadReceipt(val friendNumber: Int, val messageId: Int) : ToxDaemonEvent()

    /**
     * `typedef void tox_friend_connection_status_cb(Tox *tox,
     * Tox_Friend_Number friend_number, Tox_Connection connection_status,
     * void *user_data);` (tox.h lines 1292-1293, confirmed). NOT fired when
     * a friend is first added, only on actual online/offline transitions
     * (tox.h lines 1303-1304, confirmed) -- a freshly-added friend should
     * be assumed offline until this fires.
     */
    data class FriendConnectionStatusChanged(val friendNumber: Int, val connectionStatus: Int) : ToxDaemonEvent()

    /**
     * `typedef void tox_friend_name_cb(Tox *tox, Tox_Friend_Number
     * friend_number, const uint8_t name[], size_t length, void
     * *user_data);` (tox.h lines 1177-1179, confirmed).
     */
    data class FriendNameChanged(val friendNumber: Int, val name: ByteArray) : ToxDaemonEvent()

    /**
     * `typedef void tox_friend_status_message_cb(Tox *tox,
     * Tox_Friend_Number friend_number, const uint8_t message[], size_t
     * length, void *user_data);` (tox.h lines 1224-1226, confirmed).
     */
    data class FriendStatusMessageChanged(val friendNumber: Int, val statusMessage: ByteArray) : ToxDaemonEvent()

    /**
     * `typedef void tox_friend_status_cb(Tox *tox, Tox_Friend_Number
     * friend_number, Tox_User_Status status, void *user_data);` (tox.h
     * lines 1256-1257, confirmed). `Tox_User_Status` (tox.h lines 376-395,
     * confirmed): NONE=0 ("online and available"), AWAY=1, BUSY=2.
     */
    data class FriendUserStatusChanged(val friendNumber: Int, val status: Int) : ToxDaemonEvent()

    /**
     * `typedef void tox_friend_typing_cb(Tox *tox, Tox_Friend_Number
     * friend_number, bool typing, void *user_data);` (tox.h lines
     * 1329-1330, confirmed). Fires when a friend starts or stops typing
     * (tox.h line 1337, confirmed).
     */
    data class FriendTypingChanged(val friendNumber: Int, val isTyping: Boolean) : ToxDaemonEvent()

    // --- ToxAV call lifecycle (toxav.h) ---------------------------------------------------

    /**
     * `typedef void toxav_call_cb(ToxAV *av, Tox_Friend_Number
     * friend_number, bool audio_enabled, bool video_enabled, void
     * *user_data);` (toxav.h line 282, confirmed). An incoming call
     * invite -- answer via a `toxav_answer` JNI wrapper (not yet written in
     * `tox_jni.c`, see its doc) or reject via `toxav_call_control` with
     * `TOXAV_CALL_CONTROL_CANCEL`.
     */
    data class CallInviteReceived(
        val friendNumber: Int,
        val audioEnabled: Boolean,
        val videoEnabled: Boolean
    ) : ToxDaemonEvent()

    /**
     * `typedef void toxav_call_state_cb(ToxAV *av, Tox_Friend_Number
     * friend_number, uint32_t state, void *user_data);` (toxav.h line 403,
     * confirmed). `state` is a bitmask of `Toxav_Friend_Call_State` values
     * (toxav.h lines 350-392, confirmed): NONE=0, ERROR=1, FINISHED=2,
     * SENDING_A=4, SENDING_V=8, ACCEPTING_A=16, ACCEPTING_V=32 -- multiple
     * bits can be set simultaneously (e.g. SENDING_A|ACCEPTING_A for a
     * two-way audio call), so `state` is kept as a raw `Int` bitmask here
     * rather than pre-decoded into separate booleans.
     */
    data class CallStateChanged(val friendNumber: Int, val stateBitmask: Int) : ToxDaemonEvent()

    /**
     * `typedef void toxav_audio_bit_rate_cb(ToxAV *av, Tox_Friend_Number
     * friend_number, uint32_t audio_bit_rate, void *user_data);` (toxav.h
     * line 637, confirmed). ToxAV suggesting a new audio bit rate after
     * detecting network saturation (toxav.h lines 629-631, confirmed).
     */
    data class AudioBitRateSuggested(val friendNumber: Int, val bitRateKbps: Int) : ToxDaemonEvent()

    /**
     * `typedef void toxav_video_bit_rate_cb(ToxAV *av, Tox_Friend_Number
     * friend_number, uint32_t video_bit_rate, void *user_data);` (toxav.h
     * line 688, confirmed). Same as [AudioBitRateSuggested] but for video.
     */
    data class VideoBitRateSuggested(val friendNumber: Int, val bitRateKbps: Int) : ToxDaemonEvent()

    /**
     * `typedef void toxav_audio_receive_frame_cb(ToxAV *av,
     * Tox_Friend_Number friend_number, const int16_t pcm[], size_t
     * sample_count, uint8_t channels, uint32_t sampling_rate, void
     * *user_data);` (toxav.h lines 714-715, confirmed). `pcm` is raw
     * interleaved 16-bit PCM (LRLRLR... for stereo, toxav.h lines 598-601,
     * confirmed) -- kept as a `ShortArray` here rather than `ByteArray`
     * since the wire format is already 16-bit samples, not raw bytes.
     */
    data class AudioFrameReceived(
        val friendNumber: Int,
        val pcm: ShortArray,
        val sampleCount: Int,
        val channels: Int,
        val samplingRate: Int
    ) : ToxDaemonEvent()

    /**
     * `typedef void toxav_video_receive_frame_cb(ToxAV *av,
     * Tox_Friend_Number friend_number, uint16_t width, uint16_t height,
     * const uint8_t y[], const uint8_t u[], const uint8_t v[], int32_t
     * ystride, int32_t ustride, int32_t vstride, void *user_data);`
     * (toxav.h lines 744-751, confirmed). Planar YUV420 (toxav.h line 648,
     * confirmed) -- `yStride`/`uStride`/`vStride` can be negative for
     * bottom-up images and must be `abs()`-ed when computing plane buffer
     * sizes (toxav.h lines 730-732, confirmed); not pre-processed here,
     * passed through as-is.
     */
    data class VideoFrameReceived(
        val friendNumber: Int,
        val width: Int,
        val height: Int,
        val y: ByteArray,
        val u: ByteArray,
        val v: ByteArray,
        val yStride: Int,
        val uStride: Int,
        val vStride: Int
    ) : ToxDaemonEvent()
}
