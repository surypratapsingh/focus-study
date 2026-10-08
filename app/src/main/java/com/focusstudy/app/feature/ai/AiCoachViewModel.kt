package com.focusstudy.app.feature.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focusstudy.app.core.ai.AiCoachContext
import com.focusstudy.app.core.ai.AiCoachService
import com.focusstudy.app.core.ai.CoachAction
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.*

data class ChatMessageUi(
    val id: String = UUID.randomUUID().toString(),
    val sender: String, // "user", "coach"
    val text: String,
    val supportingData: String? = null,
    val action: CoachAction? = null,
    val isActionExecuted: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

data class AiCoachUiState(
    val messages: List<ChatMessageUi> = emptyList(),
    val context: AiCoachContext? = null,
    val isTyping: Boolean = false,
    val actionStatusMessage: String? = null
)

class AiCoachViewModel(
    private val coachService: AiCoachService
) : ViewModel() {

    private val _uiState = MutableStateFlow(AiCoachUiState())
    val uiState: StateFlow<AiCoachUiState> = _uiState.asStateFlow()

    init {
        loadContextAndGreeting()
    }

    private fun loadContextAndGreeting() {
        viewModelScope.launch {
            val ctx = coachService.buildContext()
            val initialMessage = ChatMessageUi(
                sender = "coach",
                text = "Welcome to your Focus Study Coach! You are currently ${ctx.coveragePercent.toInt()}% through your syllabus with ${ctx.daysUntilExam} days until ${ctx.examTitle}. How can I help fine-tune your preparation today?",
                supportingData = "Today's Target: ${ctx.todayCompletedMinutes}m / ${ctx.todayTargetMinutes}m · Peak Window: ${ctx.peakWindow}"
            )

            _uiState.value = _uiState.value.copy(
                context = ctx,
                messages = listOf(initialMessage)
            )
        }
    }

    fun sendMessage(query: String) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return

        val userMsg = ChatMessageUi(sender = "user", text = trimmed)
        _uiState.value = _uiState.value.copy(
            messages = _uiState.value.messages + userMsg,
            isTyping = true
        )

        viewModelScope.launch {
            val ctx = _uiState.value.context ?: coachService.buildContext()
            delay(300) // Natural conversational pacing

            val response = coachService.queryCoach(trimmed, ctx)
            val coachMsg = ChatMessageUi(
                sender = "coach",
                text = response.replyText,
                supportingData = response.supportingData,
                action = response.suggestedAction
            )

            _uiState.value = _uiState.value.copy(
                messages = _uiState.value.messages + coachMsg,
                isTyping = false
            )

            // Auto-commit to database if user requested it directly
            val autoCommitRequested = trimmed.contains("enter this in data", ignoreCase = true) ||
                    trimmed.contains("enter in data", ignoreCase = true) ||
                    trimmed.contains("enter into data", ignoreCase = true) ||
                    trimmed.contains("save in data", ignoreCase = true) ||
                    trimmed.contains("save to data", ignoreCase = true) ||
                    trimmed.contains("put information in it", ignoreCase = true)

            if (response.suggestedAction != null && autoCommitRequested) {
                executeAction(coachMsg.id, response.suggestedAction)
            }
        }
    }

    fun executeAction(messageId: String, action: CoachAction) {
        viewModelScope.launch {
            val result = coachService.executeAction(action)

            // Mark message action as executed
            val updatedMessages = _uiState.value.messages.map { msg ->
                if (msg.id == messageId) msg.copy(isActionExecuted = true) else msg
            }

            val feedbackMsg = ChatMessageUi(
                sender = "coach",
                text = if (result.success) "✅ ${result.message}" else "⚠️ ${result.message}"
            )

            // Refresh context after action execution
            val newCtx = coachService.buildContext()

            _uiState.value = _uiState.value.copy(
                messages = updatedMessages + feedbackMsg,
                context = newCtx,
                actionStatusMessage = result.message
            )
        }
    }
}
