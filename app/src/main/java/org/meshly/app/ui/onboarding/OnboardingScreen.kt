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

package org.meshly.app.ui.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.meshly.app.R
import org.meshly.app.ui.components.CopyableToxId
import org.meshly.app.ui.components.QrCodeImage
import org.meshly.app.ui.theme.MeshWordmarkStyle
import org.meshly.app.ui.theme.Spacing
import org.meshly.app.ui.viewmodel.OnboardingViewModel

private enum class OnboardingStep {
    CHECKING, WELCOME, CREATE_ACCOUNT, SHOW_OWN_ID, IMPORT_ACCOUNT
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
        Box(modifier = Modifier.fillMaxSize().padding(padding).padding(Spacing.xl)) {
            AnimatedContent(
                targetState = step,
                modifier = Modifier.fillMaxSize(),
                transitionSpec = {
                    (fadeIn(tween(220)) + slideInVertically(tween(220)) { it / 12 }) togetherWith
                        fadeOut(tween(150))
                },
                label = "onboarding-step"
            ) { targetStep ->
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    when (targetStep) {
                        OnboardingStep.CHECKING -> CircularProgressIndicator()
                        OnboardingStep.WELCOME -> WelcomeStep(
                            onCreateAccount = { step = OnboardingStep.CREATE_ACCOUNT },
                            onImportAccount = { step = OnboardingStep.IMPORT_ACCOUNT }
                        )
                        OnboardingStep.CREATE_ACCOUNT -> CreateAccountStep(
                            onCreate = { nickname ->
                                viewModel.createAccount(nickname.ifBlank { null })
                                step = OnboardingStep.SHOW_OWN_ID
                            },
                            onBack = { step = OnboardingStep.WELCOME }
                        )
                        OnboardingStep.SHOW_OWN_ID -> OwnIdStep(
                            toxId = account?.toxId.orEmpty(),
                            onContinue = onOnboardingComplete
                        )
                        OnboardingStep.IMPORT_ACCOUNT -> ImportAccountStep(
                            onImport = { payload, password ->
                                val success = viewModel.importAccount(payload, password)
                                if (success) onOnboardingComplete()
                                success
                            },
                            onBack = { step = OnboardingStep.WELCOME }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WelcomeStep(onCreateAccount: () -> Unit, onImportAccount: () -> Unit) {
    Box(
        modifier = Modifier
            .size(88.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Filled.Forum,
            contentDescription = null,
            modifier = Modifier.size(44.dp),
            tint = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
    Text(
        "Meshly",
        style = MeshWordmarkStyle,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(top = Spacing.xl)
    )
    Text(
        stringResource(R.string.onboarding_tagline),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = Spacing.sm, bottom = Spacing.xxl)
    )
    Button(onClick = onCreateAccount, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.action_create_new_id))
    }
    TextButton(onClick = onImportAccount, modifier = Modifier.padding(top = Spacing.sm)) {
        Text(stringResource(R.string.action_import_existing))
    }
}

@Composable
private fun CreateAccountStep(onCreate: (String) -> Unit, onBack: () -> Unit) {
    var nickname by remember { mutableStateOf("") }
    Text(stringResource(R.string.choose_nickname_title), style = MaterialTheme.typography.titleMedium)
    Text(
        stringResource(R.string.choose_nickname_desc),
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(vertical = Spacing.md)
    )
    OutlinedTextField(
        value = nickname,
        onValueChange = { nickname = it },
        label = { Text(stringResource(R.string.label_nickname)) },
        modifier = Modifier.fillMaxWidth()
    )
    Button(
        onClick = { onCreate(nickname) },
        modifier = Modifier.fillMaxWidth().padding(top = Spacing.lg)
    ) {
        Text(stringResource(R.string.action_generate_id))
    }
    TextButton(onClick = onBack, modifier = Modifier.padding(top = Spacing.xs)) {
        Text(stringResource(R.string.action_back))
    }
}

@Composable
private fun OwnIdStep(toxId: String, onContinue: () -> Unit) {
    Text(stringResource(R.string.own_tox_id_qr_title), style = MaterialTheme.typography.titleMedium)
    if (toxId.isNotBlank()) {
        QrCodeImage(content = toxId, modifier = Modifier.padding(vertical = Spacing.lg))
    }
    CopyableToxId(toxId, modifier = Modifier.padding(bottom = Spacing.xl))
    Button(onClick = onContinue, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.action_continue))
    }
}

@Composable
private fun ImportAccountStep(onImport: (payload: String, password: String) -> Boolean, onBack: () -> Unit) {
    var archivePayload by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var importFailed by remember { mutableStateOf(false) }

    Text(stringResource(R.string.import_account_title), style = MaterialTheme.typography.titleMedium)
    Text(
        stringResource(R.string.import_account_desc),
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(vertical = Spacing.md)
    )
    OutlinedTextField(
        value = archivePayload,
        onValueChange = { archivePayload = it; importFailed = false },
        label = { Text(stringResource(R.string.label_backup_archive)) },
        modifier = Modifier.fillMaxWidth()
    )
    OutlinedTextField(
        value = password,
        onValueChange = { password = it; importFailed = false },
        label = { Text(stringResource(R.string.label_password)) },
        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm)
    )
    if (importFailed) {
        Text(
            stringResource(R.string.import_failed),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = Spacing.sm)
        )
    }
    Button(
        onClick = { importFailed = !onImport(archivePayload, password) },
        modifier = Modifier.fillMaxWidth().padding(top = Spacing.lg)
    ) {
        Text(stringResource(R.string.action_import))
    }
    TextButton(onClick = onBack, modifier = Modifier.padding(top = Spacing.xs)) {
        Text(stringResource(R.string.action_back))
    }
}
