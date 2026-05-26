package com.uniandes.sport.data.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * BadgeEntity — Representación de un badge/logro en la base de datos local.
 * 
 * DOCUMENTACIÓN:
 * - Patrón: Room Entity para almacenamiento local persistente
 * - Rarity: COMMON, RARE, EPIC, LEGENDARY (para UI styling)
 * - isNew: Flag para notificaciones al usuario cuando desbloquea badge nuevo
 * - índices: userId para queries rápidas de "badges del usuario"
 * 
 * FEATURE: Local Storage (Requisito b)
 * Se guarda en Room para acceso rápido sin sincronizar con Firestore cada vez.
 * 
 * @author Juan Felipe Hernández
 * @since 26-may-2026
 */
@Entity(tableName = "badges", indices = [Index("userId")])
data class BadgeEntity(
    @PrimaryKey val badgeId: String = "",
    val userId: String = "",
    val name: String = "",
    val description: String = "",
    val icon: String = "", // ej: icon names
    val rarity: String = "", // COMMON, RARE, EPIC, LEGENDARY
    val unlockedAt: Long = 0L, // timestamp del desbloqueo
    val isNew: Boolean = false // para mostrar notificación
)
