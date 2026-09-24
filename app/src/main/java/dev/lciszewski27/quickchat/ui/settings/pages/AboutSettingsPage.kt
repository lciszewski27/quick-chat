package dev.lciszewski27.quickchat.ui.settings.pages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.lciszewski27.quickchat.BuildConfig

@Composable
internal fun AboutSettingsPage() {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("QuickChat", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Private, Material You chat for multiple AI providers. History stays on-device; only your messages go to the selected provider.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            "Replies stream in live over one shared client: Google AI Studio, OpenAI, " +
                "Claude (Anthropic), OpenRouter and DeepSeek. The assistant can use " +
                "built-in tools (current time, calculator, sandboxed JavaScript) " +
                "when an answer needs them. " +
                "Bring your own keys — history stays on-device; only your messages go " +
                "to the selected provider.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            "v${BuildConfig.VERSION_NAME}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.outline
        )
    }
}
