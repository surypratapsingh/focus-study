package com.focusstudy.app

import com.focusstudy.app.core.ai.AiCoachContext
import com.focusstudy.app.core.ai.AiCoachService
import com.focusstudy.app.core.backup.BackupPayload
import com.focusstudy.app.core.backup.ExamDto
import com.focusstudy.app.core.backup.TopicDto
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class AiCoachAndNotificationTest {

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    @Test
    fun aiCoachService_answersWhyAmIBehindWithActionProposal() {
        val coachService = AiCoachService()
        val context = AiCoachContext(
            examTitle = "AP Physics",
            daysUntilExam = 18,
            todayTargetMinutes = 180,
            todayCompletedMinutes = 45,
            nextTopicName = "Electromagnetism",
            atRiskTopics = listOf("Thermodynamics", "Optics"),
            streakDays = 4,
            coveragePercent = 62f,
            peakWindow = "Morning (07:00 – 09:00 AM)"
        )

        val response = coachService.answerPrompt("Why am I behind?", context)

        assertTrue(response.replyText.contains("62% syllabus coverage"))
        assertTrue(response.replyText.contains("Thermodynamics"))
        assertNotNull("Should propose schedule rebalance action", response.suggestedAction)
        assertEquals("REBUILD_SCHEDULE", response.suggestedAction?.actionType)
    }

    @Test
    fun aiCoachService_answersWhatShouldIStudyWithPeakWindow() {
        val coachService = AiCoachService()
        val context = AiCoachContext(
            examTitle = "GATE CS",
            daysUntilExam = 40,
            todayTargetMinutes = 240,
            todayCompletedMinutes = 60,
            nextTopicName = "B-Trees & Indexing",
            atRiskTopics = listOf("Compiler Design"),
            streakDays = 12,
            coveragePercent = 80f,
            peakWindow = "Morning (08:00 – 11:00 AM)"
        )

        val response = coachService.answerPrompt("What should I study today?", context)

        assertTrue(response.replyText.contains("B-Trees & Indexing"))
        assertTrue(response.replyText.contains("Morning (08:00 – 11:00 AM)"))
        assertNotNull(response.supportingData)
    }

    @Test
    fun aiCoachService_proposesLightenTodayAction() {
        val coachService = AiCoachService()
        val context = AiCoachContext(
            examTitle = "Semester Finals",
            daysUntilExam = 25,
            todayTargetMinutes = 180,
            todayCompletedMinutes = 90,
            nextTopicName = "Linear Algebra",
            atRiskTopics = emptyList(),
            streakDays = 7,
            coveragePercent = 75f,
            peakWindow = "Afternoon (14:00 – 16:00)"
        )

        val response = coachService.answerPrompt("Make today lighter please", context)

        assertTrue(response.replyText.contains("Preventing burnout"))
        assertNotNull(response.suggestedAction)
        assertEquals("LIGHTEN_TODAY", response.suggestedAction?.actionType)
    }

    @Test
    fun dataBackup_serializesAndDeserializesRoundTrip() {
        val payload = BackupPayload(
            exportVersion = 1,
            exportedAtUtc = 1700000000000L,
            exams = listOf(
                ExamDto(id = "e1", title = "Algorithms Exam", examType = "university", examDateUtc = 1710000000000L)
            ),
            topics = listOf(
                TopicDto(id = "t1", unitId = "u1", subjectId = "s1", name = "Dynamic Programming", estimatedEffortMinutes = 90)
            )
        )

        val jsonStr = json.encodeToString(payload)
        val decoded = json.decodeFromString<BackupPayload>(jsonStr)

        assertEquals(1, decoded.exams.size)
        assertEquals("Algorithms Exam", decoded.exams[0].title)
        assertEquals(1, decoded.topics.size)
        assertEquals("Dynamic Programming", decoded.topics[0].name)
    }

    @Test
    fun aiCoachService_parsesCodingSessionFrom1130To200AndProposesLogging() {
        val coachService = AiCoachService()
        val context = AiCoachContext(
            examTitle = "Semester Finals",
            daysUntilExam = 30,
            todayTargetMinutes = 180,
            todayCompletedMinutes = 0,
            nextTopicName = "Data Structures",
            atRiskTopics = emptyList(),
            streakDays = 5,
            coveragePercent = 40f,
            peakWindow = "Morning",
            topicsList = listOf("Data Structures", "Algorithms", "Coding")
        )

        val query = "Suppose right now I'm doing coding from 11:30 to 2:00 p.m., so it should enter this in data."
        val response = coachService.answerPrompt(query, context)

        assertNotNull("Must suggest action", response.suggestedAction)
        assertEquals("LOG_STUDY_SESSION", response.suggestedAction?.actionType)
        assertTrue(response.suggestedAction!!.payloadJson.contains("150"))
        assertTrue(response.suggestedAction!!.payloadJson.contains("Coding"))
        assertTrue(response.suggestedAction!!.payloadJson.contains("11:30"))
        assertTrue(response.suggestedAction!!.payloadJson.contains("14:00"))
        assertTrue(response.supportingData!!.contains("+300 XP"))
    }

    @Test
    fun aiCoachService_answersReadScheduleWithTodaySessions() {
        val coachService = AiCoachService()
        val context = AiCoachContext(
            examTitle = "Finals",
            daysUntilExam = 20,
            todayTargetMinutes = 180,
            todayCompletedMinutes = 45,
            nextTopicName = "Calculus",
            atRiskTopics = emptyList(),
            streakDays = 3,
            coveragePercent = 50f,
            peakWindow = "Morning",
            todaySessionsSummary = listOf("11:30: Coding (150m, Pending)", "15:00: Calculus (45m, Pending)")
        )

        val response = coachService.answerPrompt("read my schedule", context)

        assertTrue(response.replyText.contains("Coding (150m"))
        assertTrue(response.replyText.contains("Calculus (45m"))
        assertTrue(response.replyText.contains("180 minutes"))
    }

    @Test
    fun aiCoachService_proposesArrangeScheduleAction() {
        val coachService = AiCoachService()
        val context = AiCoachContext(
            examTitle = "Finals",
            daysUntilExam = 20,
            todayTargetMinutes = 180,
            todayCompletedMinutes = 0,
            nextTopicName = null,
            atRiskTopics = emptyList(),
            streakDays = 0,
            coveragePercent = 0f,
            peakWindow = "Morning"
        )

        val response = coachService.answerPrompt("arrange a schedule for me", context)

        assertNotNull(response.suggestedAction)
        assertEquals("REBUILD_SCHEDULE", response.suggestedAction?.actionType)
        assertTrue(response.replyText.contains("arrange and organize your study schedule"))
    }

    @Test
    fun aiCoachService_queryCoachOperatesOfflineWithoutApiKey() = kotlinx.coroutines.runBlocking {
        val coachService = AiCoachService()
        val context = AiCoachContext(
            examTitle = "USMLE Step 1",
            daysUntilExam = 45,
            todayTargetMinutes = 240,
            todayCompletedMinutes = 120,
            nextTopicName = "Pathology",
            atRiskTopics = emptyList(),
            streakDays = 10,
            coveragePercent = 65f,
            peakWindow = "Morning"
        )

        // Without API key, queryCoach uses offline heuristic engine ($0 cost guarantee)
        val response = coachService.queryCoach("What should I study today?", context, apiKeyOverride = null)
        assertTrue(response.replyText.contains("Pathology"))
        assertNotNull(response.supportingData)
    }

    @Test
    fun aiCoachService_queryCoachPrioritizesLocalStudyCommandsInstantly() = kotlinx.coroutines.runBlocking {
        val coachService = AiCoachService()
        val context = AiCoachContext(
            examTitle = "Finals",
            daysUntilExam = 15,
            todayTargetMinutes = 180,
            todayCompletedMinutes = 0,
            nextTopicName = "Algorithms",
            atRiskTopics = emptyList(),
            streakDays = 3,
            coveragePercent = 50f,
            peakWindow = "Morning",
            topicsList = listOf("Algorithms", "Data Structures")
        )

        val response = coachService.queryCoach("doing algorithms from 10:00 to 11:30", context, apiKeyOverride = "mock_key")
        assertNotNull("Should prioritize local action logging over cloud query", response.suggestedAction)
        assertEquals("LOG_STUDY_SESSION", response.suggestedAction?.actionType)
        assertTrue(response.suggestedAction!!.payloadJson.contains("Algorithms"))
        assertTrue(response.suggestedAction!!.payloadJson.contains("90"))
    }
}
