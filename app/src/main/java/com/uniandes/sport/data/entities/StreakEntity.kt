package com.uniandes.sport.data.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * StreakEntity — Seguimiento de rachas (ej: días seguidos escribiendo en comunidad).
 * 
 * DOCUMENTACIÓN:
 * - Patrón: Room Entity para almacenar estado de rachas
 * - streakType: SOCIAL_DAYS, EVENT_WEEKS, RUN_CONSECUTIVE
 * - currentCount: Racha actual
 * - maxCount: Racha máxima histórica
 * - lastDate: Última fecha con actividad en esta racha
 * 
 * FEATURE: Local Storage (Requisito b)
 * Se usa para calcular badges como "Social Shark" (3 días seguidos escribiendo).
 * Evita recalcular streaks desde ActivityLog cada vez.
 * 
 * EJEMPLO:
 * - Usuario escribe en comunidad lunes, martes, miércoles
 * - StreakEntity: streakId=SOCIAL_DAYS_user_123, currentCount=3, lastDate=wed_timestamp
 * - Jueves sin escribir: resetea currentCount a 0
 * - Viernes escribe: reinicia streak con currentCount=1
 * 
 * @author Juan Felipe Hernández
 * @since 26-may-2026
 */
@Entity(tableName = "streaks", indices = [Index("userId")])
data class StreakEntity(
    @PrimaryKey val streakId: String = "", // ej: "SOCIAL_DAYS_user_123"
    val userId: String = "",
    val streakType: String = "", // SOCIAL_DAYS, EVENT_WEEKS, RUN_CONSECUTIVE
    val currentCount: Int = 0,
    val maxCount: Int = 0,
    val lastDate: Long = 0L, // última fecha con actividad
    val startDate: Long = 0L // cuándo comenzó la racha actual
)
