package dev.lciszewski27.quickchat.data.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * The app's single AI transport SDK, shared by every provider.
 *
 * Why one SDK instead of one vendor SDK per provider: no single third-party
 * Android SDK natively covers OpenAI + Claude + Gemini + OpenRouter with
 * streaming. The industry-standard unification is the OpenAI-compatible
 * `chat/completions` protocol (spoken by OpenAI, OpenRouter, DeepSeek,
 * Ollama, and Gemini's `v1beta/openai` endpoint) plus Anthropic's Messages
 * SSE format — both implemented here over OkHttp with Kotlin [Flow] chunks.
 *
 * - [postSse]: POSTs JSON, parses the SSE event stream, emits extracted text
 *   deltas. `[DONE]` (OpenAI-protocol) or EOF (Anthropic) ends the stream.
 *   Cancelling the collecting coroutine cancels the HTTP call, so the Stop
 *   button genuinely stops generation and billing.
 * - [get]: one-shot GET for model catalogs.
 */
class LlmSdk(
    val client: OkHttpClient = defaultClient()
) {
    /**
     * Streams text deltas from an SSE endpoint.
     * @param extractText maps one SSE `data:` payload to its text delta (or null to skip).
     * @throws IOException on transport/HTTP/API errors.
     */
    fun postSse(
        url: String,
        headers: Map<String, String>,
        jsonBody: String,
        extractText: (dataPayload: String) -> String?
    ): Flow<String> = flow {
        val request = Request.Builder()
            .url(url)
            .post(jsonBody.toRequestBody("application/json".toMediaType()))
            .apply { headers.forEach { (k, v) -> header(k, v) } }
            .header("Accept", "text/event-stream")
            .build()
        val call = client.newCall(request)
        // Stop button / navigation-away cancels the socket, not just the collector.
        currentCoroutineContext()[Job]?.invokeOnCompletion { call.cancel() }
        try {
            call.execute().use { response ->
                if (!response.isSuccessful) {
                    val snippet = try {
                        response.body?.string()?.take(400)
                    } catch (e: Exception) {
                        null
                    }
                    throw IOException("HTTP ${response.code}${snippet?.let { ": $it" } ?: ""}")
                }
                val source = response.body?.source()
                    ?: throw IOException("Empty response body")
                val dataLines = mutableListOf<String>()
                while (true) {
                    val line = try {
                        source.readUtf8Line()
                    } catch (e: IOException) {
                        // Socket closed by our own cancel() -> normal stop, not an error.
                        if (!currentCoroutineContext().isActive) return@flow
                        throw e
                    } ?: break // EOF
                    when {
                        line.isEmpty() -> {
                            // Blank line = dispatch event.
                            if (dataLines.isNotEmpty()) {
                                val payload = dataLines.joinToString("\n")
                                dataLines.clear()
                                if (payload == "[DONE]") return@flow
                                val text = try {
                                    extractText(payload)
                                } catch (e: Exception) {
                                    null // Skip one malformed chunk, keep streaming.
                                }
                                if (!text.isNullOrEmpty()) emit(text)
                            }
                        }
                        line.startsWith("data:") -> dataLines.add(line.removePrefix("data:").trimStart())
                        line.startsWith(":") -> Unit // SSE comment / keep-alive — ignore.
                        // `event:` lines are intentionally ignored: both protocols are
                        // identified by payload shape, which is robust to event renames.
                    }
                }
                // Trailing event without a terminating blank line (defensive).
                if (dataLines.isNotEmpty() && currentCoroutineContext().isActive) {
                    val payload = dataLines.joinToString("\n")
                    if (payload != "[DONE]") {
                        try {
                            extractText(payload)?.takeIf { it.isNotEmpty() }?.let { emit(it) }
                        } catch (e: Exception) {
                            Unit
                        }
                    }
                }
            }
        } catch (e: IOException) {
            if (!currentCoroutineContext().isActive) return@flow // Cancelled — swallow.
            throw e
        }
    }.flowOn(Dispatchers.IO)

    suspend fun get(url: String, headers: Map<String, String>): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .get()
            .apply { headers.forEach { (k, v) -> header(k, v) } }
            .header("Accept", "application/json")
            .build()
        client.newCall(request).execute().use { response ->
            val body = try {
                response.body?.string().orEmpty()
            } catch (e: Exception) {
                ""
            }
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}: ${body.take(300)}")
            body
        }
    }

    companion object {
        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            // Long stall timeout: tokens can pause mid-stream on slow models.
            .readTimeout(60, TimeUnit.SECONDS)
            .callTimeout(5, TimeUnit.MINUTES)
            .retryOnConnectionFailure(true)
            .build()
    }
}
