package com.focusstudy.app.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focusstudy.app.core.database.AppDatabase
import com.focusstudy.app.core.database.entity.*
import com.focusstudy.app.core.datastore.UserPreferencesManager
import com.focusstudy.app.feature.syllabus.ParsedTopicItem
import com.focusstudy.app.feature.syllabus.SyllabusParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.focusstudy.app.core.security.FileImportValidator
import com.focusstudy.app.core.ai.AiSyllabusParser
import java.text.SimpleDateFormat
import java.util.*

data class OnboardingUiState(
    val currentStep: Int = 1,
    val examTitle: String = "Semester Finals",
    val examType: String = "University Exam",
    val daysRemaining: Int = 42,
    val isTentativeDate: Boolean = false,
    val targetScore: Int = 85,
    val dailyWeekdayHours: Float = 3.0f,
    val dailyWeekendHours: Float = 5.0f,
    val preferredStudyWindow: String = "Morning (07:00 - 10:00)",
    val sessionDurationMinutes: Int = 45,
    val breakDurationMinutes: Int = 10,
    val studyIntensity: String = "Balanced",
    val syllabusRawText: String = "",
    val parsedTopics: List<ParsedTopicItem> = emptyList(),
    val isBuildingPlan: Boolean = false,
    val isCompleted: Boolean = false,
    val isImporting: Boolean = false,
    val importErrorMessage: String? = null,
    val importSuccessMessage: String? = null
)

