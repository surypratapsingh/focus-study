package com.focusstudy.app.feature.recall

import com.focusstudy.app.core.database.entity.Subtopic
import com.focusstudy.app.core.database.entity.Topic
import com.focusstudy.app.core.database.entity.Subject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

data class ActiveRecallCard(
    val id: String = UUID.randomUUID().toString(),
    val topicId: String,
    val topicName: String,
    val subjectName: String,
    val question: String,
    val answer: String,
    val hint: String? = null,
    val conceptTag: String = "Core Concept",
    val difficulty: String = "medium",
    var masteryLevel: Int = 0 // 0: unreviewed, 1: hard (+10 XP), 2: good (+20 XP), 3: easy/mastered (+30 XP)
)

data class ActiveRecallDeck(
    val topicId: String,
    val topicName: String,
    val subjectName: String,
    val cards: List<ActiveRecallCard>,
    val isAiGenerated: Boolean = false,
    val masteryScorePercent: Int = 0
)

object ActiveRecallEngine {

    /**
     * Generates a 5-card active recall deck using 100% offline pedagogical heuristics ($0 cost, 0 dependencies).
     */
    fun generateHeuristicDeck(
        topic: Topic,
        subject: Subject?,
        subtopics: List<Subtopic> = emptyList()
    ): ActiveRecallDeck {
        val subjectName = subject?.name ?: "General Subject"
        val topicName = topic.name
        val difficulty = topic.difficulty

        val cards = mutableListOf<ActiveRecallCard>()

        // 1. Definition & Core Mechanics
        cards.add(
            ActiveRecallCard(
                topicId = topic.id,
                topicName = topicName,
                subjectName = subjectName,
                question = "What is the primary definition and core purpose of \"$topicName\" in $subjectName?",
                answer = "$topicName is a foundational concept in $subjectName. Its primary purpose is to address key requirements and optimize systemic problem-solving within the domain. Understanding it requires recognizing its role in maintaining correctness, efficiency, and structural clarity.",
                hint = "Think about the primary problem $topicName solves in $subjectName.",
                conceptTag = "Core Definition",
                difficulty = difficulty
            )
        )

        // 2. Underlying Mechanisms & Execution Flow
        cards.add(
            ActiveRecallCard(
                topicId = topic.id,
                topicName = topicName,
                subjectName = subjectName,
                question = "Walk through the key stages or execution steps involved when applying \"$topicName\".",
                answer = "The process for $topicName typically follows three sequential phases:\n1. Input formulation and precondition validation.\n2. Core execution and state transformation according to defined rules.\n3. Output verification and integration with dependent components.",
                hint = "Break the workflow into sequential phases from start to finish.",
                conceptTag = "Mechanics",
                difficulty = difficulty
            )
        )

        // 3. Subtopics / Architecture Breakdown
        if (subtopics.isNotEmpty()) {
            val subtopicNames = subtopics.take(3).joinToString(", ") { it.name }
            cards.add(
                ActiveRecallCard(
                    topicId = topic.id,
                    topicName = topicName,
                    subjectName = subjectName,
                    question = "How do key components such as $subtopicNames integrate within \"$topicName\"?",
                    answer = "These subtopics represent constituent pillars of $topicName. Each component handles specialized sub-tasks, ensuring modularity, clear separation of concerns, and reliable execution.",
                    hint = "Consider how these sub-components contribute to the whole topic.",
                    conceptTag = "Modular Breakdown",
                    difficulty = difficulty
                )
            )
        } else {
            cards.add(
                ActiveRecallCard(
                    topicId = topic.id,
                    topicName = topicName,
                    subjectName = subjectName,
                    question = "What are the essential building blocks and prerequisites required to master \"$topicName\"?",
                    answer = "Mastery requires understanding prerequisite definitions in $subjectName, recognizing the governing axioms/rules, and practicing hands-on problem sets to identify edge cases.",
                    hint = "What foundational knowledge must you know before approaching $topicName?",
                    conceptTag = "Prerequisites",
                    difficulty = difficulty
                )
            )
        }

        // 4. Trade-offs, Edge Cases & Constraints
        cards.add(
            ActiveRecallCard(
                topicId = topic.id,
                topicName = topicName,
                subjectName = subjectName,
                question = "What are the primary trade-offs, limitations, or common mistakes when using \"$topicName\"?",
                answer = "Common pitfalls include failing to validate edge cases, misestimating complexity overhead, and incorrectly assuming optimal behavior across non-standard inputs. Proper defensive validation is essential.",
                hint = "What could go wrong or cause unexpected behavior?",
                conceptTag = "Trade-offs & Constraints",
                difficulty = difficulty
            )
        )

        // 5. The Feynman Synthesis (Plain Language Retrieval)
        cards.add(
            ActiveRecallCard(
                topicId = topic.id,
                topicName = topicName,
                subjectName = subjectName,
                question = "How would you explain \"$topicName\" to a beginner without using technical jargon?",
                answer = "In simple terms, $topicName acts as a structured blueprint in $subjectName that ensures things run reliably without breaking down, translating complex problems into manageable steps.",
                hint = "Explain the essence in one or two plain sentences.",
                conceptTag = "Feynman Challenge",
                difficulty = difficulty
            )
        )

        return ActiveRecallDeck(
            topicId = topic.id,
            topicName = topicName,
            subjectName = subjectName,
            cards = cards,
            isAiGenerated = false
        )
    }

