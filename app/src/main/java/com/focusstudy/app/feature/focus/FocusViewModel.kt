package com.focusstudy.app.feature.focus

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focusstudy.app.core.database.AppDatabase
import com.focusstudy.app.core.database.entity.*
import com.focusstudy.app.core.repository.StudyPlanRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID

data class FocusUiState(
    val activeSession: StudySession? = null,
    val activeTopic: Topic? = null,
    val activeSubject: Subject? = null,
    val totalSeconds: Int = 45 * 60,
    val remainingSeconds: Int = 45 * 60,
    val isRunning: Boolean = false,
    val isCompleted: Boolean = false,
    val completedMinutes: Int = 0,
    val earnedXp: Int = 0,
    val recentAttempts: List<StudyAttempt> = emptyList()
)

class FocusViewModel(
    private val db: AppDatabase,
    private val studyPlanRepository: StudyPlanRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(FocusUiState())
    val uiState: StateFlow<FocusUiState> = _uiState.asStateFlow()

    private var timerJob: Job? = null
    private var sessionStartTimeUtc: Long = 0L

    init {
        loadDefaultOrPendingSession()
        observeRecentAttempts()
    }

    private fun loadDefaultOrPendingSession() {
        viewModelScope.launch {
            val topics = db.syllabusDao().getAllTopics().firstOrNull() ?: emptyList()
            val firstTopic = topics.firstOrNull()

            if (firstTopic != null) {
                val subject = db.syllabusDao().getSubjectById(firstTopic.subjectId)
                _uiState.value = _uiState.value.copy(
                    activeTopic = firstTopic,
                    activeSubject = subject,
                    totalSeconds = firstTopic.estimatedEffortMinutes * 60,
                    remainingSeconds = firstTopic.estimatedEffortMinutes * 60
                )
            }
        }
    }

    private fun observeRecentAttempts() {
        viewModelScope.launch {
            db.studyAttemptDao().getAllAttempts().collect { attempts ->
                _uiState.value = _uiState.value.copy(recentAttempts = attempts)
            }
        }
    }

    fun setSession(session: StudySession, topic: Topic?, subject: Subject?) {
        timerJob?.cancel()
        val durationSec = session.durationMinutes * 60
        _uiState.value = _uiState.value.copy(
            activeSession = session,
            activeTopic = topic,
            activeSubject = subject,
            totalSeconds = durationSec,
            remainingSeconds = durationSec,
            isRunning = false,
            isCompleted = false
        )
    }

    fun startTimer() {
        if (_uiState.value.isRunning) return
        if (sessionStartTimeUtc == 0L) {
            sessionStartTimeUtc = System.currentTimeMillis()
        }

        _uiState.value = _uiState.value.copy(isRunning = true)
        timerJob = viewModelScope.launch {
            while (_uiState.value.remainingSeconds > 0) {
                delay(1000L)
                _uiState.value = _uiState.value.copy(
                    remainingSeconds = _uiState.value.remainingSeconds - 1
                )
            }
            // Timer reached 0
            completeSession()
        }
    }

    fun pauseTimer() {
        timerJob?.cancel()
        _uiState.value = _uiState.value.copy(isRunning = false)
    }

    fun finishEarly() {
        completeSession()
    }

    private fun completeSession() {
        timerJob?.cancel()
        val state = _uiState.value
        val elapsedSeconds = state.totalSeconds - state.remainingSeconds
        val completedMinutes = (elapsedSeconds / 60).coerceAtLeast(1)
        val xp = completedMinutes * 2

        viewModelScope.launch {
            val topicId = state.activeTopic?.id ?: "general_topic"
            val sessionId = state.activeSession?.id

            // 1. Record StudyAttempt in Room
            val attempt = StudyAttempt(
                id = UUID.randomUUID().toString(),
                sessionId = sessionId,
                topicId = topicId,
                startedAtUtc = if (sessionStartTimeUtc > 0L) sessionStartTimeUtc else System.currentTimeMillis() - elapsedSeconds * 1000L,
                endedAtUtc = System.currentTimeMillis(),
                durationSeconds = elapsedSeconds,
                interrupted = state.remainingSeconds > 0,
                notes = "${state.activeSubject?.name ?: "Subject"} · ${state.activeTopic?.name ?: "Topic"}"
            )
            db.studyAttemptDao().insertAttempt(attempt)

            // 2. Mark StudySession completed and update topic progress
            if (sessionId != null) {
                studyPlanRepository.completeSession(sessionId, completedMinutes)
            } else if (state.activeTopic != null) {
                val topic = state.activeTopic
                db.syllabusDao().updateTopic(
                    topic.copy(
                        actualMinutes = topic.actualMinutes + completedMinutes,
                        lastStudiedAtUtc = System.currentTimeMillis()
                    )
                )
            }

            // 3. Record ActivityEvent
            db.progressDao().insertEvent(
                ActivityEvent(
                    eventType = "session_finished",
                    description = "Completed $completedMinutes min on ${state.activeTopic?.name ?: "Topic"}"
                )
            )

            _uiState.value = _uiState.value.copy(
                isRunning = false,
                isCompleted = true,
                completedMinutes = completedMinutes,
                earnedXp = xp
            )
        }
    }

    fun resetSession() {
        sessionStartTimeUtc = 0L
        _uiState.value = _uiState.value.copy(
            remainingSeconds = _uiState.value.totalSeconds,
            isCompleted = false,
            isRunning = false
        )
    }
}
