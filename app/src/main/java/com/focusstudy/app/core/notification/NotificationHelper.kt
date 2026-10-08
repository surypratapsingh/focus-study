package com.focusstudy.app.core.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.focusstudy.app.MainActivity

object NotificationHelper {

    const val CHANNEL_SESSIONS = "channel_study_sessions"
    const val CHANNEL_DAILY_PLAN = "channel_daily_plan"
    const val CHANNEL_RISKS = "channel_schedule_risks"

    private const val NOTIF_ID_SESSION = 1001
    private const val NOTIF_ID_DAILY = 1002
    private const val NOTIF_ID_EXAM = 1003
    private const val NOTIF_ID_RISK = 1004
    private const val NOTIF_ID_REVIEW = 1005

    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val sessionChannel = NotificationChannel(
                CHANNEL_SESSIONS,
                "Study Sessions & Reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Reminders when a scheduled study session is about to start"
                enableVibration(true)
            }

            val planChannel = NotificationChannel(
                CHANNEL_DAILY_PLAN,
                "Daily Plan & Exam Countdown",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Morning study plan summaries and days until exam countdown"
            }

            val riskChannel = NotificationChannel(
                CHANNEL_RISKS,
                "Schedule Risk Alerts",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Alerts when behind schedule or sessions are missed"
            }

            manager.createNotificationChannel(sessionChannel)
            manager.createNotificationChannel(planChannel)
            manager.createNotificationChannel(riskChannel)
        }
    }

    private fun getPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun showSessionReminder(
        context: Context,
        topicName: String,
        startTime: String,
        durationMinutes: Int
    ) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = NotificationCompat.Builder(context, CHANNEL_SESSIONS)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Upcoming Study Session: $topicName")
            .setContentText("Starts at $startTime · $durationMinutes minutes. Tap to prepare.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(getPendingIntent(context))
            .setAutoCancel(true)
            .build()

        manager.notify(NOTIF_ID_SESSION, notification)
    }

    fun showDailyPlanReminder(
        context: Context,
        sessionCount: Int,
        primaryTopicName: String
    ) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = NotificationCompat.Builder(context, CHANNEL_DAILY_PLAN)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Good Morning! Today's Study Plan")
            .setContentText("You have $sessionCount sessions today. First priority: $primaryTopicName.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(getPendingIntent(context))
            .setAutoCancel(true)
            .build()

        manager.notify(NOTIF_ID_DAILY, notification)
    }

    fun showExamCountdown(
        context: Context,
        daysRemaining: Int,
        examTitle: String,
        coveragePercent: Int
    ) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = NotificationCompat.Builder(context, CHANNEL_DAILY_PLAN)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Exam Countdown: $daysRemaining Days Left")
            .setContentText("$examTitle is in $daysRemaining days. You are $coveragePercent% through your planned syllabus.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(getPendingIntent(context))
            .setAutoCancel(true)
            .build()

        manager.notify(NOTIF_ID_EXAM, notification)
    }

    fun showScheduleRiskAlert(
        context: Context,
        riskTitle: String,
        message: String
    ) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = NotificationCompat.Builder(context, CHANNEL_RISKS)
            .setSmallIcon(android.R.drawable.stat_notify_more)
            .setContentTitle("Study Schedule Alert: $riskTitle")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$message Tap to open Adaptive Replan and rebalance your days."))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(getPendingIntent(context))
            .setAutoCancel(true)
            .build()

        manager.notify(NOTIF_ID_RISK, notification)
    }

    fun showEndOfDayReview(
        context: Context,
        completedMinutes: Int,
        targetMinutes: Int,
        streakDays: Int
    ) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = NotificationCompat.Builder(context, CHANNEL_DAILY_PLAN)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Day Complete: $completedMinutes / $targetMinutes mins")
            .setContentText("Great dedication today! You kept your $streakDays-day study streak alive.")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(getPendingIntent(context))
            .setAutoCancel(true)
            .build()

        manager.notify(NOTIF_ID_REVIEW, notification)
    }
}
