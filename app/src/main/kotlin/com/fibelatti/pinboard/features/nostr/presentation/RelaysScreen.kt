@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.fibelatti.pinboard.features.nostr.presentation

import android.widget.Toast
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.fibelatti.pinboard.R
import com.fibelatti.pinboard.features.nostr.domain.RelayConfig
import com.fibelatti.ui.preview.ThemePreviews
import com.fibelatti.ui.theme.ExtendedTheme

@Composable
fun RelaysScreen(
    relaysViewModel: RelaysViewModel = hiltViewModel(),
) {
    val state = relaysViewModel.state
    val context = LocalContext.current

    // Show toast for messages
    LaunchedEffect(state.message) {
        state.message?.let { message ->
            val text = when (message) {
                RelaysViewModel.MessageType.RELAY_ADDED -> context.getString(R.string.relays_added_feedback)
                RelaysViewModel.MessageType.RELAY_REMOVED -> context.getString(R.string.relays_removed_feedback)
            }
            Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
            relaysViewModel.clearMessage()
        }
    }

    RelaysScreen(
        relays = state.relays,
        isUsingCustomRelays = state.isUsingCustomRelays,
        inputUrl = state.inputUrl,
        error = state.error,
        onInputUrlChange = relaysViewModel::updateInputUrl,
        onAddRelay = relaysViewModel::addRelay,
        onRemoveRelay = relaysViewModel::removeRelay,
        onUpdateRelay = relaysViewModel::updateRelay,
        onResetToDefaults = relaysViewModel::resetToDefaults,
        onClearError = relaysViewModel::clearError,
    )
}

@Composable
private fun RelaysScreen(
    relays: List<RelayConfig>,
    isUsingCustomRelays: Boolean,
    inputUrl: String,
    error: RelaysViewModel.ErrorType?,
    onInputUrlChange: (String) -> Unit,
    onAddRelay: (String) -> Unit,
    onRemoveRelay: (String) -> Unit,
    onUpdateRelay: (String, Boolean, Boolean) -> Unit,
    onResetToDefaults: () -> Unit,
    onClearError: () -> Unit,
) {
    val windowInsetsSides = WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom
    var showResetDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(color = ExtendedTheme.colors.backgroundNoOverlay)
            .padding(horizontal = 16.dp)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(windowInsetsSides)),
    ) {
        // Header
        Icon(
            painter = painterResource(id = R.drawable.ic_pin),
            contentDescription = null,
            modifier = Modifier
                .padding(top = 24.dp, bottom = 16.dp)
                .size(48.dp)
                .align(Alignment.CenterHorizontally),
            tint = MaterialTheme.colorScheme.primary,
        )

        Surface(
            modifier = Modifier
                .sizeIn(maxWidth = 600.dp)
                .align(Alignment.CenterHorizontally),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
            ) {
                Text(
                    text = stringResource(id = R.string.relays_title),
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleLargeEmphasized,
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = stringResource(id = R.string.relays_description),
                    modifier = Modifier.padding(horizontal = 8.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodySmall,
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Add relay input
                AddRelayInput(
                    inputUrl = inputUrl,
                    error = error,
                    onInputUrlChange = onInputUrlChange,
                    onAddRelay = onAddRelay,
                )

                Spacer(modifier = Modifier.height(16.dp))

                HorizontalDivider()

                // Relay list
                if (relays.isEmpty()) {
                    Text(
                        text = stringResource(id = R.string.relays_empty),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                            .sizeIn(maxHeight = 300.dp),
                    ) {
                        items(relays, key = { it.url }) { relay ->
                            RelayItem(
                                relay = relay,
                                onUpdateRelay = onUpdateRelay,
                                onRemoveRelay = onRemoveRelay,
                            )
                        }
                    }
                }

                // Reset button
                if (isUsingCustomRelays) {
                    Spacer(modifier = Modifier.height(16.dp))

                    TextButton(
                        onClick = { showResetDialog = true },
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    ) {
                        Text(text = stringResource(id = R.string.relays_reset_defaults))
                    }
                }
            }
        }
    }

    // Reset confirmation dialog
    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text(text = stringResource(id = R.string.relays_reset_defaults)) },
            text = { Text(text = stringResource(id = R.string.relays_reset_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showResetDialog = false
                        onResetToDefaults()
                    },
                ) {
                    Text(text = stringResource(id = R.string.hint_yes))
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text(text = stringResource(id = R.string.hint_no))
                }
            },
        )
    }
}

