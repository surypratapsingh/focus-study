package com.focusstudy.app.core.repository

import com.focusstudy.app.core.database.AppDatabase
import com.focusstudy.app.core.database.entity.*
import com.focusstudy.app.core.datastore.UserPreferences
import com.focusstudy.app.core.datastore.UserPreferencesManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull

interface SyllabusRepository {
    fun getSubjectsForExam(examId: String): Flow<List<Subject>>
    fun getTopicsForSubject(subjectId: String): Flow<List<Topic>>
    fun getAllTopics(): Flow<List<Topic>>
    suspend fun saveSyllabus(subjects: List<Subject>, units: List<UnitEntity>, topics: List<Topic>)
    suspend fun updateTopic(topic: Topic)
    suspend fun markTopicCompleted(topicId: String)
}

class SyllabusRepositoryImpl(private val db: AppDatabase) : SyllabusRepository {
    override fun getSubjectsForExam(examId: String): Flow<List<Subject>> =
        db.syllabusDao().getSubjectsForExam(examId)

    override fun getTopicsForSubject(subjectId: String): Flow<List<Topic>> =
        db.syllabusDao().getTopicsForSubject(subjectId)

    override fun getAllTopics(): Flow<List<Topic>> =
        db.syllabusDao().getAllTopics()

    override suspend fun saveSyllabus(
        subjects: List<Subject>,
        units: List<UnitEntity>,
        topics: List<Topic>
    ) {
        db.syllabusDao().insertSubjects(subjects)
        db.syllabusDao().insertUnits(units)
        db.syllabusDao().insertTopics(topics)
    }

    override suspend fun updateTopic(topic: Topic) =
        db.syllabusDao().updateTopic(topic)

    override suspend fun markTopicCompleted(topicId: String) {
        val topic = db.syllabusDao().getTopicById(topicId)
        if (topic != null) {
            db.syllabusDao().updateTopic(topic.copy(status = "completed", confidenceScore = 100))
        }
    }
}

interface StudyPlanRepository {
    fun getActivePlan(examId: String): Flow<StudyPlan?>
    fun getPlanDays(planId: String): Flow<List<StudyPlanDay>>
    fun getSessionsForDate(dateString: String): Flow<List<StudySession>>
    fun getNextPendingSession(today: String): Flow<StudySession?>
    suspend fun savePlan(plan: StudyPlan, days: List<StudyPlanDay>, sessions: List<StudySession>)
    suspend fun updateSession(session: StudySession)
    suspend fun completeSession(sessionId: String, completedMinutes: Int)
}

class StudyPlanRepositoryImpl(private val db: AppDatabase) : StudyPlanRepository {
    override fun getActivePlan(examId: String): Flow<StudyPlan?> =
        db.studyPlanDao().getActivePlan(examId)

    override fun getPlanDays(planId: String): Flow<List<StudyPlanDay>> =
        db.studyPlanDao().getPlanDays(planId)

    override fun getSessionsForDate(dateString: String): Flow<List<StudySession>> =
        db.studyPlanDao().getSessionsForDate(dateString)

    override fun getNextPendingSession(today: String): Flow<StudySession?> =
        db.studyPlanDao().getNextPendingSession(today)

    override suspend fun savePlan(
        plan: StudyPlan,
        days: List<StudyPlanDay>,
        sessions: List<StudySession>
    ) {
        db.studyPlanDao().insertPlan(plan)
        db.studyPlanDao().insertPlanDays(days)
        db.studyPlanDao().insertSessions(sessions)
    }

    override suspend fun updateSession(session: StudySession) =
        db.studyPlanDao().updateSession(session)

    override suspend fun completeSession(sessionId: String, completedMinutes: Int) {
        val session = db.studyPlanDao().getSessionById(sessionId)
        if (session != null) {
            val updated = session.copy(
                isCompleted = true,
                completedDurationMinutes = completedMinutes,
                completedAtUtc = System.currentTimeMillis()
            )
            db.studyPlanDao().updateSession(updated)

            // Update corresponding topic actual minutes
            val topic = db.syllabusDao().getTopicById(session.topicId)
            if (topic != null) {
                db.syllabusDao().updateTopic(
                    topic.copy(
                        actualMinutes = topic.actualMinutes + completedMinutes,
                        lastStudiedAtUtc = System.currentTimeMillis(),
                        status = if (topic.actualMinutes + completedMinutes >= topic.plannedMinutes && topic.plannedMinutes > 0) "completed" else "in_progress"
                    )
                )
            }
        }
    }
}

