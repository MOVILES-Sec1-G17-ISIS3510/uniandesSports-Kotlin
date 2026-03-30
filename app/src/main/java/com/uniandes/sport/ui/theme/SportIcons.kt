package com.uniandes.sport.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val CrownIcon: ImageVector
    get() = ImageVector.Builder(
        name = "Crown",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(fill = SolidColor(Color(0xFFFFB300))) {
            moveTo(5f, 16f)
            lineTo(3f, 5f)
            lineTo(8.5f, 10f)
            lineTo(12f, 2f)
            lineTo(15.5f, 10f)
            lineTo(21f, 5f)
            lineTo(19f, 16f)
            close()
            moveTo(5f, 18f)
            lineTo(19f, 18f)
            lineTo(19f, 20f)
            lineTo(5f, 20f)
            close()
        }
    }.build()
