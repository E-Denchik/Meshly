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

package org.meshly.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.meshly.app.data.model.CallState
import org.meshly.app.data.model.CallType
import org.meshly.app.data.model.MessageStatus
import java.util.UUID

/**
 * JamiBridge is Stage 1's mock/stub engine: it never calls into native libjami (System.loadLibrary
 * always fails in the JVM unit test sandbox), so these tests exercise the pure-Kotlin state
 * machine that stands in for the JNI daemon until Phase 2 wires up the real bindings.
 */
class JamiBridgeTest {

    private val bridge = JamiBridge.getInstance()

    @Test
    fun `native engine is unavailable in the JVM unit test sandbox`() {
        assertFalse(bridge.isNativeEngineAvailable())
    }

    @Test
    fun `createAccount with a username produces a jami id and registers the name`() {
        val username = "alice-${UUID.randomUUID()}"
        val account = bridge.createAccount(username)

        assertTrue(account.jamiId.startsWith("jami:"))
        assertEquals(username, account.username)
        assertTrue(account.isRegisteredOnNameServer)
        assertEquals(account, bridge.currentAccount.value)
    }

    @Test
    fun `createAccount without a username is not registered on the name server`() {
        val account = bridge.createAccount(null)

        assertNull(account.username)
        assertFalse(account.isRegisteredOnNameServer)
    }

    @Test
    fun `sendTextMessage maps to an outgoing sent message`() {
        val peerId = "jami:peer-${UUID.randomUUID()}"
        val messageId = UUID.randomUUID().toString()
        val message = bridge.sendTextMessage(messageId, peerId, "hello mesh")

        assertEquals(messageId, message.id)
        assertEquals(peerId, message.conversationId)
        assertEquals("hello mesh", message.text)
        assertEquals(MessageStatus.SENT, message.status)
        assertFalse(message.isIncoming)
    }

    @Test
    fun `placeCall then acceptCall transitions the active call to connected`() {
        val peerId = "jami:peer-${UUID.randomUUID()}"
        val session = bridge.placeCall(peerId, "Bob", CallType.AUDIO)

        assertEquals(CallState.DIALING, session.state)
        assertEquals(session, bridge.activeCall.value)

        bridge.acceptCall(session.callId)

        assertEquals(CallState.CONNECTED, bridge.activeCall.value?.state)
    }

    @Test
    fun `hangUpCall clears the active call`() {
        val peerId = "jami:peer-${UUID.randomUUID()}"
        val session = bridge.placeCall(peerId, "Carol", CallType.VIDEO)

        bridge.hangUpCall(session.callId)

        assertNull(bridge.activeCall.value)
    }

    @Test
    fun `toggleMute toggleCamera and flipCamera flip the active call flags`() {
        val peerId = "jami:peer-${UUID.randomUUID()}"
        bridge.placeCall(peerId, "Dave", CallType.VIDEO)

        val muted = bridge.toggleMute()
        assertTrue(muted)
        assertEquals(true, bridge.activeCall.value?.isMuted)

        val cameraOn = bridge.toggleCamera()
        assertFalse(cameraOn)

        val frontCamera = bridge.flipCamera()
        assertFalse(frontCamera)
    }

    @Test
    fun `logout clears the current account and any active call`() {
        bridge.createAccount("erin-${UUID.randomUUID()}")
        val peerId = "jami:peer-${UUID.randomUUID()}"
        bridge.placeCall(peerId, "Frank", CallType.AUDIO)

        bridge.logout()

        assertNull(bridge.currentAccount.value)
        assertNull(bridge.activeCall.value)
    }
}
