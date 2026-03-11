package com.uniandes.sport.viewmodels.communities

import com.uniandes.sport.models.Channel
import com.uniandes.sport.models.Community
import com.uniandes.sport.models.Post
import kotlinx.coroutines.flow.StateFlow

interface CommunitiesViewModelInterface {
    val communities: StateFlow<List<Community>>
    val posts: StateFlow<List<Post>>
    val channels: StateFlow<List<Channel>>
    val isLoading: StateFlow<Boolean>

    fun loadCommunities()
    fun loadCommunityDetails(communityId: String)
}
