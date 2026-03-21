package com.uniandes.sport.models

import com.google.firebase.Timestamp

data class Event(
    var id: String = "",
    var createdAt: Timestamp? = null,
    var createdBy: String = "",
    var description: String = "",
    var location: String = "",
    var maxParticipants: Long = 0,
    var metadata: Map<String, Any> = emptyMap(),
    var modality: String = "",
    var participants: List<String> = emptyList(),
    var scheduledAt: Timestamp? = null,
    var sport: String = "",
    var status: String = "",
    var title: String = "",
    var updatedAt: Timestamp? = null
)
