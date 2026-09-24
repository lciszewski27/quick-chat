package dev.lciszewski27.quickchat.data.repository

import dev.lciszewski27.quickchat.data.local.dao.ChatMessageDao
import dev.lciszewski27.quickchat.data.local.dao.ChatSessionDao
import dev.lciszewski27.quickchat.data.local.dao.FavoriteModelDao
import dev.lciszewski27.quickchat.data.local.dao.ProviderInstanceDao
import dev.lciszewski27.quickchat.data.local.dao.SkillDao
import dev.lciszewski27.quickchat.data.local.dao.SkillSecretDao
import dev.lciszewski27.quickchat.data.local.dao.ToolCallDao
import dev.lciszewski27.quickchat.data.local.entity.ChatMessageEntity
import dev.lciszewski27.quickchat.data.local.entity.ChatSessionEntity
import dev.lciszewski27.quickchat.data.local.entity.FavoriteModelEntity
import dev.lciszewski27.quickchat.data.local.entity.ProviderInstanceEntity
import dev.lciszewski27.quickchat.data.local.entity.SkillEntity
import dev.lciszewski27.quickchat.data.local.entity.SkillSecretEntity
import dev.lciszewski27.quickchat.data.local.entity.ToolCallEntity
import dev.lciszewski27.quickchat.domain.model.ChatMessage
import dev.lciszewski27.quickchat.domain.model.ChatRole
import dev.lciszewski27.quickchat.domain.model.ChatSession
import dev.lciszewski27.quickchat.domain.model.FavoriteModel
import dev.lciszewski27.quickchat.domain.model.ProviderInstance
import dev.lciszewski27.quickchat.domain.model.SecretDecl
import dev.lciszewski27.quickchat.domain.model.Skill
import dev.lciszewski27.quickchat.domain.model.ToolCallRecord
import dev.lciszewski27.quickchat.domain.repository.ChatRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import java.util.UUID

