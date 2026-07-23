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

package org.meshly.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Meshly's brand palette: an indigo "mesh" primary (network/security connotation, distinct
 * from Jami's own orange-red brand to avoid confusion between the two), with a teal tertiary
 * reserved for presence/success states (online dots, accept-call, delivered receipts). Full
 * Material 3 tonal roles are defined explicitly (not left to `lightColorScheme()`/
 * `darkColorScheme()` defaults) so containers, on-colors, and surface variants all read as
 * one consistent system instead of a custom primary bolted onto stock Material purple.
 */

// Light scheme
val MeshPrimaryLight = Color(0xFF4A55D6)
val MeshOnPrimaryLight = Color(0xFFFFFFFF)
val MeshPrimaryContainerLight = Color(0xFFE0E1FF)
val MeshOnPrimaryContainerLight = Color(0xFF000A6E)

val MeshSecondaryLight = Color(0xFF5B5D72)
val MeshOnSecondaryLight = Color(0xFFFFFFFF)
val MeshSecondaryContainerLight = Color(0xFFE0E1F9)
val MeshOnSecondaryContainerLight = Color(0xFF181A2C)

val MeshTertiaryLight = Color(0xFF2E7D63)
val MeshOnTertiaryLight = Color(0xFFFFFFFF)
val MeshTertiaryContainerLight = Color(0xFFB3F1D7)
val MeshOnTertiaryContainerLight = Color(0xFF00210F)

val MeshErrorLight = Color(0xFFBA1A1A)
val MeshOnErrorLight = Color(0xFFFFFFFF)
val MeshErrorContainerLight = Color(0xFFFFDAD6)
val MeshOnErrorContainerLight = Color(0xFF410002)

val MeshBackgroundLight = Color(0xFFFBF8FF)
val MeshOnBackgroundLight = Color(0xFF1B1B21)
val MeshSurfaceLight = Color(0xFFFBF8FF)
val MeshOnSurfaceLight = Color(0xFF1B1B21)
val MeshSurfaceVariantLight = Color(0xFFE3E1EC)
val MeshOnSurfaceVariantLight = Color(0xFF46464F)
val MeshOutlineLight = Color(0xFF777680)

// Dark scheme
val MeshPrimaryDark = Color(0xFFC0C1FF)
val MeshOnPrimaryDark = Color(0xFF1A1A8E)
val MeshPrimaryContainerDark = Color(0xFF2F35B0)
val MeshOnPrimaryContainerDark = Color(0xFFE0E1FF)

val MeshSecondaryDark = Color(0xFFC4C5DD)
val MeshOnSecondaryDark = Color(0xFF2C2F42)
val MeshSecondaryContainerDark = Color(0xFF434659)
val MeshOnSecondaryContainerDark = Color(0xFFE0E1F9)

val MeshTertiaryDark = Color(0xFF96D4B8)
val MeshOnTertiaryDark = Color(0xFF003825)
val MeshTertiaryContainerDark = Color(0xFF00513A)
val MeshOnTertiaryContainerDark = Color(0xFFB3F1D7)

val MeshErrorDark = Color(0xFFFFB4AB)
val MeshOnErrorDark = Color(0xFF690005)
val MeshErrorContainerDark = Color(0xFF93000A)
val MeshOnErrorContainerDark = Color(0xFFFFDAD6)

val MeshBackgroundDark = Color(0xFF131318)
val MeshOnBackgroundDark = Color(0xFFE4E1E9)
val MeshSurfaceDark = Color(0xFF131318)
val MeshOnSurfaceDark = Color(0xFFE4E1E9)
val MeshSurfaceVariantDark = Color(0xFF46464F)
val MeshOnSurfaceVariantDark = Color(0xFFC7C5D0)
val MeshOutlineDark = Color(0xFF90909A)

/** Fixed palette used to derive a deterministic per-contact [org.meshly.app.ui.components.Avatar]
 *  color from a Tox ID, so the same contact always gets the same color across the app. */
val AvatarPalette = listOf(
    Color(0xFF4A55D6), Color(0xFF2E7D63), Color(0xFFB5541F),
    Color(0xFF9A3B7A), Color(0xFF3D6E9E), Color(0xFF7A6A1E),
    Color(0xFFA23E56), Color(0xFF1E7A6E)
)
