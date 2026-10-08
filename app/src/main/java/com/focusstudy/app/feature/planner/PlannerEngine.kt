package com.focusstudy.app.feature.planner

import com.focusstudy.app.core.database.entity.*
import java.text.SimpleDateFormat
import java.util.*

data class MacroPhase(
    val phaseNumber: Int,
    val name: String,
    val startDayOffset: Int,
    val endDayOffset: Int,
    val description: String,
    val focusMode: String // "learn", "practice", "revise", "mock"
)

data class PlanGenerationResult(
    val plan: StudyPlan,
    val days: List<StudyPlanDay>,
    val sessions: List<StudySession>,
    val totalRequiredMinutes: Int,
    val totalAvailableMinutes: Int,
    val isOverloaded: Boolean,
    val requiredPaceMinutesPerDay: Int
)

object PlannerEngine {

    /**
     * Calculates the number of buffer days to keep completely free before the exam.
     * Scale: 1 day for short timelines (<14 days), up to 4 days for longer timelines (>30 days).
     */
    fun reserveBufferDays(daysRemaining: Int): Int {
        return when {
            daysRemaining <= 7 -> 1
            daysRemaining <= 14 -> 2
            daysRemaining <= 30 -> 3
            else -> 4
        }
    }

    /**
     * Generates standard macro phases based on total days until exam.
     */
    fun generateMacroPhases(totalDays: Int): List<MacroPhase> {
        val bufferDays = reserveBufferDays(totalDays)
        val usableDays = (totalDays - bufferDays).coerceAtLeast(1)

        val foundationDays = (usableDays * 0.25f).toInt().coerceAtLeast(1)
        val coreDays = (usableDays * 0.35f).toInt().coerceAtLeast(1)
        val weakTopicsDays = (usableDays * 0.15f).toInt().coerceAtLeast(1)
        val revisionDays = (usableDays * 0.15f).toInt().coerceAtLeast(1)
        val mockDays = (usableDays - foundationDays - coreDays - weakTopicsDays - revisionDays).coerceAtLeast(1)

        var currentDay = 0
        val phases = mutableListOf<MacroPhase>()

        phases.add(
            MacroPhase(
                1,
                "Phase 1: Foundation",
                currentDay,
                currentDay + foundationDays,
                "Cover core fundamentals and high-weightage base concepts.",
                "learn"
            )
        )
        currentDay += foundationDays

        phases.add(
            MacroPhase(
                2,
                "Phase 2: Core Coverage",
                currentDay,
                currentDay + coreDays,
                "Full syllabus coverage and standard practice problems.",
                "learn"
            )
        )
        currentDay += coreDays

        phases.add(
            MacroPhase(
                3,
                "Phase 3: Weak Topics",
                currentDay,
                currentDay + weakTopicsDays,
                "Targeted deep dive on high-difficulty and low-confidence topics.",
                "practice"
            )
        )
        currentDay += weakTopicsDays

        phases.add(
            MacroPhase(
                4,
                "Phase 4: Spaced Revision",
                currentDay,
                currentDay + revisionDays,
                "Spaced repetition, key formula recall, and rapid review.",
                "revise"
            )
        )
        currentDay += revisionDays

        phases.add(
            MacroPhase(
                5,
                "Phase 5: Mock Practice",
                currentDay,
                currentDay + mockDays,
                "Timed exam simulations and past question papers.",
                "mock"
            )
        )
        currentDay += mockDays

        phases.add(
            MacroPhase(
                6,
                "Phase 6: Final Buffer",
                currentDay,
                totalDays,
                "Light formula glance and mental recovery before exam.",
                "revise"
            )
        )

        return phases
    }

    /**
     * Scores and ranks topics deterministically based on:
     * - Importance weight (critical=4, high=3, normal=2, low=1)
     * - Difficulty weight (hard=3, medium=2, easy=1)
     * - Unfinished effort
     */
    fun rankTopics(topics: List<Topic>): List<Topic> {
        fun importanceScore(topic: Topic) = when (topic.importance.lowercase()) {
            "critical" -> 4
            "high" -> 3
            "normal" -> 2
            else -> 1
        }

        fun difficultyScore(topic: Topic) = when (topic.difficulty.lowercase()) {
            "hard" -> 3
            "medium" -> 2
            else -> 1
        }

        return topics.sortedWith(
            compareByDescending<Topic> { importanceScore(it) }
                .thenByDescending { difficultyScore(it) }
                .thenBy { it.actualMinutes }
                .thenBy { it.displayOrder }
        )
    }

