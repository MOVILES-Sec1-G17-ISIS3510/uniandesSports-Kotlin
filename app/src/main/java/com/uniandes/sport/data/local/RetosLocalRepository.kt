package com.uniandes.sport.data.local

import android.content.Context
import com.uniandes.sport.models.Reto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// repositorio local que actua como facade sobre el dao de room.
// encapsula las operaciones de cache y expone modelos de negocio (reto)
// en vez de entidades de room (cachedretoentity).
// el viewmodel habla con este repositorio sin saber los detalles de room/sqlite.
// patron facade: simplifica la interfaz de la capa de datos local
class RetosLocalRepository private constructor(
    private val dao: RetosCacheDao
) {

    // observar retos reactivamente: retorna un flow que emite
    // cada vez que la tabla cached_retos cambia en la bd local.
    // el map convierte entidades de room a modelos de negocio
    fun observeRetos(): Flow<List<Reto>> =
        dao.observeRetos().map { list -> list.map { it.toModel() } }

    // lectura puntual de la cache (no reactiva).
    // se usa como fallback cuando firestore no responde
    suspend fun getCachedRetos(): List<Reto> =
        dao.getRetos().map { it.toModel() }

    // reemplaza toda la cache con datos frescos del servidor.
    // primero borra todo y luego inserta los nuevos datos.
    // esto evita retos eliminados en firestore que sigan apareciendo localmente
    suspend fun replaceRetos(items: List<Reto>) {
        dao.clearRetos()
        dao.upsertRetos(items.map { it.toEntity() })
    }

    // singleton thread-safe con double-checked locking.
    // una sola instancia del repositorio por toda la app
    companion object {
        @Volatile
        private var INSTANCE: RetosLocalRepository? = null

        fun getInstance(context: Context): RetosLocalRepository {
            return INSTANCE ?: synchronized(this) {
                val db = RetosCacheDatabase.getInstance(context)
                val instance = RetosLocalRepository(db.cacheDao())
                INSTANCE = instance
                instance
            }
        }
    }
}
