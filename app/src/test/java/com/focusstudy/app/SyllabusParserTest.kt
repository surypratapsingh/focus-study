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

    @Test
    fun parseText_filtersAdministrativeBoilerplate() {
        val raw = """
Course: Distributed Systems
Instructor: Dr. Alan Turing
Office Hours: Mon/Wed 2:00 PM - 4:00 PM
Email: alan.turing@university.edu
Credits: 4
Grading Policy: 30% Midterms, 40% Final Exam, 30% Homework
Textbook: Distributed Systems by Tanenbaum

Module 1: Consensus and Replication
1.1 Paxos and Raft Consensus
1.2 Byzantine Fault Tolerance
1.3 Vector Clocks and Causality

Attendance Policy: 80% mandatory attendance
Academic Integrity: Plagiarism is strictly prohibited
        """.trimIndent()

        val parsed = SyllabusParser.parseText(raw)
        assertEquals(3, parsed.size)
        assertEquals("Paxos and Raft Consensus", parsed[0].name)
        assertEquals("Byzantine Fault Tolerance", parsed[1].name)
        assertEquals("Vector Clocks and Causality", parsed[2].name)
        assertEquals("Distributed Systems", parsed[0].subjectName)
        assertEquals("Module 1: Consensus and Replication", parsed[0].unitTitle)
    }

    @Test
    fun pdfTextExtractor_decodesUtf16BeHexStrings() {
        // "Hello" in UTF-16BE with BOM FEFF: 0048 0065 006C 006C 006F
        val hexWithBom = "FEFF00480065006C006C006F"
        val decodedWithBom = com.focusstudy.app.feature.syllabus.PdfTextExtractor.decodeHexString(hexWithBom)
        assertEquals("Hello", decodedWithBom)

        // Standard ASCII hex: 48656C6C6F
        val asciiHex = "48656C6C6F"
        val decodedAscii = com.focusstudy.app.feature.syllabus.PdfTextExtractor.decodeHexString(asciiHex)
        assertEquals("Hello", decodedAscii)
    }
}
