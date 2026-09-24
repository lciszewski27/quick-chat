package dev.lciszewski27.quickchat.ui.chat

import dev.lciszewski27.quickchat.domain.model.AiModelInfo
import dev.lciszewski27.quickchat.domain.model.ChatMessage
import dev.lciszewski27.quickchat.domain.model.ChatSession
import dev.lciszewski27.quickchat.domain.model.FavoriteModel
import dev.lciszewski27.quickchat.domain.model.ToolCallRecord

data class ChatUiState(
    val sessions: List<ChatSession> = emptyList(),
    val currentSessionId: String? = null,
    val currentSession: ChatSession? = null,
    val messages: List<ChatMessage> = emptyList(),
    /** Tool executions in the current session, chronological. */
    val toolCalls: List<ToolCallRecord> = emptyList(),
    val input: String = "",
    val isSending: Boolean = false,
    /** In-progress streamed text (not yet persisted); null when idle. */
    val streamingText: String? = null,
    /** Session the live stream belongs to — stream survives navigation. */
    val streamingSessionId: String? = null,
    /** Tool currently executing (live indicator); null when idle/done. */
    val runningTool: RunningTool? = null,
    val error: String? = null,
    val selectedProviderId: String = "gemini",
    val selectedModelId: String = "",
    val providerDisplayName: String = "",
    val favorites: List<FavoriteModel> = emptyList(),
    /** Chat picker content: favorites only (catalogs like OpenRouter's are huge). */
    val availableModels: List<AiModelInfo> = emptyList(),
    /** instanceId → label, for grouping favorites in the picker. */
    val providerLabels: Map<String, String> = emptyMap(),
    val hasApiKey: Boolean = false,
    val searchQuery: String = ""
)

/** Tool currently executing (live "Running …" indicator). */
data class RunningTool(val name: String, val displayName: String)

sealed interface ChatUiEvent {
    data class OnInputChange(val text: String) : ChatUiEvent
    data object Send : ChatUiEvent
    /** Cancel the live stream; partial text is kept as the reply. */
    data object Stop : ChatUiEvent
    data object NewChat : ChatUiEvent
    data class SelectSession(val id: String) : ChatUiEvent
    data class DeleteSession(val id: String) : ChatUiEvent
    data class RenameSession(val id: String, val title: String) : ChatUiEvent
    data object ClearCurrent : ChatUiEvent
    data object Retry : ChatUiEvent
    data object DismissError : ChatUiEvent
    data class SelectModel(val providerId: String, val modelId: String) : ChatUiEvent
    data class OnSearchChange(val q: String) : ChatUiEvent
}
