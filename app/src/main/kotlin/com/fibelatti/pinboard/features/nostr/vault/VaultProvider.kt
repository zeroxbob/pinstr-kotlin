package com.fibelatti.pinboard.features.nostr.vault

import com.fibelatti.pinboard.core.persistence.UserSharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Vault state for tracking the vault lifecycle.
 */
enum class VaultState {
    /** User hasn't created a vault yet */
    NO_VAULT,
    /** Vault exists but not unlocked this session */
    LOCKED,
    /** Vault is ready to use */
    UNLOCKED,
}

/**
 * Manages the vault lifecycle and provides access to vault keys.
 *
 * Keys are kept in memory only and cleared on lock/logout.
 * Vault metadata (createdAt, pubkey) is persisted to SharedPreferences.
 */
@Singleton
class VaultProvider @Inject constructor(
    private val userSharedPreferences: UserSharedPreferences,
) {
    /**
     * In-memory vault keys (cleared when locked/logout).
     * Never persisted to storage.
     */
    private var vaultKeys: VaultKeys? = null

    private val _vaultState = MutableStateFlow(computeInitialState())
    val vaultState: StateFlow<VaultState> = _vaultState.asStateFlow()

    /**
     * The vault's public key (hex format) for querying private bookmarks.
     */
    val vaultPubkey: String?
        get() = userSharedPreferences.vaultPubkey

    /**
     * Returns true if a vault has been created.
     */
    fun hasVault(): Boolean = userSharedPreferences.vaultCreatedAt != null

    /**
     * Returns true if the vault is currently unlocked.
     */
    fun isUnlocked(): Boolean = _vaultState.value == VaultState.UNLOCKED

    /**
     * Creates a new vault from a passphrase.
     *
     * @param passphrase User's passphrase (minimum 12 characters)
     * @param userPubkey User's Nostr public key (hex format)
     * @return Success or failure
     */
    suspend fun createVault(passphrase: String, userPubkey: String): Result<Unit> = withContext(Dispatchers.Default) {
        try {
            Timber.d("Creating vault for pubkey: ${userPubkey.take(8)}...")

            require(passphrase.length >= MIN_PASSPHRASE_LENGTH) {
                "Passphrase must be at least $MIN_PASSPHRASE_LENGTH characters"
            }

            // Derive salt from pubkey
            val salt = VaultCrypto.deriveSalt(userPubkey)

            // Derive vault keys
            val keys = VaultCrypto.deriveVaultKeys(passphrase, salt)

            // Get vault pubkey
            val derivedVaultPubkey = VaultCrypto.getVaultPubkey(keys.signingKey)

            // Store vault metadata
            userSharedPreferences.vaultCreatedAt = System.currentTimeMillis()
            userSharedPreferences.vaultPubkey = derivedVaultPubkey

            // Keep keys in memory
            vaultKeys = keys
            _vaultState.value = VaultState.UNLOCKED

            Timber.d("Vault created successfully, vault pubkey: ${derivedVaultPubkey.take(8)}...")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to create vault")
            Result.failure(e)
        }
    }

    /**
     * Unlocks an existing vault with the passphrase.
     *
     * @param passphrase User's passphrase
     * @param userPubkey User's Nostr public key (hex format)
     * @return Success or failure
     */
    suspend fun unlockVault(passphrase: String, userPubkey: String): Result<Unit> = withContext(Dispatchers.Default) {
        try {
            Timber.d("Unlocking vault for pubkey: ${userPubkey.take(8)}...")

            require(hasVault()) { "No vault exists to unlock" }

            // Derive salt from pubkey
            val salt = VaultCrypto.deriveSalt(userPubkey)

            // Derive vault keys
            val keys = VaultCrypto.deriveVaultKeys(passphrase, salt)

            // Verify the derived pubkey matches stored pubkey
            val derivedVaultPubkey = VaultCrypto.getVaultPubkey(keys.signingKey)
            val storedVaultPubkey = userSharedPreferences.vaultPubkey

            if (derivedVaultPubkey != storedVaultPubkey) {
                Timber.w("Vault unlock failed: derived pubkey doesn't match stored pubkey")
                return@withContext Result.failure(InvalidPassphraseException())
            }

            // Keep keys in memory
            vaultKeys = keys
            _vaultState.value = VaultState.UNLOCKED

            Timber.d("Vault unlocked successfully")
            Result.success(Unit)
        } catch (e: InvalidPassphraseException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Failed to unlock vault")
            Result.failure(e)
        }
    }

    /**
     * Locks the vault (clears keys from memory, keeps metadata).
     */
    fun lockVault() {
        Timber.d("Locking vault")
        vaultKeys?.let { VaultCrypto.clearKeys(it) }
        vaultKeys = null
        _vaultState.value = if (hasVault()) VaultState.LOCKED else VaultState.NO_VAULT
    }

    /**
     * Gets the encryption key for AES-256-GCM (only when unlocked).
     */
    fun getEncryptionKey(): ByteArray? = vaultKeys?.encryptionKey

    /**
     * Gets the signing key for Nostr events (only when unlocked).
     */
    fun getSigningKey(): ByteArray? = vaultKeys?.signingKey

    /**
     * Clears all vault data (keys + metadata).
     * Call this on logout.
     */
    fun clearVault() {
        Timber.d("Clearing vault")
        vaultKeys?.let { VaultCrypto.clearKeys(it) }
        vaultKeys = null
        userSharedPreferences.vaultCreatedAt = null
        userSharedPreferences.vaultPubkey = null
        _vaultState.value = VaultState.NO_VAULT
    }

    /**
     * Resets the vault (user forgot passphrase).
     * Same as clearVault - destroys local private bookmarks.
     */
    fun resetVault() = clearVault()

    private fun computeInitialState(): VaultState {
        return if (hasVault()) VaultState.LOCKED else VaultState.NO_VAULT
    }

    companion object {
        const val MIN_PASSPHRASE_LENGTH = 12
    }
}

/**
 * Exception thrown when passphrase is incorrect.
 */
class InvalidPassphraseException : Exception("Invalid passphrase")
