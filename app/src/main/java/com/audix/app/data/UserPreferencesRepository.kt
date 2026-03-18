package com.audix.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

class UserPreferencesRepository(private val context: Context) {
    companion object {
        val EQ_INTENSITY = floatPreferencesKey("eq_intensity")
        val BASS_BOOST_INTENSITY = floatPreferencesKey("bass_boost_intensity")
    }

    val eqIntensityFlow: Flow<Float> = context.dataStore.data
        .map { preferences ->
            preferences[EQ_INTENSITY] ?: 1.0f // Default to 100%
        }

    val bassBoostFlow: Flow<Float> = context.dataStore.data
        .map { preferences ->
            preferences[BASS_BOOST_INTENSITY] ?: 0.0f // Default to 0%
        }

    suspend fun saveEqIntensity(intensity: Float) {
        context.dataStore.edit { preferences ->
            preferences[EQ_INTENSITY] = intensity
        }
    }

    suspend fun saveBassBoost(intensity: Float) {
        context.dataStore.edit { preferences ->
            preferences[BASS_BOOST_INTENSITY] = intensity
        }
    }
}
