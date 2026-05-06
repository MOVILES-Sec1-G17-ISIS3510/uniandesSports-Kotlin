package com.uniandes.sport.data.local

import android.content.Context

private const val PREFS_NAME = "profesores_kv_store"
private const val KEY_SELECTED_FILTER = "selected_filter"
private const val KEY_SEARCH_QUERY = "search_query"
private const val KEY_LAST_OPENED_PROFESOR = "last_opened_profesor"
private const val KEY_FAVORITE_COACH_IDS = "favorite_coach_ids"
private const val KEY_ONLY_FAVORITES = "only_favorites"
private const val KEY_DRAFT_SPORT = "draft_sport"
private const val KEY_DRAFT_PRICE = "draft_price"
private const val KEY_DRAFT_EXPERIENCE = "draft_experience"
private const val KEY_DRAFT_WHATSAPP = "draft_whatsapp"
private const val KEY_DRAFT_SPECIALTY = "draft_specialty"

data class BecomeCoachDraft(
    val sport: String = "Soccer",
    val precio: String = "",
    val experiencia: String = "",
    val whatsapp: String = "",
    val especialidad: String = ""
)

object ProfesoresKeyValueStore {

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun saveSelectedFilter(context: Context, filter: String) {
        prefs(context).edit().putString(KEY_SELECTED_FILTER, filter).apply()
    }

    fun getSelectedFilter(context: Context): String =
        prefs(context).getString(KEY_SELECTED_FILTER, "All") ?: "All"

    fun saveSearchQuery(context: Context, query: String) {
        prefs(context).edit().putString(KEY_SEARCH_QUERY, query).apply()
    }

    fun getSearchQuery(context: Context): String =
        prefs(context).getString(KEY_SEARCH_QUERY, "") ?: ""

    fun saveLastOpenedProfesorId(context: Context, profesorId: String) {
        prefs(context).edit().putString(KEY_LAST_OPENED_PROFESOR, profesorId).apply()
    }

    fun getLastOpenedProfesorId(context: Context): String =
        prefs(context).getString(KEY_LAST_OPENED_PROFESOR, "") ?: ""

    fun saveFavoriteCoachIds(context: Context, coachIds: Set<String>) {
        prefs(context).edit().putStringSet(KEY_FAVORITE_COACH_IDS, coachIds).apply()
    }

    fun getFavoriteCoachIds(context: Context): Set<String> =
        prefs(context).getStringSet(KEY_FAVORITE_COACH_IDS, emptySet()) ?: emptySet()

    fun clearFavoriteCoachIds(context: Context) {
        prefs(context).edit().remove(KEY_FAVORITE_COACH_IDS).apply()
    }

    fun saveOnlyFavorites(context: Context, onlyFavorites: Boolean) {
        prefs(context).edit().putBoolean(KEY_ONLY_FAVORITES, onlyFavorites).apply()
    }

    fun getOnlyFavorites(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ONLY_FAVORITES, false)

    fun clearOnlyFavorites(context: Context) {
        prefs(context).edit().remove(KEY_ONLY_FAVORITES).apply()
    }

    fun saveBecomeCoachDraft(context: Context, draft: BecomeCoachDraft) {
        prefs(context).edit()
            .putString(KEY_DRAFT_SPORT, draft.sport)
            .putString(KEY_DRAFT_PRICE, draft.precio)
            .putString(KEY_DRAFT_EXPERIENCE, draft.experiencia)
            .putString(KEY_DRAFT_WHATSAPP, draft.whatsapp)
            .putString(KEY_DRAFT_SPECIALTY, draft.especialidad)
            .apply()
    }

    fun getBecomeCoachDraft(context: Context): BecomeCoachDraft =
        BecomeCoachDraft(
            sport = prefs(context).getString(KEY_DRAFT_SPORT, "Soccer") ?: "Soccer",
            precio = prefs(context).getString(KEY_DRAFT_PRICE, "") ?: "",
            experiencia = prefs(context).getString(KEY_DRAFT_EXPERIENCE, "") ?: "",
            whatsapp = prefs(context).getString(KEY_DRAFT_WHATSAPP, "") ?: "",
            especialidad = prefs(context).getString(KEY_DRAFT_SPECIALTY, "") ?: ""
        )

    fun clearBecomeCoachDraft(context: Context) {
        prefs(context).edit()
            .remove(KEY_DRAFT_SPORT)
            .remove(KEY_DRAFT_PRICE)
            .remove(KEY_DRAFT_EXPERIENCE)
            .remove(KEY_DRAFT_WHATSAPP)
            .remove(KEY_DRAFT_SPECIALTY)
            .apply()
    }
}
