package com.uniandes.sport.data.local

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

private const val PREFS_NAME = "pending_stats_insight_prefs"
private const val KEY       = "pending_stats_insights"

data class PendingStatsInsightPayload(
    val localId:         String = UUID.randomUUID().toString(),
    val userId:          String,
    val promptText:      String,          // full prompt built at enqueue time
    val createdAtMillis: Long   = System.currentTimeMillis()
)

/**
 * Offline queue for "How am I doing?" AI insight requests.
 * One pending request per user — newer overwrites older.
 */
object PendingStatsInsightStore {

    fun getAll(context: Context): List<PendingStatsInsightPayload> {
        val raw   = prefs(context).getString(KEY, "[]") ?: "[]"
        val array = JSONArray(raw)
        return (0 until array.length()).mapNotNull { i -> array.optJSONObject(i)?.toPayload() }
    }

    fun enqueue(context: Context, payload: PendingStatsInsightPayload) {
        val items = getAll(context).toMutableList()
        items.removeAll { it.userId == payload.userId }   // one per user
        items += payload
        save(context, items)
    }

    fun remove(context: Context, localId: String) {
        save(context, getAll(context).filterNot { it.localId == localId })
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun save(context: Context, items: List<PendingStatsInsightPayload>) {
        val array = JSONArray().apply { items.forEach { put(it.toJson()) } }
        prefs(context).edit().putString(KEY, array.toString()).apply()
    }

    private fun PendingStatsInsightPayload.toJson() = JSONObject().apply {
        put("localId",         localId)
        put("userId",          userId)
        put("promptText",      promptText)
        put("createdAtMillis", createdAtMillis)
    }

    private fun JSONObject.toPayload() = PendingStatsInsightPayload(
        localId         = getString("localId"),
        userId          = getString("userId"),
        promptText      = getString("promptText"),
        createdAtMillis = optLong("createdAtMillis", System.currentTimeMillis())
    )
}
