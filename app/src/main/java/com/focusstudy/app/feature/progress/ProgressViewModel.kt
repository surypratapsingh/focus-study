package com.focusstudy.app.feature.progress

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focusstudy.app.core.ai.AiInsightService
import com.focusstudy.app.core.database.AppDatabase
import com.focusstudy.app.core.database.entity.StudyAttempt
import com.focusstudy.app.core.database.entity.StudyPlanDay
import com.focusstudy.app.core.database.entity.Subject
import com.focusstudy.app.core.database.entity.Topic
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class ProgressUiState(
    val selectedFilter: String = "7d", // "7d", "30d", "Exam"
    val syllabusCoveragePercent: Float = 0f,
    val totalTopicsCount: Int = 0,
    val completedTopicsCount: Int = 0,
    val streakData: StreakData = StreakData(0, 0, 0),
    val totalStudyMinutes: Int = 0,
    val subjectProgressList: List<SubjectProgressData> = emptyList(),
    val plannedVsActual: PlannedVsActualData = PlannedVsActualData(0, 0, 0f, emptyList()),
    val topAndNeglected: TopAndNeglectedData = TopAndNeglectedData("N/A", 0, "N/A", 0, 0),
    val peakWindow: PeakWindowResult = PeakWindowResult("07:00 – 09:00 AM", 45, 0, 0.85f, "None", ""),
    val heatmapSlots: List<HeatmapSlot> = emptyList(),
    val metricsExplanation: String = "",
    val peakWindowExplanation: String = "",
    val totalXp: Int = 0,
    val scholarRank: GamificationEngine.ScholarRank = GamificationEngine.ScholarRank(1, "Novice Scholar", 0, 150, 0f),
    val achievements: List<com.focusstudy.app.core.database.entity.Achievement> = GamificationEngine.DEFAULT_ACHIEVEMENTS,
    val chronotypeResult: ChronotypeResult = ChronotypeResult(Chronotype.ADAPTIVE, "Establishing Pattern", 0, 0, 0, "Log study attempts to identify your optimal energy curve."),
    val burnoutAssessment: BurnoutAssessment = BurnoutAssessment(BurnoutRiskLevel.OPTIMAL, 0f, 0, 0, listOf("Healthy sustainable cadence"), "Pacing is balanced."),
    val studyBuddyData: StudyBuddyData? = null,
    val buddyComparison: BuddyComparisonResult? = null,
    val examDaysRemaining: Int? = null
)

