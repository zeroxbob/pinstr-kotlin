package com.fibelatti.pinboard.features.user.domain

import com.fibelatti.core.functional.Result
import com.fibelatti.core.functional.Success
import com.fibelatti.core.functional.UseCaseWithParams
import com.fibelatti.core.functional.map
import com.fibelatti.core.functional.onFailure
import com.fibelatti.core.functional.onSuccess
import com.fibelatti.pinboard.core.AppMode
import com.fibelatti.pinboard.core.AppModeProvider
import com.fibelatti.pinboard.features.appstate.AppStateRepository
import com.fibelatti.pinboard.features.appstate.UserLoggedIn
import com.fibelatti.pinboard.features.appstate.UserLoginFailed
import com.fibelatti.pinboard.features.posts.domain.PostsRepository
import javax.inject.Inject
import timber.log.Timber

class Login @Inject constructor(
    private val userRepository: UserRepository,
    private val appStateRepository: AppStateRepository,
    private val postsRepository: PostsRepository,
    private val appModeProvider: AppModeProvider,
) : UseCaseWithParams<Login.Params, Result<Unit>> {

    override suspend operator fun invoke(params: Params): Result<Unit> {
        Timber.d("Logging in (params=$params)")
        val appMode = when (params) {
            is PinboardParams -> AppMode.PINBOARD
            is NostrParams -> AppMode.NOSTR
        }
        when (params) {
            is PinboardParams -> {
                userRepository.setAuthToken(appMode = appMode, authToken = params.authToken.trim())
                appModeProvider.setSelection(appMode = appMode)
            }

            is NostrParams -> {
                // For Nostr, store the pubkey (hex format)
                Timber.d("NostrParams: Storing pubkey")
                userRepository.nostrPubkey = params.pubkey.trim()
                appModeProvider.setSelection(appMode = appMode)
                Timber.d("NostrParams: Pubkey stored")
            }
        }

        // For Nostr, skip the API validation and fetch bookmarks directly
        if (params is NostrParams) {
            Timber.d("NostrParams: Fetching bookmarks from relays")
            postsRepository.update()
                .onSuccess {
                    Timber.d("NostrParams: Fetch succeeded")
                }
                .onFailure {
                    // Even if fetching fails, still log in (can retry later)
                    Timber.w("NostrParams: Initial fetch failed, logging in anyway")
                }
            Timber.d("NostrParams: Running UserLoggedIn action")
            appStateRepository.runAction(UserLoggedIn(appMode = appMode))
            return Success(Unit)
        }

        return postsRepository.update()
            .map { postsRepository.clearCache() }
            .onSuccess { appStateRepository.runAction(UserLoggedIn(appMode = appMode)) }
            .onFailure { appStateRepository.runAction(UserLoginFailed(appMode = appMode)) }
    }

    sealed class Params

    data class PinboardParams(val authToken: String) : Params()

    data class NostrParams(val pubkey: String) : Params()
}
