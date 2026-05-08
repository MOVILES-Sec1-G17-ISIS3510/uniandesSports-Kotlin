package com.uniandes.sport.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.firebase.Timestamp
import com.uniandes.sport.models.Reto
import org.json.JSONArray
import org.json.JSONObject

// entidad de room para cachear retos localmente en sqlite.
// resuelve el antipatron "missed caching opportunity": sin esta tabla,
// cada vez que el usuario abre la pantalla de retos tiene que esperar
// a que firestore responda por red, causando una pantalla vacia temporal.
// con esta cache, los retos se muestran al instante desde la bd local
@Entity(tableName = "cached_retos")
data class CachedRetoEntity(
    @PrimaryKey val id: String,
    val title: String,
    val sport: String,
    val difficulty: String,
    val type: String,
    val goalLabel: String,
    val createdBy: String,
    val status: String,
    // participants y progressbyuser son estructuras complejas (lista y mapa)
    // que room no soporta directamente, asi que se serializan como json string
    val participantsJson: String,
    val participantsCount: Long,
    val progress: Double,
    val progressByUserJson: String,
    // timestamps de firebase se convierten a milisegundos (long) para room
    val createdAtMillis: Long,
    val startDateMillis: Long,
    val endDateMillis: Long,
    // cachedAt permite detectar datos obsoletos (antipatron "data staleness")
    val cachedAt: Long
)

// extension para convertir el modelo de negocio (reto) a entidad de room.
// se usa al guardar datos que llegan de firestore en la cache local
fun Reto.toEntity(now: Long = System.currentTimeMillis()): CachedRetoEntity =
    CachedRetoEntity(
        id = id,
        title = title,
        sport = sport,
        difficulty = difficulty,
        type = type,
        goalLabel = goalLabel,
        createdBy = createdBy,
        status = status,
        participantsJson = JSONArray(participants).toString(),
        participantsCount = participantsCount,
        progress = progress,
        progressByUserJson = JSONObject(progressByUser.mapValues { it.value }).toString(),
        createdAtMillis = createdAt?.toDate()?.time ?: 0L,
        startDateMillis = startDate?.toDate()?.time ?: 0L,
        endDateMillis = endDate?.toDate()?.time ?: 0L,
        cachedAt = now
    )

// extension para convertir la entidad de room al modelo de negocio (reto).
// se usa al leer datos desde la cache local para mostrar en la ui
fun CachedRetoEntity.toModel(): Reto {
    // deserializar la lista de participantes desde json
    val participantsList = mutableListOf<String>()
    try {
        val arr = JSONArray(participantsJson)
        for (i in 0 until arr.length()) participantsList.add(arr.getString(i))
    } catch (_: Exception) { }

    // deserializar el mapa de progreso por usuario desde json
    val progressMap = mutableMapOf<String, Double>()
    try {
        val obj = JSONObject(progressByUserJson)
        obj.keys().forEach { key -> progressMap[key] = obj.getDouble(key) }
    } catch (_: Exception) { }

    return Reto(
        id = id,
        title = title,
        sport = sport,
        difficulty = difficulty,
        type = type,
        goalLabel = goalLabel,
        createdBy = createdBy,
        status = status,
        participants = participantsList,
        participantsCount = participantsCount,
        progress = progress,
        progressByUser = progressMap,
        createdAt = if (createdAtMillis > 0) Timestamp(java.util.Date(createdAtMillis)) else null,
        startDate = if (startDateMillis > 0) Timestamp(java.util.Date(startDateMillis)) else null,
        endDate = if (endDateMillis > 0) Timestamp(java.util.Date(endDateMillis)) else null
    )
}
