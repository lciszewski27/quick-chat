package dev.lciszewski27.quickchat.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownTypography

/**
 * Model reply body. Renders markdown (bold, lists, code, tables, …) with
 * app typography; plain text without markup renders like a normal bubble.
 * Headings are deliberately capped at title scale — renderer defaults use
 * display scale, which is oversized inside a compact chat bubble.
 * User messages intentionally stay plain [androidx.compose.material3.Text].
 */
@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier
) {
    val type = MaterialTheme.typography
    Markdown(
        content = text,
        typography = markdownTypography(
            text = type.bodyLarge,
            h1 = type.titleLarge,
            h2 = type.titleMedium,
            h3 = type.titleSmall,
            h4 = type.bodyLarge.copy(fontWeight = FontWeight.Bold),
            h5 = type.bodyMedium.copy(fontWeight = FontWeight.Bold),
            h6 = type.bodySmall.copy(fontWeight = FontWeight.Bold)
        ),
        modifier = modifier
    )
}
