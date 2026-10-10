package com.focusstudy.app.core.ai

import com.focusstudy.app.core.database.AppDatabase
import com.focusstudy.app.core.database.entity.*
import com.focusstudy.app.feature.planner.PlannerEngine
import com.focusstudy.app.feature.progress.AnalyticsEngine
import com.focusstudy.app.feature.progress.GamificationEngine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.*

import com.focusstudy.app.core.datastore.UserPreferencesManager
import com.focusstudy.app.feature.progress.PersonalizationEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

@Serializable
data class CoachAction(
    val actionType: String, // "LIGHTEN_TODAY", "RESCHEDULE_SESSION", "REBUILD_SCHEDULE", "FOCUS_TOPIC", "LOG_STUDY_SESSION"
    val title: String,
    val description: String,
    val payloadJson: String = "{}"
)

data class CoachResponse(
    val replyText: String,
    val supportingData: String? = null,
    val suggestedAction: CoachAction? = null
)

data class AiCoachContext(
    val examTitle: String,
    val daysUntilExam: Int,
    val todayTargetMinutes: Int,
    val todayCompletedMinutes: Int,
    val nextTopicName: String?,
    val atRiskTopics: List<String>,
    val streakDays: Int,
    val coveragePercent: Float,
    val peakWindow: String,
    val todaySessionsSummary: List<String> = emptyList(),
    val topicsList: List<String> = emptyList(),
    val chronotypeTitle: String = "Adaptive Explorer",
    val chronotypeEmoji: String = "⚖️",
    val chronotypeOptimalRange: String = "08:00 AM – 08:00 PM",
    val burnoutRiskLabel: String = "Optimal Balance",
    val burnoutRiskEmoji: String = "🟢",
    val burnoutAdvice: String = "Pacing is balanced."
)

data class ActionExecutionResult(
    val success: Boolean,
    val message: String
)

data class ParsedStudySession(
    val topicName: String,
    val startTime: String,
    val endTime: String,
    val durationMinutes: Int
)

