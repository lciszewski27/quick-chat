package dev.lciszewski27.quickchat.ui.settings.pages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.lciszewski27.quickchat.domain.model.AiModelInfo
import dev.lciszewski27.quickchat.ui.settings.SettingsUiEvent
import dev.lciszewski27.quickchat.ui.settings.SettingsUiState

/**
 * Models & favorites, driven by the user's added providers.
 * Fetch the live catalog, search it (catalogs like OpenRouter's are huge),
 * star entries into favorites — the chat model picker shows favorites only.
 */
@Composable
internal fun ModelsSettingsPage(
    uiState: SettingsUiState,
    onEvent: (SettingsUiEvent) -> Unit,
    onEnsureModels: (String) -> Unit
) {
    if (uiState.providers.isEmpty()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Models & favorites", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            Text(
                "Add a provider first — then fetch its catalog here and star favorites for the chat picker.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    val defaultFilter = uiState.providers
        .find { it.instanceId == uiState.selectedProviderId }?.instanceId
        ?: uiState.providers.first().instanceId
    var providerFilter by remember(defaultFilter) { mutableStateOf(defaultFilter) }
    // If the selected instance disappears (deleted), follow the first one.
    LaunchedEffect(uiState.providers.map { it.instanceId }) {
        if (uiState.providers.none { it.instanceId == providerFilter }) {
            providerFilter = uiState.providers.firstOrNull()?.instanceId.orEmpty()
        }
    }
    LaunchedEffect(providerFilter) {
        if (providerFilter.isNotBlank()) onEnsureModels(providerFilter)
    }
    var query by remember { mutableStateOf("") }

    val instance = uiState.providers.find { it.instanceId == providerFilter }
    val favIds = uiState.favorites.filter { it.providerId == providerFilter }.map { it.modelId }.toSet()
    val fetched = if (uiState.fetchingProviderId == providerFilter) uiState.fetchedModels else emptyList()
    val visible = if (query.isBlank()) fetched else fetched.filter {
        it.id.contains(query, ignoreCase = true) || it.displayName.contains(query, ignoreCase = true)
    }
    val instanceFavs = uiState.favorites.filter { it.providerId == providerFilter }

    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Provider", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            uiState.providers.forEach { p ->
                FilterChip(
                    selected = providerFilter == p.instanceId,
                    onClick = { providerFilter = p.instanceId },
                    label = { Text(p.label) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
            Column(Modifier.weight(1f)) {
                Text("Catalog", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                Text(
                    when {
                        instance == null -> ""
                        instance.apiKey.isBlank() && !instance.isCustom ->
                            "No API key — add one in Providers & Keys, then fetch."
                        fetched.isNotEmpty() -> "${fetched.size} models from ${instance.label}."
                        else -> "Fetched live from ${instance.label}."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            FilledTonalButton(
                onClick = { instance?.let { onEvent(SettingsUiEvent.FetchModels(it.instanceId)) } },
                enabled = !uiState.isFetchingModels && instance != null
            ) {
                if (uiState.isFetchingModels) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(8.dp))
                Text("Fetch")
            }
        }

        uiState.fetchError?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        if (instanceFavs.isNotEmpty()) {
            Text(
                "Favorites (${instanceFavs.size})",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 8.dp)
            )
            instanceFavs.forEach { fav ->
                ModelCatalogRow(
                    model = AiModelInfo(fav.providerId, fav.modelId, fav.displayName, null),
                    isFavorite = true,
                    isActive = fav.providerId == uiState.selectedProviderId && fav.modelId == uiState.selectedModelId,
                    onToggleFavorite = {
                        onEvent(SettingsUiEvent.ToggleFavorite(fav.providerId, AiModelInfo(fav.providerId, fav.modelId, fav.displayName, null)))
                    },
                    onSelect = { onEvent(SettingsUiEvent.SelectModel(fav.providerId, fav.modelId)) }
                )
            }
        }

        Text(
            "All models (${visible.size}" + (if (query.isNotBlank()) " of ${fetched.size}" else "") + ") — tap ★ for the chat picker",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary
        )
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Search models…") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { query = "" }) {
                        Icon(Icons.Filled.Clear, contentDescription = "Clear search")
                    }
                }
            },
            singleLine = true,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth()
        )
        if (visible.isEmpty() && !uiState.isFetchingModels) {
            Text(
                if (fetched.isEmpty()) "Nothing here yet. Press Fetch."
                else "No matches for “$query”.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            visible.forEach { model ->
                ModelCatalogRow(
                    model = model,
                    isFavorite = favIds.contains(model.id),
                    isActive = model.providerId == uiState.selectedProviderId && model.id == uiState.selectedModelId,
                    onToggleFavorite = { onEvent(SettingsUiEvent.ToggleFavorite(model.providerId, model)) },
                    onSelect = { onEvent(SettingsUiEvent.SelectModel(model.providerId, model.id)) }
                )
            }
        }
    }
}

@Composable
private fun ModelCatalogRow(
    model: AiModelInfo,
    isFavorite: Boolean,
    isActive: Boolean,
    onToggleFavorite: () -> Unit,
    onSelect: () -> Unit
) {
    SegmentedListItem(
        shapes = ListItemDefaults.segmentedShapes(index = 0, count = 1),
        colors = ListItemDefaults.colors(
            containerColor = if (isActive) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        leadingContent = {
            IconButton(onClick = onToggleFavorite) {
                Icon(
                    if (isFavorite) Icons.Filled.Star else Icons.Outlined.StarOutline,
                    contentDescription = if (isFavorite) "Remove from favorites" else "Add to favorites",
                    tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        content = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        model.displayName.ifBlank { model.id },
                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        model.id,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (isActive) {
                        Text(
                            "Active model",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                TextButton(onClick = onSelect) {
                    Text(if (isActive) "Active" else "Use")
                }
            }
        }
    )
}
