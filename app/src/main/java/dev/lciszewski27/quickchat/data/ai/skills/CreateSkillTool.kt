package dev.lciszewski27.quickchat.data.ai.skills

import dev.lciszewski27.quickchat.data.ai.tools.AiTool
import dev.lciszewski27.quickchat.domain.model.Skill
import dev.lciszewski27.quickchat.domain.repository.ChatRepository
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.UUID

/**
 * Meta-tool: lets the assistant create a persistent JavaScript skill from a
 * user request ("make a skill that …").
 *
 * The skill is saved as an untested draft and is hidden from the model until
 * a human passes it in Settings → Skills (test gate). An optional trial run
 * executes immediately so syntax errors surface while the model can still
 * fix them — trial secrets are empty, so skills needing secrets report that
 * and must be tested in Settings with real values.
 */
class CreateSkillTool(
    private val repository: ChatRepository,
    private val executor: SkillExecutor,
    private val builtInNames: Set<String> = emptySet()
) : AiTool {
    override val name = "create_skill"
    override val displayName = "Create skill"
    override val description =
        "Creates a persistent JavaScript skill that does what the user asked, usable in " +
            "this and future conversations. The skill runs sandboxed (same runtime as " +
            "run_javascript: standard JS plus base64Encode/base64Decode/request/httpRequest, " +
            "a top-level `return` or trailing expression produces the result, `args` holds " +
            "the call arguments object). Saved as a draft first: it stays hidden from you " +
            "until the user tests it in Settings → Skills. " +
            "Secrets (API keys etc.): declare names only in `secrets` — values are set by " +
            "the user in Settings and injected at runtime as the `secrets` global; you never " +
            "see them, so never invent or echo secret values. " +
            "If `testArgs` are given, the skill trial-runs immediately (without secrets) " +
            "and the outcome is reported back."
    override val parametersJson: String = """
        {"type":"object",
         "properties":{
           "name":{"type":"string","description":"Short human name, e.g. Weather lookup"},
           "description":{"type":"string","description":"What the skill does and when to use it"},
           "code":{"type":"string","description":"JavaScript source. Receives `args` object and `secrets` object globals."},
           "paramsSchema":{"type":"string","description":"Optional JSON Schema object for arguments (default {}). Example: {\"type\":\"object\",\"properties\":{\"city\":{\"type\":\"string\"}},\"required\":[\"city\"]}"},
           "secrets":{"type":"string","description":"Optional JSON array of {\"name\":\"API_TOKEN\",\"description\":\"...\"}. Names must match ^[A-Z0-9_]{1,64}$. Values are NEVER provided here."},
           "testArgs":{"type":"string","description":"Optional JSON object to trial-run the skill now (secrets will be empty during the trial)."}
         },
         "required":["name","description","code"],"additionalProperties":false}
    """.trimIndent()

    override suspend fun execute(argsJson: String): String {
        val obj = try {
            Json.parseToJsonElement(argsJson).jsonObject
        } catch (e: Exception) {
            return "Error: invalid tool arguments"
        }
        fun str(key: String): String? =
            obj[key]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }

        val name = str("name") ?: return "Error: missing required 'name'"
        val description = str("description") ?: return "Error: missing required 'description'"
        val code = str("code") ?: return "Error: missing required 'code'"
        if (code.length > SkillValidation.MAX_CODE_CHARS) {
            return "Error: code too long (max ${SkillValidation.MAX_CODE_CHARS} chars)"
        }
        val params = SkillValidation.normalizeParamsSchema(str("paramsSchema"))
            .getOrElse { return "Error: ${it.message}" }
        val secrets = SkillValidation.parseSecretDecls(str("secrets"))
            .getOrElse { return "Error: ${it.message}" }

        val existing = repository.observeSkills().first().map { it.toolName }.toSet()
        val toolName = SkillValidation.ensureUnique(
            SkillValidation.sanitizeToolName(name),
            existing + builtInNames + setOf(this.name)
        )
        val now = System.currentTimeMillis()
        val skill = Skill(
            id = UUID.randomUUID().toString(),
            name = name.take(SkillValidation.MAX_NAME_CHARS),
            toolName = toolName,
            description = description,
            code = code,
            paramsSchema = params,
            secrets = secrets,
            enabled = false,
            tested = false,
            createdAt = now,
            updatedAt = now
        )
        repository.upsertSkill(skill)

        val testArgs = str("testArgs")
        val trial = if (testArgs != null) {
            val outcome = executor.trialRun(code, testArgs, emptyMap())
            if (outcome.startsWith("Error")) {
                "\nTrial run FAILED: $outcome\n" +
                    "You may call create_skill again with corrected code (saves a new draft), " +
                    "or the user can edit and test it in Settings → Skills."
            } else {
                "\nTrial run OK, output: ${outcome.take(1000)}"
            }
        } else {
            "\nNo testArgs provided, so no trial run happened."
        }
        return "Skill '${skill.name}' saved as a draft (tool name: $toolName). " +
            "It is hidden from you until the user tests it in Settings → Skills." +
            trial
    }
}
