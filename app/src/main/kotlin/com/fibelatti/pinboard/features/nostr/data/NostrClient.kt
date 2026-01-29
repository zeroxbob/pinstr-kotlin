package com.fibelatti.pinboard.features.nostr.data

import com.fibelatti.pinboard.features.nostr.domain.DefaultRelays
import com.fibelatti.pinboard.features.nostr.domain.RelayConfig
import com.vitorpamplona.quartz.nip01Core.core.Event
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import timber.log.Timber
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Simplified filter for Nostr relay queries.
 * Uses Quartz Event for parsing responses.
 */
data class NostrFilter(
    val kinds: List<Int>? = null,
    val authors: List<String>? = null,
    val limit: Int? = null,
    val since: Long? = null,
    val until: Long? = null,
) {
    fun toJson(): String = buildJsonObject {
        kinds?.let { put("kinds", JsonArray(it.map { k -> JsonPrimitive(k) })) }
        authors?.let { put("authors", JsonArray(it.map { a -> JsonPrimitive(a) })) }
        limit?.let { put("limit", it) }
        since?.let { put("since", it) }
        until?.let { put("until", it) }
    }.toString()

    companion object {
        const val KIND_BOOKMARK = 39701

        fun bookmarksForAuthor(pubkey: String, limit: Int = 100): NostrFilter =
            NostrFilter(
                kinds = listOf(KIND_BOOKMARK),
                authors = listOf(pubkey),
                limit = limit,
            )
    }
}

/**
 * Represents parsed relay messages.
 */
sealed class RelayMessage {
    data class EventMessage(val subscriptionId: String, val event: Event) : RelayMessage()
    data class EndOfStoredEvents(val subscriptionId: String) : RelayMessage()
    data class Notice(val message: String) : RelayMessage()
    data class Closed(val subscriptionId: String, val message: String) : RelayMessage()
}

/**
 * Client for communicating with Nostr relays over WebSocket.
 * Uses Quartz library for event parsing.
 */
