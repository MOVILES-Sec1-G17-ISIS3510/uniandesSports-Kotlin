package com.uniandes.sport.ui.navigation

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Challenges : Screen("challenges")
    object Play : Screen("play")
    object Comunidades : Screen("social")
    object Profesores : Screen("coaches")
    object Perfil : Screen("profile")
    object Torneos : Screen("tournaments")
    object Clima : Screen("weather")
    object Strava : Screen("strava")
    object Historial : Screen("history")
    object LiveRun : Screen("live_run")
    object CoachDashboard : Screen("coach_dashboard/{profesorId}")
    object BookClass : Screen("book_class/{profesorId}")
    object CoachProfile : Screen("coach_profile/{profesorId}")
}
