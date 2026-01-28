package com.fibelatti.pinboard.features.nostr.data.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Represents messages sent to/from Nostr relays as defined in NIP-01.
 */
sealed class NostrMessage {

    /**
     * Client-to-relay: Request events matching filters.
     * ["REQ", <subscription_id>, <filter1>, <filter2>, ...]
     */
    data class Request(
        val subscriptionId: String,
        val filters: List<NostrFilter>,
    ) : NostrMessage() {
        fun toJson(): String = buildJsonArray {
            add("REQ")
            add(subscriptionId)
            filters.forEach { filter ->
                add(buildJsonObject {
                    filter.ids?.let { put("ids", JsonArray(it.map { id -> JsonPrimitive(id) })) }
                    filter.authors?.let { put("authors", JsonArray(it.map { a -> JsonPrimitive(a) })) }
                    filter.kinds?.let { put("kinds", JsonArray(it.map { k -> JsonPrimitive(k) })) }
                    filter.since?.let { put("since", it) }
                    filter.until?.let { put("until", it) }
                    filter.limit?.let { put("limit", it) }
                    filter.tagFilters?.forEach { (key, values) ->
                        put(key, JsonArray(values.map { v -> JsonPrimitive(v) }))
                    }
                })
            }
        }.toString()
    }

    /**
     * Client-to-relay: Close a subscription.
     * ["CLOSE", <subscription_id>]
     */
    data class Close(
        val subscriptionId: String,
    ) : NostrMessage() {
        fun toJson(): String = buildJsonArray {
            add("CLOSE")
            add(subscriptionId)
        }.toString()
    }

    /**
     * Relay-to-client: An event matching a subscription.
     * ["EVENT", <subscription_id>, <event>]
     */
    data class Event(
        val subscriptionId: String,
        val event: NostrEvent,
    ) : NostrMessage()

    /**
     * Relay-to-client: End of stored events for a subscription.
     * ["EOSE", <subscription_id>]
     */
    data class EndOfStoredEvents(
        val subscriptionId: String,
    ) : NostrMessage()

    /**
     * Relay-to-client: Notice/error message.
     * ["NOTICE", <message>]
     */
    data class Notice(
        val message: String,
    ) : NostrMessage()

    /**
     * Relay-to-client: Subscription closed by relay.
     * ["CLOSED", <subscription_id>, <message>]
     */
    data class Closed(
        val subscriptionId: String,
        val message: String,
    ) : NostrMessage()

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        /**
         * Parse a JSON message from a relay into a NostrMessage.
         */
        fun parse(text: String): NostrMessage? {
            return try {
                val array = json.decodeFromString<JsonArray>(text)
                val type = (array[0] as? JsonPrimitive)?.content ?: return null

                when (type) {
                    "EVENT" -> {
                        val subscriptionId = (array[1] as? JsonPrimitive)?.content ?: return null
                        val eventJson = array[2].toString()
                        val event = json.decodeFromString<NostrEvent>(eventJson)
                        Event(subscriptionId, event)
                    }
                    "EOSE" -> {
                        val subscriptionId = (array[1] as? JsonPrimitive)?.content ?: return null
                        EndOfStoredEvents(subscriptionId)
                    }
                    "NOTICE" -> {
                        val message = (array[1] as? JsonPrimitive)?.content ?: ""
                        Notice(message)
                    }
                    "CLOSED" -> {
                        val subscriptionId = (array[1] as? JsonPrimitive)?.content ?: return null
                        val message = (array.getOrNull(2) as? JsonPrimitive)?.content ?: ""
                        Closed(subscriptionId, message)
                    }
                    else -> null
                }
            } catch (e: Exception) {
                null
            }
        }
    }
}
