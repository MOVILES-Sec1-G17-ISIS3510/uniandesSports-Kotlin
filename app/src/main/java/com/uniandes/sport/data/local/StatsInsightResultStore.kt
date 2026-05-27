package com.uniandes.sport.data.local

import android.content.Context

private const val PREFS_NAME       = "stats_insight_result_prefs"
private const val KEY_RESULT       = "ai_insight_result"
private const val KEY_GENERATED_AT = "ai_insight_generated_at"

/**
 * Persists the AI-generated insight text (and its timestamp) produced by
 * [StatsInsightSyncWorker] or by a direct online call.
 *
 * Result is NOT cleared on read — it stays until the user explicitly regenerates.
 */
object StatsInsightResultStore {

    fun save(context: Context, result: String, generatedAt: Long = System.currentTimeMillis()) {
        prefs(context).edit()
            .putString(KEY_RESULT, result)
            .putLong(KEY_GENERATED_AT, generatedAt)
            .apply()
    }

    fun get(context: Context): String? =
        prefs(context).getString(KEY_RESULT, null)

    fun getGeneratedAt(context: Context): Long =
        prefs(context).getLong(KEY_GENERATED_AT, 0L)

    fun clear(context: Context) {
        prefs(context).edit()
            .remove(KEY_RESULT)
            .remove(KEY_GENERATED_AT)
            .apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