class OnboardingViewModel(
    private val db: AppDatabase,
    private val preferencesManager: UserPreferencesManager,
    private val aiSyllabusParser: AiSyllabusParser = AiSyllabusParser(db, preferencesManager)
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    init {
        // Pre-fill with a rich sample syllabus
        val sample = SyllabusParser.sampleComputerScienceSyllabus()
        val parsed = SyllabusParser.parseText(sample)
        _uiState.value = _uiState.value.copy(
            syllabusRawText = sample,
            parsedTopics = parsed
        )
    }

    fun nextStep() {
        if (_uiState.value.currentStep < 6) {
            _uiState.value = _uiState.value.copy(currentStep = _uiState.value.currentStep + 1)
        }
    }

    fun previousStep() {
        if (_uiState.value.currentStep > 1) {
            _uiState.value = _uiState.value.copy(currentStep = _uiState.value.currentStep - 1)
        }
    }

    fun setExamType(type: String) {
        _uiState.value = _uiState.value.copy(examType = type)
    }

    fun setExamTitle(title: String) {
        _uiState.value = _uiState.value.copy(examTitle = title)
    }

    fun setDaysRemaining(days: Int) {
        _uiState.value = _uiState.value.copy(daysRemaining = days.coerceAtLeast(1))
    }

    fun setTentativeDate(isTentative: Boolean) {
        _uiState.value = _uiState.value.copy(isTentativeDate = isTentative)
    }

    fun setStudyHours(weekday: Float, weekend: Float) {
        _uiState.value = _uiState.value.copy(dailyWeekdayHours = weekday, dailyWeekendHours = weekend)
    }

    fun setStudyWindow(window: String) {
        _uiState.value = _uiState.value.copy(preferredStudyWindow = window)
    }

    fun setSessionPreference(sessionMin: Int, breakMin: Int) {
        _uiState.value = _uiState.value.copy(
            sessionDurationMinutes = sessionMin,
            breakDurationMinutes = breakMin
        )
    }

    fun setStudyIntensity(intensity: String) {
        _uiState.value = _uiState.value.copy(studyIntensity = intensity)
    }

    fun updateSyllabusText(text: String) {
        val parsed = SyllabusParser.parseText(text, _uiState.value.examTitle)
        _uiState.value = _uiState.value.copy(
            syllabusRawText = text,
            parsedTopics = parsed
        )
    }

    fun removeTopic(topicId: String) {
        val updated = _uiState.value.parsedTopics.filterNot { it.id == topicId }
        _uiState.value = _uiState.value.copy(parsedTopics = updated)
    }

    fun updateTopicDifficulty(topicId: String, difficulty: String) {
        val updated = _uiState.value.parsedTopics.map {
            if (it.id == topicId) it.copy(difficulty = difficulty) else it
        }
        _uiState.value = _uiState.value.copy(parsedTopics = updated)
    }

    fun clearImportMessages() {
        _uiState.value = _uiState.value.copy(importErrorMessage = null, importSuccessMessage = null)
    }

    fun importContentFromUri(context: Context, uri: Uri) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isImporting = true, importErrorMessage = null, importSuccessMessage = null)
            try {
                val contentResolver = context.contentResolver
                val mimeType = contentResolver.getType(uri) ?: "application/octet-stream"

                var fileName = "imported_document"
                var sizeBytes = 0L

                contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (nameIndex != -1) {
                            fileName = cursor.getString(nameIndex) ?: fileName
                        }
                        val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (sizeIndex != -1) {
                            sizeBytes = cursor.getLong(sizeIndex)
                        }
                    }
                }

                // 1. Validate file metadata (MIME, 15MB limit)
                val metaValidation = FileImportValidator.validateFileMetadata(mimeType, sizeBytes)
                if (!metaValidation.isValid) {
                    _uiState.value = _uiState.value.copy(
                        isImporting = false,
                        importErrorMessage = metaValidation.errorMessage ?: "Invalid file format or size limit exceeded."
                    )
                    return@launch
                }

                // 2. Read bytes and parse via AiSyllabusParser (native PDF extractor + optional Gemini multimodal)
                contentResolver.openInputStream(uri)?.use { inputStream ->
                    val bytes = inputStream.readBytes()
                    val parseResult = aiSyllabusParser.parseDocument(
                        bytes = bytes,
                        mimeType = mimeType,
                        examTitle = _uiState.value.examTitle
                    )

                    if (!parseResult.isValid || parseResult.topics.isEmpty()) {
                        _uiState.value = _uiState.value.copy(
                            isImporting = false,
                            importErrorMessage = parseResult.message.ifBlank { "No structured topics detected. Please check file format." }
                        )
                    } else {
                        _uiState.value = _uiState.value.copy(
                            isImporting = false,
                            syllabusRawText = parseResult.extractedRawText.ifBlank { _uiState.value.syllabusRawText },
                            parsedTopics = parseResult.topics,
                            importSuccessMessage = "${parseResult.message} ($fileName)"
                        )
                    }
                } ?: run {
                    _uiState.value = _uiState.value.copy(
                        isImporting = false,
                        importErrorMessage = "Could not open selected file."
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isImporting = false,
                    importErrorMessage = "Import error: ${e.localizedMessage ?: "Unknown error"}"
                )
            }
        }
    }

    fun completeOnboardingAndBuildPlan() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isBuildingPlan = true)

            val state = _uiState.value
            val examDateUtc = System.currentTimeMillis() + (state.daysRemaining.toLong() * 24 * 60 * 60 * 1000L)

            // 1. Insert Exam
            val exam = Exam(
                title = state.examTitle,
                examType = state.examType,
                examDateUtc = examDateUtc,
                isTentative = state.isTentativeDate,
                targetScore = state.targetScore
            )
            db.examDao().insertExam(exam)

            // 2. Group topics by Subject and Unit
            val subjectNames = state.parsedTopics.map { it.subjectName }.distinct().ifEmpty { listOf(state.examTitle) }
            val subjects = subjectNames.mapIndexed { index, name ->
                Subject(
                    id = UUID.randomUUID().toString(),
                    examId = exam.id,
                    name = name,
                    displayOrder = index,
                    colorHex = when (index % 4) {
                        0 -> "#2563EB"
                        1 -> "#0F766E"
                        2 -> "#7C3AED"
                        else -> "#D97706"
                    }
                )
            }
            db.syllabusDao().insertSubjects(subjects)

            val unitEntities = mutableListOf<UnitEntity>()
            val topicEntities = mutableListOf<Topic>()

            for (subject in subjects) {
                val subjectTopics = state.parsedTopics.filter { it.subjectName == subject.name }
                val unitTitles = subjectTopics.map { it.unitTitle }.distinct().ifEmpty { listOf("Core Topics") }

                for ((uIdx, unitTitle) in unitTitles.withIndex()) {
                    val unit = UnitEntity(
                        id = UUID.randomUUID().toString(),
                        subjectId = subject.id,
                        title = unitTitle,
                        displayOrder = uIdx
                    )
                    unitEntities.add(unit)

                    val unitTopics = subjectTopics.filter { it.unitTitle == unitTitle }
                    for ((tIdx, parsed) in unitTopics.withIndex()) {
                        topicEntities.add(
                            Topic(
                                id = UUID.randomUUID().toString(),
                                unitId = unit.id,
                                subjectId = subject.id,
                                name = parsed.name,
                                estimatedEffortMinutes = parsed.estimatedMinutes,
                                difficulty = parsed.difficulty,
                                importance = parsed.importance,
                                status = "not_started",
                                displayOrder = tIdx
                            )
                        )
                    }
                }
            }

            db.syllabusDao().insertUnits(unitEntities)
            db.syllabusDao().insertTopics(topicEntities)

            // 3. Create initial Study Plan and Plan Days
            val totalMinutes = topicEntities.sumOf { it.estimatedEffortMinutes }
            val plan = StudyPlan(
                examId = exam.id,
                title = "${exam.title} Master Plan",
                startDateUtc = System.currentTimeMillis(),
                endDateUtc = examDateUtc,
                totalPlannedMinutes = totalMinutes,
                status = "active"
            )
            db.studyPlanDao().insertPlan(plan)

            // 4. Create initial sessions for today and this week
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val todayStr = dateFormat.format(Date())

            val todayPlanDay = StudyPlanDay(
                planId = plan.id,
                dateString = todayStr,
                targetMinutes = (state.dailyWeekdayHours * 60).toInt(),
                completedMinutes = 0,
                phaseName = "Phase 1: Foundation"
            )
            db.studyPlanDao().insertPlanDays(listOf(todayPlanDay))

            // Create initial sessions for today from the top priority topics
            val topTopics = topicEntities.take(3)
            val sessionTimes = listOf("07:00", "08:30", "17:30")
            val initialSessions = topTopics.mapIndexed { index, topic ->
                StudySession(
                    planDayId = todayPlanDay.id,
                    topicId = topic.id,
                    subjectId = topic.subjectId,
                    scheduledDate = todayStr,
                    startTime = sessionTimes.getOrElse(index) { "09:00" },
                    durationMinutes = state.sessionDurationMinutes,
                    mode = "learn",
                    reason = "High importance fundamental topic"
                )
            }
            db.studyPlanDao().insertSessions(initialSessions)

            // 5. Save preferences & mark onboarding completed
            preferencesManager.setSessionPreferences(state.sessionDurationMinutes, state.breakDurationMinutes)
            preferencesManager.setOnboardingCompleted(true)

            _uiState.value = _uiState.value.copy(
                isBuildingPlan = false,
                isCompleted = true
            )
        }
    }
}
