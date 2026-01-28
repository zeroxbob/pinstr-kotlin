package com.fibelatti.pinboard.features.nostr.data

import com.fibelatti.pinboard.features.posts.domain.model.Post
import com.fibelatti.pinboard.features.tags.domain.model.Tag
import com.vitorpamplona.quartz.nip01Core.core.Event
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/**
 * Maps between Quartz Event objects and domain models.
 */
class NostrEventMapper @Inject constructor() {

    private val dateFormatter = DateTimeFormatter.ISO_INSTANT

    /**
     * Convert a NIP-B0 bookmark event (kind 39701) to a Post domain model.
     */
    fun toPost(event: Event): Post? {
        if (event.kind.toInt() != NostrFilter.KIND_BOOKMARK) return null

        // The d-tag contains the URL without scheme
        val dTag = event.tags.firstOrNull { it.size >= 2 && it[0] == "d" }?.get(1) ?: return null

        // Reconstruct URL (assume https)
        val url = "https://$dTag"

        // Get title from tag or use URL as fallback
        val title = event.tags.firstOrNull { it.size >= 2 && it[0] == "title" }?.get(1) ?: dTag

        // Topics (t-tags) become tags
        val topics = event.tags
            .filter { it.size >= 2 && it[0] == "t" }
            .map { Tag(it[1]) }
            .takeIf { it.isNotEmpty() }

        // Convert timestamp to ISO format
        val dateAdded = formatTimestamp(event.createdAt)

        return Post(
            url = url,
            title = title,
            description = event.content,
            id = event.id,
            dateAdded = dateAdded,
            private = false, // Nostr events are public by default
            readLater = false,
            tags = topics,
            pendingSync = null,
        )
    }

    /**
     * Convert a Post domain model to Nostr event tags.
     * Returns the tag structure for a kind 39701 event.
     */
    fun toEventTags(post: Post): List<List<String>> {
        val tags = mutableListOf<List<String>>()

        // d-tag: URL without scheme (required for addressable events)
        val dTag = post.url.removePrefix("https://").removePrefix("http://")
        tags.add(listOf("d", dTag))

        // title tag
        if (post.title.isNotBlank()) {
            tags.add(listOf("title", post.title))
        }

        // t-tags for topics
        post.tags?.forEach { tag ->
            tags.add(listOf("t", tag.name))
        }

        // published_at for original timestamp
        tags.add(listOf("published_at", post.dateAdded))

        return tags
    }

    private fun formatTimestamp(epochSeconds: Long): String {
        return Instant.ofEpochSecond(epochSeconds)
            .atOffset(ZoneOffset.UTC)
            .format(dateFormatter)
    }

    /**
     * Parse an ISO timestamp to epoch seconds.
     */
    fun parseTimestamp(isoTimestamp: String): Long {
        return try {
            Instant.parse(isoTimestamp).epochSecond
        } catch (e: Exception) {
            Instant.now().epochSecond
        }
    }
}
