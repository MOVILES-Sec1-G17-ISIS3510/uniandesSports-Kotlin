package com.uniandes.sport.ui.navigation

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Challenges : Screen("challenges")
    object Play : Screen("play")
    object Comunidades : Screen("social")
    object Profesores : Screen("profesores")
    object Perfil : Screen("perfil")
    object Torneos : Screen("torneos")
    object Clima : Screen("clima")
    object Strava : Screen("strava")
    object Historial : Screen("historial")
    object CoachDashboard : Screen("coach_dashboard/{profesorId}")
    object BookClass : Screen("book_class/{profesorId}")
    object CoachProfile : Screen("coach_profile/{profesorId}")
}