@Composable
private fun AddRelayInput(
    inputUrl: String,
    error: RelaysViewModel.ErrorType?,
    onInputUrlChange: (String) -> Unit,
    onAddRelay: (String) -> Unit,
) {
    val textFieldState = rememberTextFieldState(inputUrl)

    // Sync text field state with view model
    LaunchedEffect(textFieldState.text) {
        onInputUrlChange(textFieldState.text.toString())
    }

    LaunchedEffect(inputUrl) {
        if (inputUrl.isEmpty() && textFieldState.text.isNotEmpty()) {
            textFieldState.edit { replace(0, length, "") }
        }
    }

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
        ) {
            OutlinedTextField(
                state = textFieldState,
                modifier = Modifier.weight(1f),
                placeholder = { Text(text = stringResource(id = R.string.relays_add_hint)) },
                isError = error != null,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Done,
                ),
                lineLimits = TextFieldLineLimits.SingleLine,
            )

            Spacer(modifier = Modifier.width(8.dp))

            Button(
                onClick = { onAddRelay(textFieldState.text.toString()) },
                modifier = Modifier.padding(top = 4.dp),
            ) {
                Text(text = stringResource(id = R.string.relays_add_button))
            }
        }

        if (error != null) {
            Text(
                text = stringResource(
                    id = when (error) {
                        RelaysViewModel.ErrorType.INVALID_URL -> R.string.relays_error_invalid_url
                        RelaysViewModel.ErrorType.DUPLICATE -> R.string.relays_error_duplicate
                    },
                ),
                modifier = Modifier.padding(top = 4.dp, start = 8.dp),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun RelayItem(
    relay: RelayConfig,
    onUpdateRelay: (String, Boolean, Boolean) -> Unit,
    onRemoveRelay: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = relay.url.removePrefix("wss://").removePrefix("ws://"),
                color = MaterialTheme.colorScheme.onSurface,
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodyMedium,
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = relay.read,
                        onCheckedChange = { onUpdateRelay(relay.url, it, relay.write) },
                    )
                    Text(
                        text = stringResource(id = R.string.relays_read),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = relay.write,
                        onCheckedChange = { onUpdateRelay(relay.url, relay.read, it) },
                    )
                    Text(
                        text = stringResource(id = R.string.relays_write),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }

        IconButton(onClick = { onRemoveRelay(relay.url) }) {
            Icon(
                painter = painterResource(id = R.drawable.ic_delete),
                contentDescription = stringResource(id = R.string.relays_remove),
                tint = MaterialTheme.colorScheme.error,
            )
        }
    }
}

// region Previews
@Composable
@ThemePreviews
private fun RelaysScreenPreview() {
    ExtendedTheme {
        RelaysScreen(
            relays = listOf(
                RelayConfig("wss://relay.primal.net", read = true, write = true),
                RelayConfig("wss://relay.damus.io", read = true, write = true),
                RelayConfig("wss://relay.nostr.band", read = true, write = false),
            ),
            isUsingCustomRelays = true,
            inputUrl = "",
            error = null,
            onInputUrlChange = {},
            onAddRelay = {},
            onRemoveRelay = {},
            onUpdateRelay = { _, _, _ -> },
            onResetToDefaults = {},
            onClearError = {},
        )
    }
}

@Composable
@ThemePreviews
private fun RelaysScreenEmptyPreview() {
    ExtendedTheme {
        RelaysScreen(
            relays = emptyList(),
            isUsingCustomRelays = false,
            inputUrl = "",
            error = null,
            onInputUrlChange = {},
            onAddRelay = {},
            onRemoveRelay = {},
            onUpdateRelay = { _, _, _ -> },
            onResetToDefaults = {},
            onClearError = {},
        )
    }
}
// endregion Previews
