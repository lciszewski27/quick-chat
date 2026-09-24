package dev.lciszewski27.quickchat.data.ai.tools

import com.dokar.quickjs.binding.asyncFunction
import com.dokar.quickjs.binding.function
import com.dokar.quickjs.quickJs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Runs JavaScript in a minimal sandboxed QuickJS runtime.
 *
 * Sandbox: plain QuickJS has no fetch/XHR, timers, modules, file system, or
 * Java bridge — scripts only see standard JS (Math, JSON, Array, …) plus the
 * three helpers below. A fresh engine is created per call (no state leaks),
 * execution is time-boxed, and results are length-capped for model context.
 */
class JsTool(
    private val timeoutMs: Long = 8000,
    private val maxResultChars: Int = 8000
) : AiTool {
    override val name = "run_javascript"
    override val displayName = "JavaScript"
    override val description =
        "Runs a JavaScript snippet in a minimal sandboxed runtime and returns its completion " +
            "value as text. No fetch, timers, imports, or file access — only standard JS plus: " +
            "base64Encode(text), base64Decode(b64), request(url) for a simple HTTPS GET " +
            "(15s timeout, truncated), and httpRequest(optionsJson) where optionsJson is " +
            "{\"url\":...,\"method\":\"GET|POST|PUT|DELETE|PATCH|HEAD\",\"headers\":{...},\"body\":\"...\"}. " +
            "Use for data transformation, JSON reshaping, date/encoding work, or small algorithms. " +
            "End the snippet with the value you want back (use JSON.stringify for objects)."
    override val parametersJson: String = """
        {"type":"object",
         "properties":{"code":{"type":"string","description":"JavaScript source. The completion value (last expression) is returned."}},
         "required":["code"],"additionalProperties":false}
    """.trimIndent()

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .build()

    override suspend fun execute(argsJson: String): String {
        val code = try {
            Json.parseToJsonElement(argsJson).jsonObject["code"]
                ?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
                ?: return "Error: missing required 'code' string"
        } catch (e: Exception) {
            return "Error: invalid tool arguments"
        }
        return try {
            // Blocking native + HTTP work stays off the main thread; coroutine
            // cancellation (Stop button) interrupts even infinite loops.
            val raw = withContext(Dispatchers.IO) {
                withTimeout(timeoutMs) {
                    quickJs {
                        function("base64Encode") { args ->
                            base64Encode(args.firstOrNull()?.toString().orEmpty())
                        }
                        function("base64Decode") { args ->
                            base64Decode(args.firstOrNull()?.toString().orEmpty())
                        }
                        asyncFunction("request") { url: String ->
                            httpFetch(url, "GET", emptyMap(), null)
                        }
                        asyncFunction("httpRequest") { optionsJson: String ->
                            httpFetchOptions(optionsJson)
                        }
                        stringify(evaluate<Any?>(code))
                    }
                }
            }
            cap(raw)
        } catch (e: TimeoutCancellationException) {
            "Error: script timed out after ${timeoutMs}ms"
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            "Error: ${e.message?.take(300) ?: "script failed"}"
        }
    }

    // ── JS helpers ───────────────────────────────────────────────────
    private fun base64Encode(text: String): String =
        java.util.Base64.getEncoder().encodeToString(text.toByteArray(Charsets.UTF_8))

    private fun base64Decode(b64: String): String = try {
        String(java.util.Base64.getDecoder().decode(b64.trim()), Charsets.UTF_8)
    } catch (e: IllegalArgumentException) {
        throw IllegalArgumentException("invalid base64 input")
    }

    private fun httpFetch(url: String, method: String, headers: Map<String, String>, body: String?): String {
        require(url.startsWith("http://") || url.startsWith("https://")) {
            "only http(s) URLs allowed"
        }
        val m = method.uppercase()
        require(m in setOf("GET", "POST", "PUT", "DELETE", "PATCH", "HEAD")) {
            "unsupported method $m"
        }
        val builder = Request.Builder().url(url)
        headers.forEach { (k, v) -> builder.header(k, v) }
        if (m == "GET" || m == "HEAD") builder.method(m, null)
        else builder.method(m, (body.orEmpty()).toRequestBody("text/plain".toMediaType()))
        http.newCall(builder.build()).execute().use { response ->
            if (!response.isSuccessful) throw java.io.IOException("HTTP ${response.code}")
            return cap(readBounded(response))
        }
    }

    /**
     * Reads at most [MAX_RESPONSE_BYTES] without throwing on short bodies
     * (Okio's readUtf8(n) throws EOFException via require() when fewer than
     * n bytes exist — the exact crash seen from request()).
     */
    private fun readBounded(response: okhttp3.Response): String {
        val stream = response.body?.byteStream() ?: return ""
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(8192)
        var total = 0L
        while (total < MAX_RESPONSE_BYTES) {
            val want = minOf(buf.size.toLong(), MAX_RESPONSE_BYTES - total).toInt()
            val n = stream.read(buf, 0, want)
            if (n == -1) break
            out.write(buf, 0, n)
            total += n
        }
        return out.toString(Charsets.UTF_8.name())
    }

    private fun httpFetchOptions(optionsJson: String): String {
        val obj = try {
            Json.parseToJsonElement(optionsJson).jsonObject
        } catch (e: Exception) {
            throw IllegalArgumentException("options must be a JSON object string")
        }
        val url = obj["url"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("options.url is required")
        val method = obj["method"]?.jsonPrimitive?.contentOrNull ?: "GET"
        val headers = obj["headers"]?.jsonObject
            ?.mapValues { it.value.jsonPrimitive.content } ?: emptyMap()
        val body = obj["body"]?.jsonPrimitive?.contentOrNull
        return httpFetch(url, method, headers, body)
    }

    // ── Result mapping ───────────────────────────────────────────────
    private fun stringify(value: Any?): String = when (value) {
        null -> "null"
        is String -> value
        is Number, is Boolean -> value.toString()
        is Map<*, *> -> toElement(value).toString()
        is List<*> -> toElement(value).toString()
        else -> value.toString()
    }

    private fun toElement(value: Any?): JsonElement = when (value) {
        null -> JsonNull
        is String -> JsonPrimitive(value)
        is Boolean -> JsonPrimitive(value)
        is Number -> when (value) {
            is Long, is Int, is Short, is Byte -> JsonPrimitive(value.toLong())
            else -> JsonPrimitive(value.toDouble())
        }
        is Map<*, *> -> buildJsonObject {
            value.forEach { (k, v) -> put(k.toString(), toElement(v)) }
        }
        is List<*> -> buildJsonArray { value.forEach { add(toElement(it)) } }
        else -> JsonPrimitive(value.toString())
    }

    private fun cap(text: String): String =
        if (text.length > maxResultChars) text.take(maxResultChars) + "\n…[truncated]"
        else text

    companion object {
        const val MAX_RESPONSE_BYTES = 65536L
    }
}
