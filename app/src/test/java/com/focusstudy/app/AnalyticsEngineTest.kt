package com.focusstudy.app

import com.focusstudy.app.core.database.entity.StudyAttempt
import com.focusstudy.app.core.database.entity.Subject
import com.focusstudy.app.core.database.entity.Topic
import com.focusstudy.app.feature.progress.AnalyticsEngine
import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar

class AnalyticsEngineTest {

    @Test
    fun calculateSyllabusCoverage_returnsExactPercentage() {
        val topics = listOf(
            Topic(id = "1", unitId = "u1", subjectId = "s1", name = "T1", status = "completed"),
            Topic(id = "2", unitId = "u1", subjectId = "s1", name = "T2", status = "completed"),
            Topic(id = "3", unitId = "u1", subjectId = "s1", name = "T3", status = "not_started"),
            Topic(id = "4", unitId = "u1", subjectId = "s1", name = "T4", status = "not_started")
        )

        val coverage = AnalyticsEngine.calculateSyllabusCoverage(topics)
        assertEquals(50.0f, coverage, 0.001f)
    }

    @Test
    fun calculateSubjectProgress_groupsCorrectly() {
        val subjects = listOf(
            Subject(id = "s1", examId = "e1", name = "Math", colorHex = "#2563EB"),
            Subject(id = "s2", examId = "e1", name = "Physics", colorHex = "#0F766E")
        )

        val topics = listOf(
            Topic(id = "t1", unitId = "u1", subjectId = "s1", name = "Calc", status = "completed", actualMinutes = 60),
            Topic(id = "t2", unitId = "u1", subjectId = "s1", name = "Alg", status = "not_started", actualMinutes = 0),
            Topic(id = "t3", unitId = "u2", subjectId = "s2", name = "Optics", status = "completed", actualMinutes = 45)
        )

        val result = AnalyticsEngine.calculateSubjectProgress(subjects, topics)
        assertEquals(2, result.size)

        val math = result.find { it.subjectId == "s1" }!!
        assertEquals(2, math.totalTopics)
        assertEquals(1, math.completedTopics)
        assertEquals(50.0f, math.coveragePercent, 0.001f)
        assertEquals(60, math.actualMinutes)

        val physics = result.find { it.subjectId == "s2" }!!
        assertEquals(1, physics.totalTopics)
        assertEquals(1, physics.completedTopics)
        assertEquals(100.0f, physics.coveragePercent, 0.001f)
        assertEquals(45, physics.actualMinutes)
    }

    @Test
    fun calculateStreaks_computesConsecutiveDays() {
        val cal = Calendar.getInstance()
        val today = cal.timeInMillis

        cal.add(Calendar.DAY_OF_YEAR, -1)
        val yesterday = cal.timeInMillis

        cal.add(Calendar.DAY_OF_YEAR, -1)
        val twoDaysAgo = cal.timeInMillis

        val attempts = listOf(
            StudyAttempt(id = "1", topicId = "t1", startedAtUtc = today, endedAtUtc = today + 1800000, durationSeconds = 1800),
            StudyAttempt(id = "2", topicId = "t1", startedAtUtc = yesterday, endedAtUtc = yesterday + 1800000, durationSeconds = 1800),
            StudyAttempt(id = "3", topicId = "t2", startedAtUtc = twoDaysAgo, endedAtUtc = twoDaysAgo + 1800000, durationSeconds = 1800)
        )

        val streakData = AnalyticsEngine.calculateStreaks(attempts)
        assertEquals(3, streakData.currentStreakDays)
        assertTrue(streakData.longestStreakDays >= 3)
        assertEquals(3, streakData.totalActiveDays)
    }

    @Test
    fun detectPeakStudyWindow_requiresThresholdBeforeConfidence() {
        // Less than 3 sessions
        val sparseAttempts = listOf(
            StudyAttempt(id = "1", topicId = "t1", startedAtUtc = System.currentTimeMillis(), endedAtUtc = System.currentTimeMillis() + 1800000, durationSeconds = 1800)
        )
        val sparseResult = AnalyticsEngine.detectPeakStudyWindow(sparseAttempts)
        assertEquals("None", sparseResult.confidenceLevel)

        // 3+ morning sessions
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 7) // 7:00 AM
        val t1 = cal.timeInMillis

        cal.add(Calendar.DAY_OF_YEAR, -1)
        val t2 = cal.timeInMillis

        cal.add(Calendar.DAY_OF_YEAR, -1)
        val t3 = cal.timeInMillis

        val morningAttempts = listOf(
            StudyAttempt(id = "1", topicId = "t1", startedAtUtc = t1, endedAtUtc = t1 + 2700000, durationSeconds = 2700),
            StudyAttempt(id = "2", topicId = "t1", startedAtUtc = t2, endedAtUtc = t2 + 2700000, durationSeconds = 2700),
            StudyAttempt(id = "3", topicId = "t1", startedAtUtc = t3, endedAtUtc = t3 + 2700000, durationSeconds = 2700)
        )

        val morningResult = AnalyticsEngine.detectPeakStudyWindow(morningAttempts)
        assertEquals("06:00 – 09:00 AM", morningResult.windowName)
        assertEquals("Early Evidence", morningResult.confidenceLevel)
    }

    @Test
    fun generateHeatmapGrid_returns35Slots() {
        val attempts = emptyList<StudyAttempt>()
        val grid = AnalyticsEngine.generateHeatmapGrid(attempts)

        assertEquals(35, grid.size) // 7 days * 5 slots
        assertTrue(grid.all { it.intensity == 0 })
    }
}
