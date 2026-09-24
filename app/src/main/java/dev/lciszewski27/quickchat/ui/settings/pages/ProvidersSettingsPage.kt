package dev.lciszewski27.quickchat.ui.settings.pages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import dev.lciszewski27.quickchat.data.ai.Providers
import dev.lciszewski27.quickchat.ui.settings.ProviderInstanceUi
import dev.lciszewski27.quickchat.ui.settings.SettingsUiEvent
import dev.lciszewski27.quickchat.ui.settings.SettingsUiState

/**
 * Your providers. Add built-in backends or any number of custom
 * OpenAI-compatible endpoints (Ollama, LM Studio, proxies, …).
 */
@Composable
internal fun ProvidersSettingsPage(
    uiState: SettingsUiState,
    onEvent: (SettingsUiEvent) -> Unit
) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Providers",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
            FilledTonalButton(onClick = { onEvent(SettingsUiEvent.ShowAddProvider) }) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Add")
            }
        }

        if (uiState.providers.isEmpty()) {
            Text(
                "No providers yet. Add Google AI Studio, OpenAI, Claude, OpenRouter — " +
                    "or your own OpenAI-compatible endpoint.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        } else {
            uiState.providers.forEach { provider ->
                ProviderCard(
                    item = provider,
                    aiCoreStatus = if (provider.type == Providers.aicore.id) uiState.aiCoreStatus else null,
                    onEvent = onEvent
                )
            }
        }
    }

    if (uiState.showAddProvider) {
        AddProviderDialog(
            addedTypes = uiState.providers.filterNot { it.isCustom }.map { it.type }.toSet(),
            aiCoreStatus = uiState.aiCoreStatus,
            onDismiss = { onEvent(SettingsUiEvent.DismissAddProvider) },
            onAdd = { type, label, baseUrl, apiKey ->
                onEvent(SettingsUiEvent.AddProvider(type, label, baseUrl, apiKey))
            }
        )
    }

    uiState.editingProviderId?.let { id ->
        uiState.providers.find { it.instanceId == id }?.let { item ->
            EditProviderDialog(
                item = item,
                onDismiss = { onEvent(SettingsUiEvent.DismissEditProvider) },
                onSave = { label, baseUrl ->
                    onEvent(SettingsUiEvent.UpdateProvider(id, label, baseUrl))
                }
            )
        }
    }

    uiState.confirmDeleteProviderId?.let { id ->
        val label = uiState.providers.find { it.instanceId == id }?.label ?: "this provider"
        AlertDialog(
            onDismissRequest = { onEvent(SettingsUiEvent.DismissDeleteProvider) },
            title = { Text("Remove provider?") },
            text = { Text("“$label” will be removed with its favorites and chats. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = { onEvent(SettingsUiEvent.ConfirmDeleteProvider) }) {
                    Text("Remove", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { onEvent(SettingsUiEvent.DismissDeleteProvider) }) { Text("Cancel") } },
            shape = MaterialTheme.shapes.extraLarge
        )
    }
}

