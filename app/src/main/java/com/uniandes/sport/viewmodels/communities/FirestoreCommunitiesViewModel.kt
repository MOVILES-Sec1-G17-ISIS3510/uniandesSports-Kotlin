package com.uniandes.sport.viewmodels.communities

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.ktx.Firebase
import com.google.firebase.messaging.ktx.messaging
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.uniandes.sport.workers.MessageSyncWorker
import com.uniandes.sport.data.local.CachedMembershipEntity
import com.uniandes.sport.models.MessageStatus
import com.uniandes.sport.data.local.PendingMessageEntity
import java.util.UUID
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
    private val topicPrefs = application.getSharedPreferences("fcm_topics", Context.MODE_PRIVATE)

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
    private var channelMessagesListener: ListenerRegistration? = null

    init {
        viewModelScope.launch {
            val cached = cacheDao.getCachedCommunities().map { it.toModel() }
            if (cached.isNotEmpty()) _communities.value = cached
        }
    }

    // ─── Memberships ──────────────────────────────────────────────────────────

    override fun loadUserMemberships(userId: String) {
        viewModelScope.launch {
            try {
                // 1. Emit cached memberships first
                val cachedIds = cacheDao.getMembershipIds(userId).toSet()
                if (cachedIds.isNotEmpty()) {
                    _myCommunityIds.value = cachedIds
                    syncCommunityTopics(cachedIds)
                }

                // 2. Fetch from Firebase
                val snapshot = db.collectionGroup("members")
                    .whereEqualTo("userId", userId)
                    .get()
                    .await()

                val ids = snapshot.documents.mapNotNull { doc ->
                    doc.reference.parent.parent?.id
                }.toSet()

                _myCommunityIds.value = ids
                syncCommunityTopics(ids)

                // 3. Write back to Room
                cacheDao.clearMemberships(userId)
                cacheDao.upsertMemberships(ids.map { CachedMembershipEntity(userId, it, System.currentTimeMillis()) })

            } catch (e: Exception) {
                Log.e("FirestoreCommunities", "Error fetching user memberships", e)
            }
        }
    }

    // ─── Communities ──────────────────────────────────────────────────────────

    override fun loadCommunities() {
        _isLoading.value = true
        viewModelScope.launch {
            try {
                // 1. Emit from Room cache immediately
                if (_communities.value.isEmpty()) {
                    val cached = cacheDao.getCachedCommunities().map { it.toModel() }
                    if (cached.isNotEmpty()) _communities.value = cached
                }

                // 2. Fetch from Firebase
                val snapshot = db.collection("communities").get().await()
                val loadedCommunities = snapshot.documents.mapNotNull { doc ->
                    val c = doc.toObject(Community::class.java)
                    c?.copy(id = doc.id)
                }
                _communities.value = loadedCommunities

                // 3. Write back to Room
                cacheDao.clearCommunities()
                cacheDao.upsertCommunities(loadedCommunities.map { it.toEntity() })

            } catch (e: Exception) {
                Log.e("FirestoreCommunities", "Error fetching communities", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    // ─── Community Details (Posts + Channels + Members) ───────────────────────

    override fun loadCommunityDetails(communityId: String) {
        _isLoading.value = true
        viewModelScope.launch {
            try {
                // 1. Immediately emit Room cache for all three
                val cachedPosts = cacheDao.getPostsByCommunity(communityId).map { it.toModel() }
                if (cachedPosts.isNotEmpty()) _posts.value = cachedPosts

                val cachedChannels = cacheDao.getChannelsByCommunity(communityId).map { it.toModel() }
                if (cachedChannels.isNotEmpty()) _channels.value = cachedChannels

                val cachedMembers = cacheDao.getMembersByCommunity(communityId).map { it.toModel() }
                if (cachedMembers.isNotEmpty()) _members.value = cachedMembers

                // 2. Fetch fresh data from Firebase in parallel
                val postsSnapshot = db.collection("communities").document(communityId)
                    .collection("posts").get().await()
                val loadedPosts = postsSnapshot.documents.mapNotNull { doc ->
                    val p = doc.toObject(Post::class.java)
                    p?.copy(id = doc.id)
                }.sortedByDescending { it.createdAt }
                _posts.value = loadedPosts

                val channelsSnapshot = db.collection("communities").document(communityId)
                    .collection("channels").get().await()
                val loadedChannels = channelsSnapshot.documents.mapNotNull { doc ->
                    val ch = doc.toObject(Channel::class.java)
                    ch?.copy(id = doc.id)
                }
                _channels.value = loadedChannels

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

                // 3. Write back to Room
                cacheDao.clearPostsByCommunity(communityId)
                cacheDao.upsertPosts(loadedPosts.map { it.toEntity(communityId) })

                cacheDao.clearChannelsByCommunity(communityId)
                cacheDao.upsertChannels(loadedChannels.map { it.toEntity(communityId) })

                cacheDao.clearMembersByCommunity(communityId)
                cacheDao.upsertMembers(loadedMembers.map { it.toEntity(communityId) })

            } catch (e: Exception) {
                Log.e("FirestoreCommunities", "Error fetching community details", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    // ─── Join Community ───────────────────────────────────────────────────────

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

                // Write membership to Room immediately
                val now = System.currentTimeMillis()
                cacheDao.upsertMemberships(listOf(CachedMembershipEntity(userId, communityId, now)))
                val newMember = CommunityMember(id = userId, userId = userId, displayName = displayName, role = "member", joinedAt = now)
                cacheDao.upsertMembers(listOf(newMember.toEntity(communityId, now)))

                val updatedMemberships = _myCommunityIds.value + communityId
                _myCommunityIds.value = updatedMemberships
                syncCommunityTopics(updatedMemberships)

                loadCommunities()
                loadCommunityDetails(communityId)
                onSuccess()
            } catch (e: Exception) {
                Log.e("FirestoreCommunities", "Error joining community", e)
                onFailure(e)
            }
        }
    }

    // ─── Create Community ─────────────────────────────────────────────────────

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

                // Write to Room immediately
                val now = System.currentTimeMillis()
                val newCommunity = Community(
                    id = communityRef.id, name = name.trim(), type = type,
                    sport = sport.trim(), description = description.trim(),
                    memberCount = 1, channelCount = 1, ownerId = ownerId
                )
                cacheDao.upsertCommunities(listOf(newCommunity.toEntity(now)))
                cacheDao.upsertMemberships(listOf(CachedMembershipEntity(ownerId, communityRef.id, now)))

                val updatedMemberships = _myCommunityIds.value + communityRef.id
                _myCommunityIds.value = updatedMemberships
                syncCommunityTopics(updatedMemberships)

                loadCommunities()
                onSuccess()
            } catch (e: Exception) {
                Log.e("FirestoreCommunities", "Error creating community", e)
                onFailure(e)
            }
        }
    }

    // ─── Create Post ──────────────────────────────────────────────────────────

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
                val docRef = db.collection("communities")
                    .document(communityId)
                    .collection("posts")
                    .add(payload)
                    .await()

                // Write to Room immediately
                val now = System.currentTimeMillis()
                val newPost = Post(
                    id = docRef.id, author = author, role = role, content = content.trim(),
                    time = "Just now", pinned = pinned, likes = 0, createdAt = now
                )
                cacheDao.upsertPosts(listOf(newPost.toEntity(communityId, now)))

                loadCommunityDetails(communityId)
                onSuccess()
            } catch (e: Exception) {
                Log.e("FirestoreCommunities", "Error creating announcement", e)
                onFailure(e)
            }
        }
    }

    // ─── Create Channel ───────────────────────────────────────────────────────

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

                // Write to Room immediately
                val now = System.currentTimeMillis()
                val newChannel = Channel(id = channelRef.id, name = name.trim(), type = type, mensajes = 0)
                cacheDao.upsertChannels(listOf(newChannel.toEntity(communityId, now)))

                loadCommunities()
                loadCommunityDetails(communityId)
                onSuccess()
            } catch (e: Exception) {
                Log.e("FirestoreCommunities", "Error creating channel", e)
                onFailure(e)
            }
        }
    }

    // ─── Remove Member ────────────────────────────────────────────────────────

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

                // Remove from Room immediately (best effort — memberId is the userId in members doc)
                try {
                    cacheDao.clearMemberships(memberId)
                } catch (_: Exception) {}

                loadCommunities()
                loadCommunityDetails(communityId)
                onSuccess()
            } catch (e: Exception) {
                Log.e("FirestoreCommunities", "Error removing member", e)
                onFailure(e)
            }
        }
    }

    // ─── Channel Messages ─────────────────────────────────────────────────────

    override fun loadChannelMessages(communityId: String, channelId: String) {
        viewModelScope.launch {
            try {
                val isDifferentChannel = communityId != activeCommunityId || channelId != activeChannelId
                activeCommunityId = communityId
                activeChannelId = channelId

                if (isDifferentChannel) {
                    oldestLoadedMessageSnapshot = null
                }

                channelMessagesListener?.remove()
                channelMessagesListener = null

                // 1. Emit Room cache immediately
                val cached = loadCachedRecentMessages(communityId, channelId)
                if (cached.isNotEmpty()) _channelMessages.value = cached

                // 2. Keep latest messages in real time
                channelMessagesListener = db.collection("communities").document(communityId)
                    .collection("channels").document(channelId)
                    .collection("messages")
                    .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
                    .limit(20)
                    .addSnapshotListener { snapshot, error ->
                        if (error != null) {
                            Log.e("FirestoreCommunities", "Realtime listener error for channel messages", error)
                            return@addSnapshotListener
                        }

                        if (snapshot == null) return@addSnapshotListener

                        oldestLoadedMessageSnapshot = snapshot.documents.lastOrNull()
                        _hasMoreOldChannelMessages.value = snapshot.size() >= 20

                        val latestMessages = snapshot.documents.mapNotNull { doc ->
                            val m = doc.toObject(ChannelMessage::class.java)
                            m?.copy(id = doc.id)
                        }.reversed()

                        // 1. Filter out any optimistic messages that have a matching server-side message
                        // We identify matches by (authorId + content + createdAt)
                        val serverMessagesSet = latestMessages.map {
                            "${it.authorId}_${it.content}_${it.createdAt}"
                        }.toSet()

                        val filteredCurrent = _channelMessages.value.filter { msg ->
                            if (msg.status == MessageStatus.SENDING) {
                                val identity = "${msg.authorId}_${msg.content}_${msg.createdAt}"
                                !serverMessagesSet.contains(identity)
                            } else {
                                true
                            }
                        }

                        // 2. Merge with the latest server messages
                        val merged = (filteredCurrent + latestMessages)
                            .distinctBy { it.id }
                            .sortedBy { it.createdAt }

                        _channelMessages.value = merged

                        viewModelScope.launch {
                            cacheRecentMessages(communityId, channelId, merged)
                        }
                    }

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
                    val merged = olderMessagesAsc + _channelMessages.value.filter { msg ->
                        olderMessagesAsc.none { it.id == msg.id }
                    }
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
                val localId = UUID.randomUUID().toString()
                val now = System.currentTimeMillis()

                val newMsg = ChannelMessage(
                    id = localId,
                    authorId = authorId,
                    authorName = authorName,
                    content = content.trim(),
                    createdAt = now,
                    status = MessageStatus.SENDING
                )

                // 1. Update UI immediately
                _channelMessages.value = (_channelMessages.value + newMsg)
                    .distinctBy { it.id }
                    .sortedBy { it.createdAt }

                // 2. Save to cache and pending queue
                cacheDao.upsertChannelMessages(listOf(newMsg.toEntity(communityId, channelId, now)))
                cacheDao.upsertPendingMessage(
                    PendingMessageEntity(
                        localId = localId,
                        communityId = communityId,
                        channelId = channelId,
                        authorId = authorId,
                        authorName = authorName,
                        content = content.trim(),
                        createdAt = now
                    )
                )

                // 3. Schedule background sync
                val constraints = Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()

                val syncRequest = OneTimeWorkRequestBuilder<MessageSyncWorker>()
                    .setConstraints(constraints)
                    .build()

                WorkManager.getInstance(getApplication<Application>()).enqueue(syncRequest)

                onSuccess()
            } catch (e: Exception) {
                Log.e("FirestoreCommunities", "Error sending channel message optimistically", e)
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

                onSuccess()
            } catch (e: Exception) {
                Log.e("FirestoreCommunities", "Error reacting to message", e)
                onFailure(e)
            }
        }
    }

    // ─── Post Comments ────────────────────────────────────────────────────────

    override fun loadPostComments(communityId: String, postId: String) {
        viewModelScope.launch {
            try {
                // 1. Emit Room cache immediately
                val cached = cacheDao.getCommentsByPost(communityId, postId).map { it.toModel() }
                if (cached.isNotEmpty()) _postComments.value = cached

                // 2. Fetch from Firebase
                val snapshot = db.collection("communities").document(communityId)
                    .collection("posts").document(postId)
                    .collection("comments").get().await()

                val comments = snapshot.documents.mapNotNull { doc ->
                    val comment = doc.toObject(PostComment::class.java)
                    comment?.copy(id = doc.id)
                }.sortedBy { it.createdAt }
                _postComments.value = comments

                // 3. Write to Room
                cacheDao.clearCommentsByPost(communityId, postId)
                cacheDao.upsertComments(comments.map { it.toEntity(communityId, postId) })

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

                var newCommentId = ""
                db.runTransaction { transaction ->
                    val postSnapshot = transaction.get(postRef)
                    val commentRef = postRef.collection("comments").document()
                    newCommentId = commentRef.id
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

                // Write to Room immediately
                val now = System.currentTimeMillis()
                val newComment = PostComment(
                    id = newCommentId, authorId = authorId, authorName = authorName,
                    content = content.trim(), createdAt = now
                )
                cacheDao.upsertComments(listOf(newComment.toEntity(communityId, postId, now)))

                loadPostComments(communityId, postId)
                onSuccess()
            } catch (e: Exception) {
                Log.e("FirestoreCommunities", "Error adding post comment", e)
                onFailure(e)
            }
        }
    }

    // ─── Private helpers ──────────────────────────────────────────────────────

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

    private fun toCommunityTopic(communityId: String): String {
        return "community_${communityId.replace(Regex("[^a-zA-Z0-9_-]"), "_")}".take(900)
    }

    private fun syncCommunityTopics(membershipIds: Set<String>) {
        val targetTopics = membershipIds.map { toCommunityTopic(it) }.toSet()
        val savedTopics = topicPrefs.getStringSet("community_topics", emptySet())?.toSet() ?: emptySet()

        val toSubscribe = targetTopics - savedTopics
        val toUnsubscribe = savedTopics - targetTopics

        toSubscribe.forEach { topic ->
            Firebase.messaging.subscribeToTopic(topic)
                .addOnFailureListener { e -> Log.e("FirestoreCommunities", "Topic subscribe failed: $topic", e) }
        }

        toUnsubscribe.forEach { topic ->
            Firebase.messaging.unsubscribeFromTopic(topic)
                .addOnFailureListener { e -> Log.e("FirestoreCommunities", "Topic unsubscribe failed: $topic", e) }
        }

        topicPrefs.edit().putStringSet("community_topics", targetTopics.toMutableSet()).apply()
    }

    override fun onCleared() {
        channelMessagesListener?.remove()
        channelMessagesListener = null
        super.onCleared()
    }
}
