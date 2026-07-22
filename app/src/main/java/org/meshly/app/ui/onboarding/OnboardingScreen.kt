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

package org.meshly.app.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.meshly.app.ui.viewmodel.OnboardingViewModel

private enum class OnboardingStep {
    CHECKING, WELCOME, CREATE_ACCOUNT, IMPORT_ACCOUNT
}

@Composable
fun OnboardingScreen(
    onOnboardingComplete: () -> Unit,
    viewModel: OnboardingViewModel = viewModel()
) {
    var step by remember { mutableStateOf(OnboardingStep.CHECKING) }
    val account by viewModel.account.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        if (viewModel.hasAccount()) {
            viewModel.initOrLoad()
        } else {
            step = OnboardingStep.WELCOME
        }
    }

    LaunchedEffect(account) {
        if (account != null && step == OnboardingStep.CHECKING) {
            onOnboardingComplete()
        }
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            when (step) {
                OnboardingStep.CHECKING -> CircularProgressIndicator()
                OnboardingStep.WELCOME -> WelcomeStep(
                    onCreateAccount = { step = OnboardingStep.CREATE_ACCOUNT },
                    onImportAccount = { step = OnboardingStep.IMPORT_ACCOUNT }
                )
                OnboardingStep.CREATE_ACCOUNT -> CreateAccountStep(
                    onCreate = { username ->
                        viewModel.createAccount(username.ifBlank { null })
                        onOnboardingComplete()
                    },
                    onBack = { step = OnboardingStep.WELCOME }
                )
                OnboardingStep.IMPORT_ACCOUNT -> ImportAccountStep(
                    onImport = { payload ->
                        viewModel.importAccount(payload)
                        onOnboardingComplete()
                    },
                    onBack = { step = OnboardingStep.WELCOME }
                )
            }
        }
    }
}

@Composable
private fun WelcomeStep(onCreateAccount: () -> Unit, onImportAccount: () -> Unit) {
    Icon(
        imageVector = Icons.Filled.Forum,
        contentDescription = null,
        modifier = Modifier.padding(bottom = 16.dp),
        tint = MaterialTheme.colorScheme.primary
    )
    Text("Meshly", style = MaterialTheme.typography.headlineMedium)
    Text(
        "Serverless, end-to-end encrypted P2P messaging and calls, powered by GNU Jami.",
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(top = 8.dp, bottom = 32.dp)
    )
    Button(onClick = onCreateAccount, modifier = Modifier.fillMaxWidth()) {
        Text("Create a new Jami ID")
    }
    TextButton(onClick = onImportAccount, modifier = Modifier.padding(top = 8.dp)) {
        Text("Import an existing account")
    }
}

@Composable
private fun CreateAccountStep(onCreate: (String) -> Unit, onBack: () -> Unit) {
    var username by remember { mutableStateOf("") }
    Text("Choose a username (optional)", style = MaterialTheme.typography.titleMedium)
    Text(
        "Your Jami ID keypair will be generated on-device. Registering a username lets others find you by name instead of the raw ID.",
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(vertical = 12.dp)
    )
    OutlinedTextField(
        value = username,
        onValueChange = { username = it },
        label = { Text("Username") },
        modifier = Modifier.fillMaxWidth()
    )
    Button(
        onClick = { onCreate(username) },
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
    ) {
        Text("Generate Jami ID")
    }
    TextButton(onClick = onBack, modifier = Modifier.padding(top = 4.dp)) {
        Text("Back")
    }
}

@Composable
private fun ImportAccountStep(onImport: (String) -> Unit, onBack: () -> Unit) {
    var archivePath by remember { mutableStateOf("") }
    Text("Import account archive", style = MaterialTheme.typography.titleMedium)
    Text(
        "Paste the path to a password-protected account export (.gz) created from another device.",
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(vertical = 12.dp)
    )
    OutlinedTextField(
        value = archivePath,
        onValueChange = { archivePath = it },
        label = { Text("Archive path") },
        modifier = Modifier.fillMaxWidth()
    )
    Button(
        onClick = { onImport(archivePath) },
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
    ) {
        Text("Import")
    }
    OutlinedButton(onClick = onBack, modifier = Modifier.padding(top = 4.dp)) {
        Text("Back")
    }
}
