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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import org.meshly.app.data.model.CallType
import org.meshly.app.ui.call.CallScreen
import org.meshly.app.ui.chat.ChatScreen
import org.meshly.app.ui.main.MainScreen
import org.meshly.app.ui.onboarding.OnboardingScreen

/**
 * [pendingChatDeepLink] navigates straight into a chat when the app was opened from an incoming
 * message notification (FR-5.1); [onDeepLinkConsumed] clears it so back-navigation or process
 * restarts don't replay the same jump.
 */
@Composable
fun MeshlyNavHost(
    pendingChatDeepLink: Pair<String, String>? = null,
    onDeepLinkConsumed: () -> Unit = {}
) {
    val navController = rememberNavController()

    LaunchedEffect(pendingChatDeepLink) {
        pendingChatDeepLink?.let { (toxId, displayName) ->
            navController.navigate(Routes.chat(toxId, displayName))
            onDeepLinkConsumed()
        }
    }

    NavHost(navController = navController, startDestination = Routes.ONBOARDING) {
        composable(Routes.ONBOARDING) {
            OnboardingScreen(
                onOnboardingComplete = {
                    navController.navigate(Routes.MAIN) {
                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.MAIN) {
            MainScreen(navController = navController)
        }

        composable(
            route = Routes.CHAT,
            arguments = listOf(
                navArgument("toxId") { type = NavType.StringType },
                navArgument("displayName") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val toxId = Routes.decode(backStackEntry.arguments?.getString("toxId").orEmpty())
            val displayName = Routes.decode(backStackEntry.arguments?.getString("displayName").orEmpty())
            ChatScreen(
                peerToxId = toxId,
                peerDisplayName = displayName,
                onBack = { navController.popBackStack() },
                onStartCall = { type ->
                    navController.navigate(Routes.call(toxId, displayName, type.name, outgoing = true))
                }
            )
        }

        composable(
            route = Routes.CALL,
            arguments = listOf(
                navArgument("toxId") { type = NavType.StringType },
                navArgument("displayName") { type = NavType.StringType },
                navArgument("callType") { type = NavType.StringType },
                navArgument("outgoing") { type = NavType.BoolType }
            )
        ) { backStackEntry ->
            val args = backStackEntry.arguments
            val toxId = Routes.decode(args?.getString("toxId").orEmpty())
            val displayName = Routes.decode(args?.getString("displayName").orEmpty())
            val callType = CallType.valueOf(args?.getString("callType") ?: CallType.AUDIO.name)
            val outgoing = args?.getBoolean("outgoing") ?: true
            CallScreen(
                peerToxId = toxId,
                peerDisplayName = displayName,
                callType = callType,
                isOutgoing = outgoing,
                onCallEnded = { navController.popBackStack() }
            )
        }
    }
}
