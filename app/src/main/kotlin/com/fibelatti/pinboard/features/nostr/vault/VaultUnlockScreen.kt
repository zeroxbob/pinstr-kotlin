@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.fibelatti.pinboard.features.nostr.vault

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.KeyboardActionHandler
import androidx.compose.foundation.text.input.TextObfuscationMode
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedSecureTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fibelatti.pinboard.R
import com.fibelatti.pinboard.core.android.composable.LaunchedErrorHandlerEffect
import com.fibelatti.ui.preview.DevicePreviews
import com.fibelatti.ui.preview.ThemePreviews
import com.fibelatti.ui.theme.ExtendedTheme

@Composable
fun VaultUnlockScreen(
    vaultViewModel: VaultViewModel = hiltViewModel(),
) {
    val screenState by vaultViewModel.screenState.collectAsStateWithLifecycle()

    val error by vaultViewModel.error.collectAsStateWithLifecycle()
    LaunchedErrorHandlerEffect(error = error, handler = vaultViewModel::errorHandled)

    VaultUnlockScreen(
        isLoading = screenState.isLoading,
        error = screenState.error,
        onUnlockVault = vaultViewModel::unlockVault,
        onResetVault = vaultViewModel::resetVault,
    )
}

@Composable
private fun VaultUnlockScreen(
    isLoading: Boolean,
    error: String?,
    onUnlockVault: (String) -> Unit,
    onResetVault: () -> Unit,
) {
    val windowInsetsSides = WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom
    var showResetDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(color = ExtendedTheme.colors.backgroundNoOverlay)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 32.dp)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(windowInsetsSides)),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_launcher_monochrome),
            contentDescription = null,
            modifier = Modifier
                .padding(top = 40.dp, bottom = 20.dp)
                .size(80.dp),
            tint = MaterialTheme.colorScheme.primary,
        )

        Surface(
            modifier = Modifier.sizeIn(maxWidth = 600.dp),
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            Column(
                modifier = Modifier
                    .padding(all = 16.dp)
                    .animateContentSize(),
            ) {
                Text(
                    text = stringResource(id = R.string.vault_unlock_title),
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleLargeEmphasized,
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = stringResource(id = R.string.vault_unlock_description),
                    modifier = Modifier.padding(horizontal = 8.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium,
                )

                Spacer(modifier = Modifier.height(16.dp))

                VaultUnlockForm(
                    isLoading = isLoading,
                    error = error,
                    onUnlockVault = onUnlockVault,
                    onForgotPassphrase = { showResetDialog = true },
                )
            }
        }
    }

    if (showResetDialog) {
        ResetVaultDialog(
            onConfirm = {
                showResetDialog = false
                onResetVault()
            },
            onDismiss = { showResetDialog = false },
        )
    }
}

@Composable
private fun VaultUnlockForm(
    isLoading: Boolean,
    error: String?,
    onUnlockVault: (String) -> Unit,
    onForgotPassphrase: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val passphraseFieldState = rememberTextFieldState()
        var passphraseVisible by remember { mutableStateOf(false) }

        // Passphrase field
        OutlinedSecureTextField(
            state = passphraseFieldState,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            textObfuscationMode = if (passphraseVisible) {
                TextObfuscationMode.Visible
            } else {
                TextObfuscationMode.RevealLastTyped
            },
            label = { Text(text = stringResource(id = R.string.vault_passphrase_hint)) },
            trailingIcon = {
                IconButton(
                    onClick = { passphraseVisible = !passphraseVisible },
                    shapes = IconButtonDefaults.shapes(),
                ) {
                    AnimatedContent(
                        targetState = passphraseVisible,
                        label = "PassphraseIconVisibility",
                    ) { visible ->
                        Icon(
                            painter = painterResource(
                                id = if (visible) R.drawable.ic_eye else R.drawable.ic_eye_slash,
                            ),
                            contentDescription = null,
                            modifier = Modifier.size(30.dp),
                        )
                    }
                }
            },
            isError = error != null,
            keyboardOptions = KeyboardOptions(
                imeAction = ImeAction.Go,
                keyboardType = KeyboardType.Password,
            ),
            onKeyboardAction = KeyboardActionHandler {
                onUnlockVault(passphraseFieldState.text.toString())
            },
            contentPadding = OutlinedTextFieldDefaults.contentPadding(
                start = 8.dp,
                end = 8.dp,
                bottom = 8.dp,
            ),
        )

        // Error message
        if (error != null) {
            Text(
                text = error,
                modifier = Modifier.padding(bottom = 8.dp),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Unlock button
        AnimatedContent(
            targetState = isLoading,
            modifier = Modifier.padding(vertical = 8.dp),
            transitionSpec = { fadeIn() + scaleIn() togetherWith fadeOut() + scaleOut() },
            label = "vault-unlock-button-progress",
        ) { loading ->
            if (loading) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Unlocking vault...",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            } else {
                Button(
                    onClick = { onUnlockVault(passphraseFieldState.text.toString()) },
                    shapes = ExtendedTheme.defaultButtonShapes,
                ) {
                    Text(text = stringResource(id = R.string.vault_unlock_button))
                }
            }
        }

        // Forgot passphrase
        TextButton(onClick = onForgotPassphrase) {
            Text(text = stringResource(id = R.string.vault_forgot_passphrase))
        }
    }
}

@Composable
private fun ResetVaultDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = stringResource(id = R.string.vault_reset_button))
        },
        text = {
            Text(text = stringResource(id = R.string.vault_reset_warning))
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = stringResource(id = R.string.vault_reset_button),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(id = R.string.hint_no))
            }
        },
    )
}

// region Previews
@Composable
@ThemePreviews
@DevicePreviews
private fun VaultUnlockScreenPreview() {
    ExtendedTheme {
        VaultUnlockScreen(
            isLoading = false,
            error = null,
            onUnlockVault = {},
            onResetVault = {},
        )
    }
}

@Composable
@ThemePreviews
@DevicePreviews
private fun VaultUnlockScreenLoadingPreview() {
    ExtendedTheme {
        VaultUnlockScreen(
            isLoading = true,
            error = null,
            onUnlockVault = {},
            onResetVault = {},
        )
    }
}

@Composable
@ThemePreviews
@DevicePreviews
private fun VaultUnlockScreenErrorPreview() {
    ExtendedTheme {
        VaultUnlockScreen(
            isLoading = false,
            error = "Incorrect passphrase",
            onUnlockVault = {},
            onResetVault = {},
        )
    }
}
// endregion Previews
