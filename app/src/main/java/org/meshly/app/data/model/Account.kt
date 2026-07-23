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

package org.meshly.app.data.model

data class Account(
    val toxId: String,
    val nickname: String? = null,
    val bootstrapNodes: List<String> = listOf(
        "node.tox.biribiri.org:33445:F404ABAA1C99A9D37D61AB54898F56793E1DEF8BD46B1038B9D822E8460FAB67",
        "tox.abilinski.com:33445:10C00EB250C3233E343E2AEBA07115A5C28920E9C8D29492F6D00B29049EDC7",
        "tox.plastiras.org:33445:8E8B63299B3D520FB377FE5100E65E3322F7AE5B20A0ACED2981769FC5B43B4"
    )
)
