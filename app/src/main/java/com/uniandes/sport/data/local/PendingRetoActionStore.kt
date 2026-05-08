package com.uniandes.sport.data.local

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

// almacen de acciones pendientes (join/leave) para retos cuando no hay internet.
// resuelve el antipatron #5 "non-existent result notification" y #9 "unexpected
// background process": el usuario puede unirse o salir de un reto sin internet,
// la accion se guarda en sharedpreferences y se sincroniza con workmanager
// cuando vuelve la conexion. el usuario recibe notificacion al sincronizar.
// patron identico a pendingReviewStore en profesores

private const val PREFS_NAME = "pending_reto_action_prefs"
private const val KEY_PENDING_ACTIONS = "pending_reto_actions"

data class PendingRetoActionPayload(
    val localId: String,
    val retoId: String,
    val userId: String,
    val action: String,
    val createdAtMillis: Long = System.currentTimeMillis()
)

object PendingRetoActionStore {

    fun getAll(context: Context): List<PendingRetoActionPayload> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_PENDING_ACTIONS, "[]") ?: "[]"
        val array = JSONArray(raw)
        val items = mutableListOf<PendingRetoActionPayload>()
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            items += obj.toPayload()
        }
        return items
    }

    fun enqueue(context: Context, payload: PendingRetoActionPayload) {
        val items = getAll(context).toMutableList()
        // evitar duplicados: si ya hay una accion pendiente para el mismo reto y usuario, reemplazar
        items.removeAll { it.retoId == payload.retoId && it.userId == payload.userId }
        items += payload
        saveAll(context, items)
    }

    fun remove(context: Context, localId: String) {
        val remaining = getAll(context).filterNot { it.localId == localId }
        saveAll(context, remaining)
    }

    fun getPendingForUser(context: Context, userId: String): List<PendingRetoActionPayload> {
        return getAll(context).filter { it.userId == userId }
    }

    private fun saveAll(context: Context, items: List<PendingRetoActionPayload>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val array = JSONArray()
        items.forEach { array.put(it.toJson()) }
        prefs.edit().putString(KEY_PENDING_ACTIONS, array.toString()).apply()
    }

    private fun PendingRetoActionPayload.toJson(): JSONObject {
        return JSONObject().apply {
            put("localId", localId)
            put("retoId", retoId)
            put("userId", userId)
            put("action", action)
            put("createdAtMillis", createdAtMillis)
        }
    }

    private fun JSONObject.toPayload(): PendingRetoActionPayload {
        return PendingRetoActionPayload(
            localId = optString("localId", ""),
            retoId = optString("retoId", ""),
            userId = optString("userId", ""),
            action = optString("action", ""),
            createdAtMillis = optLong("createdAtMillis", System.currentTimeMillis())
        )
    }
}
