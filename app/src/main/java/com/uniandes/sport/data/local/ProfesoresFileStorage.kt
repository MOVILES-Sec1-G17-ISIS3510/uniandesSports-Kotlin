package com.uniandes.sport.data.local

import android.content.Context
import com.uniandes.sport.models.BookingRequest
import com.uniandes.sport.models.Profesor
import com.uniandes.sport.models.Review
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ProfesoresFileStorage {

    private fun baseDir(context: Context): File {
        val dir = File(context.filesDir, "profesores")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun timestampSuffix(): String =
        SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())

    fun exportProfesoresSnapshot(context: Context, profesores: List<Profesor>): File {
        val payload = JSONObject().apply {
            put("exportedAt", System.currentTimeMillis())
            put("type", "profesores_snapshot")
            put("count", profesores.size)
            put("items", JSONArray().apply {
                profesores.forEach { profesor ->
                    put(
                        JSONObject().apply {
                            put("id", profesor.id)
                            put("nombre", profesor.nombre)
                            put("deporte", profesor.deporte)
                            put("rating", profesor.rating)
                            put("totalReviews", profesor.totalReviews)
                            put("precio", profesor.precio)
                            put("experiencia", profesor.experiencia)
                            put("whatsapp", profesor.whatsapp)
                            put("disponibilidad", profesor.disponibilidad)
                            put("especialidad", profesor.especialidad)
                            put("verified", profesor.verified)
                            put("sessionsDelivered", profesor.sessionsDelivered)
                            put("tournamentWins", profesor.tournamentWins)
                            put("rankInSport", profesor.rankInSport)
                        }
                    )
                }
            })
        }

        val file = File(baseDir(context), "profesores_snapshot_${timestampSuffix()}.json")
        file.writeText(payload.toString(2))
        return file
    }

    fun exportProfesorReviews(context: Context, profesor: Profesor, reviews: List<Review>): File {
        val payload = JSONObject().apply {
            put("exportedAt", System.currentTimeMillis())
            put("type", "profesor_reviews")
            put("profesorId", profesor.id)
            put("profesorNombre", profesor.nombre)
            put("count", reviews.size)
            put("items", JSONArray().apply {
                reviews.forEach { review ->
                    put(
                        JSONObject().apply {
                            put("id", review.id)
                            put("estudiante", review.estudiante)
                            put("rating", review.rating)
                            put("comentario", review.comentario)
                            put("fecha", review.fecha)
                        }
                    )
                }
            })
        }

        val safeId = profesor.id.ifBlank { "coach" }
        val file = File(baseDir(context), "reviews_${safeId}_${timestampSuffix()}.json")
        file.writeText(payload.toString(2))
        return file
    }

    fun exportBookingHistory(context: Context, userId: String, bookings: List<BookingRequest>): File {
        val payload = JSONObject().apply {
            put("exportedAt", System.currentTimeMillis())
            put("type", "booking_history")
            put("userId", userId)
            put("count", bookings.size)
            put("items", JSONArray().apply {
                bookings.forEach { booking ->
                    put(
                        JSONObject().apply {
                            put("id", booking.id)
                            put("studentName", booking.studentName)
                            put("targetProfesorId", booking.targetProfesorId)
                            put("targetProfesorName", booking.targetProfesorName)
                            put("sport", booking.sport)
                            put("skillLevel", booking.skillLevel)
                            put("schedule", booking.schedule)
                            put("notes", booking.notes)
                            put("status", booking.status)
                            put("createdAt", booking.createdAt.toDate().time)
                        }
                    )
                }
            })
        }

        val file = File(baseDir(context), "booking_history_${userId}_${timestampSuffix()}.json")
        file.writeText(payload.toString(2))
        return file
    }
}
