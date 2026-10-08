package com.focusstudy.app.feature.progress

import com.focusstudy.app.core.database.entity.StudyAttempt
import com.focusstudy.app.core.database.entity.Topic
import java.util.Calendar

enum class Chronotype(
    val title: String,
    val emoji: String,
    val description: String,
    val optimalTimeRange: String
) {
    EARLY_BIRD(
        title = "Early Bird",
        emoji = "🌅",
        description = "Peak cognitive focus occurs during early morning hours. Ideal for heavy theory and difficult problem-solving.",
        optimalTimeRange = "05:00 – 10:30 AM"
    ),
    DAYLIGHT_ACHIEVER(
        title = "Daylight Achiever",
        emoji = "☀️",
        description = "Sustained executive alertness throughout midday and afternoon. Excellent for consistent revision schedules.",
        optimalTimeRange = "11:00 AM – 04:30 PM"
    ),
    NIGHT_OWL(
        title = "Night Owl",
        emoji = "🦉",
        description = "Deep flow state and minimal distraction tolerance in evening and night hours. Ideal for uninterrupted coding and practice.",
        optimalTimeRange = "06:00 PM – 01:00 AM"
    ),
    ADAPTIVE(
        title = "Adaptive Explorer",
        emoji = "⚖️",
        description = "Balanced focus evenly spread across the day. High versatility across dynamic study windows.",
        optimalTimeRange = "08:00 AM – 08:00 PM"
    )
}

data class ChronotypeResult(
    val chronotype: Chronotype,
    val confidence: String, // "High", "Moderate", "Establishing Pattern"
    val morningMinutes: Int,
    val afternoonMinutes: Int,
    val nightMinutes: Int,
    val summary: String
)

enum class LearningTechnique(
    val id: String,
    val title: String,
    val emoji: String,
    val summary: String,
    val recommendedIntervalMinutes: Int,
    val idealFor: String
) {
    ACTIVE_RECALL(
        id = "ACTIVE_RECALL",
        title = "Active Recall & Testing",
        emoji = "🧠",
        summary = "Read short sections, close notes, and actively retrieve key concepts from memory before checking answers.",
        recommendedIntervalMinutes = 30,
        idealFor = "High difficulty & conceptual units"
    ),
    FEYNMAN_METHOD(
        id = "FEYNMAN_METHOD",
        title = "Feynman Technique",
        emoji = "🗣️",
        summary = "Explain the topic in plain language as if teaching a beginner. Pinpoint gaps where jargon is used.",
        recommendedIntervalMinutes = 45,
        idealFor = "Complex theories & synthesis"
    ),
    POMODORO_CLASSIC(
        id = "POMODORO_CLASSIC",
        title = "Pomodoro Classic",
        emoji = "🍅",
        summary = "25 minutes of single-task immersion paired with 5 minutes of restorative mental rest.",
        recommendedIntervalMinutes = 25,
        idealFor = "Daily consistency & momentum"
    ),
    SPACED_BLURTING(
        id = "SPACED_BLURTING",
        title = "Spaced Blurting",
        emoji = "📝",
        summary = "Write down everything remembered in a timed sprint, then fill gaps using contrasting color notes.",
        recommendedIntervalMinutes = 20,
        idealFor = "Pre-exam rapid revision"
    )
}

enum class BurnoutRiskLevel(val label: String, val emoji: String) {
    OPTIMAL("Optimal Balance", "🟢"),
    MODERATE("Moderate Strain", "🟡"),
    HIGH("High Burnout Risk", "🔴")
}

data class BurnoutAssessment(
    val riskLevel: BurnoutRiskLevel,
    val recent7DaysHours: Float,
    val consecutiveHeavyDays: Int,
    val lateNightSessionsCount: Int,
    val indicators: List<String>,
    val recoveryRecommendation: String
)

