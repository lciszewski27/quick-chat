package dev.lciszewski27.quickchat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import dev.lciszewski27.quickchat.ui.navigation.AppNavHost
import dev.lciszewski27.quickchat.ui.settings.ColorPreset
import dev.lciszewski27.quickchat.ui.theme.QuickChatTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as QuickChatApp
        setContent {
            val dynamicColor by app.preferences.dynamicColorEnabled.collectAsState(initial = true)
            val darkMode by app.preferences.darkThemeEnabled.collectAsState(initial = "auto")
            val amoled by app.preferences.amoledModeEnabled.collectAsState(initial = false)
            val animations by app.preferences.animationsEnabled.collectAsState(initial = true)
            val presetStr by app.preferences.colorPreset.collectAsState(initial = "DEFAULT")
            val fontStr by app.preferences.appFont.collectAsState(initial = "quicksand")
            val preset = try { ColorPreset.valueOf(presetStr) } catch (e: Exception) { ColorPreset.DEFAULT }

            val isDark = when (darkMode) {
                "light" -> false
                "dark" -> true
                else -> isSystemInDarkTheme()
            }

            QuickChatTheme(
                darkTheme = isDark,
                dynamicColor = dynamicColor,
                amoledMode = amoled,
                animationsEnabled = animations,
                colorPreset = preset,
                useSystemFont = fontStr == "system"
            ) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppNavHost()
                }
            }
        }
    }
}
