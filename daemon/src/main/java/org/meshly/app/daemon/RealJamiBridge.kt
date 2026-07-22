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

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import net.jami.daemon.JamiService
import net.jami.daemon.StringMap
import net.jami.daemon.VectMap

/**
 * Real libjami engine, replacing [org.meshly.app.core.JamiBridge]'s Phase 1 mock once :daemon is
 * actually built (see /PHASE2_BUILD.md) and wired into :app.
 *
 * Every native call and constant below is copied from the real daemon source under
 * native/upstream/jami-daemon (SWIG .i files + src/jami/media_const.h + src/account_schema.h),
 * not guessed. Where the source didn't make something unambiguous, it's called out explicitly
 * instead of silently assuming.
 */
object RealJamiBridge {

    private val _events = MutableSharedFlow<RealJamiEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<RealJamiEvent> = _events.asSharedFlow()

    private var started = false

    init {
        // Real .so name: CMakeLists.txt names the JNI target "${PROJECT_NAME}-jni" and
        // project(jami-core ...) sets PROJECT_NAME=jami-core, so the artifact is
        // libjami-core-jni.so, NOT libjami.so (that name was only ever the Phase 1 mock's
        // placeholder in org.meshly.app.core.JamiBridge).
        System.loadLibrary("jami-core-jni")
    }

    /**
     * Starts the daemon. Maps to the free function `init(ConfigurationCallback*, Callback*,
     * PresenceCallback*, DataTransferCallback*, VideoCallback*, ConversationCallback*,
     * NetworkServiceCallback*)` in bin/jni/jni_interface.i, which internally calls
     * `libjami::init(...)`, registers every signal handler, then `libjami::start()` — one call
     * does the whole daemon bring-up, there is no separate "start" step.
     */
    @Synchronized
    fun startDaemon() {
        if (started) return
        JamiService.init(
            MeshlyConfigurationCallback(_events),
            MeshlyCallCallback(_events),
            MeshlyPresenceCallback(),
            MeshlyDataTransferCallback(),
            MeshlyVideoCallback(),
            MeshlyConversationCallback(),
            MeshlyNetworkServiceCallback()
        )
        started = true
    }

    /** Maps to `libjami::fini()` (native/upstream/jami-daemon/bin/jni/managerimpl.i). */
    @Synchronized
    fun stopDaemon() {
        if (!started) return
        JamiService.fini()
        started = false
    }

    /**
     * Creates a Jami account. `getAccountTemplate` + `addAccount` come from
     * configurationmanager.i. The type string "RING" is not a typo — it's libjami's real,
     * still-current constant `JamiAccount::ACCOUNT_TYPE_JAMI` (src/jamidht/jamiaccount_config.h),
     * kept as "RING" for on-disk config compatibility with the pre-rebrand Ring project.
     * `Account.alias` is the display name key (src/account_schema.h); username registration
     * (Jami name server) is a separate async call, `registerName(accountId, name, scheme,
     * password)`, not part of account creation itself.
     *
     * Returns the daemon's internal accountId (NOT the Jami ID / public key hash — that lives
     * inside the account's volatile/details map once registered, under a key this scaffolding
     * hasn't confirmed yet against a real running daemon).
     */
    fun createAccount(displayName: String?): String {
        val details: StringMap = JamiService.getAccountTemplate("RING")
        if (!displayName.isNullOrBlank()) {
            details["Account.alias"] = displayName
        }
        return JamiService.addAccount(details)
    }

    /**
     * Maps to `sendAccountTextMessage(accountId, to, message, flag)` (configurationmanager.i).
     * `message` is a StringMap because Jami messages carry a MIME-type-keyed payload map
     * (e.g. "text/plain" -> body) rather than a single string. The `flag` parameter's meaning
     * wasn't confirmed against daemon source in this pass — passing 0 (no special flag) until
     * verified.
     */
    fun sendTextMessage(accountId: String, toUri: String, text: String): Long {
        val message = StringMap()
        message["text/plain"] = text
        return JamiService.sendAccountTextMessage(accountId, toUri, message, 0)
    }

    /**
     * Maps to `placeCallWithMedia(accountId, to, mediaList)` (callmanager.i). Media attribute
     * keys (MEDIA_TYPE/ENABLED/MUTED/SOURCE/LABEL) come from src/jami/media_const.h's
     * MediaAttributeKey/MediaAttributeValue namespaces.
     */
    fun placeCall(accountId: String, toUri: String, withVideo: Boolean): String {
        val audioMedia = StringMap().apply {
            set("MEDIA_TYPE", "MEDIA_TYPE_AUDIO")
            set("ENABLED", "true")
            set("MUTED", "false")
            set("SOURCE", "")
            set("LABEL", "audio_0")
        }
        val mediaList = VectMap().apply { add(audioMedia) }
        if (withVideo) {
            mediaList.add(
                StringMap().apply {
                    set("MEDIA_TYPE", "MEDIA_TYPE_VIDEO")
                    set("ENABLED", "true")
                    set("MUTED", "false")
                    set("SOURCE", "")
                    set("LABEL", "video_0")
                }
            )
        }
        return JamiService.placeCallWithMedia(accountId, toUri, mediaList)
    }

    fun acceptCall(accountId: String, callId: String): Boolean = JamiService.accept(accountId, callId)

    fun hangUpCall(accountId: String, callId: String): Boolean = JamiService.hangUp(accountId, callId)

    fun toggleMute(accountId: String, callId: String, muted: Boolean): Boolean =
        JamiService.muteLocalMedia(accountId, callId, "MEDIA_TYPE_AUDIO", muted)
}