data class StudyBuddyData(
    val name: String,
    val xp: Int,
    val streakDays: Int,
    val focusMinutes: Int,
    val examDaysRemaining: Int? = null
)

data class BuddyComparisonResult(
    val userAheadInXp: Boolean,
    val xpDifference: Int,
    val streakDifference: Int,
    val minutesDifference: Int,
    val encouragementMessage: String
)

object PersonalizationEngine {

    /**
     * Determines user chronotype based on historical attempt timestamps.
     */
    fun detectChronotype(attempts: List<StudyAttempt>): ChronotypeResult {
        if (attempts.size < 3) {
            return ChronotypeResult(
                chronotype = Chronotype.ADAPTIVE,
                confidence = "Establishing Pattern",
                morningMinutes = 0,
                afternoonMinutes = 0,
                nightMinutes = 0,
                summary = "Complete at least 3 study sessions to detect your natural cognitive peak."
            )
        }

        val cal = Calendar.getInstance()
        var morningMins = 0
        var afternoonMins = 0
        var nightMins = 0

        for (attempt in attempts) {
            cal.timeInMillis = attempt.startedAtUtc
            val hour = cal.get(Calendar.HOUR_OF_DAY)
            val durationMins = attempt.durationSeconds / 60

            when (hour) {
                in 5..10 -> morningMins += durationMins
                in 11..16 -> afternoonMins += durationMins
                in 17..23, in 0..4 -> nightMins += durationMins
            }
        }

        val totalMins = morningMins + afternoonMins + nightMins
        if (totalMins == 0) {
            return ChronotypeResult(
                chronotype = Chronotype.ADAPTIVE,
                confidence = "Establishing Pattern",
                morningMinutes = 0,
                afternoonMinutes = 0,
                nightMinutes = 0,
                summary = "Log study attempts to identify your optimal energy curve."
            )
        }

        val morningPct = morningMins.toFloat() / totalMins
        val afternoonPct = afternoonMins.toFloat() / totalMins
        val nightPct = nightMins.toFloat() / totalMins

        val dominant = when {
            morningPct >= 0.45f -> Chronotype.EARLY_BIRD
            nightPct >= 0.45f -> Chronotype.NIGHT_OWL
            afternoonPct >= 0.45f -> Chronotype.DAYLIGHT_ACHIEVER
            else -> Chronotype.ADAPTIVE
        }

        val confidence = when {
            attempts.size >= 8 -> "High"
            attempts.size >= 4 -> "Moderate"
            else -> "Establishing Pattern"
        }

        val summary = when (dominant) {
            Chronotype.EARLY_BIRD -> "You do ${(morningPct * 100).toInt()}% of your work before 11 AM. Morning focus yields your highest retention."
            Chronotype.NIGHT_OWL -> "You do ${(nightPct * 100).toInt()}% of your work in the evening/night. Late sessions are your natural flow state."
            Chronotype.DAYLIGHT_ACHIEVER -> "You do ${(afternoonPct * 100).toInt()}% of your work during midday. Daylight blocks keep you most consistent."
            Chronotype.ADAPTIVE -> "Your study sessions are evenly distributed. You adapt smoothly across different time slots."
        }

        return ChronotypeResult(
            chronotype = dominant,
            confidence = confidence,
            morningMinutes = morningMins,
            afternoonMinutes = afternoonMins,
            nightMinutes = nightMins,
            summary = summary
        )
    }

    /**
     * Recommends the ideal learning technique for a specific topic based on its attributes.
     */
    fun recommendTechniqueForTopic(topic: Topic): LearningTechnique {
        return when {
            topic.status == "revision" -> LearningTechnique.SPACED_BLURTING
            topic.difficulty == "hard" || topic.importance == "critical" -> LearningTechnique.ACTIVE_RECALL
            topic.estimatedEffortMinutes >= 90 -> LearningTechnique.FEYNMAN_METHOD
            else -> LearningTechnique.POMODORO_CLASSIC
        }
    }

