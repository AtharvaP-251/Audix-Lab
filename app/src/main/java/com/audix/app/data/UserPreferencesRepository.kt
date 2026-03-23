package com.audix.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

class UserPreferencesRepository(private val context: Context) {
    companion object {
        val EQ_INTENSITY = floatPreferencesKey("eq_intensity")
        val CUSTOM_TUNING_ENABLED = booleanPreferencesKey("custom_tuning_enabled")
        val CUSTOM_BASS = floatPreferencesKey("custom_bass")
        val CUSTOM_VOCALS = floatPreferencesKey("custom_vocals")
        val CUSTOM_TREBLE = floatPreferencesKey("custom_treble")
        val AUTO_EQ_ENABLED = booleanPreferencesKey("auto_eq_enabled")
        val ONBOARDING_SHOWN = booleanPreferencesKey("onboarding_shown")
    }

    val eqIntensityFlow: Flow<Float> = context.dataStore.data
        .map { preferences -> preferences[EQ_INTENSITY] ?: 1.0f }

    val customTuningEnabledFlow: Flow<Boolean> = context.dataStore.data
        .map { preferences -> preferences[CUSTOM_TUNING_ENABLED] ?: false }

    val customBassFlow: Flow<Float> = context.dataStore.data
        .map { preferences -> preferences[CUSTOM_BASS] ?: 0.0f }

    val customVocalsFlow: Flow<Float> = context.dataStore.data
        .map { preferences -> preferences[CUSTOM_VOCALS] ?: 0.0f }

    val customTrebleFlow: Flow<Float> = context.dataStore.data
        .map { preferences -> preferences[CUSTOM_TREBLE] ?: 0.0f }

    val autoEqEnabledFlow: Flow<Boolean> = context.dataStore.data
        .map { preferences -> preferences[AUTO_EQ_ENABLED] ?: false }

    /** True once the user has seen the first-launch onboarding screen. */
    val onboardingShownFlow: Flow<Boolean> = context.dataStore.data
        .map { preferences -> preferences[ONBOARDING_SHOWN] ?: false }

    suspend fun saveEqIntensity(intensity: Float) {
        context.dataStore.edit { preferences -> preferences[EQ_INTENSITY] = intensity }
    }

    suspend fun saveCustomTuningEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences -> preferences[CUSTOM_TUNING_ENABLED] = enabled }
    }

    suspend fun saveCustomBass(value: Float) {
        context.dataStore.edit { preferences -> preferences[CUSTOM_BASS] = value }
    }

    suspend fun saveCustomVocals(value: Float) {
        context.dataStore.edit { preferences -> preferences[CUSTOM_VOCALS] = value }
    }

    suspend fun saveCustomTreble(value: Float) {
        context.dataStore.edit { preferences -> preferences[CUSTOM_TREBLE] = value }
    }

    suspend fun saveAutoEqEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences -> preferences[AUTO_EQ_ENABLED] = enabled }
    }

    suspend fun saveOnboardingShown(shown: Boolean) {
        context.dataStore.edit { preferences -> preferences[ONBOARDING_SHOWN] = shown }
    }
}
