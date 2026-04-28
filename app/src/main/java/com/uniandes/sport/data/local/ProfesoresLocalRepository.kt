package com.uniandes.sport.data.local

import android.content.Context
import com.uniandes.sport.models.BookingRequest
import com.uniandes.sport.models.Profesor
import com.uniandes.sport.models.Review
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ProfesoresLocalRepository private constructor(
    private val dao: ProfesoresCacheDao
) {

    fun observeProfesores(): Flow<List<Profesor>> =
        dao.observeProfesores().map { list -> list.map { it.toModel() } }

    suspend fun getCachedProfesores(): List<Profesor> =
        dao.getProfesores().map { it.toModel() }

    suspend fun replaceProfesores(items: List<Profesor>) {
        dao.clearProfesores()
        dao.upsertProfesores(items.map { it.toEntity() })
    }

    suspend fun upsertProfesor(profesor: Profesor) {
        dao.upsertProfesores(listOf(profesor.toEntity()))
    }

    fun observeReviews(profesorId: String): Flow<List<Review>> =
        dao.observeReviews(profesorId).map { list -> list.map { it.toModel() } }

    suspend fun getCachedReviews(profesorId: String): List<Review> =
        dao.getReviews(profesorId).map { it.toModel() }

    suspend fun replaceReviews(profesorId: String, items: List<Review>) {
        dao.clearReviewsForProfesor(profesorId)
        dao.upsertReviews(items.map { it.toEntity(profesorId) })
    }

    suspend fun upsertReview(profesorId: String, review: Review) {
        dao.upsertReviews(listOf(review.toEntity(profesorId)))
    }

    fun observeBookingRequestsBySport(sport: String): Flow<List<BookingRequest>> =
        dao.observeBookingRequestsBySport(sport).map { list -> list.map { it.toModel() } }

    fun observeBookingRequestsByUser(userId: String): Flow<List<BookingRequest>> =
        dao.observeBookingRequestsByUser(userId).map { list -> list.map { it.toModel() } }

    suspend fun getCachedBookingRequestsBySport(sport: String): List<BookingRequest> =
        dao.getBookingRequestsBySport(sport).map { it.toModel() }

    suspend fun replaceBookingRequestsBySport(sport: String, items: List<BookingRequest>) {
        dao.clearBookingRequestsBySport(sport)
        dao.upsertBookingRequests(items.map { it.toEntity() })
    }

    suspend fun upsertBookingRequests(items: List<BookingRequest>) {
        dao.upsertBookingRequests(items.map { it.toEntity() })
    }

    companion object {
        @Volatile
        private var INSTANCE: ProfesoresLocalRepository? = null

        fun getInstance(context: Context): ProfesoresLocalRepository {
            return INSTANCE ?: synchronized(this) {
                val db = ProfesoresCacheDatabase.getInstance(context)
                val instance = ProfesoresLocalRepository(db.cacheDao())
                INSTANCE = instance
                instance
            }
        }
    }
}
