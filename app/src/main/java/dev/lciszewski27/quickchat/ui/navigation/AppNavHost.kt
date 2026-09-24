package dev.lciszewski27.quickchat.ui.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dev.lciszewski27.quickchat.QuickChatApp
import dev.lciszewski27.quickchat.ui.chat.ChatScreen
import dev.lciszewski27.quickchat.ui.chat.ChatViewModel
import dev.lciszewski27.quickchat.ui.settings.SettingsScreen
import dev.lciszewski27.quickchat.ui.settings.SettingsUiEvent
import dev.lciszewski27.quickchat.ui.settings.SettingsViewModel
import dev.lciszewski27.quickchat.ui.theme.LocalAnimationsEnabled

@Composable
fun AppNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController()
) {
    val context = LocalContext.current
    val app = context.applicationContext as QuickChatApp
    val animationsEnabled = LocalAnimationsEnabled.current

    NavHost(
        navController = navController,
        startDestination = Route.Chat,
        modifier = modifier.fillMaxSize(),
        enterTransition = {
            if (animationsEnabled) slideInHorizontally(spring(0.7f, 300f)) { it } + fadeIn(spring()) else EnterTransition.None
        },
        exitTransition = {
            if (animationsEnabled) slideOutHorizontally(spring(0.7f, 300f)) { -it / 3 } + fadeOut(spring()) else ExitTransition.None
        },
        popEnterTransition = {
            if (animationsEnabled) slideInHorizontally(spring(0.7f, 300f)) { -it / 3 } + fadeIn(spring()) else EnterTransition.None
        },
        popExitTransition = {
            if (animationsEnabled) slideOutHorizontally(spring(0.7f, 300f)) { it } + fadeOut(spring()) else ExitTransition.None
        }
    ) {
        composable<Route.Chat> {
            val vm: ChatViewModel = viewModel(
                factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                        return ChatViewModel(app.repository, app.preferences, app.providerResolver) as T
                    }
                }
            )
            val state by vm.uiState.collectAsState()
            ChatScreen(
                state = state,
                onEvent = vm::onEvent,
                onOpenSettings = { navController.navigate(Route.Settings) }
            )
        }
        composable<Route.Settings> {
            val vm: SettingsViewModel = viewModel(
                factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                        return SettingsViewModel(app.preferences, app.repository, app.providerResolver) as T
                    }
                }
            )
            val state by vm.uiState.collectAsState()
            SettingsScreen(
                uiState = state,
                onEvent = { event ->
                    when (event) {
                        SettingsUiEvent.NavigateBack -> navController.popBackStack()
                        else -> vm.onEvent(event)
                    }
                },
                onEnsureModels = vm::ensureFetched
            )
        }
    }
}
