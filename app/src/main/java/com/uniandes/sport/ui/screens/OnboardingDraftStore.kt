package com.uniandes.sport.ui.screens

import android.content.Context

private const val ONBOARDING_DRAFT_PREFS = "onboarding_draft_prefs"

private const val KEY_ENTRY_MODE = "entry_mode"
private const val KEY_FULL_NAME = "full_name"
private const val KEY_EMAIL = "email"
private const val KEY_PASSWORD = "password"
private const val KEY_CURRENT_STEP = "current_step"
private const val KEY_PROGRAM = "program"
private const val KEY_SEMESTER = "semester"
private const val KEY_MAIN_SPORT = "main_sport"

private const val MODE_SIGNUP = "signup"

internal fun hasOnboardingDraft(context: Context): Boolean {
    val prefs = context.getSharedPreferences(ONBOARDING_DRAFT_PREFS, Context.MODE_PRIVATE)
    return prefs.contains(KEY_ENTRY_MODE) || prefs.contains(KEY_EMAIL) || prefs.contains(KEY_PROGRAM)
}

internal fun saveSignupDraft(
    context: Context,
    fullName: String? = null,
    email: String? = null,
    password: String? = null
) {
    val editor = context.getSharedPreferences(ONBOARDING_DRAFT_PREFS, Context.MODE_PRIVATE).edit()
    editor.putString(KEY_ENTRY_MODE, MODE_SIGNUP)
    if (fullName != null) editor.putString(KEY_FULL_NAME, fullName)
    if (email != null) editor.putString(KEY_EMAIL, email)
    if (password != null) editor.putString(KEY_PASSWORD, password)
    editor.apply()
}

internal fun saveOnboardingProgress(
    context: Context,
    currentStep: Int,
    program: String,
    semester: String,
    mainSport: String
) {
    context.getSharedPreferences(ONBOARDING_DRAFT_PREFS, Context.MODE_PRIVATE)
        .edit()
        .putString(KEY_ENTRY_MODE, MODE_SIGNUP)
        .putInt(KEY_CURRENT_STEP, currentStep)
        .putString(KEY_PROGRAM, program)
        .putString(KEY_SEMESTER, semester)
        .putString(KEY_MAIN_SPORT, mainSport)
        .apply()
}

internal fun restoreSignupDraft(
    context: Context,
    applyFields: (fullName: String, email: String, password: String) -> Unit
) {
    val prefs = context.getSharedPreferences(ONBOARDING_DRAFT_PREFS, Context.MODE_PRIVATE)
    val fullName = prefs.getString(KEY_FULL_NAME, "").orEmpty()
    val email = prefs.getString(KEY_EMAIL, "").orEmpty()
    val password = prefs.getString(KEY_PASSWORD, "").orEmpty()

    if (fullName.isNotBlank() || email.isNotBlank() || password.isNotBlank()) {
        applyFields(fullName, email, password)
    }
}

internal fun restoreOnboardingProgress(
    context: Context,
    applyState: (currentStep: Int, program: String, semester: String, mainSport: String) -> Unit
) {
    val prefs = context.getSharedPreferences(ONBOARDING_DRAFT_PREFS, Context.MODE_PRIVATE)
    val currentStep = prefs.getInt(KEY_CURRENT_STEP, 1)
    val program = prefs.getString(KEY_PROGRAM, "").orEmpty()
    val semester = prefs.getString(KEY_SEMESTER, "").orEmpty()
    val mainSport = prefs.getString(KEY_MAIN_SPORT, "").orEmpty()

    applyState(currentStep, program, semester, mainSport)
}

internal fun clearOnboardingDraft(context: Context) {
    context.getSharedPreferences(ONBOARDING_DRAFT_PREFS, Context.MODE_PRIVATE)
        .edit()
        .clear()
        .apply()
}
