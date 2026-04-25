package com.uniandes.sport.data.local

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

private const val PREFS_NAME = "pending_open_match_prefs"
private const val KEY_PENDING_OPEN_MATCHES = "pending_open_matches"

data class PendingOpenMatchPayload(
    val localId: String,
    val title: String,
    val description: String,
    val location: String,
    val sport: String,
    val modality: String,
    val scheduledAtMillis: Long,
    val finishedAtMillis: Long?,
    val skillLevel: String,
    val maxParticipants: Long,
    val shouldJoin: Boolean,
    val createdBy: String,
    val createdAtMillis: Long = System.currentTimeMillis()
)

object PendingOpenMatchStore {

    fun getAll(context: Context): List<PendingOpenMatchPayload> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_PENDING_OPEN_MATCHES, "[]") ?: "[]"
        val array = JSONArray(raw)
        val items = mutableListOf<PendingOpenMatchPayload>()

        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            items += obj.toPendingOpenMatchPayload()
        }

        return items
    }

    fun enqueue(context: Context, payload: PendingOpenMatchPayload) {
        val items = getAll(context).toMutableList()
        items.removeAll { it.localId == payload.localId }
        items += payload
        saveAll(context, items)
    }

    fun remove(context: Context, localId: String) {
        val remaining = getAll(context).filterNot { it.localId == localId }
        saveAll(context, remaining)
    }

    private fun saveAll(context: Context, items: List<PendingOpenMatchPayload>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val array = JSONArray()
        items.forEach { payload -> array.put(payload.toJson()) }
        prefs.edit().putString(KEY_PENDING_OPEN_MATCHES, array.toString()).apply()
    }

    private fun PendingOpenMatchPayload.toJson(): JSONObject {
        return JSONObject().apply {
            put("localId", localId)
            put("title", title)
            put("description", description)
            put("location", location)
            put("sport", sport)
            put("modality", modality)
            put("scheduledAtMillis", scheduledAtMillis)
            put("finishedAtMillis", finishedAtMillis ?: JSONObject.NULL)
            put("skillLevel", skillLevel)
            put("maxParticipants", maxParticipants)
            put("shouldJoin", shouldJoin)
            put("createdBy", createdBy)
            put("createdAtMillis", createdAtMillis)
        }
    }

    private fun JSONObject.toPendingOpenMatchPayload(): PendingOpenMatchPayload {
        val finishedAt = if (isNull("finishedAtMillis")) null else getLong("finishedAtMillis")

        return PendingOpenMatchPayload(
            localId = getString("localId"),
            title = getString("title"),
            description = getString("description"),
            location = getString("location"),
            sport = getString("sport"),
            modality = getString("modality"),
            scheduledAtMillis = getLong("scheduledAtMillis"),
            finishedAtMillis = finishedAt,
            skillLevel = getString("skillLevel"),
            maxParticipants = getLong("maxParticipants"),
            shouldJoin = getBoolean("shouldJoin"),
            createdBy = getString("createdBy"),
            createdAtMillis = optLong("createdAtMillis", System.currentTimeMillis())
        )
    }
}
