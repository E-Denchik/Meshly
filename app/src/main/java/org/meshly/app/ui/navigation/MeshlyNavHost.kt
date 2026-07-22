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

package org.meshly.app.ui.navigation

import androidx.compose.runtime.Composable
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

@Composable
fun MeshlyNavHost() {
    val navController = rememberNavController()

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
                navArgument("jamiId") { type = NavType.StringType },
                navArgument("displayName") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val jamiId = Routes.decode(backStackEntry.arguments?.getString("jamiId").orEmpty())
            val displayName = Routes.decode(backStackEntry.arguments?.getString("displayName").orEmpty())
            ChatScreen(
                peerJamiId = jamiId,
                peerDisplayName = displayName,
                onBack = { navController.popBackStack() },
                onStartCall = { type ->
                    navController.navigate(Routes.call(jamiId, displayName, type.name, outgoing = true))
                }
            )
        }

        composable(
            route = Routes.CALL,
            arguments = listOf(
                navArgument("jamiId") { type = NavType.StringType },
                navArgument("displayName") { type = NavType.StringType },
                navArgument("callType") { type = NavType.StringType },
                navArgument("outgoing") { type = NavType.BoolType }
            )
        ) { backStackEntry ->
            val args = backStackEntry.arguments
            val jamiId = Routes.decode(args?.getString("jamiId").orEmpty())
            val displayName = Routes.decode(args?.getString("displayName").orEmpty())
            val callType = CallType.valueOf(args?.getString("callType") ?: CallType.AUDIO.name)
            val outgoing = args?.getBoolean("outgoing") ?: true
            CallScreen(
                peerJamiId = jamiId,
                peerDisplayName = displayName,
                callType = callType,
                isOutgoing = outgoing,
                onCallEnded = { navController.popBackStack() }
            )
        }
    }
}
