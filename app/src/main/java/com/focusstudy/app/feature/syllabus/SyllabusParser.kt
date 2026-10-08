package com.focusstudy.app.feature.syllabus

import com.focusstudy.app.core.database.entity.Subject
import com.focusstudy.app.core.database.entity.Topic
import com.focusstudy.app.core.database.entity.UnitEntity
import com.focusstudy.app.core.security.FileImportValidator
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.UUID

data class ParsedTopicItem(
    val id: String = UUID.randomUUID().toString(),
    var name: String,
    var unitTitle: String,
    var subjectName: String,
    var difficulty: String = "medium", // easy, medium, hard
    var importance: String = "normal", // critical, high, normal, low
    var estimatedMinutes: Int = 60
)

object SyllabusParser {

    /**
     * Parses unstructured or semi-structured syllabus text into hierarchical topics.
     * Detects "Subject:", "Unit", "Module", "Chapter", bullet points, numbered items.
     */
    fun parseText(
        rawText: String,
        defaultSubjectName: String = "General Subject"
    ): List<ParsedTopicItem> {
        val lines = rawText.lines().map { it.trim() }.filter { it.isNotBlank() }
        val topics = mutableListOf<ParsedTopicItem>()

        var currentSubject = defaultSubjectName
        var currentUnit = "General Topics"

        for (line in lines) {
            val lower = line.lowercase()

            when {
                lower.startsWith("subject:") || lower.startsWith("course:") -> {
                    currentSubject = line.substringAfter(":").trim().ifBlank { defaultSubjectName }
                    currentUnit = "Unit 1: Fundamentals"
                }
                lower.startsWith("unit") || lower.startsWith("module") || lower.startsWith("chapter") || lower.startsWith("section") -> {
                    currentUnit = line.trim()
                }
                else -> {
                    // Clean line from bullet points or numbers (e.g. "1. Topic", "- Topic", "* Topic")
                    val cleanedName = line
                        .replaceFirst(Regex("^[-*•\\d+.)\\s]+"), "")
                        .trim()

                    if (cleanedName.length >= 3) {
                        val importance = if (lower.contains("core") || lower.contains("advanced") || lower.contains("important")) "high" else "normal"
                        val difficulty = if (lower.contains("intro") || lower.contains("basic") || lower.contains("overview")) "easy"
                        else if (lower.contains("advanced") || lower.contains("design") || lower.contains("analysis")) "hard"
                        else "medium"

                        topics.add(
                            ParsedTopicItem(
                                name = cleanedName,
                                unitTitle = currentUnit,
                                subjectName = currentSubject,
                                difficulty = difficulty,
                                importance = importance,
                                estimatedMinutes = if (difficulty == "hard") 90 else if (difficulty == "easy") 45 else 60
                            )
                        )
                    }
                }
            }
        }

        return topics
    }

    /**
     * Provides a curated template syllabus for university CS/IT semester exam
     */
    fun sampleComputerScienceSyllabus(): String {
        return """
Subject: Computer Science Finals

Unit 1: Database Management Systems
Relational Database Concepts
SQL Query Optimization
Normalization & Normal Forms (1NF, 2NF, 3NF, BCNF)
Transactions and ACID Properties
Concurrency Control & Locking

Unit 2: Computer Networks
OSI and TCP/IP Reference Models
Data Link Layer & Sliding Window
IP Addressing & Subnetting
Routing Algorithms (Distance Vector, Link State)
Network Security Basics

Unit 3: Probability and Statistics
Random Variables & Probability Distributions
Mean, Variance and Standard Deviation
Hypothesis Testing and p-values
        """.trimIndent()
    }

    /**
     * Parses structured schema-constrained JSON output from Gemini syllabus extraction (FSTUDY-003-05)
     */
    fun parseStructuredAiResponse(jsonString: String): List<ParsedTopicItem> {
        val json = Json { ignoreUnknownKeys = true }
        return try {
            val response = json.decodeFromString<AiSyllabusResponse>(jsonString)
            val items = mutableListOf<ParsedTopicItem>()

            for (unit in response.units) {
                for (topic in unit.topics) {
                    val cleanDiff = when (topic.difficulty.lowercase()) {
                        "easy" -> "easy"
                        "hard" -> "hard"
                        else -> "medium"
                    }
                    val cleanImp = when (topic.importance.lowercase()) {
                        "critical" -> "critical"
                        "high" -> "high"
                        "low" -> "low"
                        else -> "normal"
                    }

                    items.add(
                        ParsedTopicItem(
                            name = topic.name.trim(),
                            unitTitle = unit.unitTitle.trim(),
                            subjectName = response.subjectName.trim(),
                            difficulty = cleanDiff,
                            importance = cleanImp,
                            estimatedMinutes = topic.estimatedMinutes.coerceIn(15, 240)
                        )
                    )
                }
            }
            items
        } catch (e: Exception) {
            // Fallback to text parsing if JSON parsing fails
            parseText(jsonString)
        }
    }

    /**
     * Ingests documents (PDF/images/text) with security validation and sanitization
     */
    fun parseDocument(content: String, mimeType: String? = null): List<ParsedTopicItem> {
        val validation = FileImportValidator.sanitizeExtractedText(content)
        val textToParse = validation.sanitizedText ?: content
        return if (textToParse.trim().startsWith("{")) {
            parseStructuredAiResponse(textToParse)
        } else {
            parseText(textToParse)
        }
    }
}

@Serializable
data class AiSyllabusResponse(
    val subjectName: String,
    val units: List<AiSyllabusUnitDto> = emptyList()
)

@Serializable
data class AiSyllabusUnitDto(
    val unitTitle: String,
    val topics: List<AiSyllabusTopicDto> = emptyList()
)

@Serializable
data class AiSyllabusTopicDto(
    val name: String,
    val difficulty: String = "medium", // easy, medium, hard
    val importance: String = "normal", // critical, high, normal, low
    val estimatedMinutes: Int = 60
)
