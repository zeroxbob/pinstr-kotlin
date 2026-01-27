package com.fibelatti.pinboard.features.user.domain

import com.fibelatti.pinboard.core.AppMode

data class UserCredentials(
    val pinboardAuthToken: String?,
    val nostrNsec: String? = null,
    val appReviewMode: Boolean = false,
) {

    fun getConnectedServices(): Set<AppMode> = buildSet {
        if (appReviewMode || !nostrNsec.isNullOrBlank()) add(AppMode.NO_API)
        if (pinboardAuthToken != null) add(AppMode.PINBOARD)
    }

    fun hasAuthToken(): Boolean = getConnectedServices().isNotEmpty()

    fun getPinboardUsername(): String? = pinboardAuthToken?.substringBefore(":")
}
