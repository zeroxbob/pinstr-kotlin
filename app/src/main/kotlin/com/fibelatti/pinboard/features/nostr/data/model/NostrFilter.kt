package com.fibelatti.pinboard.features.nostr.data.model

import kotlinx.serialization.Serializable

/**
 * Represents a Nostr filter for querying relays as defined in NIP-01.
 */
@Serializable
data class NostrFilter(
    val ids: List<String>? = null,
    val authors: List<String>? = null,
    val kinds: List<Int>? = null,
    val since: Long? = null,
    val until: Long? = null,
    val limit: Int? = null,
    @Serializable(with = TagFilterSerializer::class)
    val tagFilters: Map<String, List<String>>? = null,
) {
    companion object {
        /**
         * Creates a filter to fetch bookmarks for a specific pubkey.
         */
        fun bookmarksForAuthor(
            pubkey: String,
            since: Long? = null,
            until: Long? = null,
            limit: Int = 100,
        ): NostrFilter = NostrFilter(
            authors = listOf(pubkey),
            kinds = listOf(NostrEvent.KIND_BOOKMARK),
            since = since,
            until = until,
            limit = limit,
        )

        /**
         * Creates a filter to fetch a specific bookmark by its d-tag (URL without scheme).
         */
        fun bookmarkByDTag(
            pubkey: String,
            dTag: String,
        ): NostrFilter = NostrFilter(
            authors = listOf(pubkey),
            kinds = listOf(NostrEvent.KIND_BOOKMARK),
            tagFilters = mapOf("#d" to listOf(dTag)),
            limit = 1,
        )
    }
}

/**
 * Custom serializer for tag filters.
 * Nostr filters use "#<tagname>" keys for tag filtering.
 */
object TagFilterSerializer : kotlinx.serialization.KSerializer<Map<String, List<String>>?> {
    override val descriptor = kotlinx.serialization.descriptors.buildClassSerialDescriptor("TagFilter")

    override fun serialize(encoder: kotlinx.serialization.encoding.Encoder, value: Map<String, List<String>>?) {
        // Tag filters are serialized inline with the parent object
        // This is handled specially in NostrMessage serialization
    }

    override fun deserialize(decoder: kotlinx.serialization.encoding.Decoder): Map<String, List<String>>? = null
}
