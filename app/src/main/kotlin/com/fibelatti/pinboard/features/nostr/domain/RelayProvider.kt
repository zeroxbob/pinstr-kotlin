package com.fibelatti.pinboard.features.nostr.domain

import com.fibelatti.pinboard.core.persistence.UserSharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Serializable version of RelayConfig for JSON storage.
 */
@Serializable
private data class RelayConfigJson(
    val url: String,
    val read: Boolean = true,
    val write: Boolean = true,
)

/**
 * Provides relay configuration for Nostr connections.
 * Users can customize relays, with fallback to defaults.
 */
@Singleton
class RelayProvider @Inject constructor(
    private val userSharedPreferences: UserSharedPreferences,
) {
    private val json = Json { ignoreUnknownKeys = true }

    private val _relays = MutableStateFlow(loadRelays())
    val relays: StateFlow<List<RelayConfig>> = _relays.asStateFlow()

    /**
     * Get the current list of relays (custom or default).
     */
    fun getRelays(): List<RelayConfig> = _relays.value

    /**
     * Returns true if using custom relays.
     */
    fun isUsingCustomRelays(): Boolean = userSharedPreferences.customRelays != null

    /**
     * Save custom relay configuration.
     */
    fun saveRelays(relays: List<RelayConfig>) {
        val jsonConfigs = relays.map { RelayConfigJson(it.url, it.read, it.write) }
        val jsonString = json.encodeToString(jsonConfigs)
        userSharedPreferences.customRelays = jsonString
        _relays.value = relays
        Timber.d("Saved ${relays.size} custom relays")
    }

    /**
     * Reset to default relays.
     */
    fun resetToDefaults() {
        userSharedPreferences.customRelays = null
        _relays.value = DefaultRelays.relays
        Timber.d("Reset to default relays")
    }

    /**
     * Add a new relay.
     */
    fun addRelay(url: String, read: Boolean = true, write: Boolean = true): Boolean {
        val normalizedUrl = normalizeRelayUrl(url)
        if (normalizedUrl == null) {
            Timber.w("Invalid relay URL: $url")
            return false
        }

        val current = _relays.value.toMutableList()
        if (current.any { it.url == normalizedUrl }) {
            Timber.w("Relay already exists: $normalizedUrl")
            return false
        }

        current.add(RelayConfig(normalizedUrl, read, write))
        saveRelays(current)
        return true
    }

    /**
     * Remove a relay by URL.
     */
    fun removeRelay(url: String) {
        val current = _relays.value.toMutableList()
        current.removeAll { it.url == url }
        saveRelays(current)
    }

    /**
     * Update a relay's read/write settings.
     */
    fun updateRelay(url: String, read: Boolean, write: Boolean) {
        val current = _relays.value.toMutableList()
        val index = current.indexOfFirst { it.url == url }
        if (index >= 0) {
            current[index] = current[index].copy(read = read, write = write)
            saveRelays(current)
        }
    }

    private fun loadRelays(): List<RelayConfig> {
        val jsonString = userSharedPreferences.customRelays
        if (jsonString == null) {
            Timber.d("Using default relays")
            return DefaultRelays.relays
        }

        return try {
            val jsonConfigs = json.decodeFromString<List<RelayConfigJson>>(jsonString)
            jsonConfigs.map { RelayConfig(it.url, it.read, it.write) }.also {
                Timber.d("Loaded ${it.size} custom relays")
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to parse custom relays, using defaults")
            DefaultRelays.relays
        }
    }

    private fun normalizeRelayUrl(url: String): String? {
        val trimmed = url.trim()
        return when {
            trimmed.startsWith("wss://") -> trimmed
            trimmed.startsWith("ws://") -> trimmed
            trimmed.contains("://") -> null // Invalid protocol
            else -> "wss://$trimmed" // Add wss:// prefix
        }
    }
}
