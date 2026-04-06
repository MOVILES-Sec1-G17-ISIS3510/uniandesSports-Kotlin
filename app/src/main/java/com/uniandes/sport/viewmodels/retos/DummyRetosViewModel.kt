package com.uniandes.sport.viewmodels.retos

import androidx.lifecycle.ViewModel
import com.google.firebase.Timestamp
import com.uniandes.sport.models.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.*
import kotlinx.coroutines.flow.*
import androidx.lifecycle.viewModelScope

// viewmodel dummy actualizado con timestamps de firebase
class DummyRetosViewModel : ViewModel(), RetosViewModelInterface {

    private val _retos = MutableStateFlow<List<Reto>>(emptyList())
    override val retos: StateFlow<List<Reto>> = _retos.asStateFlow()

    private val _userChallenges = MutableStateFlow<Map<String, UserChallenge>>(emptyMap())
    override val userChallenges: StateFlow<Map<String, UserChallenge>> = _userChallenges.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    override val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _selectedType = MutableStateFlow("All")
    override val selectedType: StateFlow<String> = _selectedType.asStateFlow()

    private val _selectedSport = MutableStateFlow("All Sports")
    override val selectedSport: StateFlow<String> = _selectedSport.asStateFlow()

    private val _creationStatus = MutableStateFlow("IDLE")
    override val creationStatus: StateFlow<String> = _creationStatus.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    override val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    override val activeChallenges: StateFlow<List<Reto>> = combine(_retos, _searchQuery) { list, query ->
        list.filter { it.title.contains(query, ignoreCase = true) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    override val exploreChallenges: StateFlow<List<Reto>> = combine(_retos, _searchQuery) { list, query ->
        list.filter { it.title.contains(query, ignoreCase = true) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    private val currentUserId = "irHfuQbI4DZ0fUwAqkNbcSVvjNW2"

    init {
        fetchRetos()
    }

    override fun setTypeFilter(type: String) {
        _selectedType.value = type
    }

    override fun setSportFilter(sport: String) {
        _selectedSport.value = sport
    }

    override fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    override fun fetchRetos() {
        val indCreator = IndividualRetoCreator()
        val teamCreator = TeamRetoCreator()
        
        val calendar = Calendar.getInstance()
        val start = calendar.time
        calendar.add(Calendar.DAY_OF_YEAR, 20)
        val end = calendar.time

        // armamos los retos con timestamps de verdad
        val r1 = indCreator.createReto("100K Running Challenge", "running", "advanced", "100 km", currentUserId, "individual", start, end).copy(
            id = "challenge_running_100k",
            participantsCount = 45,
            progress = 0.35,
            progressByUser = mapOf(currentUserId to 0.35)
        )

        val r2 = teamCreator.createReto("UniAndes Soccer Cup", "soccer", "intermediate", "Win tournament", currentUserId, "team", start, end).copy(
            id = "challenge_soccer_cup",
            participantsCount = 8,
            progress = 0.20,
            progressByUser = mapOf(currentUserId to 0.20)
        )

        val r3 = indCreator.createReto("30-Day Push-ups", "calisthenics", "beginner", "1000 reps", currentUserId, "individual", start, end).copy(
            id = "challenge_pushups_30d",
            participantsCount = 67,
            progress = 0.42,
            progressByUser = mapOf(currentUserId to 0.42)
        )

        _retos.value = listOf(r1, r2, r3)
    }

    override fun joinReto(retoId: String, userId: String) { }
    
    override fun leaveReto(retoId: String, userId: String) { }

    override fun addReto(reto: Reto) {
        _retos.value = _retos.value + reto
    }
}
