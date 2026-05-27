package com.uniandes.sport.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

// dao para la tabla cached_warmup_routines.
// las queries usan el indice (category, intensity) para responder rapido
// a la combinacion seleccionada por el usuario sin escanear la tabla completa
@Dao
interface WarmupCacheDao {

    // query principal: rutinas que matchean la combinacion category+intensity.
    // espejo de la query compuesta de firestore pero en sqlite local,
    // utilizable cuando no hay red o como fallback al fallar la primera fetch
    @Query("SELECT * FROM cached_warmup_routines WHERE category = :category AND intensity = :intensity")
    suspend fun getByCategoryIntensity(category: String, intensity: String): List<CachedWarmupRoutineEntity>

    // snapshot completo de la cache, util para debug y exportacion a archivo
    @Query("SELECT * FROM cached_warmup_routines ORDER BY category, intensity, id")
    suspend fun getAll(): List<CachedWarmupRoutineEntity>

    // upsert: si la rutina ya existe (mismo id) la reemplaza, si no la inserta.
    // onconflictstrategy.replace garantiza idempotencia al cachear repetidamente
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRoutines(items: List<CachedWarmupRoutineEntity>)

    // borra todas las rutinas de una combinacion antes de reemplazarlas
    // con datos frescos del servidor; evita que rutinas eliminadas
    // en firestore queden colgadas en la cache local
    @Query("DELETE FROM cached_warmup_routines WHERE category = :category AND intensity = :intensity")
    suspend fun deleteByCategoryIntensity(category: String, intensity: String)
}
