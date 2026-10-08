package com.focusstudy.app.feature.focus

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focusstudy.app.core.database.entity.StudySession
import com.focusstudy.app.core.design.theme.SuccessGreen
import com.focusstudy.app.core.di.AppContainer
import com.focusstudy.app.core.widget.TodayStudyWidgetProvider
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun FocusScreen(
    appContainer: AppContainer,
    modifier: Modifier = Modifier,
    initialSession: StudySession? = null,
    onSessionCompleted: () -> Unit = {}
) {
    val viewModel = remember {
        FocusViewModel(
            db = appContainer.database,
            studyPlanRepository = appContainer.studyPlanRepository
        )
    }
    val state by viewModel.uiState.collectAsState()
    val preferences by appContainer.userPreferencesManager.userPreferencesFlow.collectAsState(initial = null)
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current

    LaunchedEffect(state.isCompleted) {
        if (state.isCompleted) {
            TodayStudyWidgetProvider.triggerUpdate(context)
            if (preferences?.hapticEnabled != false) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            }
        }
    }

    LaunchedEffect(initialSession) {
        if (initialSession != null) {
            val topic = appContainer.database.syllabusDao().getTopicById(initialSession.topicId)
            val subject = initialSession.subjectId.let { appContainer.database.syllabusDao().getSubjectById(it) }
            viewModel.setSession(initialSession, topic, subject)
        }
    }

    val minutes = state.remainingSeconds / 60
    val seconds = state.remainingSeconds % 60
    val formattedTime = String.format("%02d:%02d", minutes, seconds)
    val progress = if (state.totalSeconds > 0)
        1f - (state.remainingSeconds.toFloat() / state.totalSeconds.toFloat())
    else 0f

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Top Topic & Subject Header
        item {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 8.dp)
            ) {
                Text(
                    text = state.activeSubject?.name?.uppercase() ?: "STUDY FOCUS",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = state.activeTopic?.name ?: "Focus Session",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Mode: ${state.activeSession?.mode?.uppercase() ?: "LEARN"} · Target: ${state.totalSeconds / 60} min",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Circular Timer Display
        item {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(240.dp)
            ) {
                CircularProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxSize(),
                    strokeWidth = 12.dp,
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = formattedTime,
                        fontSize = 50.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = when {
                            state.isCompleted -> "Completed"
                            state.isRunning -> "Focusing"
                            else -> "Ready"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Completion Banner (+XP & minutes)
        if (state.isCompleted) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = SuccessGreen.copy(alpha = 0.15f)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "🎉 Session Completed!",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = SuccessGreen
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "+${state.completedMinutes} study minutes recorded  •  +${state.earnedXp} XP",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Syllabus topic progress & daily goal updated in Room.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Timer Controls
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (!state.isCompleted) {
                    Button(
                        onClick = {
                            if (preferences?.hapticEnabled != false) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                            if (state.isRunning) viewModel.pauseTimer() else viewModel.startTimer()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(
                            imageVector = if (state.isRunning) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (state.isRunning) "Pause" else "Start Focus")
                    }

                    OutlinedButton(
                        onClick = { viewModel.finishEarly() },
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Finish Early")
                    }
                } else {
                    Button(
                        onClick = {
                            viewModel.resetSession()
                            onSessionCompleted()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Return to Dashboard")
                    }
                }
            }
        }

        // Session History Header (FSTUDY-006-05)
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.History, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Recent Study Sessions (${state.recentAttempts.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        if (state.recentAttempts.isEmpty()) {
            item {
                Text(
                    text = "No focus sessions recorded yet. Start your first session above!",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            items(state.recentAttempts.take(10), key = { it.id }) { attempt ->
                val timeFormat = SimpleDateFormat("h:mm a · MMM d", Locale.getDefault())
                val timeStr = timeFormat.format(Date(attempt.endedAtUtc))
                val minutesStudied = (attempt.durationSeconds / 60).coerceAtLeast(1)

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = attempt.notes ?: "Focus Session",
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = timeStr,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = "+$minutesStudied min",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = SuccessGreen
                        )
                    }
                }
            }
        }
    }
}
