package com.uniandes.sport.models

import com.google.firebase.Timestamp
import java.util.Date

// fabrik para crear los retos con todas las de la ley
abstract class RetoCreator {
    abstract fun createReto(
        title: String,
        sport: String,
        difficulty: String,
        goalLabel: String,
        createdBy: String,
        type: String,
        start: Date? = null,
        end: Date? = null
    ): Reto
}

class BaseRetoCreator : RetoCreator() {
    override fun createReto(
        title: String,
        sport: String,
        difficulty: String,
        goalLabel: String,
        createdBy: String,
        type: String,
        start: Date?,
        end: Date?
    ): Reto {
        return Reto(
            title = title,
            sport = sport,
            difficulty = difficulty,
            type = type,
            goalLabel = goalLabel,
            createdBy = createdBy,
            status = "active",
            createdAt = Timestamp.now(),
            startDate = start?.let { Timestamp(it) },
            endDate = end?.let { Timestamp(it) },
            participants = listOf(createdBy),
            participantsCount = 1L, // long de una
            progressByUser = mapOf(createdBy to 0.0)
        )
    }
}

// para keep simple solo usamos uno y pasamos el tipo
class IndividualRetoCreator : RetoCreator() {
    override fun createReto(title: String, sport: String, difficulty: String, goalLabel: String, createdBy: String, type: String, start: Date?, end: Date?): Reto {
        return BaseRetoCreator().createReto(title, sport, difficulty, goalLabel, createdBy, "individual", start, end)
    }
}

class TeamRetoCreator : RetoCreator() {
    override fun createReto(title: String, sport: String, difficulty: String, goalLabel: String, createdBy: String, type: String, start: Date?, end: Date?): Reto {
        return BaseRetoCreator().createReto(title, sport, difficulty, goalLabel, createdBy, "team", start, end)
    }
}
