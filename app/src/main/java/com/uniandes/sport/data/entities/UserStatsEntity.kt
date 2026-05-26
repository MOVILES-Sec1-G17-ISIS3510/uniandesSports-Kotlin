package com.uniandes.sport.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * UserStatsEntity — Snapshot de estadísticas del usuario.
 * 
 * DOCUMENTACIÓN:
 * - Patrón: Room Entity para almacenamiento local (caché persistente)
 * - syncStatus: Estado de sincronización (IDLE, SYNCING, ERROR)
 * - level: Calculado como (points / 100)
 * - lastSyncAt: Timestamp para validar si caché expiró (TTL = 15 min)
 * 
 * FEATURE: Caching (Requisito c)
 * Este entity se usa como caché persistente entre Room y LRUCache en memoria.
 * Si LRU expira o se limpia, Room sirve como fallback.
 * 
 * @author Juan Felipe Hernández
 * @since 26-may-2026
 */
@Entity(tableName = "user_stats", primaryKeys = ["userId"])
data class UserStatsEntity(
    val userId: String = "",
    val totalKm: Float = 0f,
    val totalEvents: Int = 0,
    val totalPosts: Int = 0,
    val totalMessages: Int = 0,
    val totalBadgesUnlocked: Int = 0,
    val level: Int = 1, // puntos / 100
    val points: Int = 0,
    val streakDays: Int = 0,
    val lastSyncAt: Long = 0L, // para TTL validation
    val syncStatus: String = "IDLE" // SYNCING, IDLE, ERROR
)
