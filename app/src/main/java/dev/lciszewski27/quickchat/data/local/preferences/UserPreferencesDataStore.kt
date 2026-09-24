package dev.lciszewski27.quickchat.data.local.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "quickchat_prefs")

/**
 * Appearance (copied from reference app) + AI provider settings.
 * API keys are stored per provider as `api_key_<providerId>`.
 * Empty string = not configured.
 */
class UserPreferencesDataStore(private val context: Context) {

    private object Keys {
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color_enabled")
        val DARK_THEME = stringPreferencesKey("dark_theme_enabled") // auto|light|dark
        val AMOLED = booleanPreferencesKey("amoled_mode_enabled")
        val ANIMATIONS = booleanPreferencesKey("animations_enabled")
        val COLOR_PRESET = stringPreferencesKey("color_preset")
        val APP_FONT = stringPreferencesKey("app_font")
        val SELECTED_PROVIDER = stringPreferencesKey("selected_provider_id")
        val SELECTED_MODEL = stringPreferencesKey("selected_model_id")
        val TEMPERATURE = floatPreferencesKey("temperature")
        val SYSTEM_PROMPT = stringPreferencesKey("system_prompt")
    }

    // ── Appearance ────────────────────────────────────────────────
    val dynamicColorEnabled: Flow<Boolean> = context.dataStore.data.map { it[Keys.DYNAMIC_COLOR] ?: true }
    val darkThemeEnabled: Flow<String> = context.dataStore.data.map { it[Keys.DARK_THEME] ?: "auto" }
    val amoledModeEnabled: Flow<Boolean> = context.dataStore.data.map { it[Keys.AMOLED] ?: false }
    val animationsEnabled: Flow<Boolean> = context.dataStore.data.map { it[Keys.ANIMATIONS] ?: true }
    val colorPreset: Flow<String> = context.dataStore.data.map { it[Keys.COLOR_PRESET] ?: "DEFAULT" }
    val appFont: Flow<String> = context.dataStore.data.map { it[Keys.APP_FONT] ?: "quicksand" }

    suspend fun setDynamicColorEnabled(v: Boolean) = context.dataStore.edit { it[Keys.DYNAMIC_COLOR] = v }
    suspend fun setDarkThemeEnabled(v: String) = context.dataStore.edit { it[Keys.DARK_THEME] = v }
    suspend fun setAmoledModeEnabled(v: Boolean) = context.dataStore.edit { it[Keys.AMOLED] = v }
    suspend fun setAnimationsEnabled(v: Boolean) = context.dataStore.edit { it[Keys.ANIMATIONS] = v }
    suspend fun setColorPreset(v: String) = context.dataStore.edit { it[Keys.COLOR_PRESET] = v }
    suspend fun setAppFont(v: String) = context.dataStore.edit { it[Keys.APP_FONT] = v }

    // ── AI ────────────────────────────────────────────────────────
    val selectedProviderId: Flow<String> = context.dataStore.data.map { it[Keys.SELECTED_PROVIDER] ?: "gemini" }
    val selectedModelId: Flow<String> = context.dataStore.data.map { it[Keys.SELECTED_MODEL] ?: "" }
    val temperature: Flow<Float> = context.dataStore.data.map { it[Keys.TEMPERATURE] ?: 0.7f }
    val systemPrompt: Flow<String> = context.dataStore.data.map { it[Keys.SYSTEM_PROMPT] ?: "" }

    suspend fun setSelectedProvider(id: String) = context.dataStore.edit { it[Keys.SELECTED_PROVIDER] = id }
    suspend fun setSelectedModel(id: String) = context.dataStore.edit { it[Keys.SELECTED_MODEL] = id }
    suspend fun setTemperature(v: Float) = context.dataStore.edit { it[Keys.TEMPERATURE] = v }
    suspend fun setSystemPrompt(v: String) = context.dataStore.edit { it[Keys.SYSTEM_PROMPT] = v }
}
