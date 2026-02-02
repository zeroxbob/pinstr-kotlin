package com.fibelatti.pinboard.features.nostr.presentation

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.fibelatti.pinboard.features.nostr.domain.RelayConfig
import com.fibelatti.pinboard.features.nostr.domain.RelayProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class RelaysViewModel @Inject constructor(
    private val relayProvider: RelayProvider,
) : ViewModel() {

    var state: State by mutableStateOf(loadState())
        private set

    fun addRelay(url: String) {
        val trimmedUrl = url.trim()
        if (trimmedUrl.isBlank()) return

        // Validate URL format
        val normalizedUrl = normalizeUrl(trimmedUrl)
        if (normalizedUrl == null) {
            state = state.copy(
                error = ErrorType.INVALID_URL,
                inputUrl = url,
            )
            return
        }

        // Check for duplicates
        if (state.relays.any { it.url == normalizedUrl }) {
            state = state.copy(
                error = ErrorType.DUPLICATE,
                inputUrl = url,
            )
            return
        }

        // Add the relay
        val success = relayProvider.addRelay(normalizedUrl)
        if (success) {
            state = loadState().copy(
                message = MessageType.RELAY_ADDED,
            )
        }
    }

    fun removeRelay(url: String) {
        relayProvider.removeRelay(url)
        state = loadState().copy(
            message = MessageType.RELAY_REMOVED,
        )
    }

    fun updateRelay(url: String, read: Boolean, write: Boolean) {
        relayProvider.updateRelay(url, read, write)
        state = loadState()
    }

    fun resetToDefaults() {
        relayProvider.resetToDefaults()
        state = loadState()
    }

    fun updateInputUrl(url: String) {
        state = state.copy(
            inputUrl = url,
            error = null,
        )
    }

    fun clearMessage() {
        state = state.copy(message = null)
    }

    fun clearError() {
        state = state.copy(error = null)
    }

    private fun loadState(): State = State(
        relays = relayProvider.getRelays(),
        isUsingCustomRelays = relayProvider.isUsingCustomRelays(),
        inputUrl = "",
        error = null,
        message = null,
    )

    private fun normalizeUrl(url: String): String? {
        return when {
            url.startsWith("wss://") -> url
            url.startsWith("ws://") -> url
            url.contains("://") -> null
            else -> "wss://$url"
        }
    }

    @Stable
    data class State(
        val relays: List<RelayConfig>,
        val isUsingCustomRelays: Boolean,
        val inputUrl: String,
        val error: ErrorType?,
        val message: MessageType?,
    )

    enum class ErrorType {
        INVALID_URL,
        DUPLICATE,
    }

    enum class MessageType {
        RELAY_ADDED,
        RELAY_REMOVED,
    }
}
