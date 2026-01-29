package com.fibelatti.pinboard.features.user.domain

import com.fibelatti.core.functional.Failure
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
import com.fibelatti.pinboard.features.nostr.signer.NostrSignerProvider
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
    private val nostrSignerProvider: NostrSignerProvider,
) : UseCaseWithParams<Login.Params, Result<Unit>> {

    override suspend operator fun invoke(params: Params): Result<Unit> {
        Timber.d("Logging in (params=$params)")
        val appMode = when (params) {
            is PinboardParams -> AppMode.PINBOARD
            is NostrNsecParams -> AppMode.NOSTR
            is NostrAmberParams -> AppMode.NOSTR
            is NostrBunkerParams -> AppMode.NOSTR
        }
        when (params) {
            is PinboardParams -> {
                userRepository.setAuthToken(appMode = appMode, authToken = params.authToken.trim())
                appModeProvider.setSelection(appMode = appMode)
            }

            is NostrNsecParams -> {
                // Convert nsec/npub to hex pubkey and store credentials
                val input = params.nsec.trim()
                val hexPubkey = convertToHexPubkey(input)
                Timber.d("NostrNsecParams: Storing pubkey: ${hexPubkey.take(16)}...")
                userRepository.nostrPubkey = hexPubkey

                // Store nsec for signing and setup internal signer
                if (input.startsWith("nsec1")) {
                    nostrSignerProvider.setupInternalSigner(input)
                    Timber.d("NostrNsecParams: Internal signer configured")
                }

                appModeProvider.setSelection(appMode = appMode)
                Timber.d("NostrNsecParams: Credentials stored")
            }

            is NostrAmberParams -> {
                // Amber already provided the pubkey via intent
                Timber.d("NostrAmberParams: Storing pubkey: ${params.pubkey.take(16)}...")
                userRepository.nostrPubkey = params.pubkey

                // Setup Amber signer
                nostrSignerProvider.setupAmberSigner(params.pubkey, params.signerPackage)
                Timber.d("NostrAmberParams: Amber signer configured (package=${params.signerPackage})")

                appModeProvider.setSelection(appMode = appMode)
                Timber.d("NostrAmberParams: Credentials stored")
            }

            is NostrBunkerParams -> {
                // Parse bunker URI to get pubkey
                val pubkey = parseBunkerPubkey(params.bunkerUri)
                if (pubkey != null) {
                    Timber.d("NostrBunkerParams: Storing pubkey: ${pubkey.take(16)}...")
                    userRepository.nostrPubkey = pubkey

                    // Setup Bunker signer
                    nostrSignerProvider.setupBunkerSigner(params.bunkerUri)
                    Timber.d("NostrBunkerParams: Bunker signer configured")

                    appModeProvider.setSelection(appMode = appMode)
                    Timber.d("NostrBunkerParams: Credentials stored")
                } else {
                    Timber.e("NostrBunkerParams: Failed to parse bunker URI")
                    return Failure(IllegalArgumentException("Invalid bunker URI"))
                }
            }
        }

        // For Nostr, skip the API validation and fetch bookmarks directly
        if (params is NostrNsecParams || params is NostrAmberParams || params is NostrBunkerParams) {
            Timber.d("Nostr: Fetching bookmarks from relays")
            postsRepository.update()
                .onSuccess {
                    Timber.d("Nostr: Fetch succeeded")
                }
                .onFailure {
                    // Even if fetching fails, still log in (can retry later)
                    Timber.w("Nostr: Initial fetch failed, logging in anyway")
                }
            Timber.d("Nostr: Running UserLoggedIn action")
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

    /** Login with nsec (private key) - stored locally for signing */
    data class NostrNsecParams(val nsec: String) : Params()

    /** Login with Amber (NIP-55) - uses external signer app */
    data class NostrAmberParams(val pubkey: String, val signerPackage: String) : Params()

    /** Login with Bunker (NIP-46) - uses remote signer */
    data class NostrBunkerParams(val bunkerUri: String) : Params()

    /**
     * Parse pubkey from bunker URI.
     * Format: bunker://<pubkey>?relay=wss://...&secret=...
     */
    private fun parseBunkerPubkey(bunkerUri: String): String? {
        return try {
            if (!bunkerUri.startsWith("bunker://")) return null
            val afterScheme = bunkerUri.removePrefix("bunker://")
            val pubkey = afterScheme.substringBefore("?")
            if (pubkey.length == 64) pubkey else null
        } catch (e: Exception) {
            Timber.e(e, "Failed to parse bunker URI")
            null
        }
    }

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
