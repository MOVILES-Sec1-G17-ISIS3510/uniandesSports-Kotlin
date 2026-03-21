package com.uniandes.sport.models

import com.google.firebase.Timestamp
import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.IgnoreExtraProperties

// estratejia de progreso para ver ke tan pro es el usuario
interface ProgressStrategy {
    fun getPercent(progress: Double): Int
}

class DefaultProgressStrategy : ProgressStrategy {
    override fun getPercent(progress: Double): Int {
        return (progress * 100).toInt()
    }
}

// modelo identiko al de firestore con los timestamps y todo
@IgnoreExtraProperties
data class Reto(
    @get:Exclude var id: String = "", // esto es para ke no se guarde el id bacio en la db
    var title: String = "",
    var sport: String = "",
    var difficulty: String = "",
    var type: String = "",
    var goalLabel: String = "", // ej: "100 km"
    var createdBy: String = "",
    var status: String = "active",
    var participants: List<String> = emptyList(),
    var participantsCount: Long = 0, // asemos k sea long para k sea igual al de firestore
    var progress: Double = 0.0, // el global o promedio
    var progressByUser: Map<String, Double> = emptyMap(),
    
    // fechas de verdad de firebase
    var createdAt: Timestamp? = null,
    var startDate: Timestamp? = null,
    var endDate: Timestamp? = null
)

// dolsito para el progrezo individual
data class UserChallenge(
    val userId: String = "",
    val progress: Double = 0.0,
    val isCompleted: Boolean = false
)
