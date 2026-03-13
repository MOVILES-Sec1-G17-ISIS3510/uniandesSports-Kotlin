package com.uniandes.sport.models

data class Profesor(
    var id: String = "",
    var nombre: String = "",
    var deporte: String = "",
    var rating: Double = 0.0,
    var totalReviews: Int = 0,
    var precio: String = "",
    var experiencia: String = "",
    var whatsapp: String = "",
    var disponibilidad: String = "",
    var especialidad: String = "",
    var verified: Boolean = false,
    var sessionsDelivered: Int = 0,
    var tournamentWins: Int = 0,
    var rankInSport: Int = 0,
    var totalCoachesInSport: Int = 0
)

data class Review(
    var id: String = "",
    var estudiante: String = "",
    var rating: Int = 0,
    var comentario: String = "",
    var fecha: String = ""
)
