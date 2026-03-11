package com.uniandes.sport.viewmodels.communities

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FirebaseFirestore
import com.uniandes.sport.models.Channel
import com.uniandes.sport.models.Community
import com.uniandes.sport.models.Post
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class FirestoreCommunitiesViewModel : ViewModel(), CommunitiesViewModelInterface {

    private val db = FirebaseFirestore.getInstance()

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
        viewModelScope.launch {
            try {
                val snapshot = db.collection("communities").get().await()
                val loadedCommunities = snapshot.documents.mapNotNull { doc ->
                    val c = doc.toObject(Community::class.java)
                    c?.copy(id = doc.id)
                }
                _communities.value = loadedCommunities
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
                }
                _posts.value = loadedPosts

                // Fetch Channels
                val channelsSnapshot = db.collection("communities").document(communityId)
                    .collection("channels").get().await()
                    
                val loadedChannels = channelsSnapshot.documents.mapNotNull { doc ->
                    val ch = doc.toObject(Channel::class.java)
                    ch?.copy(id = doc.id)
                }
                _channels.value = loadedChannels

            } catch (e: Exception) {
                Log.e("FirestoreCommunities", "Error fetching community details", e)
            } finally {
                _isLoading.value = false
            }
        }
    }
}
