package com.fibelatti.pinboard.features.nostr.data

import com.fibelatti.pinboard.features.nostr.data.model.NostrEvent
import com.fibelatti.pinboard.features.nostr.data.model.NostrFilter
import com.fibelatti.pinboard.features.nostr.data.model.NostrMessage
import com.fibelatti.pinboard.features.nostr.domain.DefaultRelays
import com.fibelatti.pinboard.features.nostr.domain.RelayConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
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
 * Client for communicating with Nostr relays over WebSocket.
 */
@Singleton
class NostrClient @Inject constructor(
    private val okHttpClient: OkHttpClient,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val activeConnections = ConcurrentHashMap<String, WebSocket>()
    private val subscriptionChannels = ConcurrentHashMap<String, Channel<NostrMessage>>()

    /**
     * Fetch events matching the given filter from configured relays.
     * Returns a Flow of events as they arrive.
     */
    fun fetchEvents(
        filter: NostrFilter,
        relays: List<RelayConfig> = DefaultRelays.relays,
        timeoutMs: Long = 15_000,
    ): Flow<NostrEvent> = flow {
        val subscriptionId = UUID.randomUUID().toString()
        Timber.tag(TAG).d("Starting subscription: $subscriptionId")
        Timber.tag(TAG).d("Filter: kinds=${filter.kinds}, authors=${filter.authors?.map { it.take(8) }}, limit=${filter.limit}")

        val events = mutableSetOf<String>() // Track by event ID to dedupe
        val channel = Channel<NostrMessage>(Channel.UNLIMITED)
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
                    is NostrMessage.Event -> {
                        if (events.add(message.event.id)) {
                            Timber.tag(TAG).d("EVENT: kind=${message.event.kind}, id=${message.event.id.take(8)}...")
                            emit(message.event)
                        }
                    }
                    is NostrMessage.EndOfStoredEvents -> {
                        eoseCount++
                        Timber.tag(TAG).d("EOSE received ($eoseCount/${connectedRelays.size})")
                    }
                    is NostrMessage.Closed -> {
                        Timber.tag(TAG).w("CLOSED: ${message.message}")
                        eoseCount++
                    }
                    is NostrMessage.Notice -> {
                        Timber.tag(TAG).w("NOTICE: ${message.message}")
                    }
                    else -> {}
                }
            }

            Timber.tag(TAG).d("Subscription complete: received ${events.size} unique events")
        } finally {
            // Clean up subscription
            closeSubscription(subscriptionId)
        }
    }

    /**
     * Fetch all events matching the filter and return as a list.
     * Convenience method when you need all results at once.
     */
    suspend fun fetchAllEvents(
        filter: NostrFilter,
        relays: List<RelayConfig> = DefaultRelays.relays,
        timeoutMs: Long = 15_000,
    ): List<NostrEvent> {
        val events = mutableListOf<NostrEvent>()
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

                // Send subscription request
                val reqMessage = NostrMessage.Request(subscriptionId, listOf(filter))
                val json = reqMessage.toJson()
                Timber.tag(TAG).d(">>> SEND to $relayUrl: $json")
                webSocket.send(json)

                connected = true
                if (continuation.isActive) {
                    continuation.resume(relayUrl)
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                // Log the raw message (truncate if too long)
                val logText = if (text.length > 500) "${text.take(500)}... (${text.length} chars)" else text
                Timber.tag(TAG).d("<<< RECV from $relayUrl: $logText")

                val message = NostrMessage.parse(text)
                if (message != null) {
                    val channel = subscriptionChannels[subscriptionId]
                    if (channel != null) {
                        scope.launch {
                            channel.send(message)
                        }
                    }
                } else {
                    Timber.tag(TAG).w("Failed to parse message: $logText")
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

    private fun closeSubscription(subscriptionId: String) {
        Timber.tag(TAG).d("Closing subscription: $subscriptionId")
        subscriptionChannels.remove(subscriptionId)?.close()

        val closeMessage = NostrMessage.Close(subscriptionId)
        val closeJson = closeMessage.toJson()

        activeConnections.forEach { (url, socket) ->
            try {
                Timber.tag(TAG).d(">>> SEND CLOSE to $url: $closeJson")
                socket.send(closeJson)
            } catch (e: Exception) {
                Timber.tag(TAG).w(e, "Failed to send CLOSE to $url")
            }
        }
    }

    /**
     * Close all connections and clean up resources.
     */
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

    companion object {
        private const val TAG = "NostrClient"
    }
}
