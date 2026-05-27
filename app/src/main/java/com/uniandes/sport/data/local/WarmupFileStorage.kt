package com.uniandes.sport.data.local

import android.content.Context
import com.uniandes.sport.models.warmup.WarmupExercise
import com.uniandes.sport.models.warmup.WarmupRoutine
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// almacenamiento en archivos locales para exportar snapshots de warm-up.
// archivos en context.filesdir/warmup/ (almacenamiento privado, se borra al desinstalar).
// usado como auditoria/debug del ultimo pool descargado y como respaldo
// independiente de room (estrategia "multiple sources of truth")
object WarmupFileStorage {

    private fun baseDir(context: Context): File {
        val dir = File(context.filesDir, "warmup")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun timestampSuffix(): String =
        SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())

    // exporta el ultimo pool de ejercicios descargado, anotando categoria e intensidad.
    // sobrescribe siempre el archivo "warmup_last_pool.json" (single-entry, espejo de
    // hive box.put(0) que usa flutter para la nutrition feature)
    fun saveLastPool(
        context: Context,
        category: String,
        intensity: String,
        exercises: List<WarmupExercise>
    ): File {
        val payload = JSONObject().apply {
            put("exportedAt", System.currentTimeMillis())
            put("category", category)
            put("intensity", intensity)
            put("count", exercises.size)
            put("items", JSONArray().apply {
                exercises.forEach { ex ->
                    put(JSONObject().apply {
                        put("name", ex.name)
                        put("quantity", ex.quantity)
                        put("description", ex.description)
                    })
                }
            })
        }

        val file = File(baseDir(context), "warmup_last_pool.json")
        file.writeText(payload.toString(2))
        return file
    }

    // lee el ultimo pool guardado (si existe). util cuando ni la cache l1 ni room
    // tienen datos pero el usuario abrio la app antes con conexion
    fun readLastPool(context: Context): Triple<String, String, List<WarmupExercise>>? {
        val file = File(baseDir(context), "warmup_last_pool.json")
        if (!file.exists()) return null
        return try {
            val obj = JSONObject(file.readText())
            val category = obj.optString("category", "")
            val intensity = obj.optString("intensity", "")
            val items = mutableListOf<WarmupExercise>()
            val arr = obj.optJSONArray("items") ?: JSONArray()
            for (i in 0 until arr.length()) {
                val ex = arr.optJSONObject(i) ?: continue
                items += WarmupExercise(
                    name = ex.optString("name", ""),
                    quantity = ex.optString("quantity", ""),
                    description = ex.optString("description", "")
                )
            }
            Triple(category, intensity, items)
        } catch (_: Exception) {
            null
        }
    }

    // exporta un snapshot etiquetado con timestamp para auditoria/debug.
    // a diferencia de savelastpool, este si guarda multiples archivos historicos
    fun exportSnapshot(
        context: Context,
        category: String,
        intensity: String,
        routines: List<WarmupRoutine>
    ): File {
        val payload = JSONObject().apply {
            put("exportedAt", System.currentTimeMillis())
            put("category", category)
            put("intensity", intensity)
            put("routinesCount", routines.size)
            put("routines", JSONArray().apply {
                routines.forEach { r ->
                    put(JSONObject().apply {
                        put("id", r.id)
                        put("title", r.title)
                        put("durationMinutes", r.durationMinutes)
                        put("exercises", JSONArray().apply {
                            r.exercises.forEach { ex ->
                                put(JSONObject().apply {
                                    put("name", ex.name)
                                    put("quantity", ex.quantity)
                                    put("description", ex.description)
                                })
                            }
                        })
                    })
                }
            })
        }

        val file = File(baseDir(context), "warmup_snapshot_${timestampSuffix()}.json")
        file.writeText(payload.toString(2))
        return file
    }
}
