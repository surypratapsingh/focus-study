package com.focusstudy.app.feature.progress

import com.focusstudy.app.core.database.AppDatabase
import com.focusstudy.app.core.database.entity.Achievement
import kotlinx.coroutines.flow.firstOrNull

object GamificationEngine {

    val DEFAULT_ACHIEVEMENTS = listOf(
        Achievement(
            id = "first_step",
            title = "First Study Block",
            description = "Completed your first focused study interval.",
            badgeIcon = "🎯"
        ),
        Achievement(
            id = "streak_3d",
            title = "Consistency Habit",
            description = "Maintained a 3-day uninterrupted study streak.",
            badgeIcon = "🔥"
        ),
        Achievement(
            id = "focus_5h",
            title = "Deep Focus Scholar",
            description = "Logged 5 total hours of distraction-free study.",
            badgeIcon = "⏳"
        ),
        Achievement(
            id = "coverage_25",
            title = "Syllabus Pioneer",
            description = "Completed 25% of all declared syllabus topics.",
            badgeIcon = "📘"
        ),
        Achievement(
            id = "coverage_50",
            title = "Halfway Milestone",
            description = "Achieved 50% overall exam syllabus coverage.",
            badgeIcon = "⭐"
        ),
        Achievement(
            id = "exam_ready",
            title = "Exam Champion",
            description = "Mastered 80% or more of syllabus topics.",
            badgeIcon = "🏆"
        )
    )

    data class ScholarRank(
        val level: Int,
        val rankTitle: String,
        val currentLevelXp: Int,
        val nextLevelXp: Int,
        val progressPercent: Float
    )

    fun calculateRank(totalXp: Int): ScholarRank {
        return when {
            totalXp < 150 -> ScholarRank(1, "Novice Scholar", totalXp, 150, (totalXp / 150f).coerceIn(0f, 1f))
            totalXp < 400 -> ScholarRank(2, "Apprentice Scholar", totalXp - 150, 250, ((totalXp - 150) / 250f).coerceIn(0f, 1f))
            totalXp < 800 -> ScholarRank(3, "Practitioner", totalXp - 400, 400, ((totalXp - 400) / 400f).coerceIn(0f, 1f))
            totalXp < 1500 -> ScholarRank(4, "Senior Scholar", totalXp - 800, 700, ((totalXp - 800) / 700f).coerceIn(0f, 1f))
            else -> ScholarRank(5, "Master Strategist", totalXp, totalXp, 1f)
        }
    }

    suspend fun evaluateAndUnlockAchievements(
        db: AppDatabase,
        totalStudyMinutes: Int,
        activeStreakDays: Int,
        syllabusCoveragePercent: Float
    ): List<String> = evaluateAndUnlockAchievements(
        progressDao = db.progressDao(),
        totalStudyMinutes = totalStudyMinutes,
        activeStreakDays = activeStreakDays,
        syllabusCoveragePercent = syllabusCoveragePercent
    )

    suspend fun evaluateAndUnlockAchievements(
        progressDao: com.focusstudy.app.core.database.dao.ProgressDao,
        totalStudyMinutes: Int,
        activeStreakDays: Int,
        syllabusCoveragePercent: Float
    ): List<String> {
        // Seed default achievements if table is empty
        val existing = progressDao.getAllAchievements().firstOrNull() ?: emptyList()
        if (existing.isEmpty()) {
            progressDao.insertDefaultAchievements(DEFAULT_ACHIEVEMENTS)
        }

        val newlyUnlocked = mutableListOf<String>()

        if (totalStudyMinutes >= 20) {
            progressDao.unlockAchievement("first_step")
            newlyUnlocked.add("first_step")
        }
        if (activeStreakDays >= 3) {
            progressDao.unlockAchievement("streak_3d")
            newlyUnlocked.add("streak_3d")
        }
        if (totalStudyMinutes >= 300) {
            progressDao.unlockAchievement("focus_5h")
            newlyUnlocked.add("focus_5h")
        }
        if (syllabusCoveragePercent >= 25f) {
            progressDao.unlockAchievement("coverage_25")
            newlyUnlocked.add("coverage_25")
        }
        if (syllabusCoveragePercent >= 50f) {
            progressDao.unlockAchievement("coverage_50")
            newlyUnlocked.add("coverage_50")
        }
        if (syllabusCoveragePercent >= 80f) {
            progressDao.unlockAchievement("exam_ready")
            newlyUnlocked.add("exam_ready")
        }

        return newlyUnlocked
    }
}
