package com.fibelatti.pinboard.features.nostr.domain

/**
 * Configuration for Nostr relay connections.
 */
data class RelayConfig(
    val url: String,
    val read: Boolean = true,
    val write: Boolean = true,
)

/**
 * Default relays used when no user-configured relays are available.
 * Includes popular general relays plus relay.nostr.band which indexes many events.
 */
object DefaultRelays {
    val relays = listOf(
        RelayConfig("wss://relay.damus.io", read = true, write = false),
        RelayConfig("wss://relay.primal.net", read = true, write = false),
        RelayConfig("wss://nos.lol", read = true, write = false),
        RelayConfig("wss://relay.nostr.band", read = true, write = false),
        RelayConfig("wss://purplepag.es", read = true, write = false),
        RelayConfig("wss://nostr.wine", read = true, write = false),
    )
}
