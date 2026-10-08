package com.focusstudy.app.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "student_profiles")
data class StudentProfile(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val targetScorePercent: Int? = null,
    val studyIntensity: String = "balanced",
    val createdAtUtc: Long = System.currentTimeMillis()
)

@Entity(tableName = "exams")
data class Exam(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val title: String,
    val examType: String, // university, school, competitive, certification, custom
    val examDateUtc: Long,
    val isTentative: Boolean = false,
    val targetScore: Int = 85,
    val confidenceScore: Int = 50,
    val createdAtUtc: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "subjects",
    foreignKeys = [
        ForeignKey(
            entity = Exam::class,
            parentColumns = ["id"],
            childColumns = ["examId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("examId")]
)
data class Subject(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val examId: String,
    val name: String,
    val knowledgeLevel: String = "beginner", // not_started, beginner, some_familiarity, moderate, strong, revision_only
    val importance: String = "normal", // critical, high, normal, low
    val colorHex: String = "#2563EB",
    val displayOrder: Int = 0
)

@Entity(
    tableName = "units",
    foreignKeys = [
        ForeignKey(
            entity = Subject::class,
            parentColumns = ["id"],
            childColumns = ["subjectId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("subjectId")]
)
data class UnitEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val subjectId: String,
    val title: String,
    val displayOrder: Int = 0
)

@Entity(
    tableName = "topics",
    foreignKeys = [
        ForeignKey(
            entity = UnitEntity::class,
            parentColumns = ["id"],
            childColumns = ["unitId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("unitId"), Index("subjectId")]
)
data class Topic(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val unitId: String,
    val subjectId: String,
    val name: String,
    val estimatedEffortMinutes: Int = 60,
    val difficulty: String = "medium", // easy, medium, hard
    val importance: String = "normal", // critical, high, normal, low
    val status: String = "not_started", // not_started, in_progress, completed, revision
    val confidenceScore: Int = 0, // 0 - 100
    val lastStudiedAtUtc: Long? = null,
    val plannedMinutes: Int = 0,
    val actualMinutes: Int = 0,
    val displayOrder: Int = 0
)

@Entity(
    tableName = "subtopics",
    foreignKeys = [
        ForeignKey(
            entity = Topic::class,
            parentColumns = ["id"],
            childColumns = ["topicId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("topicId")]
)
data class Subtopic(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val topicId: String,
    val name: String,
    val isCompleted: Boolean = false,
    val displayOrder: Int = 0
)

@Entity(
    tableName = "study_plans",
    foreignKeys = [
        ForeignKey(
            entity = Exam::class,
            parentColumns = ["id"],
            childColumns = ["examId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("examId")]
)
data class StudyPlan(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val examId: String,
    val title: String,
    val startDateUtc: Long,
    val endDateUtc: Long,
    val totalPlannedMinutes: Int = 0,
    val status: String = "active", // active, completed, archived
    val version: Int = 1,
    val createdAtUtc: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "study_plan_days",
    foreignKeys = [
        ForeignKey(
            entity = StudyPlan::class,
            parentColumns = ["id"],
            childColumns = ["planId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("planId"), Index("dateString")]
)
data class StudyPlanDay(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val planId: String,
    val dateString: String, // YYYY-MM-DD
    val targetMinutes: Int = 0,
    val completedMinutes: Int = 0,
    val phaseName: String = "Core Coverage" // Foundation, Core, Weak Topics, Revision, Mock Practice, Final Review
)

@Entity(
    tableName = "study_sessions",
    foreignKeys = [
        ForeignKey(
            entity = Topic::class,
            parentColumns = ["id"],
            childColumns = ["topicId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("topicId"), Index("scheduledDate")]
)
data class StudySession(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val planDayId: String? = null,
    val topicId: String,
    val subjectId: String,
    val scheduledDate: String, // YYYY-MM-DD
    val startTime: String = "07:00", // HH:mm
    val durationMinutes: Int = 45,
    val completedDurationMinutes: Int = 0,
    val mode: String = "learn", // learn, revise, practice, mock
    val reason: String = "",
    val isCompleted: Boolean = false,
    val isLocked: Boolean = false, // user pinned
    val scheduledAtUtc: Long = System.currentTimeMillis(),
    val completedAtUtc: Long? = null
)

@Entity(
    tableName = "study_attempts",
    indices = [Index("sessionId"), Index("topicId")]
)
data class StudyAttempt(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val sessionId: String? = null,
    val topicId: String,
    val startedAtUtc: Long,
    val endedAtUtc: Long,
    val durationSeconds: Int,
    val interrupted: Boolean = false,
    val notes: String? = null
)

@Entity(tableName = "availability_windows")
data class AvailabilityWindow(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val dayOfWeek: Int, // 1 = Monday ... 7 = Sunday
    val startMinuteOfDay: Int, // e.g., 420 for 07:00
    val endMinuteOfDay: Int, // e.g., 540 for 09:00
    val maxDailyMinutes: Int = 180
)

@Entity(tableName = "study_preferences")
data class StudyPreference(
    @PrimaryKey val id: String = "default_preference",
    val sessionDurationMinutes: Int = 45,
    val breakDurationMinutes: Int = 10,
    val preferredWindow: String = "morning",
    val enableHaptics: Boolean = true,
    val enableSound: Boolean = true
)

@Entity(tableName = "progress_snapshots")
data class ProgressSnapshot(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val dateString: String,
    val totalStudyMinutes: Int = 0,
    val syllabusCoveragePercent: Float = 0f,
    val activeStreakDays: Int = 0,
    val neglectedTopicCount: Int = 0,
    val createdAtUtc: Long = System.currentTimeMillis()
)

@Entity(tableName = "activity_events")
data class ActivityEvent(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val eventType: String,
    val description: String,
    val timestampUtc: Long = System.currentTimeMillis()
)

@Entity(tableName = "ai_insights")
data class AiInsight(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val category: String, // daily_brief, risk_alert, neglected_topics, peak_window, weekly_review
    val title: String,
    val content: String,
    val actionIntentJson: String? = null,
    val isRead: Boolean = false,
    val createdAtUtc: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "ai_plan_versions",
    indices = [Index("planId")]
)
data class AiPlanVersion(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val planId: String,
    val versionNumber: Int,
    val summaryOfChanges: String,
    val promptVersion: String,
    val rawJsonDiff: String,
    val appliedAtUtc: Long = System.currentTimeMillis()
)

@Entity(tableName = "achievements")
data class Achievement(
    @PrimaryKey val id: String,
    val title: String,
    val description: String,
    val badgeIcon: String,
    val unlockedAtUtc: Long? = null
)
