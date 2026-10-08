package com.focusstudy.app.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "focus_study_preferences")

data class UserPreferences(
    val isOnboardingCompleted: Boolean,
    val themeMode: String, // system, light, dark
    val defaultSessionMinutes: Int,
    val defaultBreakMinutes: Int,
    val soundEnabled: Boolean,
    val hapticEnabled: Boolean,
    val sessionRemindersEnabled: Boolean,
    val dailyPlanReminderEnabled: Boolean,
    val examCountdownEnabled: Boolean,
    val aiConsentGiven: Boolean,
    val aiFeaturesEnabled: Boolean,
    val preferredLearningTechnique: String = "POMODORO_CLASSIC",
    val burnoutGuardEnabled: Boolean = true,
    val studyBuddyName: String = "",
    val studyBuddyXp: Int = 0,
    val studyBuddyStreak: Int = 0,
    val studyBuddyMinutes: Int = 0,
    val customGeminiApiKey: String = ""
)

class UserPreferencesManager(private val context: Context) {

    private object PreferencesKeys {
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DEFAULT_SESSION_MINUTES = intPreferencesKey("default_session_minutes")
        val DEFAULT_BREAK_MINUTES = intPreferencesKey("default_break_minutes")
        val SOUND_ENABLED = booleanPreferencesKey("sound_enabled")
        val HAPTIC_ENABLED = booleanPreferencesKey("haptic_enabled")
        val NOTIFICATION_SESSION = booleanPreferencesKey("notif_session_reminders")
        val NOTIFICATION_DAILY = booleanPreferencesKey("notif_daily_plan")
        val NOTIFICATION_EXAM = booleanPreferencesKey("notif_exam_countdown")
        val AI_CONSENT = booleanPreferencesKey("ai_consent_given")
        val AI_FEATURES = booleanPreferencesKey("ai_features_enabled")
        val PREFERRED_LEARNING_TECHNIQUE = stringPreferencesKey("preferred_learning_technique")
        val BURNOUT_GUARD_ENABLED = booleanPreferencesKey("burnout_guard_enabled")
        val STUDY_BUDDY_NAME = stringPreferencesKey("study_buddy_name")
        val STUDY_BUDDY_XP = intPreferencesKey("study_buddy_xp")
        val STUDY_BUDDY_STREAK = intPreferencesKey("study_buddy_streak")
        val STUDY_BUDDY_MINUTES = intPreferencesKey("study_buddy_minutes")
        val CUSTOM_GEMINI_API_KEY = stringPreferencesKey("custom_gemini_api_key")
    }

    val userPreferencesFlow: Flow<UserPreferences> = context.dataStore.data.map { preferences ->
        UserPreferences(
            isOnboardingCompleted = preferences[PreferencesKeys.ONBOARDING_COMPLETED] ?: false,
            themeMode = preferences[PreferencesKeys.THEME_MODE] ?: "system",
            defaultSessionMinutes = preferences[PreferencesKeys.DEFAULT_SESSION_MINUTES] ?: 45,
            defaultBreakMinutes = preferences[PreferencesKeys.DEFAULT_BREAK_MINUTES] ?: 10,
            soundEnabled = preferences[PreferencesKeys.SOUND_ENABLED] ?: true,
            hapticEnabled = preferences[PreferencesKeys.HAPTIC_ENABLED] ?: true,
            sessionRemindersEnabled = preferences[PreferencesKeys.NOTIFICATION_SESSION] ?: true,
            dailyPlanReminderEnabled = preferences[PreferencesKeys.NOTIFICATION_DAILY] ?: true,
            examCountdownEnabled = preferences[PreferencesKeys.NOTIFICATION_EXAM] ?: true,
            aiConsentGiven = preferences[PreferencesKeys.AI_CONSENT] ?: false,
            aiFeaturesEnabled = preferences[PreferencesKeys.AI_FEATURES] ?: true,
            preferredLearningTechnique = preferences[PreferencesKeys.PREFERRED_LEARNING_TECHNIQUE] ?: "POMODORO_CLASSIC",
            burnoutGuardEnabled = preferences[PreferencesKeys.BURNOUT_GUARD_ENABLED] ?: true,
            studyBuddyName = preferences[PreferencesKeys.STUDY_BUDDY_NAME] ?: "",
            studyBuddyXp = preferences[PreferencesKeys.STUDY_BUDDY_XP] ?: 0,
            studyBuddyStreak = preferences[PreferencesKeys.STUDY_BUDDY_STREAK] ?: 0,
            studyBuddyMinutes = preferences[PreferencesKeys.STUDY_BUDDY_MINUTES] ?: 0,
            customGeminiApiKey = preferences[PreferencesKeys.CUSTOM_GEMINI_API_KEY] ?: ""
        )
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.ONBOARDING_COMPLETED] = completed }
    }

    suspend fun setThemeMode(mode: String) {
        context.dataStore.edit { it[PreferencesKeys.THEME_MODE] = mode }
    }

    suspend fun setSessionPreferences(sessionMinutes: Int, breakMinutes: Int) {
        context.dataStore.edit {
            it[PreferencesKeys.DEFAULT_SESSION_MINUTES] = sessionMinutes
            it[PreferencesKeys.DEFAULT_BREAK_MINUTES] = breakMinutes
        }
    }

    suspend fun setAiConsent(consentGiven: Boolean, featuresEnabled: Boolean) {
        context.dataStore.edit {
            it[PreferencesKeys.AI_CONSENT] = consentGiven
            it[PreferencesKeys.AI_FEATURES] = featuresEnabled
        }
    }

    suspend fun setSoundEnabled(enabled: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.SOUND_ENABLED] = enabled }
    }

    suspend fun setHapticEnabled(enabled: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.HAPTIC_ENABLED] = enabled }
    }

    suspend fun setSessionRemindersEnabled(enabled: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.NOTIFICATION_SESSION] = enabled }
    }

    suspend fun setDailyPlanReminderEnabled(enabled: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.NOTIFICATION_DAILY] = enabled }
    }

    suspend fun setExamCountdownEnabled(enabled: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.NOTIFICATION_EXAM] = enabled }
    }

    suspend fun setPersonalizationPreferences(technique: String, burnoutGuard: Boolean) {
        context.dataStore.edit {
            it[PreferencesKeys.PREFERRED_LEARNING_TECHNIQUE] = technique
            it[PreferencesKeys.BURNOUT_GUARD_ENABLED] = burnoutGuard
        }
    }

    suspend fun setStudyBuddy(name: String, xp: Int, streak: Int, minutes: Int) {
        context.dataStore.edit {
            it[PreferencesKeys.STUDY_BUDDY_NAME] = name
            it[PreferencesKeys.STUDY_BUDDY_XP] = xp
            it[PreferencesKeys.STUDY_BUDDY_STREAK] = streak
            it[PreferencesKeys.STUDY_BUDDY_MINUTES] = minutes
        }
    }

    suspend fun clearStudyBuddy() {
        context.dataStore.edit {
            it[PreferencesKeys.STUDY_BUDDY_NAME] = ""
            it[PreferencesKeys.STUDY_BUDDY_XP] = 0
            it[PreferencesKeys.STUDY_BUDDY_STREAK] = 0
            it[PreferencesKeys.STUDY_BUDDY_MINUTES] = 0
        }
    }

    suspend fun setCustomGeminiApiKey(key: String) {
        context.dataStore.edit {
            it[PreferencesKeys.CUSTOM_GEMINI_API_KEY] = key.trim()
        }
    }
}
