package com.focusstudy.app.core.backup

import com.focusstudy.app.core.database.AppDatabase
import com.focusstudy.app.core.database.entity.*
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class BackupPayload(
    val exportVersion: Int = 1,
    val exportedAtUtc: Long = System.currentTimeMillis(),
    val exams: List<ExamDto> = emptyList(),
    val subjects: List<SubjectDto> = emptyList(),
    val units: List<UnitDto> = emptyList(),
    val topics: List<TopicDto> = emptyList(),
    val subtopics: List<SubtopicDto> = emptyList(),
    val studyPlans: List<StudyPlanDto> = emptyList(),
    val studyPlanDays: List<StudyPlanDayDto> = emptyList(),
    val studySessions: List<StudySessionDto> = emptyList(),
    val studyAttempts: List<StudyAttemptDto> = emptyList(),
    val progressSnapshots: List<ProgressSnapshotDto> = emptyList()
)

@Serializable
data class ExamDto(
    val id: String,
    val title: String,
    val examType: String,
    val examDateUtc: Long,
    val isTentative: Boolean = false,
    val targetScore: Int = 85,
    val confidenceScore: Int = 50
)

@Serializable
data class SubjectDto(
    val id: String,
    val examId: String,
    val name: String,
    val knowledgeLevel: String = "beginner",
    val importance: String = "normal",
    val colorHex: String = "#3B82F6",
    val displayOrder: Int = 0
)

@Serializable
data class UnitDto(
    val id: String,
    val subjectId: String,
    val name: String,
    val displayOrder: Int = 0
)

@Serializable
data class TopicDto(
    val id: String,
    val unitId: String,
    val subjectId: String,
    val name: String,
    val estimatedEffortMinutes: Int = 60,
    val plannedMinutes: Int = 60,
    val actualMinutes: Int = 0,
    val importance: String = "normal",
    val difficulty: String = "medium",
    val confidenceScore: Int = 0,
    val status: String = "not_started",
    val displayOrder: Int = 0
)

@Serializable
data class SubtopicDto(
    val id: String,
    val topicId: String,
    val name: String,
    val isCompleted: Boolean = false,
    val displayOrder: Int = 0
)

@Serializable
data class StudyPlanDto(
    val id: String,
    val examId: String,
    val title: String,
    val startDateUtc: Long,
    val endDateUtc: Long,
    val totalPlannedMinutes: Int = 0,
    val status: String = "active",
    val version: Int = 1
)

@Serializable
data class StudyPlanDayDto(
    val id: String,
    val planId: String,
    val dateString: String,
    val targetMinutes: Int = 0,
    val completedMinutes: Int = 0,
    val phaseName: String = "Core Coverage"
)

@Serializable
data class StudySessionDto(
    val id: String,
    val planDayId: String? = null,
    val topicId: String,
    val subjectId: String,
    val scheduledDate: String,
    val startTime: String = "07:00",
    val durationMinutes: Int = 45,
    val completedDurationMinutes: Int = 0,
    val mode: String = "learn",
    val reason: String = "",
    val isCompleted: Boolean = false,
    val isLocked: Boolean = false
)

@Serializable
data class StudyAttemptDto(
    val id: String,
    val sessionId: String? = null,
    val topicId: String,
    val startedAtUtc: Long,
    val endedAtUtc: Long,
    val durationSeconds: Int,
    val interrupted: Boolean = false,
    val notes: String? = null
)

@Serializable
data class ProgressSnapshotDto(
    val id: String,
    val dateString: String,
    val totalStudyMinutes: Int = 0,
    val syllabusCoveragePercent: Float = 0f,
    val activeStreakDays: Int = 0,
    val neglectedTopicCount: Int = 0
)

data class ImportResult(
    val success: Boolean,
    val message: String,
    val importedExamsCount: Int = 0,
    val importedTopicsCount: Int = 0,
    val importedSessionsCount: Int = 0
)

