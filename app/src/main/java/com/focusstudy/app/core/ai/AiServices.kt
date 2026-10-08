package com.focusstudy.app.core.ai

import com.focusstudy.app.core.database.AppDatabase
import com.focusstudy.app.core.database.entity.*
import com.focusstudy.app.feature.planner.PlannerEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.*

enum class RiskLevel {
    LOW,
    MODERATE,
    HIGH,
    CRITICAL
}

data class ScheduleRiskAssessment(
    val riskLevel: RiskLevel,
    val remainingRequiredMinutes: Int,
    val remainingCapacityMinutes: Int,
    val deficitMinutes: Int,
    val explanation: String
)

data class ReplanApplyResult(
    val appliedActionsCount: Int,
    val summary: String,
    val planVersion: AiPlanVersion
)

/**
 * Service generating and explaining full study plans with deterministic fallback
 */
class AiPlannerService(
    private val db: AppDatabase? = null
) {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    /**
     * Generates a structured study plan proposal
     */
    suspend fun generateStudyPlan(
        exam: Exam,
        topics: List<Topic>,
        availableHours: Float = 3.0f,
        daysRemaining: Int
    ): AiPlanResponse {
        val topicIds = topics.map { it.id }.toSet()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val examDateStr = dateFormat.format(Date(exam.examDateUtc))

        // Deterministic proposal generation (offline / fallback mode)
        val dailyMinutes = (availableHours * 60).toInt().coerceIn(60, 480)
        val weekendMinutes = (dailyMinutes * 1.5f).toInt().coerceIn(60, 600)
        val deterministicResult = PlannerEngine.generateDeterministicPlan(
            exam = exam,
            topics = topics,
            weekdayMinutes = dailyMinutes,
            weekendMinutes = weekendMinutes
        )

        val daysDto = deterministicResult.days.map { day ->
            val daySessions = deterministicResult.sessions.filter { it.scheduledDate == day.dateString }
            AiPlanDayDto(
                date = day.dateString,
                sessions = daySessions.map { s ->
                    AiSessionDto(
                        startTime = s.startTime,
                        durationMinutes = s.durationMinutes,
                        topicId = s.topicId,
                        task = "Study key concepts & exercises",
                        mode = s.mode,
                        reason = "Ranked priority for phase ${day.phaseName}"
                    )
                }
            )
        }

        val proposal = AiPlanResponse(
            planTitle = "${exam.title} Strategic AI Plan",
            days = daysDto,
            warnings = if (deterministicResult.isOverloaded) listOf("Required pace (${deterministicResult.requiredPaceMinutesPerDay}m/day) exceeds standard daily availability.") else emptyList(),
            assumptions = listOf("Assumes $availableHours hours/day study availability", "${PlannerEngine.reserveBufferDays(daysRemaining)} buffer days reserved before exam")
        )

        // Validate proposal against domain boundaries
        val validation = AiResponseValidator.validatePlanResponse(proposal, topicIds, examDateStr)
        return validation.validatedData ?: proposal
    }

    /**
     * Generates high-level strategic reasoning behind the schedule
     */
    fun generatePlanExplanation(
        exam: Exam,
        topics: List<Topic>,
        daysRemaining: Int,
        plan: StudyPlan
    ): String {
        val bufferDays = PlannerEngine.reserveBufferDays(daysRemaining)
        val totalTopics = topics.size
        val criticalCount = topics.count { it.importance == "critical" || it.importance == "high" }

        return "Your plan for '${exam.title}' allocates $totalTopics topics over $daysRemaining days with a $bufferDays-day final buffer. $criticalCount high-weightage topics are scheduled during peak morning focus phases, followed by interleaved spaced revision."
    }

    /**
     * Records a new plan version in history
     */
    suspend fun savePlanVersion(
        planId: String,
        versionNumber: Int,
        summaryOfChanges: String,
        rawDiffJson: String
    ): AiPlanVersion {
        val version = AiPlanVersion(
            planId = planId,
            versionNumber = versionNumber,
            summaryOfChanges = summaryOfChanges,
            promptVersion = AiModelConfig.PROMPT_VERSION_PLANNER,
            rawJsonDiff = rawDiffJson,
            appliedAtUtc = System.currentTimeMillis()
        )
        val database = requireNotNull(db) { "AppDatabase required to save plan version" }
        database.studyPlanDao().insertPlanVersion(version)
        return version
    }

    fun getPlanVersions(planId: String): Flow<List<AiPlanVersion>> =
        requireNotNull(db) { "AppDatabase required to query plan versions" }.studyPlanDao().getPlanVersions(planId)
}

