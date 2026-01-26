@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.fibelatti.pinboard.features.user.presentation

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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.KeyboardActionHandler
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextObfuscationMode
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
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

    AuthScreen(
        onAuthRequested = authViewModel::login,
        isLoading = screenState.isLoading,
        apiTokenError = screenState.apiTokenError,
    )
}

@Composable
private fun AuthScreen(
    onAuthRequested: (token: String, instanceUrl: String) -> Unit,
    isLoading: Boolean,
    apiTokenError: String?,
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
                val authTokenFieldState = rememberTextFieldState()

                Text(
                    text = stringResource(id = R.string.auth_title_nostr),
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleLargeEmphasized,
                )

                var authTokenVisible by remember { mutableStateOf(false) }

                OutlinedSecureTextField(
                    state = authTokenFieldState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    textObfuscationMode = if (authTokenVisible) {
                        TextObfuscationMode.Visible
                    } else {
                        TextObfuscationMode.RevealLastTyped
                    },
                    label = { Text(text = stringResource(id = R.string.auth_token_hint)) },
                    trailingIcon = {
                        IconButton(
                            onClick = { authTokenVisible = !authTokenVisible },
                            shapes = IconButtonDefaults.shapes(),
                        ) {
                            AnimatedContent(
                                targetState = authTokenVisible,
                                label = "AuthTokenIconVisibility",
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
                        onAuthRequested(
                            authTokenFieldState.text.toString(),
                            "", // No instance URL needed for Nostr
                        )
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
                        modifier = Modifier.padding(top = 4.dp),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                AnimatedContent(
                    targetState = isLoading,
                    modifier = Modifier
                        .padding(top = 16.dp)
                        .align(Alignment.CenterHorizontally),
                    transitionSpec = { fadeIn() + scaleIn() togetherWith fadeOut() + scaleOut() },
                    label = "button-progress",
                ) { loading ->
                    if (loading) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                        )
                    } else {
                        Button(
                            onClick = {
                                onAuthRequested(
                                    authTokenFieldState.text.toString(),
                                    "", // No instance URL needed for Nostr
                                )
                            },
                            shapes = ExtendedTheme.defaultButtonShapes,
                        ) {
                            Text(text = stringResource(id = R.string.auth_button))
                        }
                    }
                }

                AuthTokenHelp(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(top = 8.dp),
                )
            }
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
private fun AuthScreenPreview() {
    ExtendedTheme {
        AuthScreen(
            onAuthRequested = { _, _ -> },
            isLoading = false,
            apiTokenError = null,
        )
    }
}

@Composable
@ThemePreviews
@DevicePreviews
private fun AuthScreenLoadingPreview() {
    ExtendedTheme {
        AuthScreen(
            onAuthRequested = { _, _ -> },
            isLoading = true,
            apiTokenError = null,
        )
    }
}

@Composable
@ThemePreviews
@DevicePreviews
private fun AuthScreenErrorPreview() {
    ExtendedTheme {
        AuthScreen(
            onAuthRequested = { _, _ -> },
            isLoading = false,
            apiTokenError = "Some error happened. Please try again.",
        )
    }
}
// endregion Previews
