package com.uniandes.sport.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface CommunitiesCacheDao {
    @Query("SELECT * FROM cached_communities ORDER BY name ASC")
    suspend fun getCachedCommunities(): List<CachedCommunityEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCommunities(items: List<CachedCommunityEntity>)

    @Query("DELETE FROM cached_communities")
    suspend fun clearCommunities()

    @Query("SELECT * FROM cached_channel_messages WHERE communityId = :communityId AND channelId = :channelId ORDER BY createdAt DESC LIMIT :limit")
    suspend fun getRecentChannelMessages(
        communityId: String,
        channelId: String,
        limit: Int = 20
    ): List<CachedChannelMessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertChannelMessages(items: List<CachedChannelMessageEntity>)

    @Query("DELETE FROM cached_channel_messages WHERE communityId = :communityId AND channelId = :channelId")
    suspend fun clearChannelMessages(communityId: String, channelId: String)
}
