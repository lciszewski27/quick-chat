package dev.lciszewski27.quickchat.data.ai

import dev.lciszewski27.quickchat.domain.model.AiModelInfo
import dev.lciszewski27.quickchat.domain.model.ChatTurn
import kotlinx.coroutines.flow.Flow

/**
 * One item of a streaming reply. Text deltas render live; tool executions
 * are reported as they complete so the UI can show them inline.
 */
sealed interface AiStreamEvent {
    data class Text(val delta: String) : AiStreamEvent
    /** Emitted before a tool executes so the UI can show live progress. */
    data class ToolStarted(
        val name: String,
        val displayName: String,
        val argsJson: String
    ) : AiStreamEvent
    data class Tool(
        val name: String,
        val displayName: String,
        val argsJson: String,
        val result: String,
        val durationMs: Long
    ) : AiStreamEvent
}

/**
 * Provider abstraction. Every provider streams through the shared [LlmSdk];
 * providers themselves are resolved from user-added [ProviderInstance]s
 * (see [ProviderResolver]). Model catalogs are always fetched live — no
 * hardcoded model ids anywhere.
 */
interface AiProvider {
    val id: String
    val displayName: String
    val apiKeyHint: String
    val apiKeyHelpUrl: String
    suspend fun listModels(apiKey: String): Result<List<AiModelInfo>>

    /**
     * Streams reply events in arrival order; completes when the turn ends.
     * Throws on transport/HTTP/API errors; cancelling the collector aborts the
     * HTTP call (used by the Stop button).
     */
    fun streamReply(
        apiKey: String,
        modelId: String,
        history: List<ChatTurn>,
        systemPrompt: String?,
        temperature: Float
    ): Flow<AiStreamEvent>

    /** One-shot convenience over [streamReply]; kept for callers that don't stream. */
    suspend fun generateReply(
        apiKey: String,
        modelId: String,
        history: List<ChatTurn>,
        systemPrompt: String?,
        temperature: Float
    ): Result<String> = try {
        val acc = StringBuilder()
        streamReply(apiKey, modelId, history, systemPrompt, temperature).collect { event ->
            if (event is AiStreamEvent.Text) acc.append(event.delta)
        }
        val text = acc.toString().trim()
        if (text.isBlank()) Result.failure(IllegalStateException("Empty response"))
        else Result.success(text)
    } catch (e: Exception) {
        Result.failure(e)
    }
}
