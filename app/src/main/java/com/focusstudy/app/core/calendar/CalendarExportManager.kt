package com.focusstudy.app.core.calendar

import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import androidx.core.content.FileProvider
import com.focusstudy.app.core.database.entity.Exam
import com.focusstudy.app.core.database.entity.StudySession
import com.focusstudy.app.core.database.entity.Subject
import com.focusstudy.app.core.database.entity.Topic
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

/**
 * Manages calendar timetable generation conforming to RFC 5545 iCalendar (.ics) specification
 * and Android platform CalendarContract integration.
 */
object CalendarExportManager {

    /**
     * Generates a standard RFC 5545 iCalendar (.ics) timetable from study sessions and exam
     */
    fun generateIcsCalendar(
        exam: Exam?,
        sessions: List<StudySession>,
        topicsMap: Map<String, Topic> = emptyMap(),
        subjectsMap: Map<String, Subject> = emptyMap()
    ): String {
        val sb = StringBuilder()
        val utcFormat = SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val nowUtc = utcFormat.format(Date())

        sb.append("BEGIN:VCALENDAR\r\n")
        sb.append("VERSION:2.0\r\n")
        sb.append("PRODID:-//FocusStudy//Focus Study OS v1.0.0//EN\r\n")
        sb.append("CALSCALE:GREGORIAN\r\n")
        sb.append("METHOD:PUBLISH\r\n")
        sb.append("X-WR-CALNAME:${escapeIcs(exam?.title ?: "Focus Study Timetable")}\r\n")
        sb.append("X-WR-TIMEZONE:UTC\r\n")

        // 1. Exam Target Milestone Event
        if (exam != null && exam.examDateUtc > 0) {
            val examDate = Date(exam.examDateUtc)
            val dateOnlyFormat = SimpleDateFormat("yyyyMMdd", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            val examDayStr = dateOnlyFormat.format(examDate)
            val nextDayStr = dateOnlyFormat.format(Date(exam.examDateUtc + 24 * 60 * 60 * 1000L))

            sb.append("BEGIN:VEVENT\r\n")
            sb.append("UID:exam-${exam.id}@focusstudy.app\r\n")
            sb.append("DTSTAMP:$nowUtc\r\n")
            sb.append("DTSTART;VALUE=DATE:$examDayStr\r\n")
            sb.append("DTEND;VALUE=DATE:$nextDayStr\r\n")
            sb.append("SUMMARY:🎯 EXAM: ${escapeIcs(exam.title)}\r\n")
            sb.append("DESCRIPTION:Target Score: ${exam.targetScore}% · Type: ${escapeIcs(exam.examType)}\r\n")
            sb.append("STATUS:CONFIRMED\r\n")
            sb.append("TRANSP:TRANSPARENT\r\n")
            sb.append("END:VEVENT\r\n")
        }

        // 2. Scheduled Study Sessions
        for (session in sessions) {
            val topic = topicsMap[session.topicId]
            val topicName = topic?.name ?: "Topic Study"
            val subjectName = subjectsMap[session.subjectId]?.name ?: "General Subject"

            val (startUtcStr, endUtcStr) = formatSessionTimes(session.scheduledDate, session.startTime, session.durationMinutes)

            sb.append("BEGIN:VEVENT\r\n")
            sb.append("UID:session-${session.id}@focusstudy.app\r\n")
            sb.append("DTSTAMP:$nowUtc\r\n")
            sb.append("DTSTART:$startUtcStr\r\n")
            sb.append("DTEND:$endUtcStr\r\n")
            sb.append("SUMMARY:Study: ${escapeIcs(topicName)}\r\n")
            val desc = "Subject: $subjectName\\nDuration: ${session.durationMinutes} mins\\nPhase: ${session.mode}\\nStatus: ${if (session.isCompleted) "Completed" else "Planned"}"
            sb.append("DESCRIPTION:${escapeIcs(desc)}\r\n")
            sb.append("STATUS:CONFIRMED\r\n")
            sb.append("TRANSP:OPAQUE\r\n")
            sb.append("END:VEVENT\r\n")
        }

        sb.append("END:VCALENDAR\r\n")
        return sb.toString()
    }

    /**
     * Converts local date ("yyyy-MM-dd") and time ("HH:mm") into UTC strings for iCalendar
     */
    fun formatSessionTimes(
        dateStr: String,
        startTimeStr: String,
        durationMinutes: Int
    ): Pair<String, String> {
        val inputFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).apply {
            timeZone = TimeZone.getDefault()
        }
        val utcFormat = SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }

        val parsedDate = try {
            inputFormat.parse("$dateStr $startTimeStr") ?: Date()
        } catch (_: Exception) {
            Date()
        }

        val startUtc = utcFormat.format(parsedDate)
        val endUtc = utcFormat.format(Date(parsedDate.time + (durationMinutes * 60 * 1000L)))
        return Pair(startUtc, endUtc)
    }

    /**
     * Escapes text characters per RFC 5545 section 3.3.11
     */
    fun escapeIcs(text: String): String {
        return text
            .replace("\\", "\\\\")
            .replace(";", "\\;")
            .replace(",", "\\,")
            .replace("\r", "")
            .replace("\n", "\\n")
    }

    /**
     * Creates an Android Intent to share the generated .ics file or calendar data
     */
    fun createShareIntent(
        context: Context,
        icsContent: String,
        fileName: String = "focus_study_schedule.ics"
    ): Intent {
        return try {
            val file = File(context.cacheDir, fileName)
            FileOutputStream(file).use { it.write(icsContent.toByteArray(Charsets.UTF_8)) }

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/calendar"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Focus Study Timetable (.ics)")
                putExtra(Intent.EXTRA_TEXT, "Here is my study schedule exported from Focus Study.")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            Intent.createChooser(shareIntent, "Export Timetable to Calendar")
        } catch (e: Exception) {
            // Fallback plain-text share
            val fallback = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, icsContent)
                putExtra(Intent.EXTRA_SUBJECT, "Focus Study Timetable (.ics)")
            }
            Intent.createChooser(fallback, "Share Study Timetable")
        }
    }

    /**
     * Creates an Intent to insert a single session into Android native CalendarContract
     */
    fun createInsertSessionIntent(
        session: StudySession,
        topicName: String,
        subjectName: String
    ): Intent {
        val inputFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).apply {
            timeZone = TimeZone.getDefault()
        }
        val startTimeMs = try {
            inputFormat.parse("${session.scheduledDate} ${session.startTime}")?.time ?: System.currentTimeMillis()
        } catch (_: Exception) {
            System.currentTimeMillis()
        }
        val endTimeMs = startTimeMs + (session.durationMinutes * 60 * 1000L)

        return Intent(Intent.ACTION_INSERT).apply {
            data = CalendarContract.Events.CONTENT_URI
            putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, startTimeMs)
            putExtra(CalendarContract.EXTRA_EVENT_END_TIME, endTimeMs)
            putExtra(CalendarContract.Events.TITLE, "Study: $topicName")
            putExtra(CalendarContract.Events.DESCRIPTION, "Subject: $subjectName · Duration: ${session.durationMinutes}m · Phase: ${session.mode}")
            putExtra(CalendarContract.Events.AVAILABILITY, CalendarContract.Events.AVAILABILITY_BUSY)
        }
    }
}
