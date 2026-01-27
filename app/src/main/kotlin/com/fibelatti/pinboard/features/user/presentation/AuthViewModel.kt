package com.fibelatti.pinboard.features.user.presentation

import com.fibelatti.core.android.platform.ResourceProvider
import com.fibelatti.core.functional.onFailure
import com.fibelatti.core.functional.onSuccess
import com.fibelatti.pinboard.R
import com.fibelatti.pinboard.core.AppConfig
import com.fibelatti.pinboard.core.AppMode
import com.fibelatti.pinboard.core.android.base.BaseViewModel
import com.fibelatti.pinboard.core.extension.isServerException
import com.fibelatti.pinboard.features.appstate.AppStateRepository
import com.fibelatti.pinboard.features.appstate.LoginContent
import com.fibelatti.pinboard.features.user.domain.Login
import dagger.hilt.android.lifecycle.HiltViewModel
import io.ktor.client.plugins.ResponseException
import java.net.ConnectException
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import timber.log.Timber
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class AuthViewModel @Inject constructor(
    scope: CoroutineScope,
    appStateRepository: AppStateRepository,
    private val loginUseCase: Login,
    private val resourceProvider: ResourceProvider,
) : BaseViewModel(scope, appStateRepository) {

    private val _screenState = MutableStateFlow(ScreenState())
    val screenState: StateFlow<ScreenState> = _screenState.asStateFlow()

    init {
        appState.map { it.content }
            .distinctUntilChangedBy { it::class }
            .filterIsInstance<LoginContent>()
            .onEach { loginContent ->
                _screenState.update {
                    ScreenState()
                }
            }
            .launchIn(scope)
    }

    fun login(apiToken: String, instanceUrl: String) {
        if (apiToken.isBlank()) {
            _screenState.update { current ->
                current.copy(
                    isLoading = false,
                    apiTokenError = resourceProvider.getString(R.string.auth_token_empty),
                )
            }
            return
        }

        scope.launch {
            Timber.d("AuthViewModel: Starting login with nsec")
            _screenState.update { current ->
                current.copy(
                    isLoading = true,
                    apiTokenError = null,
                )
            }

            val params = Login.NostrParams(authToken = apiToken)

            Timber.d("AuthViewModel: Calling loginUseCase")
            loginUseCase(params)
                .onSuccess {
                    Timber.d("AuthViewModel: Login succeeded, clearing loading state")
                    _screenState.update { current ->
                        current.copy(isLoading = false)
                    }
                }
                .onFailure { error ->
                    Timber.e(error, "AuthViewModel: Login failed")
                    when {
                        error is ResponseException && error.response.status.value in AppConfig.LOGIN_FAILED_CODES -> {
                            _screenState.update { currentState ->
                                currentState.copy(
                                    isLoading = false,
                                    apiTokenError = resourceProvider.getString(R.string.auth_token_error),
                                )
                            }
                        }

                        error.isServerException() -> {
                            _screenState.update { currentState ->
                                currentState.copy(
                                    isLoading = false,
                                    apiTokenError = resourceProvider.getString(R.string.server_error),
                                )
                            }
                        }

                        else -> {
                            _screenState.update { current -> current.copy(isLoading = false) }
                            handleError(error)
                        }
                    }
                }
        }
    }

    data class ScreenState(
        val allowSwitching: Boolean = true,
        val isLoading: Boolean = false,
        val apiTokenError: String? = null,
    )
}
