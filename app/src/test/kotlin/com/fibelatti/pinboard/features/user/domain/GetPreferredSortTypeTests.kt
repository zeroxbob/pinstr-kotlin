package com.fibelatti.pinboard.features.user.domain

import com.fibelatti.pinboard.features.appstate.ByDateAddedNewestFirst
import com.fibelatti.pinboard.features.appstate.ByDateAddedOldestFirst
import com.fibelatti.pinboard.features.appstate.ByDateModifiedNewestFirst
import com.fibelatti.pinboard.features.appstate.ByDateModifiedOldestFirst
import com.fibelatti.pinboard.features.appstate.SortType
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test

class GetPreferredSortTypeTests {

    private val mockUserRepository = mockk<UserRepository>()

    private val getPreferredSortType = GetPreferredSortType(
        userRepository = mockUserRepository,
    )

    @Test
    fun `WHEN invoke is called THEN ByDateModifiedNewestFirst is converted to ByDateAddedNewestFirst`() {
        every { mockUserRepository.preferredSortType } returns ByDateModifiedNewestFirst

        val result = getPreferredSortType()

        assertThat(result).isEqualTo(ByDateAddedNewestFirst)
    }

    @Test
    fun `WHEN invoke is called THEN ByDateModifiedOldestFirst is converted to ByDateAddedOldestFirst`() {
        every { mockUserRepository.preferredSortType } returns ByDateModifiedOldestFirst

        val result = getPreferredSortType()

        assertThat(result).isEqualTo(ByDateAddedOldestFirst)
    }

    @Test
    fun `WHEN invoke is called THEN other sort types are not converted`() {
        val expectedSortType = mockk<SortType>()

        every { mockUserRepository.preferredSortType } returns expectedSortType

        val result = getPreferredSortType()

        assertThat(result).isEqualTo(expectedSortType)
    }
}
