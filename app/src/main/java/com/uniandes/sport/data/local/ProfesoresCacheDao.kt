package com.uniandes.sport.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfesoresCacheDao {

    @Query("SELECT * FROM cached_profesores ORDER BY verified DESC, rating DESC, nombre ASC")
    fun observeProfesores(): Flow<List<CachedProfesorEntity>>

    @Query("SELECT * FROM cached_profesores ORDER BY verified DESC, rating DESC, nombre ASC")
    suspend fun getProfesores(): List<CachedProfesorEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProfesores(items: List<CachedProfesorEntity>)

    @Query("DELETE FROM cached_profesores")
    suspend fun clearProfesores()

    @Query("SELECT * FROM cached_profesor_reviews WHERE profesorId = :profesorId ORDER BY fecha DESC, reviewId DESC")
    fun observeReviews(profesorId: String): Flow<List<CachedReviewEntity>>

    @Query("SELECT * FROM cached_profesor_reviews WHERE profesorId = :profesorId ORDER BY fecha DESC, reviewId DESC")
    suspend fun getReviews(profesorId: String): List<CachedReviewEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertReviews(items: List<CachedReviewEntity>)

    @Query("DELETE FROM cached_profesor_reviews WHERE profesorId = :profesorId")
    suspend fun clearReviewsForProfesor(profesorId: String)

    @Query("SELECT * FROM cached_booking_requests WHERE sport = :sport ORDER BY createdAtMillis DESC")
    fun observeBookingRequestsBySport(sport: String): Flow<List<CachedBookingRequestEntity>>

    @Query("SELECT * FROM cached_booking_requests WHERE userId = :userId ORDER BY createdAtMillis DESC")
    fun observeBookingRequestsByUser(userId: String): Flow<List<CachedBookingRequestEntity>>

    @Query("SELECT * FROM cached_booking_requests WHERE sport = :sport ORDER BY createdAtMillis DESC")
    suspend fun getBookingRequestsBySport(sport: String): List<CachedBookingRequestEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBookingRequests(items: List<CachedBookingRequestEntity>)

    @Query("DELETE FROM cached_booking_requests WHERE sport = :sport")
    suspend fun clearBookingRequestsBySport(sport: String)
}
