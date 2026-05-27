package com.uniandes.sport.data.local

import android.content.Context
import com.uniandes.sport.models.warmup.WarmupExercise
import com.uniandes.sport.models.warmup.WarmupRoutine

// repositorio local (facade sobre el dao) para warm-up routines.
// el service y el viewmodel hablan con este repo sin saber detalles de room/sqlite.
// expone modelos de negocio (warmuproutine, warmupexercise) en vez de entidades.
//
// metodo de conveniencia getcachedexercisespool aplana las rutinas a un solo
// pool de ejercicios, igual que hace el service contra firestore
class WarmupLocalRepository private constructor(
    private val dao: WarmupCacheDao
) {

    suspend fun getCachedRoutines(category: String, intensity: String): List<WarmupRoutine> =
        dao.getByCategoryIntensity(category, intensity).map { it.toModel() }

    suspend fun getAllCachedRoutines(): List<WarmupRoutine> =
        dao.getAll().map { it.toModel() }

    // reemplaza las rutinas de una combinacion con datos frescos.
    // primero borra la combinacion (no toda la tabla) y luego inserta,
    // asi se conserva la cache de otras combinaciones que ya el usuario consulto
    suspend fun saveRoutines(category: String, intensity: String, items: List<WarmupRoutine>) {
        dao.deleteByCategoryIntensity(category, intensity)
        dao.upsertRoutines(items.map { it.toEntity() })
    }

    // helper que devuelve el pool aplanado de ejercicios para una combinacion.
    // matches la semantica de warmuproutinesservice.getexercisespool pero leyendo
    // de room (modo offline o fallback)
    suspend fun getCachedExercisesPool(category: String, intensity: String): List<WarmupExercise> =
        getCachedRoutines(category, intensity).flatMap { it.exercises }

    companion object {
        @Volatile
        private var INSTANCE: WarmupLocalRepository? = null

        fun getInstance(context: Context): WarmupLocalRepository {
            return INSTANCE ?: synchronized(this) {
                val db = WarmupCacheDatabase.getInstance(context)
                val instance = WarmupLocalRepository(db.warmupDao())
                INSTANCE = instance
                instance
            }
        }
    }
}