/**
 * Service managing missed sessions, schedule risk, and minimal delta replanning
 */
class AiReplanService(
    private val db: AppDatabase? = null
) {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    /**
     * Identifies past scheduled sessions that were not completed
     */
    fun detectMissedSessions(
        allSessions: List<StudySession>,
        todayStr: String
    ): List<StudySession> {
        return allSessions.filter {
            it.scheduledDate < todayStr && !it.isCompleted
        }.sortedBy { it.scheduledDate }
    }

    /**
     * Calculates schedule risk deterministically
     */
    fun calculateScheduleRisk(
        exam: Exam,
        allTopics: List<Topic>,
        remainingDays: Int,
        availableDailyMinutes: Int
    ): ScheduleRiskAssessment {
        val bufferDays = PlannerEngine.reserveBufferDays(remainingDays)
        val usableDays = (remainingDays - bufferDays).coerceAtLeast(1)
        val totalCapacity = usableDays * availableDailyMinutes

        val remainingRequired = allTopics.filter { it.status != "completed" }.sumOf {
            (it.estimatedEffortMinutes - it.actualMinutes).coerceAtLeast(0)
        }

        val deficit = (remainingRequired - totalCapacity).coerceAtLeast(0)
        val riskRatio = if (totalCapacity > 0) remainingRequired.toFloat() / totalCapacity.toFloat() else 2.0f

        val (riskLevel, explanation) = when {
            riskRatio > 1.3f -> RiskLevel.CRITICAL to "Critical schedule risk: unfinished syllabus exceeds total available study capacity by ${deficit / 60}h ${deficit % 60}m."
            riskRatio > 1.0f -> RiskLevel.HIGH to "High schedule risk: workload exceeds available time. Daily pace must increase or lower-priority topics dropped."
            riskRatio > 0.75f -> RiskLevel.MODERATE to "Moderate risk: on track, but tight timeline. Keep consistency to preserve the $bufferDays-day buffer."
            else -> RiskLevel.LOW to "Low risk: comfortably on track with ample capacity remaining before exam."
        }

        return ScheduleRiskAssessment(
            riskLevel = riskLevel,
            remainingRequiredMinutes = remainingRequired,
            remainingCapacityMinutes = totalCapacity,
            deficitMinutes = deficit,
            explanation = explanation
        )
    }

    /**
     * Proposes minimal delta replan to recover from missed sessions
     */
    fun generateReplanProposal(
        exam: Exam,
        missedSessions: List<StudySession>,
        atRiskTopics: List<Topic>,
        existingSessions: List<StudySession>,
        todayStr: String
    ): AiReplanProposal {
        val actions = mutableListOf<AiPlanActionDto>()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val cal = Calendar.getInstance()
        cal.time = Date()

        val examDateStr = dateFormat.format(Date(exam.examDateUtc))

        // Minimal Delta: Reschedule missed sessions into upcoming days
        var dayOffset = 0
        for (session in missedSessions) {
            if (session.isLocked) continue // Never move locked sessions

            // Find target date in the upcoming 3 days
            cal.time = Date()
            cal.add(Calendar.DAY_OF_YEAR, dayOffset)
            val targetDateStr = dateFormat.format(cal.time)

            if (targetDateStr <= examDateStr) {
                actions.add(
                    AiPlanActionDto(
                        actionType = "MOVE_SESSION",
                        sessionId = session.id,
                        newDate = targetDateStr,
                        newStartTime = "17:00",
                        topicId = session.topicId,
                        durationMinutes = session.durationMinutes,
                        mode = session.mode,
                        reason = "Rescheduling missed session from ${session.scheduledDate} to $targetDateStr"
                    )
                )
            }
            dayOffset = (dayOffset + 1) % 4
        }

        // Add targeted practice for high-risk topics lacking upcoming sessions
        val upcomingTopicIds = existingSessions.filter { it.scheduledDate >= todayStr }.map { it.topicId }.toSet()
        for (topic in atRiskTopics.take(2)) {
            if (topic.id !in upcomingTopicIds) {
                cal.time = Date()
                cal.add(Calendar.DAY_OF_YEAR, 1)
                val targetDateStr = dateFormat.format(cal.time)
                if (targetDateStr <= examDateStr) {
                    actions.add(
                        AiPlanActionDto(
                            actionType = "ADD_SESSION",
                            sessionId = null,
                            newDate = targetDateStr,
                            newStartTime = "19:00",
                            topicId = topic.id,
                            durationMinutes = 45,
                            mode = "practice",
                            reason = "Urgent catchup session for at-risk topic '${topic.name}'"
                        )
                    )
                }
            }
        }

        val proposal = AiReplanProposal(
            summary = "Recovered ${actions.count { it.actionType == "MOVE_SESSION" }} missed sessions and slotted ${actions.count { it.actionType == "ADD_SESSION" }} priority catchup sessions.",
            riskAssessment = if (missedSessions.size > 3) "High missed backlog" else "Recoverable with minor schedule shifts",
            actions = actions
        )

        // Validate proposal against constraints
        val sessionsMap = existingSessions.associateBy { it.id }
        val topicIds = (existingSessions.map { it.topicId } + atRiskTopics.map { it.id }).toSet()
        val validation = AiResponseValidator.validateReplanProposal(proposal, sessionsMap, topicIds, examDateStr)

        return validation.validatedData ?: proposal
    }

    /**
     * Applies accepted replan delta actions directly to Room database
     */
    suspend fun applyReplan(
        proposal: AiReplanProposal,
        planId: String
    ): ReplanApplyResult {
        val database = requireNotNull(db) { "AppDatabase required to apply replan" }
        var appliedCount = 0

        for (action in proposal.actions) {
            when (action.actionType) {
                "MOVE_SESSION" -> {
                    val sessionId = action.sessionId ?: continue
                    val existing = database.studyPlanDao().getSessionById(sessionId) ?: continue
                    if (!existing.isLocked) {
                        val updated = existing.copy(
                            scheduledDate = action.newDate ?: existing.scheduledDate,
                            startTime = action.newStartTime ?: existing.startTime
                        )
                        database.studyPlanDao().updateSession(updated)
                        appliedCount++
                    }
                }
                "ADD_SESSION" -> {
                    val topicId = action.topicId ?: continue
                    val newSession = StudySession(
                        topicId = topicId,
                        subjectId = "subject_catchup",
                        scheduledDate = action.newDate ?: SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()),
                        startTime = action.newStartTime ?: "17:00",
                        durationMinutes = action.durationMinutes,
                        mode = action.mode,
                        reason = action.reason
                    )
                    database.studyPlanDao().insertSession(newSession)
                    appliedCount++
                }
                "REMOVE_SESSION" -> {
                    val sessionId = action.sessionId ?: continue
                    val existing = database.studyPlanDao().getSessionById(sessionId) ?: continue
                    if (!existing.isLocked) {
                        database.studyPlanDao().deleteSession(existing)
                        appliedCount++
                    }
                }
            }
        }

        // Record plan version
        val version = AiPlanVersion(
            planId = planId,
            versionNumber = System.currentTimeMillis().toInt() and 0xFFFF,
            summaryOfChanges = proposal.summary,
            promptVersion = AiModelConfig.PROMPT_VERSION_PLANNER,
            rawJsonDiff = json.encodeToString(proposal.actions),
            appliedAtUtc = System.currentTimeMillis()
        )
        database.studyPlanDao().insertPlanVersion(version)

        return ReplanApplyResult(
            appliedActionsCount = appliedCount,
            summary = proposal.summary,
            planVersion = version
        )
    }
}

