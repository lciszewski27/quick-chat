package dev.lciszewski27.quickchat.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import dev.lciszewski27.quickchat.data.local.entity.ChatMessageEntity
import dev.lciszewski27.quickchat.data.local.entity.ChatSessionEntity
import dev.lciszewski27.quickchat.data.local.entity.FavoriteModelEntity
import dev.lciszewski27.quickchat.data.local.entity.ProviderInstanceEntity
import dev.lciszewski27.quickchat.data.local.entity.SkillEntity
import dev.lciszewski27.quickchat.data.local.entity.SkillSecretEntity
import dev.lciszewski27.quickchat.data.local.entity.ToolCallEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatSessionDao {
    @Query("SELECT * FROM chat_sessions ORDER BY updatedAt DESC")
    fun observeSessions(): Flow<List<ChatSessionEntity>>

    @Query("SELECT * FROM chat_sessions WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): ChatSessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(session: ChatSessionEntity)

    @Query("UPDATE chat_sessions SET title = :title, updatedAt = :updatedAt WHERE id = :id")
    suspend fun rename(id: String, title: String, updatedAt: Long)

    @Query("UPDATE chat_sessions SET providerId = :providerId, modelId = :modelId, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateModel(id: String, providerId: String, modelId: String, updatedAt: Long)

    @Query("UPDATE chat_sessions SET updatedAt = :updatedAt WHERE id = :id")
    suspend fun touch(id: String, updatedAt: Long)

    @Query("DELETE FROM chat_sessions WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT * FROM chat_sessions WHERE providerId = :providerId")
    suspend fun byProvider(providerId: String): List<ChatSessionEntity>

    @Query("DELETE FROM chat_sessions WHERE providerId = :providerId")
    suspend fun deleteByProvider(providerId: String)
}

@Dao
interface ChatMessageDao {
    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    fun observeMessages(sessionId: String): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    suspend fun getMessages(sessionId: String): List<ChatMessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: ChatMessageEntity)

    @Query("DELETE FROM chat_messages WHERE sessionId = :sessionId")
    suspend fun clearSession(sessionId: String)

    @Query("DELETE FROM chat_messages WHERE sessionId = :sessionId")
    suspend fun deleteBySession(sessionId: String)
}

@Dao
interface FavoriteModelDao {
    @Query("SELECT * FROM favorite_models ORDER BY addedAt DESC")
    fun observeFavorites(): Flow<List<FavoriteModelEntity>>

    @Query("SELECT COUNT(*) FROM favorite_models WHERE providerId = :providerId AND modelId = :modelId")
    suspend fun count(providerId: String, modelId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun add(fav: FavoriteModelEntity)

    @Query("DELETE FROM favorite_models WHERE providerId = :providerId AND modelId = :modelId")
    suspend fun remove(providerId: String, modelId: String)

    @Query("DELETE FROM favorite_models WHERE providerId = :providerId")
    suspend fun deleteByProvider(providerId: String)
}

@Dao
interface ProviderInstanceDao {
    @Query("SELECT * FROM provider_instances ORDER BY createdAt ASC")
    fun observe(): Flow<List<ProviderInstanceEntity>>

    @Query("SELECT * FROM provider_instances WHERE instanceId = :id LIMIT 1")
    suspend fun get(id: String): ProviderInstanceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(instance: ProviderInstanceEntity)

    @Query("DELETE FROM provider_instances WHERE instanceId = :id")
    suspend fun delete(id: String)
}

@Dao
interface ToolCallDao {
    @Query("SELECT * FROM tool_calls WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    fun observeBySession(sessionId: String): Flow<List<ToolCallEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(call: ToolCallEntity)

    @Query("DELETE FROM tool_calls WHERE sessionId = :sessionId")
    suspend fun deleteBySession(sessionId: String)
}

@Dao
interface SkillDao {
    @Query("SELECT * FROM skills ORDER BY createdAt ASC")
    fun observe(): Flow<List<SkillEntity>>

    @Query("SELECT * FROM skills WHERE id = :id LIMIT 1")
    suspend fun get(id: String): SkillEntity?

    @Query("SELECT * FROM skills WHERE toolName = :toolName LIMIT 1")
    suspend fun getByToolName(toolName: String): SkillEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(skill: SkillEntity)

    @Query("UPDATE skills SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: String, enabled: Boolean)

    @Query("UPDATE skills SET tested = :tested WHERE id = :id")
    suspend fun setTested(id: String, tested: Boolean)

    @Query("DELETE FROM skills WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface SkillSecretDao {
    @Query("SELECT * FROM skill_secrets WHERE skillId = :skillId")
    suspend fun forSkill(skillId: String): List<SkillSecretEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(secret: SkillSecretEntity)

    @Query("DELETE FROM skill_secrets WHERE skillId = :skillId AND name = :name")
    suspend fun delete(skillId: String, name: String)

    @Query("DELETE FROM skill_secrets WHERE skillId = :skillId")
    suspend fun deleteBySkill(skillId: String)
}
