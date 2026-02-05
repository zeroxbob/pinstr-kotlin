package com.fibelatti.pinboard.features.posts.presentation

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
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
    val keyColor = MaterialTheme.colorScheme.primary
    val stringColor = MaterialTheme.colorScheme.tertiary
    val numberColor = MaterialTheme.colorScheme.secondary
    val booleanColor = MaterialTheme.colorScheme.error
    val defaultColor = MaterialTheme.colorScheme.onSurfaceVariant

    val highlightedJson = remember(json, keyColor, stringColor, numberColor, booleanColor, defaultColor) {
        json?.let { prettyPrintJson(it) }?.let { prettyJson ->
            highlightJson(prettyJson, keyColor, stringColor, numberColor, booleanColor, defaultColor)
        }
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
                text = highlightedJson ?: AnnotatedString(stringResource(R.string.posts_json_not_available)),
                modifier = Modifier.fillMaxWidth(),
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

private fun highlightJson(
    json: String,
    keyColor: Color,
    stringColor: Color,
    numberColor: Color,
    booleanColor: Color,
    defaultColor: Color,
): AnnotatedString = buildAnnotatedString {
    // Regex patterns for JSON tokens
    val keyPattern = """"[^"]*"\s*:""".toRegex()
    val stringPattern = """:\s*"[^"]*"""".toRegex()
    val numberPattern = """:\s*-?\d+\.?\d*""".toRegex()
    val booleanNullPattern = """\b(true|false|null)\b""".toRegex()

    // Default style
    append(json)
    addStyle(SpanStyle(color = defaultColor), 0, json.length)

    // Highlight keys (property names)
    keyPattern.findAll(json).forEach { match ->
        val quoteEnd = match.value.lastIndexOf('"')
        addStyle(SpanStyle(color = keyColor), match.range.first, match.range.first + quoteEnd + 1)
    }

    // Highlight string values
    stringPattern.findAll(json).forEach { match ->
        val quoteStart = match.value.indexOf('"')
        addStyle(SpanStyle(color = stringColor), match.range.first + quoteStart, match.range.last + 1)
    }

    // Highlight numbers
    numberPattern.findAll(json).forEach { match ->
        val colonEnd = match.value.indexOfFirst { it.isDigit() || it == '-' }
        addStyle(SpanStyle(color = numberColor), match.range.first + colonEnd, match.range.last + 1)
    }

    // Highlight booleans and null
    booleanNullPattern.findAll(json).forEach { match ->
        addStyle(SpanStyle(color = booleanColor), match.range.first, match.range.last + 1)
    }
}
