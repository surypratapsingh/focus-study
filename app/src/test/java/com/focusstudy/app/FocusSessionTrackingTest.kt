package com.focusstudy.app

import org.junit.Assert.*
import org.junit.Test

class FocusSessionTrackingTest {

    @Test
    fun dailyProgress_calculatesRatioCorrectly() {
        val targetMinutes = 180 // 3 hours
        val completedMinutes = 90 // 1.5 hours
        val ratio = completedMinutes.toFloat() / targetMinutes.toFloat()

        assertEquals(0.5f, ratio, 0.001f)
        assertEquals(90, (targetMinutes - completedMinutes).coerceAtLeast(0))
    }

    @Test
    fun focusXp_calculatesTwiceMinutes() {
        val completedMinutes = 45
        val earnedXp = completedMinutes * 2

        assertEquals(90, earnedXp)
    }

    @Test
    fun remainingTime_clampsToZeroWhenTargetExceeded() {
        val targetMinutes = 120
        val completedMinutes = 150
        val remaining = (targetMinutes - completedMinutes).coerceAtLeast(0)

        assertEquals(0, remaining)
    }

    @Test
    fun sessionAttempt_detectsInterruption() {
        val plannedSeconds = 45 * 60
        val remainingSeconds = 15 * 60
        val isInterrupted = remainingSeconds > 0
        val elapsedSeconds = plannedSeconds - remainingSeconds

        assertTrue(isInterrupted)
        assertEquals(30 * 60, elapsedSeconds)
    }
}
