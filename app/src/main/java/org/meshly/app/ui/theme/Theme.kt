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

package org.meshly.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val MeshDarkColorScheme = darkColorScheme(
    primary = MeshPrimaryDark,
    onPrimary = MeshOnPrimaryDark,
    primaryContainer = MeshPrimaryContainerDark,
    onPrimaryContainer = MeshOnPrimaryContainerDark,
    secondary = MeshSecondaryDark,
    onSecondary = MeshOnSecondaryDark,
    secondaryContainer = MeshSecondaryContainerDark,
    onSecondaryContainer = MeshOnSecondaryContainerDark,
    tertiary = MeshTertiaryDark,
    onTertiary = MeshOnTertiaryDark,
    tertiaryContainer = MeshTertiaryContainerDark,
    onTertiaryContainer = MeshOnTertiaryContainerDark,
    error = MeshErrorDark,
    onError = MeshOnErrorDark,
    errorContainer = MeshErrorContainerDark,
    onErrorContainer = MeshOnErrorContainerDark,
    background = MeshBackgroundDark,
    onBackground = MeshOnBackgroundDark,
    surface = MeshSurfaceDark,
    onSurface = MeshOnSurfaceDark,
    surfaceVariant = MeshSurfaceVariantDark,
    onSurfaceVariant = MeshOnSurfaceVariantDark,
    outline = MeshOutlineDark
)

private val MeshLightColorScheme = lightColorScheme(
    primary = MeshPrimaryLight,
    onPrimary = MeshOnPrimaryLight,
    primaryContainer = MeshPrimaryContainerLight,
    onPrimaryContainer = MeshOnPrimaryContainerLight,
    secondary = MeshSecondaryLight,
    onSecondary = MeshOnSecondaryLight,
    secondaryContainer = MeshSecondaryContainerLight,
    onSecondaryContainer = MeshOnSecondaryContainerLight,
    tertiary = MeshTertiaryLight,
    onTertiary = MeshOnTertiaryLight,
    tertiaryContainer = MeshTertiaryContainerLight,
    onTertiaryContainer = MeshOnTertiaryContainerLight,
    error = MeshErrorLight,
    onError = MeshOnErrorLight,
    errorContainer = MeshErrorContainerLight,
    onErrorContainer = MeshOnErrorContainerLight,
    background = MeshBackgroundLight,
    onBackground = MeshOnBackgroundLight,
    surface = MeshSurfaceLight,
    onSurface = MeshOnSurfaceLight,
    surfaceVariant = MeshSurfaceVariantLight,
    onSurfaceVariant = MeshOnSurfaceVariantLight,
    outline = MeshOutlineLight
)

/**
 * Dynamic (wallpaper-derived) color is deliberately not offered here: Meshly has its own
 * brand palette (see [Color.kt]) and a decentralized identity app benefits from a
 * consistent, recognizable look rather than one that shifts with the user's wallpaper.
 */
@Composable
fun MeshlyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) MeshDarkColorScheme else MeshLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        shapes = MeshShapes,
        typography = MeshTypography,
        content = content
    )
}
