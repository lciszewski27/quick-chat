package dev.lciszewski27.quickchat.data.ai.skills

import dev.lciszewski27.quickchat.data.ai.tools.AiTool
import dev.lciszewski27.quickchat.domain.repository.ChatRepository
import kotlinx.coroutines.flow.first

/**
 * Inventory tool: the model can only *call* tested + enabled skills, so a
 * user referring to anything else ("use the skill I just made") would fail
 * opaquely. This tool shows every saved skill with its status, letting the
 * model explain the draft → test → active path instead of guessing.
 */
class ListSkillsTool(
    private val repository: ChatRepository
) : AiTool {
    override val name = "list_skills"
    override val displayName = "List skills"
    override val description =
        "Lists all saved skills with their status. Skills marked ACTIVE are directly " +
            "callable tools. DRAFT skills (just created, untested) and switched-off skills " +
            "are NOT callable — tell the user to test them in Settings → Skills first. " +
            "Call this when the user refers to a skill you don't have as a callable tool."
    override val parametersJson: String = """{"type":"object","properties":{}}"""

    override suspend fun execute(argsJson: String): String {
        val skills = repository.observeSkills().first()
        if (skills.isEmpty()) return "No skills saved yet."
        return skills.joinToString("\n") { s ->
            val status = when {
                s.enabled && s.tested -> "ACTIVE"
                s.tested -> "tested but OFF"
                else -> "DRAFT (untested)"
            }
            val desc = s.description.take(160).replace("\n", " ")
            val secrets = if (s.secrets.isEmpty()) "no secrets"
            else "secrets: " + s.secrets.joinToString(", ") { it.name }
            "• ${s.name} (tool: ${s.toolName}) — $status — $desc [$secrets]"
        }
    }
}
