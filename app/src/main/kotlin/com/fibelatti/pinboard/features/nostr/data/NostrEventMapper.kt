package com.fibelatti.pinboard.features.nostr.data

import com.fibelatti.pinboard.features.nostr.data.model.NostrEvent
import com.fibelatti.pinboard.features.posts.domain.model.Post
import com.fibelatti.pinboard.features.tags.domain.model.Tag
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/**
 * Maps between Nostr events and domain models.
 */
class NostrEventMapper @Inject constructor() {

    private val dateFormatter = DateTimeFormatter.ISO_INSTANT

    /**
     * Convert a NIP-B0 bookmark event (kind 39701) to a Post domain model.
     */
    fun toPost(event: NostrEvent): Post? {
        if (event.kind != NostrEvent.KIND_BOOKMARK) return null

        // The d-tag contains the URL without scheme
        val dTag = event.dTag ?: return null

        // Reconstruct URL (assume https)
        val url = "https://$dTag"

        // Get title from tag or use URL as fallback
        val title = event.titleTag ?: dTag

        // Topics become tags
        val tags = event.topics.map { Tag(it) }.takeIf { it.isNotEmpty() }

        // Convert timestamp to ISO format
        val dateAdded = formatTimestamp(event.created_at)

        return Post(
            url = url,
            title = title,
            description = event.content,
            id = event.id,
            dateAdded = dateAdded,
            private = false, // Nostr events are public by default
            readLater = false,
            tags = tags,
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
