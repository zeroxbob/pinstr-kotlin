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
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip19Bech32.bech32.bechToBytes
import com.vitorpamplona.quartz.nip19Bech32.entities.NSec
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
                // Convert nsec/npub to hex pubkey and store credentials
                val input = params.pubkey.trim()
                val hexPubkey = convertToHexPubkey(input)
                Timber.d("NostrParams: Storing pubkey: ${hexPubkey.take(16)}...")
                userRepository.nostrPubkey = hexPubkey

                // Store nsec for signing (only if input was nsec)
                if (input.startsWith("nsec1")) {
                    userRepository.nostrNsec = input
                    Timber.d("NostrParams: Nsec stored for signing")
                }

                appModeProvider.setSelection(appMode = appMode)
                Timber.d("NostrParams: Credentials stored")
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

    /**
     * Converts nsec (private key) or npub (public key) in bech32 format to hex pubkey.
     * If already hex, returns as-is.
     */
    private fun convertToHexPubkey(input: String): String {
        return try {
            when {
                input.startsWith("nsec1") -> {
                    // Decode nsec to private key bytes, then derive public key
                    val privateKeyBytes = input.bechToBytes()
                    val nsec = NSec.parse(privateKeyBytes)
                    val pubkey = nsec?.toPubKeyHex()
                    if (pubkey != null) {
                        Timber.d("convertToHexPubkey: Derived pubkey ${pubkey.take(16)}... from nsec")
                        pubkey
                    } else {
                        Timber.e("convertToHexPubkey: Failed to parse NSec")
                        input
                    }
                }
                input.startsWith("npub1") -> {
                    // Decode npub directly to pubkey hex
                    val pubkeyBytes = input.bechToBytes()
                    val pubkey = pubkeyBytes.toHexKey()
                    Timber.d("convertToHexPubkey: Decoded npub to ${pubkey.take(16)}...")
                    pubkey
                }
                else -> {
                    // Assume it's already hex
                    Timber.d("convertToHexPubkey: Input appears to be hex already")
                    input
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "convertToHexPubkey: Failed to convert key")
            input
        }
    }
}
