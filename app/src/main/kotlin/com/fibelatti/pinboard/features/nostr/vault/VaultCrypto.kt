package com.fibelatti.pinboard.features.nostr.vault

import com.lambdapioneer.argon2kt.Argon2Kt
import com.lambdapioneer.argon2kt.Argon2Mode
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Cryptographic utilities for the private vault.
 *
 * Uses Argon2id for key derivation and AES-256-GCM for encryption.
 * These are symmetric encryption methods that remain secure against
 * quantum computers (unlike elliptic curve cryptography).
 *
 * Security model:
 * - Passphrase + deterministic salt (derived from pubkey) -> Argon2id -> 64 bytes
 * - First 32 bytes: Nostr signing key (for vault identity)
 * - Second 32 bytes: AES-256 encryption key (for content)
 * - Salt is derived from user's pubkey, enabling recovery on any device
 * - Passphrase is never stored
 */
object VaultCrypto {

    /**
     * Argon2id parameters.
     * These match the pinstrjs implementation for cross-platform compatibility.
     * - t (iterations): 3
     * - m (memory): 65536 KiB (64 MB)
     * - p (parallelism): 4
     */
    private const val ARGON2_ITERATIONS = 3
    private const val ARGON2_MEMORY_KIB = 65536
    private const val ARGON2_PARALLELISM = 4
    private const val ARGON2_OUTPUT_LENGTH = 64 // 32 for signing + 32 for encryption

    /**
     * AES-GCM nonce length in bytes.
     * 12 bytes (96 bits) is the recommended size for GCM.
     */
    private const val NONCE_LENGTH = 12

    /**
     * AES-GCM authentication tag length in bits.
     */
    private const val GCM_TAG_LENGTH_BITS = 128

    /**
     * Domain separator for salt derivation.
     * This must match the pinstrjs implementation exactly.
     */
    private const val SALT_DOMAIN = "pinstr-vault-v1"

    private val argon2 = Argon2Kt()
    private val secureRandom = SecureRandom()

    /**
     * Derives a deterministic salt from a user's public key.
     * This enables vault recovery on any device with just pubkey + passphrase.
     *
     * @param pubkey The user's Nostr public key (hex format)
     * @return 32-byte deterministic salt
     */
    fun deriveSalt(pubkey: String): ByteArray {
        val input = "$SALT_DOMAIN:$pubkey"
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(input.toByteArray(Charsets.UTF_8))
    }

    /**
     * Derives vault keys from a passphrase and salt using Argon2id.
     *
     * @param passphrase User's passphrase (should be strong, min 12 characters)
     * @param salt Salt derived from user's pubkey via deriveSalt()
     * @return Signing key and encryption key
     */
    fun deriveVaultKeys(passphrase: String, salt: ByteArray): VaultKeys {
        val result = argon2.hash(
            mode = Argon2Mode.ARGON2_ID,
            password = passphrase.toByteArray(Charsets.UTF_8),
            salt = salt,
            tCostInIterations = ARGON2_ITERATIONS,
            mCostInKibibyte = ARGON2_MEMORY_KIB,
            parallelism = ARGON2_PARALLELISM,
            hashLengthInBytes = ARGON2_OUTPUT_LENGTH,
        )

        val keyMaterial = result.rawHashAsByteArray()

        return VaultKeys(
            signingKey = keyMaterial.copyOfRange(0, 32),
            encryptionKey = keyMaterial.copyOfRange(32, 64),
        )
    }

    /**
     * Encrypts plaintext using AES-256-GCM.
     *
     * @param plaintext Content to encrypt
     * @param encryptionKey 32-byte key from deriveVaultKeys
     * @return Serialized format: hex(nonce):hex(ciphertext)
     */
    fun encrypt(plaintext: String, encryptionKey: ByteArray): String {
        val nonce = ByteArray(NONCE_LENGTH)
        secureRandom.nextBytes(nonce)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val keySpec = SecretKeySpec(encryptionKey, "AES")
        val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, nonce)

        cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec)
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))

        return "${nonce.toHex()}:${ciphertext.toHex()}"
    }

    /**
     * Decrypts ciphertext using AES-256-GCM.
     *
     * @param encryptedData Serialized format: hex(nonce):hex(ciphertext)
     * @param encryptionKey 32-byte key from deriveVaultKeys
     * @return Decrypted plaintext
     * @throws Exception if decryption fails (wrong key or tampered data)
     */
    fun decrypt(encryptedData: String, encryptionKey: ByteArray): String {
        val parts = encryptedData.split(":")
        require(parts.size == 2) { "Invalid encrypted data format" }

        val nonce = parts[0].hexToByteArray()
        val ciphertext = parts[1].hexToByteArray()

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val keySpec = SecretKeySpec(encryptionKey, "AES")
        val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, nonce)

        cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec)
        val plaintext = cipher.doFinal(ciphertext)

        return String(plaintext, Charsets.UTF_8)
    }

    /**
     * Gets the Nostr public key (hex) from a vault signing key.
     *
     * @param signingKey 32-byte private key from deriveVaultKeys
     * @return Hex-encoded public key
     */
    fun getVaultPubkey(signingKey: ByteArray): String {
        val keyPair = KeyPair(privKey = signingKey)
        return keyPair.pubKey.toHexKey()
    }

    /**
     * Clears sensitive data from memory.
     * Call this when vault is locked or on logout.
     */
    fun clearKeys(keys: VaultKeys) {
        keys.signingKey.fill(0)
        keys.encryptionKey.fill(0)
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private fun String.hexToByteArray(): ByteArray {
        require(length % 2 == 0) { "Hex string must have even length" }
        return chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }
}

/**
 * Container for vault encryption keys.
 *
 * @property signingKey 32-byte private key for signing Nostr events
 * @property encryptionKey 32-byte key for AES-256-GCM encryption
 */
data class VaultKeys(
    val signingKey: ByteArray,
    val encryptionKey: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as VaultKeys
        return signingKey.contentEquals(other.signingKey) &&
            encryptionKey.contentEquals(other.encryptionKey)
    }

    override fun hashCode(): Int {
        var result = signingKey.contentHashCode()
        result = 31 * result + encryptionKey.contentHashCode()
        return result
    }
}
