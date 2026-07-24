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
 * Hand-written JNI wrapper around c-toxcore's tox.h/toxav.h C API. This is
 * the structural replacement for the removed jami-daemon scaffold's SWIG-
 * generated bindings: c-toxcore has no binding generator at all, so every
 * JNI entry point below is written by hand against the real tox_* and
 * toxav_* functions declared in native/upstream/c-toxcore's toxcore/tox.h
 * and toxav/toxav.h.
 *
 * The Tox/ToxAV instance handles are threaded through Kotlin as `jlong` --
 * raw `Tox *`/`ToxAV *` pointers cast to `jlong` and back. This mirrors the
 * conventional "opaque native handle" JNI pattern; c-toxcore itself has no
 * concept of a Java/JNI handle since tox.h/toxav.h are plain C APIs.
 *
 * --- Callback dispatch design ------------------------------------------
 *
 * tox_callback_*(Tox *tox, callback) takes NO per-call user_data; instead
 * tox_iterate(Tox *tox, void *user_data) threads ONE user_data pointer to
 * every core callback fired during that call (tox.h line 680, confirmed).
 * toxav_callback_*(ToxAV *av, callback, user_data), by contrast, takes its
 * OWN user_data at *registration* time (toxav.h lines 288/409/..., confirmed)
 * -- a different design from core tox's uniform-per-iterate model.
 *
 * Rather than juggle two different user_data conventions, every callback
 * trampoline below reads a single global `g_env`, refreshed at the start of
 * every toxIterate()/toxavIterate() JNI call. This is safe as long as both
 * are always invoked from the same OS thread (documented requirement on
 * ToxBridge.kt's Kotlin side, which drives both from one single-threaded
 * coroutine dispatcher) -- a JNIEnv* is only valid on the thread that
 * obtained it, but stays valid and consistent for the lifetime of that
 * thread's JVM attachment, so re-reading it at the top of each iterate call
 * is correct and avoids any AttachCurrentThread/DetachCurrentThread
 * bookkeeping entirely.
 *
 * A single global jobject ref to the Kotlin ToxCallbackAdapter instance
 * (ToxCallbackAdapter.kt) is bound once via toxRegisterCallbacks/
 * toxavRegisterCallbacks and reused for both tox_* and toxav_* callbacks --
 * the app only ever runs one account/one ToxAV instance at a time, so a
 * single global (not a per-handle registry) is the simplest correct design.
 */

#include <jni.h>
#include <stdlib.h>
#include <string.h>

#include "toxcore/tox.h"
#include "toxav/toxav.h"

/* ---- global callback-dispatch state --------------------------------- */

static JNIEnv *g_env = NULL;
static jobject g_adapter = NULL;

static jmethodID mid_onSelfConnectionStatus = NULL;
static jmethodID mid_onFriendRequest = NULL;
static jmethodID mid_onFriendMessage = NULL;
static jmethodID mid_onFriendReadReceipt = NULL;
static jmethodID mid_onFriendConnectionStatus = NULL;
static jmethodID mid_onFriendName = NULL;
static jmethodID mid_onFriendStatusMessage = NULL;
static jmethodID mid_onFriendStatus = NULL;
static jmethodID mid_onFriendTyping = NULL;
static jmethodID mid_onCallInvite = NULL;
static jmethodID mid_onCallState = NULL;
static jmethodID mid_onAudioBitRate = NULL;
static jmethodID mid_onVideoBitRate = NULL;
static jmethodID mid_onAudioFrame = NULL;
static jmethodID mid_onVideoFrame = NULL;

/*
 * Binds g_adapter (if not already bound) and caches every jmethodID against
 * ToxCallbackAdapter.kt's exact method signatures. Idempotent: safe to call
 * from both toxRegisterCallbacks and toxavRegisterCallbacks.
 */
