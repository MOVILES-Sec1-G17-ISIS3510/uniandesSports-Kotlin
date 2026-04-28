package com.uniandes.sport.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.firebase.Timestamp
import com.uniandes.sport.models.BookingRequest
import com.uniandes.sport.models.Profesor
import com.uniandes.sport.models.Review

@Entity(tableName = "cached_profesores")
data class CachedProfesorEntity(
    @PrimaryKey val id: String,
    val nombre: String,
    val deporte: String,
    val rating: Double,
    val totalReviews: Int,
    val precio: String,
    val experiencia: String,
    val whatsapp: String,
    val disponibilidad: String,
    val especialidad: String,
    val verified: Boolean,
    val sessionsDelivered: Int,
    val tournamentWins: Int,
    val rankInSport: Int,
    val totalCoachesInSport: Int,
    val cachedAt: Long
)

fun Profesor.toEntity(now: Long = System.currentTimeMillis()): CachedProfesorEntity =
    CachedProfesorEntity(
        id = id,
        nombre = nombre,
        deporte = deporte,
        rating = rating,
        totalReviews = totalReviews,
        precio = precio,
        experiencia = experiencia,
        whatsapp = whatsapp,
        disponibilidad = disponibilidad,
        especialidad = especialidad,
        verified = verified,
        sessionsDelivered = sessionsDelivered,
        tournamentWins = tournamentWins,
        rankInSport = rankInSport,
        totalCoachesInSport = totalCoachesInSport,
        cachedAt = now
    )

fun CachedProfesorEntity.toModel(): Profesor =
    Profesor(
        id = id,
        nombre = nombre,
        deporte = deporte,
        rating = rating,
        totalReviews = totalReviews,
        precio = precio,
        experiencia = experiencia,
        whatsapp = whatsapp,
        disponibilidad = disponibilidad,
        especialidad = especialidad,
        verified = verified,
        sessionsDelivered = sessionsDelivered,
        tournamentWins = tournamentWins,
        rankInSport = rankInSport,
        totalCoachesInSport = totalCoachesInSport
    )

@Entity(tableName = "cached_profesor_reviews", primaryKeys = ["profesorId", "reviewId"])
data class CachedReviewEntity(
    val profesorId: String,
    val reviewId: String,
    val estudiante: String,
    val rating: Int,
    val comentario: String,
    val fecha: String,
    val cachedAt: Long
)

fun Review.toEntity(profesorId: String, now: Long = System.currentTimeMillis()): CachedReviewEntity =
    CachedReviewEntity(
        profesorId = profesorId,
        reviewId = id,
        estudiante = estudiante,
        rating = rating,
        comentario = comentario,
        fecha = fecha,
        cachedAt = now
    )

fun CachedReviewEntity.toModel(): Review =
    Review(
        id = reviewId,
        estudiante = estudiante,
        rating = rating,
        comentario = comentario,
        fecha = fecha
    )

@Entity(tableName = "cached_booking_requests")
data class CachedBookingRequestEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val studentName: String,
    val targetProfesorId: String,
    val targetProfesorName: String,
    val sport: String,
    val skillLevel: String,
    val schedule: String,
    val notes: String,
    val status: String,
    val createdAtMillis: Long,
    val cachedAt: Long
)

fun BookingRequest.toEntity(now: Long = System.currentTimeMillis()): CachedBookingRequestEntity =
    CachedBookingRequestEntity(
        id = id,
        userId = userId,
        studentName = studentName,
        targetProfesorId = targetProfesorId,
        targetProfesorName = targetProfesorName,
        sport = sport,
        skillLevel = skillLevel,
        schedule = schedule,
        notes = notes,
        status = status,
        createdAtMillis = createdAt.toDate().time,
        cachedAt = now
    )

fun CachedBookingRequestEntity.toModel(): BookingRequest =
    BookingRequest(
        id = id,
        userId = userId,
        studentName = studentName,
        targetProfesorId = targetProfesorId,
        targetProfesorName = targetProfesorName,
        sport = sport,
        skillLevel = skillLevel,
        schedule = schedule,
        notes = notes,
        status = status,
        createdAt = Timestamp(java.util.Date(createdAtMillis))
    )