class ProgressViewModel(
    private val db: AppDatabase,
    private val aiInsightService: AiInsightService = AiInsightService(db),
    private val userPreferencesManager: com.focusstudy.app.core.datastore.UserPreferencesManager? = null
) : ViewModel() {

    private val _selectedFilter = MutableStateFlow("7d")
    private val _uiState = MutableStateFlow(ProgressUiState())
    val uiState: StateFlow<ProgressUiState> = _uiState.asStateFlow()

    init {
        observeAnalyticsData()
    }

    private fun observeAnalyticsData() {
        viewModelScope.launch {
            // Seed default achievements if empty
            val initial = db.progressDao().getAllAchievements().firstOrNull() ?: emptyList()
            if (initial.isEmpty()) {
                db.progressDao().insertDefaultAchievements(GamificationEngine.DEFAULT_ACHIEVEMENTS)
            }

            val prefsFlow = userPreferencesManager?.userPreferencesFlow ?: flowOf(null)

            // Combine all required data streams
            combine(
                db.syllabusDao().getAllTopics(),
                db.studyAttemptDao().getAllAttempts(),
                db.progressDao().getAllAchievements(),
                _selectedFilter,
                prefsFlow
            ) { topics, attempts, achievements, filter, prefs ->
                val exam = db.examDao().getAllExams().firstOrNull()?.firstOrNull()
                val subjects = if (exam != null)
                    db.syllabusDao().getSubjectsForExam(exam.id).firstOrNull() ?: emptyList()
                else emptyList()

                val plan = if (exam != null)
                    db.studyPlanDao().getActivePlan(exam.id).firstOrNull()
                else null

                val planDays = if (plan != null)
                    db.studyPlanDao().getPlanDays(plan.id).firstOrNull() ?: emptyList()
                else emptyList()

                val daysLeft = exam?.let {
                    (((it.examDateUtc - System.currentTimeMillis()) / (1000L * 60 * 60 * 24)).toInt()).coerceAtLeast(1)
                }

                computeUiState(topics, attempts, subjects, planDays, achievements, filter, prefs, daysLeft)
            }.collect { newState ->
                _uiState.value = newState
            }
        }
    }

    private fun computeUiState(
        topics: List<Topic>,
        attempts: List<StudyAttempt>,
        subjects: List<Subject>,
        planDays: List<StudyPlanDay>,
        achievements: List<com.focusstudy.app.core.database.entity.Achievement>,
        filter: String,
        prefs: com.focusstudy.app.core.datastore.UserPreferences?,
        daysUntilExam: Int?
    ): ProgressUiState {
        val windowDays = when (filter) {
            "30d" -> 30
            "Exam" -> 45
            else -> 7
        }

        val coverage = AnalyticsEngine.calculateSyllabusCoverage(topics)
        val completedTopics = topics.count { it.status == "completed" }
        val streaks = AnalyticsEngine.calculateStreaks(attempts)
        val totalMins = attempts.sumOf { it.durationSeconds / 60 }
        val subjectProgress = AnalyticsEngine.calculateSubjectProgress(subjects, topics)
        val plannedVsActual = AnalyticsEngine.calculatePlannedVsActual(planDays, attempts, windowDays)
        val topNeglected = AnalyticsEngine.detectTopAndNeglectedSubjects(subjects, topics, attempts)
        val peakWindow = AnalyticsEngine.detectPeakStudyWindow(attempts)
        val heatmap = AnalyticsEngine.generateHeatmapGrid(attempts)

        val metricsExpl = aiInsightService.explainMetrics(coverage, streaks.currentStreakDays, topNeglected.neglectedTopicCount)
        val peakExpl = aiInsightService.explainPeakWindow(peakWindow.windowName, peakWindow.completionRatio)

        val chronotype = PersonalizationEngine.detectChronotype(attempts)
        val burnout = PersonalizationEngine.evaluateBurnoutRisk(attempts)

        // Asynchronously evaluate and unlock achievements based on progress metrics
        viewModelScope.launch {
            GamificationEngine.evaluateAndUnlockAchievements(
                db = db,
                totalStudyMinutes = totalMins,
                activeStreakDays = streaks.currentStreakDays,
                syllabusCoveragePercent = coverage
            )
        }

        val effectiveBadges = if (achievements.isEmpty()) GamificationEngine.DEFAULT_ACHIEVEMENTS else achievements
        val unlockedCount = effectiveBadges.count { it.unlockedAtUtc != null }
        val totalXp = (totalMins * 2) + (unlockedCount * 50)
        val rank = GamificationEngine.calculateRank(totalXp)

        val buddyData = if (!prefs?.studyBuddyName.isNullOrBlank()) {
            StudyBuddyData(
                name = prefs!!.studyBuddyName,
                xp = prefs.studyBuddyXp,
                streakDays = prefs.studyBuddyStreak,
                focusMinutes = prefs.studyBuddyMinutes
            )
        } else null

        val buddyComp = buddyData?.let {
            PersonalizationEngine.compareWithBuddy(
                userXp = totalXp,
                userStreak = streaks.currentStreakDays,
                userMinutes = totalMins,
                buddy = it
            )
        }

        return ProgressUiState(
            selectedFilter = filter,
            syllabusCoveragePercent = coverage,
            totalTopicsCount = topics.size,
            completedTopicsCount = completedTopics,
            streakData = streaks,
            totalStudyMinutes = totalMins,
            subjectProgressList = subjectProgress,
            plannedVsActual = plannedVsActual,
            topAndNeglected = topNeglected,
            peakWindow = peakWindow,
            heatmapSlots = heatmap,
            metricsExplanation = metricsExpl,
            peakWindowExplanation = peakExpl,
            totalXp = totalXp,
            scholarRank = rank,
            achievements = effectiveBadges,
            chronotypeResult = chronotype,
            burnoutAssessment = burnout,
            studyBuddyData = buddyData,
            buddyComparison = buddyComp,
            examDaysRemaining = daysUntilExam
        )
    }

    fun selectFilter(filter: String) {
        _selectedFilter.value = filter
        _uiState.value = _uiState.value.copy(selectedFilter = filter)
    }

    fun saveStudyBuddy(buddy: StudyBuddyData) {
        viewModelScope.launch {
            userPreferencesManager?.setStudyBuddy(
                name = buddy.name,
                xp = buddy.xp,
                streak = buddy.streakDays,
                minutes = buddy.focusMinutes
            )
        }
    }

    fun clearStudyBuddy() {
        viewModelScope.launch {
            userPreferencesManager?.clearStudyBuddy()
        }
    }
}
