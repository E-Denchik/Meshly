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
 * A snapshot of one ToxAV call's state, assembled client-side from
 * [ToxDaemonEvent.CallInviteReceived]/[ToxDaemonEvent.CallStateChanged]
 * rather than returned by a single native "get call details" call -- ToxAV
 * has no equivalent of the removed jami-daemon scaffold's `getCallDetails`;
 * `stateBitmask` (see [ToxDaemonEvent.CallStateChanged]'s doc, citing
 * `toxav.h` lines 350-392) is the only state ToxAV itself tracks, so
 * everything else here (e.g. [startedAtMillis]) is bookkeeping this bridge
 * layer would need to do itself, not something ToxAV provides.
 *
 * `Toxav_Friend_Call_State` is a BITMASK, not a single enum value (toxav.h
 * lines 372-390, confirmed) -- a call can simultaneously be `SENDING_A`
 * (4) and `ACCEPTING_A` (16) for two-way audio, for example. [isActive]
 * below decodes only the two terminal bits (`ERROR`=1, `FINISHED`=2, toxav.h
 * lines 358-370, confirmed); decoding the rest into separate
 * audio/video-sending/receiving booleans is left to whoever wires this up
 * for real.
 */
data class ToxCallSession(
    val friendNumber: Int,
    val stateBitmask: Int,
    val audioEnabled: Boolean,
    val videoEnabled: Boolean,
    val startedAtMillis: Long
) {
    /** `true` unless the terminal `ERROR` (1) or `FINISHED` (2) bit is set (toxav.h lines 358-370). */
    val isActive: Boolean
        get() = (stateBitmask and (TOXAV_FRIEND_CALL_STATE_ERROR or TOXAV_FRIEND_CALL_STATE_FINISHED)) == 0

    private companion object {
        /** `Toxav_Friend_Call_State.TOXAV_FRIEND_CALL_STATE_ERROR` (toxav.h line 363, confirmed). */
        const val TOXAV_FRIEND_CALL_STATE_ERROR = 1

        /** `Toxav_Friend_Call_State.TOXAV_FRIEND_CALL_STATE_FINISHED` (toxav.h line 370, confirmed). */
        const val TOXAV_FRIEND_CALL_STATE_FINISHED = 2
    }
}
