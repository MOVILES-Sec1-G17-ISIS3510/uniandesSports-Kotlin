package com.uniandes.sport.models.warmup

// modelo de un ejercicio individual de una rutina de warm-up.
// los nombres de los campos coinciden con la estructura de firestore
// (warmup_routines/<doc>/exercises[*])
data class WarmupExercise(
    val name: String = "",
    val quantity: String = "",
    val description: String = ""
)

// modelo de una rutina completa tal como esta almacenada en firestore.
// cada documento de la coleccion warmup_routines representa una rutina,
// que contiene un arreglo de ejercicios
data class WarmupRoutine(
    val id: String = "",
    val title: String = "",
    val category: String = "",
    val intensity: String = "",
    val durationMinutes: Int = 0,
    val exercises: List<WarmupExercise> = emptyList()
)