static void bind_adapter(JNIEnv *env, jobject adapter) {
    if (g_adapter != NULL) {
        return;
    }
    g_adapter = (*env)->NewGlobalRef(env, adapter);
    jclass cls = (*env)->GetObjectClass(env, g_adapter);

    mid_onSelfConnectionStatus = (*env)->GetMethodID(env, cls, "onSelfConnectionStatus", "(I)V");
    mid_onFriendRequest = (*env)->GetMethodID(env, cls, "onFriendRequest", "([B[B)V");
    mid_onFriendMessage = (*env)->GetMethodID(env, cls, "onFriendMessage", "(II[B)V");
    mid_onFriendReadReceipt = (*env)->GetMethodID(env, cls, "onFriendReadReceipt", "(II)V");
    mid_onFriendConnectionStatus = (*env)->GetMethodID(env, cls, "onFriendConnectionStatus", "(II)V");
    mid_onFriendName = (*env)->GetMethodID(env, cls, "onFriendName", "(I[B)V");
    mid_onFriendStatusMessage = (*env)->GetMethodID(env, cls, "onFriendStatusMessage", "(I[B)V");
    mid_onFriendStatus = (*env)->GetMethodID(env, cls, "onFriendStatus", "(II)V");
    mid_onFriendTyping = (*env)->GetMethodID(env, cls, "onFriendTyping", "(IZ)V");
    mid_onCallInvite = (*env)->GetMethodID(env, cls, "onCallInvite", "(IZZ)V");
    mid_onCallState = (*env)->GetMethodID(env, cls, "onCallState", "(II)V");
    mid_onAudioBitRate = (*env)->GetMethodID(env, cls, "onAudioBitRate", "(II)V");
    mid_onVideoBitRate = (*env)->GetMethodID(env, cls, "onVideoBitRate", "(II)V");
    mid_onAudioFrame = (*env)->GetMethodID(env, cls, "onAudioFrame", "(I[SIII)V");
    mid_onVideoFrame = (*env)->GetMethodID(env, cls, "onVideoFrame", "(III[B[B[BIII)V");

    (*env)->DeleteLocalRef(env, cls);
}

/* ---- tox_callback_* trampolines --------------------------------------- */

static void cb_self_connection_status(Tox *tox, Tox_Connection status, void *user_data) {
    (void) tox; (void) user_data;
    if (!g_env || !g_adapter) return;
    (*g_env)->CallVoidMethod(g_env, g_adapter, mid_onSelfConnectionStatus, (jint) status);
}

static void cb_friend_request(Tox *tox, const uint8_t *public_key, const uint8_t *message, size_t length,
                               void *user_data) {
    (void) tox; (void) user_data;
    if (!g_env || !g_adapter) return;
    jbyteArray pk = (*g_env)->NewByteArray(g_env, 32); /* TOX_PUBLIC_KEY_SIZE */
    (*g_env)->SetByteArrayRegion(g_env, pk, 0, 32, (const jbyte *) public_key);
    jbyteArray msg = (*g_env)->NewByteArray(g_env, (jsize) length);
    (*g_env)->SetByteArrayRegion(g_env, msg, 0, (jsize) length, (const jbyte *) message);
    (*g_env)->CallVoidMethod(g_env, g_adapter, mid_onFriendRequest, pk, msg);
    (*g_env)->DeleteLocalRef(g_env, pk);
    (*g_env)->DeleteLocalRef(g_env, msg);
}

static void cb_friend_message(Tox *tox, uint32_t friend_number, Tox_Message_Type type, const uint8_t *message,
                               size_t length, void *user_data) {
    (void) tox; (void) user_data;
    if (!g_env || !g_adapter) return;
    jbyteArray msg = (*g_env)->NewByteArray(g_env, (jsize) length);
    (*g_env)->SetByteArrayRegion(g_env, msg, 0, (jsize) length, (const jbyte *) message);
    (*g_env)->CallVoidMethod(g_env, g_adapter, mid_onFriendMessage, (jint) friend_number, (jint) type, msg);
    (*g_env)->DeleteLocalRef(g_env, msg);
}

static void cb_friend_read_receipt(Tox *tox, uint32_t friend_number, uint32_t message_id, void *user_data) {
    (void) tox; (void) user_data;
    if (!g_env || !g_adapter) return;
    (*g_env)->CallVoidMethod(g_env, g_adapter, mid_onFriendReadReceipt, (jint) friend_number, (jint) message_id);
}

static void cb_friend_connection_status(Tox *tox, uint32_t friend_number, Tox_Connection status, void *user_data) {
    (void) tox; (void) user_data;
    if (!g_env || !g_adapter) return;
    (*g_env)->CallVoidMethod(g_env, g_adapter, mid_onFriendConnectionStatus, (jint) friend_number, (jint) status);
}

static void cb_friend_name(Tox *tox, uint32_t friend_number, const uint8_t *name, size_t length, void *user_data) {
    (void) tox; (void) user_data;
    if (!g_env || !g_adapter) return;
    jbyteArray n = (*g_env)->NewByteArray(g_env, (jsize) length);
    (*g_env)->SetByteArrayRegion(g_env, n, 0, (jsize) length, (const jbyte *) name);
    (*g_env)->CallVoidMethod(g_env, g_adapter, mid_onFriendName, (jint) friend_number, n);
    (*g_env)->DeleteLocalRef(g_env, n);
}

static void cb_friend_status_message(Tox *tox, uint32_t friend_number, const uint8_t *message, size_t length,
                                      void *user_data) {
    (void) tox; (void) user_data;
    if (!g_env || !g_adapter) return;
    jbyteArray m = (*g_env)->NewByteArray(g_env, (jsize) length);
    (*g_env)->SetByteArrayRegion(g_env, m, 0, (jsize) length, (const jbyte *) message);
    (*g_env)->CallVoidMethod(g_env, g_adapter, mid_onFriendStatusMessage, (jint) friend_number, m);
    (*g_env)->DeleteLocalRef(g_env, m);
}

