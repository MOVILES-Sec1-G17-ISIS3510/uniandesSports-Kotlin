package com.uniandes.sport.data.local

import android.content.Context
import com.uniandes.sport.models.Review
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

private const val PREFS_NAME = "pending_review_prefs"
private const val KEY_PENDING_REVIEWS = "pending_reviews"

data class PendingReviewPayload(
    val localId: String = "pending_${UUID.randomUUID()}",
    val profesorId: String,
    val reviewerId: String,
    val estudiante: String,
    val rating: Int,
    val comentario: String,
    val fecha: String,
    val createdAtMillis: Long = System.currentTimeMillis()
)

object PendingReviewStore {

    fun getAll(context: Context): List<PendingReviewPayload> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_PENDING_REVIEWS, "[]") ?: "[]"
        val array = JSONArray(raw)
        val items = mutableListOf<PendingReviewPayload>()

        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            items += obj.toPendingReviewPayload()
        }

        return items
    }

    fun getByProfesor(context: Context, profesorId: String): List<PendingReviewPayload> =
        getAll(context).filter { it.profesorId == profesorId }.sortedByDescending { it.createdAtMillis }

    fun enqueue(context: Context, payload: PendingReviewPayload) {
        val items = getAll(context).toMutableList()
        items.removeAll {
            it.localId == payload.localId ||
                (it.profesorId == payload.profesorId && it.reviewerId == payload.reviewerId)
        }
        items += payload
        saveAll(context, items)
    }

    fun remove(context: Context, localId: String) {
        val remaining = getAll(context).filterNot { it.localId == localId }
        saveAll(context, remaining)
    }

    fun toReview(payload: PendingReviewPayload): Review =
        Review(
            id = payload.localId,
            reviewerId = payload.reviewerId,
            estudiante = payload.estudiante,
            rating = payload.rating,
            comentario = payload.comentario,
            fecha = payload.fecha
        )

    private fun saveAll(context: Context, items: List<PendingReviewPayload>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val array = JSONArray()
        items.forEach { payload -> array.put(payload.toJson()) }
        prefs.edit().putString(KEY_PENDING_REVIEWS, array.toString()).apply()
    }

    private fun PendingReviewPayload.toJson(): JSONObject {
        return JSONObject().apply {
            put("localId", localId)
            put("profesorId", profesorId)
            put("reviewerId", reviewerId)
            put("estudiante", estudiante)
            put("rating", rating)
            put("comentario", comentario)
            put("fecha", fecha)
            put("createdAtMillis", createdAtMillis)
        }
    }

    private fun JSONObject.toPendingReviewPayload(): PendingReviewPayload {
        return PendingReviewPayload(
            localId = getString("localId"),
            profesorId = getString("profesorId"),
            reviewerId = getString("reviewerId"),
            estudiante = getString("estudiante"),
            rating = optInt("rating", 5),
            comentario = getString("comentario"),
            fecha = getString("fecha"),
            createdAtMillis = optLong("createdAtMillis", System.currentTimeMillis())
        )
    }
}
