package com.uniandes.sport.data.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * ActivityLogEntity — Log de actividades del usuario para histórico y gráficas.
 * 
 * DOCUMENTACIÓN:
 * - Patrón: Room Entity con ForeignKey a UserStatsEntity (CASCADE delete)
 * - activityType: POST, MESSAGE, EVENT, RUN, BADGE
 * - activityDate: Fecha de la actividad (no del registro)
 * - value: Cantidad numérica (ej: km en RUN, count en POST)
 * - índices: userId + date para queries rápidas de gráficas
 * 
 * FEATURE: Local Storage (Requisito b) + Caching (Requisito c)
 * Se almacenan históricos para:
 * 1. Calcular streaks (días seguidos escribiendo)
 * 2. Generar gráficas (actividad por semana)
 * 3. Validar badges (usuarios que cumplen condiciones)
 * 
 * @author Juan Felipe Hernández
 * @since 26-may-2026
 */
@Entity(
    tableName = "activity_log",
    indices = [Index("userId"), Index("userId", "activityDate")],
    foreignKeys = [ForeignKey(
        entity = UserStatsEntity::class,
        parentColumns = ["userId"],
        childColumns = ["userId"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class ActivityLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val userId: String = "",
    val activityType: String = "", // POST, MESSAGE, EVENT, RUN, BADGE
    val activityDate: Long = 0L, // fecha de la actividad
    val value: Float = 0f, // km, count, etc
    val recordedAt: Long = System.currentTimeMillis() // cuándo se registró
)
