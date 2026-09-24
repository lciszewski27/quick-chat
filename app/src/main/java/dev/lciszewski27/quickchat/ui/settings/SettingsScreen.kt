package dev.lciszewski27.quickchat.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.lciszewski27.quickchat.BuildConfig
import dev.lciszewski27.quickchat.ui.settings.components.SettingsMenuItem
import dev.lciszewski27.quickchat.ui.settings.pages.AboutSettingsPage
import dev.lciszewski27.quickchat.ui.settings.pages.AppearanceSettingsPage
import dev.lciszewski27.quickchat.ui.settings.pages.GeneralSettingsPage
import dev.lciszewski27.quickchat.ui.settings.pages.ModelsSettingsPage
import dev.lciszewski27.quickchat.ui.settings.pages.ProvidersSettingsPage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    uiState: SettingsUiState,
    onEvent: (SettingsUiEvent) -> Unit,
    onEnsureModels: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var currentPage by remember { mutableStateOf(SettingsPage.MAIN) }

    BackHandler(enabled = currentPage != SettingsPage.MAIN) {
        currentPage = SettingsPage.MAIN
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = when (currentPage) {
                            SettingsPage.MAIN -> "Settings"
                            SettingsPage.APPEARANCE -> "Appearance"
                            SettingsPage.PROVIDERS -> "Providers & Keys"
                            SettingsPage.MODELS -> "Models & Favorites"
                            SettingsPage.GENERAL -> "General"
                            SettingsPage.ABOUT -> "About"
                        },
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (currentPage == SettingsPage.MAIN) onEvent(SettingsUiEvent.NavigateBack)
                        else currentPage = SettingsPage.MAIN
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        AnimatedContent(
            targetState = currentPage,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "settings_page_transition"
        ) { page ->
            Column(
                modifier = Modifier.fillMaxSize().padding(innerPadding)
                    // Keep scrolled content (e.g. searched models) reachable
                    // above the keyboard instead of hidden behind it.
                    .imePadding()
                    .verticalScroll(rememberScrollState())
            ) {
                when (page) {
                    SettingsPage.MAIN -> MainSettingsPage(onNavigate = { currentPage = it })
                    SettingsPage.APPEARANCE -> AppearanceSettingsPage(uiState, onEvent)
                    SettingsPage.PROVIDERS -> ProvidersSettingsPage(
                        uiState = uiState,
                        onEvent = onEvent
                    )
                    SettingsPage.MODELS -> ModelsSettingsPage(
                        uiState = uiState,
                        onEvent = onEvent,
                        onEnsureModels = onEnsureModels
                    )
                    SettingsPage.GENERAL -> GeneralSettingsPage(uiState, onEvent)
                    SettingsPage.ABOUT -> AboutSettingsPage()
                }
            }
        }
    }
}

@Composable
private fun MainSettingsPage(onNavigate: (SettingsPage) -> Unit) {
    val groupedSettings = remember {
        listOf(
            SettingsGroup(
                items = listOf(
                    SettingsItem(
                        "Providers & Keys",
                        "Google AI Studio + compatible APIs",
                        Icons.Filled.Key,
                        SettingsPage.PROVIDERS
                    ),
                    SettingsItem(
                        "Models & Favorites",
                        "Fetch catalog, star favorites",
                        Icons.Filled.SmartToy,
                        SettingsPage.MODELS
                    ),
                    SettingsItem(
                        "General",
                        "Temperature and system prompt",
                        Icons.Filled.Tune,
                        SettingsPage.GENERAL
                    ),
                    SettingsItem(
                        "Appearance",
                        "Theme, colors, and animations",
                        Icons.Filled.Palette,
                        SettingsPage.APPEARANCE
                    )
                )
            ),
            SettingsGroup(
                items = listOf(
                    SettingsItem(
                        "About",
                        "App info and provider help",
                        Icons.Filled.Favorite,
                        SettingsPage.ABOUT
                    )
                )
            )
        )
    }

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        groupedSettings.forEach { group ->
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                group.items.forEachIndexed { index, item ->
                    SettingsMenuItem(
                        title = item.title,
                        icon = item.icon,
                        subtitle = item.subtitle,
                        onClick = { onNavigate(item.page) },
                        index = index,
                        count = group.items.size
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = "QuickChat v${BuildConfig.VERSION_NAME}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(horizontal = 12.dp)
        )
    }
}
