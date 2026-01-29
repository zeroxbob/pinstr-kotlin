package com.fibelatti.pinboard.core.persistence

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Secure storage for sensitive data like private keys.
 * Uses EncryptedSharedPreferences with AES-256 encryption backed by Android Keystore.
 */
@Singleton
class SecureStorage @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val encryptedPrefs by lazy {
        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            EncryptedSharedPreferences.create(
                context,
                ENCRYPTED_PREFS_FILE,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        } catch (e: Exception) {
            Timber.e(e, "SecureStorage: Failed to create EncryptedSharedPreferences")
            null
        }
    }

    var nostrNsec: String?
        get() = encryptedPrefs?.getString(KEY_NOSTR_NSEC, null)
        set(value) {
            encryptedPrefs?.edit()?.apply {
                if (value != null) {
                    putString(KEY_NOSTR_NSEC, value)
                } else {
                    remove(KEY_NOSTR_NSEC)
                }
                apply()
            }
        }

    fun clear() {
        encryptedPrefs?.edit()?.clear()?.apply()
    }

    companion object {
        private const val ENCRYPTED_PREFS_FILE = "secure_prefs"
        private const val KEY_NOSTR_NSEC = "nostr_nsec"
    }
}
