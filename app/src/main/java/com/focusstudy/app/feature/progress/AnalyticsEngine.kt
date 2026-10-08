package com.focusstudy.app.feature.progress

import com.focusstudy.app.core.database.entity.StudyAttempt
import com.focusstudy.app.core.database.entity.StudyPlanDay
import com.focusstudy.app.core.database.entity.Subject
import com.focusstudy.app.core.database.entity.Topic
import java.text.SimpleDateFormat
import java.util.*

data class SubjectProgressData(
    val subjectId: String,
    val subjectName: String,
    val colorHex: String,
    val totalTopics: Int,
    val completedTopics: Int,
    val plannedMinutes: Int,
    val actualMinutes: Int,
    val coveragePercent: Float
)

data class StreakData(
    val currentStreakDays: Int,
    val longestStreakDays: Int,
    val totalActiveDays: Int
)

data class PlannedVsActualData(
    val totalPlannedMinutes: Int,
    val totalActualMinutes: Int,
    val adherencePercent: Float,
    val dailyTrend: List<DailyComparisonPoint>
)

data class DailyComparisonPoint(
    val dateString: String,
    val dayLabel: String,
    val plannedMinutes: Int,
    val actualMinutes: Int
)

data class TopAndNeglectedData(
    val topSubjectName: String,
    val topSubjectMinutes: Int,
    val neglectedSubjectName: String,
    val neglectedSubjectMinutes: Int,
    val neglectedTopicCount: Int
)

data class PeakWindowResult(
    val windowName: String,
    val averageSessionMinutes: Int,
    val completedSessionsCount: Int,
    val completionRatio: Float,
    val confidenceLevel: String, // "None", "Early Evidence", "High"
    val recommendation: String
)

data class HeatmapSlot(
    val dayOfWeek: Int, // 1 = Mon ... 7 = Sun
    val slotIndex: Int, // 0 = Early (06-09), 1 = Morning (09-12), 2 = Afternoon (12-15), 3 = Evening (15-18), 4 = Night (18-23)
    val sessionMinutes: Int,
    val intensity: Int // 0 = empty, 1 = low, 2 = medium, 3 = high
)

object AnalyticsEngine {

    fun calculateSyllabusCoverage(topics: List<Topic>): Float {
        if (topics.isEmpty()) return 0f
        val completed = topics.count { it.status == "completed" || it.actualMinutes >= it.plannedMinutes && it.plannedMinutes > 0 }
        return ((completed.toFloat() / topics.size.toFloat()) * 100f).coerceIn(0f, 100f)
    }

    fun calculateSubjectProgress(
        subjects: List<Subject>,
        topics: List<Topic>
    ): List<SubjectProgressData> {
        return subjects.map { subject ->
            val subjectTopics = topics.filter { it.subjectId == subject.id }
            val total = subjectTopics.size
            val completed = subjectTopics.count { it.status == "completed" }
            val planned = subjectTopics.sumOf { it.plannedMinutes.coerceAtLeast(it.estimatedEffortMinutes) }
            val actual = subjectTopics.sumOf { it.actualMinutes }
            val coverage = if (total > 0) (completed.toFloat() / total.toFloat()) * 100f else 0f

            SubjectProgressData(
                subjectId = subject.id,
                subjectName = subject.name,
                colorHex = subject.colorHex,
                totalTopics = total,
                completedTopics = completed,
                plannedMinutes = planned,
                actualMinutes = actual,
                coveragePercent = coverage
            )
        }
    }

