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
import dev.lciszewski27.quickchat.data.local.dao.ToolCallDao
import dev.lciszewski27.quickchat.data.local.entity.ChatMessageEntity
import dev.lciszewski27.quickchat.data.local.entity.ChatSessionEntity
import dev.lciszewski27.quickchat.data.local.entity.FavoriteModelEntity
import dev.lciszewski27.quickchat.data.local.entity.ProviderInstanceEntity
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

@Database(
    entities = [
        ChatSessionEntity::class,
        ChatMessageEntity::class,
        FavoriteModelEntity::class,
        ProviderInstanceEntity::class,
        ToolCallEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun chatSessionDao(): ChatSessionDao
    abstract fun chatMessageDao(): ChatMessageDao
    abstract fun favoriteModelDao(): FavoriteModelDao
    abstract fun providerInstanceDao(): ProviderInstanceDao
    abstract fun toolCallDao(): ToolCallDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "quickchat.db"
                )                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .fallbackToDestructiveMigration(false).build().also { INSTANCE = it }
            }
        }
    }
}
