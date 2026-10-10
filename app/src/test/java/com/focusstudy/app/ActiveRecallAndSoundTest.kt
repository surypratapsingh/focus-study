package com.focusstudy.app

import com.focusstudy.app.core.ai.AiCoachContext
import com.focusstudy.app.core.ai.AiCoachService
import com.focusstudy.app.core.audio.FocusSoundManager
import com.focusstudy.app.core.audio.FocusSoundType
import com.focusstudy.app.core.database.entity.Subject
import com.focusstudy.app.core.database.entity.Subtopic
import com.focusstudy.app.core.database.entity.Topic
import com.focusstudy.app.feature.recall.ActiveRecallCard
import com.focusstudy.app.feature.recall.ActiveRecallEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class ActiveRecallAndSoundTest {

    private val sampleSubject = Subject(
        id = UUID.randomUUID().toString(),
        examId = "exam_1",
        name = "Operating Systems",
        knowledgeLevel = "intermediate",
        importance = "high"
    )

    private val sampleTopic = Topic(
        id = UUID.randomUUID().toString(),
        unitId = "unit_1",
        subjectId = sampleSubject.id,
        name = "Virtual Memory & Paging",
        difficulty = "hard",
        importance = "critical",
        estimatedEffortMinutes = 60
    )

    @Test
    fun testGenerateHeuristicDeckProduces5ValidCards() {
        val deck = ActiveRecallEngine.generateHeuristicDeck(
            topic = sampleTopic,
            subject = sampleSubject,
            subtopics = emptyList()
        )

        assertEquals(sampleTopic.id, deck.topicId)
        assertEquals("Virtual Memory & Paging", deck.topicName)
        assertEquals("Operating Systems", deck.subjectName)
        assertFalse(deck.isAiGenerated)
        assertEquals(5, deck.cards.size)

        deck.cards.forEach { card ->
            assertEquals(sampleTopic.id, card.topicId)
            assertTrue("Question must not be blank", card.question.isNotBlank())
            assertTrue("Answer must not be blank", card.answer.isNotBlank())
            assertNotNull("Hint should be provided", card.hint)
            assertTrue("Concept tag must be provided", card.conceptTag.isNotBlank())
            assertEquals("hard", card.difficulty)
            assertEquals(0, card.masteryLevel)
        }

        // Verify specific card pedagogical roles
        assertEquals("Core Definition", deck.cards[0].conceptTag)
        assertEquals("Mechanics", deck.cards[1].conceptTag)
        assertEquals("Prerequisites", deck.cards[2].conceptTag)
        assertEquals("Trade-offs & Constraints", deck.cards[3].conceptTag)
        assertEquals("Feynman Challenge", deck.cards[4].conceptTag)
    }

    @Test
    fun testHeuristicDeckWithSubtopicsIncludesModularBreakdown() {
        val subtopics = listOf(
            Subtopic(id = "sub_1", topicId = sampleTopic.id, name = "Page Table Entries"),
            Subtopic(id = "sub_2", topicId = sampleTopic.id, name = "TLB Translation"),
            Subtopic(id = "sub_3", topicId = sampleTopic.id, name = "Page Fault Handling")
        )

        val deck = ActiveRecallEngine.generateHeuristicDeck(
            topic = sampleTopic,
            subject = sampleSubject,
            subtopics = subtopics
        )

        assertEquals(5, deck.cards.size)
        val componentCard = deck.cards[2]
        assertEquals("Modular Breakdown", componentCard.conceptTag)
        assertTrue(componentCard.question.contains("Page Table Entries"))
        assertTrue(componentCard.question.contains("TLB Translation"))
    }

    @Test
    fun testDeckMasteryScoreCalculation() {
        val cards = listOf(
            ActiveRecallCard(topicId = "t1", topicName = "T1", subjectName = "S1", question = "Q1", answer = "A1", masteryLevel = 0),
            ActiveRecallCard(topicId = "t1", topicName = "T1", subjectName = "S1", question = "Q2", answer = "A2", masteryLevel = 0)
        )
        // 0% mastery when unreviewed
        assertEquals(0, ActiveRecallEngine.calculateDeckMastery(cards))

        // Fully mastered (3 + 3 = 6 / 6)
        cards[0].masteryLevel = 3
        cards[1].masteryLevel = 3
        assertEquals(100, ActiveRecallEngine.calculateDeckMastery(cards))

        // Partial mastery (1 + 2 = 3 / 6) = 50%
        cards[0].masteryLevel = 1
        cards[1].masteryLevel = 2
        assertEquals(50, ActiveRecallEngine.calculateDeckMastery(cards))
    }

    @Test
    fun testEarnedXpCalculation() {
        val cards = listOf(
            ActiveRecallCard(topicId = "t1", topicName = "T1", subjectName = "S1", question = "Q1", answer = "A1", masteryLevel = 1), // 10 XP
            ActiveRecallCard(topicId = "t1", topicName = "T1", subjectName = "S1", question = "Q2", answer = "A2", masteryLevel = 2), // 20 XP
            ActiveRecallCard(topicId = "t1", topicName = "T1", subjectName = "S1", question = "Q3", answer = "A3", masteryLevel = 3), // 30 XP
            ActiveRecallCard(topicId = "t1", topicName = "T1", subjectName = "S1", question = "Q4", answer = "A4", masteryLevel = 3), // 30 XP
            ActiveRecallCard(topicId = "t1", topicName = "T1", subjectName = "S1", question = "Q5", answer = "A5", masteryLevel = 2)  // 20 XP
        )

        val totalXp = ActiveRecallEngine.calculateEarnedXp(cards)
        assertEquals(110, totalXp) // 10 + 20 + 30 + 30 + 20
    }

    @Test
    fun testAiDeckFallbackOnBlankKey() = runBlocking {
        val deck = ActiveRecallEngine.generateAiDeck(
            topic = sampleTopic,
            subject = sampleSubject,
            subtopics = emptyList(),
            apiKey = ""
        )

        assertNotNull(deck)
        assertEquals(5, deck.cards.size)
        assertFalse(deck.isAiGenerated)
    }

    @Test
    fun testFocusSoundManagerLifecycleAndTypes() {
        assertEquals("Off", FocusSoundType.OFF.title)
        assertEquals("White Noise", FocusSoundType.WHITE_NOISE.title)
        assertEquals("Brown Noise", FocusSoundType.BROWN_NOISE.title)
        assertEquals("10Hz Alpha", FocusSoundType.ALPHA_WAVES.title)

        // Verify start and stop execute safely on JVM without crashing
        FocusSoundManager.play(FocusSoundType.WHITE_NOISE)
        FocusSoundManager.stop()
        assertEquals(FocusSoundType.OFF, FocusSoundManager.currentSound.value)
    }

    @Test
    fun testAiCoachQuizPromptResponse() {
        val coach = AiCoachService(db = null, userPreferencesManager = null)
        val context = AiCoachContext(
            examTitle = "Computer Systems",
            daysUntilExam = 14,
            todayTargetMinutes = 120,
            todayCompletedMinutes = 45,
            nextTopicName = "Virtual Memory & Paging",
            atRiskTopics = listOf("Virtual Memory & Paging"),
            streakDays = 5,
            coveragePercent = 65f,
            peakWindow = "07:00 – 09:00 AM"
        )

        val response = coach.answerPrompt("quiz me on my topics", context)
        assertNotNull(response.suggestedAction)
        assertEquals("TEST_ACTIVE_RECALL", response.suggestedAction?.actionType)
        assertTrue(response.replyText.contains("Active recall", ignoreCase = true))
        assertTrue(response.suggestedAction!!.title.contains("Virtual Memory & Paging"))
    }

    @Test
    fun testAiCoachSoundscapePromptResponse() {
        val coach = AiCoachService(db = null, userPreferencesManager = null)
        val context = AiCoachContext(
            examTitle = "Computer Systems",
            daysUntilExam = 14,
            todayTargetMinutes = 120,
            todayCompletedMinutes = 45,
            nextTopicName = "Virtual Memory",
            atRiskTopics = emptyList(),
            streakDays = 5,
            coveragePercent = 65f,
            peakWindow = "07:00 – 09:00 AM"
        )

        val response = coach.answerPrompt("can I play white noise?", context)
        assertTrue(response.replyText.contains("White Noise", ignoreCase = true))
        assertTrue(response.replyText.contains("AudioTrack", ignoreCase = true))
        assertTrue(response.replyText.contains("zero downloads", ignoreCase = true))
    }
}
