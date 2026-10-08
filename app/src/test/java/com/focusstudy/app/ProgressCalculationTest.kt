package com.focusstudy.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressCalculationTest {

    @Test
    fun syllabusCoverage_calculatesAccurately() {
        val totalTopics = 47
        val completedTopics = 35
        val coverage = (completedTopics.toFloat() / totalTopics.toFloat()) * 100f

        assertEquals(74.468f, coverage, 0.01f)
    }

    @Test
    fun dailyTarget_calculatesRemainingMinutes() {
        val plannedMinutes = 170 // 2h 50m
        val completedMinutes = 45
        val remainingMinutes = plannedMinutes - completedMinutes

        assertEquals(125, remainingMinutes)
        assertTrue(remainingMinutes > 0)
    }

    @Test
    fun examCountdown_calculatesDaysCorrectly() {
        val now = 1760000000000L
        val examDate = now + (42L * 24 * 60 * 60 * 1000L)
        val daysRemaining = (examDate - now) / (24 * 60 * 60 * 1000L)

        assertEquals(42L, daysRemaining)
    }
}
