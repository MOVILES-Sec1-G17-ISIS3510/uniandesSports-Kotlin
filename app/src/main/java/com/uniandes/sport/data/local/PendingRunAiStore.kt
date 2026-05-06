package com.uniandes.sport.data.local

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

private const val PREFS_NAME = "pending_run_ai_prefs"
private const val KEY_PENDING_RUN_AI = "pending_run_ai_requests"

data class PendingRunAiPayload(
    val localId: String = UUID.randomUUID().toString(),
    val runId: String,
    val userId: String,
    val distanceKm: Float,
    val pace: String,
    val elevationGain: Float,
    val cadence: Int,
    val createdAtMillis: Long = System.currentTimeMillis()
)

object PendingRunAiStore {

    fun getAll(context: Context): List<PendingRunAiPayload> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_PENDING_RUN_AI, "[]") ?: "[]"
        val array = JSONArray(raw)
        val items = mutableListOf<PendingRunAiPayload>()

        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            items += obj.toPendingRunAiPayload()
        }

        return items
    }

    fun enqueue(context: Context, payload: PendingRunAiPayload) {
        val items = getAll(context).toMutableList()
        items.removeAll { it.localId == payload.localId || it.runId == payload.runId }
        items += payload
        saveAll(context, items)
    }

    fun remove(context: Context, localId: String) {
        val remaining = getAll(context).filterNot { it.localId == localId }
        saveAll(context, remaining)
    }

    private fun saveAll(context: Context, items: List<PendingRunAiPayload>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val array = JSONArray()
        items.forEach { payload -> array.put(payload.toJson()) }
        prefs.edit().putString(KEY_PENDING_RUN_AI, array.toString()).apply()
    }

    private fun PendingRunAiPayload.toJson(): JSONObject {
        return JSONObject().apply {
            put("localId", localId)
            put("runId", runId)
            put("userId", userId)
            put("distanceKm", distanceKm.toDouble())
            put("pace", pace)
            put("elevationGain", elevationGain.toDouble())
            put("cadence", cadence)
            put("createdAtMillis", createdAtMillis)
        }
    }

    private fun JSONObject.toPendingRunAiPayload(): PendingRunAiPayload {
        return PendingRunAiPayload(
            localId = getString("localId"),
            runId = getString("runId"),
            userId = getString("userId"),
            distanceKm = optDouble("distanceKm", 0.0).toFloat(),
            pace = getString("pace"),
            elevationGain = optDouble("elevationGain", 0.0).toFloat(),
            cadence = optInt("cadence", 0),
            createdAtMillis = optLong("createdAtMillis", System.currentTimeMillis())
        )
    }
}
