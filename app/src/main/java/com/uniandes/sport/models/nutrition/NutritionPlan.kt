package com.uniandes.sport.models.nutrition

// plan de nutricion generado por ia. el contenido viene en markdown
// y se renderiza con un parser ligero en la pantalla.
// los campos de perfil se guardan junto al plan para auditoria
// (saber con que entradas se genero el plan)
data class NutritionPlan(
    val planContent: String = "",
    val createdAtMillis: Long = 0L,
    val age: Int = 0,
    val weightKg: Double = 0.0,
    val heightCm: Double = 0.0,
    val goal: String = "",
    val dietaryRestrictions: String = ""
)
