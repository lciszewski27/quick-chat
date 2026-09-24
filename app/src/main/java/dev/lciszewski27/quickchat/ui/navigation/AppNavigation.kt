package dev.lciszewski27.quickchat.ui.navigation

import kotlinx.serialization.Serializable

sealed interface Route {
    @Serializable
    data object Chat : Route

    @Serializable
    data object Settings : Route
}
