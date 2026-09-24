package dev.lciszewski27.quickchat.ui.settings

import androidx.compose.ui.graphics.vector.ImageVector

internal enum class SettingsPage {
    MAIN, APPEARANCE, PROVIDERS, MODELS, GENERAL, ABOUT
}

internal data class SettingsGroup(val items: List<SettingsItem>)

internal data class SettingsItem(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val page: SettingsPage
)
