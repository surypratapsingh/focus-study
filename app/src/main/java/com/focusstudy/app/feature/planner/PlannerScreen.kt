package com.focusstudy.app.feature.planner

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.focusstudy.app.core.ai.RiskLevel
import com.focusstudy.app.core.database.entity.AiPlanVersion
import com.focusstudy.app.core.database.entity.StudyPlanDay
import com.focusstudy.app.core.database.entity.StudySession
import com.focusstudy.app.core.design.theme.StatusPill
import com.focusstudy.app.core.design.theme.SuccessGreen
import com.focusstudy.app.core.design.theme.WarningAmber
import com.focusstudy.app.core.di.AppContainer

@Composable
fun PlannerScreen(
    appContainer: AppContainer,
    modifier: Modifier = Modifier
) {
    val configuration = LocalConfiguration.current
    val isWideScreen = configuration.screenWidthDp >= 600

    val viewModel = remember {
        PlannerViewModel(
            db = appContainer.database,
            aiPlannerService = appContainer.aiPlannerService,
            aiReplanService = appContainer.aiReplanService
        )
    }
    val state by viewModel.uiState.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }
    var wideRightTab by remember { mutableIntStateOf(0) }
    val phoneTabs = listOf("Today", "Week", "Exam Roadmap")
    val wideRightTabs = listOf("Day Schedule", "Week Overview")

    // Replan Dialog
    if (state.isReplanDialogVisible && state.replanProposal != null) {
        ReplanDialog(
            proposal = state.replanProposal!!,
            riskAssessment = state.scheduleRisk,
            topicsMap = state.topicsMap,
            onApply = { viewModel.applyReplanProposal() },
            onDismiss = { viewModel.dismissReplanDialog() }
        )
    }

    if (isWideScreen) {
        // Two-pane responsive layout for tablets, foldables, and landscape mode
        Row(
            modifier = modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Left pane: Strategy, Roadmap, Replan Alerts & Version History
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = state.exam?.title ?: "Master Exam Plan",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Target Date: ${state.selectedDate} (${state.daysUntilExam} days to exam)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            )
                        }
                    }
                }

                if (state.missedSessions.isNotEmpty() || state.scheduleRisk?.riskLevel == RiskLevel.HIGH || state.scheduleRisk?.riskLevel == RiskLevel.CRITICAL) {
                    item { ReplanAlertBanner(state, onReplan = { viewModel.openReplanProposal() }) }
                }

                if (state.planExplanation.isNotBlank()) {
                    item { AiStrategyInsightCard(state.planExplanation) }
                }

                item {
                    Text(
                        text = "Exam Preparation Roadmap (${state.daysUntilExam} Days)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                items(state.phases) { phase ->
                    PhaseRoadmapCard(phase)
                }

                if (state.planVersions.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.History, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text(
                                text = "Plan Version History (${state.planVersions.size})",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    items(state.planVersions) { version ->
                        PlanVersionCard(version)
                    }
                }
            }

            // Right pane: Tabbed Day Schedule and Week Overview
            Column(
                modifier = Modifier
                    .weight(1.3f)
                    .fillMaxHeight()
            ) {
                TabRow(selectedTabIndex = wideRightTab) {
                    wideRightTabs.forEachIndexed { index, title ->
                        Tab(
                            selected = wideRightTab == index,
                            onClick = { wideRightTab = index },
                            text = { Text(title, fontWeight = FontWeight.SemiBold) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    if (wideRightTab == 0) {
                        // Day sessions
                        if (state.daySessions.isEmpty()) {
                            item { EmptyDayScheduleCard(onGenerate = { viewModel.regeneratePlan() }) }
                        } else {
                            items(state.daySessions, key = { it.id }) { session ->
                                val topic = state.topicsMap[session.topicId]
                                val topicName = topic?.name ?: "Topic #${session.topicId.take(4)}"
                                val subjectName = state.subjectsMap[session.subjectId]?.name ?: "Subject"

                                SessionDetailCard(
                                    session = session,
                                    topicName = topicName,
                                    subjectName = subjectName,
                                    onToggleLock = { viewModel.toggleSessionLock(session.id) },
                                    onDelete = { viewModel.deleteSession(session.id) }
                                )
                            }
                        }
                    } else {
                        // Week timeline
                        item { WeekStripCard(state = state, onSelectDate = { viewModel.selectDate(it) }) }
                        item {
                            PlanOptimizationCard(
                                state = state,
                                onRebalance = { viewModel.regeneratePlan() },
                                onAdaptiveReplan = { viewModel.openReplanProposal() }
                            )
                        }
                    }
                }
            }
        }
    } else {
        // Standard compact phone single-column layout
        Column(modifier = modifier.fillMaxSize()) {
            TabRow(selectedTabIndex = selectedTab) {
                phoneTabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title, fontWeight = FontWeight.SemiBold) }
                    )
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (state.missedSessions.isNotEmpty() || state.scheduleRisk?.riskLevel == RiskLevel.HIGH || state.scheduleRisk?.riskLevel == RiskLevel.CRITICAL) {
                    item { ReplanAlertBanner(state, onReplan = { viewModel.openReplanProposal() }) }
                }

                if (state.planExplanation.isNotBlank()) {
                    item { AiStrategyInsightCard(state.planExplanation) }
                }

                when (selectedTab) {
                    0 -> {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        text = state.exam?.title ?: "Master Exam Plan",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Scheduled for: ${state.selectedDate} (${state.daysUntilExam} days to exam)",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                    )
                                }
                            }
                        }

                        if (state.daySessions.isEmpty()) {
                            item { EmptyDayScheduleCard(onGenerate = { viewModel.regeneratePlan() }) }
                        } else {
                            items(state.daySessions, key = { it.id }) { session ->
                                val topic = state.topicsMap[session.topicId]
                                val topicName = topic?.name ?: "Topic #${session.topicId.take(4)}"
                                val subjectName = state.subjectsMap[session.subjectId]?.name ?: "Subject"

                                SessionDetailCard(
                                    session = session,
                                    topicName = topicName,
                                    subjectName = subjectName,
                                    onToggleLock = { viewModel.toggleSessionLock(session.id) },
                                    onDelete = { viewModel.deleteSession(session.id) }
                                )
                            }
                        }
                    }

                    1 -> {
                        item { WeekStripCard(state = state, onSelectDate = { viewModel.selectDate(it) }) }
                        item {
                            PlanOptimizationCard(
                                state = state,
                                onRebalance = { viewModel.regeneratePlan() },
                                onAdaptiveReplan = { viewModel.openReplanProposal() }
                            )
                        }
                    }

                    2 -> {
                        item {
                            Text(
                                text = "Exam Preparation Roadmap (${state.daysUntilExam} Days)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        items(state.phases) { phase ->
                            PhaseRoadmapCard(phase)
                        }

                        if (state.planVersions.isNotEmpty()) {
                            item {
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(Icons.Default.History, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Text(
                                        text = "Plan Version History (${state.planVersions.size})",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            items(state.planVersions) { version ->
                                PlanVersionCard(version)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReplanAlertBanner(state: PlannerUiState, onReplan: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = WarningAmber.copy(alpha = 0.15f)),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = WarningAmber)
                    Text(
                        text = if (state.missedSessions.isNotEmpty()) "${state.missedSessions.size} Missed Sessions" else "Schedule Pace Alert",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = WarningAmber
                    )
                }
                Button(
                    onClick = onReplan,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("AI Replan", style = MaterialTheme.typography.labelMedium)
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = state.scheduleRisk?.explanation ?: "Adaptive adjustment recommended to restore exam readiness.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun AiStrategyInsightCard(explanation: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                Icons.Default.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Column {
                Text(
                    text = "AI Strategy Insight",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = explanation,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun EmptyDayScheduleCard(onGenerate: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("No sessions scheduled for this day.", style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(12.dp))
            Button(onClick = onGenerate) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Generate Schedule")
            }
        }
    }
}

@Composable
private fun WeekStripCard(state: PlannerUiState, onSelectDate: (String) -> Unit) {
    Column {
        Text("7-Day Schedule Strip", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(state.weekDays) { day ->
                val isSelected = state.selectedDate == day.dateString
                Card(
                    modifier = Modifier
                        .width(100.dp)
                        .clickable { onSelectDate(day.dateString) },
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = day.dateString.substringAfterLast("-"),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${day.targetMinutes}m",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = day.phaseName.substringBefore(":"),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PlanOptimizationCard(
    state: PlannerUiState,
    onRebalance: () -> Unit,
    onAdaptiveReplan: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Plan Optimization", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                if (state.isOverloaded) {
                    StatusPill(
                        text = "High Load",
                        containerColor = WarningAmber.copy(alpha = 0.2f),
                        contentColor = WarningAmber
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = if (state.isOverloaded)
                    "Required pace exceeds standard availability. Minimum ~${state.requiredDailyMinutes}m/day required to finish all topics."
                else
                    "Workload is well-balanced across weekdays and weekends with a buffer period reserved before the exam.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onRebalance,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Rebalance")
                }
                Button(
                    onClick = onAdaptiveReplan,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Adaptive Replan")
                }
            }
        }
    }
}

@Composable
fun PlanVersionCard(version: AiPlanVersion) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Version v${version.versionNumber}",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                StatusPill(
                    text = version.promptVersion,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = version.summaryOfChanges,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun SessionDetailCard(
    session: StudySession,
    topicName: String,
    subjectName: String,
    onToggleLock: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusPill(
                        text = session.mode.uppercase(),
                        containerColor = when (session.mode) {
                            "practice" -> MaterialTheme.colorScheme.secondaryContainer
                            "revise" -> SuccessGreen.copy(alpha = 0.15f)
                            "mock" -> WarningAmber.copy(alpha = 0.15f)
                            else -> MaterialTheme.colorScheme.primaryContainer
                        },
                        contentColor = when (session.mode) {
                            "revise" -> SuccessGreen
                            "mock" -> WarningAmber
                            else -> MaterialTheme.colorScheme.primary
                        }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${session.startTime} · ${session.durationMinutes} min",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium
                    )
                }

                Row {
                    IconButton(onClick = onToggleLock) {
                        Icon(
                            imageVector = if (session.isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                            contentDescription = if (session.isLocked) "Unlock" else "Lock",
                            tint = if (session.isLocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                        )
                    }
                    IconButton(onClick = onDelete) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "$subjectName — $topicName",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            if (!session.reason.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = session.reason!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun PhaseRoadmapCard(phase: MacroPhase) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            val daysCount = (phase.endDayOffset - phase.startDayOffset + 1).coerceAtLeast(1)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = phase.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                StatusPill(
                    text = "$daysCount Days",
                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                    contentColor = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = phase.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
