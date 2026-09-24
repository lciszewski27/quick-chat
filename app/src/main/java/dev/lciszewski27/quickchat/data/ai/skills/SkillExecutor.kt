package dev.lciszewski27.quickchat.data.ai.skills

import dev.lciszewski27.quickchat.data.ai.tools.JsSandbox
import dev.lciszewski27.quickchat.domain.repository.ChatRepository
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject

/**
 * Runs persisted skills through the shared [JsSandbox]. Secret values are
 * loaded here, at runtime, and injected as the `secrets` global — tool
 * definitions (built from the skill row only) never contain them, so the
 * model can neither see nor exfiltrate them.
 */
class SkillExecutor(
    private val repository: ChatRepository,
    private val timeoutMs: Long = 15000,
    private val maxResultChars: Int = 8000,
    private val maxArgsChars: Int = 200_000
) {
    suspend fun run(skillId: String, argsJson: String): String {
        val skill = repository.getSkill(skillId) ?: return "Error: skill not found"
        val args = parseArgs(argsJson) ?: return "Error: skill arguments must be a JSON object"
        val secrets = repository.getSecretValues(skillId)
            .filterKeys { name -> skill.secrets.any { it.name == name } }
        return JsSandbox.eval(
            code = skill.code,
            args = args,
            secrets = secrets,
            timeoutMs = timeoutMs,
            maxResultChars = maxResultChars
        )
    }

    /** Trial run for unsaved code (creation flow / Settings test). */
    suspend fun trialRun(code: String, argsJson: String, secrets: Map<String, String>): String {
        val args = parseArgs(argsJson) ?: return "Error: test arguments must be a JSON object"
        return JsSandbox.eval(
            code = code,
            args = args,
            secrets = secrets,
            timeoutMs = timeoutMs,
            maxResultChars = maxResultChars
        )
    }

    private fun parseArgs(argsJson: String): JsonObject? {
        if (argsJson.length > maxArgsChars) return null
        return try {
            val el = Json.parseToJsonElement(argsJson.ifBlank { "{}" })
            el as? JsonObject
        } catch (e: Exception) {
            null
        }
    }

    fun emptyArgs(): JsonObject = buildJsonObject { }
}