    /**
     * Generates a 5-card active recall deck using BYOK Gemini 1.5 Flash structured output.
     * Automatically falls back to offline heuristic deck if offline, keyless, or on network error.
     */
    suspend fun generateAiDeck(
        topic: Topic,
        subject: Subject?,
        subtopics: List<Subtopic> = emptyList(),
        apiKey: String
    ): ActiveRecallDeck = withContext(Dispatchers.IO) {
        val subjectName = subject?.name ?: "General Subject"
        val topicName = topic.name

        if (apiKey.isBlank()) {
            return@withContext generateHeuristicDeck(topic, subject, subtopics)
        }

        try {
            val subtopicsStr = if (subtopics.isNotEmpty()) {
                "Subtopics to cover: ${subtopics.joinToString(", ") { it.name }}"
            } else ""

            val prompt = """
                You are an expert academic tutor specializing in Active Recall flashcards.
                Create exactly 5 high-yield flashcard questions and answers for:
                Subject: $subjectName
                Topic: $topicName
                Difficulty: ${topic.difficulty}
                $subtopicsStr

                Each card must test active memory retrieval.
                Return ONLY valid JSON matching this schema:
                {
                  "cards": [
                    {
                      "question": "string",
                      "answer": "string",
                      "hint": "string",
                      "conceptTag": "string",
                      "difficulty": "${topic.difficulty}"
                    }
                  ]
                }
            """.trimIndent()

            val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=$apiKey"
            val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15000
                readTimeout = 20000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }

            val requestBody = JSONObject().apply {
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().put("text", prompt))
                        })
                    })
                })
                put("generationConfig", JSONObject().apply {
                    put("responseMimeType", "application/json")
                    put("temperature", 0.3)
                })
            }

            conn.outputStream.use { it.write(requestBody.toString().toByteArray()) }

            val responseCode = conn.responseCode
            if (responseCode == 200) {
                val responseText = conn.inputStream.bufferedReader().use { it.readText() }
                val rootJson = JSONObject(responseText)
                val candidates = rootJson.optJSONArray("candidates")
                val textCandidate = candidates?.optJSONObject(0)
                    ?.optJSONObject("content")
                    ?.optJSONArray("parts")
                    ?.optJSONObject(0)
                    ?.optString("text")

                if (!textCandidate.isNullOrBlank()) {
                    val parsedJson = JSONObject(textCandidate)
                    val cardsArray = parsedJson.optJSONArray("cards")
                    if (cardsArray != null && cardsArray.length() > 0) {
                        val aiCards = mutableListOf<ActiveRecallCard>()
                        for (i in 0 until cardsArray.length()) {
                            val cardObj = cardsArray.getJSONObject(i)
                            aiCards.add(
                                ActiveRecallCard(
                                    topicId = topic.id,
                                    topicName = topicName,
                                    subjectName = subjectName,
                                    question = cardObj.optString("question", "What is $topicName?"),
                                    answer = cardObj.optString("answer", "Core principle of $topicName."),
                                    hint = cardObj.optString("hint").takeIf { it.isNotBlank() },
                                    conceptTag = cardObj.optString("conceptTag", "Active Recall"),
                                    difficulty = cardObj.optString("difficulty", topic.difficulty)
                                )
                            )
                        }
                        return@withContext ActiveRecallDeck(
                            topicId = topic.id,
                            topicName = topicName,
                            subjectName = subjectName,
                            cards = aiCards,
                            isAiGenerated = true
                        )
                    }
                }
            }
        } catch (e: Exception) {
            // Graceful fallback to offline heuristics
        }

        generateHeuristicDeck(topic, subject, subtopics)
    }

    /**
     * Calculates the overall mastery score for a reviewed deck (0% - 100%).
     */
    fun calculateDeckMastery(cards: List<ActiveRecallCard>): Int {
        if (cards.isEmpty()) return 0
        val maxScore = cards.size * 3 // 3 is "Easy / Mastered"
        var earnedScore = 0
        for (card in cards) {
            earnedScore += card.masteryLevel
        }
        return ((earnedScore.toDouble() / maxScore.toDouble()) * 100).toInt().coerceIn(0, 100)
    }

    /**
     * Calculates earned Scholar XP from active recall cards reviewed (+10 for Hard, +20 for Good, +30 for Mastered).
     */
    fun calculateEarnedXp(cards: List<ActiveRecallCard>): Int {
        var totalXp = 0
        for (card in cards) {
            totalXp += when (card.masteryLevel) {
                3 -> 30 // Easy / Mastered
                2 -> 20 // Good
                1 -> 10 // Hard
                else -> 0
            }
        }
        return totalXp
    }
}