    /**
     * Evaluates cognitive fatigue and burnout indicators from recent study patterns.
     */
    fun evaluateBurnoutRisk(attempts: List<StudyAttempt>): BurnoutAssessment {
        if (attempts.isEmpty()) {
            return BurnoutAssessment(
                riskLevel = BurnoutRiskLevel.OPTIMAL,
                recent7DaysHours = 0f,
                consecutiveHeavyDays = 0,
                lateNightSessionsCount = 0,
                indicators = listOf("Healthy study pacing"),
                recoveryRecommendation = "Pacing is balanced. Continue steady daily sessions."
            )
        }

        val now = System.currentTimeMillis()
        val sevenDaysAgo = now - (7 * 24 * 60 * 60 * 1000L)
        val recentAttempts = attempts.filter { it.startedAtUtc >= sevenDaysAgo }
        val recentTotalMins = recentAttempts.sumOf { it.durationSeconds / 60 }
        val recentHours = recentTotalMins / 60f

        val cal = Calendar.getInstance()
        var lateNightCount = 0
        val dailyMinutes = mutableMapOf<Int, Int>()

        for (attempt in recentAttempts) {
            cal.timeInMillis = attempt.startedAtUtc
            val dayOfYear = cal.get(Calendar.DAY_OF_YEAR)
            val hour = cal.get(Calendar.HOUR_OF_DAY)
            if (hour >= 23 || hour <= 3) {
                lateNightCount++
            }
            dailyMinutes[dayOfYear] = (dailyMinutes[dayOfYear] ?: 0) + (attempt.durationSeconds / 60)
        }

        // Count consecutive days with > 240 mins (4 hours)
        var maxConsecutiveHeavyDays = 0
        var currentHeavyStreak = 0
        val sortedDays = dailyMinutes.keys.sorted()
        var lastDay = -1

        for (day in sortedDays) {
            val mins = dailyMinutes[day] ?: 0
            if (mins >= 240) {
                if (lastDay != -1 && day == lastDay + 1) {
                    currentHeavyStreak++
                } else {
                    currentHeavyStreak = 1
                }
                if (currentHeavyStreak > maxConsecutiveHeavyDays) {
                    maxConsecutiveHeavyDays = currentHeavyStreak
                }
            } else {
                currentHeavyStreak = 0
            }
            lastDay = day
        }

        val indicators = mutableListOf<String>()
        if (maxConsecutiveHeavyDays >= 3) {
            indicators.add("$maxConsecutiveHeavyDays consecutive 4h+ study days")
        }
        if (lateNightCount >= 3) {
            indicators.add("$lateNightCount late-night sessions past 11:00 PM")
        }
        if (recentHours >= 30f) {
            indicators.add("Intense volume: ${String.format("%.1f", recentHours)} hours in 7 days")
        }

        val riskLevel = when {
            maxConsecutiveHeavyDays >= 4 || recentHours >= 35f || (maxConsecutiveHeavyDays >= 2 && lateNightCount >= 3) ->
                BurnoutRiskLevel.HIGH
            maxConsecutiveHeavyDays >= 2 || recentHours >= 22f || lateNightCount >= 2 ->
                BurnoutRiskLevel.MODERATE
            else ->
                BurnoutRiskLevel.OPTIMAL
        }

        val advice = when (riskLevel) {
            BurnoutRiskLevel.HIGH ->
                "High cognitive fatigue detected. Schedule a half-day recovery break and avoid late-night study to prevent exam burnout."
            BurnoutRiskLevel.MODERATE ->
                "Moderate workload. Ensure adequate 15-minute breaks and aim to finish before 10:30 PM tonight."
            BurnoutRiskLevel.OPTIMAL ->
                "Healthy sustainable pace. Your study cadence maintains steady progress without overload."
        }

        return BurnoutAssessment(
            riskLevel = riskLevel,
            recent7DaysHours = recentHours,
            consecutiveHeavyDays = maxConsecutiveHeavyDays,
            lateNightSessionsCount = lateNightCount,
            indicators = if (indicators.isEmpty()) listOf("Healthy sustainable study cadence") else indicators,
            recoveryRecommendation = advice
        )
    }

