package dev.lciszewski27.quickchat.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chat_sessions")
data class ChatSessionEntity(
    @PrimaryKey val id: String,
    val title: String,
    val providerId: String,
    val modelId: String,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val role: String,
    val text: String,
    val timestamp: Long,
    val isError: Boolean = false
)

@Entity(tableName = "favorite_models", primaryKeys = ["providerId", "modelId"])
data class FavoriteModelEntity(
    val providerId: String,
    val modelId: String,
    val displayName: String,
    val addedAt: Long
)

/**
 * A user-added provider backend. Preset types (gemini/openai/…) use their
 * type id as [instanceId] (added at most once, so stored favorites survive);
 * custom OpenAI-compatible endpoints get a UUID.
 */
@Entity(tableName = "provider_instances")
data class ProviderInstanceEntity(
    @PrimaryKey val instanceId: String,
    val type: String,
    val label: String,
    val baseUrl: String,
    val apiKey: String,
    val createdAt: Long
)

/** One local tool execution inside a chat session (shown as a timeline card). */
@Entity(tableName = "tool_calls")
data class ToolCallEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val name: String,
    val displayName: String,
    val argsJson: String,
    val result: String,
    val durationMs: Long,
    val timestamp: Long
)
