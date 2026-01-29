package com.fibelatti.pinboard.features.nostr.vault

import com.fibelatti.core.android.platform.ResourceProvider
import com.fibelatti.pinboard.R
import com.fibelatti.pinboard.core.android.base.BaseViewModel
import com.fibelatti.pinboard.features.appstate.AppStateRepository
import com.fibelatti.pinboard.features.appstate.ResetVault
import com.fibelatti.pinboard.features.appstate.VaultReady
import com.fibelatti.pinboard.features.user.domain.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

@HiltViewModel
class VaultViewModel @Inject constructor(
    scope: CoroutineScope,
    private val appStateRepository: AppStateRepository,
    private val vaultProvider: VaultProvider,
    private val userRepository: UserRepository,
    private val resourceProvider: ResourceProvider,
) : BaseViewModel(scope, appStateRepository) {

    private val _screenState = MutableStateFlow(ScreenState())
    val screenState: StateFlow<ScreenState> = _screenState.asStateFlow()

    /**
     * Creates a new vault with the given passphrase.
     */
    fun createVault(passphrase: String, confirmPassphrase: String) {
        // Validate passphrase
        if (passphrase.length < VaultProvider.MIN_PASSPHRASE_LENGTH) {
            _screenState.update {
                it.copy(
                    error = resourceProvider.getString(
                        R.string.vault_passphrase_too_short,
                        VaultProvider.MIN_PASSPHRASE_LENGTH,
                    ),
                )
            }
            return
        }

        if (passphrase != confirmPassphrase) {
            _screenState.update {
                it.copy(error = resourceProvider.getString(R.string.vault_passphrase_mismatch))
            }
            return
        }

        val userPubkey = userRepository.nostrPubkey
        if (userPubkey.isNullOrBlank()) {
            _screenState.update {
                it.copy(error = resourceProvider.getString(R.string.vault_error_no_pubkey))
            }
            return
        }

        scope.launch {
            _screenState.update { it.copy(isLoading = true, error = null) }

            vaultProvider.createVault(passphrase, userPubkey)
                .onSuccess {
                    Timber.d("Vault created successfully")
                    _screenState.update { it.copy(isLoading = false) }
                    appStateRepository.runAction(VaultReady)
                }
                .onFailure { error ->
                    Timber.e(error, "Failed to create vault")
                    _screenState.update {
                        it.copy(
                            isLoading = false,
                            error = resourceProvider.getString(R.string.vault_error_create_failed),
                        )
                    }
                }
        }
    }

    /**
     * Unlocks an existing vault with the given passphrase.
     */
    fun unlockVault(passphrase: String) {
        if (passphrase.isBlank()) {
            _screenState.update {
                it.copy(error = resourceProvider.getString(R.string.vault_passphrase_empty))
            }
            return
        }

        val userPubkey = userRepository.nostrPubkey
        if (userPubkey.isNullOrBlank()) {
            _screenState.update {
                it.copy(error = resourceProvider.getString(R.string.vault_error_no_pubkey))
            }
            return
        }

        scope.launch {
            _screenState.update { it.copy(isLoading = true, error = null) }

            vaultProvider.unlockVault(passphrase, userPubkey)
                .onSuccess {
                    Timber.d("Vault unlocked successfully")
                    _screenState.update { it.copy(isLoading = false) }
                    appStateRepository.runAction(VaultReady)
                }
                .onFailure { error ->
                    Timber.e(error, "Failed to unlock vault")
                    val errorMessage = when (error) {
                        is InvalidPassphraseException -> resourceProvider.getString(R.string.vault_error_wrong_passphrase)
                        else -> resourceProvider.getString(R.string.vault_error_unlock_failed)
                    }
                    _screenState.update {
                        it.copy(isLoading = false, error = errorMessage)
                    }
                }
        }
    }

    /**
     * Resets the vault (user forgot passphrase).
     * This will delete all local private bookmarks.
     */
    fun resetVault() {
        scope.launch {
            appStateRepository.runAction(ResetVault)
        }
    }

    /**
     * Clears the current error message.
     */
    fun clearError() {
        _screenState.update { it.copy(error = null) }
    }

    /**
     * Calculates passphrase strength for UI feedback.
     */
    fun calculatePassphraseStrength(passphrase: String): PassphraseStrength {
        return when {
            passphrase.length < 8 -> PassphraseStrength.TOO_SHORT
            passphrase.length < VaultProvider.MIN_PASSPHRASE_LENGTH -> PassphraseStrength.WEAK
            passphrase.length < 16 -> PassphraseStrength.FAIR
            passphrase.length < 20 -> PassphraseStrength.GOOD
            else -> PassphraseStrength.STRONG
        }
    }

    data class ScreenState(
        val isLoading: Boolean = false,
        val error: String? = null,
    )
}

enum class PassphraseStrength {
    TOO_SHORT,
    WEAK,
    FAIR,
    GOOD,
    STRONG,
}