@Composable
private fun ProviderCard(
    item: ProviderInstanceUi,
    onEvent: (SettingsUiEvent) -> Unit,
    aiCoreStatus: dev.lciszewski27.quickchat.data.ai.AiCoreStatus? = null
) {
    val uriHandler = LocalUriHandler.current
    val preset = remember(item.type) { Providers.preset(item.type) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        // Row 0: select + identity + manage
        SegmentedListItem(
            shapes = ListItemDefaults.segmentedShapes(index = 0, count = 3),
            colors = ListItemDefaults.colors(
                containerColor = if (item.isSelected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceContainerHigh
            ),
            leadingContent = {
                Column {
                    Text(item.label, fontWeight = FontWeight.SemiBold)
                    Text(
                        buildString {
                            append(item.typeDisplay)
                            if (item.isCustom && item.baseUrl.isNotBlank()) append(" • ${item.baseUrl}")
                            if (!item.requiresKey) append(" • on-device")
                            else if (item.apiKey.isBlank()) append(" • no key") else append(" • key saved")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            content = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (item.isSelected) {
                        Icon(Icons.Filled.Check, contentDescription = "Active provider", tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(4.dp))
                    }
                    RadioButton(
                        selected = item.isSelected,
                        onClick = { onEvent(SettingsUiEvent.SelectProvider(item.instanceId)) }
                    )
                    IconButton(onClick = { onEvent(SettingsUiEvent.ShowEditProvider(item.instanceId)) }) {
                        Icon(Icons.Filled.Edit, contentDescription = "Edit provider")
                    }
                    IconButton(onClick = { onEvent(SettingsUiEvent.RequestDeleteProvider(item.instanceId)) }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Remove provider")
                    }
                }
            }
        )
        // Row 1: API key — or on-device model status for keyless backends.
        if (!item.requiresKey) {
            AiCoreStatusRow(
                status = aiCoreStatus,
                onDownload = { onEvent(SettingsUiEvent.DownloadAiCoreModel) },
                onRetry = { onEvent(SettingsUiEvent.CheckAiCoreStatus) }
            )
        } else {
            SegmentedListItem(
                shapes = ListItemDefaults.segmentedShapes(index = 1, count = 3),
                colors = ListItemDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ),
                leadingContent = { Text("API key") },
                content = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = item.apiKey,
                            onValueChange = { onEvent(SettingsUiEvent.SetProviderApiKey(item.instanceId, it)) },
                            placeholder = { Text(preset?.apiKeyHint.orEmpty()) },
                            singleLine = true,
                            shape = MaterialTheme.shapes.medium,
                            visualTransformation = if (item.apiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            trailingIcon = {
                                IconButton(onClick = { onEvent(SettingsUiEvent.ToggleApiKeyVisibility(item.instanceId)) }) {
                                    Icon(
                                        if (item.apiKeyVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                        contentDescription = if (item.apiKeyVisible) "Hide key" else "Show key"
                                    )
                                }
                            },
                            leadingIcon = { Icon(Icons.Filled.Key, contentDescription = null) },
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (!preset?.helpUrl.isNullOrBlank()) {
                            TextButton(onClick = { uriHandler.openUri(preset!!.helpUrl) }) { Text("Get an API key") }
                        }
                    }
                }
            )
        }
        // Row 2: fetch + use
        SegmentedListItem(
            shapes = ListItemDefaults.segmentedShapes(index = 2, count = 3),
            colors = ListItemDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            ),
            leadingContent = { Text("Catalog") },
            content = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Spacer(Modifier.weight(1f))
                    FilledTonalButton(onClick = { onEvent(SettingsUiEvent.FetchModels(item.instanceId)) }) {
                        Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Fetch models")
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { onEvent(SettingsUiEvent.SelectProvider(item.instanceId)) }) {
                        Text("Use")
                    }
                }
            }
        )
    }
}

/**
 * On-device model state in place of the API-key row: status text plus
 * Download / Retry actions and indeterminate progress while downloading
 * (AICore doesn't report a total size, so no percentage is shown).
 */
@Composable
private fun AiCoreStatusRow(
    status: dev.lciszewski27.quickchat.data.ai.AiCoreStatus?,
    onDownload: () -> Unit,
    onRetry: () -> Unit
) {
    SegmentedListItem(
        shapes = ListItemDefaults.segmentedShapes(index = 1, count = 3),
        colors = ListItemDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        leadingContent = { Text("On-device model") },
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val message = when (status) {
                    null, dev.lciszewski27.quickchat.data.ai.AiCoreStatus.Checking ->
                        "Checking device support…"
                    dev.lciszewski27.quickchat.data.ai.AiCoreStatus.Unsupported ->
                        "Not supported on this device."
                    dev.lciszewski27.quickchat.data.ai.AiCoreStatus.Downloadable ->
                        "Supported — model download needed (one-time, large)."
                    is dev.lciszewski27.quickchat.data.ai.AiCoreStatus.Downloading ->
                        "Downloading on-device model…"
                    dev.lciszewski27.quickchat.data.ai.AiCoreStatus.Ready ->
                        "Ready — private, works offline."
                    is dev.lciszewski27.quickchat.data.ai.AiCoreStatus.Failed ->
                        status.message
                }
                Text(
                    message,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (status is dev.lciszewski27.quickchat.data.ai.AiCoreStatus.Failed)
                        MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (status is dev.lciszewski27.quickchat.data.ai.AiCoreStatus.Downloading) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                Row {
                    Spacer(Modifier.weight(1f))
                    when (status) {
                        dev.lciszewski27.quickchat.data.ai.AiCoreStatus.Downloadable,
                        is dev.lciszewski27.quickchat.data.ai.AiCoreStatus.Failed -> {
                            FilledTonalButton(onClick = onDownload) {
                                Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Download")
                            }
                        }
                        dev.lciszewski27.quickchat.data.ai.AiCoreStatus.Unsupported,
                        dev.lciszewski27.quickchat.data.ai.AiCoreStatus.Checking,
                        null -> {
                            TextButton(onClick = onRetry) { Text("Recheck") }
                        }
                        else -> Unit
                    }
                }
            }
        }
    )
}

