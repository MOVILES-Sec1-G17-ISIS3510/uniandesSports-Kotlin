package com.uniandes.sport.models

import androidx.compose.ui.graphics.vector.ImageVector

enum class InsightType {
    ENVIRONMENTAL,
    BEHAVIORAL,
    WELCOME
}

data class CoachInsight(
    val message: String,
    val type: InsightType,
    val icon: ImageVector? = null
)
