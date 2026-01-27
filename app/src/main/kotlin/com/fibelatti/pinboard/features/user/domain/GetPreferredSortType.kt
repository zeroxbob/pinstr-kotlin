package com.fibelatti.pinboard.features.user.domain

import com.fibelatti.pinboard.features.appstate.ByDateAddedNewestFirst
import com.fibelatti.pinboard.features.appstate.ByDateAddedOldestFirst
import com.fibelatti.pinboard.features.appstate.ByDateModifiedNewestFirst
import com.fibelatti.pinboard.features.appstate.ByDateModifiedOldestFirst
import com.fibelatti.pinboard.features.appstate.SortType
import javax.inject.Inject

class GetPreferredSortType @Inject constructor(
    private val userRepository: UserRepository,
) {

    operator fun invoke(): SortType {
        val preferredSortType = userRepository.preferredSortType

        return when (preferredSortType) {
            is ByDateModifiedNewestFirst -> ByDateAddedNewestFirst
            is ByDateModifiedOldestFirst -> ByDateAddedOldestFirst
            else -> preferredSortType
        }
    }
}
