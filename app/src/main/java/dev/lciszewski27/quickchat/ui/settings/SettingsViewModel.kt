package dev.lciszewski27.quickchat.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.lciszewski27.quickchat.data.ai.AiCoreProvider
import dev.lciszewski27.quickchat.data.ai.AiCoreStatus
import dev.lciszewski27.quickchat.data.ai.ProviderResolver
import dev.lciszewski27.quickchat.data.ai.Providers
import dev.lciszewski27.quickchat.data.ai.skills.SkillExecutor
import dev.lciszewski27.quickchat.data.ai.skills.SkillValidation
import dev.lciszewski27.quickchat.data.local.preferences.UserPreferencesDataStore
import dev.lciszewski27.quickchat.domain.model.FavoriteModel
import dev.lciszewski27.quickchat.domain.model.ProviderInstance
import dev.lciszewski27.quickchat.domain.model.Skill
import dev.lciszewski27.quickchat.domain.repository.ChatRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

class SettingsViewModel(
    private val preferences: UserPreferencesDataStore,
    private val repository: ChatRepository,
    private val resolver: ProviderResolver,
    private val skillExecutor: SkillExecutor
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private val aiCore = AiCoreProvider()
    private var aiCoreDownloadJob: Job? = null

    init {
        checkAiCoreStatus()
        viewModelScope.launch {
            combine(
                preferences.dynamicColorEnabled,
                preferences.darkThemeEnabled,
                preferences.amoledModeEnabled,
                preferences.animationsEnabled,
                preferences.colorPreset,
                preferences.appFont,
                preferences.selectedProviderId,
                preferences.selectedModelId,
                preferences.temperature,
                preferences.systemPrompt,
                repository.observeProviderInstances(),
                repository.observeFavorites(),
                repository.observeSkills()
            ) { flows: Array<Any> ->
                @Suppress("UNCHECKED_CAST")
                SettingsSnapshot(
                    dynamic = flows[0] as Boolean,
                    themeStr = flows[1] as String,
                    amoled = flows[2] as Boolean,
                    animations = flows[3] as Boolean,
                    presetStr = flows[4] as String,
                    fontStr = flows[5] as String,
                    selProvider = flows[6] as String,
                    selModel = flows[7] as String,
                    temp = flows[8] as Float,
                    sys = flows[9] as String,
                    instances = flows[10] as List<ProviderInstance>,
                    favs = flows[11] as List<FavoriteModel>,
                    skills = flows[12] as List<Skill>
                )
            }.collect { snap ->
                _uiState.update { s ->
                    val visibility = s.providers.associate { it.instanceId to it.apiKeyVisible }
                    s.copy(
                        dynamicColorEnabled = snap.dynamic,
                        darkThemeMode = when (snap.themeStr) {
                            "light" -> ThemeMode.LIGHT
                            "dark" -> ThemeMode.DARK
                            else -> ThemeMode.AUTO
                        },
                        amoledModeEnabled = snap.amoled,
                        animationsEnabled = snap.animations,
                        colorPreset = try { ColorPreset.valueOf(snap.presetStr) } catch (e: Exception) { ColorPreset.DEFAULT },
                        appFont = AppFont.entries.find { it.value == snap.fontStr } ?: AppFont.QUICKSAND,
                        selectedProviderId = snap.selProvider,
                        selectedModelId = snap.selModel,
                        temperature = snap.temp,
                        systemPrompt = snap.sys,
                        favorites = snap.favs,
                        skills = snap.skills,
                        providers = snap.instances.map { inst ->
                            val preset = resolver.presetFor(inst.type)
                            ProviderInstanceUi(
                                instanceId = inst.instanceId,
                                type = inst.type,
                                typeDisplay = preset.displayName,
                                label = inst.label.ifBlank { preset.displayName },
                                baseUrl = inst.baseUrl.ifBlank { preset.baseUrl },
                                apiKey = inst.apiKey,
                                apiKeyVisible = visibility[inst.instanceId] == true,
                                isSelected = inst.instanceId == snap.selProvider,
                                isCustom = inst.type == Providers.custom.id,
                                requiresKey = preset.requiresApiKey
                            )
                        }
                    )
                }
            }
        }
    }

    fun onEvent(event: SettingsUiEvent) {
        when (event) {
            SettingsUiEvent.NavigateBack -> {}
            is SettingsUiEvent.ToggleDynamicColor -> viewModelScope.launch {
                preferences.setDynamicColorEnabled(event.enabled)
            }
            is SettingsUiEvent.SetThemeMode -> viewModelScope.launch {
                preferences.setDarkThemeEnabled(event.mode.value)
            }
            is SettingsUiEvent.ToggleAmoledMode -> viewModelScope.launch {
                preferences.setAmoledModeEnabled(event.enabled)
            }
            is SettingsUiEvent.ToggleAnimations -> viewModelScope.launch {
                preferences.setAnimationsEnabled(event.enabled)
            }
            is SettingsUiEvent.SetColorPreset -> viewModelScope.launch {
                preferences.setColorPreset(event.preset.name)
            }
            is SettingsUiEvent.SetAppFont -> viewModelScope.launch {
                preferences.setAppFont(event.font.value)
            }
            SettingsUiEvent.TogglePresetDropdown -> _uiState.update { it.copy(isPresetDropdownExpanded = !it.isPresetDropdownExpanded) }
            SettingsUiEvent.DismissPresetDropdown -> _uiState.update { it.copy(isPresetDropdownExpanded = false) }
            SettingsUiEvent.ToggleThemeDropdown -> _uiState.update { it.copy(isThemeDropdownExpanded = !it.isThemeDropdownExpanded) }
            SettingsUiEvent.DismissThemeDropdown -> _uiState.update { it.copy(isThemeDropdownExpanded = false) }
            SettingsUiEvent.ToggleFontDropdown -> _uiState.update { it.copy(isFontDropdownExpanded = !it.isFontDropdownExpanded) }
            SettingsUiEvent.DismissFontDropdown -> _uiState.update { it.copy(isFontDropdownExpanded = false) }

            SettingsUiEvent.ShowAddProvider -> _uiState.update { it.copy(showAddProvider = true) }
            SettingsUiEvent.DismissAddProvider -> _uiState.update { it.copy(showAddProvider = false) }
            is SettingsUiEvent.AddProvider -> viewModelScope.launch {
                val preset = resolver.presetFor(event.type)
                val isCustom = event.type == Providers.custom.id
                val instanceId = if (isCustom) UUID.randomUUID().toString() else event.type
                val baseUrl = if (isCustom) normalizeUrl(event.baseUrl) else preset.baseUrl
                if (isCustom && baseUrl.isBlank()) return@launch
                repository.upsertProviderInstance(
                    ProviderInstance(
                        instanceId = instanceId,
                        type = event.type,
                        label = event.label.ifBlank { preset.displayName },
                        baseUrl = baseUrl,
                        apiKey = event.apiKey.trim(),
                        createdAt = System.currentTimeMillis()
                    )
                )
                preferences.setSelectedProvider(instanceId)
                preferences.setSelectedModel("")
                _uiState.update {
                    it.copy(
                        showAddProvider = false,
                        fetchingProviderId = instanceId,
                        fetchedModels = emptyList(),
                        fetchError = null
                    )
                }
            }
            is SettingsUiEvent.SetProviderApiKey -> viewModelScope.launch {
                val current = repository.observeProviderInstances().first()
                    .find { it.instanceId == event.instanceId } ?: return@launch
                repository.upsertProviderInstance(current.copy(apiKey = event.apiKey.trim()))
            }
            is SettingsUiEvent.ToggleApiKeyVisibility -> _uiState.update { s ->
                s.copy(providers = s.providers.map {
                    if (it.instanceId == event.instanceId) it.copy(apiKeyVisible = !it.apiKeyVisible) else it
                })
            }
            is SettingsUiEvent.UpdateProvider -> viewModelScope.launch {
                val current = repository.observeProviderInstances().first()
                    .find { it.instanceId == event.instanceId } ?: return@launch
                val baseUrl = if (current.type == Providers.custom.id) {
                    normalizeUrl(event.baseUrl).ifBlank { current.baseUrl }
                } else current.baseUrl
                val preset = resolver.presetFor(current.type)
                repository.upsertProviderInstance(
                    current.copy(
                        label = event.label.ifBlank { preset.displayName },
                        baseUrl = baseUrl
                    )
                )
                _uiState.update { it.copy(editingProviderId = null) }
            }
            is SettingsUiEvent.ShowEditProvider -> _uiState.update { it.copy(editingProviderId = event.instanceId) }
            SettingsUiEvent.DismissEditProvider -> _uiState.update { it.copy(editingProviderId = null) }
            is SettingsUiEvent.RequestDeleteProvider -> _uiState.update { it.copy(confirmDeleteProviderId = event.instanceId) }
            SettingsUiEvent.DismissDeleteProvider -> _uiState.update { it.copy(confirmDeleteProviderId = null) }
            SettingsUiEvent.ConfirmDeleteProvider -> viewModelScope.launch {
                val id = _uiState.value.confirmDeleteProviderId ?: return@launch
                repository.removeProviderInstance(id)
                val remaining = repository.observeProviderInstances().first()
                if (preferences.selectedProviderId.first() == id) {
                    preferences.setSelectedProvider(remaining.firstOrNull()?.instanceId.orEmpty())
                    preferences.setSelectedModel("")
                }
                _uiState.update {
                    it.copy(
                        confirmDeleteProviderId = null,
                        editingProviderId = null,
                        fetchingProviderId = null,
                        fetchedModels = emptyList()
                    )
                }
            }
            is SettingsUiEvent.SelectProvider -> viewModelScope.launch {
                preferences.setSelectedProvider(event.instanceId)
                val fav = repository.observeFavorites().first()
                    .firstOrNull { it.providerId == event.instanceId }
                preferences.setSelectedModel(fav?.modelId.orEmpty())
                _uiState.update { it.copy(fetchingProviderId = event.instanceId, fetchedModels = emptyList(), fetchError = null) }
            }
            is SettingsUiEvent.FetchModels -> fetchModels(event.instanceId)
            SettingsUiEvent.CheckAiCoreStatus -> checkAiCoreStatus()
            SettingsUiEvent.DownloadAiCoreModel -> downloadAiCoreModel()
            is SettingsUiEvent.ToggleFavorite -> viewModelScope.launch {
                if (repository.isFavorite(event.instanceId, event.model.id)) {
                    repository.removeFavorite(event.instanceId, event.model.id)
                } else {
                    repository.addFavorite(event.instanceId, event.model.id, event.model.displayName)
                }
            }
            is SettingsUiEvent.SelectModel -> viewModelScope.launch {
                preferences.setSelectedProvider(event.instanceId)
                preferences.setSelectedModel(event.modelId)
            }
            SettingsUiEvent.ShowNewSkill -> _uiState.update {
                it.copy(showNewSkill = true, editingSkillId = null)
            }
            is SettingsUiEvent.ShowEditSkill -> _uiState.update {
                it.copy(editingSkillId = event.skillId, showNewSkill = false)
            }
            SettingsUiEvent.DismissSkillEditor -> _uiState.update {
                it.copy(showNewSkill = false, editingSkillId = null)
            }
            is SettingsUiEvent.SaveSkill -> viewModelScope.launch {
                saveSkill(event)
            }
            is SettingsUiEvent.RequestDeleteSkill -> _uiState.update {
                it.copy(confirmDeleteSkillId = event.skillId)
            }
            SettingsUiEvent.DismissDeleteSkill -> _uiState.update {
                it.copy(confirmDeleteSkillId = null)
            }
            SettingsUiEvent.ConfirmDeleteSkill -> viewModelScope.launch {
                val id = _uiState.value.confirmDeleteSkillId ?: return@launch
                repository.deleteSkill(id)
                _uiState.update {
                    it.copy(
                        confirmDeleteSkillId = null,
                        editingSkillId = null,
                        testingSkillId = null,
                        secretsSkillId = null
                    )
                }
            }
            is SettingsUiEvent.ToggleSkillEnabled -> viewModelScope.launch {
                repository.setSkillEnabled(event.skillId, event.enabled)
            }
            is SettingsUiEvent.ShowSkillTest -> _uiState.update {
                it.copy(testingSkillId = event.skillId, testArgs = "{}", testResult = null)
            }
            SettingsUiEvent.DismissSkillTest -> _uiState.update {
                it.copy(testingSkillId = null, testArgs = "{}", testResult = null, testRunning = false)
            }
            is SettingsUiEvent.SetSkillTestArgs -> _uiState.update {
                it.copy(testArgs = event.args)
            }
            SettingsUiEvent.RunSkillTest -> runSkillTest()
            is SettingsUiEvent.ShowSkillSecrets -> viewModelScope.launch {
                val values = repository.getSecretValues(event.skillId)
                _uiState.update { it.copy(secretsSkillId = event.skillId, secretValues = values) }
            }
            SettingsUiEvent.DismissSkillSecrets -> _uiState.update {
                it.copy(secretsSkillId = null, secretValues = emptyMap())
            }
            is SettingsUiEvent.SaveSkillSecrets -> viewModelScope.launch {
                saveSkillSecrets(event)
            }
            is SettingsUiEvent.SetTemperature -> viewModelScope.launch {
                preferences.setTemperature(event.value)
            }
            is SettingsUiEvent.SetSystemPrompt -> viewModelScope.launch {
                preferences.setSystemPrompt(event.value)
            }
        }
    }

    private fun saveSkill(event: SettingsUiEvent.SaveSkill) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            if (event.skillId != null) {
                val current = repository.getSkill(event.skillId) ?: return@launch
                val params = SkillValidation.normalizeParamsSchema(event.paramsSchema)
                    .getOrElse { return@launch }
                repository.upsertSkill(
                    current.copy(
                        name = event.name.ifBlank { current.name },
                        description = event.description,
                        paramsSchema = params,
                        code = event.code.ifBlank { current.code },
                        // Editing code invalidates the previous pass: re-test to reactivate.
                        tested = if (event.code != current.code) false else current.tested,
                        enabled = if (event.code != current.code) false else current.enabled,
                        updatedAt = now
                    )
                )
            } else {
                if (event.name.isBlank() || event.code.isBlank()) return@launch
                val params = SkillValidation.normalizeParamsSchema(event.paramsSchema)
                    .getOrElse { return@launch }
                val taken = repository.observeSkills().first().map { it.toolName }.toSet()
                val toolName = SkillValidation.ensureUnique(
                    SkillValidation.sanitizeToolName(event.name), taken
                )
                repository.upsertSkill(
                    Skill(
                        id = UUID.randomUUID().toString(),
                        name = event.name.take(SkillValidation.MAX_NAME_CHARS),
                        toolName = toolName,
                        description = event.description,
                        code = event.code,
                        paramsSchema = params,
                        secrets = emptyList(),
                        enabled = false,
                        tested = false,
                        createdAt = now,
                        updatedAt = now
                    )
                )
            }
            _uiState.update { it.copy(showNewSkill = false, editingSkillId = null) }
        }
    }

    private fun runSkillTest() {
        viewModelScope.launch {
            val id = _uiState.value.testingSkillId ?: return@launch
            val skill = repository.getSkill(id) ?: return@launch
            _uiState.update { it.copy(testRunning = true, testResult = null) }
            // Real stored secrets are used: this is the gate that proves the
            // skill works as the model will call it.
            val secrets = repository.getSecretValues(id)
                .filterKeys { name -> skill.secrets.any { it.name == name } }
            val outcome = skillExecutor.trialRun(skill.code, _uiState.value.testArgs, secrets)
            if (!outcome.startsWith("Error")) {
                repository.setSkillTested(id, true)
                repository.setSkillEnabled(id, true)
            }
            _uiState.update { it.copy(testRunning = false, testResult = outcome) }
        }
    }

    private fun saveSkillSecrets(event: SettingsUiEvent.SaveSkillSecrets) {
        viewModelScope.launch {
            val skill = repository.getSkill(event.skillId) ?: return@launch
            // Persist declarations on the skill row…
            repository.upsertSkill(skill.copy(secrets = event.decls, updatedAt = System.currentTimeMillis()))
            // …values in their own table, then prune values whose
            // declaration was removed.
            event.values.forEach { (name, value) ->
                if (event.decls.any { it.name == name }) {
                    repository.setSecretValue(event.skillId, name, value)
                }
            }
            val current = repository.getSecretValues(event.skillId)
            current.keys.forEach { name ->
                if (event.decls.none { it.name == name }) {
                    repository.deleteSecretValue(event.skillId, name)
                }
            }
            _uiState.update { it.copy(secretsSkillId = null, secretValues = emptyMap()) }
        }
    }

        private fun checkAiCoreStatus() {
        viewModelScope.launch {
            _uiState.update { it.copy(aiCoreStatus = AiCoreStatus.Checking) }
            _uiState.update { it.copy(aiCoreStatus = aiCore.status()) }
        }
    }

    private fun downloadAiCoreModel() {
        aiCoreDownloadJob?.cancel()
        aiCoreDownloadJob = viewModelScope.launch {
            _uiState.update { it.copy(aiCoreStatus = AiCoreStatus.Downloading(null)) }
            try {
                aiCore.download().collect { p ->
                    when {
                        p.error != null -> _uiState.update {
                            it.copy(aiCoreStatus = AiCoreStatus.Failed(p.error))
                        }
                        p.done -> _uiState.update {
                            it.copy(aiCoreStatus = AiCoreStatus.Ready)
                        }
                        else -> _uiState.update {
                            it.copy(aiCoreStatus = AiCoreStatus.Downloading(p.fraction))
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(aiCoreStatus = AiCoreStatus.Failed(e.message ?: "download failed")) }
            }
        }
    }

    private fun fetchModels(instanceId: String) {        viewModelScope.launch {
            val instance = repository.observeProviderInstances().first()
                .find { it.instanceId == instanceId } ?: return@launch
            _uiState.update { it.copy(isFetchingModels = true, fetchError = null, fetchingProviderId = instanceId) }
            val result = resolver.resolve(instance).listModels(instance.apiKey)
            result.onSuccess { models ->
                _uiState.update { it.copy(isFetchingModels = false, fetchedModels = models) }
            }.onFailure { e ->
                _uiState.update { it.copy(isFetchingModels = false, fetchError = e.message ?: "Failed to fetch models", fetchedModels = emptyList()) }
            }
        }
    }

    /** Pre-select provider's models on first open of Models page. */
    fun ensureFetched(instanceId: String) {
        val s = _uiState.value
        if (instanceId.isBlank()) return
        if (s.fetchingProviderId == instanceId && (s.fetchedModels.isNotEmpty() || s.isFetchingModels)) return
        _uiState.update { it.copy(fetchingProviderId = instanceId, fetchedModels = emptyList(), fetchError = null) }
        fetchModels(instanceId)
    }

    private fun normalizeUrl(raw: String): String {
        var url = raw.trim().trimEnd('/')
        if (url.isNotEmpty() && !url.startsWith("http://") && !url.startsWith("https://")) {
            url = "http://$url"
        }
        return url
    }

    private data class SettingsSnapshot(
        val dynamic: Boolean,
        val themeStr: String,
        val amoled: Boolean,
        val animations: Boolean,
        val presetStr: String,
        val fontStr: String,
        val selProvider: String,
        val selModel: String,
        val temp: Float,
        val sys: String,
        val instances: List<ProviderInstance>,
        val favs: List<FavoriteModel>,
        val skills: List<Skill>
    )
}
