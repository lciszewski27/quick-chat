package dev.lciszewski27.quickchat.ui.settings.pages

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Animation
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.lciszewski27.quickchat.ui.settings.AppFont
import dev.lciszewski27.quickchat.ui.settings.ColorPreset
import dev.lciszewski27.quickchat.ui.settings.SettingsUiEvent
import dev.lciszewski27.quickchat.ui.settings.SettingsUiState
import dev.lciszewski27.quickchat.ui.settings.ThemeMode
import dev.lciszewski27.quickchat.ui.settings.components.SettingsToggle

/**
 * Appearance page — same structure/roles as the reference app:
 * Material You toggle, color presets, theme mode, AMOLED, animations, font.
 */
@Composable
internal fun AppearanceSettingsPage(
    uiState: SettingsUiState,
    onEvent: (SettingsUiEvent) -> Unit
) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Theme", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        SettingsToggle(
            title = "Material You",
            subtitle = "Dynamic colors from wallpaper",
            icon = Icons.Filled.Palette,
            checked = uiState.dynamicColorEnabled,
            index = 0,
            count = 3,
            onCheckedChange = { onEvent(SettingsUiEvent.ToggleDynamicColor(it)) }
        )

        if (!uiState.dynamicColorEnabled) {
            SegmentedListItem(
                shapes = ListItemDefaults.segmentedShapes(index = 1, count = 3),
                colors = ListItemDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ),
                leadingContent = { Text("Color Presets") },
                content = {
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .clickable { onEvent(SettingsUiEvent.TogglePresetDropdown) },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Spacer(modifier = Modifier.weight(1f))
                        Box {
                            Text(
                                text = uiState.colorPreset.displayName,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            DropdownMenu(
                                expanded = uiState.isPresetDropdownExpanded,
                                onDismissRequest = { onEvent(SettingsUiEvent.DismissPresetDropdown) }
                            ) {
                                ColorPreset.entries.forEach { preset ->
                                    DropdownMenuItem(
                                        onClick = {
                                            onEvent(SettingsUiEvent.SetColorPreset(preset))
                                            onEvent(SettingsUiEvent.DismissPresetDropdown)
                                        },
                                        text = { Text(preset.displayName) }
                                    )
                                }
                            }
                        }
                    }
                }
            )
        }

        SegmentedListItem(
            shapes = ListItemDefaults.segmentedShapes(index = 2, count = 3),
            colors = ListItemDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            ),
            leadingContent = { Text("Theme Mode") },
            content = {
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .clickable { onEvent(SettingsUiEvent.ToggleThemeDropdown) },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Spacer(modifier = Modifier.weight(1f))
                    Box {
                        Text(
                            text = uiState.darkThemeMode.displayName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Bold
                        )
                        DropdownMenu(
                            expanded = uiState.isThemeDropdownExpanded,
                            onDismissRequest = { onEvent(SettingsUiEvent.DismissThemeDropdown) }
                        ) {
                            ThemeMode.entries.forEach { mode ->
                                DropdownMenuItem(
                                    onClick = {
                                        onEvent(SettingsUiEvent.SetThemeMode(mode))
                                        onEvent(SettingsUiEvent.DismissThemeDropdown)
                                    },
                                    text = {
                                        Text(
                                            text = mode.displayName,
                                            fontWeight = if (uiState.darkThemeMode == mode) FontWeight.Bold else FontWeight.Normal
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            }
        )

        Text(
            "Other",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 16.dp)
        )

        val otherItemsCount = if (uiState.darkThemeMode != ThemeMode.LIGHT) 3 else 2
        var currentIndex = 0

        if (uiState.darkThemeMode != ThemeMode.LIGHT) {
            SettingsToggle(
                title = "AMOLED Pure Black",
                subtitle = "Save battery with true black",
                icon = Icons.Filled.DarkMode,
                checked = uiState.amoledModeEnabled,
                index = currentIndex++,
                count = otherItemsCount,
                onCheckedChange = { onEvent(SettingsUiEvent.ToggleAmoledMode(it)) }
            )
        }

        SettingsToggle(
            title = "Enable Animations",
            subtitle = "Smooth transitions between screens",
            icon = Icons.Filled.Animation,
            checked = uiState.animationsEnabled,
            index = currentIndex++,
            count = otherItemsCount,
            onCheckedChange = { onEvent(SettingsUiEvent.ToggleAnimations(it)) }
        )

        SegmentedListItem(
            shapes = ListItemDefaults.segmentedShapes(index = currentIndex++, count = otherItemsCount),
            colors = ListItemDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            ),
            leadingContent = { Text("Font Family") },
            content = {
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .clickable { onEvent(SettingsUiEvent.ToggleFontDropdown) },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Spacer(modifier = Modifier.weight(1f))
                    Box {
                        Text(
                            text = uiState.appFont.displayName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Bold
                        )
                        DropdownMenu(
                            expanded = uiState.isFontDropdownExpanded,
                            onDismissRequest = { onEvent(SettingsUiEvent.DismissFontDropdown) }
                        ) {
                            AppFont.entries.forEach { font ->
                                DropdownMenuItem(
                                    onClick = {
                                        onEvent(SettingsUiEvent.SetAppFont(font))
                                        onEvent(SettingsUiEvent.DismissFontDropdown)
                                    },
                                    text = {
                                        Text(
                                            text = font.displayName,
                                            fontWeight = if (uiState.appFont == font) FontWeight.Bold else FontWeight.Normal
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            }
        )
    }
}
