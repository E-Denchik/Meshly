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

import net.jami.daemon.StringMap

/**
 * The real, raw call state strings libjami emits — `callStateChanged`'s `state` param,
 * `incomingCall`'s implicit "INCOMING", and `getCallDetails`' `CALL_STATE` key all use these.
 * Verbatim from `libjami::Call::StateEvent` (src/jami/call_const.h) — derived in
 * `Call::getStateStr()` (src/call.cpp) from the daemon's internal `CallState`/`ConnectionState`
 * enums, which are NOT exposed over JNI, only this flattened string form is.
 */
enum class RealCallState(val wireValue: String) {
    INCOMING("INCOMING"),
    CONNECTING("CONNECTING"),
    RINGING("RINGING"),
    CURRENT("CURRENT"),
    HUNGUP("HUNGUP"),
    BUSY("BUSY"),
    PEER_BUSY("PEER_BUSY"),
    FAILURE("FAILURE"),
    HOLD("HOLD"),
    INACTIVE("INACTIVE"),
    OVER("OVER"),
    /** Anything callStateChanged sends that isn't in the list above — should never happen. */
    UNKNOWN("");

    /**
     * Buckets the 11 raw states into the same 5 states Meshly's Phase 1 mock UI already knows
     * (`org.meshly.app.data.model.CallState`), since the UI doesn't need to distinguish e.g.
     * BUSY vs FAILURE vs HUNGUP -- they're all just "the call ended".
     */
    fun toSimplified(): SimplifiedCallState = when (this) {
        INCOMING -> SimplifiedCallState.INCOMING
        CONNECTING, RINGING -> SimplifiedCallState.DIALING
        CURRENT -> SimplifiedCallState.CONNECTED
        HOLD -> SimplifiedCallState.CONNECTED // Phase 1's mock has no separate HOLD state yet.
        HUNGUP, BUSY, PEER_BUSY, FAILURE, INACTIVE, OVER, UNKNOWN -> SimplifiedCallState.ENDED
    }

    companion object {
        fun fromWireValue(value: String): RealCallState =
            entries.firstOrNull { it.wireValue == value } ?: UNKNOWN
    }
}

/** Mirrors org.meshly.app.data.model.CallState's cases, without depending on :app. */
enum class SimplifiedCallState { DIALING, INCOMING, CONNECTED, ENDED }

/** `libjami::Call::CallType` (src/call.h) — `getCallDetails`'s `CALL_TYPE` value, stringified. */
enum class RealCallType {
    INCOMING, OUTGOING, MISSED;

    companion object {
        fun fromWireValue(value: String): RealCallType = when (value.toIntOrNull()) {
            0 -> INCOMING
            1 -> OUTGOING
            2 -> MISSED
            else -> INCOMING
        }
    }
}

/**
 * Mirrors `getCallDetails(accountId, callId)`'s map. Keys are verbatim from
 * `libjami::Call::Details` (src/jami/call_const.h), populated exactly as `Call::getDetails()`
 * builds them (src/call.cpp) -- every field below was cross-checked against that function body,
 * not guessed:
 *
 * - `CALL_TYPE` — "0"/"1"/"2" (see [RealCallType]), NOT a friendly string
 * - `PEER_NUMBER` / `TO_USERNAME` — peer URI / the username originally dialed
 * - `DISPLAY_NAME` — peer's display name, if known
 * - `CALL_STATE` — see [RealCallState]
 * - `CONF_ID` — non-empty only if this call is part of a conference (Meshly doesn't do
 *   conferences yet)
 * - `TIMESTAMP_START` — epoch seconds, as a decimal string
 * - `ACCOUNTID` — the owning account's internal accountId
 * - `AUDIO_MUTED` / `VIDEO_MUTED` / `AUDIO_ONLY` — "true"/"false"
 */
data class RealCallSession(
    val callId: String,
    val accountId: String,
    val peerNumber: String,
    val peerDisplayName: String,
    val toUsername: String,
    val callType: RealCallType,
    val state: RealCallState,
    val startedAtEpochSeconds: Long,
    val audioMuted: Boolean,
    val videoMuted: Boolean,
    val audioOnly: Boolean
) {
    companion object {
        fun fromStringMap(callId: String, map: StringMap): RealCallSession =
            RealCallSession(
                callId = callId,
                accountId = map.get("ACCOUNTID").orEmpty(),
                peerNumber = map.get("PEER_NUMBER").orEmpty(),
                peerDisplayName = map.get("DISPLAY_NAME").orEmpty(),
                toUsername = map.get("TO_USERNAME").orEmpty(),
                callType = RealCallType.fromWireValue(map.get("CALL_TYPE").orEmpty()),
                state = RealCallState.fromWireValue(map.get("CALL_STATE").orEmpty()),
                startedAtEpochSeconds = map.get("TIMESTAMP_START")?.toLongOrNull() ?: 0L,
                audioMuted = map.get("AUDIO_MUTED") == "true",
                videoMuted = map.get("VIDEO_MUTED") == "true",
                audioOnly = map.get("AUDIO_ONLY") == "true"
            )
    }
}
