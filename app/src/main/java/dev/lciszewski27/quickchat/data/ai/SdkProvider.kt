package dev.lciszewski27.quickchat.data.ai

import dev.lciszewski27.quickchat.data.ai.skills.SkillToolSource
import dev.lciszewski27.quickchat.data.ai.tools.AiTool
import dev.lciszewski27.quickchat.domain.model.AiModelInfo
import dev.lciszewski27.quickchat.domain.model.ChatTurn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.io.IOException

/** Max tool-call rounds per reply (loop guard). */
private const val MAX_TOOL_ROUNDS = 5

/**
 * The single [AiProvider] implementation for every configured backend.
 * Protocol differences (OpenAI-protocol SSE vs Anthropic Messages SSE) live
 * here; providers themselves are pure [ProviderConfig] data.
 *
 * When [tools] is non-empty, replies run an agentic loop: stream → collect
 * tool calls → execute locally → resume streaming with results (up to
 * [MAX_TOOL_ROUNDS] rounds). Only streamed text reaches the UI/history.
 */
class SdkProvider(
    private val config: ProviderConfig,
    private val sdk: LlmSdk,
    private val tools: List<AiTool> = emptyList(),
    private val skillTools: SkillToolSource? = null
) : AiProvider {

    override val id: String get() = config.id
    override val displayName: String get() = config.displayName
    override val apiKeyHint: String get() = config.apiKeyHint
    override val apiKeyHelpUrl: String get() = config.helpUrl

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false // omit nulls from requests (safer across backends)
    }

    // ── Catalog ──────────────────────────────────────────────────────
    override suspend fun listModels(apiKey: String): Result<List<AiModelInfo>> {
        // No key (e.g. local Ollama without auth) still tries the endpoint —
        // only a truly blank key+local combo is resolved by the caller.
        return try {
            val models = when (config.protocol) {
                LlmProtocol.OPENAI -> fetchOpenAiStyleList(apiKey)
                LlmProtocol.ANTHROPIC -> {
                    if (apiKey.isBlank()) emptyList()
                    else fetchAnthropicList(apiKey)
                }
            }
            Result.success(models)
        } catch (e: Exception) {
            // Gemini resilience: the OpenAI-compat catalog is newer than the
            // native one — retry natively before giving up.
            if (config.geminiNativeListFallback) {
                try {
                    return Result.success(fetchGeminiNativeList(apiKey))
                } catch (e2: Exception) {
                    return Result.failure(e2)
                }
            }
            Result.failure(e)
        }
    }

    private suspend fun fetchOpenAiStyleList(apiKey: String): List<AiModelInfo> {
        // Handles both OpenAI `{data:[{id}]}` and OpenRouter
        // `{data:[{id,name,description}]}` shapes with one parser.
        val body = sdk.get(
            url = config.baseUrl + config.listPath,
            headers = openAiAuth(apiKey)
        )
        return json.decodeFromString<OpenAiModelList>(body).data
            .map {
                AiModelInfo(
                    providerId = id,
                    id = it.id,
                    displayName = it.name?.takeIf { n -> n.isNotBlank() } ?: prettyName(it.id),
                    description = it.description
                )
            }
            .sortedBy { it.displayName.lowercase() }
    }

    private suspend fun fetchAnthropicList(apiKey: String): List<AiModelInfo> {
        val body = sdk.get(
            url = config.baseUrl + config.listPath,
            headers = anthropicAuth(apiKey)
        )
        return json.decodeFromString<AnthropicModelList>(body).data
            .map {
                AiModelInfo(
                    providerId = id,
                    id = it.id,
                    displayName = it.displayName?.takeIf { n -> n.isNotBlank() } ?: prettyName(it.id),
                    description = null
                )
            }
            .sortedBy { it.displayName.lowercase() }
    }

    private suspend fun fetchGeminiNativeList(apiKey: String): List<AiModelInfo> {
        val body = sdk.get(
            url = "${Providers.geminiNativeBase()}/models?key=$apiKey&pageSize=100",
            headers = emptyMap()
        )
        return json.decodeFromString<GeminiNativeList>(body).models
            ?.filter { m -> m.supportedMethods == null || m.supportedMethods.contains("generateContent") }
            ?.map { m ->
                val shortId = m.name.removePrefix("models/")
                AiModelInfo(id, shortId, m.displayName?.takeIf { it.isNotBlank() } ?: shortId, m.description)
            }
            ?.sortedBy { it.displayName.lowercase() }
            .orEmpty()
    }

    // ── Streaming chat (+ agentic tool loop) ──────────────────────────
    override fun streamReply(
        apiKey: String,
        modelId: String,
        history: List<ChatTurn>,
        systemPrompt: String?,
        temperature: Float
    ): Flow<AiStreamEvent> = flow {
        // Note: blank keys are allowed through (local servers like Ollama need
        // no auth); the ViewModel decides whether a missing key blocks sending.
        require(modelId.isNotBlank()) { "No model selected — star one in Settings → Models" }
        val temp = temperature.coerceIn(0f, 2f)
        // Skills are resolved per request: the same static registry serves
        // every provider, freshly filtered to tested + enabled.
        val allTools = tools + (skillTools?.load().orEmpty())
        when (config.protocol) {
            LlmProtocol.OPENAI -> runOpenAiLoop(apiKey, modelId, history, systemPrompt, temp, allTools)
            LlmProtocol.ANTHROPIC -> runAnthropicLoop(apiKey, modelId, history, systemPrompt, temp, allTools)
        }
    }

    // ── OpenAI-protocol tool loop ────────────────────────────────────
    private suspend fun FlowCollector<AiStreamEvent>.runOpenAiLoop(
        apiKey: String,
        model: String,
        history: List<ChatTurn>,
        systemPrompt: String?,
        temperature: Float,
        tools: List<AiTool>
    ) {
        val messages = mutableListOf<OpenAiMessage>().apply {
            systemPrompt?.takeIf { it.isNotBlank() }?.let { add(OpenAiMessage("system", it)) }
            history.forEach { add(OpenAiMessage(openAiRole(it.role), it.text)) }
        }
        val toolsArray = if (tools.isEmpty()) null else openAiToolsArray(tools)
        val tracker = EmitTracker()
        try {
            loopOpenAiRounds(apiKey, model, messages, temperature, toolsArray, tracker, tools)
        } catch (e: IOException) {
            // Older/partial OpenAI-compatible servers may 400 on `tools`.
            // A 400 before any chunk means the first request was rejected —
            // safe to retry once without tools (nothing was emitted yet).
            if (toolsArray != null && tracker.count == 0 && e.message?.contains("400") == true) {
                loopOpenAiRounds(apiKey, model, messages, temperature, null, EmitTracker(), emptyList())
            } else throw e
        }
    }

    private class EmitTracker { var count = 0 }

    private suspend fun FlowCollector<AiStreamEvent>.loopOpenAiRounds(
        apiKey: String,
        model: String,
        messages: MutableList<OpenAiMessage>,
        temperature: Float,
        toolsArray: JsonArray?,
        tracker: EmitTracker,
        tools: List<AiTool>
    ) {
        repeat(MAX_TOOL_ROUNDS) {
            val textAcc = StringBuilder()
            val toolAcc = OpenAiToolAcc()
            val payload = json.encodeToString(
                OpenAiChatRequest.serializer(),
                OpenAiChatRequest(
                    model = model,
                    messages = messages.toList(),
                    temperature = temperature,
                    stream = true,
                    tools = toolsArray,
                    toolChoice = if (toolsArray == null) null else "auto"
                )
            )
            sdk.postSse(
                url = config.baseUrl + config.chatPath,
                headers = openAiAuth(apiKey),
                jsonBody = payload
            ) { data ->
                val chunk = try {
                    json.decodeFromString<OpenAiChunk>(data)
                } catch (e: Exception) {
                    return@postSse null // skip one malformed chunk, keep streaming
                }
                var text: String? = null
                chunk.choices.firstOrNull()?.delta?.let { delta ->
                    delta.content?.let { text = it }
                    delta.toolCalls?.forEach { toolAcc.add(it) }
                }
                text
            }.collect {
                textAcc.append(it)
                tracker.count++
                emit(AiStreamEvent.Text(it))
            }
            val calls = toolAcc.build()
            if (calls.isEmpty()) return
            messages.add(
                OpenAiMessage(
                    role = "assistant",
                    content = textAcc.toString().ifBlank { null },
                    toolCalls = calls.map { c ->
                        OpenAiToolCallOut(
                            id = c.id,
                            function = OpenAiFunctionOut(name = c.name, arguments = c.argsJson),
                            extraContent = c.echoExtraContent()
                        )
                    }
                )
            )
            calls.forEach { c ->
                emit(AiStreamEvent.ToolStarted(c.name, toolLabel(c.name, tools), c.argsJson))
                val out = runTool(c, tools)
                emit(AiStreamEvent.Tool(c.name, toolLabel(c.name, tools), c.argsJson, out.text, out.durationMs))
                messages.add(OpenAiMessage(role = "tool", content = out.text, toolCallId = c.id))
            }
        }
    }

    // ── Anthropic tool loop ──────────────────────────────────────────
    private suspend fun FlowCollector<AiStreamEvent>.runAnthropicLoop(
        apiKey: String,
        model: String,
        history: List<ChatTurn>,
        systemPrompt: String?,
        temperature: Float,
        tools: List<AiTool>
    ) {
        val messages = mutableListOf<AnthropicMessage>().apply {
            history.forEach { add(AnthropicMessage(anthropicRole(it.role), JsonPrimitive(it.text))) }
        }
        val toolsArray = if (tools.isEmpty()) null else anthropicToolsArray(tools)
        repeat(MAX_TOOL_ROUNDS) {
            val textAcc = StringBuilder()
            val toolAcc = AnthropicToolAcc()
            val payload = json.encodeToString(
                AnthropicRequest.serializer(),
                AnthropicRequest(
                    model = model,
                    maxTokens = config.anthropicMaxTokens,
                    system = systemPrompt?.takeIf { it.isNotBlank() },
                    messages = messages.toList(),
                    temperature = temperature,
                    stream = true,
                    tools = toolsArray
                )
            )
            sdk.postSse(
                url = config.baseUrl + config.chatPath,
                headers = anthropicAuth(apiKey),
                jsonBody = payload
            ) { data ->
                val event = try {
                    json.decodeFromString<AnthropicEvent>(data)
                } catch (e: Exception) {
                    return@postSse null
                }
                toolAcc.onEvent(event)
            }.collect {
                textAcc.append(it)
                emit(AiStreamEvent.Text(it))
            }
            val calls = toolAcc.build()
            if (calls.isEmpty()) return
            val results = mutableMapOf<String, ToolOutcome>()
            calls.forEach { c ->
                emit(AiStreamEvent.ToolStarted(c.name, toolLabel(c.name, tools), c.argsJson))
                val out = runTool(c, tools)
                results[c.id] = out
                emit(AiStreamEvent.Tool(c.name, toolLabel(c.name, tools), c.argsJson, out.text, out.durationMs))
            }
            messages.add(
                AnthropicMessage(
                    role = "assistant",
                    content = buildJsonArray {
                        if (textAcc.isNotBlank()) {
                            addJsonObject {
                                put("type", "text")
                                put("text", textAcc.toString())
                            }
                        }
                        calls.forEach { c ->
                            addJsonObject {
                                put("type", "tool_use")
                                put("id", c.id)
                                put("name", c.name)
                                put("input", toolInput(c.argsJson))
                            }
                        }
                    }
                )
            )
            messages.add(
                AnthropicMessage(
                    role = "user",
                    content = buildJsonArray {
                        calls.forEach { c ->
                            addJsonObject {
                                put("type", "tool_result")
                                put("tool_use_id", c.id)
                                put("content", results.getValue(c.id).text)
                            }
                        }
                    }
                )
            )
        }
    }

    // ── Tool plumbing ────────────────────────────────────────────────
    private data class ToolOutcome(val text: String, val durationMs: Long)

    private suspend fun runTool(call: ResolvedToolCall, tools: List<AiTool>): ToolOutcome {
        val tool = tools.find { it.name == call.name }
        if (tool == null) return ToolOutcome("Error: unknown tool '${call.name}'", 0)
        val start = System.currentTimeMillis()
        return try {
            ToolOutcome(tool.execute(call.argsJson), System.currentTimeMillis() - start)
        } catch (e: Exception) {
            ToolOutcome(
                "Error: ${e.message ?: "tool failed"}",
                System.currentTimeMillis() - start
            )
        }
    }

    private fun toolLabel(name: String, tools: List<AiTool>): String =
        tools.find { it.name == name }?.displayName ?: name

    private fun openAiToolsArray(tools: List<AiTool>): JsonArray = buildJsonArray {
        tools.forEach { t ->
            addJsonObject {
                put("type", "function")
                putJsonObject("function") {
                    put("name", t.name)
                    put("description", t.description)
                    put("parameters", toolParams(t))
                }
            }
        }
    }

    private fun anthropicToolsArray(tools: List<AiTool>): JsonArray = buildJsonArray {
        tools.forEach { t ->
            addJsonObject {
                put("name", t.name)
                put("description", t.description)
                put("input_schema", toolParams(t))
            }
        }
    }

    private fun toolParams(t: AiTool): JsonElement = try {
        Json.parseToJsonElement(t.parametersJson)
    } catch (e: Exception) {
        buildJsonObject { } // never break the whole request on one bad schema
    }

    private fun toolInput(argsJson: String): JsonElement = try {
        Json.parseToJsonElement(argsJson.ifBlank { "{}" })
    } catch (e: Exception) {
        buildJsonObject { }
    }

    // ── Headers / roles ──────────────────────────────────────────────
    private fun openAiAuth(apiKey: String): Map<String, String> =
        buildMap {
            put("Authorization", "Bearer $apiKey")
            putAll(config.extraHeaders)
        }

    private fun anthropicAuth(apiKey: String): Map<String, String> = mapOf(
        "x-api-key" to apiKey,
        "anthropic-version" to config.anthropicVersion
    )

    /** Internal turns use "user"/"model" (Gemini heritage); both wire protocols want assistant. */
    private fun openAiRole(role: String): String = if (role == "model") "assistant" else "user"
    private fun anthropicRole(role: String): String = if (role == "model") "assistant" else "user"

    private fun prettyName(modelId: String): String =
        modelId.substringAfterLast("/").replace('-', ' ').replace('_', ' ')
            .split(' ').joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }

    // ── DTOs ─────────────────────────────────────────────────────────
    @Serializable private data class OpenAiMessage(
        val role: String,
        val content: String? = null,
        @SerialName("tool_call_id") val toolCallId: String? = null,
        @SerialName("tool_calls") val toolCalls: List<OpenAiToolCallOut>? = null
    )
    @Serializable private data class OpenAiToolCallOut(
        val id: String,
        val type: String = "function",
        val function: OpenAiFunctionOut,
        @SerialName("extra_content") val extraContent: JsonObject? = null
    )
    @Serializable private data class OpenAiFunctionOut(val name: String, val arguments: String)
    @Serializable private data class OpenAiChatRequest(
        val model: String,
        val messages: List<OpenAiMessage>,
        val temperature: Float?,
        val stream: Boolean,
        val tools: JsonArray? = null,
        @SerialName("tool_choice") val toolChoice: String? = null
    )
    @Serializable private data class OpenAiChunk(val choices: List<OpenAiChoice> = emptyList())
    @Serializable private data class OpenAiChoice(
        val delta: OpenAiDelta? = null,
        @SerialName("finish_reason") val finishReason: String? = null
    )
    @Serializable private data class OpenAiDelta(
        val content: String? = null,
        @SerialName("tool_calls") val toolCalls: List<OpenAiToolCallDelta>? = null
    )
    @Serializable private data class OpenAiToolCallDelta(
        val index: Int? = null,
        val id: String? = null,
        val type: String? = null,
        val function: OpenAiFunctionDelta? = null,
        // Gemini thought signature (docs: thought-signatures). Other providers
        // never send it; captured here so it can be echoed back verbatim.
        @SerialName("extra_content") val extraContent: JsonObject? = null
    )
    @Serializable private data class OpenAiFunctionDelta(
        val name: String? = null,
        val arguments: String? = null
    )

    /** Reassembles index-fragmented streaming tool calls into whole calls. */
    private class OpenAiToolAcc {
        private data class Part(
            var id: String? = null,
            var name: String? = null,
            val args: StringBuilder = StringBuilder(),
            var thoughtSignature: String? = null
        )

        private val parts = mutableMapOf<Int, Part>()

        fun add(d: OpenAiToolCallDelta) {
            val p = parts.getOrPut(d.index ?: 0) { Part() }
            d.id?.let { p.id = it }
            d.function?.name?.let { p.name = it }
            d.function?.arguments?.let { p.args.append(it) }
            // Round-trip Gemini's per-call thought signature: it arrives on
            // the tool_calls delta item and MUST be echoed back on the next
            // turn, otherwise Gemini 3 rejects the follow-up with 400.
            d.extraContent
                ?.get("google")?.jsonObject
                ?.get("thought_signature")?.jsonPrimitive?.contentOrNull
                ?.takeIf { it.isNotBlank() }
                ?.let { p.thoughtSignature = it }
        }

        fun build(): List<ResolvedToolCall> = parts.values.mapNotNull { p ->
            val id = p.id
            val name = p.name
            if (id.isNullOrBlank() || name.isNullOrBlank()) null
            else ResolvedToolCall(id, name, p.args.toString(), p.thoughtSignature)
        }
    }

    private data class ResolvedToolCall(
        val id: String,
        val name: String,
        val argsJson: String,
        val thoughtSignature: String? = null
    ) {
        /** Verbatim echo block for Gemini; null for everyone else (omitted). */
        fun echoExtraContent(): JsonObject? = thoughtSignature?.let { sig ->
            buildJsonObject {
                putJsonObject("google") {
                    put("thought_signature", sig)
                }
            }
        }
    }

    @Serializable private data class OpenAiModelList(val data: List<OpenAiModelEntry> = emptyList())
    @Serializable private data class OpenAiModelEntry(
        val id: String,
        val name: String? = null,
        val description: String? = null
    )

    @Serializable private data class AnthropicMessage(val role: String, val content: JsonElement)
    @Serializable private data class AnthropicRequest(
        val model: String,
        @SerialName("max_tokens") val maxTokens: Int,
        val system: String? = null,
        val messages: List<AnthropicMessage>,
        val temperature: Float?,
        val stream: Boolean,
        val tools: JsonArray? = null
    )
    @Serializable private data class AnthropicEvent(
        val type: String? = null,
        val index: Int? = null,
        @SerialName("content_block") val block: AnthropicBlockStart? = null,
        val delta: AnthropicDelta? = null
    )
    @Serializable private data class AnthropicBlockStart(
        val type: String? = null,
        val id: String? = null,
        val name: String? = null
    )
    @Serializable private data class AnthropicDelta(
        val type: String? = null,
        val text: String? = null,
        @SerialName("partial_json") val partialJson: String? = null
    )

    /** Tracks tool_use blocks across SSE events; text deltas pass through. */
    private class AnthropicToolAcc {
        private data class Part(
            var id: String = "",
            var name: String = "",
            val args: StringBuilder = StringBuilder()
        )

        private val parts = mutableMapOf<Int, Part>()

        /** Returns text to emit, or null for protocol-only events. */
        fun onEvent(e: AnthropicEvent): String? {
            when (e.type) {
                "content_block_start" -> {
                    if (e.block?.type == "tool_use") {
                        parts.getOrPut(e.index ?: 0) { Part() }.apply {
                            id = e.block.id.orEmpty()
                            name = e.block.name.orEmpty()
                        }
                    }
                }
                "content_block_delta" -> {
                    val d = e.delta ?: return null
                    if (d.type == "text_delta") return d.text
                    if (d.type == "input_json_delta" && d.partialJson != null) {
                        parts.getOrPut(e.index ?: 0) { Part() }.args.append(d.partialJson)
                    }
                }
                else -> Unit // message_start/stop, pings — nothing to emit
            }
            return null
        }

        fun build(): List<ResolvedToolCall> = parts.values.mapNotNull {
            if (it.id.isBlank() || it.name.isBlank()) null
            else ResolvedToolCall(it.id, it.name, it.args.toString())
        }
    }

    @Serializable private data class AnthropicModelList(val data: List<AnthropicModelEntry> = emptyList())
    @Serializable private data class AnthropicModelEntry(
        val id: String,
        @SerialName("display_name") val displayName: String? = null
    )

    @Serializable private data class GeminiNativeList(val models: List<GeminiNativeEntry>? = null)
    @Serializable private data class GeminiNativeEntry(
        val name: String = "",
        val displayName: String? = null,
        val description: String? = null,
        @SerialName("supportedGenerationMethods") val supportedMethods: List<String>? = null
    )
}
