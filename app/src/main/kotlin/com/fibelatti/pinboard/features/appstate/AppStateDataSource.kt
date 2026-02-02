package com.fibelatti.pinboard.features.appstate

import com.fibelatti.pinboard.core.AppMode
import com.fibelatti.pinboard.core.AppModeProvider
import com.fibelatti.pinboard.core.android.ConnectivityInfoProvider
import com.fibelatti.pinboard.core.di.AppDispatchers
import com.fibelatti.pinboard.core.di.Scope
import com.fibelatti.pinboard.core.network.UnauthorizedPluginProvider
import com.fibelatti.pinboard.features.nostr.vault.VaultProvider
import com.fibelatti.pinboard.features.nostr.vault.VaultState
import com.fibelatti.pinboard.features.posts.data.PostsDao
import com.fibelatti.pinboard.features.user.domain.GetPreferredSortType
import com.fibelatti.pinboard.features.user.domain.UserRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import timber.log.Timber

@Singleton
class AppStateDataSource @Inject constructor(
    @Scope(AppDispatchers.DEFAULT) scope: CoroutineScope,
    @Scope(AppDispatchers.DEFAULT) dispatcher: CoroutineDispatcher,
    sharingStarted: SharingStarted,
    private val actionHandlers: Map<Class<out Action>, @JvmSuppressWildcards ActionHandler<*>>,
    private val userRepository: UserRepository,
    private val connectivityInfoProvider: ConnectivityInfoProvider,
    private val appModeProvider: AppModeProvider,
    private val unauthorizedPluginProvider: UnauthorizedPluginProvider,
    private val getPreferredSortType: GetPreferredSortType,
    private val postsDao: PostsDao,
    private val vaultProvider: VaultProvider,
) : AppStateRepository {

    private val reducer: MutableSharedFlow<suspend (AppState) -> AppState> = MutableSharedFlow()

    override val appState: StateFlow<AppState> = reducer
        .scan(getInitialAppState()) { appState, reducer -> reducer(appState) }
        .combine(appModeProvider.appMode) { appState, appMode -> appState.copy(appMode = appMode) }
        .flowOn(dispatcher)
        .stateIn(scope = scope, started = sharingStarted, initialValue = getInitialAppState())

    init {
        unauthorizedPluginProvider.unauthorized
            .onEach { appMode -> runAction(UserUnauthorized(appMode = appMode)) }
            .launchIn(scope)
    }

    override suspend fun runAction(action: Action) {
        withContext(NonCancellable) {
            reduce(action)
        }
    }

    private suspend fun reduce(action: Action) {
        reducer.emit { appState: AppState ->
            Timber.d("Reducing (action=${action.prettyPrint()}, appState=${appState.prettyPrint()})")

            val newContent: Content = when (action) {
                is AppAction -> {
                    when (action) {
                        is MultiPanelAvailabilityChanged -> appState.content
                        is Reset -> getInitialContent()
                    }
                }

                is AuthAction -> {
                    when (action) {
                        is UserLoggedIn -> {
                            appModeProvider.setSelection(action.appMode)
                            unauthorizedPluginProvider.enable(appMode = action.appMode)

                            // For Nostr, check vault state before proceeding
                            if (action.appMode == AppMode.NOSTR) {
                                when (vaultProvider.vaultState.value) {
                                    VaultState.NO_VAULT -> {
                                        Timber.d("Nostr login: No vault, navigating to setup")
                                        VaultSetupContent()
                                    }
                                    VaultState.LOCKED -> {
                                        // Try auto-unlock with stored passphrase
                                        val storedPassphrase = vaultProvider.getStoredPassphrase()
                                        val userPubkey = userRepository.nostrPubkey
                                        if (storedPassphrase != null && userPubkey != null) {
                                            Timber.d("Nostr login: Attempting auto-unlock")
                                            val result = vaultProvider.unlockVault(storedPassphrase, userPubkey)
                                            if (result.isSuccess) {
                                                Timber.d("Nostr login: Auto-unlock successful, proceeding to posts")
                                                getInitialPostListContent()
                                            } else {
                                                Timber.d("Nostr login: Auto-unlock failed, navigating to unlock")
                                                VaultUnlockContent()
                                            }
                                        } else {
                                            Timber.d("Nostr login: Vault locked, navigating to unlock")
                                            VaultUnlockContent()
                                        }
                                    }
                                    VaultState.UNLOCKED -> {
                                        Timber.d("Nostr login: Vault unlocked, proceeding to posts")
                                        getInitialPostListContent()
                                    }
                                }
                            } else {
                                getInitialPostListContent()
                            }
                        }

                        is UserLoginFailed, is UserLoggedOut, is UserUnauthorized -> {
                            appModeProvider.setSelection(appMode = null)
                            unauthorizedPluginProvider.disable(appMode = action.appMode)
                            userRepository.clearAuthToken(appMode = action.appMode)

                            // Clear local posts cache and vault when logging out
                            if (action is UserLoggedOut) {
                                postsDao.deleteAllPosts()
                                vaultProvider.clearVault()
                                Timber.d("Cleared posts cache and vault on logout")
                            }

                            when {
                                action is UserLoginFailed -> appState.content
                                userRepository.userCredentials.first().hasAuthToken() -> getInitialPostListContent()
                                else -> LoginContent()
                            }
                        }
                    }
                }

                is VaultAction -> {
                    when (action) {
                        is ViewVaultSetup -> VaultSetupContent(previousContent = appState.content)
                        is ViewVaultUnlock -> VaultUnlockContent(previousContent = appState.content)
                        is VaultReady -> {
                            Timber.d("Vault ready, navigating to posts")
                            getInitialPostListContent()
                        }
                        is ResetVault -> {
                            vaultProvider.resetVault()
                            Timber.d("Vault reset, returning to setup")
                            VaultSetupContent()
                        }
                    }
                }

                else -> {
                    @Suppress("UNCHECKED_CAST")
                    val handler = actionHandlers[action.getActionType()] as? ActionHandler<Action>
                    handler?.runAction(action, appState.content) ?: appState.content
                }
            }

            appState.copy(
                content = newContent,
                multiPanelAvailable = if (action is MultiPanelAvailabilityChanged) {
                    action.available
                } else {
                    appState.multiPanelAvailable
                },
                useSplitNav = userRepository.useSplitNav,
            )
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun Action.getActionType(): Class<out Action> {
        val thisType: Class<out Action> = this::class.java
        if (thisType == Action::class.java) return thisType

        var supertype: Class<out Action> = thisType.superclass as Class<out Action>
        while (supertype.superclass != Action::class.java) {
            supertype = supertype.superclass as Class<out Action>
        }

        return supertype
    }

    private fun getInitialAppState(): AppState = AppState(
        appMode = appModeProvider.appMode.value,
        content = getInitialContent(),
        multiPanelAvailable = false,
        useSplitNav = userRepository.useSplitNav,
    )

    private fun getInitialContent(): Content {
        return if (userRepository.userCredentials.value.hasAuthToken()) {
            // For Nostr, check vault state
            if (appModeProvider.appMode.value == AppMode.NOSTR) {
                when (vaultProvider.vaultState.value) {
                    VaultState.NO_VAULT -> {
                        Timber.d("Initial content: No vault, showing setup")
                        VaultSetupContent()
                    }
                    VaultState.LOCKED -> {
                        // Try auto-unlock with stored passphrase
                        val storedPassphrase = vaultProvider.getStoredPassphrase()
                        val userPubkey = userRepository.nostrPubkey
                        if (storedPassphrase != null && userPubkey != null) {
                            Timber.d("Initial content: Attempting auto-unlock")
                            val result = runBlocking { vaultProvider.unlockVault(storedPassphrase, userPubkey) }
                            if (result.isSuccess) {
                                Timber.d("Initial content: Auto-unlock successful, showing posts")
                                getInitialPostListContent()
                            } else {
                                Timber.d("Initial content: Auto-unlock failed, showing unlock screen")
                                VaultUnlockContent()
                            }
                        } else {
                            Timber.d("Initial content: Vault locked, showing unlock")
                            VaultUnlockContent()
                        }
                    }
                    VaultState.UNLOCKED -> {
                        Timber.d("Initial content: Vault unlocked, showing posts")
                        getInitialPostListContent()
                    }
                }
            } else {
                getInitialPostListContent()
            }
        } else {
            LoginContent()
        }
    }

    private fun getInitialPostListContent(): Content = PostListContent(
        category = All,
        posts = null,
        showDescription = userRepository.showDescriptionInLists,
        sortType = getPreferredSortType(),
        searchParameters = SearchParameters(),
        shouldLoad = ShouldLoadFirstPage,
        isConnected = connectivityInfoProvider.isConnected(),
    )
}
