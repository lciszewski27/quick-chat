package dev.lciszewski27.quickchat.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // New table only — chats, messages and favorites are preserved as-is.
        // favorite_models.providerId now holds a provider *instance* id
        // (identical values for preset types, so existing stars survive).
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `provider_instances` (" +
                "`instanceId` TEXT NOT NULL, `type` TEXT NOT NULL, " +
                "`label` TEXT NOT NULL, `baseUrl` TEXT NOT NULL, " +
                "`apiKey` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, " +
                "PRIMARY KEY(`instanceId`))"
        )
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Tool-call timeline is new — chats, messages and favorites untouched.
        // NOTE: no index here — the entity declares none, and Room validates
        // migrated schemas exactly (an extra index crashes with
        // "Migration didn't properly handle").
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `tool_calls` (" +
                "`id` TEXT NOT NULL, `sessionId` TEXT NOT NULL, " +
                "`name` TEXT NOT NULL, `displayName` TEXT NOT NULL, " +
                "`argsJson` TEXT NOT NULL, `result` TEXT NOT NULL, " +
                "`durationMs` INTEGER NOT NULL, `timestamp` INTEGER NOT NULL, " +
                "PRIMARY KEY(`id`))"
        )
    }
}

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Skills + secret values are new tables; everything else untouched.
        // Secret values live in their own table so tool definitions
        // (built from `skills` only) can never leak them to the model.
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `skills` (" +
                "`id` TEXT NOT NULL, `name` TEXT NOT NULL, " +
                "`toolName` TEXT NOT NULL, `description` TEXT NOT NULL, " +
                "`code` TEXT NOT NULL, `paramsSchema` TEXT NOT NULL, " +
                "`secretsSchema` TEXT NOT NULL, `enabled` INTEGER NOT NULL, " +
                "`tested` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, " +
                "`updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `skill_secrets` (" +
                "`skillId` TEXT NOT NULL, `name` TEXT NOT NULL, " +
                "`value` TEXT NOT NULL, PRIMARY KEY(`skillId`, `name`))"
        )
    }
}

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Generation stats on messages. Columns carry DEFAULT 0 matching the
        // entity's @ColumnInfo(defaultValue) — Room validates migrated schemas
        // exactly, so the default must be declared on both sides.
        db.execSQL("ALTER TABLE `chat_messages` ADD COLUMN `genTokens` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `chat_messages` ADD COLUMN `genMs` INTEGER NOT NULL DEFAULT 0")
    }
}

@Database(
    entities = [
        ChatSessionEntity::class,
        ChatMessageEntity::class,
        FavoriteModelEntity::class,
        ProviderInstanceEntity::class,
        ToolCallEntity::class,
        SkillEntity::class,
        SkillSecretEntity::class
    ],
    version = 5,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun chatSessionDao(): ChatSessionDao
    abstract fun chatMessageDao(): ChatMessageDao
    abstract fun favoriteModelDao(): FavoriteModelDao
    abstract fun providerInstanceDao(): ProviderInstanceDao
    abstract fun toolCallDao(): ToolCallDao
    abstract fun skillDao(): SkillDao
    abstract fun skillSecretDao(): SkillSecretDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "quickchat.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                    .fallbackToDestructiveMigration(false).build().also { INSTANCE = it }
            }
        }
    }
}
