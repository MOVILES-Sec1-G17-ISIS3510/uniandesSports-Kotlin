package com.uniandes.sport.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class ProfesoresSortMode {
    RATING,
    NAME,
    EXPERIENCE
}

data class ProfesoresUiPreferences(
    val onlyVerified: Boolean = false,
    val sortMode: ProfesoresSortMode = ProfesoresSortMode.RATING,
    val showQuickContact: Boolean = true,
    val showRecentRequests: Boolean = true
)

class ProfesoresPreferencesRepository private constructor(
    private val context: Context
) {
    private val dataStore = PreferenceDataStoreFactory.create(
        produceFile = { context.preferencesDataStoreFile(DATASTORE_NAME) }
    )

    val preferencesFlow: Flow<ProfesoresUiPreferences> = dataStore.data.map { prefs ->
        ProfesoresUiPreferences(
            onlyVerified = prefs[KEY_ONLY_VERIFIED] ?: false,
            sortMode = prefs[KEY_SORT_MODE]?.let {
                runCatching { ProfesoresSortMode.valueOf(it) }.getOrDefault(ProfesoresSortMode.RATING)
            } ?: ProfesoresSortMode.RATING,
            showQuickContact = prefs[KEY_SHOW_QUICK_CONTACT] ?: true,
            showRecentRequests = prefs[KEY_SHOW_RECENT_REQUESTS] ?: true
        )
    }

    suspend fun setOnlyVerified(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[KEY_ONLY_VERIFIED] = enabled }
    }

    suspend fun setSortMode(mode: ProfesoresSortMode) {
        dataStore.edit { prefs -> prefs[KEY_SORT_MODE] = mode.name }
    }

    suspend fun setShowQuickContact(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[KEY_SHOW_QUICK_CONTACT] = enabled }
    }

    suspend fun setShowRecentRequests(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[KEY_SHOW_RECENT_REQUESTS] = enabled }
    }

    companion object {
        private const val DATASTORE_NAME = "profesores_preferences"
        private val KEY_ONLY_VERIFIED = booleanPreferencesKey("only_verified_coaches")
        private val KEY_SORT_MODE = stringPreferencesKey("sort_mode")
        private val KEY_SHOW_QUICK_CONTACT = booleanPreferencesKey("show_quick_contact")
        private val KEY_SHOW_RECENT_REQUESTS = booleanPreferencesKey("show_recent_requests")

        @Volatile
        private var INSTANCE: ProfesoresPreferencesRepository? = null

        fun getInstance(context: Context): ProfesoresPreferencesRepository {
            return INSTANCE ?: synchronized(this) {
                val instance = ProfesoresPreferencesRepository(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }
}
