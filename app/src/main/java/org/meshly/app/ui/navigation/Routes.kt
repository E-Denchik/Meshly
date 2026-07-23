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

package org.meshly.app.ui.navigation

import java.net.URLDecoder
import java.net.URLEncoder

object Routes {
    const val ONBOARDING = "onboarding"
    const val MAIN = "main"
    const val CHAT = "chat/{toxId}/{displayName}"
    const val CALL = "call/{toxId}/{displayName}/{callType}/{outgoing}"

    fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")
    fun decode(value: String): String = URLDecoder.decode(value, "UTF-8")

    fun chat(toxId: String, displayName: String): String =
        "chat/${encode(toxId)}/${encode(displayName)}"

    fun call(toxId: String, displayName: String, callType: String, outgoing: Boolean): String =
        "call/${encode(toxId)}/${encode(displayName)}/$callType/$outgoing"
}
