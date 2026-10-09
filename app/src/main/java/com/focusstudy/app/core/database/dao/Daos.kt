package com.focusstudy.app.core.database.dao

import androidx.room.*
import com.focusstudy.app.core.database.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ExamDao {
    @Query("SELECT * FROM exams ORDER BY createdAtUtc DESC")
    fun getAllExams(): Flow<List<Exam>>

    @Query("SELECT * FROM exams WHERE id = :id")
    suspend fun getExamById(id: String): Exam?

    @Query("SELECT * FROM exams ORDER BY examDateUtc ASC LIMIT 1")
    fun getPrimaryExam(): Flow<Exam?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExam(exam: Exam)

    @Update
    suspend fun updateExam(exam: Exam)

    @Delete
    suspend fun deleteExam(exam: Exam)
}

@Dao
interface SyllabusDao {
    @Query("SELECT * FROM subjects WHERE examId = :examId ORDER BY displayOrder ASC")
    fun getSubjectsForExam(examId: String): Flow<List<Subject>>

    @Query("SELECT * FROM subjects WHERE id = :subjectId")
    suspend fun getSubjectById(subjectId: String): Subject?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSubject(subject: Subject)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSubjects(subjects: List<Subject>)

    @Query("SELECT * FROM units WHERE subjectId = :subjectId ORDER BY displayOrder ASC")
    fun getUnitsForSubject(subjectId: String): Flow<List<UnitEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUnits(units: List<UnitEntity>)

    @Query("SELECT * FROM topics WHERE subjectId = :subjectId ORDER BY displayOrder ASC")
    fun getTopicsForSubject(subjectId: String): Flow<List<Topic>>

    @Query("SELECT * FROM topics WHERE id = :topicId")
    suspend fun getTopicById(topicId: String): Topic?

    @Query("SELECT * FROM topics ORDER BY displayOrder ASC")
    fun getAllTopics(): Flow<List<Topic>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTopic(topic: Topic)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTopics(topics: List<Topic>)

    @Update
    suspend fun updateTopic(topic: Topic)

    @Delete
    suspend fun deleteTopic(topic: Topic)

    @Query("SELECT * FROM subtopics WHERE topicId = :topicId ORDER BY displayOrder ASC")
    fun getSubtopicsForTopic(topicId: String): Flow<List<Subtopic>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSubtopics(subtopics: List<Subtopic>)
}

@Dao
interface StudyPlanDao {
    @Query("SELECT * FROM study_plans WHERE examId = :examId AND status = 'active' LIMIT 1")
    fun getActivePlan(examId: String): Flow<StudyPlan?>

    @Query("UPDATE study_plans SET status = 'superseded' WHERE examId = :examId AND status = 'active'")
    suspend fun archiveActivePlans(examId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlan(plan: StudyPlan)

    @Update
    suspend fun updatePlan(plan: StudyPlan)

    @Query("SELECT * FROM study_plan_days WHERE planId = :planId AND dateString = :dateString LIMIT 1")
    suspend fun getPlanDay(planId: String, dateString: String): StudyPlanDay?

    @Query("SELECT * FROM study_plan_days WHERE planId = :planId ORDER BY dateString ASC")
    fun getPlanDays(planId: String): Flow<List<StudyPlanDay>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlanDays(days: List<StudyPlanDay>)

    @Update
    suspend fun updatePlanDay(day: StudyPlanDay)

    @Query("SELECT * FROM study_sessions WHERE scheduledDate = :dateString ORDER BY startTime ASC")
    fun getSessionsForDate(dateString: String): Flow<List<StudySession>>

    @Query("SELECT * FROM study_sessions ORDER BY scheduledDate ASC, startTime ASC")
    fun getAllSessions(): Flow<List<StudySession>>

    @Query("SELECT * FROM study_sessions WHERE id = :sessionId")
    suspend fun getSessionById(sessionId: String): StudySession?

    @Query("SELECT * FROM study_sessions WHERE isCompleted = 0 AND scheduledDate >= :today ORDER BY scheduledDate ASC, startTime ASC LIMIT 1")
    fun getNextPendingSession(today: String): Flow<StudySession?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: StudySession)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSessions(sessions: List<StudySession>)

    @Update
    suspend fun updateSession(session: StudySession)

    @Delete
    suspend fun deleteSession(session: StudySession)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlanVersion(version: AiPlanVersion)

    @Query("SELECT * FROM ai_plan_versions WHERE planId = :planId ORDER BY versionNumber DESC")
    fun getPlanVersions(planId: String): Flow<List<AiPlanVersion>>
}

@Dao
interface StudyAttemptDao {
    @Query("SELECT * FROM study_attempts ORDER BY startedAtUtc DESC")
    fun getAllAttempts(): Flow<List<StudyAttempt>>

    @Query("SELECT * FROM study_attempts WHERE startedAtUtc >= :sinceUtc")
    suspend fun getAttemptsSince(sinceUtc: Long): List<StudyAttempt>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAttempt(attempt: StudyAttempt)
}

@Dao
interface AvailabilityDao {
    @Query("SELECT * FROM availability_windows ORDER BY dayOfWeek ASC, startMinuteOfDay ASC")
    fun getAllWindows(): Flow<List<AvailabilityWindow>>

    @Query("SELECT * FROM availability_windows WHERE dayOfWeek = :dayOfWeek")
    suspend fun getWindowsForDay(dayOfWeek: Int): List<AvailabilityWindow>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWindows(windows: List<AvailabilityWindow>)

    @Query("DELETE FROM availability_windows")
    suspend fun clearAllWindows()
}

@Dao
interface ProgressDao {
    @Query("SELECT * FROM progress_snapshots ORDER BY dateString DESC LIMIT 30")
    fun getRecentSnapshots(): Flow<List<ProgressSnapshot>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSnapshot(snapshot: ProgressSnapshot)

    @Query("SELECT * FROM activity_events ORDER BY timestampUtc DESC LIMIT 50")
    fun getRecentEvents(): Flow<List<ActivityEvent>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(event: ActivityEvent)

    @Query("SELECT * FROM achievements ORDER BY id ASC")
    fun getAllAchievements(): Flow<List<Achievement>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertDefaultAchievements(achievements: List<Achievement>)

    @Query("UPDATE achievements SET unlockedAtUtc = :timestamp WHERE id = :id AND unlockedAtUtc IS NULL")
    suspend fun unlockAchievement(id: String, timestamp: Long = System.currentTimeMillis())
}

@Dao
interface AiInsightDao {
    @Query("SELECT * FROM ai_insights ORDER BY createdAtUtc DESC")
    fun getAllInsights(): Flow<List<AiInsight>>

    @Query("SELECT * FROM ai_insights WHERE isRead = 0 ORDER BY createdAtUtc DESC")
    fun getUnreadInsights(): Flow<List<AiInsight>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInsight(insight: AiInsight)

    @Query("UPDATE ai_insights SET isRead = 1 WHERE id = :id")
    suspend fun markAsRead(id: String)
}
