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

package org.meshly.app.ui.main

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import org.meshly.app.R
import org.meshly.app.data.model.CallType
import org.meshly.app.ui.calls.CallsScreen
import org.meshly.app.ui.chats.ChatsListScreen
import org.meshly.app.ui.contacts.ContactListScreen
import org.meshly.app.ui.navigation.Routes
import org.meshly.app.ui.settings.SettingsScreen

private enum class MainTab {
    CHATS, CONTACTS, CALLS, SETTINGS
}

@Composable
private fun MainTab.label(): String = when (this) {
    MainTab.CHATS -> stringResource(R.string.tab_chats)
    MainTab.CONTACTS -> stringResource(R.string.tab_contacts)
    MainTab.CALLS -> stringResource(R.string.tab_calls)
    MainTab.SETTINGS -> stringResource(R.string.tab_settings)
}

@Composable
fun MainScreen(navController: NavHostController) {
    var selectedTab by remember { mutableStateOf(MainTab.CHATS) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == MainTab.CHATS,
                    onClick = { selectedTab = MainTab.CHATS },
                    icon = { Icon(Icons.Filled.Forum, contentDescription = MainTab.CHATS.label()) },
                    label = { Text(MainTab.CHATS.label()) }
                )
                NavigationBarItem(
                    selected = selectedTab == MainTab.CONTACTS,
                    onClick = { selectedTab = MainTab.CONTACTS },
                    icon = { Icon(Icons.Filled.People, contentDescription = MainTab.CONTACTS.label()) },
                    label = { Text(MainTab.CONTACTS.label()) }
                )
                NavigationBarItem(
                    selected = selectedTab == MainTab.CALLS,
                    onClick = { selectedTab = MainTab.CALLS },
                    icon = { Icon(Icons.Filled.Call, contentDescription = MainTab.CALLS.label()) },
                    label = { Text(MainTab.CALLS.label()) }
                )
                NavigationBarItem(
                    selected = selectedTab == MainTab.SETTINGS,
                    onClick = { selectedTab = MainTab.SETTINGS },
                    icon = { Icon(Icons.Filled.Settings, contentDescription = MainTab.SETTINGS.label()) },
                    label = { Text(MainTab.SETTINGS.label()) }
                )
            }
        }
    ) { padding ->
        val contentModifier = androidx.compose.ui.Modifier.padding(padding)
        when (selectedTab) {
            MainTab.CHATS -> ChatsListScreen(
                modifier = contentModifier,
                onOpenChat = { toxId, displayName ->
                    navController.navigate(Routes.chat(toxId, displayName))
                }
            )
            MainTab.CONTACTS -> ContactListScreen(
                modifier = contentModifier,
                onOpenChat = { toxId, displayName ->
                    navController.navigate(Routes.chat(toxId, displayName))
                },
                onCall = { toxId, displayName, callType ->
                    navController.navigate(
                        Routes.call(toxId, displayName, callType.name, outgoing = true)
                    )
                }
            )
            MainTab.CALLS -> CallsScreen(
                modifier = contentModifier,
                onDial = { toxId, displayName, callType ->
                    navController.navigate(
                        Routes.call(toxId, displayName, callType.name, outgoing = true)
                    )
                }
            )
            MainTab.SETTINGS -> SettingsScreen(
                modifier = contentModifier,
                onLoggedOut = {
                    // By the time a user can reach Settings, onboarding's own completion
                    // callback has already popped Routes.ONBOARDING off the back stack
                    // (inclusive), so popping up to that route here would be a no-op. Popping
                    // up to the graph's own id instead clears the entire back stack unconditionally,
                    // which is the documented pattern for a full "log out and start over" flow.
                    navController.navigate(Routes.ONBOARDING) {
                        popUpTo(navController.graph.id) { inclusive = true }
                    }
                }
            )
        }
    }
}
