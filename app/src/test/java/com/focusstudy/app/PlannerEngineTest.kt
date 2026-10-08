package com.focusstudy.app

import com.focusstudy.app.core.database.entity.Exam
import com.focusstudy.app.core.database.entity.Topic
import com.focusstudy.app.feature.planner.PlannerEngine
import org.junit.Assert.*
import org.junit.Test
import java.util.*

class PlannerEngineTest {

    @Test
    fun reserveBufferDays_calculatesAppropriateBuffer() {
        assertEquals(1, PlannerEngine.reserveBufferDays(7))
        assertEquals(2, PlannerEngine.reserveBufferDays(14))
        assertEquals(3, PlannerEngine.reserveBufferDays(28))
        assertEquals(4, PlannerEngine.reserveBufferDays(42))
    }

    @Test
    fun generateMacroPhases_coversFullDurationWithBuffer() {
        val totalDays = 42
        val phases = PlannerEngine.generateMacroPhases(totalDays)

        assertEquals(6, phases.size)
        assertEquals("Phase 1: Foundation", phases[0].name)
        assertEquals("Phase 6: Final Buffer", phases[5].name)

        // Verify continuous day offsets without gaps
        for (i in 0 until phases.size - 1) {
            assertEquals(phases[i].endDayOffset, phases[i + 1].startDayOffset)
        }
        assertEquals(totalDays, phases.last().endDayOffset)
    }

    @Test
    fun rankTopics_prioritizesCriticalAndDifficultTopics() {
        val topics = listOf(
            Topic(id = "1", unitId = "u1", subjectId = "s1", name = "Basic Intro", importance = "normal", difficulty = "easy"),
            Topic(id = "2", unitId = "u1", subjectId = "s1", name = "Hard Normal", importance = "normal", difficulty = "hard"),
            Topic(id = "3", unitId = "u1", subjectId = "s1", name = "Critical Topic", importance = "critical", difficulty = "medium"),
            Topic(id = "4", unitId = "u1", subjectId = "s1", name = "High Hard", importance = "high", difficulty = "hard")
        )

        val ranked = PlannerEngine.rankTopics(topics)

        assertEquals("Critical Topic", ranked[0].name)
        assertEquals("High Hard", ranked[1].name)
        assertEquals("Hard Normal", ranked[2].name)
        assertEquals("Basic Intro", ranked[3].name)
    }

    @Test
    fun generateDeterministicPlan_placesSessionsAndDetectsOverload() {
        val now = System.currentTimeMillis()
        val exam = Exam(
            id = "exam_1",
            title = "Final Exam",
            examType = "university",
            examDateUtc = now + (30L * 24 * 60 * 60 * 1000L) // 30 days
        )

        val topics = listOf(
            Topic(id = "t1", unitId = "u1", subjectId = "s1", name = "Topic 1", estimatedEffortMinutes = 90, importance = "high"),
            Topic(id = "t2", unitId = "u1", subjectId = "s1", name = "Topic 2", estimatedEffortMinutes = 60, importance = "normal"),
            Topic(id = "t3", unitId = "u1", subjectId = "s1", name = "Topic 3", estimatedEffortMinutes = 45, importance = "low")
        )

        val result = PlannerEngine.generateDeterministicPlan(
            exam = exam,
            topics = topics,
            weekdayMinutes = 180, // 3 hours
            weekendMinutes = 300, // 5 hours
            sessionDurationMinutes = 45,
            startDate = Date(now)
        )

        assertNotNull(result.plan)
        assertTrue(result.days.isNotEmpty())
        assertTrue(result.sessions.isNotEmpty())
        assertFalse(result.isOverloaded) // 195 min needed vs thousands available
        assertTrue(result.requiredPaceMinutesPerDay > 0)
    }
}
