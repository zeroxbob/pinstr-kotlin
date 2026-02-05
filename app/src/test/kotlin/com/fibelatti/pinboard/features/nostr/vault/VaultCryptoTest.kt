package com.fibelatti.pinboard.features.nostr.vault

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * Unit tests for VaultCrypto.
 *
 * Note: Argon2id is intentionally slow for security.
 * Key derivation can take several seconds on CI runners.
 */
class VaultCryptoTest {

    @Test
    fun `deriveSalt produces 32-byte salt from pubkey`() {
        val pubkey = "1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef"
        val salt = VaultCrypto.deriveSalt(pubkey)

        assertThat(salt.size).isEqualTo(32)
    }

    @Test
    fun `deriveSalt produces consistent salt for same pubkey`() {
        val pubkey = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
        val salt1 = VaultCrypto.deriveSalt(pubkey)
        val salt2 = VaultCrypto.deriveSalt(pubkey)

        assertThat(salt1.toHex()).isEqualTo(salt2.toHex())
    }

    @Test
    fun `deriveSalt produces different salts for different pubkeys`() {
        val pubkey1 = "1111111111111111111111111111111111111111111111111111111111111111"
        val pubkey2 = "2222222222222222222222222222222222222222222222222222222222222222"
        val salt1 = VaultCrypto.deriveSalt(pubkey1)
        val salt2 = VaultCrypto.deriveSalt(pubkey2)

        assertThat(salt1.toHex()).isNotEqualTo(salt2.toHex())
    }

    @Test
    fun `deriveVaultKeys produces 32-byte keys`() {
        val salt = VaultCrypto.deriveSalt("testpubkey")
        val keys = VaultCrypto.deriveVaultKeys("test-passphrase", salt)

        assertThat(keys.signingKey.size).isEqualTo(32)
        assertThat(keys.encryptionKey.size).isEqualTo(32)
    }

    @Test
    fun `deriveVaultKeys produces different keys for different passphrases`() {
        val salt = VaultCrypto.deriveSalt("testpubkey")
        val keys1 = VaultCrypto.deriveVaultKeys("passphrase-one", salt)
        val keys2 = VaultCrypto.deriveVaultKeys("passphrase-two", salt)

        assertThat(keys1.signingKey.toHex()).isNotEqualTo(keys2.signingKey.toHex())
        assertThat(keys1.encryptionKey.toHex()).isNotEqualTo(keys2.encryptionKey.toHex())
    }

    @Test
    fun `deriveVaultKeys produces different keys for different salts`() {
        val salt1 = VaultCrypto.deriveSalt("pubkey1")
        val salt2 = VaultCrypto.deriveSalt("pubkey2")
        val keys1 = VaultCrypto.deriveVaultKeys("same-passphrase", salt1)
        val keys2 = VaultCrypto.deriveVaultKeys("same-passphrase", salt2)

        assertThat(keys1.signingKey.toHex()).isNotEqualTo(keys2.signingKey.toHex())
    }

    @Test
    fun `deriveVaultKeys produces consistent keys for same inputs`() {
        val salt = VaultCrypto.deriveSalt("consistent-pubkey")
        val keys1 = VaultCrypto.deriveVaultKeys("consistent-passphrase", salt)
        val keys2 = VaultCrypto.deriveVaultKeys("consistent-passphrase", salt)

        assertThat(keys1.signingKey.toHex()).isEqualTo(keys2.signingKey.toHex())
        assertThat(keys1.encryptionKey.toHex()).isEqualTo(keys2.encryptionKey.toHex())
    }

    @Test
    fun `deterministic vault key derivation enables recovery`() {
        val pubkey = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
        val passphrase = "my-secret-passphrase"

        // Simulate vault creation
        val salt1 = VaultCrypto.deriveSalt(pubkey)
        val keys1 = VaultCrypto.deriveVaultKeys(passphrase, salt1)

        // Simulate recovery on a different device
        val salt2 = VaultCrypto.deriveSalt(pubkey)
        val keys2 = VaultCrypto.deriveVaultKeys(passphrase, salt2)

        // Keys should be identical
        assertThat(keys1.signingKey.toHex()).isEqualTo(keys2.signingKey.toHex())
        assertThat(keys1.encryptionKey.toHex()).isEqualTo(keys2.encryptionKey.toHex())
    }

    @Test
    fun `encrypt and decrypt roundtrip`() {
        val salt = VaultCrypto.deriveSalt("testpubkey")
        val keys = VaultCrypto.deriveVaultKeys("encryption-test", salt)
        val plaintext = "Hello, private bookmark!"

        val encrypted = VaultCrypto.encrypt(plaintext, keys.encryptionKey)
        val decrypted = VaultCrypto.decrypt(encrypted, keys.encryptionKey)

        assertThat(decrypted).isEqualTo(plaintext)
    }

