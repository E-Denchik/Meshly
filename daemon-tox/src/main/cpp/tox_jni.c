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
 * Hand-written JNI wrapper around c-toxcore's tox.h C API. This is the
 * structural replacement for the removed jami-daemon scaffold's SWIG-
 * generated bindings: c-toxcore has no binding generator at all, so every
 * JNI entry point below is written by hand against the real tox_* functions
 * declared in native/upstream/c-toxcore/toxcore/tox.h.
 *
 * This is a REPRESENTATIVE SLICE (account lifecycle, bootstrap, friend
 * management, messaging), not an exhaustive wrapper. Call/AV entry points
 * (toxav_new/toxav_call/toxav_answer/toxav_call_control/
 * toxav_audio_send_frame/toxav_video_send_frame/...) follow the exact same
 * pattern against native/upstream/c-toxcore/toxav/toxav.h and are not
 * individually scaffolded here yet -- see PHASE2_BUILD_TOX.md.
 *
 * NOT expected to compile as-is: it assumes c-toxcore's headers/library are
 * actually configured and built for the target ABI first (see
 * daemon-tox/CMakeLists.txt's comments on the still-open libsodium/opus/vpx
 * dependency question), which has not been done in this pass.
 *
 * The Tox instance handle is threaded through Kotlin as a `jlong` -- a raw
 * `Tox *` pointer cast to `jlong` and back. This mirrors the conventional
 * "opaque native handle" JNI pattern; c-toxcore itself has no concept of a
 * Java/JNI handle since tox.h is a plain C API.
 */

#include <jni.h>
#include <stdlib.h>
#include <string.h>

#include "tox/tox.h"

/*
 * Java_org_meshly_app_daemontox_ToxNative_toxNew
 *
 * Wraps `Tox *tox_new(const Tox_Options *options, Tox_Err_New *error);`
 * (tox.h line 504, confirmed). This scaffold ignores `options` and passes
 * NULL, which tox_new documents as "the default options are used" (tox.h
 * line 497-498) -- a real implementation should build a Tox_Options via
 * tox_options_new()/tox_options_free() (tox_options.h) first, not scaffolded
 * here yet. Returns the new Tox* reinterpreted as a jlong handle, or 0 on
 * failure (tox_new returns NULL on failure per its doc, line 502).
 */
JNIEXPORT jlong JNICALL
Java_org_meshly_app_daemontox_ToxNative_toxNew(JNIEnv *env, jobject thiz) {
    (void) env;
    (void) thiz;
    Tox_Err_New error;
    Tox *tox = tox_new(NULL, &error);
    return (jlong) (intptr_t) tox;
}

/*
 * Java_org_meshly_app_daemontox_ToxNative_toxKill
 *
 * Wraps `void tox_kill(Tox *tox);` (tox.h line 513, confirmed). Not part of
 * ToxNative.kt's KDoc'd surface in the task description but included here
 * since a native handle obtained from toxNew must be released somewhere.
 */
JNIEXPORT void JNICALL
Java_org_meshly_app_daemontox_ToxNative_toxKill(JNIEnv *env, jobject thiz, jlong handle) {
    (void) env;
    (void) thiz;
    tox_kill((Tox *) (intptr_t) handle);
}

/*
 * Java_org_meshly_app_daemontox_ToxNative_toxBootstrap
 *
 * Wraps `bool tox_bootstrap(Tox *tox, const char *host, uint16_t port,
 * const Tox_Dht_Id public_key, Tox_Err_Bootstrap *error);` (tox.h line 584,
 * confirmed). `Tox_Dht_Id` is `uint8_t[TOX_DHT_ID_SIZE]` with
 * TOX_DHT_ID_SIZE == 32 (tox.h lines 234/238, confirmed) -- the Kotlin side
 * passes it as a 32-byte ByteArray.
 */
JNIEXPORT jboolean JNICALL
Java_org_meshly_app_daemontox_ToxNative_toxBootstrap(
    JNIEnv *env, jobject thiz, jlong handle, jstring host, jint port, jbyteArray publicKey) {
    (void) thiz;
    Tox *tox = (Tox *) (intptr_t) handle;
    const char *host_chars = (*env)->GetStringUTFChars(env, host, NULL);
    jbyte *pk_bytes = (*env)->GetByteArrayElements(env, publicKey, NULL);

    Tox_Err_Bootstrap error;
    bool ok = tox_bootstrap(tox, host_chars, (uint16_t) port, (const uint8_t *) pk_bytes, &error);

    (*env)->ReleaseStringUTFChars(env, host, host_chars);
    (*env)->ReleaseByteArrayElements(env, publicKey, pk_bytes, JNI_ABORT);
    return (jboolean) ok;
}

