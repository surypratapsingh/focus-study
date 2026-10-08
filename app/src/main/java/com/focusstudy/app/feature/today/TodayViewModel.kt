package com.focusstudy.app.feature.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focusstudy.app.core.ai.AiInsightService
import com.focusstudy.app.core.database.AppDatabase
import com.focusstudy.app.core.database.entity.*
import com.focusstudy.app.core.repository.ProgressRepository
import com.focusstudy.app.core.repository.StudyPlanRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

data class TodayUiState(
    val exam: Exam? = null,
    val daysRemaining: Int = 0,
    val todayPlanDay: StudyPlanDay? = null,
    val targetMinutes: Int = 180,
    val completedMinutes: Int = 0,
    val progressPercent: Float = 0f,
    val sessions: List<StudySession> = emptyList(),
    val nextSession: StudySession? = null,
    val nextTopic: Topic? = null,
    val nextSubject: Subject? = null,
    val topicsMap: Map<String, Topic> = emptyMap(),
    val subjectsMap: Map<String, Subject> = emptyMap(),
    val atRiskTopics: List<Topic> = emptyList(),
    val dailyBrief: AiInsight? = null,
    val isGeneratingBrief: Boolean = false
)

class TodayViewModel(
    private val db: AppDatabase,
    private val studyPlanRepository: StudyPlanRepository,
    private val progressRepository: ProgressRepository,
    private val aiInsightService: AiInsightService = AiInsightService(db)
) : ViewModel() {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val todayDateString: String = dateFormat.format(Date())

    private val _uiState = MutableStateFlow(TodayUiState())
    val uiState: StateFlow<TodayUiState> = _uiState.asStateFlow()

    init {
        observeTodayData()
        observeDailyBrief()
    }

    private fun observeDailyBrief() {
        viewModelScope.launch {
            aiInsightService.getAllInsights().collect { insights ->
                val latestBrief = insights.firstOrNull { it.category == "daily_brief" }
                _uiState.value = _uiState.value.copy(dailyBrief = latestBrief)
            }
        }
    }

    private fun observeTodayData() {
        viewModelScope.launch {
            // 1. Observe primary exam
            db.examDao().getPrimaryExam().collect { exam ->
                if (exam != null) {
                    val daysRemaining = (((exam.examDateUtc - System.currentTimeMillis()) / (1000L * 60 * 60 * 24)).toInt()).coerceAtLeast(1)

                    // 2. Observe topics & subjects
                    val topics = db.syllabusDao().getAllTopics().firstOrNull() ?: emptyList()
                    val topicsMap = topics.associateBy { it.id }

                    val subjects = db.syllabusDao().getSubjectsForExam(exam.id).firstOrNull() ?: emptyList()
                    val subjectsMap = subjects.associateBy { it.id }

                    // 3. At-risk topics (neglected or hard + incomplete)
                    val atRisk = topics.filter {
                        it.status != "completed" && (it.importance == "critical" || it.importance == "high" || it.difficulty == "hard")
                    }.take(3)

                    // 4. Observe active plan
                    db.studyPlanDao().getActivePlan(exam.id).collect { plan ->
                        if (plan != null) {
                            val planDay = db.studyPlanDao().getPlanDay(plan.id, todayDateString)

                            // 5. Observe sessions for today
                            db.studyPlanDao().getSessionsForDate(todayDateString).collect { sessions ->
                                val totalCompleted = sessions.filter { it.isCompleted }.sumOf { it.completedDurationMinutes }
                                val target = planDay?.targetMinutes ?: sessions.sumOf { it.durationMinutes }.coerceAtLeast(60)
                                val progress = if (target > 0) (totalCompleted.toFloat() / target.toFloat()).coerceIn(0f, 1f) else 0f

                                val nextSession = sessions.firstOrNull { !it.isCompleted }
                                val nextTopic = nextSession?.let { topicsMap[it.topicId] }
                                val nextSubject = nextSession?.let { subjectsMap[it.subjectId] }

                                _uiState.value = _uiState.value.copy(
                                    exam = exam,
                                    daysRemaining = daysRemaining,
                                    todayPlanDay = planDay,
                                    targetMinutes = target,
                                    completedMinutes = totalCompleted,
                                    progressPercent = progress,
                                    sessions = sessions,
                                    nextSession = nextSession,
                                    nextTopic = nextTopic,
                                    nextSubject = nextSubject,
                                    topicsMap = topicsMap,
                                    subjectsMap = subjectsMap,
                                    atRiskTopics = atRisk
                                )

                                // Auto-generate daily brief if not already present
                                if (_uiState.value.dailyBrief == null && !_uiState.value.isGeneratingBrief) {
                                    generateDailyBrief(exam, sessions, atRisk, target)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    fun generateDailyBrief(
        exam: Exam? = _uiState.value.exam,
        sessions: List<StudySession> = _uiState.value.sessions,
        atRisk: List<Topic> = _uiState.value.atRiskTopics,
        target: Int = _uiState.value.targetMinutes
    ) {
        val activeExam = exam ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isGeneratingBrief = true)
            val brief = aiInsightService.generateDailyBrief(
                exam = activeExam,
                todaySessions = sessions,
                atRiskTopics = atRisk,
                peakWindow = "Morning (07:00 - 10:00)",
                todayTargetMinutes = target
            )
            _uiState.value = _uiState.value.copy(
                dailyBrief = brief,
                isGeneratingBrief = false
            )
        }
    }

    fun markSessionCompleted(sessionId: String, durationMinutes: Int) {
        viewModelScope.launch {
            studyPlanRepository.completeSession(sessionId, durationMinutes)
        }
    }
}
