package dev.lciszewski27.quickchat.ui.settings

import dev.lciszewski27.quickchat.domain.model.AiModelInfo
import dev.lciszewski27.quickchat.domain.model.FavoriteModel
import dev.lciszewski27.quickchat.domain.model.SecretDecl
import dev.lciszewski27.quickchat.domain.model.Skill

data class SettingsUiState(
    // Appearance (copied from reference)
    val dynamicColorEnabled: Boolean = true,
    val darkThemeMode: ThemeMode = ThemeMode.AUTO,
    val amoledModeEnabled: Boolean = false,
    val animationsEnabled: Boolean = true,
    val colorPreset: ColorPreset = ColorPreset.DEFAULT,
    val appFont: AppFont = AppFont.QUICKSAND,
    val isPresetDropdownExpanded: Boolean = false,
    val isThemeDropdownExpanded: Boolean = false,
    val isFontDropdownExpanded: Boolean = false,
    // AI — user-added provider instances
    val providers: List<ProviderInstanceUi> = emptyList(),
    val selectedProviderId: String = "",
    val selectedModelId: String = "",
    val favorites: List<FavoriteModel> = emptyList(),
    val fetchedModels: List<AiModelInfo> = emptyList(),
    val isFetchingModels: Boolean = false,
    val fetchError: String? = null,
    val fetchingProviderId: String? = null,
    val showAddProvider: Boolean = false,
    val editingProviderId: String? = null,
    val confirmDeleteProviderId: String? = null,
    // Skills
    val skills: List<Skill> = emptyList(),
    val showNewSkill: Boolean = false,
    val editingSkillId: String? = null,
    val confirmDeleteSkillId: String? = null,
    val testingSkillId: String? = null,
    val testArgs: String = "{}",
    val testRunning: Boolean = false,
    val testResult: String? = null,
    val secretsSkillId: String? = null,
    val secretValues: Map<String, String> = emptyMap(),
    val temperature: Float = 0.7f,
    val systemPrompt: String = ""
)

/** One row in Providers & Keys — a user-added backend instance. */
data class ProviderInstanceUi(
    val instanceId: String,
    val type: String,
    val typeDisplay: String,
    val label: String,
    val baseUrl: String,
    val apiKey: String = "",
    val apiKeyVisible: Boolean = false,
    val isSelected: Boolean = false,
    val isCustom: Boolean = false
)

enum class AppFont(val displayName: String, val value: String) {
    QUICKSAND("Quicksand", "quicksand"),
    SYSTEM("System", "system")
}

enum class ThemeMode(val displayName: String, val value: String) {
    AUTO("System", "auto"),
    LIGHT("Light", "light"),
    DARK("Dark", "dark")
}

enum class ColorPreset(val displayName: String) {
    DEFAULT("Default"),
    CHAT_TEAL("Chat Teal"),
    OCEAN_BLUE("Ocean Blue"),
    ROYAL_PURPLE("Royal Purple"),
    CHARCOAL("Charcoal")
}

sealed interface SettingsUiEvent {
    data object NavigateBack : SettingsUiEvent
    // Appearance
    data class ToggleDynamicColor(val enabled: Boolean) : SettingsUiEvent
    data class SetThemeMode(val mode: ThemeMode) : SettingsUiEvent
    data class ToggleAmoledMode(val enabled: Boolean) : SettingsUiEvent
    data class ToggleAnimations(val enabled: Boolean) : SettingsUiEvent
    data class SetColorPreset(val preset: ColorPreset) : SettingsUiEvent
    data class SetAppFont(val font: AppFont) : SettingsUiEvent
    data object TogglePresetDropdown : SettingsUiEvent
    data object DismissPresetDropdown : SettingsUiEvent
    data object ToggleThemeDropdown : SettingsUiEvent
    data object DismissThemeDropdown : SettingsUiEvent
    data object ToggleFontDropdown : SettingsUiEvent
    data object DismissFontDropdown : SettingsUiEvent
    // Provider instances
    data object ShowAddProvider : SettingsUiEvent
    data object DismissAddProvider : SettingsUiEvent
    data class AddProvider(val type: String, val label: String, val baseUrl: String, val apiKey: String) : SettingsUiEvent
    data class SetProviderApiKey(val instanceId: String, val apiKey: String) : SettingsUiEvent
    data class ToggleApiKeyVisibility(val instanceId: String) : SettingsUiEvent
    data class UpdateProvider(val instanceId: String, val label: String, val baseUrl: String) : SettingsUiEvent
    data class ShowEditProvider(val instanceId: String) : SettingsUiEvent
    data object DismissEditProvider : SettingsUiEvent
    data class RequestDeleteProvider(val instanceId: String) : SettingsUiEvent
    data object DismissDeleteProvider : SettingsUiEvent
    data object ConfirmDeleteProvider : SettingsUiEvent
    data class SelectProvider(val instanceId: String) : SettingsUiEvent
    data class FetchModels(val instanceId: String) : SettingsUiEvent
    data class ToggleFavorite(val instanceId: String, val model: AiModelInfo) : SettingsUiEvent
    data class SelectModel(val instanceId: String, val modelId: String) : SettingsUiEvent
    // Skills
    data object ShowNewSkill : SettingsUiEvent
    data class ShowEditSkill(val skillId: String) : SettingsUiEvent
    data object DismissSkillEditor : SettingsUiEvent
    data class SaveSkill(
        val skillId: String?,
        val name: String,
        val description: String,
        val paramsSchema: String,
        val code: String
    ) : SettingsUiEvent
    data class RequestDeleteSkill(val skillId: String) : SettingsUiEvent
    data object DismissDeleteSkill : SettingsUiEvent
    data object ConfirmDeleteSkill : SettingsUiEvent
    data class ToggleSkillEnabled(val skillId: String, val enabled: Boolean) : SettingsUiEvent
    data class ShowSkillTest(val skillId: String) : SettingsUiEvent
    data object DismissSkillTest : SettingsUiEvent
    data class SetSkillTestArgs(val args: String) : SettingsUiEvent
    data object RunSkillTest : SettingsUiEvent
    data class ShowSkillSecrets(val skillId: String) : SettingsUiEvent
    data object DismissSkillSecrets : SettingsUiEvent
    data class SaveSkillSecrets(
        val skillId: String,
        val decls: List<SecretDecl>,
        val values: Map<String, String>
    ) : SettingsUiEvent
    // General
    data class SetTemperature(val value: Float) : SettingsUiEvent
    data class SetSystemPrompt(val value: String) : SettingsUiEvent
}
