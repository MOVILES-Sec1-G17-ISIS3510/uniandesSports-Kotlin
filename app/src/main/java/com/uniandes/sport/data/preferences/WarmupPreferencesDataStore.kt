package com.uniandes.sport.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// preferencias persistentes para warm-up routines.
// guarda la ultima combinacion (categoria+intensidad) seleccionada por el usuario,
// para que al volver a la pantalla aparezca con sus filtros previos
// en lugar de los defaults. resuelve el antipatron "missing state persistence"
data class WarmupUiPreferences(
    val lastCategory: String = "",
    val lastIntensity: String = ""
)

class WarmupPreferencesRepository private constructor(
    private val context: Context
) {
    private val dataStore = PreferenceDataStoreFactory.create(
        produceFile = { context.preferencesDataStoreFile(DATASTORE_NAME) }
    )

    val preferencesFlow: Flow<WarmupUiPreferences> = dataStore.data.map { prefs ->
        WarmupUiPreferences(
            lastCategory = prefs[KEY_LAST_CATEGORY] ?: "",
            lastIntensity = prefs[KEY_LAST_INTENSITY] ?: ""
        )
    }

    suspend fun setLastCategory(category: String) {
        dataStore.edit { prefs -> prefs[KEY_LAST_CATEGORY] = category }
    }

    suspend fun setLastIntensity(intensity: String) {
        dataStore.edit { prefs -> prefs[KEY_LAST_INTENSITY] = intensity }
    }

    // helper para guardar ambas a la vez en una sola escritura atomica
    suspend fun setLastSelection(category: String, intensity: String) {
        dataStore.edit { prefs ->
            prefs[KEY_LAST_CATEGORY] = category
            prefs[KEY_LAST_INTENSITY] = intensity
        }
    }

    companion object {
        private const val DATASTORE_NAME = "warmup_preferences"
        private val KEY_LAST_CATEGORY = stringPreferencesKey("last_category")
        private val KEY_LAST_INTENSITY = stringPreferencesKey("last_intensity")

        @Volatile
        private var INSTANCE: WarmupPreferencesRepository? = null

        fun getInstance(context: Context): WarmupPreferencesRepository {
            return INSTANCE ?: synchronized(this) {
                val instance = WarmupPreferencesRepository(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }
}
