package com.uniandes.sport.data.warmup

import com.google.firebase.firestore.FirebaseFirestore
import com.uniandes.sport.models.warmup.WarmupExercise
import kotlinx.coroutines.tasks.await

// servicio que descarga rutinas de warm-up desde firestore.
//
// --- consulta estricta en el backend ---
// usa una compuesta where(category == x).where(intensity == y) para evitar
// procesamiento en memoria. requiere un indice compuesto en la consola de firebase
// sobre la coleccion warmup_routines (category asc + intensity asc).
//
// --- cache l1 "smart discard" ---
// en lugar de acumular todas las combinaciones en un mapa que satura ram,
// solo guarda la ultima clave consultada y su lista. si el usuario repite
// la misma combinacion, se devuelve la lista instantaneamente; si la cambia,
// las variables se reemplazan por completo para que el gc libere la lista anterior
object WarmupRoutinesService {

    private val db by lazy { FirebaseFirestore.getInstance() }

    private var cachedKey: String? = null
    private var cachedExercises: List<WarmupExercise> = emptyList()

    // devuelve el pool de ejercicios (aplanado a partir de las rutinas que matchean).
    // throws si falla la red o si el indice no existe (firebase devuelve failed_precondition)
    suspend fun getExercisesPool(category: String, intensity: String): List<WarmupExercise> {
        val key = "$category|$intensity"
        if (key == cachedKey && cachedExercises.isNotEmpty()) {
            return cachedExercises
        }

        val snapshot = db.collection("warmup_routines")
            .whereEqualTo("category", category)
            .whereEqualTo("intensity", intensity)
            .get()
            .await()

        val pool = snapshot.documents.flatMap { doc ->
            @Suppress("UNCHECKED_CAST")
            val raw = doc.get("exercises") as? List<Map<String, Any?>> ?: emptyList()
            raw.map { ex ->
                WarmupExercise(
                    name = ex["name"] as? String ?: "",
                    quantity = ex["quantity"] as? String ?: "",
                    description = ex["description"] as? String ?: ""
                )
            }
        }

        cachedKey = key
        cachedExercises = pool
        return pool
    }

    // expone el pool actualmente cacheado sin hacer red.
    // util cuando la pantalla de ejercicios entra justo despues de un fetch exitoso
    // y solo necesita reusar lo que ya esta en memoria
    fun currentPool(): List<WarmupExercise> = cachedExercises
}