@Singleton
class NostrClient @Inject constructor(
    private val okHttpClient: OkHttpClient,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true }

    private val activeConnections = ConcurrentHashMap<String, WebSocket>()
    private val subscriptionChannels = ConcurrentHashMap<String, Channel<RelayMessage>>()

    /**
     * Fetch events matching the given filter from configured relays.
     * Returns a Flow of Quartz Event objects as they arrive.
     */
    fun fetchEvents(
        filter: NostrFilter,
        relays: List<RelayConfig> = DefaultRelays.relays,
        timeoutMs: Long = 15_000,
    ): Flow<Event> = flow {
        val subscriptionId = UUID.randomUUID().toString()
        Timber.tag(TAG).d("Starting subscription: $subscriptionId")
        Timber.tag(TAG).d("Filter: kinds=${filter.kinds}, authors=${filter.authors?.map { it.take(8) }}, limit=${filter.limit}")

        val events = mutableSetOf<String>() // Track by event ID to dedupe
        val channel = Channel<RelayMessage>(Channel.UNLIMITED)
        subscriptionChannels[subscriptionId] = channel

        try {
            // Connect to relays and send subscription
            val connectedRelays = relays.filter { it.read }.mapNotNull { relay ->
                connectAndSubscribe(relay.url, subscriptionId, filter)
            }

            if (connectedRelays.isEmpty()) {
                Timber.tag(TAG).w("No relays connected!")
                return@flow
            }

            Timber.tag(TAG).d("Connected to ${connectedRelays.size} relays: $connectedRelays")

            // Collect events with timeout
            var eoseCount = 0
            val startTime = System.currentTimeMillis()

            while (eoseCount < connectedRelays.size) {
                val remainingTime = timeoutMs - (System.currentTimeMillis() - startTime)
                if (remainingTime <= 0) {
                    Timber.tag(TAG).w("Timeout reached after ${timeoutMs}ms")
                    break
                }

                val message = withTimeoutOrNull(remainingTime) {
                    channel.receive()
                } ?: break

                when (message) {
                    is RelayMessage.EventMessage -> {
                        if (events.add(message.event.id)) {
                            Timber.tag(TAG).d("EVENT: kind=${message.event.kind}, id=${message.event.id.take(8)}...")
                            emit(message.event)
                        }
                    }
                    is RelayMessage.EndOfStoredEvents -> {
                        eoseCount++
                        Timber.tag(TAG).d("EOSE received ($eoseCount/${connectedRelays.size})")
                    }
                    is RelayMessage.Closed -> {
                        Timber.tag(TAG).w("CLOSED: ${message.message}")
                        eoseCount++
                    }
                    is RelayMessage.Notice -> {
                        Timber.tag(TAG).w("NOTICE: ${message.message}")
                    }
                }
            }

            Timber.tag(TAG).d("Subscription complete: received ${events.size} unique events")
        } finally {
            closeSubscription(subscriptionId)
        }
    }

    /**
     * Fetch all events matching the filter and return as a list.
     */
    suspend fun fetchAllEvents(
        filter: NostrFilter,
        relays: List<RelayConfig> = DefaultRelays.relays,
        timeoutMs: Long = 15_000,
    ): List<Event> {
        val events = mutableListOf<Event>()
        fetchEvents(filter, relays, timeoutMs).collect { events.add(it) }
        return events
    }

    private suspend fun connectAndSubscribe(
        relayUrl: String,
        subscriptionId: String,
        filter: NostrFilter,
    ): String? = suspendCancellableCoroutine { continuation ->
        Timber.tag(TAG).d("Connecting to relay: $relayUrl")

        val request = Request.Builder()
            .url(relayUrl)
            .build()

        val listener = object : WebSocketListener() {
            private var connected = false

            override fun onOpen(webSocket: WebSocket, response: Response) {
                Timber.tag(TAG).d("WebSocket OPEN: $relayUrl (${response.code})")
                activeConnections[relayUrl] = webSocket

                // Send subscription request: ["REQ", <sub_id>, <filter>]
                val reqJson = buildJsonArray {
                    add("REQ")
                    add(subscriptionId)
                    add(json.parseToJsonElement(filter.toJson()))
                }.toString()

                Timber.tag(TAG).d(">>> SEND to $relayUrl: $reqJson")
                webSocket.send(reqJson)

                connected = true
                if (continuation.isActive) {
                    continuation.resume(relayUrl)
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val logText = if (text.length > 500) "${text.take(500)}... (${text.length} chars)" else text
                Timber.tag(TAG).d("<<< RECV from $relayUrl: $logText")

                val message = parseRelayMessage(text)
                if (message != null) {
                    subscriptionChannels[subscriptionId]?.let { channel ->
                        scope.launch { channel.send(message) }
                    }
                } else {
                    Timber.tag(TAG).w("Failed to parse message")
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Timber.tag(TAG).e(t, "WebSocket FAILURE: $relayUrl (response=${response?.code})")
                activeConnections.remove(relayUrl)
                if (!connected && continuation.isActive) {
                    continuation.resume(null)
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Timber.tag(TAG).d("WebSocket CLOSED: $relayUrl (code=$code, reason=$reason)")
                activeConnections.remove(relayUrl)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Timber.tag(TAG).d("WebSocket CLOSING: $relayUrl (code=$code, reason=$reason)")
            }
        }

        okHttpClient.newWebSocket(request, listener)

        continuation.invokeOnCancellation {
            Timber.tag(TAG).d("Cancelling connection to $relayUrl")
            activeConnections[relayUrl]?.close(1000, "Cancelled")
        }
    }

    private fun parseRelayMessage(text: String): RelayMessage? {
        return try {
            val array = json.decodeFromString<JsonArray>(text)
            val type = (array[0] as? JsonPrimitive)?.content ?: return null

            when (type) {
                "EVENT" -> {
                    val subscriptionId = (array[1] as? JsonPrimitive)?.content ?: return null
                    val eventJson = array[2].toString()
                    // Use Quartz's Event.fromJson for parsing
                    val event = Event.fromJson(eventJson)
                    RelayMessage.EventMessage(subscriptionId, event)
                }
                "EOSE" -> {
                    val subscriptionId = (array[1] as? JsonPrimitive)?.content ?: return null
                    RelayMessage.EndOfStoredEvents(subscriptionId)
                }
                "NOTICE" -> {
                    val message = (array[1] as? JsonPrimitive)?.content ?: ""
                    RelayMessage.Notice(message)
                }
                "CLOSED" -> {
                    val subscriptionId = (array[1] as? JsonPrimitive)?.content ?: return null
                    val message = (array.getOrNull(2) as? JsonPrimitive)?.content ?: ""
                    RelayMessage.Closed(subscriptionId, message)
                }
                else -> null
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to parse relay message")
            null
        }
    }

    private fun closeSubscription(subscriptionId: String) {
        Timber.tag(TAG).d("Closing subscription: $subscriptionId")
        subscriptionChannels.remove(subscriptionId)?.close()

        val closeJson = buildJsonArray {
            add("CLOSE")
            add(subscriptionId)
        }.toString()

        activeConnections.forEach { (url, socket) ->
            try {
                Timber.tag(TAG).d(">>> SEND CLOSE to $url: $closeJson")
                socket.send(closeJson)
            } catch (e: Exception) {
                Timber.tag(TAG).w(e, "Failed to send CLOSE to $url")
            }
        }
    }

    fun disconnect() {
        Timber.tag(TAG).d("Disconnecting all (${activeConnections.size} connections)")
        subscriptionChannels.values.forEach { it.close() }
        subscriptionChannels.clear()

        activeConnections.forEach { (url, socket) ->
            try {
                Timber.tag(TAG).d("Closing connection to $url")
                socket.close(1000, "Client disconnect")
            } catch (e: Exception) {
                Timber.tag(TAG).w(e, "Failed to close socket to $url")
            }
        }
        activeConnections.clear()
    }

    /**
     * Publish an event to the configured relays.
     * Returns true if at least one relay accepted the event.
     */
    suspend fun publishEvent(
        event: Event,
        relays: List<RelayConfig> = DefaultRelays.relays,
        timeoutMs: Long = 10_000,
    ): Boolean {
        Timber.tag(TAG).d("Publishing event: kind=${event.kind}, id=${event.id.take(8)}...")

        val eventJson = buildJsonArray {
            add("EVENT")
            add(json.parseToJsonElement(event.toJson()))
        }.toString()

        val writeRelays = relays.filter { it.write }
        if (writeRelays.isEmpty()) {
            Timber.tag(TAG).w("No write relays configured!")
            return false
        }

        var successCount = 0
        val results = mutableMapOf<String, Boolean>()

        writeRelays.forEach { relay ->
            try {
                val accepted = publishToRelay(relay.url, eventJson, event.id, timeoutMs)
                results[relay.url] = accepted
                if (accepted) successCount++
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Failed to publish to ${relay.url}")
                results[relay.url] = false
            }
        }

        Timber.tag(TAG).d("Publish results: $successCount/${writeRelays.size} relays accepted")
        return successCount > 0
    }

    private suspend fun publishToRelay(
        relayUrl: String,
        eventJson: String,
        eventId: String,
        timeoutMs: Long,
    ): Boolean = suspendCancellableCoroutine { continuation ->
        Timber.tag(TAG).d("Publishing to relay: $relayUrl")

        val request = Request.Builder()
            .url(relayUrl)
            .build()

        val listener = object : WebSocketListener() {
            private var resumed = false

            override fun onOpen(webSocket: WebSocket, response: Response) {
                Timber.tag(TAG).d(">>> SEND EVENT to $relayUrl")
                webSocket.send(eventJson)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Timber.tag(TAG).d("<<< RECV from $relayUrl: $text")

                // Parse OK response: ["OK", <event_id>, <accepted>, <message>]
                try {
                    val array = json.decodeFromString<JsonArray>(text)
                    val type = (array[0] as? JsonPrimitive)?.content
                    if (type == "OK" && array.size >= 3) {
                        val receivedId = (array[1] as? JsonPrimitive)?.content
                        val accepted = (array[2] as? JsonPrimitive)?.content?.toBooleanStrictOrNull() ?: false
                        val message = (array.getOrNull(3) as? JsonPrimitive)?.content ?: ""

                        if (receivedId == eventId && !resumed) {
                            resumed = true
                            Timber.tag(TAG).d("Event ${if (accepted) "accepted" else "rejected"} by $relayUrl: $message")
                            webSocket.close(1000, "Done")
                            continuation.resume(accepted)
                        }
                    }
                } catch (e: Exception) {
                    Timber.tag(TAG).w(e, "Failed to parse OK response")
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Timber.tag(TAG).e(t, "WebSocket FAILURE publishing to $relayUrl")
                if (!resumed) {
                    resumed = true
                    continuation.resume(false)
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (!resumed) {
                    resumed = true
                    continuation.resume(false)
                }
            }
        }

        val webSocket = okHttpClient.newWebSocket(request, listener)

        // Timeout handler
        scope.launch {
            kotlinx.coroutines.delay(timeoutMs)
            if (continuation.isActive) {
                Timber.tag(TAG).w("Publish timeout for $relayUrl")
                webSocket.close(1000, "Timeout")
            }
        }

        continuation.invokeOnCancellation {
            webSocket.close(1000, "Cancelled")
        }
    }

    companion object {
        private const val TAG = "NostrClient"
    }
}
