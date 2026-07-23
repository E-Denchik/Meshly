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
        "tox.plastiras.org:33445:8E8B63299B3D520FB377FE5100E65E3322F7AE5B20A0ACED2981769FC5B43B4",
        "205.185.115.131:53:3091C6BEB2A993F1C6300C16549FABA67098FF3D62C6D253828B531470B53D68",
        "3.0.24.15:33445:E20ABCF38CDBFFD7D04B29C956B33F7B27A3BB7AF0618101617B036E4AEA402D",
        "tox2.mf-net.eu:33445:70EA214FDE161E7432530605213F18F7427DC773E276B3E317A07531F548545F",
        "tox3.mf-net.eu:33445:F4FC9398B7167668ED2BCF85634E04D4CDCDD2F95DA5F305BD234888B6E6A771",
        "tox4.mf-net.eu:33445:DCD342A0D5E2AA8E35C2BD2C7988F906EEB631B35100170A7F30E77D7F596442",
        "144.172.88.203:33445:2016A0F2797EE3A8B004BA623F11AAFC8146F1B8F45107232A1A1AECCE856674",
        "119.59.101.63:33445:197F746696062FA3BD07BB3BC0656ABD6692B4DAA27DACF0F474754F2B09B060",
        "172.86.77.39:33445:AFFD3FAD3460E62A894E439534B27E5A5DCFE379C1C0FB78DEF1B150A87E900F",
        "144.217.167.73:33445:7E5668E0EE09E19F320AD47902419331FFEE147BB3606769CFBE921A2A2FD34C"
    )
)