    /**
     * Generates a shareable study progress card with verified stats and an importable buddy token.
     */
    fun formatShareableStudyCard(
        userName: String,
        userXp: Int,
        streakDays: Int,
        totalMinutes: Int,
        examDaysRemaining: Int?,
        scholarRankTitle: String
    ): String {
        val hours = String.format("%.1f", totalMinutes / 60f)
        val examPart = if (examDaysRemaining != null) "$examDaysRemaining days until exam" else "Exam preparation in progress"

        return buildString {
            appendLine("📚 Focus Study — Accountability Check-in")
            appendLine("👤 $userName")
            appendLine("⚡ Scholar Rank: $scholarRankTitle ($userXp XP)")
            appendLine("🔥 Streak: $streakDays days active")
            appendLine("⏱️ Focus Time: $hours hours")
            appendLine("🎯 Target: $examPart")
            appendLine("Keep each other accountable! #FocusStudy #StudyBuddy")
            appendLine()
            append("[BUDDY-STATS:NAME=$userName:XP=$userXp:STREAK=$streakDays:MINS=$totalMinutes${if (examDaysRemaining != null) ":EXAM=$examDaysRemaining" else ""}]")
        }
    }

    /**
     * Parses an accountability partner's token from shared text or clipboard.
     * Format: [BUDDY-STATS:NAME=Alex:XP=850:STREAK=5:MINS=600:EXAM=14]
     */
    fun parseBuddyStatsToken(rawText: String): StudyBuddyData? {
        val pattern = Regex("""\[BUDDY-STATS:([^\]]+)\]""")
        val match = pattern.find(rawText) ?: return null
        val payload = match.groupValues[1]

        val map = payload.split(":").mapNotNull { part ->
            val split = part.split("=")
            if (split.size == 2) split[0].trim() to split[1].trim() else null
        }.toMap()

        val name = map["NAME"] ?: "Study Partner"
        val xp = map["XP"]?.toIntOrNull() ?: return null
        val streak = map["STREAK"]?.toIntOrNull() ?: 0
        val mins = map["MINS"]?.toIntOrNull() ?: 0
        val exam = map["EXAM"]?.toIntOrNull()

        return StudyBuddyData(
            name = name,
            xp = xp,
            streakDays = streak,
            focusMinutes = mins,
            examDaysRemaining = exam
        )
    }

    /**
     * Compares user progress with a study buddy to provide friendly motivation.
     */
    fun compareWithBuddy(
        userXp: Int,
        userStreak: Int,
        userMinutes: Int,
        buddy: StudyBuddyData
    ): BuddyComparisonResult {
        val xpDiff = userXp - buddy.xp
        val streakDiff = userStreak - buddy.streakDays
        val minsDiff = userMinutes - buddy.focusMinutes
        val userAhead = xpDiff >= 0

        val encouragement = when {
            xpDiff > 200 -> "You're leading ${buddy.name} by $xpDiff XP! Keep up the momentum and motivate them to catch up."
            xpDiff in 0..200 -> "You and ${buddy.name} are in a neck-and-neck study sprint! Every focus session counts."
            xpDiff in -200..-1 -> "${buddy.name} is just ${-xpDiff} XP ahead. A 45-minute study sprint will close the gap!"
            else -> "${buddy.name} has a strong lead (+${-xpDiff} XP). Lock in today's sessions to climb back up!"
        }

        return BuddyComparisonResult(
            userAheadInXp = userAhead,
            xpDifference = kotlin.math.abs(xpDiff),
            streakDifference = streakDiff,
            minutesDifference = minsDiff,
            encouragementMessage = encouragement
        )
    }
}