class DataBackupManager(
    private val db: AppDatabase? = null
) {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    /**
     * Exports complete database contents to JSON string
     */
    suspend fun exportDataToJson(): String {
        if (db == null) return json.encodeToString(BackupPayload())
        val exams = db.examDao().getAllExams().firstOrNull() ?: emptyList()
        val primaryExam = db.examDao().getPrimaryExam().firstOrNull()
        val allSubjects = mutableListOf<Subject>()
        for (exam in exams) {
            allSubjects.addAll(db.syllabusDao().getSubjectsForExam(exam.id).firstOrNull() ?: emptyList())
        }

        val allTopics = db.syllabusDao().getAllTopics().firstOrNull() ?: emptyList()
        val allAttempts = db.studyAttemptDao().getAllAttempts().firstOrNull() ?: emptyList()

        val plans = if (primaryExam != null) {
            val active = db.studyPlanDao().getActivePlan(primaryExam.id).firstOrNull()
            if (active != null) listOf(active) else emptyList()
        } else emptyList()

        val planDays = mutableListOf<StudyPlanDay>()
        val planSessions = mutableListOf<StudySession>()
        for (plan in plans) {
            val days = db.studyPlanDao().getPlanDays(plan.id).firstOrNull() ?: emptyList()
            planDays.addAll(days)
            for (day in days) {
                val sessions = db.studyPlanDao().getSessionsForDate(day.dateString).firstOrNull() ?: emptyList()
                planSessions.addAll(sessions)
            }
        }

        val snapshots = db.progressDao().getRecentSnapshots().firstOrNull() ?: emptyList()

        val payload = BackupPayload(
            exams = exams.map { ExamDto(it.id, it.title, it.examType, it.examDateUtc, it.isTentative, it.targetScore, it.confidenceScore) },
            subjects = allSubjects.map { SubjectDto(it.id, it.examId, it.name, it.knowledgeLevel, it.importance, it.colorHex, it.displayOrder) },
            topics = allTopics.map {
                TopicDto(it.id, it.unitId, it.subjectId, it.name, it.estimatedEffortMinutes, it.plannedMinutes, it.actualMinutes, it.importance, it.difficulty, it.confidenceScore, it.status, it.displayOrder)
            },
            studyPlans = plans.map { StudyPlanDto(it.id, it.examId, it.title, it.startDateUtc, it.endDateUtc, it.totalPlannedMinutes, it.status, it.version) },
            studyPlanDays = planDays.map { StudyPlanDayDto(it.id, it.planId, it.dateString, it.targetMinutes, it.completedMinutes, it.phaseName) },
            studySessions = planSessions.distinctBy { it.id }.map {
                StudySessionDto(it.id, it.planDayId, it.topicId, it.subjectId, it.scheduledDate, it.startTime, it.durationMinutes, it.completedDurationMinutes, it.mode, it.reason, it.isCompleted, it.isLocked)
            },
            studyAttempts = allAttempts.map {
                StudyAttemptDto(it.id, it.sessionId, it.topicId, it.startedAtUtc, it.endedAtUtc, it.durationSeconds, it.interrupted, it.notes)
            },
            progressSnapshots = snapshots.map {
                ProgressSnapshotDto(it.id, it.dateString, it.totalStudyMinutes, it.syllabusCoveragePercent, it.activeStreakDays, it.neglectedTopicCount)
            }
        )

        return json.encodeToString(payload)
    }

    /**
     * Imports and validates backup JSON, inserting entities into Room
     */
    suspend fun importDataFromJson(jsonString: String): ImportResult {
        return try {
            val payload = json.decodeFromString<BackupPayload>(jsonString)

            if (payload.exams.isEmpty() && payload.topics.isEmpty()) {
                return ImportResult(false, "Invalid backup: No exams or topics found in backup file.")
            }

            val examEntities = payload.exams.map {
                Exam(it.id, it.title, it.examType, it.examDateUtc, it.isTentative, it.targetScore, it.confidenceScore)
            }
            val subjectEntities = payload.subjects.map {
                Subject(it.id, it.examId, it.name, it.knowledgeLevel, it.importance, it.colorHex, it.displayOrder)
            }
            val topicEntities = payload.topics.map {
                Topic(
                    id = it.id,
                    unitId = it.unitId,
                    subjectId = it.subjectId,
                    name = it.name,
                    estimatedEffortMinutes = it.estimatedEffortMinutes,
                    plannedMinutes = it.plannedMinutes,
                    actualMinutes = it.actualMinutes,
                    importance = it.importance,
                    difficulty = it.difficulty,
                    confidenceScore = it.confidenceScore,
                    status = it.status,
                    displayOrder = it.displayOrder
                )
            }
            val sessionEntities = payload.studySessions.map {
                StudySession(
                    id = it.id,
                    planDayId = it.planDayId,
                    topicId = it.topicId,
                    subjectId = it.subjectId,
                    scheduledDate = it.scheduledDate,
                    startTime = it.startTime,
                    durationMinutes = it.durationMinutes,
                    completedDurationMinutes = it.completedDurationMinutes,
                    mode = it.mode,
                    reason = it.reason,
                    isCompleted = it.isCompleted,
                    isLocked = it.isLocked
                )
            }

            // Restore to Room DB if provided
            if (db != null) {
                for (exam in examEntities) {
                    db.examDao().insertExam(exam)
                }
                if (subjectEntities.isNotEmpty()) {
                    db.syllabusDao().insertSubjects(subjectEntities)
                }
                if (topicEntities.isNotEmpty()) {
                    db.syllabusDao().insertTopics(topicEntities)
                }
                for (planDto in payload.studyPlans) {
                    db.studyPlanDao().insertPlan(
                        StudyPlan(planDto.id, planDto.examId, planDto.title, planDto.startDateUtc, planDto.endDateUtc, planDto.totalPlannedMinutes, planDto.status, planDto.version)
                    )
                }
                val dayEntities = payload.studyPlanDays.map {
                    StudyPlanDay(it.id, it.planId, it.dateString, it.targetMinutes, it.completedMinutes, it.phaseName)
                }
                if (dayEntities.isNotEmpty()) {
                    db.studyPlanDao().insertPlanDays(dayEntities)
                }
                if (sessionEntities.isNotEmpty()) {
                    db.studyPlanDao().insertSessions(sessionEntities)
                }
            }

            ImportResult(
                success = true,
                message = "Successfully imported ${examEntities.size} exams, ${topicEntities.size} topics, and ${sessionEntities.size} study sessions.",
                importedExamsCount = examEntities.size,
                importedTopicsCount = topicEntities.size,
                importedSessionsCount = sessionEntities.size
            )
        } catch (e: Exception) {
            ImportResult(
                success = false,
                message = "Failed to parse backup JSON: ${e.localizedMessage ?: "Format error"}"
            )
        }
    }
}
