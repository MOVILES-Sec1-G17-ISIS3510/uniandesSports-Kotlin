package com.uniandes.sport.viewmodels.communities

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.uniandes.sport.data.local.CommunitiesCacheDatabase
import com.uniandes.sport.data.local.toEntity
import com.uniandes.sport.data.local.toModel
import com.uniandes.sport.models.Channel
import com.uniandes.sport.models.ChannelMessage
import com.uniandes.sport.models.Community
import com.uniandes.sport.models.CommunityMember
import com.uniandes.sport.models.Post
import com.uniandes.sport.models.PostComment
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class FirestoreCommunitiesViewModel(application: Application) : AndroidViewModel(application), CommunitiesViewModelInterface {

    private val db = FirebaseFirestore.getInstance()
    private val cacheDao = CommunitiesCacheDatabase.getInstance(application).cacheDao()

    private val _communities = MutableStateFlow<List<Community>>(emptyList())
    override val communities: StateFlow<List<Community>> = _communities.asStateFlow()

    private val _posts = MutableStateFlow<List<Post>>(emptyList())
    override val posts: StateFlow<List<Post>> = _posts.asStateFlow()

    private val _channels = MutableStateFlow<List<Channel>>(emptyList())
    override val channels: StateFlow<List<Channel>> = _channels.asStateFlow()

    private val _channelMessages = MutableStateFlow<List<ChannelMessage>>(emptyList())
    override val channelMessages: StateFlow<List<ChannelMessage>> = _channelMessages.asStateFlow()

    private val _hasMoreOldChannelMessages = MutableStateFlow(false)
    override val hasMoreOldChannelMessages: StateFlow<Boolean> = _hasMoreOldChannelMessages.asStateFlow()

    private val _isLoadingOlderChannelMessages = MutableStateFlow(false)
    override val isLoadingOlderChannelMessages: StateFlow<Boolean> = _isLoadingOlderChannelMessages.asStateFlow()

    private val _members = MutableStateFlow<List<CommunityMember>>(emptyList())
    override val members: StateFlow<List<CommunityMember>> = _members.asStateFlow()

    private val _postComments = MutableStateFlow<List<PostComment>>(emptyList())
    override val postComments: StateFlow<List<PostComment>> = _postComments.asStateFlow()

    private val _myCommunityIds = MutableStateFlow<Set<String>>(emptySet())
    override val myCommunityIds: StateFlow<Set<String>> = _myCommunityIds.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    override val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private var activeCommunityId: String? = null
    private var activeChannelId: String? = null
    private var oldestLoadedMessageSnapshot: DocumentSnapshot? = null

    init {
        viewModelScope.launch {
            val cached = cacheDao.getCachedCommunities().map { it.toModel() }
            if (cached.isNotEmpty()) {
                _communities.value = cached
            }
        }
    }

    override fun loadUserMemberships(userId: String) {
        viewModelScope.launch {
            try {
                val snapshot = db.collectionGroup("members")
                    .whereEqualTo("userId", userId)
                    .get()
                    .await()
                
                val ids = snapshot.documents.mapNotNull { doc ->
                    doc.reference.parent.parent?.id
                }.toSet()
                
                _myCommunityIds.value = ids
            } catch (e: Exception) {
                Log.e("FirestoreCommunities", "Error fetching user memberships", e)
            }
        }
    }

    override fun loadCommunities() {
        _isLoading.value = true
        viewModelScope.launch {
            try {
                if (_communities.value.isEmpty()) {
                    val cached = cacheDao.getCachedCommunities().map { it.toModel() }
                    if (cached.isNotEmpty()) _communities.value = cached
                }
                val snapshot = db.collection("communities").get().await()
                val loadedCommunities = snapshot.documents.mapNotNull { doc ->
                    val c = doc.toObject(Community::class.java)
                    c?.copy(id = doc.id)
                }
                _communities.value = loadedCommunities
                cacheCommunities(loadedCommunities)
            } catch (e: Exception) {
                Log.e("FirestoreCommunities", "Error fetching communities", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    override fun loadCommunityDetails(communityId: String) {
        _isLoading.value = true
        viewModelScope.launch {
            try {
                // Fetch Posts (Feed)
                val postsSnapshot = db.collection("communities").document(communityId)
                    .collection("posts").get().await()
                    
                val loadedPosts = postsSnapshot.documents.mapNotNull { doc ->
                    val p = doc.toObject(Post::class.java)
                    p?.copy(id = doc.id)
                }.sortedByDescending { it.createdAt }
                _posts.value = loadedPosts

                // Fetch Channels
                val channelsSnapshot = db.collection("communities").document(communityId)
                    .collection("channels").get().await()
                    
                val loadedChannels = channelsSnapshot.documents.mapNotNull { doc ->
                    val ch = doc.toObject(Channel::class.java)
                    ch?.copy(id = doc.id)
                }
                _channels.value = loadedChannels

                // Fetch Members
                val membersSnapshot = db.collection("communities").document(communityId)
                    .collection("members").get().await()

                val loadedMembers = membersSnapshot.documents.mapNotNull { doc ->
                    val member = doc.toObject(CommunityMember::class.java)
                    member?.copy(
                        id = doc.id,
                        userId = if (member.userId.isBlank()) doc.id else member.userId,
                        displayName = if (member.displayName.isBlank()) "Miembro" else member.displayName
                    )
                }.sortedBy { it.displayName.lowercase() }

                _members.value = loadedMembers

            } catch (e: Exception) {
                Log.e("FirestoreCommunities", "Error fetching community details", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    override fun joinCommunity(
        communityId: String,
        userId: String,
        displayName: String,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val communityRef = db.collection("communities").document(communityId)
                val memberRef = communityRef.collection("members").document(userId)

                db.runTransaction { transaction ->
                    val communitySnapshot = transaction.get(communityRef)
                    val existingMember = transaction.get(memberRef)
                    if (!existingMember.exists()) {
                        val payload = hashMapOf(
                            "userId" to userId,
                            "displayName" to displayName,
                            "role" to "member",
                            "joinedAt" to System.currentTimeMillis()
                        )
                        transaction.set(memberRef, payload)

                        val currentCount = communitySnapshot.getLong("memberCount") ?: 0L
                        transaction.update(communityRef, "memberCount", currentCount + 1L)
                    }
                    null
                }.await()

                loadCommunities()
                loadCommunityDetails(communityId)
                onSuccess()
            } catch (e: Exception) {
                Log.e("FirestoreCommunities", "Error joining community", e)
                onFailure(e)
            }
        }
    }

    override fun createCommunity(
        name: String,
        type: String,
        sport: String,
        description: String,
        ownerId: String,
        ownerDisplayName: String,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val communityRef = db.collection("communities").document()
                val memberRef = communityRef.collection("members").document(ownerId)
                val channelRef = communityRef.collection("channels").document()

                val batch = db.batch()

                val communityPayload = hashMapOf(
                    "name" to name.trim(),
                    "type" to type,
                    "sport" to sport.trim(),
                    "description" to description.trim(),
                    "memberCount" to 1,
                    "channelCount" to 1,
                    "ownerId" to ownerId
                )

                val ownerPayload = hashMapOf(
                    "userId" to ownerId,
                    "displayName" to ownerDisplayName,
                    "role" to "admin",
                    "joinedAt" to System.currentTimeMillis()
                )

                val defaultChannelPayload = hashMapOf(
                    "name" to "general",
                    "type" to "publico",
                    "mensajes" to 0
                )

                batch.set(communityRef, communityPayload)
                batch.set(memberRef, ownerPayload)
                batch.set(channelRef, defaultChannelPayload)
                batch.commit().await()

                loadCommunities()
                onSuccess()
            } catch (e: Exception) {
                Log.e("FirestoreCommunities", "Error creating community", e)
                onFailure(e)
            }
        }
    }

    override fun createAnnouncement(
        communityId: String,
        author: String,
        role: String,
        content: String,
        pinned: Boolean,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val payload = hashMapOf(
                    "author" to author,
                    "role" to role,
                    "content" to content.trim(),
                    "time" to "Just now",
                    "pinned" to pinned,
                    "likes" to 0,
                    "createdAt" to System.currentTimeMillis()
                )

                db.collection("communities")
                    .document(communityId)
                    .collection("posts")
                    .add(payload)
                    .await()

                loadCommunityDetails(communityId)
                onSuccess()
            } catch (e: Exception) {
                Log.e("FirestoreCommunities", "Error creating announcement", e)
                onFailure(e)
            }
        }
    }

    override fun createChannel(
        communityId: String,
        name: String,
        type: String,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val communityRef = db.collection("communities").document(communityId)
                val channelRef = communityRef.collection("channels").document()

                db.runTransaction { transaction ->
                    val communitySnapshot = transaction.get(communityRef)

                    transaction.set(channelRef, hashMapOf(
                        "name" to name.trim(),
                        "type" to type,
                        "mensajes" to 0
                    ))

                    val currentCount = communitySnapshot.getLong("channelCount") ?: 0L
                    transaction.update(communityRef, "channelCount", currentCount + 1L)
                    null
                }.await()

                loadCommunities()
                loadCommunityDetails(communityId)
                onSuccess()
            } catch (e: Exception) {
                Log.e("FirestoreCommunities", "Error creating channel", e)
                onFailure(e)
            }
        }
    }

    override fun removeMember(
        communityId: String,
        memberId: String,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val communityRef = db.collection("communities").document(communityId)
                val memberRef = communityRef.collection("members").document(memberId)

                db.runTransaction { transaction ->
                    val communitySnapshot = transaction.get(communityRef)
                    val memberSnapshot = transaction.get(memberRef)
                    if (memberSnapshot.exists()) {
                        transaction.delete(memberRef)
                        val currentCount = communitySnapshot.getLong("memberCount") ?: 0L
                        val newCount = if (currentCount > 0) currentCount - 1L else 0L
                        transaction.update(communityRef, "memberCount", newCount)
                    }
                    null
                }.await()

                loadCommunities()
                loadCommunityDetails(communityId)
                onSuccess()
            } catch (e: Exception) {
                Log.e("FirestoreCommunities", "Error removing member", e)
                onFailure(e)
            }
        }
    }

    override fun loadChannelMessages(communityId: String, channelId: String) {
        viewModelScope.launch {
            try {
                activeCommunityId = communityId
                activeChannelId = channelId

                val cached = loadCachedRecentMessages(communityId, channelId)
                if (cached.isNotEmpty()) {
                    _channelMessages.value = cached
                }

                val snapshot = db.collection("communities").document(communityId)
                    .collection("channels").document(channelId)
                    .collection("messages")
                    .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
                    .limit(20)
                    .get()
                    .await()

                oldestLoadedMessageSnapshot = snapshot.documents.lastOrNull()
                _hasMoreOldChannelMessages.value = snapshot.size() >= 20

                _channelMessages.value = snapshot.documents.mapNotNull { doc ->
                    val m = doc.toObject(ChannelMessage::class.java)
                    m?.copy(id = doc.id)
                }.reversed()
                cacheRecentMessages(communityId, channelId, _channelMessages.value)
            } catch (e: Exception) {
                Log.e("FirestoreCommunities", "Error loading channel messages", e)
                if (_channelMessages.value.isEmpty()) {
                    _channelMessages.value = loadCachedRecentMessages(communityId, channelId)
                }
                _hasMoreOldChannelMessages.value = false
                oldestLoadedMessageSnapshot = null
            }
        }
    }

    override fun loadOlderChannelMessages() {
        val communityId = activeCommunityId
        val channelId = activeChannelId
        val cursor = oldestLoadedMessageSnapshot

        if (communityId.isNullOrBlank() || channelId.isNullOrBlank() || cursor == null) return
        if (_isLoadingOlderChannelMessages.value || !_hasMoreOldChannelMessages.value) return

        _isLoadingOlderChannelMessages.value = true
        viewModelScope.launch {
            try {
                val snapshot = db.collection("communities").document(communityId)
                    .collection("channels").document(channelId)
                    .collection("messages")
                    .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
                    .startAfter(cursor)
                    .limit(20)
                    .get()
                    .await()

                val olderMessagesAsc = snapshot.documents.mapNotNull { doc ->
                    val m = doc.toObject(ChannelMessage::class.java)
                    m?.copy(id = doc.id)
                }.reversed()

                if (olderMessagesAsc.isNotEmpty()) {
                    val merged = olderMessagesAsc + _channelMessages.value.filter { !olderMessagesAsc.any { old -> old.id == it.id } }
                    _channelMessages.value = merged
                    cacheRecentMessages(communityId, channelId, _channelMessages.value)
                    oldestLoadedMessageSnapshot = snapshot.documents.lastOrNull() ?: oldestLoadedMessageSnapshot
                    _hasMoreOldChannelMessages.value = snapshot.size() >= 20
                } else {
                    _hasMoreOldChannelMessages.value = false
                }
            } catch (e: Exception) {
                Log.e("FirestoreCommunities", "Error loading older channel messages", e)
            } finally {
                _isLoadingOlderChannelMessages.value = false
            }
        }
    }

    override fun sendChannelMessage(
        communityId: String,
        channelId: String,
        authorId: String,
        authorName: String,
        content: String,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val channelRef = db.collection("communities").document(communityId)
                    .collection("channels").document(channelId)

                val messagePayload = hashMapOf(
                    "authorId" to authorId,
                    "authorName" to authorName,
                    "content" to content.trim(),
                    "createdAt" to System.currentTimeMillis(),
                    "reactions" to emptyMap<String, Long>()
                )

                db.runTransaction { transaction ->
                    val channelSnapshot = transaction.get(channelRef)
                    val messageRef = channelRef.collection("messages").document()
                    transaction.set(messageRef, messagePayload)

                    val currentCount = channelSnapshot.getLong("mensajes") ?: 0L
                    transaction.update(channelRef, "mensajes", currentCount + 1L)
                    null
                }.await()

                loadCommunityDetails(communityId)
                loadChannelMessages(communityId, channelId)
                onSuccess()
            } catch (e: Exception) {
                Log.e("FirestoreCommunities", "Error sending channel message", e)
                onFailure(e)
            }
        }
    }

    override fun reactToChannelMessage(
        communityId: String,
        channelId: String,
        messageId: String,
        userId: String,
        emoji: String,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val messageRef = db.collection("communities").document(communityId)
                    .collection("channels").document(channelId)
                    .collection("messages").document(messageId)

                db.runTransaction { transaction ->
                    val messageSnapshot = transaction.get(messageRef)
                    val existingCounts = messageSnapshot.get("reactions") as? Map<*, *> ?: emptyMap<String, Long>()
                    val reactionCounts = existingCounts.mapNotNull { entry ->
                        val key = entry.key as? String ?: return@mapNotNull null
                        val value = when (val raw = entry.value) {
                            is Long -> raw
                            is Int -> raw.toLong()
                            else -> 0L
                        }
                        key to value
                    }.toMap().toMutableMap()

                    val existingUserReactions = messageSnapshot.get("userReactions") as? Map<*, *> ?: emptyMap<String, String>()
                    val userReactions = existingUserReactions.mapNotNull { entry ->
                        val key = entry.key as? String ?: return@mapNotNull null
                        val value = entry.value as? String ?: return@mapNotNull null
                        key to value
                    }.toMap().toMutableMap()

                    val previousEmoji = userReactions[userId]

                    // Toggle off if the same emoji was selected, else replace with the new one.
                    if (previousEmoji == emoji) {
                        userReactions.remove(userId)
                        val prevCount = (reactionCounts[emoji] ?: 0L) - 1L
                        if (prevCount > 0) reactionCounts[emoji] = prevCount else reactionCounts.remove(emoji)
                    } else {
                        if (!previousEmoji.isNullOrBlank()) {
                            val prevCount = (reactionCounts[previousEmoji] ?: 0L) - 1L
                            if (prevCount > 0) reactionCounts[previousEmoji] = prevCount else reactionCounts.remove(previousEmoji)
                        }
                        userReactions[userId] = emoji
                        reactionCounts[emoji] = (reactionCounts[emoji] ?: 0L) + 1L
                    }

                    transaction.update(messageRef, mapOf(
                        "reactions" to reactionCounts,
                        "userReactions" to userReactions
                    ))
                    null
                }.await()

                loadChannelMessages(communityId, channelId)
                onSuccess()
            } catch (e: Exception) {
                Log.e("FirestoreCommunities", "Error reacting to message", e)
                onFailure(e)
            }
        }
    }

    override fun loadPostComments(communityId: String, postId: String) {
        viewModelScope.launch {
            try {
                val snapshot = db.collection("communities").document(communityId)
                    .collection("posts").document(postId)
                    .collection("comments").get().await()

                _postComments.value = snapshot.documents.mapNotNull { doc ->
                    val comment = doc.toObject(PostComment::class.java)
                    comment?.copy(id = doc.id)
                }.sortedBy { it.createdAt }
            } catch (e: Exception) {
                Log.e("FirestoreCommunities", "Error loading post comments", e)
                _postComments.value = emptyList()
            }
        }
    }

    override fun addPostComment(
        communityId: String,
        postId: String,
        authorId: String,
        authorName: String,
        content: String,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val postRef = db.collection("communities").document(communityId)
                    .collection("posts").document(postId)

                db.runTransaction { transaction ->
                    val postSnapshot = transaction.get(postRef)
                    val commentRef = postRef.collection("comments").document()

                    transaction.set(commentRef, hashMapOf(
                        "authorId" to authorId,
                        "authorName" to authorName,
                        "content" to content.trim(),
                        "createdAt" to System.currentTimeMillis()
                    ))

                    val currentCount = postSnapshot.getLong("comments") ?: 0L
                    transaction.update(postRef, "comments", currentCount + 1L)
                    null
                }.await()

                loadPostComments(communityId, postId)
                onSuccess()
            } catch (e: Exception) {
                Log.e("FirestoreCommunities", "Error adding post comment", e)
                onFailure(e)
            }
        }
    }

    private suspend fun cacheCommunities(list: List<Community>) {
        cacheDao.clearCommunities()
        cacheDao.upsertCommunities(list.map { it.toEntity() })
    }

    private suspend fun cacheRecentMessages(communityId: String, channelId: String, messages: List<ChannelMessage>) {
        val recent = messages.takeLast(20)
        cacheDao.clearChannelMessages(communityId, channelId)
        cacheDao.upsertChannelMessages(recent.map { it.toEntity(communityId, channelId) })
    }

    private suspend fun loadCachedRecentMessages(communityId: String, channelId: String): List<ChannelMessage> {
        return try {
            cacheDao.getRecentChannelMessages(communityId, channelId, 20)
                .map { it.toModel() }
                .sortedBy { it.createdAt }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
