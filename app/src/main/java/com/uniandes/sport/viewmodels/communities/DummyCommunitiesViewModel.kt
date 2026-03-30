package com.uniandes.sport.viewmodels.communities

import androidx.lifecycle.ViewModel
import com.uniandes.sport.models.Channel
import com.uniandes.sport.models.ChannelMessage
import com.uniandes.sport.models.Community
import com.uniandes.sport.models.CommunityMember
import com.uniandes.sport.models.Post
import com.uniandes.sport.models.PostComment
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class DummyCommunitiesViewModel : ViewModel(), CommunitiesViewModelInterface {

    private val _communities = MutableStateFlow<List<Community>>(emptyList())
    override val communities: StateFlow<List<Community>> = _communities.asStateFlow()

    private val _posts = MutableStateFlow<List<Post>>(emptyList())
    override val posts: StateFlow<List<Post>> = _posts.asStateFlow()

    private val _channels = MutableStateFlow<List<Channel>>(emptyList())
    override val channels: StateFlow<List<Channel>> = _channels.asStateFlow()

    private val _channelMessages = MutableStateFlow<List<ChannelMessage>>(emptyList())
    override val channelMessages: StateFlow<List<ChannelMessage>> = _channelMessages.asStateFlow()

    private val _hasMoreOldChannelMessages = MutableStateFlow(true)
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

    override fun loadCommunities() {
        _isLoading.value = true
        // Hardcoded Dummy Data referencing the React equivalent
        _communities.value = listOf(
            Community("1", "UniAndes Football Club", "Clan", "Soccer", "Official soccer clan. Weekly training sessions.", 48, 3),
            Community("2", "UniAndes Racquets", "Community", "Tennis", "Tennis community for all skill levels.", 32, 2),
            Community("3", "Basketball Warriors", "Clan", "Basketball", "Competitive basketball clan 3x3 and 5x5.", 24, 3),
            Community("4", "Running UniAndes", "Community", "Running", "Runners group for all levels.", 67, 2)
        )
        _isLoading.value = false
    }

    override fun loadUserMemberships(userId: String) {
        // Dummy implementation
        _myCommunityIds.value = setOf("1", "3") // Just simulating dummy memberships
    }

    override fun loadCommunityDetails(communityId: String) {
        _isLoading.value = true
        // Simulated Network Delay
        _posts.value = listOf(
            Post("1", "Daniel Torres", "Instructor", "⚡ Schedule change: tomorrow's training session moved to 5 PM at La Caneca. See you there!", "1h ago", true, 12),
            Post("2", "Sofia Castañeda", "Organizer", "🏆 Copa Turing 2026 registrations are OPEN! 16 team slots — register your team before Mar 1.", "3h ago", true, 34),
            Post("3", "Julián Martínez", "Member", "Great match today! Our team won 4-2 against Team Beta. Thanks for the coordination \uD83D\uDE4C", "6h ago", false, 8),
            Post("4", "Carlos Mendez", "Coach", "Tip: focus on your first touch — it determines the quality of your next action. Practice wall passes for 10 minutes before each session.", "1d ago", false, 21)
        )
        
        _channels.value = listOf(
            Channel("1", "general", "publico", 245),
            Channel("2", "matches", "publico", 128),
            Channel("3", "strategy", "privado", 67)
        )

        _members.value = listOf(
            CommunityMember("1", "u1", "Daniel Torres", "admin", System.currentTimeMillis()),
            CommunityMember("2", "u2", "Sofia Castañeda", "member", System.currentTimeMillis()),
            CommunityMember("3", "u3", "Julian Martinez", "member", System.currentTimeMillis())
        )
        _isLoading.value = false
    }

    override fun joinCommunity(
        communityId: String,
        userId: String,
        displayName: String,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val exists = _members.value.any { it.userId == userId }
        if (!exists) {
            _members.value = _members.value + CommunityMember(
                id = userId,
                userId = userId,
                displayName = displayName,
                role = "member",
                joinedAt = System.currentTimeMillis()
            )
            _communities.value = _communities.value.map {
                if (it.id == communityId) it.copy(memberCount = it.memberCount + 1) else it
            }
        }
        onSuccess()
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
        val newId = (_communities.value.size + 1).toString()
        _communities.value = listOf(
            Community(
                id = newId,
                name = name,
                type = type,
                sport = sport,
                description = description,
                memberCount = 1,
                channelCount = 1
            )
        ) + _communities.value
        onSuccess()
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
        _posts.value = listOf(
            Post(
                id = (_posts.value.size + 1).toString(),
                author = author,
                role = role,
                content = content,
                time = "Just now",
                pinned = pinned,
                likes = 0
            )
        ) + _posts.value
        onSuccess()
    }

    override fun createChannel(
        communityId: String,
        name: String,
        type: String,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        _channels.value = _channels.value + Channel(
            id = (_channels.value.size + 1).toString(),
            name = name,
            type = type,
            mensajes = 0
        )

        _communities.value = _communities.value.map {
            if (it.id == communityId) it.copy(channelCount = it.channelCount + 1) else it
        }
        onSuccess()
    }

    override fun removeMember(
        communityId: String,
        memberId: String,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val before = _members.value.size
        _members.value = _members.value.filterNot { it.userId == memberId || it.id == memberId }
        if (_members.value.size < before) {
            _communities.value = _communities.value.map {
                if (it.id == communityId && it.memberCount > 0) it.copy(memberCount = it.memberCount - 1) else it
            }
        }
        onSuccess()
    }

    override fun loadChannelMessages(communityId: String, channelId: String) {
        val now = System.currentTimeMillis()
        _channelMessages.value = (1..20).map { i ->
            ChannelMessage(
                id = i.toString(),
                authorId = if (i % 2 == 0) "u1" else "u2",
                authorName = if (i % 2 == 0) "Daniel Torres" else "Sofia Castañeda",
                content = "Mensaje reciente #$i",
                createdAt = now - (20 - i) * 60000L
            )
        }
        _hasMoreOldChannelMessages.value = true
    }

    override fun loadOlderChannelMessages() {
        if (!_hasMoreOldChannelMessages.value || _isLoadingOlderChannelMessages.value) return
        _isLoadingOlderChannelMessages.value = true

        val firstId = _channelMessages.value.firstOrNull()?.id?.toIntOrNull() ?: 1
        val olderStart = firstId - 20
        if (olderStart <= 0) {
            _hasMoreOldChannelMessages.value = false
            _isLoadingOlderChannelMessages.value = false
            return
        }

        val now = System.currentTimeMillis()
        val older = (olderStart until firstId).map { i ->
            ChannelMessage(
                id = i.toString(),
                authorId = if (i % 2 == 0) "u1" else "u2",
                authorName = if (i % 2 == 0) "Daniel Torres" else "Sofia Castañeda",
                content = "Mensaje antiguo #$i",
                createdAt = now - (200 - i) * 60000L
            )
        }

        _channelMessages.value = older + _channelMessages.value
        _hasMoreOldChannelMessages.value = olderStart > 1
        _isLoadingOlderChannelMessages.value = false
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
        _channelMessages.value = _channelMessages.value + ChannelMessage(
            id = (_channelMessages.value.size + 1).toString(),
            authorId = authorId,
            authorName = authorName,
            content = content,
            createdAt = System.currentTimeMillis()
        )

        _channels.value = _channels.value.map {
            if (it.id == channelId) it.copy(mensajes = it.mensajes + 1) else it
        }
        onSuccess()
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
        _channelMessages.value = _channelMessages.value.map { msg ->
            if (msg.id == messageId) {
                val userReactions = msg.userReactions.toMutableMap()
                val counts = msg.reactions.toMutableMap()
                val previousEmoji = userReactions[userId]

                if (previousEmoji == emoji) {
                    userReactions.remove(userId)
                    val newPrevCount = (counts[emoji] ?: 0L) - 1L
                    if (newPrevCount > 0) counts[emoji] = newPrevCount else counts.remove(emoji)
                } else {
                    if (!previousEmoji.isNullOrBlank()) {
                        val newPrevCount = (counts[previousEmoji] ?: 0L) - 1L
                        if (newPrevCount > 0) counts[previousEmoji] = newPrevCount else counts.remove(previousEmoji)
                    }
                    userReactions[userId] = emoji
                    counts[emoji] = (counts[emoji] ?: 0L) + 1L
                }

                msg.copy(
                    reactions = counts,
                    userReactions = userReactions
                )
            } else msg
        }
        onSuccess()
    }

    override fun loadPostComments(communityId: String, postId: String) {
        _postComments.value = listOf(
            PostComment("1", "u1", "Daniel Torres", "Buen post!", System.currentTimeMillis() - 60000),
            PostComment("2", "u2", "Sofia Castañeda", "Me interesa", System.currentTimeMillis() - 30000)
        )
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
        _postComments.value = _postComments.value + PostComment(
            id = (_postComments.value.size + 1).toString(),
            authorId = authorId,
            authorName = authorName,
            content = content,
            createdAt = System.currentTimeMillis()
        )
        onSuccess()
    }
}
