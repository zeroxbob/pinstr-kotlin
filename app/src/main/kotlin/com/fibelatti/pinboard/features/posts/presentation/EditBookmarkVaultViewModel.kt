package com.fibelatti.pinboard.features.posts.presentation

import androidx.lifecycle.ViewModel
import com.fibelatti.pinboard.features.nostr.vault.VaultProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * Simple ViewModel to provide VaultProvider access to EditBookmarkScreen.
 */
@HiltViewModel
class EditBookmarkVaultViewModel @Inject constructor(
    val vaultProvider: VaultProvider,
) : ViewModel()
