package com.focusstudy.app

import com.focusstudy.app.core.database.entity.StudyAttempt
import com.focusstudy.app.core.database.entity.Topic
import com.focusstudy.app.feature.progress.*
import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar

class PersonalizationAndSocialTest {

    private fun createAttemptAtHour(hour: Int, durationMins: Int, daysAgo: Int = 0): StudyAttempt {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -daysAgo)
        cal.set(Calendar.HOUR_OF_DAY, hour)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)

        return StudyAttempt(
            id = "att_${System.nanoTime()}",
            topicId = "topic_1",
            sessionId = "sess_1",
            startedAtUtc = cal.timeInMillis,
            endedAtUtc = cal.timeInMillis + (durationMins * 60 * 1000L),
            durationSeconds = durationMins * 60
        )
    }

    @Test
    fun testDetectChronotype_EarlyBird() {
        val attempts = listOf(
            createAttemptAtHour(hour = 7, durationMins = 60),
            createAttemptAtHour(hour = 8, durationMins = 60),
            createAttemptAtHour(hour = 9, durationMins = 60),
            createAttemptAtHour(hour = 14, durationMins = 30)
        )

        val result = PersonalizationEngine.detectChronotype(attempts)
        assertEquals(Chronotype.EARLY_BIRD, result.chronotype)
        assertTrue(result.summary.contains("Morning", ignoreCase = true))
        assertTrue(result.morningMinutes > result.afternoonMinutes)
    }

    @Test
    fun testDetectChronotype_NightOwl() {
        val attempts = listOf(
            createAttemptAtHour(hour = 20, durationMins = 90),
            createAttemptAtHour(hour = 21, durationMins = 90),
            createAttemptAtHour(hour = 23, durationMins = 60),
            createAttemptAtHour(hour = 10, durationMins = 30)
        )

        val result = PersonalizationEngine.detectChronotype(attempts)
        assertEquals(Chronotype.NIGHT_OWL, result.chronotype)
        assertTrue(result.summary.contains("Late", ignoreCase = true) || result.summary.contains("evening", ignoreCase = true))
        assertTrue(result.nightMinutes > result.morningMinutes)
    }

    @Test
    fun testDetectChronotype_InsufficientData() {
        val attempts = listOf(
            createAttemptAtHour(hour = 10, durationMins = 30)
        )

        val result = PersonalizationEngine.detectChronotype(attempts)
        assertEquals(Chronotype.ADAPTIVE, result.chronotype)
        assertEquals("Establishing Pattern", result.confidence)
    }

    @Test
    fun testRecommendTechniqueForTopic() {
        val hardTopic = Topic(
            unitId = "u1",
            subjectId = "s1",
            name = "Quantum Mechanics",
            estimatedEffortMinutes = 60,
            difficulty = "hard",
            importance = "critical",
            status = "pending"
        )
        assertEquals(LearningTechnique.ACTIVE_RECALL, PersonalizationEngine.recommendTechniqueForTopic(hardTopic))

        val revisionTopic = Topic(
            unitId = "u1",
            subjectId = "s1",
            name = "Formula Review",
            estimatedEffortMinutes = 30,
            difficulty = "medium",
            importance = "normal",
            status = "revision"
        )
        assertEquals(LearningTechnique.SPACED_BLURTING, PersonalizationEngine.recommendTechniqueForTopic(revisionTopic))

        val synthesisTopic = Topic(
            unitId = "u1",
            subjectId = "s1",
            name = "System Architecture",
            estimatedEffortMinutes = 120,
            difficulty = "medium",
            importance = "normal",
            status = "pending"
        )
        assertEquals(LearningTechnique.FEYNMAN_METHOD, PersonalizationEngine.recommendTechniqueForTopic(synthesisTopic))

        val standardTopic = Topic(
            unitId = "u1",
            subjectId = "s1",
            name = "Vocabulary List",
            estimatedEffortMinutes = 25,
            difficulty = "easy",
            importance = "normal",
            status = "pending"
        )
        assertEquals(LearningTechnique.POMODORO_CLASSIC, PersonalizationEngine.recommendTechniqueForTopic(standardTopic))
    }

    @Test
    fun testBurnoutGuard_OptimalPacing() {
        val attempts = listOf(
            createAttemptAtHour(hour = 10, durationMins = 45, daysAgo = 1),
            createAttemptAtHour(hour = 14, durationMins = 45, daysAgo = 2),
            createAttemptAtHour(hour = 11, durationMins = 60, daysAgo = 3)
        )

        val assessment = PersonalizationEngine.evaluateBurnoutRisk(attempts)
        assertEquals(BurnoutRiskLevel.OPTIMAL, assessment.riskLevel)
        assertTrue(assessment.recoveryRecommendation.contains("balanced", ignoreCase = true) || assessment.recoveryRecommendation.contains("sustainable", ignoreCase = true))
    }

    @Test
    fun testBurnoutGuard_HighBurnoutRisk() {
        val attempts = mutableListOf<StudyAttempt>()
        // 4 consecutive days with 300 mins (5h) each
        for (day in 0..4) {
            attempts.add(createAttemptAtHour(hour = 10, durationMins = 180, daysAgo = day))
            attempts.add(createAttemptAtHour(hour = 23, durationMins = 120, daysAgo = day))
        }

        val assessment = PersonalizationEngine.evaluateBurnoutRisk(attempts)
        assertEquals(BurnoutRiskLevel.HIGH, assessment.riskLevel)
        assertTrue(assessment.consecutiveHeavyDays >= 3)
        assertTrue(assessment.recoveryRecommendation.contains("recovery", ignoreCase = true) || assessment.recoveryRecommendation.contains("burnout", ignoreCase = true))
    }

    @Test
    fun testShareableCardAndBuddyParsing() {
        val cardText = PersonalizationEngine.formatShareableStudyCard(
            userName = "Alex",
            userXp = 1250,
            streakDays = 7,
            totalMinutes = 1470,
            examDaysRemaining = 18,
            scholarRankTitle = "Senior Scholar"
        )

        assertTrue(cardText.contains("Alex"))
        assertTrue(cardText.contains("1250 XP"))
        assertTrue(cardText.contains("7 days active"))
        assertTrue(cardText.contains("#FocusStudy"))

        val parsed = PersonalizationEngine.parseBuddyStatsToken(cardText)
        assertNotNull(parsed)
        assertEquals("Alex", parsed!!.name)
        assertEquals(1250, parsed.xp)
        assertEquals(7, parsed.streakDays)
        assertEquals(1470, parsed.focusMinutes)
        assertEquals(18, parsed.examDaysRemaining)
    }

    @Test
    fun testBuddyStatsToken_InvalidFormat() {
        assertNull(PersonalizationEngine.parseBuddyStatsToken("random text with no token"))
        assertNull(PersonalizationEngine.parseBuddyStatsToken("[BUDDY-STATS:INVALID_DATA]"))
    }

    @Test
    fun testCompareWithBuddy() {
        val buddy = StudyBuddyData(
            name = "Maya",
            xp = 800,
            streakDays = 5,
            focusMinutes = 900
        )

        // User is ahead
        val compUserAhead = PersonalizationEngine.compareWithBuddy(
            userXp = 1100,
            userStreak = 6,
            userMinutes = 1200,
            buddy = buddy
        )
        assertTrue(compUserAhead.userAheadInXp)
        assertEquals(300, compUserAhead.xpDifference)
        assertEquals(1, compUserAhead.streakDifference)
        assertTrue(compUserAhead.encouragementMessage.contains("leading", ignoreCase = true))

        // User is behind
        val compUserBehind = PersonalizationEngine.compareWithBuddy(
            userXp = 650,
            userStreak = 3,
            userMinutes = 700,
            buddy = buddy
        )
        assertFalse(compUserBehind.userAheadInXp)
        assertEquals(150, compUserBehind.xpDifference)
        assertEquals(-2, compUserBehind.streakDifference)
        assertTrue(compUserBehind.encouragementMessage.contains("close the gap", ignoreCase = true))
    }
}
