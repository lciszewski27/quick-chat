package dev.lciszewski27.quickchat.data.ai.tools

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Runs JavaScript in a minimal sandboxed QuickJS runtime ([JsSandbox]).
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
        "Runs a JavaScript snippet in a minimal sandboxed runtime and returns its result " +
            "as text. No fetch, timers, imports, or file access — only standard JS plus " +
            "three synchronous helpers (return values directly, never use await): " +
            "base64Encode(text), base64Decode(b64), request(url) for a simple HTTPS GET " +
            "(15s timeout, truncated), and httpRequest(optionsJson) where optionsJson is " +
            "{\"url\":...,\"method\":\"GET|POST|PUT|DELETE|PATCH|HEAD\",\"headers\":{...},\"body\":\"...\"}. " +
            "Use for data transformation, JSON reshaping, date/encoding work, or small algorithms. " +
            "End the snippet with the value you want back or `return` it " +
            "(use JSON.stringify for objects)." +
            "Don't make comments in code"
    override val parametersJson: String = """
        {"type":"object",
         "properties":{"code":{"type":"string","description":"JavaScript source. The completion value (last expression) is returned."}},
         "required":["code"],"additionalProperties":false}
    """.trimIndent()

    override suspend fun execute(argsJson: String): String {
        val code = try {
            Json.parseToJsonElement(argsJson).jsonObject["code"]
                ?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
                ?: return "Error: missing required 'code' string"
        } catch (e: Exception) {
            return "Error: invalid tool arguments"
        }
        return JsSandbox.eval(code, timeoutMs = timeoutMs, maxResultChars = maxResultChars)
    }
}