static void cb_friend_status(Tox *tox, uint32_t friend_number, Tox_User_Status status, void *user_data) {
    (void) tox; (void) user_data;
    if (!g_env || !g_adapter) return;
    (*g_env)->CallVoidMethod(g_env, g_adapter, mid_onFriendStatus, (jint) friend_number, (jint) status);
}

static void cb_friend_typing(Tox *tox, uint32_t friend_number, bool is_typing, void *user_data) {
    (void) tox; (void) user_data;
    if (!g_env || !g_adapter) return;
    (*g_env)->CallVoidMethod(g_env, g_adapter, mid_onFriendTyping, (jint) friend_number, (jboolean) is_typing);
}

/* ---- toxav_callback_* trampolines -------------------------------------- */

static void cb_call(ToxAV *av, uint32_t friend_number, bool audio_enabled, bool video_enabled, void *user_data) {
    (void) av; (void) user_data;
    if (!g_env || !g_adapter) return;
    (*g_env)->CallVoidMethod(g_env, g_adapter, mid_onCallInvite, (jint) friend_number,
                              (jboolean) audio_enabled, (jboolean) video_enabled);
}

static void cb_call_state(ToxAV *av, uint32_t friend_number, uint32_t state, void *user_data) {
    (void) av; (void) user_data;
    if (!g_env || !g_adapter) return;
    (*g_env)->CallVoidMethod(g_env, g_adapter, mid_onCallState, (jint) friend_number, (jint) state);
}

static void cb_audio_bit_rate(ToxAV *av, uint32_t friend_number, uint32_t audio_bit_rate, void *user_data) {
    (void) av; (void) user_data;
    if (!g_env || !g_adapter) return;
    (*g_env)->CallVoidMethod(g_env, g_adapter, mid_onAudioBitRate, (jint) friend_number, (jint) audio_bit_rate);
}

static void cb_video_bit_rate(ToxAV *av, uint32_t friend_number, uint32_t video_bit_rate, void *user_data) {
    (void) av; (void) user_data;
    if (!g_env || !g_adapter) return;
    (*g_env)->CallVoidMethod(g_env, g_adapter, mid_onVideoBitRate, (jint) friend_number, (jint) video_bit_rate);
}

static void cb_audio_receive_frame(ToxAV *av, uint32_t friend_number, const int16_t *pcm, size_t sample_count,
                                    uint8_t channels, uint32_t sampling_rate, void *user_data) {
    (void) av; (void) user_data;
    if (!g_env || !g_adapter) return;
    jsize total_samples = (jsize) (sample_count * channels);
    jshortArray arr = (*g_env)->NewShortArray(g_env, total_samples);
    (*g_env)->SetShortArrayRegion(g_env, arr, 0, total_samples, (const jshort *) pcm);
    (*g_env)->CallVoidMethod(g_env, g_adapter, mid_onAudioFrame, (jint) friend_number, arr,
                              (jint) sample_count, (jint) channels, (jint) sampling_rate);
    (*g_env)->DeleteLocalRef(g_env, arr);
}

static void cb_video_receive_frame(ToxAV *av, uint32_t friend_number, uint16_t width, uint16_t height,
                                    const uint8_t *y, const uint8_t *u, const uint8_t *v,
                                    int32_t ystride, int32_t ustride, int32_t vstride, void *user_data) {
    (void) av; (void) user_data;
    if (!g_env || !g_adapter) return;

    int32_t y_abs = ystride < 0 ? -ystride : ystride;
    int32_t u_abs = ustride < 0 ? -ustride : ustride;
    int32_t v_abs = vstride < 0 ? -vstride : vstride;
    jsize y_size = (jsize) ((y_abs > width ? y_abs : width) * height);
    jsize uv_size = (jsize) ((u_abs > width / 2 ? u_abs : width / 2) * (height / 2));
    jsize v_size = (jsize) ((v_abs > width / 2 ? v_abs : width / 2) * (height / 2));

    jbyteArray ya = (*g_env)->NewByteArray(g_env, y_size);
    (*g_env)->SetByteArrayRegion(g_env, ya, 0, y_size, (const jbyte *) y);
    jbyteArray ua = (*g_env)->NewByteArray(g_env, uv_size);
    (*g_env)->SetByteArrayRegion(g_env, ua, 0, uv_size, (const jbyte *) u);
    jbyteArray va = (*g_env)->NewByteArray(g_env, v_size);
    (*g_env)->SetByteArrayRegion(g_env, va, 0, v_size, (const jbyte *) v);

    (*g_env)->CallVoidMethod(g_env, g_adapter, mid_onVideoFrame, (jint) friend_number,
                              (jint) width, (jint) height, ya, ua, va,
                              (jint) ystride, (jint) ustride, (jint) vstride);

    (*g_env)->DeleteLocalRef(g_env, ya);
    (*g_env)->DeleteLocalRef(g_env, ua);
    (*g_env)->DeleteLocalRef(g_env, va);
}

