package com.uniandes.sport.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

// dao (data access object) para la tabla cached_retos.
// define las operaciones de lectura y escritura sobre la bd local.
// usa flow para observar cambios reactivamente (patron observer)
// y suspend functions para operaciones de escritura no bloqueantes
@Dao
interface RetosCacheDao {

    // observe retorna un flow que emite cada vez que la tabla cambia.
    // esto permite que la ui se actualice automaticamente cuando
    // se guardan nuevos datos en la cache (patron observer reactivo)
    @Query("SELECT * FROM cached_retos ORDER BY status ASC, participantsCount DESC, title ASC")
    fun observeRetos(): Flow<List<CachedRetoEntity>>

    // get retorna una lista estatica (snapshot), util para el fallback
    // de carga inicial cuando no hay conexion a firestore
    @Query("SELECT * FROM cached_retos ORDER BY status ASC, participantsCount DESC, title ASC")
    suspend fun getRetos(): List<CachedRetoEntity>

    // upsert: si el reto ya existe (mismo id) lo reemplaza, si no lo inserta.
    // onconflictstrategy.replace evita duplicados en la cache
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRetos(items: List<CachedRetoEntity>)

    // limpia toda la tabla antes de reemplazar con datos frescos del servidor
    @Query("DELETE FROM cached_retos")
    suspend fun clearRetos()
}
