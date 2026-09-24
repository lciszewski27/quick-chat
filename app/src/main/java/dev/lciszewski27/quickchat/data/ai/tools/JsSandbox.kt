package dev.lciszewski27.quickchat.data.ai.tools

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
import kotlinx.serialization.json.JsonObject
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
 * The single sandboxed JS runtime shared by the ad-hoc `run_javascript`
 * tool and persisted user skills.
 *
 * Sandbox: plain QuickJS exposes no fetch/XHR, timers, modules, file system
 * or Java bridge — scripts see standard JS (Math, JSON, Array, …) plus the
 * helpers defined below. A fresh engine is created per call (no state
 * leaks), execution is time-boxed, results are length-capped.
 *
 * Skill scripts additionally receive two globals:
 * - `args`: the tool-call arguments object (parsed JSON).
 * - `secrets`: declared secret values, injected at runtime. The model never
 *   sees these values — only setting them in Settings fills this object.
 */
object JsSandbox {

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun eval(
        code: String,
        args: JsonObject = emptyJsonObject(),
        secrets: Map<String, String> = emptyMap(),
        timeoutMs: Long = 8000,
        maxResultChars: Int = 8000
    ): String {
        if (code.isBlank()) return "Error: empty code"
        if (code.length > MAX_CODE_CHARS) return "Error: code too long (max $MAX_CODE_CHARS chars)"
        return try {
            // Blocking native + HTTP work stays off the main thread; coroutine
            // cancellation (Stop button) interrupts even infinite loops.
            val raw = withContext(Dispatchers.IO) {
                withTimeout(timeoutMs) {
                    quickJs {
                        function("base64Encode") { a ->
                            base64Encode(a.firstOrNull()?.toString().orEmpty())
                        }
                        function("base64Decode") { a ->
                            base64Decode(a.firstOrNull()?.toString().orEmpty())
                        }
                        // Synchronous helpers: request() returns the response
                        // text directly, so callers must NOT use await on them.
                        function("request") { a ->
                            httpFetch(
                                a.firstOrNull()?.toString().orEmpty(),
                                "GET", emptyMap(), null
                            )
                        }
                        function("httpRequest") { a ->
                            httpFetchOptions(a.firstOrNull()?.toString().orEmpty())
                        }
                        // Wrapped in a function so a top-level `return` is
                        // legal. The opening stays on line 1 with no prepended
                        // newline, so error line numbers still match the
                        // snippet; the trailing newline terminates a `//` tail.
                        // `args`/`secrets` are declared outside the wrapper so
                        // user code (and its functions) can read them.
                        val script = "var args = (" + args.toString() + ");\n" +
                            "var secrets = (" + secretsJson(secrets) + ");\n" +
                            "(function(){" + code + "\n})()"
                        stringify(evaluate<Any?>(script))
                    }
                }
            }
            cap(raw, maxResultChars)
        } catch (e: TimeoutCancellationException) {
            "Error: script timed out after ${timeoutMs}ms"
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            "Error: ${e.message?.take(300) ?: "script failed"}"
        }
    }

    private fun emptyJsonObject(): JsonObject = buildJsonObject { }

    private fun secretsJson(secrets: Map<String, String>): String =
        buildJsonObject {
            secrets.forEach { (k, v) -> put(k, v) }
        }.toString()

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
            return cap(readBounded(response), MAX_RESULT_CHARS)
        }
    }

    /**
     * Reads at most [MAX_RESPONSE_BYTES] without throwing on short bodies
     * (Okio's readUtf8(n) throws EOFException via require() when fewer than
     * n bytes exist).
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

    private fun cap(text: String, max: Int = MAX_RESULT_CHARS): String =
        if (text.length > max) text.take(max) + "\n…[truncated]"
        else text

    const val MAX_RESPONSE_BYTES = 65536L
    const val MAX_RESULT_CHARS = 8000
    const val MAX_CODE_CHARS = 20000
}
