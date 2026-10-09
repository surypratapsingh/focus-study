package com.focusstudy.app.feature.planner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focusstudy.app.core.ai.*
import com.focusstudy.app.core.database.AppDatabase
import com.focusstudy.app.core.database.entity.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

data class PlannerUiState(
    val exam: Exam? = null,
    val activePlan: StudyPlan? = null,
    val selectedDate: String = "",
    val todayDate: String = "",
    val daysUntilExam: Int = 0,
    val phases: List<MacroPhase> = emptyList(),
    val weekDays: List<StudyPlanDay> = emptyList(),
    val daySessions: List<StudySession> = emptyList(),
    val allSessions: List<StudySession> = emptyList(),
    val topicsMap: Map<String, Topic> = emptyMap(),
    val subjectsMap: Map<String, Subject> = emptyMap(),
    val isOverloaded: Boolean = false,
    val requiredDailyMinutes: Int = 0,
    val isRegenerating: Boolean = false,
    val planExplanation: String = "",
    val missedSessions: List<StudySession> = emptyList(),
    val scheduleRisk: ScheduleRiskAssessment? = null,
    val replanProposal: AiReplanProposal? = null,
    val isReplanDialogVisible: Boolean = false,
    val planVersions: List<AiPlanVersion> = emptyList()
)