    /**
     * Calculates total usable available minutes across all days leading to the exam date.
     */
    fun calculateAvailableMinutes(
        daysRemaining: Int,
        weekdayDailyMinutes: Int,
        weekendDailyMinutes: Int,
        startDate: Date = Date()
    ): Int {
        val bufferDays = reserveBufferDays(daysRemaining)
        val usableDays = (daysRemaining - bufferDays).coerceAtLeast(1)

        val cal = Calendar.getInstance()
        cal.time = startDate
        var total = 0

        for (i in 0 until usableDays) {
            val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
            val isWeekend = (dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY)
            total += if (isWeekend) weekendDailyMinutes else weekdayDailyMinutes
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return total
    }

    /**
     * Builds a comprehensive study plan allocating sessions across all days.
     */
    fun generateDeterministicPlan(
        exam: Exam,
        topics: List<Topic>,
        weekdayMinutes: Int = 180, // 3 hours
        weekendMinutes: Int = 300, // 5 hours
        sessionDurationMinutes: Int = 45,
        startDate: Date = Date()
    ): PlanGenerationResult {
        val daysRemaining = (((exam.examDateUtc - startDate.time) / (1000L * 60 * 60 * 24)).toInt()).coerceAtLeast(7)
        val bufferDays = reserveBufferDays(daysRemaining)
        val usableDays = (daysRemaining - bufferDays).coerceAtLeast(1)

        val totalAvailable = calculateAvailableMinutes(daysRemaining, weekdayMinutes, weekendMinutes, startDate)
        val totalRequired = topics.sumOf { it.estimatedEffortMinutes }
        val isOverloaded = totalRequired > totalAvailable
        val requiredPace = (totalRequired / usableDays).coerceAtLeast(30)

        val plan = StudyPlan(
            examId = exam.id,
            title = "${exam.title} Master Plan",
            startDateUtc = startDate.time,
            endDateUtc = exam.examDateUtc,
            totalPlannedMinutes = totalRequired,
            status = "active"
        )

        val phases = generateMacroPhases(daysRemaining)
        val rankedTopics = rankTopics(topics)
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

        val planDays = mutableListOf<StudyPlanDay>()
        val allSessions = mutableListOf<StudySession>()

        val cal = Calendar.getInstance()
        cal.time = startDate

        // Track allocated minutes per topic
        val topicRemainingMinutes = rankedTopics.associate { it.id to it.estimatedEffortMinutes }.toMutableMap()
        val defaultSessionTimes = listOf("07:00", "08:30", "17:30", "19:00", "21:00")

        var topicIndex = 0

        for (dayOffset in 0 until usableDays) {
            val currentDateStr = dateFormat.format(cal.time)
            val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
            val isWeekend = (dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY)
            val dailyBudget = if (isWeekend) weekendMinutes else weekdayMinutes

            // Determine active phase for this day
            val activePhase = phases.find { dayOffset >= it.startDayOffset && dayOffset < it.endDayOffset }
                ?: phases.first()

            val planDay = StudyPlanDay(
                planId = plan.id,
                dateString = currentDateStr,
                targetMinutes = dailyBudget,
                completedMinutes = 0,
                phaseName = activePhase.name
            )
            planDays.add(planDay)

            // Allocate sessions for this day up to dailyBudget
            var allocatedDayMinutes = 0
            var sessionSlotIndex = 0

            while (allocatedDayMinutes + sessionDurationMinutes <= dailyBudget && rankedTopics.isNotEmpty()) {
                val currentTopic = rankedTopics[topicIndex % rankedTopics.size]
                val startTime = defaultSessionTimes.getOrElse(sessionSlotIndex % defaultSessionTimes.size) { "08:00" }

                allSessions.add(
                    StudySession(
                        planDayId = planDay.id,
                        topicId = currentTopic.id,
                        subjectId = currentTopic.subjectId,
                        scheduledDate = currentDateStr,
                        startTime = startTime,
                        durationMinutes = sessionDurationMinutes,
                        mode = activePhase.focusMode,
                        reason = "${activePhase.name} · Priority: ${currentTopic.importance}"
                    )
                )

                allocatedDayMinutes += sessionDurationMinutes
                sessionSlotIndex++
                topicIndex++
            }

            cal.add(Calendar.DAY_OF_YEAR, 1)
        }

        return PlanGenerationResult(
            plan = plan,
            days = planDays,
            sessions = allSessions,
            totalRequiredMinutes = totalRequired,
            totalAvailableMinutes = totalAvailable,
            isOverloaded = isOverloaded,
            requiredPaceMinutesPerDay = requiredPace
        )
    }
}