/* ======================================================================
 * Core tox_* JNI entry points
 * ====================================================================== */

/*
 * Java_org_meshly_app_daemontox_ToxNative_toxNew
 *
 * Wraps `Tox *tox_new(const Tox_Options *options, Tox_Err_New *error);`
 * (tox.h line 504). Builds real options via tox_options_new/_free
 * (tox_options.h, confirmed) instead of passing NULL: UDP/IPv6/local
 * discovery/hole punching all enabled (the defaults a normal Tox client
 * wants), and if `savedata` is non-null/non-empty, loads it as
 * TOX_SAVEDATA_TYPE_TOX_SAVE (tox_options.h line 59, confirmed) so an
 * existing identity is restored instead of a new one being generated.
 */
JNIEXPORT jlong JNICALL
Java_org_meshly_app_daemontox_ToxNative_toxNew(JNIEnv *env, jobject thiz, jbyteArray savedata) {
    (void) thiz;

    Tox_Err_Options_New opt_error;
    Tox_Options *options = tox_options_new(&opt_error);
    if (options == NULL) {
        return 0;
    }

    tox_options_set_udp_enabled(options, true);
    tox_options_set_ipv6_enabled(options, true);
    tox_options_set_local_discovery_enabled(options, true);
    tox_options_set_hole_punching_enabled(options, true);

    jbyte *save_bytes = NULL;
    if (savedata != NULL) {
        jsize save_len = (*env)->GetArrayLength(env, savedata);
        if (save_len > 0) {
            save_bytes = (*env)->GetByteArrayElements(env, savedata, NULL);
            tox_options_set_savedata_type(options, TOX_SAVEDATA_TYPE_TOX_SAVE);
            tox_options_set_savedata_data(options, (const uint8_t *) save_bytes, (size_t) save_len);
        }
    }

    Tox_Err_New error;
    Tox *tox = tox_new(options, &error);

    if (save_bytes != NULL) {
        (*env)->ReleaseByteArrayElements(env, savedata, save_bytes, JNI_ABORT);
    }
    tox_options_free(options);

    return (jlong) (intptr_t) tox;
}

/*
 * Java_org_meshly_app_daemontox_ToxNative_toxKill
 * Wraps `void tox_kill(Tox *tox);` (tox.h line 513).
 */
JNIEXPORT void JNICALL
Java_org_meshly_app_daemontox_ToxNative_toxKill(JNIEnv *env, jobject thiz, jlong handle) {
    (void) env;
    (void) thiz;
    tox_kill((Tox *) (intptr_t) handle);
}

/*
 * Java_org_meshly_app_daemontox_ToxNative_toxGetSavedataSize
 * Wraps `size_t tox_get_savedata_size(const Tox *tox);` (tox.h line 523).
 */
JNIEXPORT jint JNICALL
Java_org_meshly_app_daemontox_ToxNative_toxGetSavedataSize(JNIEnv *env, jobject thiz, jlong handle) {
    (void) env;
    (void) thiz;
    Tox *tox = (Tox *) (intptr_t) handle;
    return (jint) tox_get_savedata_size(tox);
}

/*
 * Java_org_meshly_app_daemontox_ToxNative_toxGetSavedata
 * Wraps `void tox_get_savedata(const Tox *tox, uint8_t savedata[]);`
 * (tox.h line 533) -- used to persist the account (keys, friend list, ...)
 * across app restarts by round-tripping through toxNew's `savedata` param.
 */
JNIEXPORT jbyteArray JNICALL
Java_org_meshly_app_daemontox_ToxNative_toxGetSavedata(JNIEnv *env, jobject thiz, jlong handle) {
    (void) thiz;
    Tox *tox = (Tox *) (intptr_t) handle;
    size_t size = tox_get_savedata_size(tox);
    uint8_t *buf = (uint8_t *) malloc(size);
    tox_get_savedata(tox, buf);
    jbyteArray result = (*env)->NewByteArray(env, (jsize) size);
    (*env)->SetByteArrayRegion(env, result, 0, (jsize) size, (const jbyte *) buf);
    free(buf);
    return result;
}

/*
 * Java_org_meshly_app_daemontox_ToxNative_toxRegisterCallbacks
 *
 * Binds the Kotlin ToxCallbackAdapter (see this file's top-of-file doc for
 * the dispatch design) and registers every tox_callback_* handler.
 */
