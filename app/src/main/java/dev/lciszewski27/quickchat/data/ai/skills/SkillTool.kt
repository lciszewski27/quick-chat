package dev.lciszewski27.quickchat.data.ai.skills

import dev.lciszewski27.quickchat.data.ai.tools.AiTool
import dev.lciszewski27.quickchat.domain.model.Skill

/**
 * A persisted user skill, exposed to the model like any built-in tool.
 * The definition carries schemas and secret *declarations* only — values
 * are injected by [SkillExecutor] at runtime and never leave the device.
 */
class SkillTool(
    private val skill: Skill,
    private val executor: SkillExecutor
) : AiTool {
    override val name: String get() = skill.toolName
    override val displayName: String get() = skill.name
    override val description: String get() = buildString {
        append(skill.description.ifBlank { "User-defined skill '${skill.name}'." })
        if (skill.secrets.isNotEmpty()) {
            append("\nRequires configured secrets (set by the user in Settings, values hidden from you): ")
            append(skill.secrets.joinToString(", ") { it.name })
        }
    }
    override val parametersJson: String get() = skill.paramsSchema

    override suspend fun execute(argsJson: String): String =
        executor.run(skill.id, argsJson)
}
