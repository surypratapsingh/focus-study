package com.focusstudy.app

import com.focusstudy.app.feature.syllabus.SyllabusParser
import org.junit.Assert.*
import org.junit.Test

class SyllabusParserTest {

    @Test
    fun sampleSyllabus_parsesSuccessfully() {
        val sample = SyllabusParser.sampleComputerScienceSyllabus()
        val parsed = SyllabusParser.parseText(sample)

        assertTrue(parsed.isNotEmpty())
        assertTrue(parsed.size >= 10)

        // Check that subjects and units are preserved
        val subjects = parsed.map { it.subjectName }.distinct()
        assertTrue(subjects.contains("Computer Science Finals"))

        val units = parsed.map { it.unitTitle }.distinct()
        assertTrue(units.any { it.contains("Unit 1: Database Management Systems") })
        assertTrue(units.any { it.contains("Unit 2: Computer Networks") })
        assertTrue(units.any { it.contains("Unit 3: Probability and Statistics") })
    }

    @Test
    fun parseText_cleansNumberedBullets() {
        val raw = """
Subject: Mathematics
Unit 1: Calculus
1. Differential Equations
2. Integral Calculus
* Limits and Continuity
- Multivariable Optimization
        """.trimIndent()

        val parsed = SyllabusParser.parseText(raw)
        assertEquals(4, parsed.size)
        assertEquals("Differential Equations", parsed[0].name)
        assertEquals("Integral Calculus", parsed[1].name)
        assertEquals("Limits and Continuity", parsed[2].name)
        assertEquals("Multivariable Optimization", parsed[3].name)
    }

    @Test
    fun parseText_heuristicsAssignDifficultyAndMinutes() {
        val raw = """
Unit 1: Algorithms
Basic Introduction to Sorting
Advanced Graph Theory Analysis
        """.trimIndent()

        val parsed = SyllabusParser.parseText(raw)
        assertEquals(2, parsed.size)
        assertEquals("easy", parsed[0].difficulty)
        assertEquals(45, parsed[0].estimatedMinutes)

        assertEquals("hard", parsed[1].difficulty)
        assertEquals(90, parsed[1].estimatedMinutes)
    }
}