    @Test
    fun `encrypt produces different ciphertext each time due to unique nonce`() {
        val salt = VaultCrypto.deriveSalt("testpubkey")
        val keys = VaultCrypto.deriveVaultKeys("nonce-test", salt)
        val plaintext = "Same content"

        val encrypted1 = VaultCrypto.encrypt(plaintext, keys.encryptionKey)
        val encrypted2 = VaultCrypto.encrypt(plaintext, keys.encryptionKey)

        assertThat(encrypted1).isNotEqualTo(encrypted2)
    }

    @Test
    fun `decrypt fails with wrong key`() {
        val salt = VaultCrypto.deriveSalt("testpubkey")
        val keys1 = VaultCrypto.deriveVaultKeys("correct-key", salt)
        val keys2 = VaultCrypto.deriveVaultKeys("wrong-key", salt)
        val plaintext = "Secret content"

        val encrypted = VaultCrypto.encrypt(plaintext, keys1.encryptionKey)

        assertThrows<Exception> {
            VaultCrypto.decrypt(encrypted, keys2.encryptionKey)
        }
    }

    @Test
    fun `encrypt and decrypt handles unicode content`() {
        val salt = VaultCrypto.deriveSalt("testpubkey")
        val keys = VaultCrypto.deriveVaultKeys("unicode-test", salt)
        val plaintext = "Hello \uD83D\uDC4B \u4E16\u754C \uD83C\uDF0D \u0645\u0631\u062D\u0628\u0627"

        val encrypted = VaultCrypto.encrypt(plaintext, keys.encryptionKey)
        val decrypted = VaultCrypto.decrypt(encrypted, keys.encryptionKey)

        assertThat(decrypted).isEqualTo(plaintext)
    }

    @Test
    fun `encrypt and decrypt handles large content`() {
        val salt = VaultCrypto.deriveSalt("testpubkey")
        val keys = VaultCrypto.deriveVaultKeys("large-content-test", salt)
        val plaintext = "x".repeat(100000)

        val encrypted = VaultCrypto.encrypt(plaintext, keys.encryptionKey)
        val decrypted = VaultCrypto.decrypt(encrypted, keys.encryptionKey)

        assertThat(decrypted).isEqualTo(plaintext)
    }

    @Test
    fun `encrypted format is hex(nonce) colon hex(ciphertext)`() {
        val salt = VaultCrypto.deriveSalt("testpubkey")
        val keys = VaultCrypto.deriveVaultKeys("format-test", salt)
        val plaintext = "Test content"

        val encrypted = VaultCrypto.encrypt(plaintext, keys.encryptionKey)
        val parts = encrypted.split(":")

        assertThat(parts.size).isEqualTo(2)
        // Nonce is 12 bytes = 24 hex chars
        assertThat(parts[0].length).isEqualTo(24)
        // Ciphertext includes auth tag (16 bytes) + plaintext
        assertThat(parts[1].length).isGreaterThan(32)
    }

    @Test
    fun `decrypt throws on invalid format`() {
        val salt = VaultCrypto.deriveSalt("testpubkey")
        val keys = VaultCrypto.deriveVaultKeys("format-test", salt)

        assertThrows<IllegalArgumentException> {
            VaultCrypto.decrypt("invalid", keys.encryptionKey)
        }
        assertThrows<IllegalArgumentException> {
            VaultCrypto.decrypt("", keys.encryptionKey)
        }
    }

    @Test
    fun `getVaultPubkey produces valid hex pubkey`() {
        val salt = VaultCrypto.deriveSalt("testpubkey")
        val keys = VaultCrypto.deriveVaultKeys("pubkey-test", salt)

        val vaultPubkey = VaultCrypto.getVaultPubkey(keys.signingKey)

        assertThat(vaultPubkey.length).isEqualTo(64)
        assertThat(vaultPubkey).matches("[0-9a-f]+")
    }

    @Test
    fun `getVaultPubkey is consistent for same signing key`() {
        val salt = VaultCrypto.deriveSalt("testpubkey")
        val keys = VaultCrypto.deriveVaultKeys("consistent-test", salt)

        val pubkey1 = VaultCrypto.getVaultPubkey(keys.signingKey)
        val pubkey2 = VaultCrypto.getVaultPubkey(keys.signingKey)

        assertThat(pubkey1).isEqualTo(pubkey2)
    }

    @Test
    fun `clearKeys zeros out key material`() {
        val salt = VaultCrypto.deriveSalt("testpubkey")
        val keys = VaultCrypto.deriveVaultKeys("clear-test", salt)

        // Verify keys have data
        assertThat(keys.signingKey.any { it != 0.toByte() }).isTrue()
        assertThat(keys.encryptionKey.any { it != 0.toByte() }).isTrue()

        VaultCrypto.clearKeys(keys)

        // Verify keys are zeroed
        assertThat(keys.signingKey.all { it == 0.toByte() }).isTrue()
        assertThat(keys.encryptionKey.all { it == 0.toByte() }).isTrue()
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}
