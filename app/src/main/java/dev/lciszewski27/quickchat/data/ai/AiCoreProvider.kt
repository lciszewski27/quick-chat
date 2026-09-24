package dev.lciszewski27.quickchat.data.ai

import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Generation
import dev.lciszewski27.quickchat.domain.model.AiModelInfo
import dev.lciszewski27.quickchat.domain.model.ChatTurn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/** On-device model state for UI (download gating, progress). */
sealed interface AiCoreStatus {
    data object Checking : AiCoreStatus
    data object Unsupported : AiCoreStatus
    data object Downloadable : AiCoreStatus
    data class Downloading(val fraction: Float?) : AiCoreStatus
    data object Ready : AiCoreStatus
    data class Failed(val message: String) : AiCoreStatus
}

data class AiCoreDownloadProgress(
    val fraction: Float?,
    val done: Boolean,
    val error: String? = null
)

/**
 * On-device Gemini Nano via ML Kit GenAI Prompt API (AICore-backed).
 * No API key, no network for inference — private and offline-capable.
 * Differences from server providers, by design:
 * - No function calling: tools are never attached (Nano can't call them).
 * - No temperature control: default generation config is used.
 * - Input budget ~4000 tokens: history is truncated to the most recent turns.
 *
 * Client lifecycle: [Generation.getClient] hands out the shared AICore
 * binder; the system service owns the engine, so instances are intentionally
 * not closed per call.
 */
class AiCoreProvider : AiProvider {
    override val id = "aicore"
    override val displayName = "On-device (Gemini Nano)"
    override val requiresApiKey = false
    override val apiKeyHint = ""
    override val apiKeyHelpUrl = "https://developer.android.com/ai/gemini-nano"

    suspend fun status(): AiCoreStatus {
        return try {
            when (Generation.getClient().checkStatus()) {
                FeatureStatus.AVAILABLE -> AiCoreStatus.Ready
                FeatureStatus.DOWNLOADABLE -> AiCoreStatus.Downloadable
                FeatureStatus.DOWNLOADING -> AiCoreStatus.Downloading(null)
                else -> AiCoreStatus.Unsupported
            }
        } catch (e: Exception) {
            // Fresh AICore setup / binding failures surface here; user can retry.
            AiCoreStatus.Failed(e.message ?: "status check failed")
        }
    }

    fun download(): Flow<AiCoreDownloadProgress> =
        Generation.getClient().download().map { s ->
            when (s) {
                is DownloadStatus.DownloadProgress -> AiCoreDownloadProgress(
                    fraction = null, // only bytes-downloaded is reported; show indeterminate
                    done = false
                )
                DownloadStatus.DownloadCompleted -> AiCoreDownloadProgress(null, done = true)
                is DownloadStatus.DownloadFailed ->
                    AiCoreDownloadProgress(null, done = true, error = s.e.message)
                else -> AiCoreDownloadProgress(null, done = false)
            }
        }

    override suspend fun listModels(apiKey: String): Result<List<AiModelInfo>> {
        return try {
            if (status() != AiCoreStatus.Ready) return Result.success(emptyList())
            val name = try {
                Generation.getClient().getBaseModelName()
            } catch (e: Exception) {
                null
            }
            Result.success(
                listOf(
                    AiModelInfo(
                        providerId = id,
                        id = "gemini-nano",
                        displayName = name?.let { "Gemini Nano ($it)" } ?: "Gemini Nano",
                        description = "On-device • private • works offline"
                    )
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun streamReply(
        apiKey: String,
        modelId: String,
        history: List<ChatTurn>,
        systemPrompt: String?,
        temperature: Float
    ): Flow<AiStreamEvent> = kotlinx.coroutines.flow.flow {
        if (status() != AiCoreStatus.Ready) {
            throw IllegalStateException(
                "On-device model isn't downloaded — open Settings → Providers & Keys to download it."
            )
        }
        val prompt = buildTranscript(history, systemPrompt)
        Generation.getClient().generateContentStream(prompt).collect { chunk ->
            val text = chunk.candidates.firstOrNull()?.text
            if (!text.isNullOrEmpty()) emit(AiStreamEvent.Text(text))
        }
    }.flowOn(Dispatchers.IO)

    companion object {
        /** Stays under the ~4000-token input limit with margin. */
        const val MAX_TRANSCRIPT_CHARS = 12000

        /**
         * Flattens turns into a role-labeled transcript (the String prompt
         * overload is the only generation form used, so no request-builder
         * API is needed). Oldest turns are dropped first; the latest user
         * turn always survives.
         */
        fun buildTranscript(history: List<ChatTurn>, systemPrompt: String?): String {
            val lines = ArrayDeque<String>()
            systemPrompt?.takeIf { it.isNotBlank() }?.let { lines.add("System: $it") }
            history.forEach { turn ->
                val role = if (turn.role == "model") "Assistant" else "User"
                lines.add("$role: ${turn.text}")
            }
            var total = lines.sumOf { it.length + 1 }
            while (total > MAX_TRANSCRIPT_CHARS && lines.size > 1) {
                // Never drop the leading System line or the latest turn:
                // drop from just after System (or from the front if no System).
                val dropAt = if (lines.first().startsWith("System: ")) 1 else 0
                if (dropAt >= lines.size) break
                total -= lines.removeAt(dropAt).length + 1
            }
            return lines.joinToString("\n")
        }
    }
}
