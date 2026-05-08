package com.uniandes.sport.data.local

import android.content.Context

// almacen llave-valor (sharedpreferences) para persistir el estado de la ui de retos.
// resuelve el antipatron "missing state persistence": sin esto, cada vez que el usuario
// cierra la app y vuelve a abrir, los filtros se resetean a "all" y pierde su contexto.
// tambien guarda drafts de creacion para que no se pierda el formulario si el usuario
// cierra el dialogo sin terminar.
// usa .apply() en vez de .commit() para no bloquear el hilo principal
// (segun las diapos, commit() es sincrono y puede causar gui lagging)
private const val PREFS_NAME = "retos_kv_store"
private const val KEY_SELECTED_TYPE = "selected_type"
private const val KEY_SELECTED_SPORT = "selected_sport"
private const val KEY_SEARCH_QUERY = "search_query"
private const val KEY_LAST_OPENED_RETO = "last_opened_reto"
private const val KEY_DRAFT_TITLE = "draft_title"
private const val KEY_DRAFT_SPORT = "draft_sport"
private const val KEY_DRAFT_TYPE = "draft_type"
private const val KEY_DRAFT_DIFFICULTY = "draft_difficulty"
private const val KEY_DRAFT_GOAL = "draft_goal"

// data class para el borrador de creacion de reto
data class NewRetoDraft(
    val title: String = "",
    val sport: String = "soccer",
    val type: String = "Individual",
    val difficulty: String = "Beginner",
    val goalLabel: String = ""
)

object RetosKeyValueStore {

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // --- filtros de ui ---

    fun saveSelectedType(context: Context, type: String) {
        prefs(context).edit().putString(KEY_SELECTED_TYPE, type).apply()
    }

    fun getSelectedType(context: Context): String =
        prefs(context).getString(KEY_SELECTED_TYPE, "All") ?: "All"

    fun saveSelectedSport(context: Context, sport: String) {
        prefs(context).edit().putString(KEY_SELECTED_SPORT, sport).apply()
    }

    fun getSelectedSport(context: Context): String =
        prefs(context).getString(KEY_SELECTED_SPORT, "All Sports") ?: "All Sports"

    fun saveSearchQuery(context: Context, query: String) {
        prefs(context).edit().putString(KEY_SEARCH_QUERY, query).apply()
    }

    fun getSearchQuery(context: Context): String =
        prefs(context).getString(KEY_SEARCH_QUERY, "") ?: ""

    // --- ultimo reto abierto ---

    fun saveLastOpenedRetoId(context: Context, retoId: String) {
        prefs(context).edit().putString(KEY_LAST_OPENED_RETO, retoId).apply()
    }

    fun getLastOpenedRetoId(context: Context): String =
        prefs(context).getString(KEY_LAST_OPENED_RETO, "") ?: ""

    // --- borrador de creacion de reto ---

    fun saveNewRetoDraft(context: Context, draft: NewRetoDraft) {
        prefs(context).edit()
            .putString(KEY_DRAFT_TITLE, draft.title)
            .putString(KEY_DRAFT_SPORT, draft.sport)
            .putString(KEY_DRAFT_TYPE, draft.type)
            .putString(KEY_DRAFT_DIFFICULTY, draft.difficulty)
            .putString(KEY_DRAFT_GOAL, draft.goalLabel)
            .apply()
    }

    fun getNewRetoDraft(context: Context): NewRetoDraft =
        NewRetoDraft(
            title = prefs(context).getString(KEY_DRAFT_TITLE, "") ?: "",
            sport = prefs(context).getString(KEY_DRAFT_SPORT, "soccer") ?: "soccer",
            type = prefs(context).getString(KEY_DRAFT_TYPE, "Individual") ?: "Individual",
            difficulty = prefs(context).getString(KEY_DRAFT_DIFFICULTY, "Beginner") ?: "Beginner",
            goalLabel = prefs(context).getString(KEY_DRAFT_GOAL, "") ?: ""
        )

    fun clearNewRetoDraft(context: Context) {
        prefs(context).edit()
            .remove(KEY_DRAFT_TITLE)
            .remove(KEY_DRAFT_SPORT)
            .remove(KEY_DRAFT_TYPE)
            .remove(KEY_DRAFT_DIFFICULTY)
            .remove(KEY_DRAFT_GOAL)
            .apply()
    }
}
