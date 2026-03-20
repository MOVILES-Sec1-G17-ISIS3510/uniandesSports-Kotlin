package com.uniandes.sport.viewmodels.communities

import com.uniandes.sport.models.Channel
import com.uniandes.sport.models.ChannelMessage
import com.uniandes.sport.models.Community
import com.uniandes.sport.models.CommunityMember
import com.uniandes.sport.models.Post
import com.uniandes.sport.models.PostComment
import kotlinx.coroutines.flow.StateFlow

interface CommunitiesViewModelInterface {
    val communities: StateFlow<List<Community>>
    val posts: StateFlow<List<Post>>
    val channels: StateFlow<List<Channel>>
    val channelMessages: StateFlow<List<ChannelMessage>>
    val hasMoreOldChannelMessages: StateFlow<Boolean>
    val isLoadingOlderChannelMessages: StateFlow<Boolean>
    val members: StateFlow<List<CommunityMember>>
    val postComments: StateFlow<List<PostComment>>
    val isLoading: StateFlow<Boolean>

    fun loadCommunities()
    fun loadCommunityDetails(communityId: String)
    fun joinCommunity(
        communityId: String,
        userId: String,
        displayName: String,
        onSuccess: () -> Unit = {},
        onFailure: (Exception) -> Unit = {}
    )

    fun createCommunity(
        name: String,
        type: String,
        sport: String,
        description: String,
        ownerId: String,
        ownerDisplayName: String,
        onSuccess: () -> Unit = {},
        onFailure: (Exception) -> Unit = {}
    )

    fun createAnnouncement(
        communityId: String,
        author: String,
        role: String,
        content: String,
        pinned: Boolean,
        onSuccess: () -> Unit = {},
        onFailure: (Exception) -> Unit = {}
    )

    fun createChannel(
        communityId: String,
        name: String,
        type: String,
        onSuccess: () -> Unit = {},
        onFailure: (Exception) -> Unit = {}
    )

    fun removeMember(
        communityId: String,
        memberId: String,
        onSuccess: () -> Unit = {},
        onFailure: (Exception) -> Unit = {}
    )

    fun loadChannelMessages(communityId: String, channelId: String)
    fun loadOlderChannelMessages()

    fun sendChannelMessage(
        communityId: String,
        channelId: String,
        authorId: String,
        authorName: String,
        content: String,
        onSuccess: () -> Unit = {},
        onFailure: (Exception) -> Unit = {}
    )

    fun reactToChannelMessage(
        communityId: String,
        channelId: String,
        messageId: String,
        userId: String,
        emoji: String,
        onSuccess: () -> Unit = {},
        onFailure: (Exception) -> Unit = {}
    )

    fun loadPostComments(communityId: String, postId: String)

    fun addPostComment(
        communityId: String,
        postId: String,
        authorId: String,
        authorName: String,
        content: String,
        onSuccess: () -> Unit = {},
        onFailure: (Exception) -> Unit = {}
    )
}
