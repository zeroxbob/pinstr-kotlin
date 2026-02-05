@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.fibelatti.pinboard.features.user.presentation

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
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
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedSecureTextField
import androidx.compose.material3.OutlinedTextField
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
import androidx.core.text.HtmlCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fibelatti.pinboard.R
import com.fibelatti.pinboard.core.android.composable.LaunchedErrorHandlerEffect
import com.fibelatti.pinboard.core.android.composable.LongClickIconButton
import com.fibelatti.ui.components.TextWithLinks
import com.fibelatti.ui.preview.DevicePreviews
import com.fibelatti.ui.preview.ThemePreviews
import com.fibelatti.ui.theme.ExtendedTheme

@Composable
fun AuthScreen(
    authViewModel: AuthViewModel = hiltViewModel(),
) {
    val screenState by authViewModel.screenState.collectAsStateWithLifecycle()

    val error by authViewModel.error.collectAsStateWithLifecycle()
    LaunchedErrorHandlerEffect(error = error, handler = authViewModel::errorHandled)

    val amberLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            authViewModel.handleAmberResult(result.data)
        }
    }

    AuthScreen(
        isAmberInstalled = screenState.isAmberInstalled,
        selectedMethod = screenState.selectedMethod,
        isLoading = screenState.isLoading,
        apiTokenError = screenState.apiTokenError,
        onMethodSelected = authViewModel::selectLoginMethod,
        onBackToMethodSelection = authViewModel::clearSelectedMethod,
        onAmberLogin = { amberLauncher.launch(authViewModel.createAmberIntent()) },
        onBunkerLogin = authViewModel::loginWithBunker,
        onNsecLogin = authViewModel::loginWithNsec,
    )
}