JNIEXPORT void JNICALL
Java_org_meshly_app_daemontox_ToxNative_toxRegisterCallbacks(
    JNIEnv *env, jobject thiz, jlong handle, jobject adapter) {
    (void) thiz;
    Tox *tox = (Tox *) (intptr_t) handle;
    bind_adapter(env, adapter);

    tox_callback_self_connection_status(tox, cb_self_connection_status);
    tox_callback_friend_request(tox, cb_friend_request);
    tox_callback_friend_message(tox, cb_friend_message);
    tox_callback_friend_read_receipt(tox, cb_friend_read_receipt);
    tox_callback_friend_connection_status(tox, cb_friend_connection_status);
    tox_callback_friend_name(tox, cb_friend_name);
    tox_callback_friend_status_message(tox, cb_friend_status_message);
    tox_callback_friend_status(tox, cb_friend_status);
    tox_callback_friend_typing(tox, cb_friend_typing);
}

/*
 * Java_org_meshly_app_daemontox_ToxNative_toxBootstrap
 * Wraps `bool tox_bootstrap(...)` (tox.h line 584). Unchanged from the
 * original scaffold.
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
 * Java_org_meshly_app_daemontox_ToxNative_toxAddTcpRelay
 *
 * Wraps `bool tox_add_tcp_relay(...)` (tox.h line 600) -- distinct from
 * tox_bootstrap: this registers a node as a TCP relay for onion routing,
 * which is what lets friend/DHT rendezvous keep working when a network
 * blocks or degrades outbound UDP (a real, previously-missing capability:
 * without any TCP relay registered, this client had no fallback path on
 * UDP-hostile networks, even though tox_bootstrap traffic looked "active").
 */
JNIEXPORT jboolean JNICALL
Java_org_meshly_app_daemontox_ToxNative_toxAddTcpRelay(
    JNIEnv *env, jobject thiz, jlong handle, jstring host, jint port, jbyteArray publicKey) {
    (void) thiz;
    Tox *tox = (Tox *) (intptr_t) handle;
    const char *host_chars = (*env)->GetStringUTFChars(env, host, NULL);
    jbyte *pk_bytes = (*env)->GetByteArrayElements(env, publicKey, NULL);

    Tox_Err_Bootstrap error;
    bool ok = tox_add_tcp_relay(tox, host_chars, (uint16_t) port, (const uint8_t *) pk_bytes, &error);

    (*env)->ReleaseStringUTFChars(env, host, host_chars);
    (*env)->ReleaseByteArrayElements(env, publicKey, pk_bytes, JNI_ABORT);
    return (jboolean) ok;
}

/*
 * Java_org_meshly_app_daemontox_ToxNative_toxSelfGetAddress
 * Wraps `void tox_self_get_address(...)` (tox.h line 698). Unchanged.
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
 * Wraps `uint32_t tox_iteration_interval(...)` (tox.h line 672). Unchanged.
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
 * Wraps `void tox_iterate(Tox *tox, void *user_data);` (tox.h line 680).
 * Refreshes g_env (see top-of-file doc on the callback-dispatch design)
 * before driving the event loop step so every tox_callback_* trampoline
 * fired from within this call has a valid, current JNIEnv*.
 */
JNIEXPORT void JNICALL
Java_org_meshly_app_daemontox_ToxNative_toxIterate(JNIEnv *env, jobject thiz, jlong handle) {
    (void) thiz;
    g_env = env;
    Tox *tox = (Tox *) (intptr_t) handle;
    tox_iterate(tox, NULL);
}

/*
 * Java_org_meshly_app_daemontox_ToxNative_toxFriendAdd
 * Wraps `Tox_Friend_Number tox_friend_add(...)` (tox.h lines 935-938).
 * Unchanged.
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
 * Java_org_meshly_app_daemontox_ToxNative_toxFriendAddNorequest
 *
 * Wraps `Tox_Friend_Number tox_friend_add_norequest(Tox *tox, const
 * Tox_Public_Key public_key, Tox_Err_Friend_Add *error);` (tox.h lines
 * 958-959) -- used for the app's "accept incoming friend request" flow,
 * where a request has already arrived via cb_friend_request and the app
 * just needs to confirm the peer as a mutual friend without sending a new
 * request of its own. `publicKey` is 32 bytes (TOX_PUBLIC_KEY_SIZE), not
 * the 38-byte address `toxFriendAdd` takes.
 */
JNIEXPORT jint JNICALL
Java_org_meshly_app_daemontox_ToxNative_toxFriendAddNorequest(
    JNIEnv *env, jobject thiz, jlong handle, jbyteArray publicKey) {
    (void) thiz;
    Tox *tox = (Tox *) (intptr_t) handle;

    jbyte *pk_bytes = (*env)->GetByteArrayElements(env, publicKey, NULL);
    Tox_Err_Friend_Add error;
    uint32_t friend_number = tox_friend_add_norequest(tox, (const uint8_t *) pk_bytes, &error);
    (*env)->ReleaseByteArrayElements(env, publicKey, pk_bytes, JNI_ABORT);
    return (jint) friend_number;
}