class ChatRepositoryImpl(
    private val sessionDao: ChatSessionDao,
    private val messageDao: ChatMessageDao,
    private val favoriteDao: FavoriteModelDao,
    private val providerDao: ProviderInstanceDao,
    private val toolCallDao: ToolCallDao,
    private val skillDao: SkillDao,
    private val skillSecretDao: SkillSecretDao
) : ChatRepository {

    override fun observeSessions(): Flow<List<ChatSession>> =
        sessionDao.observeSessions().map { list -> list.map { it.toDomain() } }

    override fun observeMessages(sessionId: String): Flow<List<ChatMessage>> =
        messageDao.observeMessages(sessionId).map { list -> list.map { it.toDomain() } }

    override suspend fun createSession(providerId: String, modelId: String): ChatSession {
        val now = System.currentTimeMillis()
        val entity = ChatSessionEntity(
            id = UUID.randomUUID().toString(),
            title = "New chat",
            providerId = providerId,
            modelId = modelId,
            createdAt = now,
            updatedAt = now
        )
        sessionDao.upsert(entity)
        return entity.toDomain()
    }

    override suspend fun getSession(sessionId: String): ChatSession? =
        sessionDao.getById(sessionId)?.toDomain()

    override suspend fun appendMessage(
        sessionId: String,
        role: ChatRole,
        text: String,
        isError: Boolean,
        genTokens: Int,
        genMs: Long
    ): ChatMessage {
        val now = System.currentTimeMillis()
        val entity = ChatMessageEntity(
            id = UUID.randomUUID().toString(),
            sessionId = sessionId,
            role = role.name,
            text = text,
            timestamp = now,
            isError = isError,
            genTokens = genTokens,
            genMs = genMs
        )
        messageDao.insert(entity)
        sessionDao.touch(sessionId, now)
        // Auto-title from first user message.
        val session = sessionDao.getById(sessionId)
        if (session != null && session.title == "New chat" && role == ChatRole.USER) {
            val title = text.trim().take(48).replace("\n", " ").ifBlank { "New chat" }
            sessionDao.rename(sessionId, title, now)
        }
        return entity.toDomain()
    }

    override suspend fun updateSessionModel(sessionId: String, providerId: String, modelId: String) {
        sessionDao.updateModel(sessionId, providerId, modelId, System.currentTimeMillis())
    }

    override suspend fun renameSession(sessionId: String, title: String) {
        sessionDao.rename(sessionId, title.ifBlank { "Untitled" }, System.currentTimeMillis())
    }

    override suspend fun deleteSession(sessionId: String) {
        toolCallDao.deleteBySession(sessionId)
        messageDao.deleteBySession(sessionId)
        sessionDao.delete(sessionId)
    }

    override suspend fun clearSession(sessionId: String) {
        toolCallDao.deleteBySession(sessionId)
        messageDao.clearSession(sessionId)
        sessionDao.touch(sessionId, System.currentTimeMillis())
    }

    override fun observeFavorites(): Flow<List<FavoriteModel>> =
        favoriteDao.observeFavorites().map { list ->
            list.map { FavoriteModel(it.providerId, it.modelId, it.displayName, it.addedAt) }
        }

    override suspend fun isFavorite(providerId: String, modelId: String): Boolean =
        favoriteDao.count(providerId, modelId) > 0

    override suspend fun addFavorite(providerId: String, modelId: String, displayName: String) {
        favoriteDao.add(FavoriteModelEntity(providerId, modelId, displayName.ifBlank { modelId }, System.currentTimeMillis()))
    }

    override suspend fun removeFavorite(providerId: String, modelId: String) {
        favoriteDao.remove(providerId, modelId)
    }

    override fun observeProviderInstances(): Flow<List<ProviderInstance>> =
        providerDao.observe().map { list ->
            list.map {
                ProviderInstance(it.instanceId, it.type, it.label, it.baseUrl, it.apiKey, it.createdAt)
            }
        }

    override suspend fun upsertProviderInstance(instance: ProviderInstance) {
        providerDao.upsert(
            ProviderInstanceEntity(
                instance.instanceId, instance.type, instance.label,
                instance.baseUrl, instance.apiKey, instance.createdAt
            )
        )
    }

    override suspend fun removeProviderInstance(instanceId: String) {
        favoriteDao.deleteByProvider(instanceId)
        sessionDao.byProvider(instanceId).forEach {
            toolCallDao.deleteBySession(it.id)
            messageDao.deleteBySession(it.id)
        }
        sessionDao.deleteByProvider(instanceId)
        providerDao.delete(instanceId)
    }

    override fun observeToolCalls(sessionId: String): Flow<List<ToolCallRecord>> =
        toolCallDao.observeBySession(sessionId).map { list ->
            list.map {
                ToolCallRecord(
                    it.id, it.sessionId, it.name, it.displayName,
                    it.argsJson, it.result, it.durationMs, it.timestamp
                )
            }
        }

    override suspend fun appendToolCall(
        sessionId: String,
        name: String,
        displayName: String,
        argsJson: String,
        result: String,
        durationMs: Long
    ): ToolCallRecord {
        val entity = ToolCallEntity(
            id = UUID.randomUUID().toString(),
            sessionId = sessionId,
            name = name,
            displayName = displayName.ifBlank { name },
            argsJson = argsJson,
            result = result,
            durationMs = durationMs,
            timestamp = System.currentTimeMillis()
        )
        toolCallDao.insert(entity)
        sessionDao.touch(sessionId, entity.timestamp)
        return ToolCallRecord(
            entity.id, sessionId, entity.name, entity.displayName,
            entity.argsJson, entity.result, entity.durationMs, entity.timestamp
        )
    }

    private fun ChatSessionEntity.toDomain() = ChatSession(id, title, providerId, modelId, createdAt, updatedAt)
    private fun ChatMessageEntity.toDomain() = ChatMessage(
        id, sessionId,
        try { ChatRole.valueOf(role) } catch (e: Exception) { ChatRole.USER },
        text, timestamp, isError, genTokens, genMs
    )

    // ── Skills ───────────────────────────────────────────────────────
    private val skillJson = Json { ignoreUnknownKeys = true }

    override fun observeSkills(): Flow<List<Skill>> =
        skillDao.observe().map { list -> list.map { it.toDomain() } }

    override suspend fun getSkill(id: String): Skill? =
        skillDao.get(id)?.toDomain()

    override suspend fun upsertSkill(skill: Skill) {
        val now = System.currentTimeMillis()
        skillDao.upsert(
            SkillEntity(
                id = skill.id,
                name = skill.name,
                toolName = skill.toolName,
                description = skill.description,
                code = skill.code,
                paramsSchema = skill.paramsSchema,
                secretsSchema = skillJson.encodeToString(
                    kotlinx.serialization.builtins.ListSerializer(SecretDecl.serializer()),
                    skill.secrets
                ),
                enabled = skill.enabled,
                tested = skill.tested,
                createdAt = if (skill.createdAt == 0L) now else skill.createdAt,
                updatedAt = now
            )
        )
    }

    override suspend fun deleteSkill(id: String) {
        skillSecretDao.deleteBySkill(id)
        skillDao.delete(id)
    }

    override suspend fun setSkillEnabled(id: String, enabled: Boolean) {
        skillDao.setEnabled(id, enabled)
    }

    override suspend fun setSkillTested(id: String, tested: Boolean) {
        skillDao.setTested(id, tested)
    }

    override suspend fun getSecretValues(skillId: String): Map<String, String> =
        skillSecretDao.forSkill(skillId).associate { it.name to it.value }

    override suspend fun setSecretValue(skillId: String, name: String, value: String) {
        skillSecretDao.upsert(SkillSecretEntity(skillId, name, value))
    }

    override suspend fun deleteSecretValue(skillId: String, name: String) {
        skillSecretDao.delete(skillId, name)
    }

    private fun SkillEntity.toDomain(): Skill {
        val secrets = try {
            skillJson.decodeFromString(
                kotlinx.serialization.builtins.ListSerializer(SecretDecl.serializer()),
                secretsSchema.ifBlank { "[]" }
            )
        } catch (e: Exception) {
            emptyList()
        }
        return Skill(
            id, name, toolName, description, code, paramsSchema,
            secrets, enabled, tested, createdAt, updatedAt
        )
    }
}
