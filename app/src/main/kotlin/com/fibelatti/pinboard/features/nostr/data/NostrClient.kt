package com.fibelatti.pinboard.features.nostr.data

import com.fibelatti.pinboard.features.nostr.data.model.NostrEvent
import com.fibelatti.pinboard.features.nostr.data.model.NostrFilter
import com.fibelatti.pinboard.features.nostr.data.model.NostrMessage
import com.fibelatti.pinboard.features.nostr.domain.DefaultRelays
import com.fibelatti.pinboard.features.nostr.domain.RelayConfig
import kotlinx.coroutines.CompletableDeferred
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
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Client for communicating with Nostr relays over WebSocket.
 */
@Singleton
class NostrClient @Inject constructor() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val okHttpClient = OkHttpClient.Builder()
        .readTimeout(30, TimeUnit.SECONDS)
        .connectTimeout(10, TimeUnit.SECONDS)
        .build()

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
        val events = mutableSetOf<String>() // Track by event ID to dedupe
        val channel = Channel<NostrMessage>(Channel.UNLIMITED)
        subscriptionChannels[subscriptionId] = channel

        try {
            // Connect to relays and send subscription
            val connectedRelays = relays.filter { it.read }.mapNotNull { relay ->
                connectAndSubscribe(relay.url, subscriptionId, filter)
            }

            if (connectedRelays.isEmpty()) {
                Timber.w("NostrClient: No relays connected")
                return@flow
            }

            // Collect events with timeout
            var eoseCount = 0
            val startTime = System.currentTimeMillis()

            while (eoseCount < connectedRelays.size) {
                val remainingTime = timeoutMs - (System.currentTimeMillis() - startTime)
                if (remainingTime <= 0) break

                val message = withTimeoutOrNull(remainingTime) {
                    channel.receive()
                } ?: break

                when (message) {
                    is NostrMessage.Event -> {
                        if (events.add(message.event.id)) {
                            emit(message.event)
                        }
                    }
                    is NostrMessage.EndOfStoredEvents -> {
                        eoseCount++
                        Timber.d("NostrClient: EOSE received ($eoseCount/${connectedRelays.size})")
                    }
                    is NostrMessage.Closed -> {
                        Timber.w("NostrClient: Subscription closed: ${message.message}")
                        eoseCount++
                    }
                    is NostrMessage.Notice -> {
                        Timber.w("NostrClient: Notice: ${message.message}")
                    }
                    else -> {}
                }
            }
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
        val request = Request.Builder()
            .url(relayUrl)
            .build()

        val listener = object : WebSocketListener() {
            private var connected = false

            override fun onOpen(webSocket: WebSocket, response: Response) {
                Timber.d("NostrClient: Connected to $relayUrl")
                activeConnections[relayUrl] = webSocket

                // Send subscription request
                val reqMessage = NostrMessage.Request(subscriptionId, listOf(filter))
                webSocket.send(reqMessage.toJson())

                connected = true
                if (continuation.isActive) {
                    continuation.resume(relayUrl)
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val message = NostrMessage.parse(text)
                if (message != null) {
                    val channel = subscriptionChannels[subscriptionId]
                    if (channel != null) {
                        scope.launch {
                            channel.send(message)
                        }
                    }
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Timber.e(t, "NostrClient: Connection failed to $relayUrl")
                activeConnections.remove(relayUrl)
                if (!connected && continuation.isActive) {
                    continuation.resume(null)
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Timber.d("NostrClient: Connection closed to $relayUrl: $reason")
                activeConnections.remove(relayUrl)
            }
        }

        okHttpClient.newWebSocket(request, listener)

        continuation.invokeOnCancellation {
            activeConnections[relayUrl]?.close(1000, "Cancelled")
        }
    }

    private fun closeSubscription(subscriptionId: String) {
        subscriptionChannels.remove(subscriptionId)?.close()

        val closeMessage = NostrMessage.Close(subscriptionId)
        val closeJson = closeMessage.toJson()

        activeConnections.values.forEach { socket ->
            try {
                socket.send(closeJson)
            } catch (e: Exception) {
                Timber.w(e, "NostrClient: Failed to send CLOSE")
            }
        }
    }

    /**
     * Close all connections and clean up resources.
     */
    fun disconnect() {
        subscriptionChannels.values.forEach { it.close() }
        subscriptionChannels.clear()

        activeConnections.values.forEach { socket ->
            try {
                socket.close(1000, "Client disconnect")
            } catch (e: Exception) {
                Timber.w(e, "NostrClient: Failed to close socket")
            }
        }
        activeConnections.clear()
    }
}