class PlannerViewModel(
    private val db: AppDatabase,
    private val aiPlannerService: AiPlannerService = AiPlannerService(db),
    private val aiReplanService: AiReplanService = AiReplanService(db)
) : ViewModel() {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val todayString = dateFormat.format(Date())

    private val _selectedDate = MutableStateFlow(todayString)
    private val _uiState = MutableStateFlow(
        PlannerUiState(
            selectedDate = todayString,
            todayDate = todayString
        )
    )
    val uiState: StateFlow<PlannerUiState> = _uiState.asStateFlow()

    init {
        loadPlannerData()
    }

    private fun loadPlannerData() {
        // Coroutine 1: Observe primary exam and calculate schedule risk and phases
        viewModelScope.launch {
            db.examDao().getPrimaryExam().collect { exam ->
                if (exam != null) {
                    val daysRemaining = (((exam.examDateUtc - System.currentTimeMillis()) / (1000L * 60 * 60 * 24)).toInt()).coerceAtLeast(1)
                    val phases = PlannerEngine.generateMacroPhases(daysRemaining)

                    val topics = db.syllabusDao().getAllTopics().firstOrNull() ?: emptyList()
                    val topicsMap = topics.associateBy { it.id }

                    val subjects = db.syllabusDao().getSubjectsForExam(exam.id).firstOrNull() ?: emptyList()
                    val subjectsMap = subjects.associateBy { it.id }

                    val risk = aiReplanService.calculateScheduleRisk(
                        exam = exam,
                        allTopics = topics,
                        remainingDays = daysRemaining,
                        availableDailyMinutes = 180
                    )

                    _uiState.update { current ->
                        current.copy(
                            exam = exam,
                            daysUntilExam = daysRemaining,
                            phases = phases,
                            topicsMap = topicsMap,
                            subjectsMap = subjectsMap,
                            scheduleRisk = risk
                        )
                    }
                }
            }
        }

        // Coroutine 2: Observe active plan and plan versions / explanation
        viewModelScope.launch {
            db.examDao().getPrimaryExam().collectLatest { exam ->
                if (exam != null) {
                    db.studyPlanDao().getActivePlan(exam.id).collectLatest { plan ->
                        _uiState.update { it.copy(activePlan = plan) }
                        if (plan != null) {
                            val topics = db.syllabusDao().getAllTopics().firstOrNull() ?: emptyList()
                            val daysRemaining = (((exam.examDateUtc - System.currentTimeMillis()) / (1000L * 60 * 60 * 24)).toInt()).coerceAtLeast(1)
                            val explanation = aiPlannerService.generatePlanExplanation(exam, topics, daysRemaining, plan)
                            _uiState.update { it.copy(planExplanation = explanation) }

                            launch {
                                aiPlannerService.getPlanVersions(plan.id).collect { versions ->
                                    _uiState.update { it.copy(planVersions = versions) }
                                }
                            }

                            launch {
                                db.studyPlanDao().getPlanDays(plan.id).collect { planDays ->
                                    _uiState.update { it.copy(weekDays = planDays.take(7)) }
                                }
                            }

                            launch {
                                db.studyPlanDao().getAllSessions().collect { all ->
                                    _uiState.update { it.copy(allSessions = all) }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Coroutine 3: Observe sessions for the currently selected date
        viewModelScope.launch {
            _selectedDate.collectLatest { selDate ->
                db.studyPlanDao().getSessionsForDate(selDate).collect { sessions ->
                    val missed = sessions.filter { it.scheduledDate < todayString && !it.isCompleted }
                    _uiState.update { current ->
                        current.copy(
                            selectedDate = selDate,
                            daySessions = sessions,
                            missedSessions = missed
                        )
                    }
                }
            }
        }
    }

    fun selectDate(date: String) {
        _selectedDate.value = date
        _uiState.update { it.copy(selectedDate = date) }
    }

    fun toggleSessionLock(sessionId: String) {
        viewModelScope.launch {
            val session = db.studyPlanDao().getSessionById(sessionId)
            if (session != null) {
                db.studyPlanDao().updateSession(session.copy(isLocked = !session.isLocked))
            }
        }
    }

    fun deleteSession(sessionId: String) {
        viewModelScope.launch {
            val session = db.studyPlanDao().getSessionById(sessionId)
            if (session != null) {
                db.studyPlanDao().deleteSession(session)
            }
        }
    }

    fun regeneratePlan(weekdayMinutes: Int = 180, weekendMinutes: Int = 300) {
        viewModelScope.launch {
            _uiState.update { it.copy(isRegenerating = true) }
            val exam = _uiState.value.exam ?: db.examDao().getPrimaryExam().firstOrNull() ?: return@launch
            val topics = db.syllabusDao().getAllTopics().firstOrNull() ?: return@launch

            val result = PlannerEngine.generateDeterministicPlan(
                exam = exam,
                topics = topics,
                weekdayMinutes = weekdayMinutes,
                weekendMinutes = weekendMinutes
            )

            // Archive old active plans first to ensure clean state
            db.studyPlanDao().archiveActivePlans(exam.id)

            // Save new plan and sessions
            db.studyPlanDao().insertPlan(result.plan)
            db.studyPlanDao().insertPlanDays(result.days)
            db.studyPlanDao().insertSessions(result.sessions)

            // Record plan version
            aiPlannerService.savePlanVersion(
                planId = result.plan.id,
                versionNumber = 1,
                summaryOfChanges = "Comprehensive plan generation with ${result.sessions.size} sessions across ${result.days.size} days.",
                rawDiffJson = "[]"
            )

            _uiState.update { current ->
                current.copy(
                    activePlan = result.plan,
                    isOverloaded = result.isOverloaded,
                    requiredDailyMinutes = result.requiredPaceMinutesPerDay,
                    isRegenerating = false
                )
            }
        }
    }

    fun openReplanProposal() {
        val exam = _uiState.value.exam ?: return
        val topics = _uiState.value.topicsMap.values.toList()
        val missed = _uiState.value.missedSessions
        val atRisk = topics.filter { it.status != "completed" && (it.difficulty == "hard" || it.importance == "critical") }

        val proposal = aiReplanService.generateReplanProposal(
            exam = exam,
            missedSessions = missed,
            atRiskTopics = atRisk,
            existingSessions = _uiState.value.daySessions,
            todayStr = todayString
        )

        _uiState.value = _uiState.value.copy(
            replanProposal = proposal,
            isReplanDialogVisible = true
        )
    }

    fun dismissReplanDialog() {
        _uiState.value = _uiState.value.copy(isReplanDialogVisible = false)
    }

    fun applyReplanProposal() {
        val proposal = _uiState.value.replanProposal ?: return
        val plan = _uiState.value.activePlan ?: return

        viewModelScope.launch {
            aiReplanService.applyReplan(proposal, plan.id)
            _uiState.value = _uiState.value.copy(
                isReplanDialogVisible = false,
                replanProposal = null
            )
        }
    }
}
