package com.uniandes.sport.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface CommunitiesCacheDao {

    // ─── Communities ──────────────────────────────────────────────────────────

    @Query("SELECT * FROM cached_communities ORDER BY name ASC")
    suspend fun getCachedCommunities(): List<CachedCommunityEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCommunities(items: List<CachedCommunityEntity>)

    @Query("DELETE FROM cached_communities")
    suspend fun clearCommunities()

    // ─── Posts ────────────────────────────────────────────────────────────────

    @Query("SELECT * FROM cached_posts WHERE communityId = :communityId ORDER BY createdAt DESC")
    suspend fun getPostsByCommunity(communityId: String): List<CachedPostEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPosts(items: List<CachedPostEntity>)

    @Query("DELETE FROM cached_posts WHERE communityId = :communityId")
    suspend fun clearPostsByCommunity(communityId: String)

    @Query("UPDATE cached_posts SET status = :status WHERE communityId = :communityId AND postId = :postId")
    suspend fun updatePostStatus(communityId: String, postId: String, status: String)

    // ─── Channels ─────────────────────────────────────────────────────────────

    @Query("SELECT * FROM cached_channels WHERE communityId = :communityId ORDER BY name ASC")
    suspend fun getChannelsByCommunity(communityId: String): List<CachedChannelEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertChannels(items: List<CachedChannelEntity>)

    @Query("DELETE FROM cached_channels WHERE communityId = :communityId")
    suspend fun clearChannelsByCommunity(communityId: String)

    // ─── Members ──────────────────────────────────────────────────────────────

    @Query("SELECT * FROM cached_members WHERE communityId = :communityId ORDER BY displayName ASC")
    suspend fun getMembersByCommunity(communityId: String): List<CachedMemberEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMembers(items: List<CachedMemberEntity>)

    @Query("DELETE FROM cached_members WHERE communityId = :communityId")
    suspend fun clearMembersByCommunity(communityId: String)

    // ─── PostComments ─────────────────────────────────────────────────────────

    @Query("SELECT * FROM cached_post_comments WHERE communityId = :communityId AND postId = :postId ORDER BY createdAt ASC")
    suspend fun getCommentsByPost(communityId: String, postId: String): List<CachedPostCommentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertComments(items: List<CachedPostCommentEntity>)

    @Query("DELETE FROM cached_post_comments WHERE communityId = :communityId AND postId = :postId")
    suspend fun clearCommentsByPost(communityId: String, postId: String)

    // ─── User Memberships ─────────────────────────────────────────────────────

    @Query("SELECT communityId FROM cached_memberships WHERE userId = :userId")
    suspend fun getMembershipIds(userId: String): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMemberships(items: List<CachedMembershipEntity>)

    @Query("DELETE FROM cached_memberships WHERE userId = :userId")
    suspend fun clearMemberships(userId: String)

    // ─── Channel Messages ─────────────────────────────────────────────────────

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

    @Query("UPDATE cached_channel_messages SET status = :status WHERE communityId = :communityId AND channelId = :channelId AND messageId = :messageId")
    suspend fun updateMessageStatus(communityId: String, channelId: String, messageId: String, status: String)

    @Query("UPDATE cached_channel_messages SET messageId = :newId WHERE communityId = :communityId AND channelId = :channelId AND messageId = :oldId")
    suspend fun updateMessageId(communityId: String, channelId: String, oldId: String, newId: String)

    // ─── Pending Messages ──────────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPendingMessage(message: PendingMessageEntity)

    @Query("DELETE FROM pending_messages WHERE localId = :localId")
    suspend fun deletePendingMessage(localId: String)

    @Query("SELECT * FROM pending_messages")
    suspend fun getPendingMessages(): List<PendingMessageEntity>

    // ─── Pending Posts ──────────────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPendingPost(post: PendingPostEntity)

    @Query("DELETE FROM pending_posts WHERE localId = :localId")
    suspend fun deletePendingPost(localId: String)

    @Query("SELECT * FROM pending_posts")
    suspend fun getPendingPosts(): List<PendingPostEntity>

    @Query("DELETE FROM pending_posts")
    suspend fun clearPendingPosts()
}
