package com.uniandes.sport.data.local

import android.content.Context

private const val PREFS_NAME = "profesores_kv_store"
private const val KEY_SELECTED_FILTER = "selected_filter"
private const val KEY_SEARCH_QUERY = "search_query"
private const val KEY_LAST_OPENED_PROFESOR = "last_opened_profesor"
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
