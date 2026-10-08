package com.focusstudy.app

import com.focusstudy.app.core.database.dao.ProgressDao
import com.focusstudy.app.core.database.entity.Achievement
import com.focusstudy.app.core.database.entity.ActivityEvent
import com.focusstudy.app.core.database.entity.ProgressSnapshot
import com.focusstudy.app.feature.progress.GamificationEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class GamificationEngineTest {

    @Test
    fun calculateRank_evaluatesCorrectScholarTiers() {
        // Tier 1: Novice Scholar (0 - 149 XP)
        val rank0 = GamificationEngine.calculateRank(0)
        assertEquals(1, rank0.level)
        assertEquals("Novice Scholar", rank0.rankTitle)
        assertEquals(0, rank0.currentLevelXp)
        assertEquals(150, rank0.nextLevelXp)
        assertEquals(0.0f, rank0.progressPercent, 0.001f)

        val rank100 = GamificationEngine.calculateRank(100)
        assertEquals(1, rank100.level)
        assertEquals(100, rank100.currentLevelXp)
        assertEquals(100f / 150f, rank100.progressPercent, 0.001f)

        // Tier 2: Apprentice Scholar (150 - 399 XP)
        val rank150 = GamificationEngine.calculateRank(150)
        assertEquals(2, rank150.level)
        assertEquals("Apprentice Scholar", rank150.rankTitle)
        assertEquals(0, rank150.currentLevelXp)
        assertEquals(250, rank150.nextLevelXp)

        val rank275 = GamificationEngine.calculateRank(275)
        assertEquals(2, rank275.level)
        assertEquals(125, rank275.currentLevelXp)
        assertEquals(0.5f, rank275.progressPercent, 0.001f)

        // Tier 3: Practitioner (400 - 799 XP)
        val rank400 = GamificationEngine.calculateRank(400)
        assertEquals(3, rank400.level)
        assertEquals("Practitioner", rank400.rankTitle)
        assertEquals(0, rank400.currentLevelXp)

        // Tier 4: Senior Scholar (800 - 1499 XP)
        val rank800 = GamificationEngine.calculateRank(800)
        assertEquals(4, rank800.level)
        assertEquals("Senior Scholar", rank800.rankTitle)
        assertEquals(0, rank800.currentLevelXp)

        // Tier 5: Master Strategist (1500+ XP)
        val rank1500 = GamificationEngine.calculateRank(1500)
        assertEquals(5, rank1500.level)
        assertEquals("Master Strategist", rank1500.rankTitle)
        assertEquals(1.0f, rank1500.progressPercent, 0.001f)

        val rank3000 = GamificationEngine.calculateRank(3000)
        assertEquals(5, rank3000.level)
        assertEquals(1.0f, rank3000.progressPercent, 0.001f)
    }

    @Test
    fun defaultAchievements_containProperMetadata() {
        val badges = GamificationEngine.DEFAULT_ACHIEVEMENTS
        assertEquals(6, badges.size)

        val uniqueIds = badges.map { it.id }.toSet()
        assertEquals(6, uniqueIds.size)

        badges.forEach { badge ->
            assertTrue("Badge title must not be blank", badge.title.isNotBlank())
            assertTrue("Badge description must not be blank", badge.description.isNotBlank())
            assertTrue("Badge icon must not be blank", badge.badgeIcon.isNotBlank())
            assertNull("Initial badges must be locked", badge.unlockedAtUtc)
        }
    }

    @Test
    fun evaluateAndUnlockAchievements_unlocksThresholdsCorrectly() = runBlocking {
        val fakeDao = FakeProgressDao()

        // 1. Below thresholds: 10 mins study, 1 day streak, 10% coverage
        val unlockedInitial = GamificationEngine.evaluateAndUnlockAchievements(
            progressDao = fakeDao,
            totalStudyMinutes = 10,
            activeStreakDays = 1,
            syllabusCoveragePercent = 10.0f
        )
        assertTrue(unlockedInitial.isEmpty())

        // 2. Met first step (>= 20 mins) & pioneer coverage (>= 25%)
        val unlockedPioneer = GamificationEngine.evaluateAndUnlockAchievements(
            progressDao = fakeDao,
            totalStudyMinutes = 45,
            activeStreakDays = 1,
            syllabusCoveragePercent = 25.0f
        )
        assertTrue(unlockedPioneer.contains("first_step"))
        assertTrue(unlockedPioneer.contains("coverage_25"))
        assertFalse(unlockedPioneer.contains("streak_3d"))

        // 3. Met consistency habit (>= 3 days), 5 hours (>= 300 mins), and 50% coverage
        val unlockedMastery = GamificationEngine.evaluateAndUnlockAchievements(
            progressDao = fakeDao,
            totalStudyMinutes = 320,
            activeStreakDays = 4,
            syllabusCoveragePercent = 85.0f
        )
        assertTrue(unlockedMastery.contains("first_step"))
        assertTrue(unlockedMastery.contains("streak_3d"))
        assertTrue(unlockedMastery.contains("focus_5h"))
        assertTrue(unlockedMastery.contains("coverage_25"))
        assertTrue(unlockedMastery.contains("coverage_50"))
        assertTrue(unlockedMastery.contains("exam_ready"))

        // Verify state inside DAO
        val allBadges = fakeDao.achievements
        val unlockedBadges = allBadges.filter { it.unlockedAtUtc != null }
        assertEquals(6, unlockedBadges.size)
    }

    private class FakeProgressDao : ProgressDao {
        val achievements = mutableListOf<Achievement>()

        override fun getRecentSnapshots(): Flow<List<ProgressSnapshot>> = flowOf(emptyList())
        override suspend fun insertSnapshot(snapshot: ProgressSnapshot) {}
        override fun getRecentEvents(): Flow<List<ActivityEvent>> = flowOf(emptyList())
        override suspend fun insertEvent(event: ActivityEvent) {}

        override fun getAllAchievements(): Flow<List<Achievement>> = flowOf(achievements)

        override suspend fun insertDefaultAchievements(achievements: List<Achievement>) {
            this.achievements.addAll(achievements)
        }

        override suspend fun unlockAchievement(id: String, timestamp: Long) {
            val index = achievements.indexOfFirst { it.id == id }
            if (index != -1) {
                val current = achievements[index]
                if (current.unlockedAtUtc == null) {
                    achievements[index] = current.copy(unlockedAtUtc = timestamp)
                }
            }
        }
    }
}
