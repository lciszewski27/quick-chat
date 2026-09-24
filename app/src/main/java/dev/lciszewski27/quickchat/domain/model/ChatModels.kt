package dev.lciszewski27.quickchat.domain.model

data class ChatSession(
    val id: String,
    val title: String,
    val providerId: String,
    val modelId: String,
    val createdAt: Long,
    val updatedAt: Long
)

enum class ChatRole { USER, MODEL }

data class ChatMessage(
    val id: String,
    val sessionId: String,
    val role: ChatRole,
    val text: String,
    val timestamp: Long,
    val isError: Boolean = false
)

data class AiModelInfo(
    val providerId: String,
    val id: String,
    val displayName: String,
    val description: String? = null
)

data class FavoriteModel(
    val providerId: String,
    val modelId: String,
    val displayName: String,
    val addedAt: Long
)

/**
 * A user-added provider backend (preset type or custom OpenAI-compatible
 * endpoint). Favorites, selection and sessions reference [instanceId].
 */
data class ProviderInstance(
    val instanceId: String,
    val type: String,
    val label: String,
    val baseUrl: String,
    val apiKey: String,
    val createdAt: Long
)

/** One local tool execution, rendered as an expandable timeline card. */
data class ToolCallRecord(
    val id: String,
    val sessionId: String,
    val name: String,
    val displayName: String,
    val argsJson: String,
    val result: String,
    val durationMs: Long,
    val timestamp: Long
)

data class ChatTurn(val role: String, val text: String)
