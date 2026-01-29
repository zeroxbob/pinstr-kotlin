package com.fibelatti.pinboard.features.nostr.data

import com.fibelatti.core.functional.Failure
import com.fibelatti.core.functional.Result
import com.fibelatti.core.functional.Success
import com.fibelatti.core.functional.catching
import com.fibelatti.core.functional.getOrDefault
import com.fibelatti.pinboard.core.AppConfig
import com.fibelatti.pinboard.core.functional.resultFrom
import com.fibelatti.pinboard.core.network.InvalidRequestException
import com.fibelatti.pinboard.core.util.DateFormatter
import com.fibelatti.pinboard.features.appstate.SortType
import com.fibelatti.pinboard.features.nostr.signer.NostrSignerProvider
import com.fibelatti.pinboard.features.nostr.signer.SignerType
import com.fibelatti.pinboard.features.nostr.vault.VaultCrypto
import com.fibelatti.pinboard.features.nostr.vault.VaultProvider
import com.fibelatti.pinboard.features.posts.data.PostsDao
import com.fibelatti.pinboard.features.posts.data.model.PostDto
import com.fibelatti.pinboard.features.posts.data.model.PostDtoMapper
import com.fibelatti.pinboard.features.posts.domain.PostVisibility
import com.fibelatti.pinboard.features.posts.domain.PostsRepository
import com.fibelatti.pinboard.features.posts.domain.model.Post
import com.fibelatti.pinboard.features.posts.domain.model.PostListResult
import com.fibelatti.pinboard.features.tags.domain.model.Tag
import com.fibelatti.pinboard.features.user.domain.UserRepository
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject

/**
 * Structure of encrypted bookmark content for private bookmarks.
 * All fields are encrypted together as JSON to prevent metadata leakage.
 */
@Serializable
private data class EncryptedBookmarkContent(
    val url: String,
    val title: String? = null,
    val description: String? = null,
    val tags: List<String>? = null,
    val publishedAt: Long? = null,
)

/**
 * PostsRepository implementation that fetches and publishes bookmarks to Nostr relays.
 * Uses kind 39701 events (NIP-B0 bookmark format).
 */
