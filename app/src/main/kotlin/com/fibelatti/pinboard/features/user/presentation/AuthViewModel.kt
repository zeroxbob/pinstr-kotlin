package com.fibelatti.pinboard.features.user.presentation

import android.content.Context
import android.content.Intent
import com.fibelatti.core.android.platform.ResourceProvider
import com.fibelatti.core.functional.onFailure
import com.fibelatti.core.functional.onSuccess
import com.fibelatti.pinboard.R
import com.fibelatti.pinboard.core.AppConfig
import com.fibelatti.pinboard.core.android.base.BaseViewModel
import com.fibelatti.pinboard.core.extension.isServerException
import com.fibelatti.pinboard.features.appstate.AppStateRepository
import com.fibelatti.pinboard.features.appstate.LoginContent
import com.fibelatti.pinboard.features.nostr.signer.AmberSigner
import com.fibelatti.pinboard.features.user.domain.Login
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.ktor.client.plugins.ResponseException
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
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
import timber.log.Timber

enum class NostrLoginMethod {
    AMBER,
    BUNKER,
    NSEC,
}

@HiltViewModel
class AuthViewModel @Inject constructor(
    scope: CoroutineScope,
    appStateRepository: AppStateRepository,
    @ApplicationContext private val context: Context,
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
                    ScreenState(
                        isAmberInstalled = AmberSigner.isExternalSignerInstalled(context),
                    )
                }
            }
            .launchIn(scope)
    }

    fun selectLoginMethod(method: NostrLoginMethod) {
        _screenState.update { current ->
            current.copy(selectedMethod = method)
        }
    }

    fun clearSelectedMethod() {
        _screenState.update { current ->
            current.copy(selectedMethod = null, apiTokenError = null)
        }
    }

    /**
     * Create intent to launch Amber for getting public key.
     */
    fun createAmberIntent(): Intent = AmberSigner.createGetPublicKeyIntent()

    /**
     * Handle result from Amber get_public_key intent.
     */
    fun handleAmberResult(resultIntent: Intent?) {
        val result = AmberSigner.parseGetPublicKeyResult(resultIntent)
        if (result != null) {
            val (pubkey, signerPackage) = result
            loginWithAmber(pubkey, signerPackage)
        } else {
            _screenState.update { current ->
                current.copy(
                    isLoading = false,
                    apiTokenError = resourceProvider.getString(R.string.auth_token_error),
                )
            }
        }
    }

    private fun loginWithAmber(pubkey: String, signerPackage: String) {
        scope.launch {
            Timber.d("AuthViewModel: Starting login with Amber")
            _screenState.update { current ->
                current.copy(isLoading = true, apiTokenError = null)
            }

            val params = Login.NostrAmberParams(pubkey = pubkey, signerPackage = signerPackage)

            loginUseCase(params)
                .onSuccess {
                    Timber.d("AuthViewModel: Amber login succeeded")
                    _screenState.update { current ->
                        current.copy(isLoading = false)
                    }
                }
                .onFailure { error ->
                    Timber.e(error, "AuthViewModel: Amber login failed")
                    _screenState.update { current ->
                        current.copy(isLoading = false)
                    }
                    handleError(error)
                }
        }
    }

    fun loginWithBunker(bunkerUri: String) {
        if (bunkerUri.isBlank()) {
            _screenState.update { current ->
                current.copy(
                    isLoading = false,
                    apiTokenError = resourceProvider.getString(R.string.auth_bunker_empty),
                )
            }
            return
        }

        if (!bunkerUri.startsWith("bunker://")) {
            _screenState.update { current ->
                current.copy(
                    isLoading = false,
                    apiTokenError = resourceProvider.getString(R.string.auth_bunker_invalid),
                )
            }
            return
        }

        scope.launch {
            Timber.d("AuthViewModel: Starting login with Bunker")
            _screenState.update { current ->
                current.copy(isLoading = true, apiTokenError = null)
            }

            val params = Login.NostrBunkerParams(bunkerUri = bunkerUri.trim())

            loginUseCase(params)
                .onSuccess {
                    Timber.d("AuthViewModel: Bunker login succeeded")
                    _screenState.update { current ->
                        current.copy(isLoading = false)
                    }
                }
                .onFailure { error ->
                    Timber.e(error, "AuthViewModel: Bunker login failed")
                    _screenState.update { current ->
                        current.copy(
                            isLoading = false,
                            apiTokenError = resourceProvider.getString(R.string.auth_bunker_invalid),
                        )
                    }
                }
        }
    }

    fun loginWithNsec(nsec: String) {
        if (nsec.isBlank()) {
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
                current.copy(isLoading = true, apiTokenError = null)
            }

            val params = Login.NostrNsecParams(nsec = nsec.trim())

            loginUseCase(params)
                .onSuccess {
                    Timber.d("AuthViewModel: Nsec login succeeded")
                    _screenState.update { current ->
                        current.copy(isLoading = false)
                    }
                }
                .onFailure { error ->
                    Timber.e(error, "AuthViewModel: Nsec login failed")
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
        val isAmberInstalled: Boolean = false,
        val selectedMethod: NostrLoginMethod? = null,
        val isLoading: Boolean = false,
        val apiTokenError: String? = null,
    )
}