/**
 * Service generating AI daily briefs, weekly reviews, and metrics explanations
 */
class AiInsightService(
    private val db: AppDatabase? = null
) {
    /**
     * Generates and stores the AI daily brief
     */
    suspend fun generateDailyBrief(
        exam: Exam,
        todaySessions: List<StudySession>,
        atRiskTopics: List<Topic>,
        peakWindow: String = "Morning (07:00 - 10:00)",
        todayTargetMinutes: Int = 180
    ): AiInsight {
        val database = requireNotNull(db) { "AppDatabase required for generateDailyBrief" }
        val daysUntilExam = (((exam.examDateUtc - System.currentTimeMillis()) / (1000L * 60 * 60 * 24)).toInt()).coerceAtLeast(1)
        val firstTopicId = todaySessions.firstOrNull()?.topicId
        val topicName = if (firstTopicId != null) {
            database.syllabusDao().getTopicById(firstTopicId)?.name ?: "Core Concepts"
        } else {
            atRiskTopics.firstOrNull()?.name ?: "Syllabus Review"
        }

        val content = "Today is crucial: $daysUntilExam days left until ${exam.title}. Aim for $todayTargetMinutes minutes, kicking off with '$topicName'. Your optimal focus window is $peakWindow."

        val insight = AiInsight(
            category = "daily_brief",
            title = "Daily Focus Brief",
            content = content,
            actionIntentJson = """{"targetMinutes":$todayTargetMinutes,"primaryTopic":"$topicName"}""",
            isRead = false,
            createdAtUtc = System.currentTimeMillis()
        )

        database.aiInsightDao().insertInsight(insight)
        return insight
    }

    /**
     * Generates and stores a weekly progress review
     */
    suspend fun generateWeeklyReview(
        totalStudyMinutes: Int,
        plannedMinutes: Int,
        streakDays: Int,
        completedTopicsCount: Int
    ): AiInsight {
        val database = requireNotNull(db) { "AppDatabase required for generateWeeklyReview" }
        val adherence = if (plannedMinutes > 0) ((totalStudyMinutes.toFloat() / plannedMinutes.toFloat()) * 100).toInt() else 100
        val content = "Weekly recap: You completed ${totalStudyMinutes / 60}h ${totalStudyMinutes % 60}m of study with $adherence% schedule adherence and a $streakDays-day streak! $completedTopicsCount topics fully mastered."

        val insight = AiInsight(
            category = "weekly_review",
            title = "Weekly Performance Review",
            content = content,
            actionIntentJson = """{"adherencePercent":$adherence,"streakDays":$streakDays}""",
            isRead = false,
            createdAtUtc = System.currentTimeMillis()
        )

        database.aiInsightDao().insertInsight(insight)
        return insight
    }

    /**
     * Generates natural language explanation of peak study hours
     */
    fun explainPeakWindow(windowName: String, confidenceScore: Float): String {
        val scorePercent = (confidenceScore * 100).toInt()
        return "Your study data indicates your peak productivity window is $windowName ($scorePercent% focus completion rate without interruptions). Schedule difficult topics here."
    }

    /**
     * Generates natural language progress summarizer
     */
    fun explainMetrics(
        coveragePercent: Float,
        streakDays: Int,
        neglectedCount: Int
    ): String {
        val covInt = coveragePercent.toInt()
        return when {
            neglectedCount > 2 -> "You've reached $covInt% coverage with a $streakDays-day streak, but $neglectedCount priority topics have not been touched recently and risk decay."
            covInt >= 80 -> "Outstanding progress! You have achieved $covInt% coverage. Shift focus towards full exam simulation and rapid spaced revision."
            else -> "Steady pacing: $covInt% coverage and $streakDays-day active streak. Continue daily consistency to stay ahead of the curve."
        }
    }

    fun getAllInsights(): Flow<List<AiInsight>> =
        requireNotNull(db) { "AppDatabase required to query insights" }.aiInsightDao().getAllInsights()

    fun getUnreadInsights(): Flow<List<AiInsight>> =
        requireNotNull(db) { "AppDatabase required to query unread insights" }.aiInsightDao().getUnreadInsights()

    suspend fun markAsRead(insightId: String) =
        requireNotNull(db) { "AppDatabase required to mark insight as read" }.aiInsightDao().markAsRead(insightId)
}
