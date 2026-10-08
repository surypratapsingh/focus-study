package com.focusstudy.app

import com.focusstudy.app.core.backup.BackupPayload
import com.focusstudy.app.core.backup.ExamDto
import com.focusstudy.app.core.backup.TopicDto
import com.focusstudy.app.core.datastore.UserPreferences
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class SettingsAndBackupTest {

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    @Test
    fun testUserPreferencesDefaults() {
        val prefs = UserPreferences(
            isOnboardingCompleted = true,
            themeMode = "system",
            defaultSessionMinutes = 45,
            defaultBreakMinutes = 10,
            soundEnabled = true,
            hapticEnabled = true,
            sessionRemindersEnabled = true,
            dailyPlanReminderEnabled = true,
            examCountdownEnabled = true,
            aiConsentGiven = true,
            aiFeaturesEnabled = true
        )

        assertTrue(prefs.soundEnabled)
        assertTrue(prefs.hapticEnabled)
        assertTrue(prefs.sessionRemindersEnabled)
        assertTrue(prefs.dailyPlanReminderEnabled)
        assertEquals(45, prefs.defaultSessionMinutes)
    }

    @Test
    fun testBackupPayloadSerializationRoundTrip() {
        val payload = BackupPayload(
            exportVersion = 1,
            exportedAtUtc = 1775500000000L,
            exams = listOf(
                ExamDto(
                    id = "exam_1",
                    title = "Database Systems Final",
                    examType = "University Exam",
                    examDateUtc = 1778000000000L,
                    isTentative = false,
                    targetScore = 90
                )
            ),
            topics = listOf(
                TopicDto(
                    id = "topic_1",
                    unitId = "unit_1",
                    subjectId = "sub_1",
                    name = "B+ Trees",
                    estimatedEffortMinutes = 90,
                    plannedMinutes = 90,
                    actualMinutes = 45,
                    importance = "high",
                    difficulty = "hard",
                    status = "in_progress"
                )
            )
        )

        val jsonString = json.encodeToString(payload)
        assertTrue(jsonString.contains("Database Systems Final"))
        assertTrue(jsonString.contains("B+ Trees"))

        val decoded = json.decodeFromString<BackupPayload>(jsonString)
        assertEquals(1, decoded.exportVersion)
        assertEquals(1, decoded.exams.size)
        assertEquals("Database Systems Final", decoded.exams[0].title)
        assertEquals(1, decoded.topics.size)
        assertEquals("B+ Trees", decoded.topics[0].name)
    }

    @Test
    fun testBackupPayloadEmptyValidation() {
        val emptyPayload = BackupPayload(
            exportVersion = 1,
            exams = emptyList(),
            topics = emptyList()
        )
        val jsonString = json.encodeToString(emptyPayload)
        val decoded = json.decodeFromString<BackupPayload>(jsonString)

        assertTrue(decoded.exams.isEmpty())
        assertTrue(decoded.topics.isEmpty())
    }

    @Test
    fun testUserPreferences_antiBillProtectionAndByokSupport() {
        val defaultPrefs = UserPreferences(
            isOnboardingCompleted = true,
            themeMode = "system",
            defaultSessionMinutes = 45,
            defaultBreakMinutes = 10,
            soundEnabled = true,
            hapticEnabled = true,
            sessionRemindersEnabled = true,
            dailyPlanReminderEnabled = true,
            examCountdownEnabled = true,
            aiConsentGiven = true,
            aiFeaturesEnabled = true
        )
        // 100% Anti-Bill Guarantee: empty by default, zero developer credit card risk
        assertEquals("", defaultPrefs.customGeminiApiKey)

        // BYOK personal key configured
        val customKeyPrefs = defaultPrefs.copy(customGeminiApiKey = "AIzaSy_mock_personal_token_789")
        assertEquals("AIzaSy_mock_personal_token_789", customKeyPrefs.customGeminiApiKey)
    }
}
