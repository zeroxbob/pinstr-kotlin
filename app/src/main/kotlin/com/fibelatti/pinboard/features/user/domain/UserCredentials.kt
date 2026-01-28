package com.fibelatti.pinboard.features.user.domain

import com.fibelatti.pinboard.core.AppMode

data class UserCredentials(
    val pinboardAuthToken: String?,
    val nostrPubkey: String? = null,
    val nostrNsec: String? = null,
    val appReviewMode: Boolean = false,
) {

    fun getConnectedServices(): Set<AppMode> = buildSet {
        if (appReviewMode) add(AppMode.NO_API)
        if (!nostrPubkey.isNullOrBlank()) add(AppMode.NOSTR)
        if (pinboardAuthToken != null) add(AppMode.PINBOARD)
    }

    fun hasAuthToken(): Boolean = getConnectedServices().isNotEmpty()

    fun getPinboardUsername(): String? = pinboardAuthToken?.substringBefore(":")
}
