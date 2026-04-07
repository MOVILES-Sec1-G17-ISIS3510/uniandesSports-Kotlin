package com.uniandes.sport.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.unit.sp
import com.uniandes.sport.R

val provider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs
)

val ArchivoFont = GoogleFont("Archivo")
val InterFont = GoogleFont("Inter")
val BebasNeueFont = GoogleFont("Bebas Neue")

val ArchivoFamily = FontFamily(
    Font(googleFont = ArchivoFont, fontProvider = provider),
    Font(googleFont = ArchivoFont, fontProvider = provider, weight = FontWeight.Bold),
    Font(googleFont = ArchivoFont, fontProvider = provider, weight = FontWeight.SemiBold)
)

val InterFamily = FontFamily(
    Font(googleFont = InterFont, fontProvider = provider),
    Font(googleFont = InterFont, fontProvider = provider, weight = FontWeight.Medium),
    Font(googleFont = InterFont, fontProvider = provider, weight = FontWeight.SemiBold)
)

val BebasNeueFamily = FontFamily(
    Font(googleFont = BebasNeueFont, fontProvider = provider)
)

val Typography = Typography(
    // Display / Headings - Archivo
    displayLarge = TextStyle(
        fontFamily = ArchivoFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = 0.sp
    ),
    displayMedium = TextStyle( // Used for Stats / Rankings (Bebas Neue)
        fontFamily = BebasNeueFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 44.sp,
        lineHeight = 44.sp,
        letterSpacing = 0.sp
    ),
    headlineLarge = TextStyle(
        fontFamily = ArchivoFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = 0.sp
    ),
    titleLarge = TextStyle(
        fontFamily = ArchivoFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    
    // Body / UI - Inter
    bodyLarge = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp
    ),
    labelMedium = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    ),
    labelSmall = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    )
)

// Helper for large data numbers
val DataNumbersStyle = TextStyle(
    fontFamily = BebasNeueFamily,
    fontWeight = FontWeight.Normal,
    fontSize = 32.sp,
    lineHeight = 32.sp,
    letterSpacing = 0.sp
)