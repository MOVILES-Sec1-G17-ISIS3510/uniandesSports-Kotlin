package com.uniandes.sport.data.local

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

// almacen local de historial de analisis de ia (poses y tracks).
// guarda los resultados en sharedpreferences (metadata) y las fotos
// como archivos jpg en filesdir/ai_history/ (para cargar con coil).
// esto permite mostrar "your ai history" en la ui sin necesitar internet
// y sirve como evidencia de las estrategias de caching y eventual connectivity

private const val PREFS_NAME = "ai_history_prefs"
private const val KEY_HISTORY = "ai_history_entries"
private const val MAX_ENTRIES = 30

data class AiHistoryEntry(
    val id: String,
    val type: String,
    val eventId: String,
    val feedback: String,
    val imagePath: String,
    val createdAtMillis: Long = System.currentTimeMillis()
)

object AiHistoryStore {

    // directorio donde se guardan las fotos de los analisis
    private fun imageDir(context: Context): File {
        val dir = File(context.filesDir, "ai_history")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    // guardar una foto como archivo jpg y retornar el path.
    // coil carga imagenes desde file:// paths directamente
    fun saveImage(context: Context, bitmap: Bitmap, entryId: String): String {
        val file = File(imageDir(context), "${entryId}.jpg")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
        }
        return file.absolutePath
    }

    // agregar una entrada al historial (pose o track)
    fun addEntry(context: Context, entry: AiHistoryEntry) {
        val items = getAll(context).toMutableList()
        // evitar duplicados por id
        items.removeAll { it.id == entry.id }
        // agregar al inicio (mas reciente primero)
        items.add(0, entry)
        // limitar a max_entries para no acumular infinitamente
        val trimmed = items.take(MAX_ENTRIES)
        saveAll(context, trimmed)
    }

    fun getAll(context: Context): List<AiHistoryEntry> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_HISTORY, "[]") ?: "[]"
        val array = JSONArray(raw)
        val items = mutableListOf<AiHistoryEntry>()
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            items += AiHistoryEntry(
                id = obj.optString("id", ""),
                type = obj.optString("type", ""),
                eventId = obj.optString("eventId", ""),
                feedback = obj.optString("feedback", ""),
                imagePath = obj.optString("imagePath", ""),
                createdAtMillis = obj.optLong("createdAtMillis", 0L)
            )
        }
        return items
    }

    fun getRecent(context: Context, limit: Int = 10): List<AiHistoryEntry> {
        return getAll(context).take(limit)
    }

    // reemplazar una entrada pending por el resultado real del analisis.
    // busca por eventid y tipo, y reemplaza el feedback
    fun replacePendingForEvent(context: Context, eventId: String, type: String, realFeedback: String) {
        val items = getAll(context).toMutableList()
        val index = items.indexOfFirst {
            it.eventId == eventId && it.type == type && it.feedback.startsWith("Pending")
        }
        if (index >= 0) {
            items[index] = items[index].copy(feedback = realFeedback)
            saveAll(context, items)
        }
    }

    // eliminar todas las entradas pending (util al volver internet)
    fun clearPendingEntries(context: Context) {
        val items = getAll(context).filterNot { it.feedback.startsWith("Pending") }
        saveAll(context, items)
    }

    private fun saveAll(context: Context, items: List<AiHistoryEntry>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val array = JSONArray()
        items.forEach { entry ->
            array.put(JSONObject().apply {
                put("id", entry.id)
                put("type", entry.type)
                put("eventId", entry.eventId)
                put("feedback", entry.feedback)
                put("imagePath", entry.imagePath)
                put("createdAtMillis", entry.createdAtMillis)
            })
        }
        prefs.edit().putString(KEY_HISTORY, array.toString()).apply()
    }
}
