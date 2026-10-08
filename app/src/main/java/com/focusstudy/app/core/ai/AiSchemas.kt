package com.focusstudy.app.core.ai

import kotlinx.serialization.Serializable

@Serializable
data class AiPlanResponse(
    val planTitle: String,
    val days: List<AiPlanDayDto> = emptyList(),
    val warnings: List<String> = emptyList(),
    val assumptions: List<String> = emptyList()
)

@Serializable
data class AiPlanDayDto(
    val date: String, // YYYY-MM-DD
    val sessions: List<AiSessionDto> = emptyList()
)

@Serializable
data class AiSessionDto(
    val startTime: String, // HH:mm
    val durationMinutes: Int,
    val topicId: String,
    val task: String,
    val mode: String, // learn, revise, practice, mock
    val reason: String
)

@Serializable
data class AiReplanProposal(
    val summary: String,
    val riskAssessment: String,
    val actions: List<AiPlanActionDto> = emptyList()
)

@Serializable
data class AiPlanActionDto(
    val actionType: String, // MOVE_SESSION, ADD_SESSION, REMOVE_SESSION
    val sessionId: String? = null,
    val newDate: String? = null, // YYYY-MM-DD
    val newStartTime: String? = null, // HH:mm
    val topicId: String? = null,
    val durationMinutes: Int = 45,
    val mode: String = "learn",
    val reason: String
)

@Serializable
data class AiInsightDto(
    val category: String, // daily_brief, risk_alert, neglected_topics, peak_window, weekly_review
    val title: String,
    val content: String,
    val actionRecommendation: String? = null
)
