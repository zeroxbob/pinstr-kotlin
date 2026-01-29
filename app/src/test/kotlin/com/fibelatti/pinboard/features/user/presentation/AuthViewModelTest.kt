package com.fibelatti.pinboard.features.user.presentation

import android.content.Context
import app.cash.turbine.test
import com.fibelatti.core.android.platform.ResourceProvider
import com.fibelatti.core.functional.Failure
import com.fibelatti.core.functional.Success
import com.fibelatti.pinboard.BaseViewModelTest
import com.fibelatti.pinboard.MockDataProvider.SAMPLE_API_TOKEN
import com.fibelatti.pinboard.MockDataProvider.createAppState
import com.fibelatti.pinboard.MockDataProvider.createPostListContent
import com.fibelatti.pinboard.R
import com.fibelatti.pinboard.core.AppMode
import com.fibelatti.pinboard.features.appstate.AppStateRepository
import com.fibelatti.pinboard.features.appstate.LoginContent
import com.fibelatti.pinboard.features.user.domain.Login
import com.google.common.truth.Truth.assertThat
import io.ktor.client.plugins.ResponseException
import io.ktor.client.statement.HttpResponse
import io.mockk.Called
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class AuthViewModelTest : BaseViewModelTest() {

    private val mockLogin = mockk<Login>()
    private val mockContext = mockk<Context>(relaxed = true)

    private val appStateFlow = MutableStateFlow(createAppState(content = LoginContent()))
    private val mockAppStateRepository = mockk<AppStateRepository> {
        every { appState } returns appStateFlow
        coJustRun { runAction(any()) }
    }
    private val mockResourceProvider = mockk<ResourceProvider>()

    private val viewModel = AuthViewModel(
        scope = TestScope(dispatcher),
        appStateRepository = mockAppStateRepository,
        context = mockContext,
        loginUseCase = mockLogin,
        resourceProvider = mockResourceProvider,
    )

    @Nested
    inner class ContentTests {

        @Test
        fun `content changes reset the screen state`() = runTest {
            viewModel.screenState.test {
                assertThat(awaitItem()).isEqualTo(
                    AuthViewModel.ScreenState(
                        isAmberInstalled = false,
                    ),
                )

                appStateFlow.value = createAppState(content = createPostListContent())
                appStateFlow.value = createAppState(content = LoginContent(appMode = AppMode.PINBOARD))

                assertThat(awaitItem()).isEqualTo(
                    AuthViewModel.ScreenState(
                        isAmberInstalled = false,
                    ),
                )
            }
        }
    }

    @Nested
    inner class LoginWithNsecTests {

        @Test
        fun `GIVEN nsec is empty WHEN loginWithNsec is called THEN error state is emitted`() = runTest {
            // GIVEN
            every { mockResourceProvider.getString(R.string.auth_token_empty) } returns "R.string.auth_token_empty"

            // WHEN
            viewModel.loginWithNsec(nsec = "")

            verify { mockLogin wasNot Called }

            assertThat(viewModel.screenState.first()).isEqualTo(
                AuthViewModel.ScreenState(apiTokenError = "R.string.auth_token_empty"),
            )
        }

        @Test
        fun `GIVEN Login is successful WHEN loginWithNsec is called THEN nothing else happens`() = runTest {
            // GIVEN
            coEvery {
                mockLogin(
                    Login.NostrNsecParams(
                        nsec = SAMPLE_API_TOKEN,
                    ),
                )
            } returns Success(Unit)

            // WHEN
            viewModel.loginWithNsec(nsec = SAMPLE_API_TOKEN)

            // THEN
            coVerify {
                mockLogin(
                    Login.NostrNsecParams(
                        nsec = SAMPLE_API_TOKEN,
                    ),
                )
            }

            assertThat(viewModel.error.first()).isNull()
        }

        @Test
        fun `GIVEN Login fails and error code is 401 WHEN loginWithNsec is called THEN apiTokenError should receive a value`() =
            runTest {
                // GIVEN
                val error = mockk<ResponseException> {
                    every { response } returns mockk<HttpResponse> {
                        every { status } returns mockk {
                            every { value } returns 401
                        }
                    }
                }

                coEvery { mockLogin(Login.NostrNsecParams(nsec = SAMPLE_API_TOKEN)) } returns Failure(error)
                every { mockResourceProvider.getString(R.string.auth_token_error) } returns "R.string.auth_token_error"

                // WHEN
                viewModel.loginWithNsec(nsec = SAMPLE_API_TOKEN)

                // THEN
                coVerify { mockLogin(Login.NostrNsecParams(nsec = SAMPLE_API_TOKEN)) }

                assertThat(viewModel.error.first()).isNull()
                assertThat(viewModel.screenState.first()).isEqualTo(
                    AuthViewModel.ScreenState(apiTokenError = "R.string.auth_token_error"),
                )
            }

        @Test
        fun `GIVEN Login fails WHEN loginWithNsec is called THEN error should receive a value`() = runTest {
            // GIVEN
            val error = Exception()
            coEvery { mockLogin(Login.NostrNsecParams(nsec = SAMPLE_API_TOKEN)) } returns Failure(error)

            // WHEN
            viewModel.loginWithNsec(nsec = SAMPLE_API_TOKEN)

            // THEN
            coVerify { mockLogin(Login.NostrNsecParams(nsec = SAMPLE_API_TOKEN)) }

            assertThat(viewModel.error.first()).isEqualTo(error)
        }
    }

    @Nested
    inner class LoginWithBunkerTests {

        @Test
        fun `GIVEN bunker URI is empty WHEN loginWithBunker is called THEN error state is emitted`() = runTest {
            // GIVEN
            every { mockResourceProvider.getString(R.string.auth_bunker_empty) } returns "R.string.auth_bunker_empty"

            // WHEN
            viewModel.loginWithBunker(bunkerUri = "")

            verify { mockLogin wasNot Called }

            assertThat(viewModel.screenState.first()).isEqualTo(
                AuthViewModel.ScreenState(apiTokenError = "R.string.auth_bunker_empty"),
            )
        }

        @Test
        fun `GIVEN bunker URI is invalid WHEN loginWithBunker is called THEN error state is emitted`() = runTest {
            // GIVEN
            every { mockResourceProvider.getString(R.string.auth_bunker_invalid) } returns "R.string.auth_bunker_invalid"

            // WHEN
            viewModel.loginWithBunker(bunkerUri = "invalid-uri")

            verify { mockLogin wasNot Called }

            assertThat(viewModel.screenState.first()).isEqualTo(
                AuthViewModel.ScreenState(apiTokenError = "R.string.auth_bunker_invalid"),
            )
        }

        @Test
        fun `GIVEN Login is successful WHEN loginWithBunker is called THEN nothing else happens`() = runTest {
            // GIVEN
            val bunkerUri = "bunker://abcd1234abcd1234abcd1234abcd1234abcd1234abcd1234abcd1234abcd1234?relay=wss://relay.example.com"
            coEvery {
                mockLogin(Login.NostrBunkerParams(bunkerUri = bunkerUri))
            } returns Success(Unit)

            // WHEN
            viewModel.loginWithBunker(bunkerUri = bunkerUri)

            // THEN
            coVerify {
                mockLogin(Login.NostrBunkerParams(bunkerUri = bunkerUri))
            }

            assertThat(viewModel.error.first()).isNull()
        }
    }

    @Nested
    inner class MethodSelectionTests {

        @Test
        fun `WHEN selectLoginMethod is called THEN selectedMethod is updated`() = runTest {
            // WHEN
            viewModel.selectLoginMethod(NostrLoginMethod.NSEC)

            // THEN
            assertThat(viewModel.screenState.first().selectedMethod).isEqualTo(NostrLoginMethod.NSEC)
        }

        @Test
        fun `WHEN clearSelectedMethod is called THEN selectedMethod is null`() = runTest {
            // GIVEN
            viewModel.selectLoginMethod(NostrLoginMethod.NSEC)

            // WHEN
            viewModel.clearSelectedMethod()

            // THEN
            assertThat(viewModel.screenState.first().selectedMethod).isNull()
        }
    }
}