/*
 * Java_org_meshly_app_daemontox_ToxNative_toxFriendDelete
 * Wraps `bool tox_friend_delete(...)` (tox.h line 989). Unchanged.
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
 * Wraps `Tox_Friend_Message_Id tox_friend_send_message(...)` (tox.h lines
 * 1443-1445). Now takes `messageType` (0 = TOX_MESSAGE_TYPE_NORMAL, 1 =
 * TOX_MESSAGE_TYPE_ACTION, tox.h's Tox_Message_Type enum) instead of the
 * original scaffold's hardcoded NORMAL, matching
 * ToxCallbackAdapter.onFriendMessage's own messageType param for symmetry.
 */
JNIEXPORT jint JNICALL
Java_org_meshly_app_daemontox_ToxNative_toxFriendSendMessage(
    JNIEnv *env, jobject thiz, jlong handle, jint friendNumber, jint messageType, jbyteArray message) {
    (void) thiz;
    Tox *tox = (Tox *) (intptr_t) handle;

    jbyte *msg_bytes = (*env)->GetByteArrayElements(env, message, NULL);
    jsize msg_len = (*env)->GetArrayLength(env, message);

    Tox_Err_Friend_Send_Message error;
    uint32_t message_id = tox_friend_send_message(
        tox, (uint32_t) friendNumber, (Tox_Message_Type) messageType,
        (const uint8_t *) msg_bytes, (size_t) msg_len, &error);

    (*env)->ReleaseByteArrayElements(env, message, msg_bytes, JNI_ABORT);
    return (jint) message_id;
}

/*
 * Java_org_meshly_app_daemontox_ToxNative_toxFriendGetConnectionStatus
 * Wraps `Tox_Connection tox_friend_get_connection_status(...)` (tox.h lines
 * 1283-1284). Unchanged.
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
 * Wraps `Tox_Connection tox_self_get_connection_status(...)` (tox.h line
 * 646). Unchanged.
 */
JNIEXPORT jint JNICALL
Java_org_meshly_app_daemontox_ToxNative_toxSelfGetConnectionStatus(JNIEnv *env, jobject thiz, jlong handle) {
    (void) env;
    (void) thiz;
    Tox *tox = (Tox *) (intptr_t) handle;
    return (jint) tox_self_get_connection_status(tox);
}

/* ======================================================================
 * ToxAV JNI entry points (native/upstream/c-toxcore/toxav/toxav.h)
 * ====================================================================== */

/*
 * Java_org_meshly_app_daemontox_ToxNative_toxavNew
 * Wraps `ToxAV *toxav_new(Tox *tox, Toxav_Err_New *error);` (toxav.h line
 * 137). Must be called after toxNew but the resulting ToxAV must iterate
 * independently of (though typically alongside) the owning Tox's own
 * tox_iterate loop.
 */
JNIEXPORT jlong JNICALL
Java_org_meshly_app_daemontox_ToxNative_toxavNew(JNIEnv *env, jobject thiz, jlong toxHandle) {
    (void) env;
    (void) thiz;
    Tox *tox = (Tox *) (intptr_t) toxHandle;
    Toxav_Err_New error;
    ToxAV *av = toxav_new(tox, &error);
    return (jlong) (intptr_t) av;
}

/*
 * Java_org_meshly_app_daemontox_ToxNative_toxavKill
 * Wraps `void toxav_kill(ToxAV *av);` (toxav.h line 146).
 */
JNIEXPORT void JNICALL
Java_org_meshly_app_daemontox_ToxNative_toxavKill(JNIEnv *env, jobject thiz, jlong avHandle) {
    (void) env;
    (void) thiz;
    toxav_kill((ToxAV *) (intptr_t) avHandle);
}

/*
 * Java_org_meshly_app_daemontox_ToxNative_toxavRegisterCallbacks
 *
 * Binds the Kotlin ToxCallbackAdapter (idempotent, shared with
 * toxRegisterCallbacks -- see this file's top-of-file doc) and registers
 * every toxav_callback_* handler. Unlike tox_callback_*, each of these
 * takes its own user_data at registration time (toxav.h, confirmed) --
 * passed as NULL here since every trampoline reads the shared g_env global
 * instead (refreshed by toxavIterate, not per-callback user_data).
 */
JNIEXPORT void JNICALL
Java_org_meshly_app_daemontox_ToxNative_toxavRegisterCallbacks(
    JNIEnv *env, jobject thiz, jlong avHandle, jobject adapter) {
    (void) thiz;
    ToxAV *av = (ToxAV *) (intptr_t) avHandle;
    bind_adapter(env, adapter);

    toxav_callback_call(av, cb_call, NULL);
    toxav_callback_call_state(av, cb_call_state, NULL);
    toxav_callback_audio_bit_rate(av, cb_audio_bit_rate, NULL);
    toxav_callback_video_bit_rate(av, cb_video_bit_rate, NULL);
    toxav_callback_audio_receive_frame(av, cb_audio_receive_frame, NULL);
    toxav_callback_video_receive_frame(av, cb_video_receive_frame, NULL);
}

