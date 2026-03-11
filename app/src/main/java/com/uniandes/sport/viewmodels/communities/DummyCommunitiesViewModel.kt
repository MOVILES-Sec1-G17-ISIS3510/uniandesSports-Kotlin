package com.uniandes.sport.viewmodels.communities

import androidx.lifecycle.ViewModel
import com.uniandes.sport.models.Channel
import com.uniandes.sport.models.Community
import com.uniandes.sport.models.Post
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
        _isLoading.value = false
    }
}