@Composable
private fun AuthScreen(
    isAmberInstalled: Boolean,
    selectedMethod: NostrLoginMethod?,
    isLoading: Boolean,
    apiTokenError: String?,
    onMethodSelected: (NostrLoginMethod) -> Unit,
    onBackToMethodSelection: () -> Unit,
    onAmberLogin: () -> Unit,
    onBunkerLogin: (String) -> Unit,
    onNsecLogin: (String) -> Unit,
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
                    text = stringResource(id = R.string.auth_title_nostr),
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleLargeEmphasized,
                )

                Spacer(modifier = Modifier.height(16.dp))

                AnimatedContent(
                    targetState = selectedMethod,
                    label = "login-method-content",
                ) { method ->
                    when (method) {
                        null -> LoginMethodSelection(
                            isAmberInstalled = isAmberInstalled,
                            onMethodSelected = onMethodSelected,
                        )
                        NostrLoginMethod.AMBER -> AmberLoginContent(
                            isLoading = isLoading,
                            apiTokenError = apiTokenError,
                            onAmberLogin = onAmberLogin,
                            onBack = onBackToMethodSelection,
                        )
                        NostrLoginMethod.BUNKER -> BunkerLoginContent(
                            isLoading = isLoading,
                            apiTokenError = apiTokenError,
                            onBunkerLogin = onBunkerLogin,
                            onBack = onBackToMethodSelection,
                        )
                        NostrLoginMethod.NSEC -> NsecLoginContent(
                            isLoading = isLoading,
                            apiTokenError = apiTokenError,
                            onNsecLogin = onNsecLogin,
                            onBack = onBackToMethodSelection,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LoginMethodSelection(
    isAmberInstalled: Boolean,
    onMethodSelected: (NostrLoginMethod) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Amber button (recommended if installed)
        Button(
            onClick = { onMethodSelected(NostrLoginMethod.AMBER) },
            modifier = Modifier.fillMaxWidth(),
            enabled = isAmberInstalled,
            shapes = ExtendedTheme.defaultButtonShapes,
        ) {
            Text(text = stringResource(id = R.string.auth_method_amber))
        }
        Text(
            text = if (isAmberInstalled) {
                stringResource(id = R.string.auth_method_amber_description)
            } else {
                stringResource(id = R.string.auth_amber_not_installed)
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodySmall,
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Bunker button
        OutlinedButton(
            onClick = { onMethodSelected(NostrLoginMethod.BUNKER) },
            modifier = Modifier.fillMaxWidth(),
            shapes = ExtendedTheme.defaultButtonShapes,
        ) {
            Text(text = stringResource(id = R.string.auth_method_bunker))
        }
        Text(
            text = stringResource(id = R.string.auth_method_bunker_description),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodySmall,
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Nsec button
        OutlinedButton(
            onClick = { onMethodSelected(NostrLoginMethod.NSEC) },
            modifier = Modifier.fillMaxWidth(),
            shapes = ExtendedTheme.defaultButtonShapes,
        ) {
            Text(text = stringResource(id = R.string.auth_method_nsec))
        }
        Text(
            text = stringResource(id = R.string.auth_method_nsec_description),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun AmberLoginContent(
    isLoading: Boolean,
    apiTokenError: String?,
    onAmberLogin: () -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(id = R.string.auth_method_amber_description),
            modifier = Modifier.padding(bottom = 16.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
        )

        if (apiTokenError != null) {
            Text(
                text = apiTokenError,
                modifier = Modifier.padding(bottom = 8.dp),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        AnimatedContent(
            targetState = isLoading,
            modifier = Modifier.padding(vertical = 8.dp),
            transitionSpec = { fadeIn() + scaleIn() togetherWith fadeOut() + scaleOut() },
            label = "amber-button-progress",
        ) { loading ->
            if (loading) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            } else {
                Button(
                    onClick = onAmberLogin,
                    shapes = ExtendedTheme.defaultButtonShapes,
                ) {
                    Text(text = stringResource(id = R.string.auth_method_amber))
                }
            }
        }

        TextButton(onClick = onBack) {
            Text(text = stringResource(id = R.string.cd_navigate_back))
        }
    }
}

@Composable
private fun BunkerLoginContent(
    isLoading: Boolean,
    apiTokenError: String?,
    onBunkerLogin: (String) -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val bunkerFieldState = rememberTextFieldState()

        OutlinedTextField(
            state = bunkerFieldState,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            label = { Text(text = stringResource(id = R.string.auth_bunker_hint)) },
            isError = apiTokenError != null,
            keyboardOptions = KeyboardOptions(
                imeAction = ImeAction.Go,
                keyboardType = KeyboardType.Uri,
            ),
            onKeyboardAction = KeyboardActionHandler {
                onBunkerLogin(bunkerFieldState.text.toString())
            },
            contentPadding = OutlinedTextFieldDefaults.contentPadding(
                start = 8.dp,
                end = 8.dp,
                bottom = 8.dp,
            ),
        )

        if (apiTokenError != null) {
            Text(
                text = apiTokenError,
                modifier = Modifier.padding(bottom = 8.dp),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        TextWithLinks(
            text = HtmlCompat.fromHtml(
                stringResource(R.string.auth_bunker_description),
                HtmlCompat.FROM_HTML_MODE_COMPACT,
            ),
            modifier = Modifier.padding(vertical = 8.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            linkColor = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodySmall,
        )

        AnimatedContent(
            targetState = isLoading,
            modifier = Modifier.padding(vertical = 8.dp),
            transitionSpec = { fadeIn() + scaleIn() togetherWith fadeOut() + scaleOut() },
            label = "bunker-button-progress",
        ) { loading ->
            if (loading) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            } else {
                Button(
                    onClick = { onBunkerLogin(bunkerFieldState.text.toString()) },
                    shapes = ExtendedTheme.defaultButtonShapes,
                ) {
                    Text(text = stringResource(id = R.string.auth_button))
                }
            }
        }

        TextButton(onClick = onBack) {
            Text(text = stringResource(id = R.string.cd_navigate_back))
        }
    }
}

@Composable
private fun NsecLoginContent(
    isLoading: Boolean,
    apiTokenError: String?,
    onNsecLogin: (String) -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val nsecFieldState = rememberTextFieldState()
        var nsecVisible by remember { mutableStateOf(false) }

        OutlinedSecureTextField(
            state = nsecFieldState,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            textObfuscationMode = if (nsecVisible) {
                TextObfuscationMode.Visible
            } else {
                TextObfuscationMode.RevealLastTyped
            },
            label = { Text(text = stringResource(id = R.string.auth_token_hint)) },
            trailingIcon = {
                IconButton(
                    onClick = { nsecVisible = !nsecVisible },
                    shapes = IconButtonDefaults.shapes(),
                ) {
                    AnimatedContent(
                        targetState = nsecVisible,
                        label = "NsecIconVisibility",
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
            isError = apiTokenError != null,
            keyboardOptions = KeyboardOptions(
                imeAction = ImeAction.Go,
                keyboardType = KeyboardType.Password,
            ),
            onKeyboardAction = KeyboardActionHandler {
                onNsecLogin(nsecFieldState.text.toString())
            },
            contentPadding = OutlinedTextFieldDefaults.contentPadding(
                start = 8.dp,
                end = 8.dp,
                bottom = 8.dp,
            ),
        )

        if (apiTokenError != null) {
            Text(
                text = apiTokenError,
                modifier = Modifier.padding(bottom = 8.dp),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        AuthTokenHelp(modifier = Modifier.padding(vertical = 8.dp))

        AnimatedContent(
            targetState = isLoading,
            modifier = Modifier.padding(vertical = 8.dp),
            transitionSpec = { fadeIn() + scaleIn() togetherWith fadeOut() + scaleOut() },
            label = "nsec-button-progress",
        ) { loading ->
            if (loading) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            } else {
                Button(
                    onClick = { onNsecLogin(nsecFieldState.text.toString()) },
                    shapes = ExtendedTheme.defaultButtonShapes,
                ) {
                    Text(text = stringResource(id = R.string.auth_button))
                }
            }
        }

        TextButton(onClick = onBack) {
            Text(text = stringResource(id = R.string.cd_navigate_back))
        }
    }
}

@Composable
private fun AuthTokenHelp(
    modifier: Modifier = Modifier,
) {
    var helpVisible by remember { mutableStateOf(false) }

    AnimatedContent(
        targetState = helpVisible,
        modifier = modifier,
        transitionSpec = { fadeIn() + expandVertically() togetherWith fadeOut() + scaleOut() },
        label = "help-icon",
    ) { visible ->
        if (visible) {
            TextWithLinks(
                text = HtmlCompat.fromHtml(
                    stringResource(R.string.auth_token_description),
                    HtmlCompat.FROM_HTML_MODE_COMPACT,
                ),
                modifier = Modifier.padding(top = 8.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                linkColor = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium,
            )
        } else {
            LongClickIconButton(
                painter = painterResource(id = R.drawable.ic_help),
                description = stringResource(id = R.string.hint_help),
                onClick = { helpVisible = true },
                iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// region Previews
@Composable
@ThemePreviews
@DevicePreviews
private fun AuthScreenMethodSelectionPreview() {
    ExtendedTheme {
        AuthScreen(
            isAmberInstalled = true,
            selectedMethod = null,
            isLoading = false,
            apiTokenError = null,
            onMethodSelected = {},
            onBackToMethodSelection = {},
            onAmberLogin = {},
            onBunkerLogin = {},
            onNsecLogin = {},
        )
    }
}

@Composable
@ThemePreviews
@DevicePreviews
private fun AuthScreenNsecPreview() {
    ExtendedTheme {
        AuthScreen(
            isAmberInstalled = true,
            selectedMethod = NostrLoginMethod.NSEC,
            isLoading = false,
            apiTokenError = null,
            onMethodSelected = {},
            onBackToMethodSelection = {},
            onAmberLogin = {},
            onBunkerLogin = {},
            onNsecLogin = {},
        )
    }
}

@Composable
@ThemePreviews
@DevicePreviews
private fun AuthScreenLoadingPreview() {
    ExtendedTheme {
        AuthScreen(
            isAmberInstalled = true,
            selectedMethod = NostrLoginMethod.NSEC,
            isLoading = true,
            apiTokenError = null,
            onMethodSelected = {},
            onBackToMethodSelection = {},
            onAmberLogin = {},
            onBunkerLogin = {},
            onNsecLogin = {},
        )
    }
}

@Composable
@ThemePreviews
@DevicePreviews
private fun AuthScreenErrorPreview() {
    ExtendedTheme {
        AuthScreen(
            isAmberInstalled = true,
            selectedMethod = NostrLoginMethod.NSEC,
            isLoading = false,
            apiTokenError = "Some error happened. Please try again.",
            onMethodSelected = {},
            onBackToMethodSelection = {},
            onAmberLogin = {},
            onBunkerLogin = {},
            onNsecLogin = {},
        )
    }
}
// endregion Previews
