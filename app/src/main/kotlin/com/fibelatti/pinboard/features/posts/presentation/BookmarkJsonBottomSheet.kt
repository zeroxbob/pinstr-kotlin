package com.fibelatti.pinboard.features.posts.presentation

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fibelatti.pinboard.R
import com.fibelatti.pinboard.features.posts.domain.model.Post
import com.fibelatti.ui.components.AppBottomSheet
import com.fibelatti.ui.components.AppSheetState
import com.fibelatti.ui.components.bottomSheetData
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

@Composable
fun BookmarkJsonBottomSheet(
    sheetState: AppSheetState,
) {
    val post: Post = sheetState.bottomSheetData() ?: return

    AppBottomSheet(
        sheetState = sheetState,
    ) {
        BookmarkJsonContent(
            title = post.displayTitle,
            json = post.nostrEventJson,
        )
    }
}

@Composable
private fun BookmarkJsonContent(
    title: String,
    json: String?,
) {
    val prettyJson = remember(json) {
        json?.let { prettyPrintJson(it) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(rememberNestedScrollInteropConnection())
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, bottom = 100.dp),
    ) {
        Text(
            text = title,
            modifier = Modifier.padding(top = 16.dp),
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.titleLarge,
        )

        Text(
            text = stringResource(R.string.posts_json_title),
            modifier = Modifier.padding(top = 8.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )

        HorizontalDivider(
            modifier = Modifier.padding(vertical = 16.dp),
            color = MaterialTheme.colorScheme.onSurface,
        )

        SelectionContainer {
            Text(
                text = prettyJson ?: stringResource(R.string.posts_json_not_available),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                lineHeight = 18.sp,
            )
        }
    }
}

private val prettyJson = Json { prettyPrint = true }

private fun prettyPrintJson(json: String): String {
    return try {
        val jsonElement: JsonElement = Json.parseToJsonElement(json)
        prettyJson.encodeToString(JsonElement.serializer(), jsonElement)
    } catch (e: Exception) {
        json
    }
}
