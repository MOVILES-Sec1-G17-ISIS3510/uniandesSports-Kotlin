package com.uniandes.sport.data.local

import android.content.Context
import com.uniandes.sport.models.nutrition.NutritionPlan
import org.json.JSONObject
import java.io.File

// almacenamiento single-entry para el plan de nutricion del usuario.
// equivalente android del hive box que usa flutter (always box.put(0, plan)).
//
// archivo: context.filesdir/nutrition/nutrition_plan.json — siempre se sobrescribe.
// permite que la pantalla muestre el ultimo plan generado incluso sin internet
// (vista protegida offline), y que el plan persista entre reinicios del proceso
object NutritionPlanFileStorage {

    private const val FILE_NAME = "nutrition_plan.json"

    private fun baseDir(context: Context): File {
        val dir = File(context.filesDir, "nutrition")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun planFile(context: Context): File = File(baseDir(context), FILE_NAME)

    fun savePlan(context: Context, plan: NutritionPlan) {
        val payload = JSONObject().apply {
            put("planContent", plan.planContent)
            put("createdAtMillis", plan.createdAtMillis)
            put("age", plan.age)
            put("weightKg", plan.weightKg)
            put("heightCm", plan.heightCm)
            put("goal", plan.goal)
            put("dietaryRestrictions", plan.dietaryRestrictions)
        }
        planFile(context).writeText(payload.toString())
    }

    fun loadPlan(context: Context): NutritionPlan? {
        val file = planFile(context)
        if (!file.exists()) return null
        return try {
            val obj = JSONObject(file.readText())
            NutritionPlan(
                planContent = obj.optString("planContent", ""),
                createdAtMillis = obj.optLong("createdAtMillis", 0L),
                age = obj.optInt("age", 0),
                weightKg = obj.optDouble("weightKg", 0.0),
                heightCm = obj.optDouble("heightCm", 0.0),
                goal = obj.optString("goal", ""),
                dietaryRestrictions = obj.optString("dietaryRestrictions", "")
            )
        } catch (_: Exception) {
            null
        }
    }

    fun deletePlan(context: Context): Boolean {
        val file = planFile(context)
        return if (file.exists()) file.delete() else true
    }
}
