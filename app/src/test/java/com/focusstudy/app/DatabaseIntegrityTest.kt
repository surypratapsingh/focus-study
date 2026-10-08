package com.focusstudy.app

import com.focusstudy.app.core.auth.AuthState
import com.focusstudy.app.core.auth.LocalAuthManager
import com.focusstudy.app.core.backup.DataBackupManager
import com.focusstudy.app.core.database.entity.*
import com.focusstudy.app.core.security.AppCheckConfig
import com.focusstudy.app.core.security.AppCheckProviderType
import com.focusstudy.app.core.security.SafeLogger
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class DatabaseIntegrityTest {

    @Test
    fun testEntityRelationalModelIntegrity() {
        val examId = UUID.randomUUID().toString()
        val exam = Exam(
            id = examId,
            title = "Discrete Mathematics",
            examType = "University Exam",
            examDateUtc = System.currentTimeMillis() + 30L * 86400000L
        )

        val subjectId = UUID.randomUUID().toString()
        val subject = Subject(
            id = subjectId,
            examId = examId,
            name = "Graph Theory"
        )
        assertEquals(exam.id, subject.examId)

        val unitId = UUID.randomUUID().toString()
        val unit = UnitEntity(
            id = unitId,
            subjectId = subjectId,
            title = "Eulerian & Hamiltonian Paths"
        )
        assertEquals(subject.id, unit.subjectId)

        val topicId = UUID.randomUUID().toString()
        val topic = Topic(
            id = topicId,
            unitId = unitId,
            subjectId = subjectId,
            name = "Fleury's Algorithm",
            difficulty = "medium",
            importance = "high",
            estimatedEffortMinutes = 60
        )
        assertEquals(unit.id, topic.unitId)
        assertEquals(subject.id, topic.subjectId)

        val planDayId = UUID.randomUUID().toString()
        val session = StudySession(
            id = UUID.randomUUID().toString(),
            planDayId = planDayId,
            topicId = topic.id,
            subjectId = subject.id,
            scheduledDate = "2026-10-10",
            startTime = "10:00",
            durationMinutes = 45,
            mode = "learn"
        )
        assertEquals(topic.id, session.topicId)
        assertEquals(subject.id, session.subjectId)
    }

    @Test
    fun testBackupCorruptedJsonGracefulHandling() {
        val backupManager = DataBackupManager(db = null)

        runBlocking {
            val malformedJson = "{ \"exportVersion\": 1, \"exams\": [ { \"id\": " // Truncated JSON
            val result = backupManager.importDataFromJson(malformedJson)

            assertFalse(result.success)
            assertTrue(result.message.contains("Corrupted") || result.message.contains("Failed") || result.message.contains("Invalid"))
        }
    }

    @Test
    fun testSafeLoggerCredentialRedaction() {
        val rawMessageWithEmail = "Student registered with student.test@university.edu for exam notifications"
        val sanitizedEmail = SafeLogger.sanitize(rawMessageWithEmail)
        assertFalse(sanitizedEmail.contains("student.test@university.edu"))
        assertTrue(sanitizedEmail.contains("[REDACTED_EMAIL]"))

        val rawMessageWithBearer = "Calling Gemini API with Bearer ya29.a0AfH6SMD... token"
        val sanitizedBearer = SafeLogger.sanitize(rawMessageWithBearer)
        assertFalse(sanitizedBearer.contains("ya29.a0AfH6SMD"))
        assertTrue(sanitizedBearer.contains("Bearer [REDACTED_TOKEN]"))

        val rawMessageWithSecret = "API key=AIzaSyD-7Xp89Q1234567890 loaded"
        val sanitizedSecret = SafeLogger.sanitize(rawMessageWithSecret)
        assertFalse(sanitizedSecret.contains("AIzaSyD-7Xp89Q1234567890"))
        assertTrue(sanitizedSecret.contains("[REDACTED_SECRET]"))
    }

    @Test
    fun testAppCheckAttestationConfig() {
        // Test Debug Configuration
        AppCheckConfig.initialize(isDebug = true)
        val debugState = AppCheckConfig.getCurrentState()
        assertTrue(debugState.isInitialized)
        assertEquals(AppCheckProviderType.DEBUG, debugState.providerType)
        assertTrue(AppCheckConfig.verifyAttestation())

        // Test Release / Play Integrity Configuration
        AppCheckConfig.initialize(isDebug = false)
        val releaseState = AppCheckConfig.getCurrentState()
        assertTrue(releaseState.isInitialized)
        assertEquals(AppCheckProviderType.PLAY_INTEGRITY, releaseState.providerType)
        assertTrue(AppCheckConfig.verifyAttestation())
    }

    @Test
    fun testLocalAuthManagerGuestAndEmailFlow() {
        val authManager = LocalAuthManager()

        // 1. Initial State is Authenticated as Guest
        val initialUser = authManager.currentUser
        assertNotNull(initialUser)
        assertTrue(initialUser!!.isAnonymous)
        assertTrue(initialUser.uid.startsWith("local_guest_"))

        runBlocking {
            // 2. Email Sign In Validation
            val invalidResult = authManager.signInWithEmail("bademail", "123")
            assertTrue(invalidResult.isFailure)

            val validResult = authManager.signInWithEmail("student@test.com", "securePassword123")
            assertTrue(validResult.isSuccess)
            val signedInUser = validResult.getOrNull()!!
            assertFalse(signedInUser.isAnonymous)
            assertEquals("student@test.com", signedInUser.email)
            assertTrue(authManager.authState.value is AuthState.Authenticated)

            // 3. Sign Out
            authManager.signOut()
            assertTrue(authManager.authState.value is AuthState.Unauthenticated)
            assertNull(authManager.currentUser)
        }
    }
}
