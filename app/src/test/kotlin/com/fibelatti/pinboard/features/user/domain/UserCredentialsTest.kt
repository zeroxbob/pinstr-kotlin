package com.fibelatti.pinboard.features.user.domain

import com.fibelatti.pinboard.core.AppMode
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class UserCredentialsTest {

    @Test
    fun `connectedServices - Pinboard connected`() {
        val userCredentials = UserCredentials(
            pinboardAuthToken = "token",
        )

        val connectedServices = userCredentials.getConnectedServices()

        assertThat(connectedServices).containsExactly(AppMode.PINBOARD)
    }

    @Test
    fun `connectedServices - no services connected`() {
        val userCredentials = UserCredentials(
            pinboardAuthToken = null,
        )

        val connectedServices = userCredentials.getConnectedServices()

        assertThat(connectedServices).isEmpty()
    }

    @Test
    fun `connectedServices - app review mode`() {
        val userCredentials = UserCredentials(
            pinboardAuthToken = null,
            appReviewMode = true,
        )

        val connectedServices = userCredentials.getConnectedServices()

        assertThat(connectedServices).containsExactly(AppMode.NO_API)
    }

    @Test
    fun `connectedServices - nostr connected`() {
        val userCredentials = UserCredentials(
            pinboardAuthToken = null,
            nostrNsec = "nsec1...",
        )

        val connectedServices = userCredentials.getConnectedServices()

        assertThat(connectedServices).containsExactly(AppMode.NO_API)
    }

    @Test
    fun `hasAuthToken - true with Pinboard connected`() {
        val userCredentials = UserCredentials(
            pinboardAuthToken = "token",
        )

        assertThat(userCredentials.hasAuthToken()).isTrue()
    }

    @Test
    fun `hasAuthToken - false with no service connected`() {
        val userCredentials = UserCredentials(
            pinboardAuthToken = null,
        )

        assertThat(userCredentials.hasAuthToken()).isFalse()
    }

    @Test
    fun `hasAuthToken - true with app review mode`() {
        val userCredentials = UserCredentials(
            pinboardAuthToken = null,
            appReviewMode = true,
        )

        assertThat(userCredentials.hasAuthToken()).isTrue()
    }

    @Test
    fun `pinboardUsername - valid username`() {
        val userCredentials = UserCredentials(
            pinboardAuthToken = "token:123",
        )

        assertThat(userCredentials.getPinboardUsername()).isEqualTo("token")
    }

    @Test
    fun `pinboardUsername - null for null token`() {
        val userCredentials = UserCredentials(
            pinboardAuthToken = null,
        )

        assertThat(userCredentials.getPinboardUsername()).isNull()
    }
}
