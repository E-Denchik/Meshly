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

package org.meshly.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import org.meshly.app.ui.onboarding.ExportAccountDialog
import org.meshly.app.ui.viewmodel.SettingsViewModel

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = viewModel()
) {
    val account by viewModel.account.collectAsStateWithLifecycle()
    val upnpEnabled by viewModel.upnpEnabled.collectAsStateWithLifecycle()
    val turnEnabled by viewModel.turnEnabled.collectAsStateWithLifecycle()

    var showExportDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text("Settings") }) },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            ListItem(headlineContent = { Text("Account", style = MaterialTheme.typography.titleMedium) })
            ListItem(
                headlineContent = { Text(account?.username ?: "No username registered") },
                supportingContent = { Text(account?.jamiId ?: "") }
            )
            ListItem(
                headlineContent = { Text("Export account") },
                supportingContent = { Text("Password-protected backup of your Jami ID keys") },
                trailingContent = {
                    TextButton(onClick = { showExportDialog = true }) { Text("Export") }
                }
            )

            HorizontalDivider()

            ListItem(headlineContent = { Text("Network", style = MaterialTheme.typography.titleMedium) })
            ListItem(
                headlineContent = { Text("UPnP") },
                supportingContent = { Text("Automatic router port mapping for direct P2P connections") },
                trailingContent = {
                    Switch(checked = upnpEnabled, onCheckedChange = { viewModel.setUpnpEnabled(it) })
                }
            )
            ListItem(
                headlineContent = { Text("TURN relay") },
                supportingContent = { Text("Fallback relay when direct P2P connectivity fails") },
                trailingContent = {
                    Switch(checked = turnEnabled, onCheckedChange = { viewModel.setTurnEnabled(it) })
                }
            )
            ListItem(headlineContent = { Text("DHT bootstrap nodes") })
            account?.bootstrapNodes?.forEach { node ->
                ListItem(headlineContent = { Text(node, style = MaterialTheme.typography.bodyMedium) })
            }

            HorizontalDivider()

            ListItem(headlineContent = { Text("Diagnostics", style = MaterialTheme.typography.titleMedium) })
            ListItem(
                headlineContent = { Text("Export diagnostic logs") },
                supportingContent = { Text("Bundle daemon logs for troubleshooting connectivity issues") },
                trailingContent = {
                    TextButton(onClick = {
                        val fileName = viewModel.exportDiagnosticLogs()
                        coroutineScope.launch { snackbarHostState.showSnackbar("Saved $fileName") }
                    }) { Text("Export") }
                }
            )

            HorizontalDivider()

            ListItem(headlineContent = { Text("Background presence", style = MaterialTheme.typography.titleMedium) })
            ListItem(
                headlineContent = { Text("Disable battery optimization") },
                supportingContent = {
                    Text(
                        "On MIUI, OneUI, and other custom ROMs, disabling battery optimization for Meshly " +
                            "keeps the P2P foreground service alive so you keep receiving messages and calls."
                    )
                }
            )
        }
    }

    if (showExportDialog) {
        ExportAccountDialog(
            onDismiss = { showExportDialog = false },
            onExport = { password -> viewModel.exportAccount(password) }
        )
    }
}
