package com.fibelatti.pinboard.features.nostr.signer

import com.fibelatti.pinboard.core.persistence.SecureStorage
import com.fibelatti.pinboard.core.persistence.UserSharedPreferences
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip19Bech32.bech32.bechToBytes
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The type of signer being used for Nostr authentication.
 */
enum class SignerType {
    /** Using Amber or compatible NIP-55 signer app */
    AMBER,
    /** Using NIP-46 bunker/remote signer */
    BUNKER,
    /** Using locally stored nsec */
    INTERNAL,
    /** No signer configured */
    NONE,
}

/**
 * Provides the appropriate NostrSigner based on the user's authentication method.
 * Persists signer configuration to survive app restarts.
 */
@Singleton
class NostrSignerProvider @Inject constructor(
    private val secureStorage: SecureStorage,
    private val userSharedPreferences: UserSharedPreferences,
) {
    /**
     * The current signer type.
     */
    val signerType: SignerType
        get() = when (userSharedPreferences.nostrSignerType) {
            "AMBER" -> SignerType.AMBER
            "BUNKER" -> SignerType.BUNKER
            "INTERNAL" -> SignerType.INTERNAL
            else -> SignerType.NONE
        }

    /**
     * For Amber signer: the package name of the signer app.
     */
    val amberPackage: String?
        get() = userSharedPreferences.nostrAmberPackage

    /**
     * For Bunker signer: the connection URI.
     */
    val bunkerUri: String?
        get() = userSharedPreferences.nostrBunkerUri

    /**
     * Set up Amber signer with the package name.
     */
    fun setupAmberSigner(pubkey: String, packageName: String) {
        userSharedPreferences.nostrSignerType = "AMBER"
        userSharedPreferences.nostrAmberPackage = packageName
        userSharedPreferences.nostrBunkerUri = null
        secureStorage.nostrNsec = null
        Timber.d("NostrSignerProvider: Configured Amber signer (package=$packageName)")
    }

    /**
     * Set up Bunker signer with the connection URI.
     */
    fun setupBunkerSigner(bunkerConnectionUri: String) {
        userSharedPreferences.nostrSignerType = "BUNKER"
        userSharedPreferences.nostrBunkerUri = bunkerConnectionUri
        userSharedPreferences.nostrAmberPackage = null
        secureStorage.nostrNsec = null
        Timber.d("NostrSignerProvider: Configured Bunker signer")
    }

    /**
     * Set up internal signer with nsec.
     */
    fun setupInternalSigner(nsec: String) {
        userSharedPreferences.nostrSignerType = "INTERNAL"
        secureStorage.nostrNsec = nsec
        userSharedPreferences.nostrAmberPackage = null
        userSharedPreferences.nostrBunkerUri = null
        Timber.d("NostrSignerProvider: Configured internal signer")
    }

    /**
     * Get an internal signer for local signing.
     * Only works if signerType is INTERNAL and nsec is available.
     */
    fun getInternalSigner(): NostrSignerInternal? {
        if (signerType != SignerType.INTERNAL) {
            Timber.w("NostrSignerProvider: Cannot get internal signer, type is $signerType")
            return null
        }

        val nsec = secureStorage.nostrNsec
        if (nsec.isNullOrBlank()) {
            Timber.w("NostrSignerProvider: No nsec available")
            return null
        }

        return try {
            val privateKeyBytes = nsec.bechToBytes()
            val keyPair = KeyPair(privateKeyBytes)
            NostrSignerInternal(keyPair)
        } catch (e: Exception) {
            Timber.e(e, "NostrSignerProvider: Failed to create internal signer")
            null
        }
    }

    /**
     * Check if we can sign events locally (internal signer only).
     */
    fun canSignLocally(): Boolean = signerType == SignerType.INTERNAL && !secureStorage.nostrNsec.isNullOrBlank()

    /**
     * Check if we need external signing (Amber or Bunker).
     */
    fun needsExternalSigning(): Boolean = signerType == SignerType.AMBER || signerType == SignerType.BUNKER

    /**
     * Clear the signer configuration.
     */
    fun clear() {
        userSharedPreferences.nostrSignerType = null
        userSharedPreferences.nostrAmberPackage = null
        userSharedPreferences.nostrBunkerUri = null
        secureStorage.nostrNsec = null
        Timber.d("NostrSignerProvider: Cleared signer configuration")
    }
}
