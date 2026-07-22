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
import net.jami.daemon.VectMap

/**
 * IMPORTANT ARCHITECTURAL NOTE: modern libjami routes essentially all real messaging -- including
 * plain 1:1 chats, not just group chats -- through swarm conversations (this file), not the older
 * `sendAccountTextMessage`/`getLastMessages`/`accountMessageStatusChanged` API
 * (`RealJamiBridge.sendTextMessage`/`getLastMessages`, wired in an earlier pass). Evidence:
 * [RealContact] already carries a non-optional `conversationId` field (from `Contact::toMap()`),
 * populated as soon as a contact is confirmed -- that's the swarm conversation backing that 1:1
 * relationship. A real integration should very likely send/load messages via
 * `RealJamiBridge.sendConversationMessage(accountId, contact.conversationId, ...)` /
 * `loadConversation`, not the account-message calls. Both APIs are wired here since both are
 * real and JNI-exposed, but this is flagged prominently rather than left for whoever finishes
 * the wiring to discover the hard way.
 */

/**
 * Mirrors `libjami::SwarmMessage` (conversation.i) -- the real per-message shape `swarmLoaded`/
 * `swarmMessageReceived`/`swarmMessageUpdated` deliver, and what a real chat feature should
 * likely render instead of [RealChatMessage] (see this file's top-level note).
 *
 * `status` is `std::map<std::string, int32_t>` (per-device read/delivery state, keyed by device
 * id) -- assumed to reuse the `IntegerMap` template (`%template(IntegerMap) map<string, int>;`,
 * jni_interface.i) since `int32_t` and `int` are the same underlying type, but no separate
 * `map<string, int32_t>` template exists to confirm SWIG actually unifies the two for this
 * exact struct field; not confirmed against a real generated build.
 */
data class RealSwarmMessage(
    val id: String,
    val type: String,
    val linearizedParent: String,
    val body: Map<String, String>,
    val reactions: List<Map<String, String>>,
    val editions: List<Map<String, String>>,
    val status: Map<String, Int>
) {
    companion object {
        fun fromSwarmMessage(message: net.jami.daemon.SwarmMessage): RealSwarmMessage =
            RealSwarmMessage(
                id = message.id,
                type = message.type,
                linearizedParent = message.linearizedParent,
                body = message.body.entries.associate { it.key to it.value },
                reactions = message.reactions.toMapList(),
                editions = message.editions.toMapList(),
                // See this class's doc: assumes IntegerMap-shaped access (entrySet()/put()/get(),
                // same java.util.Map treatment as StringMap) for the int32_t-valued status map.
                status = message.status.entries.associate { it.key to it.value }
            )

        private fun VectMap.toMapList(): List<Map<String, String>> =
            (0 until size()).map { index -> get(index).entries.associate { it.key to it.value } }
    }
}

/**
 * `ConversationCallback.conversationMemberEvent`'s `event` int. Verbatim from
 * `CommitAction`/the action-code mapping in `Conversation::processMessages`
 * (src/jamidht/conversation.cpp): ADD=0, JOIN=1, REMOVE=2, BAN=3, UNBAN=4. Not defined as a
 * dedicated public enum anywhere in the daemon -- these int values are only ever constructed
 * inline at the emit site, so this mapping was reconstructed by reading that function's body.
 */
enum class RealConversationMemberEvent(val wireValue: Int) {
    ADD(0), JOIN(1), REMOVE(2), BAN(3), UNBAN(4), UNKNOWN(-1);

    companion object {
        fun fromWireValue(value: Int): RealConversationMemberEvent =
            entries.firstOrNull { it.wireValue == value } ?: UNKNOWN
    }
}

/**
 * `sendMessage`'s (conversation.i) `flag` parameter. Confirmed from its implementation
 * (src/client/conversation_interface.cpp): this is an action selector, not a bitmask (contrast
 * [RealJamiBridge.sendTextMessage]'s `flag`, which IS a bitmask on the older account-message API)
 * -- 0 sends a new message or reply (`commitId`/`replyTo` is the parent), 1 edits an existing
 * message (`commitId` is the message being edited, `message` is the new content), 2 reacts to a
 * message (`commitId` is the message being reacted to, `message` is the reaction, e.g. an emoji).
 */
enum class RealConversationMessageAction(val wireValue: Int) {
    SEND_OR_REPLY(0), EDIT(1), REACT(2)
}
