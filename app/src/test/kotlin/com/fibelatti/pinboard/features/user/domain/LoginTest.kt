package com.fibelatti.pinboard.features.user.domain

import com.fibelatti.core.functional.Failure
import com.fibelatti.core.functional.Success
import com.fibelatti.core.functional.exceptionOrNull
import com.fibelatti.core.functional.getOrNull
import com.fibelatti.pinboard.MockDataProvider.SAMPLE_API_TOKEN
import com.fibelatti.pinboard.MockDataProvider.SAMPLE_DATE_TIME
import com.fibelatti.pinboard.core.AppMode
import com.fibelatti.pinboard.core.AppModeProvider
import com.fibelatti.pinboard.features.appstate.AppStateRepository
import com.fibelatti.pinboard.features.appstate.UserLoggedIn
import com.fibelatti.pinboard.features.appstate.UserLoginFailed
import com.fibelatti.pinboard.features.nostr.signer.NostrSignerProvider
import com.fibelatti.pinboard.features.posts.domain.PostsRepository
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerifySequence
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class LoginTest {

    private val mockUserRepository = mockk<UserRepository> {
        coJustRun { setAuthToken(appMode = any(), authToken = any()) }
        every { nostrPubkey = any() } returns Unit
        every { nostrNsec = any() } returns Unit
    }
    private val mockAppStateRepository = mockk<AppStateRepository> {
        coJustRun { runAction(any()) }
    }
    private val mockPostsRepository = mockk<PostsRepository>()
    private val mockAppModeProvider = mockk<AppModeProvider> {
        coJustRun { setSelection(any()) }
    }
    private val mockNostrSignerProvider = mockk<NostrSignerProvider> {
        coJustRun { setupInternalSigner(any()) }
        coJustRun { setupAmberSigner(any(), any()) }
        coJustRun { setupBunkerSigner(any()) }
    }

    private val login = Login(
        userRepository = mockUserRepository,
        appStateRepository = mockAppStateRepository,
        postsRepository = mockPostsRepository,
        appModeProvider = mockAppModeProvider,
        nostrSignerProvider = mockNostrSignerProvider,
    )

    @Test
    fun `GIVEN repository call fails WHEN Login is called THEN UserLoginFailed runs`() = runTest {
        // GIVEN
        coEvery { mockPostsRepository.update() } returns Failure(Exception())

        // WHEN
        val result = login(Login.PinboardParams(authToken = SAMPLE_API_TOKEN))

        // THEN
        assertThat(result.exceptionOrNull()).isInstanceOf(Exception::class.java)
        coVerifySequence {
            mockUserRepository.setAuthToken(appMode = AppMode.PINBOARD, authToken = SAMPLE_API_TOKEN)
            mockAppModeProvider.setSelection(appMode = AppMode.PINBOARD)
            mockPostsRepository.update()
            mockAppStateRepository.runAction(UserLoginFailed(appMode = AppMode.PINBOARD))
        }
    }

    @Test
    fun `GIVEN repository call is successful WHEN Login is called THEN UserLoggedIn runs`() = runTest {
        // GIVEN
        coEvery { mockPostsRepository.update() } returns Success(SAMPLE_DATE_TIME)
        coEvery { mockPostsRepository.clearCache() } returns Success(Unit)

        // WHEN
        val result = login(Login.PinboardParams(authToken = SAMPLE_API_TOKEN))

        // THEN
        assertThat(result.getOrNull()).isEqualTo(Unit)
        coVerifySequence {
            mockUserRepository.setAuthToken(appMode = AppMode.PINBOARD, authToken = SAMPLE_API_TOKEN)
            mockAppModeProvider.setSelection(appMode = AppMode.PINBOARD)
            mockPostsRepository.update()
            mockPostsRepository.clearCache()
            mockAppStateRepository.runAction(UserLoggedIn(appMode = AppMode.PINBOARD))
        }
    }

    @Test
    fun `GIVEN nsec WHEN NostrNsecParams is used THEN internal signer is configured`() = runTest {
        // GIVEN
        val nsec = "nsec1abc123"
        coEvery { mockPostsRepository.update() } returns Success(SAMPLE_DATE_TIME)

        // WHEN
        val result = login(Login.NostrNsecParams(nsec = nsec))

        // THEN
        assertThat(result.getOrNull()).isEqualTo(Unit)
        coVerifySequence {
            mockUserRepository.nostrPubkey = any()
            mockNostrSignerProvider.setupInternalSigner(nsec)
            mockAppModeProvider.setSelection(appMode = AppMode.NOSTR)
            mockPostsRepository.update()
            mockAppStateRepository.runAction(UserLoggedIn(appMode = AppMode.NOSTR))
        }
    }

    @Test
    fun `GIVEN amber pubkey WHEN NostrAmberParams is used THEN amber signer is configured`() = runTest {
        // GIVEN
        val pubkey = "abc123"
        val signerPackage = "com.example.amber"
        coEvery { mockPostsRepository.update() } returns Success(SAMPLE_DATE_TIME)

        // WHEN
        val result = login(Login.NostrAmberParams(pubkey = pubkey, signerPackage = signerPackage))

        // THEN
        assertThat(result.getOrNull()).isEqualTo(Unit)
        coVerifySequence {
            mockUserRepository.nostrPubkey = pubkey
            mockNostrSignerProvider.setupAmberSigner(pubkey, signerPackage)
            mockAppModeProvider.setSelection(appMode = AppMode.NOSTR)
            mockPostsRepository.update()
            mockAppStateRepository.runAction(UserLoggedIn(appMode = AppMode.NOSTR))
        }
    }

    @Test
    fun `GIVEN valid bunker URI WHEN NostrBunkerParams is used THEN bunker signer is configured`() = runTest {
        // GIVEN
        val bunkerUri = "bunker://abcd1234abcd1234abcd1234abcd1234abcd1234abcd1234abcd1234abcd1234?relay=wss://relay.example.com"
        coEvery { mockPostsRepository.update() } returns Success(SAMPLE_DATE_TIME)

        // WHEN
        val result = login(Login.NostrBunkerParams(bunkerUri = bunkerUri))

        // THEN
        assertThat(result.getOrNull()).isEqualTo(Unit)
        coVerifySequence {
            mockUserRepository.nostrPubkey = "abcd1234abcd1234abcd1234abcd1234abcd1234abcd1234abcd1234abcd1234"
            mockNostrSignerProvider.setupBunkerSigner(bunkerUri)
            mockAppModeProvider.setSelection(appMode = AppMode.NOSTR)
            mockPostsRepository.update()
            mockAppStateRepository.runAction(UserLoggedIn(appMode = AppMode.NOSTR))
        }
    }
}
