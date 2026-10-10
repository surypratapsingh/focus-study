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

    private val BOILERPLATE_KEYWORDS = listOf(
        "instructor", "professor", "office hours", "email:", "credits:", "prerequisites",
        "grading policy", "grading breakdown", "grade scale", "attendance policy",
        "textbook", "reference book", "recommended reading", "isbn",
        "academic integrity", "academic honesty", "plagiarism", "canvas", "blackboard",
        "moodle", "classroom:", "zoom link", "exam date:", "submission guideline"
    )

    /**
     * Parses unstructured or semi-structured syllabus text into hierarchical topics.
     * Detects "Subject:", "Unit", "Module", "Chapter", "Part", roman numerals, bullet points, numbered items.
     * Automatically filters out course administration boilerplate (grading, office hours, textbooks).
     */
    fun parseText(
        rawText: String,
        defaultSubjectName: String = "General Subject"
    ): List<ParsedTopicItem> {
        val lines = rawText.lines().map { it.trim() }.filter { it.isNotBlank() }
        val topics = mutableListOf<ParsedTopicItem>()

        var currentSubject = defaultSubjectName
        var currentUnit = "Unit 1: Core Concepts"
        var unitCounter = 1

        val unitRegex = Regex("""^(?:unit|module|chapter|section|part)\s*(?:[ivxlcdm\d]+|[:\s-])""", RegexOption.IGNORE_CASE)

        for (line in lines) {
            val lower = line.lowercase()

            // Skip administrative boilerplate
            if (BOILERPLATE_KEYWORDS.any { lower.contains(it) }) {
                continue
            }

            when {
                lower.startsWith("subject:") || lower.startsWith("course:") || lower.startsWith("class:") -> {
                    val extracted = line.substringAfter(":").trim()
                    if (extracted.isNotBlank()) {
                        currentSubject = extracted
                        currentUnit = "Unit 1: Fundamentals"
                        unitCounter = 1
                    }
                }
                unitRegex.containsMatchIn(line) -> {
                    currentUnit = line.trim()
                }
                else -> {
                    // Clean line from bullet points, numbering (e.g. "1.1 Topic", "1. Topic", "• Topic", "* Topic", "(a) Topic")
                    val cleanedName = line
                        .replaceFirst(Regex("""^[-*•\s]+"""), "")
                        .replaceFirst(Regex("""^\(?[\d.]+\)?\s*"""), "")
                        .replaceFirst(Regex("""^\(?[a-zA-Z]\)\s*"""), "")
                        .trim()

                    // Ensure plausible topic length and meaningful characters
                    if (cleanedName.length >= 3 && cleanedName.any { it.isLetter() }) {
                        val importance = if (lower.contains("core") || lower.contains("advanced") || lower.contains("important") || lower.contains("critical")) "high" else "normal"
                        val difficulty = if (lower.contains("intro") || lower.contains("basic") || lower.contains("overview") || lower.contains("fundamental")) "easy"
                        else if (lower.contains("advanced") || lower.contains("design") || lower.contains("analysis") || lower.contains("complex")) "hard"
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
    fun parseDocument(content: String, mimeType: String? = null, examTitle: String = "General Subject"): List<ParsedTopicItem> {
        val validation = FileImportValidator.sanitizeExtractedText(content)
        val textToParse = validation.sanitizedText ?: content
        return if (textToParse.trim().startsWith("{")) {
            parseStructuredAiResponse(textToParse)
        } else {
            parseText(textToParse, examTitle)
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
