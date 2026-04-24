package com.uniandes.sport.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        CachedCommunityEntity::class,
        CachedPostEntity::class,
        CachedChannelEntity::class,
        CachedMemberEntity::class,
        CachedPostCommentEntity::class,
        CachedMembershipEntity::class,
        CachedChannelMessageEntity::class,
        PendingMessageEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class CommunitiesCacheDatabase : RoomDatabase() {
    abstract fun cacheDao(): CommunitiesCacheDao

    companion object {
        @Volatile
        private var INSTANCE: CommunitiesCacheDatabase? = null

        fun getInstance(context: Context): CommunitiesCacheDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    CommunitiesCacheDatabase::class.java,
                    "communities_cache.db"
                ).fallbackToDestructiveMigration(dropAllTables = true).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
