package com.focusstudy.app.core.ai

import com.focusstudy.app.core.database.entity.StudySession

data class ValidationResult<T>(
    val isValid: Boolean,
    val validatedData: T?,
    val errors: List<String>
)

object AiResponseValidator {

    private val dateRegex = Regex("^\\d{4}-\\d{2}-\\d{2}$")
    private val timeRegex = Regex("^([01]\\d|2[0-3]):[0-5]\\d$")

    /**
     * Validates full plan proposal from AI
     */
    fun validatePlanResponse(
        response: AiPlanResponse,
        knownTopicIds: Set<String>,
        examDateStr: String
    ): ValidationResult<AiPlanResponse> {
        val errors = mutableListOf<String>()
        val validDays = mutableListOf<AiPlanDayDto>()

        if (response.days.isEmpty()) {
            errors.add("Plan response contains no scheduled days.")
            return ValidationResult(false, null, errors)
        }

        for (day in response.days) {
            if (!dateRegex.matches(day.date)) {
                errors.add("Invalid date format in plan: ${day.date}")
                continue
            }
            if (day.date > examDateStr) {
                errors.add("Session scheduled past exam date: ${day.date} > $examDateStr")
                continue
            }

            val validSessions = mutableListOf<AiSessionDto>()
            for (session in day.sessions) {
                if (!timeRegex.matches(session.startTime)) {
                    errors.add("Invalid start time format: ${session.startTime}")
                    continue
                }
                if (session.durationMinutes !in 15..180) {
                    errors.add("Duration outside allowed range (15-180m): ${session.durationMinutes}m")
                    continue
                }
                if (session.topicId !in knownTopicIds) {
                    errors.add("Topic ID not found in database: ${session.topicId}")
                    continue
                }
                validSessions.add(session)
            }

            if (validSessions.isNotEmpty()) {
                validDays.add(day.copy(sessions = validSessions))
            }
        }

        val isValid = validDays.isNotEmpty()
        return ValidationResult(
            isValid = isValid,
            validatedData = if (isValid) response.copy(days = validDays) else null,
            errors = errors
        )
    }

    /**
     * Validates adaptive replan proposal against hard constraints and locked sessions
     */
    fun validateReplanProposal(
        proposal: AiReplanProposal,
        existingSessionsMap: Map<String, StudySession>,
        knownTopicIds: Set<String>,
        examDateStr: String
    ): ValidationResult<AiReplanProposal> {
        val errors = mutableListOf<String>()
        val validActions = mutableListOf<AiPlanActionDto>()

        for (action in proposal.actions) {
            when (action.actionType) {
                "MOVE_SESSION" -> {
                    val sessionId = action.sessionId
                    if (sessionId == null || sessionId !in existingSessionsMap) {
                        errors.add("Cannot move unknown session: $sessionId")
                        continue
                    }
                    val existing = existingSessionsMap[sessionId]!!
                    if (existing.isLocked) {
                        errors.add("Cannot move user-locked session: $sessionId")
                        continue
                    }
                    if (action.newDate != null && (!dateRegex.matches(action.newDate) || action.newDate > examDateStr)) {
                        errors.add("Invalid target date for move: ${action.newDate}")
                        continue
                    }
                    if (action.newStartTime != null && !timeRegex.matches(action.newStartTime)) {
                        errors.add("Invalid target time for move: ${action.newStartTime}")
                        continue
                    }
                    validActions.add(action)
                }
                "ADD_SESSION" -> {
                    val topicId = action.topicId
                    if (topicId == null || topicId !in knownTopicIds) {
                        errors.add("Cannot add session for unknown topic: $topicId")
                        continue
                    }
                    if (action.newDate != null && (!dateRegex.matches(action.newDate) || action.newDate > examDateStr)) {
                        errors.add("Invalid date for added session: ${action.newDate}")
                        continue
                    }
                    validActions.add(action)
                }
                "REMOVE_SESSION" -> {
                    val sessionId = action.sessionId
                    if (sessionId != null && sessionId in existingSessionsMap) {
                        val existing = existingSessionsMap[sessionId]!!
                        if (!existing.isLocked) {
                            validActions.add(action)
                        } else {
                            errors.add("Cannot remove locked session: $sessionId")
                        }
                    }
                }
                else -> {
                    errors.add("Unknown action type: ${action.actionType}")
                }
            }
        }

        return ValidationResult(
            isValid = validActions.isNotEmpty() || proposal.actions.isEmpty(),
            validatedData = proposal.copy(actions = validActions),
            errors = errors
        )
    }
}
