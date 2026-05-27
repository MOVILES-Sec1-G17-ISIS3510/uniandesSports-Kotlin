package com.uniandes.sport.viewmodels.profesores

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.uniandes.sport.models.Profesor
import com.uniandes.sport.repositories.CoachComparisonRepository
import com.uniandes.sport.repositories.CoachFetchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Holds pre-computed best-in-class insights calculated on a background thread
 * (Dispatchers.Default) to avoid blocking the UI thread.
 *
 * MULTITHREADING: This data class is the result of CPU-bound comparisons
 * done asynchronously — not on the main thread.
 */
data class ComparisonInsights(
    val bestRatingCoach: Profesor? = null,
    val bestPriceCoach: Profesor? = null,
    val mostExperiencedCoach: Profesor? = null,
    val mostWinsCoach: Profesor? = null,
    val highestRating: Double = 0.0,
    val lowestPrice: Int = 99999,
    val mostExperience: Int = 0,
    val mostWins: Int = 0,
    val computedOnThread: String = "" // Debug: which thread computed this
)

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

    /** Insights computed asynchronously on Dispatchers.Default (background CPU thread) */
    private val _insights = MutableStateFlow(ComparisonInsights())
    val insights: StateFlow<ComparisonInsights> = _insights.asStateFlow()

    fun loadComparison(coachIdsStr: String) {
        val ids = coachIdsStr.split(",").filter { it.isNotBlank() }
        if (ids.isEmpty()) {
            _uiState.value = CoachComparisonUiState.Error("No coaches selected for comparison.")
            return
        }

        // Save last compared coach IDs to SharedPreferences exclusively for comparison history
        com.uniandes.sport.data.local.CoachComparisonPreferences.saveLastComparedIds(getApplication(), coachIdsStr)

        _uiState.value = CoachComparisonUiState.Loading

        // Parent Coroutine running on UI/Main thread
        viewModelScope.launch(Dispatchers.Main) {
            try {
                // Nested Child Coroutines running on Input/Output thread pool (Dispatchers.IO)
                val deferredList = ids.map { id ->
                    async(Dispatchers.IO) {
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
                    val idsCsv = successfulCoaches.joinToString(",") { it.id }
                    val namesCsv = successfulCoaches.joinToString(", ") { it.nombre.split(" ").firstOrNull() ?: it.nombre }
                    com.uniandes.sport.data.local.CoachComparisonPreferences.addComparisonToHistory(getApplication(), idsCsv, namesCsv)

                    // MULTITHREADING: Compute insights on Dispatchers.Default (CPU-bound background thread)
                    // This keeps the UI responsive while metrics are being calculated.
                    computeInsightsAsync(successfulCoaches)
                }
            } catch (e: Exception) {
                _uiState.value = CoachComparisonUiState.Error(e.message ?: "An unexpected error occurred.")
            }
        }
    }

    /**
     * MULTITHREADING: Runs best-in-class insight calculations on Dispatchers.Default.
     * Dispatchers.Default uses a thread pool optimized for CPU-intensive work,
     * keeping the main (UI) thread free for rendering.
     */
    private fun computeInsightsAsync(coaches: List<Profesor>) {
        viewModelScope.launch {
            val computed = withContext(Dispatchers.Default) {
                val threadName = Thread.currentThread().name
                Log.d("COMPARISON_INSIGHTS", "Computing insights on thread: $threadName")

                val prices = coaches.map { parsePrice(it.precio) }
                val lowestPrice = prices.minOrNull() ?: 99999
                val highestRating = coaches.maxOfOrNull { it.rating } ?: 0.0
                val experiences = coaches.map { parseExperience(it.experiencia) }
                val mostExp = experiences.maxOrNull() ?: 0
                val mostWins = coaches.maxOfOrNull { it.tournamentWins } ?: 0

                ComparisonInsights(
                    bestRatingCoach = coaches.firstOrNull { it.rating == highestRating && highestRating > 0.0 },
                    bestPriceCoach = coaches.firstOrNull { parsePrice(it.precio) == lowestPrice && lowestPrice != 99999 },
                    mostExperiencedCoach = coaches.firstOrNull { parseExperience(it.experiencia) == mostExp && mostExp > 0 },
                    mostWinsCoach = coaches.firstOrNull { it.tournamentWins == mostWins && mostWins > 0 },
                    highestRating = highestRating,
                    lowestPrice = lowestPrice,
                    mostExperience = mostExp,
                    mostWins = mostWins,
                    computedOnThread = threadName
                )
            }
            // Post result back to the UI thread via StateFlow
            _insights.value = computed
            Log.d("COMPARISON_INSIGHTS", "Insights posted to UI from thread: ${Thread.currentThread().name}")
        }
    }

    /** Parse price string to integer for comparison */
    private fun parsePrice(priceStr: String): Int {
        return priceStr.replace(Regex("[^0-9]"), "").toIntOrNull() ?: 99999
    }

    /** Parse experience string to integer years for comparison */
    private fun parseExperience(expStr: String): Int {
        return expStr.replace(Regex("[^0-9]"), "").toIntOrNull() ?: 0
    }
}