@Composable
private fun AddProviderDialog(
    addedTypes: Set<String>,
    aiCoreStatus: dev.lciszewski27.quickchat.data.ai.AiCoreStatus,
    onDismiss: () -> Unit,
    onAdd: (type: String, label: String, baseUrl: String, apiKey: String) -> Unit
) {
    var type by remember { mutableStateOf(Providers.gemini.id) }
    var label by remember { mutableStateOf("") }
    var baseUrl by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf("") }
    val preset = remember(type) { Providers.preset(type) ?: Providers.custom }
    val isCustom = type == Providers.custom.id
    val alreadyAdded = !isCustom && addedTypes.contains(type)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add provider") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Type", style = MaterialTheme.typography.labelLarge)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Providers.presetTypes().forEach { p ->
                        val aicoreUnsupported = p.id == Providers.aicore.id &&
                            aiCoreStatus == dev.lciszewski27.quickchat.data.ai.AiCoreStatus.Unsupported
                        FilterChip(
                            selected = type == p.id,
                            onClick = { type = p.id },
                            enabled = !aicoreUnsupported,
                            label = {
                                Text(
                                    p.displayName +
                                        if (p.id != Providers.custom.id && addedTypes.contains(p.id)) " (added)" else "" +
                                        if (aicoreUnsupported) " (not supported on this device)" else ""
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text(if (isCustom) "Name (required)" else "Name (optional)") },
                    placeholder = { Text(preset.displayName) },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                )
                if (isCustom) {
                    OutlinedTextField(
                        value = baseUrl,
                        onValueChange = { baseUrl = it },
                        label = { Text("Base URL") },
                        placeholder = { Text("http://192.168.1.10:11434/v1") },
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (type == Providers.aicore.id) {
                    Text(
                        "Runs on-device — no key needed. Download the model after adding.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it },
                        label = { Text(if (isCustom) "API key (optional for local servers)" else "API key") },
                        placeholder = { Text(preset.apiKeyHint) },
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (alreadyAdded) {
                    Text(
                        "Already added — adding again replaces it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onAdd(type, label, baseUrl, apiKey) },
                enabled = !(isCustom && (label.isBlank() || baseUrl.isBlank()))
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        shape = MaterialTheme.shapes.extraLarge
    )
}

@Composable
private fun EditProviderDialog(
    item: ProviderInstanceUi,
    onDismiss: () -> Unit,
    onSave: (label: String, baseUrl: String) -> Unit
) {
    var label by remember(item.instanceId) { mutableStateOf(item.label) }
    var baseUrl by remember(item.instanceId) { mutableStateOf(if (item.isCustom) item.baseUrl else "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit provider") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Name") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                )
                if (item.isCustom) {
                    OutlinedTextField(
                        value = baseUrl,
                        onValueChange = { baseUrl = it },
                        label = { Text("Base URL") },
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    Text(
                        "${item.typeDisplay} • ${item.baseUrl}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(label, baseUrl) }, enabled = label.isNotBlank()) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        shape = MaterialTheme.shapes.extraLarge
    )
}
