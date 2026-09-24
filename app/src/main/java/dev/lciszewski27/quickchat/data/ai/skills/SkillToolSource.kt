package dev.lciszewski27.quickchat.data.ai.skills

import dev.lciszewski27.quickchat.data.ai.tools.AiTool
import dev.lciszewski27.quickchat.domain.repository.ChatRepository
import kotlinx.coroutines.flow.first

/** Loads the model's skill tools at request time (skills change at runtime). */
interface SkillToolSource {
    suspend fun load(): List<AiTool>
}

/**
 * Exposes only tested + enabled skills. Drafts, untested skills and disabled
 * skills never reach any provider — the test gate in Settings is the only
 * path to activation.
 */
class RepoSkillToolSource(
    private val repository: ChatRepository,
    private val executor: SkillExecutor
) : SkillToolSource {
    override suspend fun load(): List<AiTool> =
        repository.observeSkills().first()
            .filter { it.enabled && it.tested }
            .map { SkillTool(it, executor) }
}