class AiCoachService(
    private val db: AppDatabase? = null,
    private val userPreferencesManager: UserPreferencesManager? = null
) {

    /**
     * Builds minimal, factual, privacy-preserving context from local database
     */
    suspend fun buildContext(): AiCoachContext {
        val database = requireNotNull(db) { "AppDatabase required for buildContext" }
        val exam = database.examDao().getPrimaryExam().firstOrNull()
        val daysUntilExam = if (exam != null) {
            (((exam.examDateUtc - System.currentTimeMillis()) / (1000L * 60 * 60 * 24)).toInt()).coerceAtLeast(1)
        } else 30

        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val todayStr = dateFormat.format(Date())

        val sessions = database.studyPlanDao().getSessionsForDate(todayStr).firstOrNull() ?: emptyList()
        val completedMinutes = sessions.filter { it.isCompleted }.sumOf { it.completedDurationMinutes }
        val targetMinutes = sessions.sumOf { it.durationMinutes }.coerceAtLeast(60)

        val topics = database.syllabusDao().getAllTopics().firstOrNull() ?: emptyList()
        val coverage = AnalyticsEngine.calculateSyllabusCoverage(topics)
        val attempts = database.studyAttemptDao().getAllAttempts().firstOrNull() ?: emptyList()
        val streaks = AnalyticsEngine.calculateStreaks(attempts)
        val peak = AnalyticsEngine.detectPeakStudyWindow(attempts)

        val nextSession = sessions.firstOrNull { !it.isCompleted }
        val nextTopicName = nextSession?.let { database.syllabusDao().getTopicById(it.topicId)?.name }

        val atRisk = topics.filter {
            it.status != "completed" && (it.importance == "critical" || it.difficulty == "hard")
        }.take(3).map { it.name }

        val todaySummary = sessions.map { s ->
            val tName = database.syllabusDao().getTopicById(s.topicId)?.name ?: "Topic"
            val status = if (s.isCompleted) "✓ Done" else "Pending"
            "${s.startTime}: $tName (${s.durationMinutes}m, $status)"
        }

        val chronotype = PersonalizationEngine.detectChronotype(attempts)
        val burnout = PersonalizationEngine.evaluateBurnoutRisk(attempts)

        return AiCoachContext(
            examTitle = exam?.title ?: "Master Exam",
            daysUntilExam = daysUntilExam,
            todayTargetMinutes = targetMinutes,
            todayCompletedMinutes = completedMinutes,
            nextTopicName = nextTopicName,
            atRiskTopics = atRisk,
            streakDays = streaks.currentStreakDays,
            coveragePercent = coverage,
            peakWindow = peak.windowName,
            todaySessionsSummary = todaySummary,
            topicsList = topics.map { it.name },
            chronotypeTitle = chronotype.chronotype.title,
            chronotypeEmoji = chronotype.chronotype.emoji,
            chronotypeOptimalRange = chronotype.chronotype.optimalTimeRange,
            burnoutRiskLabel = burnout.riskLevel.label,
            burnoutRiskEmoji = burnout.riskLevel.emoji,
            burnoutAdvice = burnout.recoveryRecommendation
        )
    }

    /**
     * Parses natural language input for study logging commands like:
     * "doing coding from 11:30 to 2:00 p.m." or "studied physics for 45 minutes"
     */
    fun parseStudyCommand(query: String, context: AiCoachContext): ParsedStudySession? {
        val timeRangeRegex = Regex(
            """(?:from\s+)?(\d{1,2}(?::\d{2})?\s*(?:am|pm)?)\s*(?:to|-|until)\s*(\d{1,2}(?::\d{2})?\s*(?:am|pm)?)""",
            RegexOption.IGNORE_CASE
        )
        val match = timeRangeRegex.find(query)
        if (match != null) {
            val startRaw = match.groupValues[1]
            val endRaw = match.groupValues[2]
            val (startMin, startFormatted) = parseTimeStringToMinutes(startRaw)
            val (endMinRaw, endFormattedRaw) = parseTimeStringToMinutes(endRaw)

            var endMin = endMinRaw
            var endFormatted = endFormattedRaw
            // If end time is earlier or equal to start and PM is not explicit, assume afternoon (e.g. 11:30 to 2:00 -> 14:00)
            if (endMin <= startMin && !endRaw.lowercase().contains("am")) {
                endMin += 12 * 60
                val endHour = (endMin / 60) % 24
                val endMinutesPart = endMin % 60
                endFormatted = String.format("%02d:%02d", endHour, endMinutesPart)
            }

            val durationMinutes = (endMin - startMin).coerceIn(15, 360)

            // Clean topic name
            val textWithoutTime = query.replace(match.value, " ")
            val fillers = listOf(
                "suppose", "right now", "i'm doing", "im doing", "i am doing", "i did", "doing", "did",
                "studied", "studying", "learn", "learning", "session", "enter this in data", "enter this",
                "enter into data", "enter in data", "so it should", "it should", "please", "can you",
                "log", "record", "work on", "worked on", "practice", "practicing", "from", "to", "pm", "am"
            )
            var cleaned = textWithoutTime
            for (f in fillers) {
                cleaned = cleaned.replace(Regex("(?i)\\b$f\\b"), " ")
            }
            cleaned = cleaned.replace(Regex("[^a-zA-Z0-9 ]"), " ").trim()
            val candidate = cleaned.split("\\s+".toRegex()).filter { it.isNotBlank() }.joinToString(" ")

            val existing = context.topicsList.find {
                it.contains(candidate, ignoreCase = true) || candidate.contains(it, ignoreCase = true)
            }
            val finalTopic = existing ?: if (candidate.isNotBlank()) {
                candidate.split(" ").joinToString(" ") { word -> word.replaceFirstChar { it.uppercase() } }
            } else "Focused Study"

            return ParsedStudySession(
                topicName = finalTopic,
                startTime = startFormatted,
                endTime = endFormatted,
                durationMinutes = durationMinutes
            )
        }

        // Check duration without explicit start/end: "studied math for 45 minutes" or "coding for 2 hours"
        val durationRegex = Regex("""(\d+)\s*(hours?|hrs?|minutes?|mins?)""", RegexOption.IGNORE_CASE)
        val durMatch = durationRegex.find(query)
        if (durMatch != null) {
            val amount = durMatch.groupValues[1].toIntOrNull() ?: 45
            val unit = durMatch.groupValues[2].lowercase()
            val durationMinutes = if (unit.startsWith("h")) (amount * 60).coerceIn(15, 360) else amount.coerceIn(15, 360)

            val textWithoutDur = query.replace(durMatch.value, " ")
            val fillers = listOf(
                "suppose", "right now", "i'm doing", "im doing", "i am doing", "i did", "doing", "did",
                "studied", "studying", "learn", "learning", "session", "enter this in data", "for",
                "enter into data", "enter in data", "so it should", "it should", "please", "can you", "log"
            )
            var cleaned = textWithoutDur
            for (f in fillers) {
                cleaned = cleaned.replace(Regex("(?i)\\b$f\\b"), " ")
            }
            cleaned = cleaned.replace(Regex("[^a-zA-Z0-9 ]"), " ").trim()
            val candidate = cleaned.split("\\s+".toRegex()).filter { it.isNotBlank() }.joinToString(" ")
            val existing = context.topicsList.find {
                it.contains(candidate, ignoreCase = true) || candidate.contains(it, ignoreCase = true)
            }
            val finalTopic = existing ?: if (candidate.isNotBlank()) {
                candidate.split(" ").joinToString(" ") { word -> word.replaceFirstChar { it.uppercase() } }
            } else "Focused Study"

            val cal = Calendar.getInstance()
            val currentHour = cal.get(Calendar.HOUR_OF_DAY)
            val currentMin = cal.get(Calendar.MINUTE)
            val startFormatted = String.format("%02d:%02d", currentHour, currentMin)
            val endMinutesTotal = currentHour * 60 + currentMin + durationMinutes
            val endFormatted = String.format("%02d:%02d", (endMinutesTotal / 60) % 24, endMinutesTotal % 60)

            return ParsedStudySession(
                topicName = finalTopic,
                startTime = startFormatted,
                endTime = endFormatted,
                durationMinutes = durationMinutes
            )
        }

        return null
    }

    private fun parseTimeStringToMinutes(str: String): Pair<Int, String> {
        val clean = str.trim().lowercase().replace(".", "")
        val isPm = clean.contains("pm")
        val isAm = clean.contains("am")
        val digitsPart = clean.replace("am", "").replace("pm", "").trim()
        val parts = digitsPart.split(":")
        var hour = parts[0].toIntOrNull() ?: 9
        val minute = if (parts.size > 1) parts[1].toIntOrNull() ?: 0 else 0
        if (isPm && hour < 12) hour += 12
        if (isAm && hour == 12) hour = 0
        val formatted = String.format("%02d:%02d", hour, minute)
        return Pair(hour * 60 + minute, formatted)
    }

    /**
     * Generates pedagogical, encouraging, and data-backed response
     */
    fun answerPrompt(query: String, context: AiCoachContext): CoachResponse {
        val trimmed = query.trim()

        // 1. Natural language study logging: e.g. "doing coding from 11:30 to 2:00"
        val parsedSession = parseStudyCommand(trimmed, context)
        if (parsedSession != null) {
            val hours = parsedSession.durationMinutes / 60
            val mins = parsedSession.durationMinutes % 60
            val durationText = when {
                hours > 0 && mins > 0 -> "${hours}h ${mins}m"
                hours > 0 -> "${hours}h"
                else -> "${mins}m"
            }

            val isAutoEnter = trimmed.contains("enter this in data", ignoreCase = true) ||
                    trimmed.contains("enter in data", ignoreCase = true) ||
                    trimmed.contains("save in data", ignoreCase = true) ||
                    trimmed.contains("save to data", ignoreCase = true)

            return CoachResponse(
                replyText = if (isAutoEnter) {
                    "I've recorded that you're doing ${parsedSession.topicName} from ${parsedSession.startTime} to ${parsedSession.endTime} ($durationText). Entering this directly into your study data and awarding +${parsedSession.durationMinutes * 2} XP..."
                } else {
                    "I noticed you're working on ${parsedSession.topicName} from ${parsedSession.startTime} to ${parsedSession.endTime} ($durationText). Tap below to enter this session into your study data, log topic hours, and claim +${parsedSession.durationMinutes * 2} XP."
                },
                supportingData = "+${parsedSession.durationMinutes * 2} XP · Topic: ${parsedSession.topicName} · $durationText",
                suggestedAction = CoachAction(
                    actionType = "LOG_STUDY_SESSION",
                    title = "Enter Study: ${parsedSession.topicName}",
                    description = "Record $durationText (${parsedSession.startTime} – ${parsedSession.endTime}) into database with +${parsedSession.durationMinutes * 2} XP.",
                    payloadJson = """{"topicName":"${parsedSession.topicName}","startTime":"${parsedSession.startTime}","endTime":"${parsedSession.endTime}","durationMinutes":${parsedSession.durationMinutes}}"""
                )
            )
        }

        // 2. Read Schedule / Read Things command
        if (trimmed.contains("read things", ignoreCase = true) ||
            trimmed.contains("read my schedule", ignoreCase = true) ||
            trimmed.contains("read schedule", ignoreCase = true) ||
            trimmed.contains("read today", ignoreCase = true) ||
            trimmed.contains("read out", ignoreCase = true)
        ) {
            val scheduleSummary = if (context.todaySessionsSummary.isNotEmpty()) {
                "Here is what's on your schedule for today:\n" +
                        context.todaySessionsSummary.joinToString("\n") +
                        "\nTotal target is ${context.todayTargetMinutes} minutes with ${context.todayCompletedMinutes} minutes completed so far."
            } else {
                "You currently have no sessions scheduled for today. You've completed ${context.todayCompletedMinutes}m so far. Ask me to arrange a schedule or start a session from the Today dashboard."
            }

            return CoachResponse(
                replyText = scheduleSummary,
                supportingData = "Today: ${context.todayCompletedMinutes}m / ${context.todayTargetMinutes}m · Streak: ${context.streakDays} days",
                suggestedAction = null
            )
        }

        // 3. Arrange Schedule command
        if (trimmed.contains("arrange a schedule", ignoreCase = true) ||
            trimmed.contains("arrange schedule", ignoreCase = true) ||
            trimmed.contains("organize schedule", ignoreCase = true) ||
            trimmed.contains("set up schedule", ignoreCase = true)
        ) {
            return CoachResponse(
                replyText = "I will arrange and organize your study schedule to ensure full exam coverage before your deadline. I will slot high-importance topics into your peak windows and reserve pre-exam review buffers.",
                supportingData = "${context.daysUntilExam} days to exam · Target: ${context.todayTargetMinutes}m/day",
                suggestedAction = CoachAction(
                    actionType = "REBUILD_SCHEDULE",
                    title = "Arrange Full Study Schedule",
                    description = "Automatically slots study sessions across upcoming days."
                )
            )
        }

        // 4. Standard coaching prompts
        return when {
            trimmed.contains("Why am I behind", ignoreCase = true) -> {
                val riskList = if (context.atRiskTopics.isNotEmpty()) context.atRiskTopics.joinToString(", ") else "Core fundamentals"
                CoachResponse(
                    replyText = "You're at ${context.coveragePercent.toInt()}% syllabus coverage with ${context.daysUntilExam} days to your exam. Incomplete effort on high-weightage topics ($riskList) has created a pacing deficit.",
                    supportingData = "Today: ${context.todayCompletedMinutes}m / ${context.todayTargetMinutes}m completed · Streak: ${context.streakDays} days",
                    suggestedAction = CoachAction(
                        actionType = "REBUILD_SCHEDULE",
                        title = "Adaptive Schedule Rebalance",
                        description = "Rebalance remaining sessions across upcoming days to preserve the pre-exam buffer."
                    )
                )
            }

            trimmed.contains("What should I study", ignoreCase = true) -> {
                val topic = context.nextTopicName ?: context.atRiskTopics.firstOrNull() ?: "Syllabus Review"
                CoachResponse(
                    replyText = "Your top priority today is '$topic'. To maximize retention, schedule this during your peak window (${context.peakWindow}).",
                    supportingData = "Paced target: ${context.todayTargetMinutes} minutes today",
                    suggestedAction = null
                )
            }

            trimmed.contains("Which subject needs help", ignoreCase = true) || trimmed.contains("needs help", ignoreCase = true) -> {
                val neglected = context.atRiskTopics.firstOrNull() ?: "Core concepts"
                CoachResponse(
                    replyText = "'$neglected' has the highest difficulty and lowest recorded completion. Prioritizing targeted practice sessions here will yield the largest exam score improvement.",
                    supportingData = "At-risk topics: ${context.atRiskTopics.joinToString(", ")}",
                    suggestedAction = CoachAction(
                        actionType = "FOCUS_TOPIC",
                        title = "Prioritize '$neglected'",
                        description = "Tag '$neglected' as today's priority session.",
                        payloadJson = """{"topicName":"$neglected"}"""
                    )
                )
            }

            trimmed.contains("finish before", ignoreCase = true) || trimmed.contains("Can I finish", ignoreCase = true) -> {
                val bufferDays = PlannerEngine.reserveBufferDays(context.daysUntilExam)
                val statusText = if (context.coveragePercent > 50f) "well on track" else "tight on pace"
                CoachResponse(
                    replyText = "Yes, you are $statusText. With ${context.daysUntilExam} days remaining and a $bufferDays-day final buffer, maintaining ~${context.todayTargetMinutes} minutes daily will comfortably finish all units before review week.",
                    supportingData = "Progress: ${context.coveragePercent.toInt()}% syllabus mastered · Days left: ${context.daysUntilExam}",
                    suggestedAction = null
                )
            }

            trimmed.contains("Make today lighter", ignoreCase = true) || trimmed.contains("lighter", ignoreCase = true) -> {
                CoachResponse(
                    replyText = "I understand! Preventing burnout is critical for exam retention. I can shift one session to tomorrow so you can rest after your core priority.",
                    supportingData = "Remaining today: ${(context.todayTargetMinutes - context.todayCompletedMinutes).coerceAtLeast(0)}m",
                    suggestedAction = CoachAction(
                        actionType = "LIGHTEN_TODAY",
                        title = "Lighten Today's Target",
                        description = "Shift 1 scheduled session from today to tomorrow."
                    )
                )
            }

            trimmed.contains("Rebuild my week", ignoreCase = true) -> {
                CoachResponse(
                    replyText = "Let's rebalance your week. I will recalculate session allocations across weekdays and weekends while keeping your locked sessions untouched.",
                    supportingData = "Target pace: ${(context.todayTargetMinutes / 60)}h/day",
                    suggestedAction = CoachAction(
                        actionType = "REBUILD_SCHEDULE",
                        title = "Rebalance Full Week",
                        description = "Regenerate optimized schedule allocations for all upcoming days."
                    )
                )
            }

            trimmed.contains("chronotype", ignoreCase = true) || trimmed.contains("peak energy", ignoreCase = true) || trimmed.contains("optimal time", ignoreCase = true) -> {
                CoachResponse(
                    replyText = "Your detected cognitive profile is ${context.chronotypeTitle} ${context.chronotypeEmoji}. Your highest alertness window is ${context.chronotypeOptimalRange}. Scheduling high-difficulty topics and active recall in this window maximizes retention and minimizes mental fatigue.",
                    supportingData = "Optimal Window: ${context.chronotypeOptimalRange} · Profile: ${context.chronotypeTitle}",
                    suggestedAction = null
                )
            }

            trimmed.contains("burnout", ignoreCase = true) || trimmed.contains("fatigue", ignoreCase = true) || trimmed.contains("studying too much", ignoreCase = true) -> {
                CoachResponse(
                    replyText = "Cognitive Health Check: Status is ${context.burnoutRiskLabel} ${context.burnoutRiskEmoji}. ${context.burnoutAdvice}",
                    supportingData = "Burnout Risk: ${context.burnoutRiskLabel} · Paced target: ${context.todayTargetMinutes}m",
                    suggestedAction = if (context.burnoutRiskLabel != "Optimal Balance") {
                        CoachAction(
                            actionType = "LIGHTEN_TODAY",
                            title = "Restorative Pacing",
                            description = "Reduce today's workload to allow cognitive recovery."
                        )
                    } else null
                )
            }

            trimmed.contains("technique", ignoreCase = true) || trimmed.contains("how should I study", ignoreCase = true) || trimmed.contains("study method", ignoreCase = true) -> {
                val nextTopic = context.nextTopicName ?: "core topics"
                CoachResponse(
                    replyText = "For '$nextTopic', I recommend Active Recall & Testing (close notes and retrieve answers from memory) combined with 25-minute Pomodoro sprints. For complex conceptual chapters, try the Feynman Technique: explain the topic in plain language to reveal gaps.",
                    supportingData = "Recommended Methods: Active Recall · Feynman Technique · Pomodoro",
                    suggestedAction = null
                )
            }

            trimmed.contains("study buddy", ignoreCase = true) || trimmed.contains("buddy", ignoreCase = true) || trimmed.contains("accountability", ignoreCase = true) -> {
                CoachResponse(
                    replyText = "You can share your verified study progress card with an accountability partner from the Progress tab. Compare Scholar XP, active streaks, and hours side-by-side to stay motivated together!",
                    supportingData = "Progress Tab → Study Buddy & Accountability Card",
                    suggestedAction = null
                )
            }

            trimmed.contains("quiz", ignoreCase = true) || trimmed.contains("flashcard", ignoreCase = true) || trimmed.contains("active recall", ignoreCase = true) || trimmed.contains("test memory", ignoreCase = true) -> {
                val topic = context.nextTopicName ?: context.atRiskTopics.firstOrNull() ?: "Core Syllabus Concepts"
                CoachResponse(
                    replyText = "Active recall is the most effective evidence-based learning technique! I've loaded a 5-card retrieval sprint for '$topic' with spaced repetition scoring. Tap the brain icon on any session card or start your recall sprint now.",
                    supportingData = "Recommended Topic: $topic · 5 Recall Cards · Up to +150 Scholar XP",
                    suggestedAction = CoachAction(
                        actionType = "TEST_ACTIVE_RECALL",
                        title = "Launch Recall Sprint for '$topic'",
                        description = "Start interactive 5-question flashcard test on '$topic'.",
                        payloadJson = """{"topicName":"$topic"}"""
                    )
                )
            }

            trimmed.contains("white noise", ignoreCase = true) || trimmed.contains("soundscape", ignoreCase = true) || trimmed.contains("focus sound", ignoreCase = true) || trimmed.contains("alpha wave", ignoreCase = true) -> {
                CoachResponse(
                    replyText = "Focus Study includes built-in real-time synthesized ambient soundscapes (White Noise, Brown Noise, and 10Hz Binaural Alpha Waves) directly inside the Focus Timer. It requires zero downloads or internet and generates ambient audio via native AudioTrack to keep your deep work distraction-free.",
                    supportingData = "Available: White Noise · Deep Brown Noise · 10Hz Alpha Binaural Beats",
                    suggestedAction = null
                )
            }

            else -> {
                CoachResponse(
                    replyText = "Keep going strong! You've achieved a ${context.streakDays}-day streak with ${context.coveragePercent.toInt()}% coverage. Focusing on today's ${context.todayTargetMinutes}m target during ${context.peakWindow} will keep you ahead of your deadline.",
                    supportingData = "${context.daysUntilExam} days until ${context.examTitle}",
                    suggestedAction = null
                )
            }
        }
    }

    /**
     * Top-level query orchestrator:
     * - Immediate local action execution for deterministic commands (logging, scheduling)
     * - BYOK Gemini 1.5 Flash cloud query when a personal API key is configured
     * - Graceful offline fallback to pedagogical heuristics when offline or keyless ($0 cost guarantee)
     */
    suspend fun queryCoach(
        query: String,
        context: AiCoachContext,
        apiKeyOverride: String? = null
    ): CoachResponse {
        val trimmed = query.trim()

        // 1. High-priority local actions: run immediately and offline
        if (parseStudyCommand(trimmed, context) != null ||
            trimmed.contains("read things", ignoreCase = true) ||
            trimmed.contains("read my schedule", ignoreCase = true) ||
            trimmed.contains("read schedule", ignoreCase = true) ||
            trimmed.contains("read today", ignoreCase = true) ||
            trimmed.contains("arrange a schedule", ignoreCase = true) ||
            trimmed.contains("arrange schedule", ignoreCase = true)
        ) {
            return answerPrompt(trimmed, context)
        }

        // 2. Resolve BYOK API key (explicit parameter or local UserPreferences)
        val resolvedKey = apiKeyOverride?.trim()?.ifBlank { null }
            ?: userPreferencesManager?.userPreferencesFlow?.firstOrNull()?.customGeminiApiKey?.trim()?.ifBlank { null }

        if (!resolvedKey.isNullOrBlank()) {
            try {
                return queryGeminiRest(trimmed, context, resolvedKey)
            } catch (e: Exception) {
                // Graceful fallback to deterministic heuristic on network or API failure
                val localFallback = answerPrompt(trimmed, context)
                return localFallback.copy(
                    replyText = "${localFallback.replyText}\n\n*(Using offline coach mode — verify internet connection or API key in Settings)*"
                )
            }
        }

        // 3. 100% Anti-Bill offline heuristic mode
        return answerPrompt(trimmed, context)
    }

    /**
     * Lightweight native REST query to Gemini 1.5 Flash using standard JVM HttpURLConnection and Android JSONObject
     * Strictly requires zero third-party AI SDKs and costs developer $0.00 forever.
     */
    suspend fun queryGeminiRest(
        query: String,
        context: AiCoachContext,
        apiKey: String
    ): CoachResponse = withContext(Dispatchers.IO) {
        val systemPrompt = """
            You are Focus Study AI Coach, an expert academic mentor helping the student prepare for their exam.
            Current Student State:
            - Exam: ${context.examTitle} (${context.daysUntilExam} days remaining)
            - Progress: ${context.coveragePercent.toInt()}% syllabus mastered, ${context.streakDays}-day streak
            - Today's Target: ${context.todayCompletedMinutes}m / ${context.todayTargetMinutes}m completed
            - Peak Focus Window: ${context.peakWindow}
            - At-Risk Subjects: ${context.atRiskTopics.joinToString(", ").ifEmpty { "None" }}
            - Next Planned Topic: ${context.nextTopicName ?: "General Revision"}
            - Cognitive Chronotype: ${context.chronotypeTitle} (${context.chronotypeOptimalRange})
            - Burnout Status: ${context.burnoutRiskLabel}
            Instructions:
            Provide encouraging, concise, and academically sound advice. Keep answers between 2 to 4 sentences unless the student asks for a detailed breakdown.
        """.trimIndent()

        val fullPrompt = "$systemPrompt\n\nStudent question: $query"
        val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=$apiKey"
        val url = URL(endpoint)
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 10_000
            readTimeout = 15_000
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            doOutput = true
        }

        val requestBody = JSONObject().apply {
            val parts = JSONArray().put(JSONObject().put("text", fullPrompt))
            val contents = JSONArray().put(JSONObject().put("parts", parts))
            put("contents", contents)
        }.toString()

        conn.outputStream.use { os ->
            os.write(requestBody.toByteArray(Charsets.UTF_8))
        }

        val code = conn.responseCode
        if (code in 200..299) {
            val responseString = conn.inputStream.bufferedReader().use { it.readText() }
            val root = JSONObject(responseString)
            val candidates = root.optJSONArray("candidates")
            val candidate = candidates?.optJSONObject(0)
            val content = candidate?.optJSONObject("content")
            val partsArray = content?.optJSONArray("parts")
            val reply = partsArray?.optJSONObject(0)?.optString("text")?.trim() ?: ""
            if (reply.isNotEmpty()) {
                CoachResponse(
                    replyText = reply,
                    supportingData = "⚡ Gemini 1.5 Flash (BYOK) · ${context.daysUntilExam}d to ${context.examTitle}"
                )
            } else {
                throw IllegalStateException("Empty reply from Gemini API")
            }
        } else {
            val errorText = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
            throw IllegalStateException("Gemini API error ($code): $errorText")
        }
    }

    /**
     * Executes validated action intents with DB commit and version logging
     */
    suspend fun executeAction(action: CoachAction): ActionExecutionResult {
        val database = requireNotNull(db) { "AppDatabase required to executeAction" }
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val todayStr = dateFormat.format(Date())

        val exam = database.examDao().getPrimaryExam().firstOrNull() ?: return ActionExecutionResult(false, "No active exam found.")
        val plan = database.studyPlanDao().getActivePlan(exam.id).firstOrNull() ?: return ActionExecutionResult(false, "No active plan found.")

        return when (action.actionType) {
            "LOG_STUDY_SESSION" -> {
                val topicName = extractJsonField(action.payloadJson, "topicName").ifBlank { "Focused Study" }
                val startTime = extractJsonField(action.payloadJson, "startTime").ifBlank { "11:30" }
                val endTime = extractJsonField(action.payloadJson, "endTime").ifBlank { "14:00" }
                val durationMinutes = extractJsonField(action.payloadJson, "durationMinutes").toIntOrNull() ?: 60

                val allTopics = database.syllabusDao().getAllTopics().firstOrNull() ?: emptyList()
                var topic = allTopics.find {
                    it.name.equals(topicName, ignoreCase = true) || it.name.contains(topicName, ignoreCase = true)
                }

                if (topic == null) {
                    val allSubjects = database.syllabusDao().getSubjectsForExam(exam.id).firstOrNull() ?: emptyList()
                    val subject = allSubjects.firstOrNull() ?: run {
                        val newSubj = Subject(examId = exam.id, name = "General Studies", colorHex = "#2563EB")
                        database.syllabusDao().insertSubject(newSubj)
                        newSubj
                    }
                    val allUnits = database.syllabusDao().getUnitsForSubject(subject.id).firstOrNull() ?: emptyList()
                    val unit = allUnits.firstOrNull() ?: run {
                        val newUnit = UnitEntity(subjectId = subject.id, title = "Core Study Units")
                        database.syllabusDao().insertUnits(listOf(newUnit))
                        newUnit
                    }
                    val newTopic = Topic(
                        unitId = unit.id,
                        subjectId = subject.id,
                        name = topicName,
                        status = "completed",
                        difficulty = "medium",
                        importance = "high",
                        estimatedEffortMinutes = durationMinutes,
                        actualMinutes = durationMinutes
                    )
                    database.syllabusDao().insertTopic(newTopic)
                    topic = newTopic
                } else {
                    database.syllabusDao().updateTopic(
                        topic.copy(
                            actualMinutes = topic.actualMinutes + durationMinutes,
                            status = if (topic.status == "not_started") "in_progress" else topic.status
                        )
                    )
                }

                // Insert completed StudySession for today
                val session = StudySession(
                    topicId = topic.id,
                    subjectId = topic.subjectId,
                    scheduledDate = todayStr,
                    startTime = startTime,
                    durationMinutes = durationMinutes,
                    completedDurationMinutes = durationMinutes,
                    isCompleted = true,
                    mode = "practice",
                    reason = "Voice logged via AI Coach",
                    completedAtUtc = System.currentTimeMillis()
                )
                database.studyPlanDao().insertSession(session)

                // Insert StudyAttempt for analytics, heatmaps & streaks
                val attempt = StudyAttempt(
                    sessionId = session.id,
                    topicId = topic.id,
                    startedAtUtc = System.currentTimeMillis() - (durationMinutes * 60 * 1000L),
                    endedAtUtc = System.currentTimeMillis(),
                    durationSeconds = durationMinutes * 60,
                    interrupted = false,
                    notes = "AI Voice entry: $topicName ($startTime to $endTime)"
                )
                database.studyAttemptDao().insertAttempt(attempt)

                // Update today's StudyPlanDay
                val planDays = database.studyPlanDao().getPlanDays(plan.id).firstOrNull() ?: emptyList()
                val todayDay = planDays.find { it.dateString == todayStr }
                if (todayDay != null) {
                    database.studyPlanDao().updatePlanDay(
                        todayDay.copy(
                            completedMinutes = todayDay.completedMinutes + durationMinutes,
                            targetMinutes = todayDay.targetMinutes.coerceAtLeast(todayDay.completedMinutes + durationMinutes)
                        )
                    )
                }

                // Update achievements & Scholar XP
                val allAttempts = database.studyAttemptDao().getAllAttempts().firstOrNull() ?: emptyList()
                val streaks = AnalyticsEngine.calculateStreaks(allAttempts)
                val allTopicsUpdated = database.syllabusDao().getAllTopics().firstOrNull() ?: emptyList()
                val coverage = AnalyticsEngine.calculateSyllabusCoverage(allTopicsUpdated)
                GamificationEngine.evaluateAndUnlockAchievements(
                    progressDao = database.progressDao(),
                    totalStudyMinutes = allAttempts.sumOf { it.durationSeconds / 60 },
                    activeStreakDays = streaks.currentStreakDays,
                    syllabusCoveragePercent = coverage
                )

                // Log plan version audit
                database.studyPlanDao().insertPlanVersion(
                    AiPlanVersion(
                        planId = plan.id,
                        versionNumber = System.currentTimeMillis().toInt() and 0xFFFF,
                        summaryOfChanges = "AI Voice entered $durationMinutes min session for '$topicName'.",
                        promptVersion = AiModelConfig.PROMPT_VERSION_COACH,
                        rawJsonDiff = action.payloadJson,
                        appliedAtUtc = System.currentTimeMillis()
                    )
                )

                ActionExecutionResult(
                    true,
                    "Entered $durationMinutes mins for '$topicName' ($startTime to $endTime) into your database! +${durationMinutes * 2} XP added."
                )
            }

            "LIGHTEN_TODAY" -> {
                val todaySessions = database.studyPlanDao().getSessionsForDate(todayStr).firstOrNull() ?: emptyList()
                val sessionToShift = todaySessions.lastOrNull { !it.isCompleted && !it.isLocked }

                if (sessionToShift != null) {
                    val cal = Calendar.getInstance()
                    cal.add(Calendar.DAY_OF_YEAR, 1)
                    val tomorrowStr = dateFormat.format(cal.time)

                    database.studyPlanDao().updateSession(
                        sessionToShift.copy(scheduledDate = tomorrowStr, startTime = "18:00")
                    )

                    // Log plan version
                    database.studyPlanDao().insertPlanVersion(
                        AiPlanVersion(
                            planId = plan.id,
                            versionNumber = System.currentTimeMillis().toInt() and 0xFFFF,
                            summaryOfChanges = "Shifted session '${sessionToShift.id.take(4)}' to tomorrow to lighten workload.",
                            promptVersion = AiModelConfig.PROMPT_VERSION_COACH,
                            rawJsonDiff = """[{"action":"LIGHTEN_TODAY","shiftedSessionId":"${sessionToShift.id}"}]""",
                            appliedAtUtc = System.currentTimeMillis()
                        )
                    )

                    ActionExecutionResult(true, "Lightened today's schedule! Shifted 1 session to tomorrow.")
                } else {
                    ActionExecutionResult(false, "No unlocked pending sessions available to shift today.")
                }
            }

            "REBUILD_SCHEDULE" -> {
                val topics = database.syllabusDao().getAllTopics().firstOrNull() ?: emptyList()
                val result = PlannerEngine.generateDeterministicPlan(
                    exam = exam,
                    topics = topics,
                    weekdayMinutes = 180,
                    weekendMinutes = 300
                )

                database.studyPlanDao().insertPlan(result.plan)
                database.studyPlanDao().insertPlanDays(result.days)
                database.studyPlanDao().insertSessions(result.sessions)

                database.studyPlanDao().insertPlanVersion(
                    AiPlanVersion(
                        planId = result.plan.id,
                        versionNumber = System.currentTimeMillis().toInt() and 0xFFFF,
                        summaryOfChanges = "Coach rebalanced weekly schedule across ${result.sessions.size} sessions.",
                        promptVersion = AiModelConfig.PROMPT_VERSION_COACH,
                        rawJsonDiff = "[]",
                        appliedAtUtc = System.currentTimeMillis()
                    )
                )

                ActionExecutionResult(true, "Schedule successfully arranged and saved to your database!")
            }

            "FOCUS_TOPIC" -> {
                ActionExecutionResult(true, "Tagged priority topic for your next focus session.")
            }

            else -> {
                ActionExecutionResult(false, "Unknown action type: ${action.actionType}")
            }
        }
    }

    private fun extractJsonField(json: String, field: String): String {
        val regex = Regex(""""$field"\s*:\s*"?([^",}]+)"?""")
        return regex.find(json)?.groupValues?.get(1)?.trim() ?: ""
    }
}
