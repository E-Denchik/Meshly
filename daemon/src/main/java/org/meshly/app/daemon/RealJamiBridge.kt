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
import net.jami.daemon.Blob
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

    // libjami::Account::MessageStates::DISPLAYED (src/jami/account_const.h) — see
    // setMessageDisplayed's doc below for the full enum and why there's no DELIVERED value.
    private const val MESSAGE_STATE_DISPLAYED = 3

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
     * Returns the daemon's internal accountId (NOT the Jami ID / public key hash — use
     * [getJamiId] for that).
     */
    fun createAccount(displayName: String?): String {
        val details: StringMap = JamiService.getAccountTemplate("RING")
        if (!displayName.isNullOrBlank()) {
            details["Account.alias"] = displayName
        }
        return JamiService.addAccount(details)
    }

    /** All account IDs known to the daemon, in daemon-internal accountId form. */
    fun getAccountList(): List<String> {
        val raw = JamiService.getAccountList()
        return (0 until raw.size()).map { raw[it] }
    }

    /**
     * Persistent account config. Maps to `getAccountDetails(accountId)` (configurationmanager.i).
     * Key of interest: `Account.username` — for a Jami/"RING"-type account this is set to the
     * account's identity hash the moment the account is created
     * (`config_->username = info->accountId` / `conf.username = info.accountId`, both in
     * src/jamidht/jamiaccount.cpp), i.e. it IS the Jami ID / URI you give out to be added as a
     * contact or called — not to be confused with the daemon's internal `accountId` parameter
     * these methods take, which is a separate, purely-local identifier.
     */
    fun getAccountDetails(accountId: String): StringMap = JamiService.getAccountDetails(accountId)

    /**
     * Transient runtime state. Maps to `getVolatileAccountDetails(accountId)`. Keys (all from
     * src/jami/account_const.h's `VolatileProperties`/`VolatileProperties::Registration`):
     * `Account.registeredName` (present only once a username lookup/registration has resolved),
     * `Account.registrationStatus`/`Account.registrationCode`/`Account.registrationDescription`,
     * `Account.deviceAnnounced` ("true"/"false").
     */
    fun getVolatileAccountDetails(accountId: String): StringMap = JamiService.getVolatileAccountDetails(accountId)

    /** Convenience accessor: the actual Jami ID (see [getAccountDetails]'s doc for why). */
    fun getJamiId(accountId: String): String = getAccountDetails(accountId).get("Account.username").orEmpty()

    /** Convenience accessor for the resolved username, if any name registration succeeded. */
    fun getRegisteredName(accountId: String): String =
        getVolatileAccountDetails(accountId).get("Account.registeredName").orEmpty()

    /**
     * Registers a username on the Jami name server for this account. Maps to
     * `registerName(account, name, scheme, password)` (configurationmanager.i). Result arrives
     * asynchronously via `ConfigurationCallback.nameRegistrationEnded`, not as this call's return
     * value — `scheme`/`password` aren't used for the default Jami name server and empty strings
     * are the norm there (not confirmed against a real running daemon).
     */
    fun registerName(accountId: String, name: String): Boolean =
        JamiService.registerName(accountId, name, "", "")

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
     * Maps to `getLastMessages(accountId, base_timestamp)` (configurationmanager.i), returning
     * every message the daemon has recorded for this account since `baseTimestampMs`. Pass 0 to
     * fetch full history. `libjami::Message` is wrapped via `RealChatMessage`.
     */
    fun getLastMessages(accountId: String, baseTimestampMs: Long): List<RealChatMessage> {
        val raw = JamiService.getLastMessages(accountId, baseTimestampMs)
        return (0 until raw.size()).map { RealChatMessage.fromMessage(raw[it]) }
    }

    /**
     * Reports this account's typing state to a peer/conversation. Maps to
     * `setIsComposing(accountId, conversationUri, isWriting)`.
     */
    fun setIsComposing(accountId: String, conversationUri: String, isWriting: Boolean) {
        JamiService.setIsComposing(accountId, conversationUri, isWriting)
    }

    /**
     * Marks a message as read/displayed, which also sends a read receipt to the peer. Maps to
     * `setMessageDisplayed(accountId, conversationUri, messageId, status)`. `status` is
     * `libjami::Account::MessageStates` (src/jami/account_const.h): UNKNOWN=0, SENDING=1,
     * SENT=2, DISPLAYED=3, FAILURE=4, CANCELLED=5 — note there is no separate "delivered" state
     * at this level, only sent/displayed, unlike Meshly's Phase 1 mock `MessageStatus` enum
     * (SENDING/SENT/DELIVERED/READ/FAILED); expect to collapse DELIVERED and READ onto
     * DISPLAYED=3 when wiring this up for real.
     */
    fun setMessageDisplayed(accountId: String, conversationUri: String, messageId: String): Boolean =
        JamiService.setMessageDisplayed(accountId, conversationUri, messageId, MESSAGE_STATE_DISPLAYED)

    /**
     * Looks up a message's delivery state by its numeric id (the value [sendTextMessage]
     * returns). Maps to the accountId-scoped overload of `getMessageStatus`.
     *
     * CAUTION: `sendAccountTextMessage`'s numeric return id and the string `messageId` used by
     * `incomingAccountMessage`/`accountMessageStatusChanged`/`setMessageDisplayed` are not
     * confirmed to be the same identifier space — this hasn't been checked against a real
     * running daemon. Don't assume you can feed one into the other's API without verifying first.
     */
    fun getMessageStatus(accountId: String, messageId: Long): Int =
        JamiService.getMessageStatus(accountId, messageId)

    /**
     * Maps to `placeCallWithMedia(accountId, to, mediaList)` (callmanager.i). Media attribute
     * keys (MEDIA_TYPE/ENABLED/MUTED/SOURCE/LABEL) come from src/jami/media_const.h's
     * MediaAttributeKey/MediaAttributeValue namespaces.
     */
    fun placeCall(accountId: String, toUri: String, withVideo: Boolean): String {
        val audioMedia = StringMap().apply {
            put("MEDIA_TYPE", "MEDIA_TYPE_AUDIO")
            put("ENABLED", "true")
            put("MUTED", "false")
            put("SOURCE", "")
            put("LABEL", "audio_0")
        }
        val mediaList = VectMap().apply { add(audioMedia) }
        if (withVideo) {
            mediaList.add(
                StringMap().apply {
                    put("MEDIA_TYPE", "MEDIA_TYPE_VIDEO")
                    put("ENABLED", "true")
                    put("MUTED", "false")
                    put("SOURCE", "")
                    put("LABEL", "video_0")
                }
            )
        }
        return JamiService.placeCallWithMedia(accountId, toUri, mediaList)
    }

    fun acceptCall(accountId: String, callId: String): Boolean = JamiService.accept(accountId, callId)

    fun hangUpCall(accountId: String, callId: String): Boolean = JamiService.hangUp(accountId, callId)

    fun hold(accountId: String, callId: String): Boolean = JamiService.hold(accountId, callId)

    fun resume(accountId: String, callId: String): Boolean = JamiService.resume(accountId, callId)

    fun toggleMute(accountId: String, callId: String, muted: Boolean): Boolean =
        JamiService.muteLocalMedia(accountId, callId, "MEDIA_TYPE_AUDIO", muted)

    fun toggleVideoMute(accountId: String, callId: String, muted: Boolean): Boolean =
        JamiService.muteLocalMedia(accountId, callId, "MEDIA_TYPE_VIDEO", muted)

    /** All active call ids for this account. Maps to `getCallList(accountId)`. */
    fun getCallList(accountId: String): List<String> {
        val raw = JamiService.getCallList(accountId)
        return (0 until raw.size()).map { raw[it] }
    }

    /** Snapshot of a call's details — see [RealCallSession] for the exact key mapping. */
    fun getCallDetails(accountId: String, callId: String): RealCallSession =
        RealCallSession.fromStringMap(callId, JamiService.getCallDetails(accountId, callId))

    /**
     * Answers a peer's [RealJamiEvent.MediaChangeRequested] (e.g. accepting an escalation from
     * audio to video mid-call). Maps to `answerMediaChangeRequest(accountId, callId, mediaList)`
     * (callmanager.i) — pass back the same media list structure `placeCall` builds.
     */
    fun answerMediaChangeRequest(accountId: String, callId: String, mediaList: VectMap): Boolean =
        JamiService.answerMediaChangeRequest(accountId, callId, mediaList)

    // --- Contacts -----------------------------------------------------------------------------
    // All calls below come from configurationmanager.i's "/* Contacts */" and "/* trust
    // requests */" sections. Two distinct concepts, don't conflate them: a "contact" only
    // exists once a trust request has been accepted (or the peer accepted ours); an incoming
    // trust request is not yet a contact and needs acceptTrustRequest/discardTrustRequest before
    // it shows up in getContacts.

    /**
     * Directly adds `uri` as a contact and sends it a trust request in one step — this is the
     * "add by Jami ID / username" action, matching `addContact(accountId, uri)`. libjami sends
     * the actual DHT trust-request message on its own; there's no separate call needed for the
     * common case (an empty-payload `sendTrustRequest` is only for re-sending/retrying).
     */
    fun addContact(accountId: String, uri: String) {
        JamiService.addContact(accountId, uri)
    }

    /** `ban=true` also blocks future requests from this URI; `false` is a plain removal. */
    fun removeContact(accountId: String, uri: String, ban: Boolean) {
        JamiService.removeContact(accountId, uri, ban)
    }

    /**
     * Confirmed contacts (the JNI-exposed `getContacts(accountId)` takes no `includeRemoved`
     * flag — that's only a parameter on the internal `JamiAccount::getContacts`, not on the
     * SWIG-bound one in configurationmanager.i).
     *
     * Reads `VectMap` via `size()`/`get(i)` rather than a Kotlin `Iterable` extension like `.map`
     * — whether the real SWIG-generated `VectMap` implements `java.lang.Iterable` wasn't
     * confirmed against a real generated build (see PHASE2_BUILD.md), but `size()`/`get(Int)` are
     * guaranteed by SWIG's std::vector wrapper regardless of version/config.
     */
    fun getContacts(accountId: String): List<RealContact> {
        val raw = JamiService.getContacts(accountId)
        return (0 until raw.size()).map { RealContact.fromStringMap(raw[it]) }
    }

    fun getContactDetails(accountId: String, uri: String): RealContact =
        RealContact.fromStringMap(JamiService.getContactDetails(accountId, uri))

    /** Pending incoming friend requests — the "Requests" tab. Maps to `getTrustRequests`. */
    fun getTrustRequests(accountId: String): List<RealTrustRequest> {
        val raw = JamiService.getTrustRequests(accountId)
        return (0 until raw.size()).map { RealTrustRequest.fromStringMap(raw[it]) }
    }

    /**
     * Accepts an incoming trust request, turning it into a confirmed contact. Maps to
     * `acceptTrustRequest(accountId, from)`.
     */
    fun acceptTrustRequest(accountId: String, fromUri: String): Boolean =
        JamiService.acceptTrustRequest(accountId, fromUri)

    /** Rejects an incoming trust request without banning the sender. */
    fun discardTrustRequest(accountId: String, fromUri: String): Boolean =
        JamiService.discardTrustRequest(accountId, fromUri)

    /**
     * Sends (or re-sends) a trust request to `toUri`. `payload` is an optional VCard-style
     * profile blob attached to the request; libjami accepts an empty one (profiles can also be
     * exchanged in-band after the request is accepted), which is what an empty [Blob] means here.
     */
    fun sendTrustRequest(accountId: String, toUri: String, payload: Blob = Blob()) {
        JamiService.sendTrustRequest(accountId, toUri, payload)
    }
}
