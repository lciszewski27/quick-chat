package dev.lciszewski27.quickchat.domain.repository

import dev.lciszewski27.quickchat.domain.model.ChatMessage
import dev.lciszewski27.quickchat.domain.model.ChatSession
import dev.lciszewski27.quickchat.domain.model.FavoriteModel
import dev.lciszewski27.quickchat.domain.model.ProviderInstance
import dev.lciszewski27.quickchat.domain.model.ToolCallRecord
import kotlinx.coroutines.flow.Flow

interface ChatRepository {
    fun observeSessions(): Flow<List<ChatSession>>
    fun observeMessages(sessionId: String): Flow<List<ChatMessage>>
    suspend fun createSession(providerId: String, modelId: String): ChatSession
    suspend fun getSession(sessionId: String): ChatSession?
    suspend fun appendMessage(sessionId: String, role: dev.lciszewski27.quickchat.domain.model.ChatRole, text: String, isError: Boolean = false): ChatMessage
    suspend fun updateSessionModel(sessionId: String, providerId: String, modelId: String)
    suspend fun renameSession(sessionId: String, title: String)
    suspend fun deleteSession(sessionId: String)
    suspend fun clearSession(sessionId: String)

    fun observeFavorites(): Flow<List<FavoriteModel>>
    suspend fun isFavorite(providerId: String, modelId: String): Boolean
    suspend fun addFavorite(providerId: String, modelId: String, displayName: String)
    suspend fun removeFavorite(providerId: String, modelId: String)

    fun observeProviderInstances(): Flow<List<ProviderInstance>>
    suspend fun upsertProviderInstance(instance: ProviderInstance)
    suspend fun removeProviderInstance(instanceId: String)

    fun observeToolCalls(sessionId: String): Flow<List<ToolCallRecord>>
    suspend fun appendToolCall(
        sessionId: String,
        name: String,
        displayName: String,
        argsJson: String,
        result: String,
        durationMs: Long
    ): ToolCallRecord
}
