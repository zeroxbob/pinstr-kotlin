package com.fibelatti.pinboard.features.nostr.signer

import android.content.Context
import android.content.Intent
import android.net.Uri
import timber.log.Timber

/**
 * NIP-55 Amber signer integration.
 * Communicates with Amber (or compatible signer apps) via Android Intents.
 */
object AmberSigner {

    private const val NOSTRSIGNER_SCHEME = "nostrsigner"

    /**
     * Check if an external signer app (like Amber) is installed.
     */
    fun isExternalSignerInstalled(context: Context): Boolean {
        val intent = Intent().apply {
            action = Intent.ACTION_VIEW
            data = Uri.parse("$NOSTRSIGNER_SCHEME:")
        }
        val infos = context.packageManager.queryIntentActivities(intent, 0)
        return infos.isNotEmpty().also {
            Timber.d("AmberSigner: External signer installed = $it")
        }
    }

    /**
     * Create intent to request the user's public key from the signer.
     */
    fun createGetPublicKeyIntent(): Intent {
        return Intent(Intent.ACTION_VIEW, Uri.parse("$NOSTRSIGNER_SCHEME:")).apply {
            putExtra("type", "get_public_key")
        }
    }

    /**
     * Create intent to sign an event.
     * @param eventJson The unsigned event JSON
     * @param currentUserPubkey The currently logged in user's pubkey
     * @param signerPackage The signer app's package name (from get_public_key response)
     */
    fun createSignEventIntent(
        eventJson: String,
        currentUserPubkey: String,
        signerPackage: String,
        eventId: String? = null,
    ): Intent {
        val encodedJson = Uri.encode(eventJson)
        return Intent(Intent.ACTION_VIEW, Uri.parse("$NOSTRSIGNER_SCHEME:$encodedJson")).apply {
            `package` = signerPackage
            putExtra("type", "sign_event")
            putExtra("current_user", currentUserPubkey)
            eventId?.let { putExtra("id", it) }
            // Flags to avoid multiple signer windows
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
    }

    /**
     * Parse the result from get_public_key intent.
     * @return Pair of (pubkey, signerPackage) or null if rejected/failed
     */
    fun parseGetPublicKeyResult(intent: Intent?): Pair<String, String>? {
        val pubkey = intent?.getStringExtra("result")
        val signerPackage = intent?.getStringExtra("package")

        return if (pubkey != null && signerPackage != null) {
            Timber.d("AmberSigner: Got pubkey ${pubkey.take(16)}... from package $signerPackage")
            Pair(pubkey, signerPackage)
        } else {
            Timber.w("AmberSigner: Failed to get pubkey from result")
            null
        }
    }

    /**
     * Parse the result from sign_event intent.
     * @return Triple of (signature, eventId, signedEventJson) or null if rejected/failed
     */
    fun parseSignEventResult(intent: Intent?): SignEventResult? {
        val signature = intent?.getStringExtra("result")
        val eventId = intent?.getStringExtra("id")
        val signedEventJson = intent?.getStringExtra("event")

        return if (signature != null) {
            Timber.d("AmberSigner: Got signature for event $eventId")
            SignEventResult(
                signature = signature,
                eventId = eventId,
                signedEventJson = signedEventJson,
            )
        } else {
            Timber.w("AmberSigner: Sign request rejected or failed")
            null
        }
    }

    data class SignEventResult(
        val signature: String,
        val eventId: String?,
        val signedEventJson: String?,
    )
}
