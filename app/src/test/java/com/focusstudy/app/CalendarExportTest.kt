package com.focusstudy.app

import com.focusstudy.app.core.calendar.CalendarExportManager
import com.focusstudy.app.core.database.entity.Exam
import com.focusstudy.app.core.database.entity.StudySession
import com.focusstudy.app.core.database.entity.Subject
import com.focusstudy.app.core.database.entity.Topic
import org.junit.Assert.*
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.*

class CalendarExportTest {

    @Test
    fun testEscapeIcs_SpecialCharacters() {
        val raw = "Math, Science; Physics \\ Chemistry\nNew line"
        val escaped = CalendarExportManager.escapeIcs(raw)

        assertTrue(escaped.contains("\\,"))
        assertTrue(escaped.contains("\\;"))
        assertTrue(escaped.contains("\\\\"))
        assertTrue(escaped.contains("\\n"))
        assertFalse(escaped.contains("\n"))
    }

    @Test
    fun testFormatSessionTimes_UtcCalculation() {
        val (startUtc, endUtc) = CalendarExportManager.formatSessionTimes(
            dateStr = "2026-10-15",
            startTimeStr = "09:00",
            durationMinutes = 60
        )

        assertTrue(startUtc.endsWith("Z"))
        assertTrue(endUtc.endsWith("Z"))
        assertTrue(startUtc.startsWith("20261015T"))
        assertNotEquals(startUtc, endUtc)
    }

    @Test
    fun testGenerateIcsCalendar_ValidCalendarStructure() {
        val exam = Exam(
            id = "exam-101",
            title = "Final Semester Examination",
            examType = "University Exam",
            examDateUtc = 1792000000000L,
            targetScore = 90
        )

        val subject = Subject(
            id = "subj-1",
            examId = exam.id,
            name = "Computer Science"
        )

        val topic = Topic(
            id = "topic-1",
            unitId = "unit-1",
            subjectId = subject.id,
            name = "Algorithms and Complexity"
        )

        val session = StudySession(
            id = "sess-1",
            planDayId = "day-1",
            subjectId = subject.id,
            topicId = topic.id,
            scheduledDate = "2026-10-15",
            startTime = "10:00",
            durationMinutes = 90,
            mode = "deep_work",
            isCompleted = false
        )

        val ics = CalendarExportManager.generateIcsCalendar(
            exam = exam,
            sessions = listOf(session),
            topicsMap = mapOf(topic.id to topic),
            subjectsMap = mapOf(subject.id to subject)
        )

        // Verify RFC 5545 core structure
        assertTrue(ics.startsWith("BEGIN:VCALENDAR"))
        assertTrue(ics.endsWith("END:VCALENDAR\r\n"))
        assertTrue(ics.contains("VERSION:2.0"))
        assertTrue(ics.contains("PRODID:-//FocusStudy//Focus Study OS v1.0.0//EN"))
        assertTrue(ics.contains("CALSCALE:GREGORIAN"))
        assertTrue(ics.contains("METHOD:PUBLISH"))

        // Verify Exam Event
        assertTrue(ics.contains("UID:exam-exam-101@focusstudy.app"))
        assertTrue(ics.contains("SUMMARY:🎯 EXAM: Final Semester Examination"))
        assertTrue(ics.contains("DTSTART;VALUE=DATE:"))

        // Verify Study Session Event
        assertTrue(ics.contains("UID:session-sess-1@focusstudy.app"))
        assertTrue(ics.contains("SUMMARY:Study: Algorithms and Complexity"))
        assertTrue(ics.contains("Subject: Computer Science"))
        assertTrue(ics.contains("Duration: 90 mins"))
        assertTrue(ics.contains("Phase: deep_work"))
        assertTrue(ics.contains("STATUS:CONFIRMED"))
    }

    @Test
    fun testGenerateIcsCalendar_EmptySessions() {
        val exam = Exam(
            id = "exam-202",
            title = "Bar Exam",
            examType = "Licensing",
            examDateUtc = 1792500000000L
        )

        val ics = CalendarExportManager.generateIcsCalendar(
            exam = exam,
            sessions = emptyList()
        )

        assertTrue(ics.contains("BEGIN:VCALENDAR"))
        assertTrue(ics.contains("END:VCALENDAR"))
        assertTrue(ics.contains("SUMMARY:🎯 EXAM: Bar Exam"))
        assertFalse(ics.contains("UID:session-"))
    }
}
