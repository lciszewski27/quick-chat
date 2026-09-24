package dev.lciszewski27.quickchat.data.repository

import dev.lciszewski27.quickchat.data.local.dao.ChatMessageDao
import dev.lciszewski27.quickchat.data.local.dao.ChatSessionDao
import dev.lciszewski27.quickchat.data.local.dao.FavoriteModelDao
import dev.lciszewski27.quickchat.data.local.dao.ProviderInstanceDao
import dev.lciszewski27.quickchat.data.local.dao.ToolCallDao
import dev.lciszewski27.quickchat.data.local.entity.ChatMessageEntity
import dev.lciszewski27.quickchat.data.local.entity.ChatSessionEntity
import dev.lciszewski27.quickchat.data.local.entity.FavoriteModelEntity
import dev.lciszewski27.quickchat.data.local.entity.ProviderInstanceEntity
import dev.lciszewski27.quickchat.data.local.entity.ToolCallEntity
import dev.lciszewski27.quickchat.domain.model.ChatMessage
import dev.lciszewski27.quickchat.domain.model.ChatRole
import dev.lciszewski27.quickchat.domain.model.ChatSession
import dev.lciszewski27.quickchat.domain.model.FavoriteModel
import dev.lciszewski27.quickchat.domain.model.ProviderInstance
import dev.lciszewski27.quickchat.domain.model.ToolCallRecord
import dev.lciszewski27.quickchat.domain.repository.ChatRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

class ChatRepositoryImpl(
    private val sessionDao: ChatSessionDao,
    private val messageDao: ChatMessageDao,
    private val favoriteDao: FavoriteModelDao,
    private val providerDao: ProviderInstanceDao,
    private val toolCallDao: ToolCallDao
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
        isError: Boolean
    ): ChatMessage {
        val now = System.currentTimeMillis()
        val entity = ChatMessageEntity(
            id = UUID.randomUUID().toString(),
            sessionId = sessionId,
            role = role.name,
            text = text,
            timestamp = now,
            isError = isError
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
        text, timestamp, isError
    )
}
