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
            is NostrParams -> AppMode.NO_API
        }
        when (params) {
            is PinboardParams -> {
                userRepository.setAuthToken(appMode = appMode, authToken = params.authToken.trim())
                appModeProvider.setSelection(appMode = appMode)
            }

            is NostrParams -> {
                // For Nostr, just store the nsec and skip API validation
                Timber.d("NostrParams: Storing auth token")
                userRepository.setAuthToken(appMode = appMode, authToken = params.authToken.trim())
                appModeProvider.setSelection(appMode = appMode)
                Timber.d("NostrParams: Auth token stored, skipping API validation")
            }
        }

        // For Nostr, skip the API call and return success immediately
        if (params is NostrParams) {
            Timber.d("NostrParams: Returning success without API call")
            return Success(Unit)
                .onSuccess {
                    Timber.d("NostrParams: Running UserLoggedIn action")
                    appStateRepository.runAction(UserLoggedIn(appMode = appMode))
                }
        }

        return postsRepository.update()
            .map { postsRepository.clearCache() }
            .onSuccess { appStateRepository.runAction(UserLoggedIn(appMode = appMode)) }
            .onFailure { appStateRepository.runAction(UserLoginFailed(appMode = appMode)) }
    }

    sealed class Params {

        abstract val authToken: String
    }

    data class PinboardParams(override val authToken: String) : Params()

    data class NostrParams(override val authToken: String) : Params()
}
