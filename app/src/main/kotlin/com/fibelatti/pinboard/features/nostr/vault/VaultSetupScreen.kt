@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.fibelatti.pinboard.features.nostr.vault

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedSecureTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
fun VaultSetupScreen(
    vaultViewModel: VaultViewModel = hiltViewModel(),
) {
    val screenState by vaultViewModel.screenState.collectAsStateWithLifecycle()

    val error by vaultViewModel.error.collectAsStateWithLifecycle()
    LaunchedErrorHandlerEffect(error = error, handler = vaultViewModel::errorHandled)

    VaultSetupScreen(
        isLoading = screenState.isLoading,
        error = screenState.error,
        onCreateVault = vaultViewModel::createVault,
        onCalculateStrength = vaultViewModel::calculatePassphraseStrength,
    )
}

@Composable
private fun VaultSetupScreen(
    isLoading: Boolean,
    error: String?,
    onCreateVault: (String, String) -> Unit,
    onCalculateStrength: (String) -> PassphraseStrength,
) {
    val windowInsetsSides = WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom

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
            painter = painterResource(id = R.drawable.ic_pin),
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
                    text = stringResource(id = R.string.vault_setup_title),
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleLargeEmphasized,
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = stringResource(id = R.string.vault_setup_description),
                    modifier = Modifier.padding(horizontal = 8.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium,
                )

                Spacer(modifier = Modifier.height(16.dp))

                VaultSetupForm(
                    isLoading = isLoading,
                    error = error,
                    onCreateVault = onCreateVault,
                    onCalculateStrength = onCalculateStrength,
                )
            }
        }
    }
}

@Composable
private fun VaultSetupForm(
    isLoading: Boolean,
    error: String?,
    onCreateVault: (String, String) -> Unit,
    onCalculateStrength: (String) -> PassphraseStrength,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val passphraseFieldState = rememberTextFieldState()
        val confirmFieldState = rememberTextFieldState()
        var passphraseVisible by remember { mutableStateOf(false) }
        var confirmVisible by remember { mutableStateOf(false) }

        val strength = onCalculateStrength(passphraseFieldState.text.toString())

        // Passphrase field
        OutlinedSecureTextField(
            state = passphraseFieldState,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 4.dp),
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
            keyboardOptions = KeyboardOptions(
                imeAction = ImeAction.Next,
                keyboardType = KeyboardType.Password,
            ),
            contentPadding = OutlinedTextFieldDefaults.contentPadding(
                start = 8.dp,
                end = 8.dp,
                bottom = 8.dp,
            ),
        )

        // Strength indicator
        PassphraseStrengthIndicator(
            strength = strength,
            modifier = Modifier.padding(bottom = 16.dp),
        )

        // Confirm passphrase field
        OutlinedSecureTextField(
            state = confirmFieldState,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            textObfuscationMode = if (confirmVisible) {
                TextObfuscationMode.Visible
            } else {
                TextObfuscationMode.RevealLastTyped
            },
            label = { Text(text = stringResource(id = R.string.vault_passphrase_confirm_hint)) },
            trailingIcon = {
                IconButton(
                    onClick = { confirmVisible = !confirmVisible },
                    shapes = IconButtonDefaults.shapes(),
                ) {
                    AnimatedContent(
                        targetState = confirmVisible,
                        label = "ConfirmIconVisibility",
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
                onCreateVault(
                    passphraseFieldState.text.toString(),
                    confirmFieldState.text.toString(),
                )
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

        // Create button
        AnimatedContent(
            targetState = isLoading,
            modifier = Modifier.padding(vertical = 8.dp),
            transitionSpec = { fadeIn() + scaleIn() togetherWith fadeOut() + scaleOut() },
            label = "vault-create-button-progress",
        ) { loading ->
            if (loading) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Creating vault...",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            } else {
                Button(
                    onClick = {
                        onCreateVault(
                            passphraseFieldState.text.toString(),
                            confirmFieldState.text.toString(),
                        )
                    },
                    shapes = ExtendedTheme.defaultButtonShapes,
                ) {
                    Text(text = stringResource(id = R.string.vault_create_button))
                }
            }
        }
    }
}

@Composable
private fun PassphraseStrengthIndicator(
    strength: PassphraseStrength,
    modifier: Modifier = Modifier,
) {
    val progress = when (strength) {
        PassphraseStrength.TOO_SHORT -> 0.2f
        PassphraseStrength.WEAK -> 0.4f
        PassphraseStrength.FAIR -> 0.6f
        PassphraseStrength.GOOD -> 0.8f
        PassphraseStrength.STRONG -> 1f
    }

    val color by animateColorAsState(
        targetValue = when (strength) {
            PassphraseStrength.TOO_SHORT -> MaterialTheme.colorScheme.error
            PassphraseStrength.WEAK -> MaterialTheme.colorScheme.error
            PassphraseStrength.FAIR -> MaterialTheme.colorScheme.tertiary
            PassphraseStrength.GOOD -> MaterialTheme.colorScheme.primary
            PassphraseStrength.STRONG -> MaterialTheme.colorScheme.primary
        },
        label = "strength-color",
    )

    val label = when (strength) {
        PassphraseStrength.TOO_SHORT -> stringResource(id = R.string.vault_strength_too_short)
        PassphraseStrength.WEAK -> stringResource(id = R.string.vault_strength_weak)
        PassphraseStrength.FAIR -> stringResource(id = R.string.vault_strength_fair)
        PassphraseStrength.GOOD -> stringResource(id = R.string.vault_strength_good)
        PassphraseStrength.STRONG -> stringResource(id = R.string.vault_strength_strong)
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .weight(1f)
                .height(8.dp),
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )

        Text(
            text = label,
            color = color,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

// region Previews
@Composable
@ThemePreviews
@DevicePreviews
private fun VaultSetupScreenPreview() {
    ExtendedTheme {
        VaultSetupScreen(
            isLoading = false,
            error = null,
            onCreateVault = { _, _ -> },
            onCalculateStrength = { PassphraseStrength.GOOD },
        )
    }
}

@Composable
@ThemePreviews
@DevicePreviews
private fun VaultSetupScreenLoadingPreview() {
    ExtendedTheme {
        VaultSetupScreen(
            isLoading = true,
            error = null,
            onCreateVault = { _, _ -> },
            onCalculateStrength = { PassphraseStrength.GOOD },
        )
    }
}

@Composable
@ThemePreviews
@DevicePreviews
private fun VaultSetupScreenErrorPreview() {
    ExtendedTheme {
        VaultSetupScreen(
            isLoading = false,
            error = "Passphrases do not match",
            onCreateVault = { _, _ -> },
            onCalculateStrength = { PassphraseStrength.FAIR },
        )
    }
}
// endregion Previews
