package com.fibelatti.pinboard.features.nostr.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents a Nostr event as defined in NIP-01.
 * For bookmarks, we use kind 39701 as defined in NIP-B0.
 */
@Serializable
data class NostrEvent(
    val id: String,
    val pubkey: String,
    val created_at: Long,
    val kind: Int,
    val tags: List<List<String>>,
    val content: String,
    val sig: String,
) {
    companion object {
        const val KIND_BOOKMARK = 39701
    }

    /**
     * Get the value of a tag by its name (first element).
     * Returns null if the tag doesn't exist.
     */
    fun getTagValue(tagName: String): String? =
        tags.find { it.firstOrNull() == tagName }?.getOrNull(1)

    /**
     * Get all values for tags with the given name.
     * Useful for tags that can appear multiple times (like "t" for topics).
     */
    fun getAllTagValues(tagName: String): List<String> =
        tags.filter { it.firstOrNull() == tagName }.mapNotNull { it.getOrNull(1) }

    /**
     * For NIP-B0 bookmarks:
     * - "d" tag contains the URL without scheme (identifier for addressable events)
     * - "title" tag contains the bookmark title
     * - "t" tags contain topics/tags
     * - "published_at" tag contains the original publish timestamp
     * - content field contains the description/notes
     */
    val dTag: String? get() = getTagValue("d")
    val titleTag: String? get() = getTagValue("title")
    val topics: List<String> get() = getAllTagValues("t")
    val publishedAt: String? get() = getTagValue("published_at")
}
