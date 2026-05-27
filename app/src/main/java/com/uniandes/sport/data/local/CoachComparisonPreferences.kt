package com.uniandes.sport.data.local

import android.content.Context

private const val PREFS_NAME = "coach_comparison_prefs"
private const val KEY_LAST_COMPARED_IDS = "last_compared_ids"
private const val KEY_HIGHLIGHT_OPTIMAL = "highlight_optimal"

/**
 * SharedPreferences storage implemented EXCLUSIVELY for the Coach Comparison feature.
 * Saves comparison history and UI display preferences.
 */
object CoachComparisonPreferences {

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Persists the comma-separated IDs of the last compared coaches.
     */
    fun saveLastComparedIds(context: Context, idsCsv: String) {
        prefs(context).edit().putString(KEY_LAST_COMPARED_IDS, idsCsv).apply()
    }

    /**
     * Retrieves the comma-separated IDs of the last compared coaches.
     */
    fun getLastComparedIds(context: Context): String =
        prefs(context).getString(KEY_LAST_COMPARED_IDS, "") ?: ""

    /**
     * Saves user choice on highlighting best-in-class rows (Price, Rating, etc.).
     */
    fun saveHighlightOptimal(context: Context, highlight: Boolean) {
        prefs(context).edit().putBoolean(KEY_HIGHLIGHT_OPTIMAL, highlight).apply()
    }

    /**
     * Checks if the user wants best-in-class highlights enabled (default is true).
     */
    fun getHighlightOptimal(context: Context): Boolean =
        prefs(context).getBoolean(KEY_HIGHLIGHT_OPTIMAL, true)
}