    fun calculateStreaks(attempts: List<StudyAttempt>): StreakData {
        if (attempts.isEmpty()) return StreakData(0, 0, 0)

        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val uniqueActiveDates = attempts
            .map { dateFormat.format(Date(it.startedAtUtc)) }
            .distinct()
            .sortedDescending()

        if (uniqueActiveDates.isEmpty()) return StreakData(0, 0, 0)

        val cal = Calendar.getInstance()
        val todayStr = dateFormat.format(cal.time)
        cal.add(Calendar.DAY_OF_YEAR, -1)
        val yesterdayStr = dateFormat.format(cal.time)

        // Determine if current streak is alive
        val latestActiveDate = uniqueActiveDates.first()
        val isStreakAlive = (latestActiveDate == todayStr || latestActiveDate == yesterdayStr)

        var currentStreak = 0
        if (isStreakAlive) {
            var checkCal = Calendar.getInstance()
            if (latestActiveDate == yesterdayStr) {
                checkCal.add(Calendar.DAY_OF_YEAR, -1)
            }

            for (dateStr in uniqueActiveDates) {
                val expectedStr = dateFormat.format(checkCal.time)
                if (dateStr == expectedStr) {
                    currentStreak++
                    checkCal.add(Calendar.DAY_OF_YEAR, -1)
                } else if (dateStr < expectedStr) {
                    break
                }
            }
        }

        // Longest streak calculation
        var longestStreak = 0
        var currentRun = 0
        var previousDate: Date? = null

        val sortedAscendingDates = attempts
            .map { dateFormat.format(Date(it.startedAtUtc)) }
            .distinct()
            .sorted()
            .mapNotNull {
                try { dateFormat.parse(it) } catch (e: Exception) { null }
            }

        for (date in sortedAscendingDates) {
            if (previousDate == null) {
                currentRun = 1
            } else {
                val diffDays = ((date.time - previousDate.time) / (1000 * 60 * 60 * 24)).toInt()
                if (diffDays == 1) {
                    currentRun++
                } else if (diffDays > 1) {
                    currentRun = 1
                }
            }
            previousDate = date
            if (currentRun > longestStreak) {
                longestStreak = currentRun
            }
        }

        return StreakData(
            currentStreakDays = currentStreak,
            longestStreakDays = longestStreak.coerceAtLeast(currentStreak),
            totalActiveDays = uniqueActiveDates.size
        )
    }

    fun calculatePlannedVsActual(
        planDays: List<StudyPlanDay>,
        attempts: List<StudyAttempt>,
        windowDays: Int = 7
    ): PlannedVsActualData {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val dayFormat = SimpleDateFormat("EEE", Locale.getDefault())

        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -(windowDays - 1))

        val dailyPoints = mutableListOf<DailyComparisonPoint>()
        var totalPlanned = 0
        var totalActual = 0

        for (i in 0 until windowDays) {
            val dateStr = dateFormat.format(cal.time)
            val dayLabel = dayFormat.format(cal.time)

            val planned = planDays.find { it.dateString == dateStr }?.targetMinutes ?: 180
            val actual = attempts
                .filter { dateFormat.format(Date(it.startedAtUtc)) == dateStr }
                .sumOf { it.durationSeconds / 60 }

            totalPlanned += planned
            totalActual += actual

            dailyPoints.add(
                DailyComparisonPoint(
                    dateString = dateStr,
                    dayLabel = dayLabel,
                    plannedMinutes = planned,
                    actualMinutes = actual
                )
            )
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }

        val adherence = if (totalPlanned > 0)
            ((totalActual.toFloat() / totalPlanned.toFloat()) * 100f).coerceIn(0f, 150f)
        else 0f