/*
 * Java_org_meshly_app_daemontox_ToxNative_toxSelfGetAddress
 *
 * Wraps `void tox_self_get_address(const Tox *tox, Tox_Address address);`
 * (tox.h line 698, confirmed). TOX_ADDRESS_SIZE == 38 (tox.h line 277,
 * confirmed): 32-byte public key + 4-byte nospam + 2-byte checksum (tox.h
 * line 271, confirmed).
 */
JNIEXPORT jbyteArray JNICALL
Java_org_meshly_app_daemontox_ToxNative_toxSelfGetAddress(JNIEnv *env, jobject thiz, jlong handle) {
    (void) thiz;
    Tox *tox = (Tox *) (intptr_t) handle;

    uint8_t address[38]; /* TOX_ADDRESS_SIZE, tox.h line 277 */
    tox_self_get_address(tox, address);

    jbyteArray result = (*env)->NewByteArray(env, 38);
    (*env)->SetByteArrayRegion(env, result, 0, 38, (const jbyte *) address);
    return result;
}

/*
 * Java_org_meshly_app_daemontox_ToxNative_toxIterationInterval
 *
 * Wraps `uint32_t tox_iteration_interval(const Tox *tox);` (tox.h line 672,
 * confirmed).
 */
JNIEXPORT jint JNICALL
Java_org_meshly_app_daemontox_ToxNative_toxIterationInterval(JNIEnv *env, jobject thiz, jlong handle) {
    (void) env;
    (void) thiz;
    Tox *tox = (Tox *) (intptr_t) handle;
    return (jint) tox_iteration_interval(tox);
}

/*
 * Java_org_meshly_app_daemontox_ToxNative_toxIterate
 *
 * Wraps `void tox_iterate(Tox *tox, void *user_data);` (tox.h line 680,
 * confirmed). `user_data` is passed as NULL here; the real design routes
 * callbacks back into Kotlin via a held jobject global ref registered at
 * tox_callback_* time instead (see ToxCallbackAdapter.kt's doc), not via
 * this per-call user_data pointer.
 */
JNIEXPORT void JNICALL
Java_org_meshly_app_daemontox_ToxNative_toxIterate(JNIEnv *env, jobject thiz, jlong handle) {
    (void) env;
    (void) thiz;
    Tox *tox = (Tox *) (intptr_t) handle;
    tox_iterate(tox, NULL);
}

/*
 * Java_org_meshly_app_daemontox_ToxNative_toxFriendAdd
 *
 * Wraps `Tox_Friend_Number tox_friend_add(Tox *tox, const Tox_Address
 * address, const uint8_t message[], size_t length, Tox_Err_Friend_Add
 * *error);` (tox.h lines 935-938, confirmed). `Tox_Friend_Number` is
 * `uint32_t` (tox.h line 856, confirmed) -- returned as a Kotlin Int.
 */
JNIEXPORT jint JNICALL
Java_org_meshly_app_daemontox_ToxNative_toxFriendAdd(
    JNIEnv *env, jobject thiz, jlong handle, jbyteArray address, jbyteArray message) {
    (void) thiz;
    Tox *tox = (Tox *) (intptr_t) handle;

    jbyte *addr_bytes = (*env)->GetByteArrayElements(env, address, NULL);
    jbyte *msg_bytes = (*env)->GetByteArrayElements(env, message, NULL);
    jsize msg_len = (*env)->GetArrayLength(env, message);

    Tox_Err_Friend_Add error;
    uint32_t friend_number = tox_friend_add(
        tox, (const uint8_t *) addr_bytes, (const uint8_t *) msg_bytes, (size_t) msg_len, &error);

    (*env)->ReleaseByteArrayElements(env, address, addr_bytes, JNI_ABORT);
    (*env)->ReleaseByteArrayElements(env, message, msg_bytes, JNI_ABORT);
    return (jint) friend_number;
}

/*
 * Java_org_meshly_app_daemontox_ToxNative_toxFriendDelete
 *
 * Wraps `bool tox_friend_delete(Tox *tox, Tox_Friend_Number friend_number,
 * Tox_Err_Friend_Delete *error);` (tox.h line 989, confirmed).
 */
JNIEXPORT jboolean JNICALL
Java_org_meshly_app_daemontox_ToxNative_toxFriendDelete(
    JNIEnv *env, jobject thiz, jlong handle, jint friendNumber) {
    (void) env;
    (void) thiz;
    Tox *tox = (Tox *) (intptr_t) handle;
    Tox_Err_Friend_Delete error;
    return (jboolean) tox_friend_delete(tox, (uint32_t) friendNumber, &error);
}

