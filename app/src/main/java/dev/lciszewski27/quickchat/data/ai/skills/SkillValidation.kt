package dev.lciszewski27.quickchat.data.ai.skills

import dev.lciszewski27.quickchat.data.ai.tools.AiTool
import dev.lciszewski27.quickchat.domain.model.SecretDecl
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Pure validation for skill creation (unit-testable without a database).
 */
object SkillValidation {
    const val MAX_CODE_CHARS = 20000
    const val MAX_NAME_CHARS = 64
    const val TOOL_NAME_PATTERN = "^[a-zA-Z0-9_-]{1,64}$"
    const val SECRET_NAME_PATTERN = "^[A-Z0-9_]{1,64}$"

    /** `My Cool Skill!` → `my_cool_skill`. Guaranteed non-blank. */
    fun sanitizeToolName(raw: String): String {
        val cleaned = raw.lowercase()
            .map { c -> if (c.isLetterOrDigit() || c == '-' || c == '_') c else '_' }
            .joinToString("")
            .trim('_')
            .take(MAX_NAME_CHARS)
        return cleaned.ifBlank { "skill" }
    }

    fun ensureUnique(base: String, taken: Set<String>): String {
        if (base !in taken) return base
        var i = 2
        while ("$base-$i" in taken) i++
        return "$base-$i"
    }

    /** Blank → default `{}`; must be a JSON object with type "object" (coerced). */
    fun normalizeParamsSchema(raw: String?): Result<String> {
        if (raw.isNullOrBlank()) return Result.success("""{"type":"object"}""")
        return try {
            val obj = Json.parseToJsonElement(raw).jsonObject
            if (obj["type"]?.jsonPrimitive?.contentOrNull != "object") {
                return Result.failure(IllegalArgumentException("paramsSchema must be a JSON object schema"))
            }
            Result.success(Json.encodeToString(JsonObject.serializer(), obj))
        } catch (e: Exception) {
            Result.failure(IllegalArgumentException("paramsSchema is not valid JSON"))
        }
    }

    /**
     * Accepts a JSON array of {"name","description"} (or a JSON-encoded string
     * of one — models sometimes double-encode). Returns declarations.
     */
    fun parseSecretDecls(raw: String?): Result<List<SecretDecl>> {
        if (raw.isNullOrBlank()) return Result.success(emptyList())
        return try {
            val text = raw.trim()
            val element = Json.parseToJsonElement(text)
            val array: JsonArray = when {
                element is JsonArray -> element
                element is JsonPrimitive && element.isString ->
                    Json.parseToJsonElement(element.content).jsonArray
                else -> return Result.failure(IllegalArgumentException("secrets must be an array"))
            }
            val seen = mutableSetOf<String>()
            val decls = array.map { item ->
                val obj = item.jsonObject
                val name = obj["name"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
                if (!name.matches(Regex(SECRET_NAME_PATTERN))) {
                    throw IllegalArgumentException("secret name '$name' must match $SECRET_NAME_PATTERN")
                }
                if (!seen.add(name)) throw IllegalArgumentException("duplicate secret '$name'")
                SecretDecl(
                    name = name,
                    description = obj["description"]?.jsonPrimitive?.contentOrNull.orEmpty()
                )
            }
            Result.success(decls)
        } catch (e: IllegalArgumentException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(IllegalArgumentException("secrets is not valid JSON"))
        }
    }
}
