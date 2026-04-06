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

class ProfesorBuilder(private val id: String) {
    private var nombre: String = ""
    private var deporte: String = ""
    private var precio: String = ""
    private var experiencia: String = ""
    private var whatsapp: String = ""
    private var especialidad: String = ""

    fun setBasicInfo(nombre: String, deporte: String) = apply {
        this.nombre = nombre
        this.deporte = deporte
    }

    fun setProfessionalProfile(precio: String, experiencia: String, especialidad: String) = apply {
        this.precio = precio
        this.experiencia = experiencia
        this.especialidad = especialidad
    }

    fun setContactInfo(whatsapp: String) = apply {
        this.whatsapp = whatsapp
    }

    /**
     * Constructs the Coach explicitly applying the business rules for a newly registered, unverified coach.
     * Throws an exception if required fields are missing.
     */
    fun buildNewUnverifiedCoach(): Profesor {
        require(nombre.isNotBlank()) { "El nombre del coach es obligatorio" }
        require(deporte.isNotBlank()) { "El deporte es obligatorio" }
        require(precio.isNotBlank()) { "El precio es obligatorio" }
        require(whatsapp.isNotBlank()) { "El teléfono de contacto (WhatsApp) es obligatorio" }

        return Profesor(
            id = this.id,
            nombre = this.nombre,
            deporte = this.deporte,
            precio = this.precio,
            experiencia = this.experiencia,
            whatsapp = this.whatsapp,
            especialidad = this.especialidad,
            disponibilidad = "To be agreed",
            rating = 5.0, // starts with perfect score technically
            totalReviews = 0,
            verified = false, // Must be verified by admin
            sessionsDelivered = 0,
            tournamentWins = 0,
            rankInSport = 0,
            totalCoachesInSport = 0
        )
    }
}
