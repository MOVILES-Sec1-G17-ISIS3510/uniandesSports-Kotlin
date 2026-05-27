package com.uniandes.sport.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.uniandes.sport.models.warmup.WarmupExercise
import com.uniandes.sport.models.warmup.WarmupRoutine
import org.json.JSONArray
import org.json.JSONObject

// entidad room para cachear rutinas de warm-up en sqlite.
// resuelve el antipatron "missed caching opportunity": con esta tabla,
// abrir la pantalla de ejercicios sin internet ya no muestra un error,
// sino las rutinas guardadas localmente desde la ultima descarga.
//
// indice compuesto en (category, intensity) para acelerar las queries
// con whereequalto x2 (espejo de la query compuesta de firestore)
@Entity(
    tableName = "cached_warmup_routines",
    indices = [Index(value = ["category", "intensity"])]
)
data class CachedWarmupRoutineEntity(
    @PrimaryKey val id: String,
    val title: String,
    val category: String,
    val intensity: String,
    val durationMinutes: Int,
    // exercises es una lista de mapas (name/quantity/description) que room
    // no soporta directamente, asi que se serializa como json string
    val exercisesJson: String,
    // cachedat permite detectar datos obsoletos (antipatron "data staleness")
    // y futuras politicas de ttl
    val cachedAt: Long
)

// conversion de modelo de negocio (warmuproutine) a entidad room.
// se usa al guardar rutinas que llegan de firestore en la cache local
fun WarmupRoutine.toEntity(now: Long = System.currentTimeMillis()): CachedWarmupRoutineEntity {
    val arr = JSONArray()
    exercises.forEach { ex ->
        arr.put(JSONObject().apply {
            put("name", ex.name)
            put("quantity", ex.quantity)
            put("description", ex.description)
        })
    }
    return CachedWarmupRoutineEntity(
        id = id,
        title = title,
        category = category,
        intensity = intensity,
        durationMinutes = durationMinutes,
        exercisesJson = arr.toString(),
        cachedAt = now
    )
}

// conversion de entidad room a modelo de negocio (warmuproutine).
// se usa al leer datos desde la cache local
fun CachedWarmupRoutineEntity.toModel(): WarmupRoutine {
    val exercises = mutableListOf<WarmupExercise>()
    try {
        val arr = JSONArray(exercisesJson)
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            exercises += WarmupExercise(
                name = obj.optString("name", ""),
                quantity = obj.optString("quantity", ""),
                description = obj.optString("description", "")
            )
        }
    } catch (_: Exception) { }

    return WarmupRoutine(
        id = id,
        title = title,
        category = category,
        intensity = intensity,
        durationMinutes = durationMinutes,
        exercises = exercises
    )
}
