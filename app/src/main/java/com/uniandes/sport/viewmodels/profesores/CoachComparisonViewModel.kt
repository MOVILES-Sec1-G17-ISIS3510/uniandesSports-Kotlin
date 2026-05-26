package com.uniandes.sport.viewmodels.profesores

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.uniandes.sport.models.Profesor
import com.uniandes.sport.repositories.CoachComparisonRepository
import com.uniandes.sport.repositories.CoachFetchResult
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface CoachComparisonUiState {
    object Loading : CoachComparisonUiState

    data class Success(
        val coaches: List<Profesor>,
        val isOffline: Boolean
    ) : CoachComparisonUiState

    data class Error(
        val message: String
    ) : CoachComparisonUiState

    object EmptyOffline : CoachComparisonUiState
}

class CoachComparisonViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = CoachComparisonRepository.getInstance(application)

    private val _uiState = MutableStateFlow<CoachComparisonUiState>(CoachComparisonUiState.Loading)
    val uiState: StateFlow<CoachComparisonUiState> = _uiState.asStateFlow()

    fun loadComparison(coachIdsStr: String) {
        val ids = coachIdsStr.split(",").filter { it.isNotBlank() }
        if (ids.isEmpty()) {
            _uiState.value = CoachComparisonUiState.Error("No coaches selected for comparison.")
            return
        }

        _uiState.value = CoachComparisonUiState.Loading

        viewModelScope.launch {
            try {
                // Fetch coaches simultaneously using Kotlin Coroutines async/await
                val deferredList = ids.map { id ->
                    async {
                        repository.fetchCoach(id)
                    }
                }

                val results = deferredList.awaitAll()

                val successfulCoaches = mutableListOf<Profesor>()
                var hasFailure = false
                val isOfflineFallback = !repository.isNetworkConnected()

                for (result in results) {
                    when (result) {
                        is CoachFetchResult.Success -> {
                            successfulCoaches.add(result.coach)
                        }
                        is CoachFetchResult.Failure -> {
                            hasFailure = true
                        }
                    }
                }

                // If any coach failed to load (no internet AND no local cache exists for it)
                if (hasFailure || successfulCoaches.size < ids.size) {
                    if (!repository.isNetworkConnected()) {
                        _uiState.value = CoachComparisonUiState.EmptyOffline
                    } else {
                        _uiState.value = CoachComparisonUiState.Error("Failed to load some coaches for comparison.")
                    }
                } else {
                    _uiState.value = CoachComparisonUiState.Success(
                        coaches = successfulCoaches,
                        isOffline = isOfflineFallback
                    )
                }
            } catch (e: Exception) {
                _uiState.value = CoachComparisonUiState.Error(e.message ?: "An unexpected error occurred.")
            }
        }
    }
}