/*
 * Java_org_meshly_app_daemontox_ToxNative_toxavIterationInterval
 * Wraps `uint32_t toxav_iteration_interval(const ToxAV *av);` (toxav.h line
 * 164).
 */
JNIEXPORT jint JNICALL
Java_org_meshly_app_daemontox_ToxNative_toxavIterationInterval(JNIEnv *env, jobject thiz, jlong avHandle) {
    (void) env;
    (void) thiz;
    ToxAV *av = (ToxAV *) (intptr_t) avHandle;
    return (jint) toxav_iteration_interval(av);
}

/*
 * Java_org_meshly_app_daemontox_ToxNative_toxavIterate
 *
 * Wraps `void toxav_iterate(ToxAV *av);` (toxav.h line 171) -- note, unlike
 * tox_iterate, this takes no user_data param at all; every toxav_callback_*
 * trampoline instead reads the shared g_env global refreshed here (see
 * top-of-file doc). Must be called from the same thread as toxIterate for
 * that shared global to stay valid.
 */
JNIEXPORT void JNICALL
Java_org_meshly_app_daemontox_ToxNative_toxavIterate(JNIEnv *env, jobject thiz, jlong avHandle) {
    (void) thiz;
    g_env = env;
    ToxAV *av = (ToxAV *) (intptr_t) avHandle;
    toxav_iterate(av);
}

/*
 * Java_org_meshly_app_daemontox_ToxNative_toxavCall
 * Wraps `bool toxav_call(ToxAV *av, Tox_Friend_Number friend_number,
 * uint32_t audio_bit_rate, uint32_t video_bit_rate, Toxav_Err_Call *error);`
 * (toxav.h line 272).
 */
JNIEXPORT jboolean JNICALL
Java_org_meshly_app_daemontox_ToxNative_toxavCall(
    JNIEnv *env, jobject thiz, jlong avHandle, jint friendNumber, jint audioBitRate, jint videoBitRate) {
    (void) env;
    (void) thiz;
    ToxAV *av = (ToxAV *) (intptr_t) avHandle;
    Toxav_Err_Call error;
    return (jboolean) toxav_call(
        av, (uint32_t) friendNumber, (uint32_t) audioBitRate, (uint32_t) videoBitRate, &error);
}

/*
 * Java_org_meshly_app_daemontox_ToxNative_toxavAnswer
 * Wraps `bool toxav_answer(ToxAV *av, Tox_Friend_Number friend_number,
 * uint32_t audio_bit_rate, uint32_t video_bit_rate, Toxav_Err_Answer
 * *error);` (toxav.h line 341).
 */
JNIEXPORT jboolean JNICALL
Java_org_meshly_app_daemontox_ToxNative_toxavAnswer(
    JNIEnv *env, jobject thiz, jlong avHandle, jint friendNumber, jint audioBitRate, jint videoBitRate) {
    (void) env;
    (void) thiz;
    ToxAV *av = (ToxAV *) (intptr_t) avHandle;
    Toxav_Err_Answer error;
    return (jboolean) toxav_answer(
        av, (uint32_t) friendNumber, (uint32_t) audioBitRate, (uint32_t) videoBitRate, &error);
}

/*
 * Java_org_meshly_app_daemontox_ToxNative_toxavCallControl
 * Wraps `bool toxav_call_control(ToxAV *av, Tox_Friend_Number friend_number,
 * Toxav_Call_Control control, Toxav_Err_Call_Control *error);` (toxav.h
 * line 503). `control` matches the Toxav_Call_Control enum ordinals
 * (RESUME=0, PAUSE=1, CANCEL=2, MUTE_AUDIO=3, UNMUTE_AUDIO=4, HIDE_VIDEO=5,
 * SHOW_VIDEO=6) -- passed through as a raw Int, not re-declared here.
 */
JNIEXPORT jboolean JNICALL
Java_org_meshly_app_daemontox_ToxNative_toxavCallControl(
    JNIEnv *env, jobject thiz, jlong avHandle, jint friendNumber, jint control) {
    (void) env;
    (void) thiz;
    ToxAV *av = (ToxAV *) (intptr_t) avHandle;
    Toxav_Err_Call_Control error;
    return (jboolean) toxav_call_control(
        av, (uint32_t) friendNumber, (Toxav_Call_Control) control, &error);
}

/*
 * Java_org_meshly_app_daemontox_ToxNative_toxavAudioSetBitRate
 * Wraps `bool toxav_audio_set_bit_rate(ToxAV *av, Tox_Friend_Number
 * friend_number, uint32_t bit_rate, Toxav_Err_Bit_Rate_Set *error);`
 * (toxav.h line 626).
 */
