package dev.lciszewski27.quickchat

import android.app.Application
import dev.lciszewski27.quickchat.data.ai.LlmSdk
import dev.lciszewski27.quickchat.data.ai.ProviderResolver
import dev.lciszewski27.quickchat.data.ai.skills.CreateSkillTool
import dev.lciszewski27.quickchat.data.ai.skills.RepoSkillToolSource
import dev.lciszewski27.quickchat.data.ai.skills.SkillExecutor
import dev.lciszewski27.quickchat.data.ai.tools.AiTools
import dev.lciszewski27.quickchat.data.local.AppDatabase
import dev.lciszewski27.quickchat.data.local.preferences.UserPreferencesDataStore
import dev.lciszewski27.quickchat.data.repository.ChatRepositoryImpl

/**
 * Manual DI container (same pattern as the reference app).
 * Providers are user-added instances resolved on demand — see [ProviderResolver].
 * Tools (built-in + AI-created skills) execute locally and are shared by
 * every provider through one registry.
 */
class QuickChatApp : Application() {
    lateinit var database: AppDatabase
        private set
    lateinit var preferences: UserPreferencesDataStore
        private set
    lateinit var repository: ChatRepositoryImpl
        private set
    lateinit var llmSdk: LlmSdk
        private set
    lateinit var skillExecutor: SkillExecutor
        private set
    lateinit var providerResolver: ProviderResolver
        private set

    override fun onCreate() {
        super.onCreate()
        database = AppDatabase.getInstance(this)
        preferences = UserPreferencesDataStore(this)
        repository = ChatRepositoryImpl(
            sessionDao = database.chatSessionDao(),
            messageDao = database.chatMessageDao(),
            favoriteDao = database.favoriteModelDao(),
            providerDao = database.providerInstanceDao(),
            toolCallDao = database.toolCallDao(),
            skillDao = database.skillDao(),
            skillSecretDao = database.skillSecretDao()
        )
        llmSdk = LlmSdk()
        skillExecutor = SkillExecutor(repository)
        val staticTools = AiTools.defaults() + CreateSkillTool(
            repository = repository,
            executor = skillExecutor,
            builtInNames = (AiTools.defaults().map { it.name } + "create_skill").toSet()
        )
        providerResolver = ProviderResolver(
            sdk = llmSdk,
            tools = staticTools,
            skillTools = RepoSkillToolSource(repository, skillExecutor)
        )
    }
}
