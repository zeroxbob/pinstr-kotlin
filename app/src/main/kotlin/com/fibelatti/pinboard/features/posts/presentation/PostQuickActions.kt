package com.fibelatti.pinboard.features.posts.presentation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.fibelatti.pinboard.R
import com.fibelatti.pinboard.features.posts.domain.model.Post

sealed class PostQuickActions(
    @StringRes val title: Int,
    @DrawableRes val icon: Int,
) {

    abstract val post: Post
    abstract val serializedName: String

    data class Edit(
        override val post: Post,
    ) : PostQuickActions(
        title = R.string.quick_actions_edit,
        icon = R.drawable.ic_edit,
    ) {

        override val serializedName: String = "EDIT"
    }

    data class Delete(
        override val post: Post,
    ) : PostQuickActions(
        title = R.string.quick_actions_delete,
        icon = R.drawable.ic_delete,
    ) {

        override val serializedName: String = "DELETE"
    }

    data class CopyUrl(
        override val post: Post,
    ) : PostQuickActions(
        title = R.string.quick_actions_copy_url,
        icon = R.drawable.ic_copy,
    ) {

        override val serializedName: String = "COPY_URL"
    }

    data class Share(
        override val post: Post,
    ) : PostQuickActions(
        title = R.string.quick_actions_share,
        icon = R.drawable.ic_share,
    ) {

        override val serializedName: String = "SHARE"
    }

    data class ExpandDescription(
        override val post: Post,
    ) : PostQuickActions(
        title = R.string.quick_actions_expand_description,
        icon = R.drawable.ic_expand,
    ) {

        override val serializedName: String = "EXPAND_DESCRIPTION"
    }

    data class OpenBrowser(
        override val post: Post,
    ) : PostQuickActions(
        title = R.string.quick_actions_open_in_browser,
        icon = R.drawable.ic_browser,
    ) {

        override val serializedName: String = "OPEN_BROWSER"
    }

    companion object {

        fun allOptions(
            post: Post,
        ): List<PostQuickActions> = buildList {
            if (post.displayDescription.isNotBlank()) {
                add(ExpandDescription(post))
            }

            add(Edit(post))
            add(Delete(post))

            add(CopyUrl(post))
            add(Share(post))

            add(OpenBrowser(post))
        }
    }
}