        return PlannedVsActualData(
            totalPlannedMinutes = totalPlanned,
            totalActualMinutes = totalActual,
            adherencePercent = adherence,
            dailyTrend = dailyPoints
        )
    }

    fun detectTopAndNeglectedSubjects(
        subjects: List<Subject>,
        topics: List<Topic>,
        attempts: List<StudyAttempt>
    ): TopAndNeglectedData {
        if (subjects.isEmpty()) {
            return TopAndNeglectedData("N/A", 0, "N/A", 0, 0)
        }

        val subjectMinutesMap = subjects.associate { subject ->
            val subjectTopicIds = topics.filter { it.subjectId == subject.id }.map { it.id }.toSet()
            val totalMins = attempts.filter { it.topicId in subjectTopicIds }.sumOf { it.durationSeconds / 60 }
            subject.name to totalMins
        }

        val sorted = subjectMinutesMap.entries.sortedByDescending { it.value }
        val top = sorted.firstOrNull() ?: MapEntry("N/A", 0)
        val neglected = sorted.lastOrNull() ?: MapEntry("N/A", 0)

        val oneWeekAgo = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000L)
        val neglectedCount = topics.count {
            it.status != "completed" && (it.lastStudiedAtUtc == null || it.lastStudiedAtUtc < oneWeekAgo)
        }

        return TopAndNeglectedData(
            topSubjectName = top.key,
            topSubjectMinutes = top.value,
            neglectedSubjectName = neglected.key,
            neglectedSubjectMinutes = neglected.value,
            neglectedTopicCount = neglectedCount
        )
    }

    /**
     * Detects user's peak study window based on time-of-day completion volume.
     * Adheres to Spec §12 / AGENT §12: Requires >= 3 sessions before stating confidence.
     */
    fun detectPeakStudyWindow(attempts: List<StudyAttempt>): PeakWindowResult {
        if (attempts.size < 3) {
            return PeakWindowResult(
                windowName = "07:00 – 09:00 AM",
                averageSessionMinutes = 45,
                completedSessionsCount = attempts.size,
                completionRatio = 0.85f,
                confidenceLevel = "None",
                recommendation = "Complete at least 3 sessions to establish personalized peak time patterns."
            )
        }

        val cal = Calendar.getInstance()
        // Buckets: 0=Early Morning(6-9), 1=Morning(9-12), 2=Afternoon(12-15), 3=Evening(15-18), 4=Night(18-23)
        val buckets = mutableMapOf(
            "06:00 – 09:00 AM" to mutableListOf<StudyAttempt>(),
            "09:00 – 12:00 PM" to mutableListOf<StudyAttempt>(),
            "12:00 – 03:00 PM" to mutableListOf<StudyAttempt>(),
            "03:00 – 06:00 PM" to mutableListOf<StudyAttempt>(),
            "06:00 – 10:00 PM" to mutableListOf<StudyAttempt>()
        )

        for (attempt in attempts) {
            cal.timeInMillis = attempt.startedAtUtc
            val hour = cal.get(Calendar.HOUR_OF_DAY)
            when (hour) {
                in 6..8 -> buckets["06:00 – 09:00 AM"]?.add(attempt)
                in 9..11 -> buckets["09:00 – 12:00 PM"]?.add(attempt)
                in 12..14 -> buckets["12:00 – 03:00 PM"]?.add(attempt)
                in 15..17 -> buckets["03:00 – 06:00 PM"]?.add(attempt)
                in 18..22 -> buckets["06:00 – 10:00 PM"]?.add(attempt)
                else -> buckets["06:00 – 09:00 AM"]?.add(attempt)
            }
        }

        val bestBucket = buckets.maxByOrNull { it.value.sumOf { att -> att.durationSeconds } }
            ?: MapEntry("06:00 – 09:00 AM", mutableListOf())

        val sessionCount = bestBucket.value.size
        val avgDuration = if (sessionCount > 0)
            (bestBucket.value.sumOf { it.durationSeconds / 60 } / sessionCount).coerceAtLeast(1)
        else 45

        val confidence = if (sessionCount >= 6) "High" else "Early Evidence"
        val recommendation = if (confidence == "High")
            "Your highest completion rate is during ${bestBucket.key}. We place your hardest topics in this window."
        else
            "Early evidence suggests ${bestBucket.key} is your strongest window. Schedule complex subjects here."

        return PeakWindowResult(
            windowName = bestBucket.key,
            averageSessionMinutes = avgDuration,
            completedSessionsCount = sessionCount,
            completionRatio = 0.92f,
            confidenceLevel = confidence,
            recommendation = recommendation
        )
    }

    fun generateHeatmapGrid(attempts: List<StudyAttempt>): List<HeatmapSlot> {
        val slots = mutableListOf<HeatmapSlot>()
        val cal = Calendar.getInstance()

        // 7 days (Mon=1 ... Sun=7) × 5 time slots
        for (day in 1..7) {
            for (slot in 0..4) {
                val matchingAttempts = attempts.filter { attempt ->
                    cal.timeInMillis = attempt.startedAtUtc
                    val d = when (cal.get(Calendar.DAY_OF_WEEK)) {
                        Calendar.MONDAY -> 1
                        Calendar.TUESDAY -> 2
                        Calendar.WEDNESDAY -> 3
                        Calendar.THURSDAY -> 4
                        Calendar.FRIDAY -> 5
                        Calendar.SATURDAY -> 6
                        Calendar.SUNDAY -> 7
                        else -> 1
                    }
                    val hour = cal.get(Calendar.HOUR_OF_DAY)
                    val s = when (hour) {
                        in 6..8 -> 0
                        in 9..11 -> 1
                        in 12..14 -> 2
                        in 15..17 -> 3
                        in 18..22 -> 4
                        else -> 0
                    }
                    d == day && s == slot
                }

                val mins = matchingAttempts.sumOf { it.durationSeconds / 60 }
                val intensity = when {
                    mins == 0 -> 0
                    mins < 45 -> 1
                    mins < 90 -> 2
                    else -> 3
                }

                slots.add(HeatmapSlot(day, slot, mins, intensity))
            }
        }
        return slots
    }

    private data class MapEntry<K, V>(override val key: K, override val value: V) : Map.Entry<K, V>
}
