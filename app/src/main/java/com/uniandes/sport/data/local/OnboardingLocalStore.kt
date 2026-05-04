package com.uniandes.sport.data.local

import android.content.Context

object OnboardingLocalStore {
    private const val PREFS = "onboarding_prefs"
    private const val KEY_PENDING = "pending"
    private const val KEY_FULLNAME = "fullname"
    private const val KEY_EMAIL = "email"
    private const val KEY_PASSWORD = "password"
    private const val KEY_PROGRAM = "program"
    private const val KEY_SEMESTER = "semester"
    private const val KEY_MAINSPORT = "mainsport"

    fun savePending(
        context: Context,
        fullName: String,
        email: String,
        password: String,
        program: String,
        semester: String,
        mainSport: String
    ) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean(KEY_PENDING, true)
            .putString(KEY_FULLNAME, fullName)
            .putString(KEY_EMAIL, email)
            .putString(KEY_PASSWORD, password)
            .putString(KEY_PROGRAM, program)
            .putString(KEY_SEMESTER, semester)
            .putString(KEY_MAINSPORT, mainSport)
            .apply()
    }

    fun hasPending(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_PENDING, false)
    }

    fun loadPending(context: Context): PendingOnboarding? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_PENDING, false)) return null

        return PendingOnboarding(
            fullName = prefs.getString(KEY_FULLNAME, "") ?: "",
            email = prefs.getString(KEY_EMAIL, "") ?: "",
            password = prefs.getString(KEY_PASSWORD, "") ?: "",
            program = prefs.getString(KEY_PROGRAM, "") ?: "",
            semester = prefs.getString(KEY_SEMESTER, "1") ?: "1",
            mainSport = prefs.getString(KEY_MAINSPORT, "") ?: ""
        )
    }

    fun clear(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
    }

    data class PendingOnboarding(
        val fullName: String,
        val email: String,
        val password: String,
        val program: String,
        val semester: String,
        val mainSport: String
    )
}
