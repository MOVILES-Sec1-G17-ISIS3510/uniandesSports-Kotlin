package com.uniandes.sport.data.local

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

private const val PREFS_NAME = "pending_booking_prefs"
private const val KEY_PENDING_BOOKINGS = "pending_bookings"

data class PendingBookingPayload(
    val localId: String = UUID.randomUUID().toString(),
    val userId: String,
    val studentName: String,
    val targetProfesorId: String,
    val targetProfesorName: String,
    val sport: String,
    val skillLevel: String,
    val schedule: String,
    val notes: String,
    val createdAtMillis: Long = System.currentTimeMillis()
)

object PendingBookingStore {

    fun getAll(context: Context): List<PendingBookingPayload> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_PENDING_BOOKINGS, "[]") ?: "[]"
        val array = JSONArray(raw)
        val items = mutableListOf<PendingBookingPayload>()

        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            items += obj.toPendingBookingPayload()
        }

        return items
    }

    fun enqueue(context: Context, payload: PendingBookingPayload) {
        val items = getAll(context).toMutableList()
        items.removeAll { it.localId == payload.localId }
        items += payload
        saveAll(context, items)
    }

    fun remove(context: Context, localId: String) {
        val remaining = getAll(context).filterNot { it.localId == localId }
        saveAll(context, remaining)
    }

    private fun saveAll(context: Context, items: List<PendingBookingPayload>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val array = JSONArray()
        items.forEach { payload -> array.put(payload.toJson()) }
        prefs.edit().putString(KEY_PENDING_BOOKINGS, array.toString()).apply()
    }

    private fun PendingBookingPayload.toJson(): JSONObject {
        return JSONObject().apply {
            put("localId", localId)
            put("userId", userId)
            put("studentName", studentName)
            put("targetProfesorId", targetProfesorId)
            put("targetProfesorName", targetProfesorName)
            put("sport", sport)
            put("skillLevel", skillLevel)
            put("schedule", schedule)
            put("notes", notes)
            put("createdAtMillis", createdAtMillis)
        }
    }

    private fun JSONObject.toPendingBookingPayload(): PendingBookingPayload {
        return PendingBookingPayload(
            localId = getString("localId"),
            userId = getString("userId"),
            studentName = getString("studentName"),
            targetProfesorId = getString("targetProfesorId"),
            targetProfesorName = getString("targetProfesorName"),
            sport = getString("sport"),
            skillLevel = getString("skillLevel"),
            schedule = getString("schedule"),
            notes = getString("notes"),
            createdAtMillis = optLong("createdAtMillis", System.currentTimeMillis())
        )
    }
}
