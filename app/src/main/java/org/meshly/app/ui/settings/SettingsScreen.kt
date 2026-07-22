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

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import org.meshly.app.R
import org.meshly.app.ui.components.Avatar
import org.meshly.app.ui.onboarding.ExportAccountDialog
import org.meshly.app.ui.viewmodel.SettingsViewModel

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    onLoggedOut: () -> Unit = {},
    viewModel: SettingsViewModel = viewModel()
) {
    val account by viewModel.account.collectAsStateWithLifecycle()
    val upnpEnabled by viewModel.upnpEnabled.collectAsStateWithLifecycle()
    val turnEnabled by viewModel.turnEnabled.collectAsStateWithLifecycle()

    var showExportDialog by remember { mutableStateOf(false) }
    var showLogoutConfirm by remember { mutableStateOf(false) }
    var newBootstrapNode by remember { mutableStateOf("") }
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    val exportLogsFailedMessage = stringResource(R.string.export_logs_failed)
    val shareLogsChooserTitle = stringResource(R.string.share_logs_chooser_title)

    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text(stringResource(R.string.settings_title)) }) },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            ListItem(headlineContent = { Text(stringResource(R.string.section_account), style = MaterialTheme.typography.titleMedium) })
            ListItem(
                leadingContent = {
                    Avatar(
                        name = account?.username ?: account?.jamiId.orEmpty(),
                        seed = account?.jamiId.orEmpty()
                    )
                },
                headlineContent = { Text(account?.username ?: stringResource(R.string.label_no_username)) },
                supportingContent = { Text(account?.jamiId ?: "") }
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.export_account_item_title)) },
                supportingContent = { Text(stringResource(R.string.export_account_item_desc)) },
                trailingContent = {
                    TextButton(onClick = { showExportDialog = true }) { Text(stringResource(R.string.action_export)) }
                }
            )
            ListItem(
                headlineContent = {
                    Text(stringResource(R.string.action_logout), color = MaterialTheme.colorScheme.error)
                },
                supportingContent = { Text(stringResource(R.string.logout_item_desc)) },
                leadingContent = {
                    Icon(
                        Icons.AutoMirrored.Filled.Logout,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                },
                modifier = Modifier.clickable { showLogoutConfirm = true }
            )

            HorizontalDivider()

            ListItem(headlineContent = { Text(stringResource(R.string.section_network), style = MaterialTheme.typography.titleMedium) })
            ListItem(
                headlineContent = { Text(stringResource(R.string.upnp_title)) },
                supportingContent = { Text(stringResource(R.string.upnp_desc)) },
                trailingContent = {
                    Switch(checked = upnpEnabled, onCheckedChange = { viewModel.setUpnpEnabled(it) })
                }
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.turn_title)) },
                supportingContent = { Text(stringResource(R.string.turn_desc)) },
                trailingContent = {
                    Switch(checked = turnEnabled, onCheckedChange = { viewModel.setTurnEnabled(it) })
                }
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.bootstrap_nodes_title)) },
                supportingContent = { Text(stringResource(R.string.bootstrap_nodes_desc)) }
            )
            val bootstrapNodes = account?.bootstrapNodes.orEmpty()
            bootstrapNodes.forEach { node ->
                ListItem(
                    headlineContent = { Text(node, style = MaterialTheme.typography.bodyMedium) },
                    trailingContent = {
                        IconButton(
                            enabled = bootstrapNodes.size > 1,
                            onClick = { viewModel.removeBootstrapNode(node) }
                        ) {
                            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.content_desc_remove_node))
                        }
                    }
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                OutlinedTextField(
                    value = newBootstrapNode,
                    onValueChange = { newBootstrapNode = it },
                    label = { Text(stringResource(R.string.label_host_port)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = {
                        if (newBootstrapNode.isNotBlank()) {
                            viewModel.addBootstrapNode(newBootstrapNode)
                            newBootstrapNode = ""
                        }
                    }
                ) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.content_desc_add_node))
                }
            }

            HorizontalDivider()

            ListItem(headlineContent = { Text(stringResource(R.string.section_diagnostics), style = MaterialTheme.typography.titleMedium) })
            ListItem(
                headlineContent = { Text(stringResource(R.string.export_logs_title)) },
                supportingContent = { Text(stringResource(R.string.export_logs_desc)) },
                trailingContent = {
                    TextButton(onClick = {
                        coroutineScope.launch {
                            val uri = viewModel.exportDiagnosticLogs()
                            if (uri != null) {
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(shareIntent, shareLogsChooserTitle))
                            } else {
                                snackbarHostState.showSnackbar(exportLogsFailedMessage)
                            }
                        }
                    }) { Text(stringResource(R.string.action_export)) }
                }
            )

            HorizontalDivider()

            ListItem(headlineContent = { Text(stringResource(R.string.section_background), style = MaterialTheme.typography.titleMedium) })
            ListItem(
                headlineContent = { Text(stringResource(R.string.battery_opt_title)) },
                supportingContent = { Text(stringResource(R.string.battery_opt_desc)) },
                trailingContent = {
                    val powerManager = context.getSystemService(PowerManager::class.java)
                    val alreadyExempt = Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
                        powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true
                    TextButton(
                        enabled = !alreadyExempt,
                        onClick = {
                            val intent = Intent(
                                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                Uri.parse("package:${context.packageName}")
                            )
                            context.startActivity(intent)
                        }
                    ) {
                        Text(
                            if (alreadyExempt) {
                                stringResource(R.string.battery_opt_action_already)
                            } else {
                                stringResource(R.string.battery_opt_action_disable)
                            }
                        )
                    }
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

    if (showLogoutConfirm) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirm = false },
            title = { Text(stringResource(R.string.logout_confirm_title)) },
            text = { Text(stringResource(R.string.logout_confirm_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLogoutConfirm = false
                        coroutineScope.launch {
                            viewModel.logout()
                            onLoggedOut()
                        }
                    }
                ) {
                    Text(stringResource(R.string.action_logout), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutConfirm = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}
