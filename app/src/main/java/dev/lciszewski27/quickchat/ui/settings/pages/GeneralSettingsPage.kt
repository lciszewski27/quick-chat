package dev.lciszewski27.quickchat.ui.settings.pages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.lciszewski27.quickchat.ui.settings.SettingsUiEvent
import dev.lciszewski27.quickchat.ui.settings.SettingsUiState

@Composable
internal fun GeneralSettingsPage(
    uiState: SettingsUiState,
    onEvent: (SettingsUiEvent) -> Unit
) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Generation", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        SegmentedListItem(
            shapes = ListItemDefaults.segmentedShapes(index = 0, count = 2),
            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            leadingContent = { Text("Creativity") },
            content = {
                Column {
                    Slider(
                        value = uiState.temperature,
                        onValueChange = { onEvent(SettingsUiEvent.SetTemperature(it)) },
                        valueRange = 0f..1.5f,
                        steps = 14,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        "${String.format("%.2f", uiState.temperature)} — lower is factual, higher is creative.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        )
        SegmentedListItem(
            shapes = ListItemDefaults.segmentedShapes(index = 1, count = 2),
            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            leadingContent = { Text("System prompt") },
            content = {
                OutlinedTextField(
                    value = uiState.systemPrompt,
                    onValueChange = { onEvent(SettingsUiEvent.SetSystemPrompt(it)) },
                    placeholder = { Text("e.g. Answer concisely…") },
                    shape = MaterialTheme.shapes.medium,
                    minLines = 2,
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        )

        Text(
            "Active model",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 16.dp)
        )
        SegmentedListItem(
            shapes = ListItemDefaults.segmentedShapes(index = 0, count = 1),
            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            leadingContent = { Text("Model") },
            content = {
                Column {
                    Text(
                        uiState.selectedModelId.ifBlank { "Not selected — star one in Models & Favorites" },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        uiState.providers.find { it.instanceId == uiState.selectedProviderId }?.label.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        )
    }
}