interface ProgressRepository {
    suspend fun calculateSyllabusCoverage(examId: String): Float
    suspend fun getNeglectedTopics(): List<Topic>
    fun getRecentSnapshots(): Flow<List<ProgressSnapshot>>
}

class ProgressRepositoryImpl(private val db: AppDatabase) : ProgressRepository {
    override suspend fun calculateSyllabusCoverage(examId: String): Float {
        val topics = db.syllabusDao().getAllTopics().firstOrNull() ?: emptyList()
        if (topics.isEmpty()) return 0f
        val completedCount = topics.count { it.status == "completed" }
        return (completedCount.toFloat() / topics.size.toFloat()) * 100f
    }

    override suspend fun getNeglectedTopics(): List<Topic> {
        val topics = db.syllabusDao().getAllTopics().firstOrNull() ?: emptyList()
        val oneWeekAgo = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000L)
        return topics.filter {
            it.status != "completed" && (it.lastStudiedAtUtc == null || it.lastStudiedAtUtc < oneWeekAgo)
        }.sortedByDescending { it.importance == "critical" || it.importance == "high" }
    }

    override fun getRecentSnapshots(): Flow<List<ProgressSnapshot>> =
        db.progressDao().getRecentSnapshots()
}

interface UserSettingsRepository {
    val preferencesFlow: Flow<UserPreferences>
    suspend fun setOnboardingCompleted(completed: Boolean)
    suspend fun setThemeMode(mode: String)
    suspend fun setAiConsent(consentGiven: Boolean, featuresEnabled: Boolean)
    suspend fun setSoundEnabled(enabled: Boolean)
    suspend fun setHapticEnabled(enabled: Boolean)
    suspend fun setSessionRemindersEnabled(enabled: Boolean)
    suspend fun setDailyPlanReminderEnabled(enabled: Boolean)
    suspend fun setExamCountdownEnabled(enabled: Boolean)
    suspend fun setPersonalizationPreferences(technique: String, burnoutGuard: Boolean)
    suspend fun setStudyBuddy(name: String, xp: Int, streak: Int, minutes: Int)
    suspend fun clearStudyBuddy()
    suspend fun setCustomGeminiApiKey(key: String)
}

class UserSettingsRepositoryImpl(
    private val preferencesManager: UserPreferencesManager
) : UserSettingsRepository {
    override val preferencesFlow: Flow<UserPreferences> =
        preferencesManager.userPreferencesFlow

    override suspend fun setOnboardingCompleted(completed: Boolean) =
        preferencesManager.setOnboardingCompleted(completed)

    override suspend fun setThemeMode(mode: String) =
        preferencesManager.setThemeMode(mode)

    override suspend fun setAiConsent(consentGiven: Boolean, featuresEnabled: Boolean) =
        preferencesManager.setAiConsent(consentGiven, featuresEnabled)

    override suspend fun setSoundEnabled(enabled: Boolean) =
        preferencesManager.setSoundEnabled(enabled)

    override suspend fun setHapticEnabled(enabled: Boolean) =
        preferencesManager.setHapticEnabled(enabled)

    override suspend fun setSessionRemindersEnabled(enabled: Boolean) =
        preferencesManager.setSessionRemindersEnabled(enabled)

    override suspend fun setDailyPlanReminderEnabled(enabled: Boolean) =
        preferencesManager.setDailyPlanReminderEnabled(enabled)

    override suspend fun setExamCountdownEnabled(enabled: Boolean) =
        preferencesManager.setExamCountdownEnabled(enabled)

    override suspend fun setPersonalizationPreferences(technique: String, burnoutGuard: Boolean) =
        preferencesManager.setPersonalizationPreferences(technique, burnoutGuard)

    override suspend fun setStudyBuddy(name: String, xp: Int, streak: Int, minutes: Int) =
        preferencesManager.setStudyBuddy(name, xp, streak, minutes)

    override suspend fun clearStudyBuddy() =
        preferencesManager.clearStudyBuddy()

    override suspend fun setCustomGeminiApiKey(key: String) =
        preferencesManager.setCustomGeminiApiKey(key)
}
