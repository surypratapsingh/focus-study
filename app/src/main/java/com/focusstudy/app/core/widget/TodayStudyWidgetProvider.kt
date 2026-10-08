package com.focusstudy.app.core.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.focusstudy.app.MainActivity
import com.focusstudy.app.R
import com.focusstudy.app.core.database.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.max

/**
 * Android App Widget displaying exam countdown, today's target progress,
 * and next priority session recommendation (FSTUDY-013-01, 02, 03).
 */
class TodayStudyWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val scope = CoroutineScope(Dispatchers.IO)
        scope.launch {
            val db = AppDatabase.getInstance(context)
            val exam = db.examDao().getPrimaryExam().firstOrNull()

            val now = System.currentTimeMillis()
            val daysRemaining = if (exam != null) {
                max(0L, (exam.examDateUtc - now) / (24 * 60 * 60 * 1000L)).toInt()
            } else 30

            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val todayStr = dateFormat.format(Date(now))

            val sessions = db.studyPlanDao().getSessionsForDate(todayStr).firstOrNull() ?: emptyList()
            val completedSessions = sessions.filter { it.isCompleted }
            val nextSession = sessions.firstOrNull { !it.isCompleted }

            val completedMinutes = completedSessions.sumOf { it.completedDurationMinutes.coerceAtLeast(it.durationMinutes) }
            val totalPlannedMinutes = sessions.sumOf { it.durationMinutes }.coerceAtLeast(180)
            val progressPercent = if (totalPlannedMinutes > 0) {
                ((completedMinutes.toFloat() / totalPlannedMinutes.toFloat()) * 100).toInt().coerceIn(0, 100)
            } else 0

            var nextSessionText = "All sessions completed for today! 🎉"
            if (nextSession != null) {
                val topic = db.syllabusDao().getTopicById(nextSession.topicId)
                val topicName = topic?.name ?: "Study Session"
                nextSessionText = "Next: $topicName (${nextSession.startTime} · ${nextSession.durationMinutes}m)"
            } else if (sessions.isEmpty()) {
                nextSessionText = "Open app to schedule today's sessions"
            }

            for (widgetId in appWidgetIds) {
                val views = RemoteViews(context.packageName, R.layout.widget_today_study)

                // 1. Exam Countdown (FSTUDY-013-01)
                views.setTextViewText(R.id.widget_exam_countdown, "Exam in $daysRemaining days")

                // 2. Today's Progress Bar (FSTUDY-013-03)
                views.setTextViewText(
                    R.id.widget_progress_text,
                    "Today: $completedMinutes / $totalPlannedMinutes min ($progressPercent%)"
                )
                views.setProgressBar(R.id.widget_progress_bar, 100, progressPercent, false)

                // 3. Next Session (FSTUDY-013-02)
                views.setTextViewText(R.id.widget_next_session, nextSessionText)

                // 4. Click Intent: Launches Focus Study Main Activity
                val launchIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
                val pendingIntent = PendingIntent.getActivity(
                    context,
                    0,
                    launchIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)

                appWidgetManager.updateAppWidget(widgetId, views)
            }
        }
    }

    companion object {
        /**
         * Triggers an immediate refresh of all Focus Study widgets on the home screen
         */
        fun triggerUpdate(context: Context) {
            val intent = Intent(context, TodayStudyWidgetProvider::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                val widgetManager = AppWidgetManager.getInstance(context)
                val ids = widgetManager.getAppWidgetIds(
                    ComponentName(context, TodayStudyWidgetProvider::class.java)
                )
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            }
            context.sendBroadcast(intent)
        }
    }
}