class PostsDataSourceNostrApi @Inject constructor(
    private val nostrClient: NostrClient,
    private val nostrEventMapper: NostrEventMapper,
    private val postsDao: PostsDao,
    private val postDtoMapper: PostDtoMapper,
    private val dateFormatter: DateFormatter,
    private val userRepository: UserRepository,
    private val nostrSignerProvider: NostrSignerProvider,
    private val vaultProvider: VaultProvider,
) : PostsRepository {

    override suspend fun update(): Result<String> {
        return try {
            val pubkey = userRepository.nostrPubkey
            if (pubkey.isBlank()) {
                return Failure(InvalidRequestException())
            }

            Timber.d("NostrDataSource: Fetching bookmarks for pubkey: ${pubkey.take(16)}...")

            // Fetch public bookmarks from user's pubkey
            val publicFilter = NostrFilter.bookmarksForAuthor(pubkey, limit = 500)
            val publicEvents = nostrClient.fetchAllEvents(publicFilter)
            Timber.d("NostrDataSource: Received ${publicEvents.size} public events")

            val publicPosts = publicEvents.mapNotNull { event ->
                nostrEventMapper.toPost(event)
            }

            // Fetch private bookmarks from vault pubkey if vault is unlocked
            val privatePosts = fetchPrivateBookmarks()
            Timber.d("NostrDataSource: Fetched ${privatePosts.size} private posts")

            val allPosts = publicPosts + privatePosts

            if (allPosts.isNotEmpty()) {
                // Convert to DTOs and save to local cache
                val dtos = allPosts.map { post ->
                    PostDto(
                        href = post.url,
                        description = post.title,
                        extended = post.description,
                        hash = post.id,
                        time = post.dateAdded,
                        shared = if (post.private == true) {
                            AppConfig.PinboardApiLiterals.NO
                        } else {
                            AppConfig.PinboardApiLiterals.YES
                        },
                        toread = if (post.readLater == true) {
                            AppConfig.PinboardApiLiterals.YES
                        } else {
                            AppConfig.PinboardApiLiterals.NO
                        },
                        tags = post.tags?.joinToString(AppConfig.PinboardApiLiterals.TAG_SEPARATOR) { it.name }.orEmpty(),
                    )
                }

                postsDao.deleteAllPosts()
                postsDao.savePosts(dtos)

                Timber.d("NostrDataSource: Cached ${dtos.size} posts locally (${publicPosts.size} public, ${privatePosts.size} private)")
            }

            Success(dateFormatter.nowAsDataFormat())
        } catch (e: Exception) {
            Timber.e(e, "NostrDataSource: Failed to fetch bookmarks")
            Failure(e)
        }
    }

    /**
     * Fetches and decrypts private bookmarks from the vault identity.
     * Private bookmarks have ALL data encrypted as JSON in the content field.
     */
    private suspend fun fetchPrivateBookmarks(): List<Post> {
        val vaultPubkey = vaultProvider.vaultPubkey
        val encryptionKey = vaultProvider.getEncryptionKey()

        // Only fetch private bookmarks if vault is unlocked
        if (vaultPubkey == null || encryptionKey == null) {
            Timber.d("NostrDataSource: Vault not unlocked, skipping private bookmarks")
            return emptyList()
        }

        Timber.d("NostrDataSource: Fetching private bookmarks for vault pubkey: ${vaultPubkey.take(8)}...")

        val filter = NostrFilter.bookmarksForAuthor(vaultPubkey, limit = 500)
        val events = nostrClient.fetchAllEvents(filter)

        Timber.d("NostrDataSource: Received ${events.size} private events")

        return events.mapNotNull { event ->
            try {
                // Private bookmarks have encrypted JSON content
                if (event.content.isBlank()) {
                    Timber.d("NostrDataSource: Skipping event with empty content")
                    return@mapNotNull null
                }

                // Decrypt the content
                val decryptedJson = try {
                    VaultCrypto.decrypt(event.content, encryptionKey)
                } catch (e: Exception) {
                    Timber.w(e, "NostrDataSource: Failed to decrypt private bookmark: ${event.id.take(8)}")
                    return@mapNotNull null
                }

                // Parse the decrypted JSON
                val decryptedContent = try {
                    Json.decodeFromString<EncryptedBookmarkContent>(decryptedJson)
                } catch (e: Exception) {
                    Timber.w(e, "NostrDataSource: Failed to parse decrypted content: ${event.id.take(8)}")
                    return@mapNotNull null
                }

                // Skip "deleted" bookmarks (empty URL)
                if (decryptedContent.url.isBlank()) {
                    Timber.d("NostrDataSource: Skipping deleted private bookmark")
                    return@mapNotNull null
                }

                // Reconstruct full URL
                val url = if (decryptedContent.url.startsWith("http://") || decryptedContent.url.startsWith("https://")) {
                    decryptedContent.url
                } else {
                    "https://${decryptedContent.url}"
                }

                // Convert timestamp
                val dateAdded = decryptedContent.publishedAt?.let {
                    java.time.Instant.ofEpochSecond(it)
                        .atOffset(java.time.ZoneOffset.UTC)
                        .format(java.time.format.DateTimeFormatter.ISO_INSTANT)
                } ?: java.time.Instant.ofEpochSecond(event.createdAt)
                    .atOffset(java.time.ZoneOffset.UTC)
                    .format(java.time.format.DateTimeFormatter.ISO_INSTANT)

                Post(
                    url = url,
                    title = decryptedContent.title ?: url,
                    description = decryptedContent.description ?: "",
                    id = event.id,
                    dateAdded = dateAdded,
                    private = true,
                    readLater = false,
                    tags = decryptedContent.tags?.map { Tag(it) },
                    pendingSync = null,
                )
            } catch (e: Exception) {
                Timber.e(e, "NostrDataSource: Failed to process private event")
                null
            }
        }
    }

    override suspend fun add(post: Post): Result<Post> {
        return try {
            val isPrivate = post.private == true
            Timber.d("NostrDataSource: add() called, private=$isPrivate")

            // For private bookmarks, use vault signer
            if (isPrivate && vaultProvider.isUnlocked()) {
                return addPrivateBookmark(post)
            }

            // For public bookmarks, use user's signer
            val signerType = nostrSignerProvider.signerType
            Timber.d("NostrDataSource: Using signer type=$signerType for public bookmark")

            when (signerType) {
                SignerType.INTERNAL -> {
                    // Use internal signer for local signing
                    val signer = nostrSignerProvider.getInternalSigner()
                    if (signer == null) {
                        Timber.w("NostrDataSource: Failed to get internal signer, saving locally only")
                        return saveLocally(post)
                    }

                    // Build event tags following NIP-B0 bookmark format
                    val tags = nostrEventMapper.toEventTags(post)
                    val tagsArray = tags.map { it.toTypedArray() }.toTypedArray()

                    Timber.d("NostrDataSource: Creating bookmark event for URL: ${post.url}")

                    // Sign the event
                    val event: Event = signer.sign(
                        createdAt = System.currentTimeMillis() / 1000,
                        kind = NostrFilter.KIND_BOOKMARK,
                        tags = tagsArray,
                        content = post.description,
                    )

                    Timber.d("NostrDataSource: Signed event id=${event.id.take(8)}...")

                    // Publish to relays
                    val published = nostrClient.publishEvent(event)

                    if (published) {
                        Timber.d("NostrDataSource: Event published successfully")
                        // Save locally with the event ID
                        val resultPost = post.copy(id = event.id, dateAdded = dateFormatter.nowAsDataFormat())
                        saveLocally(resultPost)
                    } else {
                        Timber.w("NostrDataSource: Failed to publish to any relay, saving locally")
                        saveLocally(post)
                    }
                }

                SignerType.AMBER -> {
                    // TODO: Implement Amber signing via intent
                    // For now, save locally and mark as pending
                    Timber.d("NostrDataSource: Amber signing not yet implemented, saving locally")
                    saveLocally(post)
                }

                SignerType.BUNKER -> {
                    // TODO: Implement Bunker signing via NIP-46
                    // For now, save locally and mark as pending
                    Timber.d("NostrDataSource: Bunker signing not yet implemented, saving locally")
                    saveLocally(post)
                }

                SignerType.NONE -> {
                    Timber.w("NostrDataSource: No signer configured, saving locally only")
                    saveLocally(post)
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "NostrDataSource: Failed to add bookmark")
            Failure(e)
        }
    }

    /**
     * Adds a private (encrypted) bookmark using the vault identity.
     *
     * Private bookmarks are:
     * - Signed with the vault's signing key (different from user's main key)
     * - ALL content is encrypted as JSON with AES-256-GCM (URL, title, description, tags)
     * - Only a random d-tag is visible - no metadata leakage
     * - The vault pubkey is the author, so only the user can find/decrypt them
     */
    private suspend fun addPrivateBookmark(post: Post): Result<Post> {
        val signingKey = vaultProvider.getSigningKey()
        val encryptionKey = vaultProvider.getEncryptionKey()

        if (signingKey == null || encryptionKey == null) {
            Timber.w("NostrDataSource: Vault keys not available, saving locally only")
            return saveLocally(post)
        }

        // Create vault signer
        val keyPair = KeyPair(signingKey)
        val vaultSigner = NostrSignerInternal(keyPair)

        // Ensure URL has a protocol
        val fullUrl = if (post.url.startsWith("http://") || post.url.startsWith("https://")) {
            post.url
        } else {
            "https://${post.url}"
        }

        // Create content object with ALL bookmark data to encrypt
        val contentToEncrypt = EncryptedBookmarkContent(
            url = fullUrl,
            title = post.title.takeIf { it.isNotBlank() },
            description = post.description.takeIf { it.isNotBlank() },
            tags = post.tags?.map { it.name }?.takeIf { it.isNotEmpty() },
            publishedAt = nostrEventMapper.parseTimestamp(post.dateAdded).takeIf { it > 0 },
        )

        // Encrypt the entire JSON content
        val jsonContent = Json.encodeToString(contentToEncrypt)
        val encryptedContent = VaultCrypto.encrypt(jsonContent, encryptionKey)

        // Generate random d-tag to prevent URL correlation
        val randomIdentifier = UUID.randomUUID().toString()

        // Only include random d-tag - no metadata leakage
        val tagsArray = arrayOf(arrayOf("d", randomIdentifier))

        Timber.d("NostrDataSource: Creating private bookmark event (d-tag: ${randomIdentifier.take(8)}...)")

        // Sign with vault identity
        val event: Event = vaultSigner.sign(
            createdAt = System.currentTimeMillis() / 1000,
            kind = NostrFilter.KIND_BOOKMARK,
            tags = tagsArray,
            content = encryptedContent,
        )

        Timber.d("NostrDataSource: Signed private event id=${event.id.take(8)}... with vault pubkey")

        // Publish to relays
        val published = nostrClient.publishEvent(event)

        return if (published) {
            Timber.d("NostrDataSource: Private event published successfully")
            val resultPost = post.copy(id = event.id, dateAdded = dateFormatter.nowAsDataFormat())
            saveLocally(resultPost)
        } else {
            Timber.w("NostrDataSource: Failed to publish private bookmark to any relay, saving locally")
            saveLocally(post)
        }
    }

    private suspend fun saveLocally(post: Post): Result<Post> = resultFrom {
        val dto = PostDto(
            href = post.url,
            description = post.title,
            extended = post.description,
            hash = post.id.ifEmpty { post.url.hashCode().toString() },
            time = post.dateAdded.ifEmpty { dateFormatter.nowAsDataFormat() },
            shared = if (post.private == true) {
                AppConfig.PinboardApiLiterals.NO
            } else {
                AppConfig.PinboardApiLiterals.YES
            },
            toread = if (post.readLater == true) {
                AppConfig.PinboardApiLiterals.YES
            } else {
                AppConfig.PinboardApiLiterals.NO
            },
            tags = post.tags?.joinToString(AppConfig.PinboardApiLiterals.TAG_SEPARATOR) { it.name }.orEmpty(),
        )
        postsDao.savePosts(listOf(dto))
        postDtoMapper.map(dto)
    }

    override suspend fun delete(post: Post): Result<Unit> {
        // TODO: Implement with Amber signing integration
        // For now, delete locally only
        return resultFrom {
            postsDao.deletePost(url = post.url)
        }
    }

    override fun getAllPosts(
        sortType: SortType,
        searchTerm: String,
        tags: List<Tag>?,
        matchAll: Boolean,
        exactMatch: Boolean,
        untaggedOnly: Boolean,
        postVisibility: PostVisibility,
        readLaterOnly: Boolean,
        countLimit: Int,
        pageLimit: Int,
        pageOffset: Int,
        forceRefresh: Boolean,
    ): Flow<Result<PostListResult>> = flow {
        // If forceRefresh or first load, fetch from relays
        if (forceRefresh || pageOffset == 0) {
            val updateResult = update()
            if (updateResult is Failure) {
                Timber.w("NostrDataSource: Update failed, using cached data")
            }
        }

        // Query local cache
        val query = PostsDao.allPostsNoFtsQuery(
            term = searchTerm,
            tag1 = tags?.getOrNull(0)?.name.orEmpty(),
            tag2 = tags?.getOrNull(1)?.name.orEmpty(),
            tag3 = tags?.getOrNull(2)?.name.orEmpty(),
            matchAll = matchAll,
            exactMatch = exactMatch,
            untaggedOnly = untaggedOnly,
            postVisibility = postVisibility,
            readLaterOnly = readLaterOnly,
            sortType = sortType.index,
            offset = pageOffset,
            limit = pageLimit,
        )

        val countQuery = PostsDao.postCountNoFtsQuery(
            term = searchTerm,
            tag1 = tags?.getOrNull(0)?.name.orEmpty(),
            tag2 = tags?.getOrNull(1)?.name.orEmpty(),
            tag3 = tags?.getOrNull(2)?.name.orEmpty(),
            matchAll = matchAll,
            exactMatch = exactMatch,
            untaggedOnly = untaggedOnly,
            postVisibility = postVisibility,
            readLaterOnly = readLaterOnly,
            limit = countLimit,
        )

        val result = resultFrom {
            val totalCount = postsDao.getPostCount(countQuery)
            val posts = postsDao.getAllPosts(query).let(postDtoMapper::mapList)

            PostListResult(
                posts = posts,
                totalCount = totalCount,
                upToDate = true,
                canPaginate = posts.size == pageLimit,
            )
        }

        emit(result)
    }

    override suspend fun getQueryResultSize(
        searchTerm: String,
        tags: List<Tag>?,
        matchAll: Boolean,
        exactMatch: Boolean,
    ): Int = catching {
        val query = PostsDao.postCountNoFtsQuery(
            term = searchTerm,
            tag1 = tags?.getOrNull(0)?.name.orEmpty(),
            tag2 = tags?.getOrNull(1)?.name.orEmpty(),
            tag3 = tags?.getOrNull(2)?.name.orEmpty(),
            matchAll = matchAll,
            exactMatch = exactMatch,
            untaggedOnly = false,
            postVisibility = PostVisibility.None,
            readLaterOnly = false,
            limit = -1,
        )
        postsDao.getPostCount(query)
    }.getOrDefault(0)

    override suspend fun getPost(id: String, url: String): Result<Post> = resultFrom {
        postsDao.getPost(url)?.let(postDtoMapper::map) ?: throw InvalidRequestException()
    }

    override suspend fun searchExistingPostTag(
        tag: String,
        currentTags: List<Tag>,
    ): Result<List<String>> = resultFrom {
        val tagNames = currentTags.map(Tag::name)

        if (tag.isNotEmpty()) {
            val query = PostsDao.existingPostTagNoFtsQuery(tag)
            postsDao.searchExistingPostTag(query)
                .flatMap { it.split(" ") }
                .filter { it.startsWith(tag, ignoreCase = true) && it !in tagNames }
                .distinct()
                .sorted()
        } else {
            postsDao.getAllPostTags()
                .asSequence()
                .flatMap { it.split(" ") }
                .groupBy { it }
                .map { (tag, postList) -> Tag(tag, postList.size) }
                .sortedByDescending { it.posts }
                .asSequence()
                .map { it.name }
                .filter { it !in tagNames }
                .take(20)
                .toList()
        }
    }

    override suspend fun getPendingSyncPosts(): Result<List<Post>> {
        // TODO: Return posts that need to be published to Nostr
        return Success(emptyList())
    }

    override suspend fun clearCache(): Result<Unit> = resultFrom {
        postsDao.deleteAllPosts()
    }
}
