package com.focusstudy.app.core.worker

import android.content.Context
import androidx.work.*
import com.focusstudy.app.core.ai.AiReplanService
import com.focusstudy.app.core.ai.RiskLevel
import com.focusstudy.app.core.database.AppDatabase
import com.focusstudy.app.core.datastore.UserPreferencesManager
import com.focusstudy.app.core.notification.NotificationHelper
import kotlinx.coroutines.flow.firstOrNull
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class DailyPlanWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val db = AppDatabase.getInstance(applicationContext)
        val prefs = UserPreferencesManager(applicationContext).userPreferencesFlow.firstOrNull() ?: return Result.success()

        val exam = db.examDao().getPrimaryExam().firstOrNull() ?: return Result.success()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val todayStr = dateFormat.format(Date())

        val sessions = db.studyPlanDao().getSessionsForDate(todayStr).firstOrNull() ?: emptyList()
        val daysRemaining = (((exam.examDateUtc - System.currentTimeMillis()) / (1000L * 60 * 60 * 24)).toInt()).coerceAtLeast(1)

        // 1. Daily plan reminder
        if (prefs.dailyPlanReminderEnabled && sessions.isNotEmpty()) {
            val firstTopicId = sessions.firstOrNull()?.topicId
            val firstTopicName = firstTopicId?.let { db.syllabusDao().getTopicById(it)?.name } ?: "Core Foundations"
            NotificationHelper.showDailyPlanReminder(
                context = applicationContext,
                sessionCount = sessions.size,
                primaryTopicName = firstTopicName
            )
        }

        // 2. Exam countdown notification
        if (prefs.examCountdownEnabled && (daysRemaining == 30 || daysRemaining == 14 || daysRemaining <= 7)) {
            val topics = db.syllabusDao().getAllTopics().firstOrNull() ?: emptyList()
            val completedCount = topics.count { it.status == "completed" }
            val coverage = if (topics.isNotEmpty()) ((completedCount.toFloat() / topics.size) * 100).toInt() else 0
            NotificationHelper.showExamCountdown(
                context = applicationContext,
                daysRemaining = daysRemaining,
                examTitle = exam.title,
                coveragePercent = coverage
            )
        }

        return Result.success()
    }
}

class ScheduleRiskWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val db = AppDatabase.getInstance(applicationContext)
        val exam = db.examDao().getPrimaryExam().firstOrNull() ?: return Result.success()
        val plan = db.studyPlanDao().getActivePlan(exam.id).firstOrNull() ?: return Result.success()

        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val todayStr = dateFormat.format(Date())

        val planDays = db.studyPlanDao().getPlanDays(plan.id).firstOrNull() ?: emptyList()
        val allSessions = mutableListOf<com.focusstudy.app.core.database.entity.StudySession>()
        for (day in planDays.take(14)) {
            val daySessions = db.studyPlanDao().getSessionsForDate(day.dateString).firstOrNull() ?: emptyList()
            allSessions.addAll(daySessions)
        }

        val replanService = AiReplanService(db)
        val missed = replanService.detectMissedSessions(allSessions, todayStr)

        val topics = db.syllabusDao().getAllTopics().firstOrNull() ?: emptyList()
        val daysRemaining = (((exam.examDateUtc - System.currentTimeMillis()) / (1000L * 60 * 60 * 24)).toInt()).coerceAtLeast(1)

        val riskAssessment = replanService.calculateScheduleRisk(
            exam = exam,
            allTopics = topics,
            remainingDays = daysRemaining,
            availableDailyMinutes = 180
        )

        if (missed.isNotEmpty() || riskAssessment.riskLevel == RiskLevel.HIGH || riskAssessment.riskLevel == RiskLevel.CRITICAL) {
            val message = if (missed.isNotEmpty()) {
                "${missed.size} scheduled sessions were missed. ${riskAssessment.explanation}"
            } else {
                riskAssessment.explanation
            }
            NotificationHelper.showScheduleRiskAlert(
                context = applicationContext,
                riskTitle = if (missed.isNotEmpty()) "${missed.size} Missed Sessions" else "High Schedule Load",
                message = message
            )
        }

        return Result.success()
    }
}

object StudyWorkScheduler {

    private const val WORK_DAILY_PLAN = "work_daily_plan"
    private const val WORK_SCHEDULE_RISK = "work_schedule_risk"

    fun schedulePeriodicWork(context: Context) {
        val workManager = WorkManager.getInstance(context)

        // Periodic Daily Plan Check (every 24 hours)
        val dailyPlanRequest = PeriodicWorkRequestBuilder<DailyPlanWorker>(24, TimeUnit.HOURS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiresBatteryNotLow(true)
                    .build()
            )
            .build()

        workManager.enqueueUniquePeriodicWork(
            WORK_DAILY_PLAN,
            ExistingPeriodicWorkPolicy.KEEP,
            dailyPlanRequest
        )

        // Periodic Risk and Missed Session Check (every 12 hours)
        val riskRequest = PeriodicWorkRequestBuilder<ScheduleRiskWorker>(12, TimeUnit.HOURS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiresBatteryNotLow(true)
                    .build()
            )
            .build()

        workManager.enqueueUniquePeriodicWork(
            WORK_SCHEDULE_RISK,
            ExistingPeriodicWorkPolicy.KEEP,
            riskRequest
        )
    }
}