JNIEXPORT jboolean JNICALL
Java_org_meshly_app_daemontox_ToxNative_toxavAudioSetBitRate(
    JNIEnv *env, jobject thiz, jlong avHandle, jint friendNumber, jint bitRate) {
    (void) env;
    (void) thiz;
    ToxAV *av = (ToxAV *) (intptr_t) avHandle;
    Toxav_Err_Bit_Rate_Set error;
    return (jboolean) toxav_audio_set_bit_rate(av, (uint32_t) friendNumber, (uint32_t) bitRate, &error);
}

/*
 * Java_org_meshly_app_daemontox_ToxNative_toxavVideoSetBitRate
 * Wraps `bool toxav_video_set_bit_rate(ToxAV *av, Tox_Friend_Number
 * friend_number, uint32_t bit_rate, Toxav_Err_Bit_Rate_Set *error);`
 * (toxav.h line 677).
 */
JNIEXPORT jboolean JNICALL
Java_org_meshly_app_daemontox_ToxNative_toxavVideoSetBitRate(
    JNIEnv *env, jobject thiz, jlong avHandle, jint friendNumber, jint bitRate) {
    (void) env;
    (void) thiz;
    ToxAV *av = (ToxAV *) (intptr_t) avHandle;
    Toxav_Err_Bit_Rate_Set error;
    return (jboolean) toxav_video_set_bit_rate(av, (uint32_t) friendNumber, (uint32_t) bitRate, &error);
}

/*
 * Java_org_meshly_app_daemontox_ToxNative_toxavAudioSendFrame
 * Wraps `bool toxav_audio_send_frame(ToxAV *av, Tox_Friend_Number
 * friend_number, const int16_t pcm[], size_t sample_count, uint8_t
 * channels, uint32_t sampling_rate, Toxav_Err_Send_Frame *error);` (toxav.h
 * line 614). `pcm` length must be exactly `sample_count * channels`
 * (toxav.h's own doc, confirmed).
 */
JNIEXPORT jboolean JNICALL
Java_org_meshly_app_daemontox_ToxNative_toxavAudioSendFrame(
    JNIEnv *env, jobject thiz, jlong avHandle, jint friendNumber, jshortArray pcm,
    jint sampleCount, jint channels, jint samplingRate) {
    (void) thiz;
    ToxAV *av = (ToxAV *) (intptr_t) avHandle;
    jshort *pcm_bytes = (*env)->GetShortArrayElements(env, pcm, NULL);

    Toxav_Err_Send_Frame error;
    bool ok = toxav_audio_send_frame(
        av, (uint32_t) friendNumber, (const int16_t *) pcm_bytes,
        (size_t) sampleCount, (uint8_t) channels, (uint32_t) samplingRate, &error);

    (*env)->ReleaseShortArrayElements(env, pcm, pcm_bytes, JNI_ABORT);
    return (jboolean) ok;
}

/*
 * Java_org_meshly_app_daemontox_ToxNative_toxavVideoSendFrame
 * Wraps `bool toxav_video_send_frame(ToxAV *av, Tox_Friend_Number
 * friend_number, uint16_t width, uint16_t height, const uint8_t y[], const
 * uint8_t u[], const uint8_t v[], Toxav_Err_Send_Frame *error);` (toxav.h
 * line 661). Planar YUV420: Y is width*height bytes, U/V are each
 * (width/2)*(height/2) bytes (toxav.h's own doc, confirmed).
 */
JNIEXPORT jboolean JNICALL
Java_org_meshly_app_daemontox_ToxNative_toxavVideoSendFrame(
    JNIEnv *env, jobject thiz, jlong avHandle, jint friendNumber, jint width, jint height,
    jbyteArray y, jbyteArray u, jbyteArray v) {
    (void) thiz;
    ToxAV *av = (ToxAV *) (intptr_t) avHandle;

    jbyte *y_bytes = (*env)->GetByteArrayElements(env, y, NULL);
    jbyte *u_bytes = (*env)->GetByteArrayElements(env, u, NULL);
    jbyte *v_bytes = (*env)->GetByteArrayElements(env, v, NULL);

    Toxav_Err_Send_Frame error;
    bool ok = toxav_video_send_frame(
        av, (uint32_t) friendNumber, (uint16_t) width, (uint16_t) height,
        (const uint8_t *) y_bytes, (const uint8_t *) u_bytes, (const uint8_t *) v_bytes, &error);

    (*env)->ReleaseByteArrayElements(env, y, y_bytes, JNI_ABORT);
    (*env)->ReleaseByteArrayElements(env, u, u_bytes, JNI_ABORT);
    (*env)->ReleaseByteArrayElements(env, v, v_bytes, JNI_ABORT);
    return (jboolean) ok;
}
