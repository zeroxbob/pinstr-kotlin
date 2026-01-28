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
import com.fibelatti.pinboard.features.posts.data.PostsDao
import com.fibelatti.pinboard.features.posts.data.model.PostDto
import com.fibelatti.pinboard.features.posts.data.model.PostDtoMapper
import com.fibelatti.pinboard.features.posts.domain.PostVisibility
import com.fibelatti.pinboard.features.posts.domain.PostsRepository
import com.fibelatti.pinboard.features.posts.domain.model.Post
import com.fibelatti.pinboard.features.posts.domain.model.PostListResult
import com.fibelatti.pinboard.features.tags.domain.model.Tag
import com.fibelatti.pinboard.features.user.domain.UserRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import timber.log.Timber
import javax.inject.Inject

/**
 * PostsRepository implementation that fetches bookmarks from Nostr relays.
 * Currently read-only - publishing requires Amber integration for signing.
 */
class PostsDataSourceNostrApi @Inject constructor(
    private val nostrClient: NostrClient,
    private val nostrEventMapper: NostrEventMapper,
    private val postsDao: PostsDao,
    private val postDtoMapper: PostDtoMapper,
    private val dateFormatter: DateFormatter,
    private val userRepository: UserRepository,
) : PostsRepository {

    override suspend fun update(): Result<String> {
        return try {
            val pubkey = userRepository.nostrPubkey
            if (pubkey.isBlank()) {
                return Failure(InvalidRequestException())
            }

            Timber.d("NostrDataSource: Fetching bookmarks for pubkey: $pubkey")

            val filter = NostrFilter.bookmarksForAuthor(pubkey, limit = 500)
            val events = nostrClient.fetchAllEvents(filter)

            Timber.d("NostrDataSource: Received ${events.size} events")

            val posts = events.mapNotNull { event ->
                nostrEventMapper.toPost(event)
            }

            if (posts.isNotEmpty()) {
                // Convert to DTOs and save to local cache
                val dtos = posts.map { post ->
                    PostDto(
                        href = post.url,
                        description = post.title,
                        extended = post.description,
                        hash = post.id,
                        time = post.dateAdded,
                        shared = AppConfig.PinboardApiLiterals.YES, // Nostr is public
                        toread = AppConfig.PinboardApiLiterals.NO,
                        tags = post.tags?.joinToString(AppConfig.PinboardApiLiterals.TAG_SEPARATOR) { it.name }.orEmpty(),
                    )
                }

                postsDao.deleteAllPosts()
                postsDao.savePosts(dtos)

                Timber.d("NostrDataSource: Cached ${dtos.size} posts locally")
            }

            Success(dateFormatter.nowAsDataFormat())
        } catch (e: Exception) {
            Timber.e(e, "NostrDataSource: Failed to fetch bookmarks")
            Failure(e)
        }
    }

    override suspend fun add(post: Post): Result<Post> {
        // TODO: Implement with Amber signing integration
        // For now, save locally only (will sync when publishing is implemented)
        return resultFrom {
            val dto = PostDto(
                href = post.url,
                description = post.title,
                extended = post.description,
                hash = post.id.ifEmpty { post.url.hashCode().toString() },
                time = post.dateAdded.ifEmpty { dateFormatter.nowAsDataFormat() },
                shared = AppConfig.PinboardApiLiterals.YES,
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
