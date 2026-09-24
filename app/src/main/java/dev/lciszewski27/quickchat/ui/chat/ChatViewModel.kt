package dev.lciszewski27.quickchat.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.lciszewski27.quickchat.data.ai.AiStreamEvent
import dev.lciszewski27.quickchat.data.ai.ProviderResolver
import dev.lciszewski27.quickchat.data.local.preferences.UserPreferencesDataStore
import dev.lciszewski27.quickchat.domain.model.AiModelInfo
import dev.lciszewski27.quickchat.domain.model.ChatRole
import dev.lciszewski27.quickchat.domain.model.ChatTurn
import dev.lciszewski27.quickchat.domain.model.FavoriteModel
import dev.lciszewski27.quickchat.domain.model.ProviderInstance
import dev.lciszewski27.quickchat.domain.repository.ChatRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ChatViewModel(
    private val repository: ChatRepository,
    private val preferences: UserPreferencesDataStore,
    private val resolver: ProviderResolver
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private val currentSessionId = MutableStateFlow<String?>(null)

    init {
        viewModelScope.launch {
            combine(
                repository.observeSessions(),
                repository.observeProviderInstances(),
                preferences.selectedProviderId,
                preferences.selectedModelId,
                preferences.temperature,
                preferences.systemPrompt,
                repository.observeFavorites()
            ) { args: Array<Any> ->
                @Suppress("UNCHECKED_CAST")
                ChatPrefs(
                    sessions = args[0] as List<dev.lciszewski27.quickchat.domain.model.ChatSession>,
                    instances = args[1] as List<ProviderInstance>,
                    providerId = args[2] as String,
                    modelId = args[3] as String,
                    temp = args[4] as Float,
                    sys = args[5] as String,
                    favs = args[6] as List<FavoriteModel>
                )
            }.collect { prefs ->
                val active = prefs.instances.find { it.instanceId == prefs.providerId }
                    ?: prefs.instances.firstOrNull()
                val favsOfActive = prefs.favs.filter { it.providerId == active?.instanceId }
                // No hardcoded models: selected → first favorite of the provider → none.
                val effectiveModel = prefs.modelId.takeIf { it.isNotBlank() }
                    ?: favsOfActive.firstOrNull()?.modelId.orEmpty()
                val sessions = prefs.sessions
                if (currentSessionId.value == null && sessions.isNotEmpty()) {
                    currentSessionId.value = sessions.first().id
                }
                _uiState.update {
                    it.copy(
                        sessions = sessions,
                        selectedProviderId = active?.instanceId.orEmpty(),
                        selectedModelId = effectiveModel,
                        providerDisplayName = active?.label ?: "No provider",
                        favorites = prefs.favs,
                        availableModels = prefs.favs.map { f ->
                            AiModelInfo(f.providerId, f.modelId, f.displayName, null)
                        },
                        providerLabels = prefs.instances.associate { i -> i.instanceId to i.label },
                        hasApiKey = active?.let {
                            !resolver.presetFor(it.type).requiresApiKey || it.apiKey.isNotBlank()
                        } == true
                    )
                }
            }
        }

        viewModelScope.launch {
            @OptIn(ExperimentalCoroutinesApi::class)
            currentSessionId.flatMapLatest { id ->
                if (id == null) flowOf(emptyList()) else repository.observeMessages(id)
            }.collect { messages ->
                val sessionId = currentSessionId.value
                val session = sessionId?.let { sid -> _uiState.value.sessions.find { it.id == sid } }
                _uiState.update { it.copy(currentSessionId = sessionId, currentSession = session, messages = messages) }
            }
        }

        viewModelScope.launch {
            @OptIn(ExperimentalCoroutinesApi::class)
            currentSessionId.flatMapLatest { id ->
                if (id == null) flowOf(emptyList()) else repository.observeToolCalls(id)
            }.collect { calls ->
                _uiState.update { it.copy(toolCalls = calls) }
            }
        }
    }

    fun onEvent(event: ChatUiEvent) {
        when (event) {
            is ChatUiEvent.OnInputChange -> _uiState.update { it.copy(input = event.text) }
            is ChatUiEvent.OnSearchChange -> _uiState.update { it.copy(searchQuery = event.q) }
            ChatUiEvent.DismissError -> _uiState.update { it.copy(error = null) }
            ChatUiEvent.NewChat -> viewModelScope.launch {
                val s = _uiState.value
                val created = repository.createSession(s.selectedProviderId, s.selectedModelId)
                currentSessionId.value = created.id
                _uiState.update { it.copy(error = null) }
            }
            is ChatUiEvent.SelectSession -> {
                currentSessionId.value = event.id
                _uiState.update { it.copy(error = null) }
            }
            is ChatUiEvent.DeleteSession -> viewModelScope.launch {
                repository.deleteSession(event.id)
                if (currentSessionId.value == event.id) {
                    val remaining = repository.observeSessions().first().filterNot { it.id == event.id }
                    currentSessionId.value = remaining.firstOrNull()?.id
                }
            }
            is ChatUiEvent.RenameSession -> viewModelScope.launch {
                repository.renameSession(event.id, event.title)
            }
            ChatUiEvent.ClearCurrent -> viewModelScope.launch {
                currentSessionId.value?.let { repository.clearSession(it) }
            }
            is ChatUiEvent.SelectModel -> viewModelScope.launch {
                preferences.setSelectedProvider(event.providerId)
                preferences.setSelectedModel(event.modelId)
                currentSessionId.value?.let { repository.updateSessionModel(it, event.providerId, event.modelId) }
            }
            ChatUiEvent.Send -> send()
            ChatUiEvent.Stop -> stopStream()
            ChatUiEvent.Retry -> send(isRetry = true)
        }
    }

    private var lastFailedUserText: String? = null
    private var streamJob: Job? = null

    private fun stopStream() {
        streamJob?.cancel()
    }

    private fun activeInstance(instances: List<ProviderInstance>, id: String): ProviderInstance? =
        instances.find { it.instanceId == id } ?: instances.firstOrNull()

    private fun send(isRetry: Boolean = false) {
        val s = _uiState.value
        if (s.isSending) return
        val text = if (isRetry) (lastFailedUserText ?: s.input) else s.input.trim()
        if (text.isBlank()) return

        streamJob?.cancel()
        streamJob = viewModelScope.launch {
            _uiState.update { it.copy(isSending = true, error = null, input = "", streamingText = null, streamingSessionId = null, runningTool = null, streamingTps = null) }
            var streamStartMs = 0L
            fun statsFor(text: String): Pair<Int, Long> {
                val ms = if (streamStartMs == 0L) 0L
                else (System.currentTimeMillis() - streamStartMs).coerceAtLeast(1)
                return estimateTokens(text.length) to ms
            }
            try {
                val instances = repository.observeProviderInstances().first()
                val instance = activeInstance(instances, _uiState.value.selectedProviderId)
                    ?: throw IllegalStateException("Add a provider in Settings → Providers first.")
                if (resolver.presetFor(instance.type).requiresApiKey && instance.apiKey.isBlank()) {
                    _uiState.update { it.copy(isSending = false, error = "Add your ${instance.label} API key in Settings → Providers.", input = text) }
                    lastFailedUserText = text
                    return@launch
                }
                val modelId = _uiState.value.selectedModelId
                if (modelId.isBlank()) {
                    _uiState.update { it.copy(isSending = false, error = "Pick a favorite model in Settings → Models first.", input = text) }
                    lastFailedUserText = text
                    return@launch
                }
                var sessionId = currentSessionId.value
                if (sessionId == null) {
                    val created = repository.createSession(instance.instanceId, modelId)
                    sessionId = created.id
                    currentSessionId.value = sessionId
                } else {
                    repository.updateSessionModel(sessionId, instance.instanceId, modelId)
                }
                val sid = sessionId
                val snapMessages = _uiState.value.messages
                if (!isRetry) {
                    repository.appendMessage(sid, ChatRole.USER, text)
                } else if (snapMessages.none { it.role == ChatRole.USER && it.text == text }) {
                    repository.appendMessage(sid, ChatRole.USER, text)
                }
                val historyMsgs = repository.observeMessages(sid).first()
                val turns = historyMsgs.map { ChatTurn(if (it.role == ChatRole.MODEL) "model" else "user", it.text) }
                val temp = preferences.temperature.first()
                val sys = preferences.systemPrompt.first().takeIf { it.isNotBlank() }

                _uiState.update { it.copy(streamingSessionId = sid, streamingText = "") }
                val acc = StringBuilder()
                try {
                    resolver.resolve(instance).streamReply(instance.apiKey, modelId, turns, sys, temp)
                        .collect { event ->
                            when (event) {
                                is AiStreamEvent.Text -> {
                                    acc.append(event.delta)
                                    if (streamStartMs == 0L) {
                                        streamStartMs = System.currentTimeMillis()
                                    }
                                    val tps = computeTps(
                                        estimateTokens(acc.length),
                                        System.currentTimeMillis() - streamStartMs
                                    )
                                    _uiState.update { it.copy(streamingText = acc.toString(), streamingTps = tps) }
                                }
                                is AiStreamEvent.ToolStarted -> {
                                    _uiState.update {
                                        it.copy(runningTool = RunningTool(event.name, event.displayName))
                                    }
                                }
                                is AiStreamEvent.Tool -> {
                                    repository.appendToolCall(
                                        sessionId = sid,
                                        name = event.name,
                                        displayName = event.displayName,
                                        argsJson = event.argsJson,
                                        result = event.result,
                                        durationMs = event.durationMs
                                    )
                                    _uiState.update { it.copy(runningTool = null) }
                                }
                            }
                        }
                } catch (e: CancellationException) {
                    // Stop button: keep the partial reply instead of discarding it.
                    throw StreamStopped(acc.toString())
                }
                val full = acc.toString().trim()
                if (full.isBlank()) throw IllegalStateException("Empty response")
                val (tokens, ms) = statsFor(full)
                repository.appendMessage(
                    sid, ChatRole.MODEL, full, genTokens = tokens, genMs = ms
                )
                lastFailedUserText = null
                _uiState.update { it.copy(isSending = false, streamingText = null, streamingSessionId = null, runningTool = null, streamingTps = null) }
            } catch (e: StreamStopped) {
                val partial = e.partial.trim()
                val sid = _uiState.value.streamingSessionId
                if (sid != null && partial.isNotBlank()) {
                    val (tokens, ms) = statsFor(partial)
                    repository.appendMessage(
                        sid, ChatRole.MODEL, partial, genTokens = tokens, genMs = ms
                    )
                }
                lastFailedUserText = null
                _uiState.update { it.copy(isSending = false, streamingText = null, streamingSessionId = null, runningTool = null, streamingTps = null) }
            } catch (e: CancellationException) {
                _uiState.update { it.copy(isSending = false, streamingText = null, streamingSessionId = null, runningTool = null, streamingTps = null) }
            } catch (e: Exception) {
                val partial = _uiState.value.streamingText?.trim().orEmpty()
                val sid = _uiState.value.streamingSessionId
                if (sid != null && partial.isNotBlank()) {
                    // Keep what arrived, then report the failure separately.
                    val (tokens, ms) = statsFor(partial)
                    repository.appendMessage(
                        sid, ChatRole.MODEL, partial, genTokens = tokens, genMs = ms
                    )
                    _uiState.update { it.copy(isSending = false, streamingText = null, streamingSessionId = null, runningTool = null, streamingTps = null, error = e.message ?: "Request failed") }
                } else {
                    if (sid != null) {
                        repository.appendMessage(sid, ChatRole.MODEL, "Sorry — ${e.message ?: "request failed"}.", isError = true)
                    }
                    lastFailedUserText = text
                    _uiState.update { it.copy(isSending = false, streamingText = null, streamingSessionId = null, runningTool = null, streamingTps = null, error = e.message ?: "Request failed") }
                }
            }
        }
    }
}

/** Internal signal: user pressed Stop — carries the partial reply to persist. */
private class StreamStopped(val partial: String) : CancellationException("stopped")

private data class ChatPrefs(
    val sessions: List<dev.lciszewski27.quickchat.domain.model.ChatSession>,
    val instances: List<ProviderInstance>,
    val providerId: String,
    val modelId: String,
    val temp: Float,
    val sys: String,
    val favs: List<FavoriteModel>
)