/*
 * Java_org_meshly_app_daemontox_ToxNative_toxFriendSendMessage
 *
 * Wraps `Tox_Friend_Message_Id tox_friend_send_message(Tox *tox,
 * Tox_Friend_Number friend_number, Tox_Message_Type type, const uint8_t
 * message[], size_t length, Tox_Err_Friend_Send_Message *error);` (tox.h
 * lines 1443-1445, confirmed). `type` is hardcoded to
 * TOX_MESSAGE_TYPE_NORMAL (0, tox.h's Tox_Message_Type enum) here -- a real
 * implementation should take it as a parameter to also support
 * TOX_MESSAGE_TYPE_ACTION ("/me does something" style messages).
 * `Tox_Friend_Message_Id` is `uint32_t` (tox.h line 1417, confirmed) --
 * returned as a Kotlin Int.
 */
JNIEXPORT jint JNICALL
Java_org_meshly_app_daemontox_ToxNative_toxFriendSendMessage(
    JNIEnv *env, jobject thiz, jlong handle, jint friendNumber, jbyteArray message) {
    (void) thiz;
    Tox *tox = (Tox *) (intptr_t) handle;

    jbyte *msg_bytes = (*env)->GetByteArrayElements(env, message, NULL);
    jsize msg_len = (*env)->GetArrayLength(env, message);

    Tox_Err_Friend_Send_Message error;
    uint32_t message_id = tox_friend_send_message(
        tox, (uint32_t) friendNumber, TOX_MESSAGE_TYPE_NORMAL,
        (const uint8_t *) msg_bytes, (size_t) msg_len, &error);

    (*env)->ReleaseByteArrayElements(env, message, msg_bytes, JNI_ABORT);
    return (jint) message_id;
}

/*
 * Java_org_meshly_app_daemontox_ToxNative_toxFriendGetConnectionStatus
 *
 * Wraps `Tox_Connection tox_friend_get_connection_status(const Tox *tox,
 * Tox_Friend_Number friend_number, Tox_Err_Friend_Query *error);` (tox.h
 * lines 1283-1284, confirmed). `Tox_Connection` is a 3-value enum (NONE=0,
 * TCP=1, UDP=2 -- tox.h lines 605-633, confirmed) -- returned as a Kotlin
 * Int matching those ordinals.
 */
JNIEXPORT jint JNICALL
Java_org_meshly_app_daemontox_ToxNative_toxFriendGetConnectionStatus(
    JNIEnv *env, jobject thiz, jlong handle, jint friendNumber) {
    (void) env;
    (void) thiz;
    Tox *tox = (Tox *) (intptr_t) handle;
    Tox_Err_Friend_Query error;
    Tox_Connection status = tox_friend_get_connection_status(tox, (uint32_t) friendNumber, &error);
    return (jint) status;
}

/*
 * Java_org_meshly_app_daemontox_ToxNative_toxSelfGetConnectionStatus
 *
 * Wraps `Tox_Connection tox_self_get_connection_status(const Tox *tox);`
 * (tox.h line 646, confirmed). Marked @deprecated upstream in favour of
 * tracking the self_connection_status callback client-side (tox.h lines
 * 643-644) -- kept here anyway since ToxDaemonEvent.kt's
 * SelfConnectionStatusChanged event is the callback-based equivalent and a
 * synchronous getter is still occasionally useful (e.g. on daemon restart
 * before the first callback fires).
 */
JNIEXPORT jint JNICALL
Java_org_meshly_app_daemontox_ToxNative_toxSelfGetConnectionStatus(JNIEnv *env, jobject thiz, jlong handle) {
    (void) env;
    (void) thiz;
    Tox *tox = (Tox *) (intptr_t) handle;
    return (jint) tox_self_get_connection_status(tox);
}

/*
 * ToxAV wrapper functions (Java_org_meshly_app_daemontox_ToxNative_toxavNew,
 * ..._toxavCall, ..._toxavAnswer, ..._toxavCallControl,
 * ..._toxavAudioSendFrame, ..._toxavVideoSendFrame, and the
 * toxav_callback_* registration functions) are NOT scaffolded in this file
 * yet. They follow the exact same hand-written-JNI pattern shown above,
 * against native/upstream/c-toxcore/toxav/toxav.h's toxav_* functions
 * instead of tox.h's tox_* ones -- see PHASE2_BUILD_TOX.md's API reference
 * table for the specific signatures/line numbers already confirmed by
 * reading toxav.h in this pass.
 *
 * Callback registration (tox_callback_friend_message, tox_callback_
 * friend_request, tox_callback_self_connection_status, ...) is also not
 * scaffolded here yet. The intended pattern (see ToxCallbackAdapter.kt's
 * doc): toxNew (or a dedicated toxRegisterCallbacks JNI function, not yet
 * written) stores a JNIEnv-safe global ref to the calling Kotlin
 * ToxCallbackAdapter object via (*env)->NewGlobalRef(env, thiz), then calls
 * e.g. tox_callback_friend_message(tox, my_static_c_friend_message_cb) with
 * a static C function that looks up that global ref and calls back into
 * Kotlin via (*env)->CallVoidMethod(env, dispatcher, onFriendMessageMethodID,
 * ...).
 */
