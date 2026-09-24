package dev.lciszewski27.quickchat

import dev.lciszewski27.quickchat.data.ai.skills.CreateSkillTool
import dev.lciszewski27.quickchat.data.ai.skills.SkillExecutor
import dev.lciszewski27.quickchat.data.ai.skills.SkillValidation
import dev.lciszewski27.quickchat.domain.model.ChatMessage
import dev.lciszewski27.quickchat.domain.model.ChatRole
import dev.lciszewski27.quickchat.domain.model.ChatSession
import dev.lciszewski27.quickchat.domain.model.FavoriteModel
import dev.lciszewski27.quickchat.domain.model.ProviderInstance
import dev.lciszewski27.quickchat.domain.model.Skill
import dev.lciszewski27.quickchat.domain.model.ToolCallRecord
import dev.lciszewski27.quickchat.domain.repository.ChatRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SkillsTest {

    // ── Validation (pure) ────────────────────────────────────────────
    @Test
    fun toolName_isSanitizedAndUnique() {
        assertEquals("my_cool_skill", SkillValidation.sanitizeToolName("My Cool Skill!"))
        assertEquals("skill", SkillValidation.sanitizeToolName("!!!"))
        assertEquals(
            "weather-2",
            SkillValidation.ensureUnique("weather", setOf("weather", "calculate"))
        )
        assertEquals("weather", SkillValidation.ensureUnique("weather", setOf("other")))
    }

    @Test
    fun paramsSchema_defaultsAndValidates() {
        assertEquals(
            """{"type":"object"}""",
            SkillValidation.normalizeParamsSchema(null).getOrThrow()
        )
        assertTrue(SkillValidation.normalizeParamsSchema("""{"type":"object"}""").isSuccess)
        assertTrue(SkillValidation.normalizeParamsSchema("nope").isFailure)
        assertTrue(SkillValidation.normalizeParamsSchema("""{"type":"string"}""").isFailure)
    }

    @Test
    fun secretDecls_parseAndReject() {
        val ok = SkillValidation.parseSecretDecls(
            """[{"name":"API_TOKEN","description":"key for X"}]"""
        ).getOrThrow()
        assertEquals(1, ok.size)
        assertEquals("API_TOKEN", ok[0].name)
        assertTrue(SkillValidation.parseSecretDecls(null).getOrThrow().isEmpty())
        assertTrue(SkillValidation.parseSecretDecls("""[{"name":"bad name"}]""").isFailure)
        assertTrue(
            SkillValidation.parseSecretDecls(
                """[{"name":"A"},{"name":"A"}]"""
            ).isFailure
        )
    }

    // ── create_skill flow (fake repo, real executor) ─────────────────
    @Test
    fun createSkill_savesDraftAndTrialRuns() = runBlocking {
        val repo = FakeSkillRepo()
        val tool = CreateSkillTool(repo, SkillExecutor(repo), setOf("calculate"))
        val out = tool.execute(
            """{"name":"Doubler","description":"Doubles a number",
               |"code":"return args.x * 2;",
               |"paramsSchema":"{\"type\":\"object\",\"properties\":{\"x\":{\"type\":\"number\"}}}",
               |"testArgs":"{\"x\":21}"}""".trimMargin()
        )
        assertTrue(out.contains("draft"))
        assertTrue(out.contains("Trial run OK"))
        assertTrue(out.contains("42"))
        val saved = repo.skills().single()
        assertEquals("doubler", saved.toolName)
        // Gate: drafts are never active.
        assertTrue(!saved.enabled && !saved.tested)
    }

    @Test
    fun createSkill_rejectsBadInput() = runBlocking {
        val repo = FakeSkillRepo()
        val tool = CreateSkillTool(repo, SkillExecutor(repo), emptySet())
        assertTrue(tool.execute("""{"name":"x"}""").startsWith("Error"))
        assertTrue(
            tool.execute("""{"name":"x","description":"d","code":"return 1","paramsSchema":"zzz"}""")
                .startsWith("Error")
        )
        assertTrue(repo.skills().isEmpty())
    }

    @Test
    fun skillExecutor_injectsSecretsOnlyAtRuntime() = runBlocking {
        val repo = FakeSkillRepo()
        val executor = SkillExecutor(repo)
        val skill = Skill(
            id = "s1", name = "S", toolName = "s", description = "d",
            code = "return args.a + ':' + secrets.TOKEN;",
            paramsSchema = """{"type":"object"}""",
            secrets = listOf(dev.lciszewski27.quickchat.domain.model.SecretDecl("TOKEN")),
            enabled = true, tested = true, createdAt = 0, updatedAt = 0
        )
        repo.upsertSkill(skill)
        repo.setSecretValue("s1", "TOKEN", "hunter2")
        assertEquals("1:hunter2", executor.run("s1", """{"a":1}"""))
    }

    /** Minimal fake: real skill/secret storage, TODO() elsewhere. */
    private class FakeSkillRepo : ChatRepository {
        private val skillsFlow = MutableStateFlow<List<Skill>>(emptyList())
        private val secrets = mutableMapOf<String, MutableMap<String, String>>()

        fun skills(): List<Skill> = skillsFlow.value

        override fun observeSkills(): Flow<List<Skill>> = skillsFlow.asStateFlow()
        override suspend fun getSkill(id: String): Skill? = skillsFlow.value.find { it.id == id }
        override suspend fun upsertSkill(skill: Skill) {
            skillsFlow.value = skillsFlow.value.filterNot { it.id == skill.id } + skill
        }
        override suspend fun deleteSkill(id: String) {
            skillsFlow.value = skillsFlow.value.filterNot { it.id == id }
            secrets.remove(id)
        }
        override suspend fun setSkillEnabled(id: String, enabled: Boolean) {
            skillsFlow.value = skillsFlow.value.map { if (it.id == id) it.copy(enabled = enabled) else it }
        }
        override suspend fun setSkillTested(id: String, tested: Boolean) {
            skillsFlow.value = skillsFlow.value.map { if (it.id == id) it.copy(tested = tested) else it }
        }
        override suspend fun getSecretValues(skillId: String): Map<String, String> =
            secrets[skillId].orEmpty()
        override suspend fun setSecretValue(skillId: String, name: String, value: String) {
            secrets.getOrPut(skillId) { mutableMapOf() }[name] = value
        }
        override suspend fun deleteSecretValue(skillId: String, name: String) {
            secrets[skillId]?.remove(name)
        }

        override fun observeSessions(): Flow<List<ChatSession>> = TODO()
        override fun observeMessages(sessionId: String): Flow<List<ChatMessage>> = TODO()
        override suspend fun createSession(providerId: String, modelId: String): ChatSession = TODO()
        override suspend fun getSession(sessionId: String): ChatSession? = TODO()
        override suspend fun appendMessage(sessionId: String, role: ChatRole, text: String, isError: Boolean): ChatMessage = TODO()
        override suspend fun updateSessionModel(sessionId: String, providerId: String, modelId: String) = TODO()
        override suspend fun renameSession(sessionId: String, title: String) = TODO()
        override suspend fun deleteSession(sessionId: String) = TODO()
        override suspend fun clearSession(sessionId: String) = TODO()
        override fun observeFavorites(): Flow<List<FavoriteModel>> = TODO()
        override suspend fun isFavorite(providerId: String, modelId: String): Boolean = TODO()
        override suspend fun addFavorite(providerId: String, modelId: String, displayName: String) = TODO()
        override suspend fun removeFavorite(providerId: String, modelId: String) = TODO()
        override fun observeProviderInstances(): Flow<List<ProviderInstance>> = TODO()
        override suspend fun upsertProviderInstance(instance: ProviderInstance) = TODO()
        override suspend fun removeProviderInstance(instanceId: String) = TODO()
        override fun observeToolCalls(sessionId: String): Flow<List<ToolCallRecord>> = TODO()
        override suspend fun appendToolCall(
            sessionId: String, name: String, displayName: String,
            argsJson: String, result: String, durationMs: Long
        ): ToolCallRecord = TODO()
    }
}
