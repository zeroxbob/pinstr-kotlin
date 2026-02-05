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
 */
object DefaultRelays {
    val relays = listOf(
        RelayConfig("wss://relay.primal.net", read = true, write = true),
        RelayConfig("wss://relay.damus.io", read = true, write = true),
        RelayConfig("wss://relay.nostr.band", read = true, write = false), // Read-only aggregator
    )
}
