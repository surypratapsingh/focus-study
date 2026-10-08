package com.focusstudy.app.feature.today

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.focusstudy.app.core.database.entity.StudySession
import com.focusstudy.app.core.design.theme.StatusPill
import com.focusstudy.app.core.design.theme.SuccessGreen
import com.focusstudy.app.core.design.theme.WarningAmber
import com.focusstudy.app.core.di.AppContainer
import com.focusstudy.app.feature.common.AppTutorialDialog

@Composable
fun TodayScreen(
    appContainer: AppContainer,
    modifier: Modifier = Modifier,
    onNavigateToFocus: (StudySession?) -> Unit = {},
    onNavigateToSettings: () -> Unit = {}
) {
    val configuration = LocalConfiguration.current
    val isWideScreen = configuration.screenWidthDp >= 600

    val viewModel = remember {
        TodayViewModel(
            db = appContainer.database,
            studyPlanRepository = appContainer.studyPlanRepository,
            progressRepository = appContainer.progressRepository,
            aiInsightService = appContainer.aiInsightService
        )
    }
    val state by viewModel.uiState.collectAsState()
    var showTutorialDialog by remember { mutableStateOf(false) }

    if (isWideScreen) {
        // Two-pane responsive layout for tablets, foldables, and landscape
        Row(
            modifier = modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Left pane: Header & Countdown, Daily Progress, Next Session recommendation
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    HeaderCard(
                        state = state,
                        onTutorial = { showTutorialDialog = true },
                        onSettings = onNavigateToSettings
                    )
                }
                item { DailyProgressCard(state = state) }
                item {
                    NextSessionCard(
                        state = state,
                        onNavigateToFocus = onNavigateToFocus
                    )
                }
            }

            // Right pane: AI Daily Brief, At-Risk Topics, Schedule sessions timeline
            LazyColumn(
                modifier = Modifier
                    .weight(1.2f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (state.dailyBrief != null || state.isGeneratingBrief) {
                    item {
                        AiDailyBriefCard(
                            state = state,
                            onRefresh = { viewModel.generateDailyBrief() }
                        )
                    }
                }
                if (state.atRiskTopics.isNotEmpty()) {
                    item { AtRiskTopicsCard(state = state) }
                }
                renderSessionsTimeline(state = state, onNavigateToFocus = onNavigateToFocus)
            }
        }
    } else {
        // Standard compact single-column layout for phones
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            item {
                HeaderCard(
                    state = state,
                    onTutorial = { showTutorialDialog = true },
                    onSettings = onNavigateToSettings
                )
            }
            if (state.dailyBrief != null || state.isGeneratingBrief) {
                item {
                    AiDailyBriefCard(
                        state = state,
                        onRefresh = { viewModel.generateDailyBrief() }
                    )
                }
            }
            item { DailyProgressCard(state = state) }
            item {
                NextSessionCard(
                    state = state,
                    onNavigateToFocus = onNavigateToFocus
                )
            }
            if (state.atRiskTopics.isNotEmpty()) {
                item { AtRiskTopicsCard(state = state) }
            }
            renderSessionsTimeline(state = state, onNavigateToFocus = onNavigateToFocus)
        }
    }

    if (showTutorialDialog) {
        AppTutorialDialog(onDismiss = { showTutorialDialog = false })
    }
}

private fun LazyListScope.renderSessionsTimeline(
    state: TodayUiState,
    onNavigateToFocus: (StudySession?) -> Unit
) {
    item {
        Text(
            text = "Today's Schedule (${state.sessions.size} sessions)",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 4.dp)
        )
    }

    if (state.sessions.isEmpty()) {
        item {
            Text(
                text = "No sessions scheduled yet. Check the Plan tab to generate today's slots.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else {
        items(state.sessions, key = { it.id }) { session ->
            val topic = state.topicsMap[session.topicId]
            val subject = state.subjectsMap[session.subjectId]

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !session.isCompleted) {
                        onNavigateToFocus(session)
                    },
                colors = CardDefaults.cardColors(
                    containerColor = if (session.isCompleted)
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
                    else MaterialTheme.colorScheme.surface
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (session.isCompleted) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                        contentDescription = if (session.isCompleted) "Completed session" else "Pending session",
                        tint = if (session.isCompleted) SuccessGreen else MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${subject?.name ?: "Subject"} · ${topic?.name ?: "Topic"}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "${session.startTime} · ${session.durationMinutes} min · Mode: ${session.mode.uppercase()}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (!session.isCompleted) {
                        IconButton(onClick = { onNavigateToFocus(session) }) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Start", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HeaderCard(
    state: TodayUiState,
    onTutorial: () -> Unit,
    onSettings: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "GOOD MORNING",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(
                        onClick = onTutorial,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.HelpOutline,
                            contentDescription = "Tutorial & Guide",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    IconButton(
                        onClick = onSettings,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Exam in ${state.daysRemaining} days",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                text = state.exam?.title ?: "Master Exam Plan",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
            )
        }
    }
}

@Composable
private fun AiDailyBriefCard(
    state: TodayUiState,
    onRefresh: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "AI Daily Focus Brief",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(
                    onClick = onRefresh,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            if (state.isGeneratingBrief) {
                Text(
                    text = "Synthesizing today's strategic brief...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    text = state.dailyBrief?.content ?: "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun DailyProgressCard(state: TodayUiState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            val targetHours = state.targetMinutes / 60
            val targetMins = state.targetMinutes % 60
            val completedHours = state.completedMinutes / 60
            val completedMins = state.completedMinutes % 60
            val percentInt = (state.progressPercent * 100).toInt()

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Today's Target",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "${completedHours}h ${completedMins}m / ${targetHours}h ${targetMins}m ($percentInt%)",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { state.progressPercent },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(5.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
            )
            Spacer(modifier = Modifier.height(6.dp))
            val remainingMins = (state.targetMinutes - state.completedMinutes).coerceAtLeast(0)
            Text(
                text = if (remainingMins == 0) "Daily target achieved! Great focus." else "${remainingMins}m remaining to stay on pace",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun NextSessionCard(
    state: TodayUiState,
    onNavigateToFocus: (StudySession?) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatusPill(
                    text = "NEXT SESSION",
                    containerColor = WarningAmber.copy(alpha = 0.15f),
                    contentColor = WarningAmber
                )
                Text(
                    text = "${state.nextSession?.durationMinutes ?: 45} min",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium
                )
            }
            Spacer(modifier = Modifier.height(8.dp))

            val nextTopicTitle = state.nextTopic?.name ?: "All today's sessions complete!"
            val nextSubjectTitle = state.nextSubject?.name ?: "Review"
            Text(
                text = if (state.nextSession != null) "$nextSubjectTitle — $nextTopicTitle" else "All sessions done for today! 🎉",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            if (state.nextSession != null) {
                Text(
                    text = state.nextSession?.reason.takeUnless { it.isNullOrBlank() }
                        ?: "High exam priority with upcoming revision milestone",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = { onNavigateToFocus(state.nextSession) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Start Session Now", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun AtRiskTopicsCard(state: TodayUiState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Warning, contentDescription = null, tint = WarningAmber, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Priority Focus Chapters", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(6.dp))
            state.atRiskTopics.forEach { topic ->
                val subjName = state.subjectsMap[topic.subjectId]?.name ?: "Subject"
                Text(
                    text = "• $subjName: ${topic.name} (${topic.difficulty.uppercase()})",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
