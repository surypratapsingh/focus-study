package com.focusstudy.app.feature.progress

import android.content.Intent
import androidx.compose.foundation.background
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focusstudy.app.core.design.theme.StatusPill
import com.focusstudy.app.core.design.theme.SuccessGreen
import com.focusstudy.app.core.design.theme.WarningAmber
import com.focusstudy.app.core.di.AppContainer

@Composable
fun ProgressScreen(
    appContainer: AppContainer,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isWideScreen = configuration.screenWidthDp >= 600

    val viewModel = remember {
        ProgressViewModel(
            db = appContainer.database,
            aiInsightService = appContainer.aiInsightService,
            userPreferencesManager = appContainer.userPreferencesManager
        )
    }
    val state by viewModel.uiState.collectAsState()
    var showAddBuddyDialog by remember { mutableStateOf(false) }

    val filterOptions = listOf("7d" to "7 Days", "30d" to "30 Days", "Exam" to "Full Exam")

    if (showAddBuddyDialog) {
        AddStudyBuddyDialog(
            onDismiss = { showAddBuddyDialog = false },
            onSave = { buddy ->
                viewModel.saveStudyBuddy(buddy)
                showAddBuddyDialog = false
            }
        )
    }

    fun shareProgressCard() {
        val shareText = PersonalizationEngine.formatShareableStudyCard(
            userName = "Focus Student",
            userXp = state.totalXp,
            streakDays = state.streakData.currentStreakDays,
            totalMinutes = state.totalStudyMinutes,
            examDaysRemaining = state.examDaysRemaining,
            scholarRankTitle = state.scholarRank.rankTitle
        )
        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, shareText)
            type = "text/plain"
        }
        val shareIntent = Intent.createChooser(sendIntent, "Share Study Progress")
        context.startActivity(shareIntent)
    }

    if (isWideScreen) {
        // Two-pane responsive layout for tablets, foldables, and landscape mode
        Row(
            modifier = modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Left Pane (Scholar status, Personalization & Study Buddy, Streaks)
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item { FilterChipsRow(filterOptions, state.selectedFilter) { viewModel.selectFilter(it) } }
                if (state.metricsExplanation.isNotBlank()) {
                    item { AiPaceSummaryCard(state.metricsExplanation) }
                }
                item { ScholarRankCard(state) }
                item {
                    PersonalizationCard(
                        chronotype = state.chronotypeResult,
                        burnout = state.burnoutAssessment
                    )
                }
                item {
                    StudyBuddyCard(
                        buddyData = state.studyBuddyData,
                        comparison = state.buddyComparison,
                        onShare = { shareProgressCard() },
                        onAddBuddy = { showAddBuddyDialog = true },
                        onClearBuddy = { viewModel.clearStudyBuddy() }
                    )
                }
                item { StreakAndHoursCard(state) }
            }

            // Right Pane (Coverage, Planned vs Actual, Subjects, Heatmap, Peak)
            LazyColumn(
                modifier = Modifier
                    .weight(1.3f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item { SyllabusCoverageCard(state) }
                item { PlannedVsActualCard(state) }
                item { SubjectProgressCard(state) }
                item { TopNeglectedCard(state) }
                item { HeatmapCard(state) }
                item { PeakWindowCard(state) }
            }
        }
    } else {
        // Standard compact single-column layout for phones
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { FilterChipsRow(filterOptions, state.selectedFilter) { viewModel.selectFilter(it) } }
            if (state.metricsExplanation.isNotBlank()) {
                item { AiPaceSummaryCard(state.metricsExplanation) }
            }
            item { ScholarRankCard(state) }
            item {
                PersonalizationCard(
                    chronotype = state.chronotypeResult,
                    burnout = state.burnoutAssessment
                )
            }
            item {
                StudyBuddyCard(
                    buddyData = state.studyBuddyData,
                    comparison = state.buddyComparison,
                    onShare = { shareProgressCard() },
                    onAddBuddy = { showAddBuddyDialog = true },
                    onClearBuddy = { viewModel.clearStudyBuddy() }
                )
            }
            item { SyllabusCoverageCard(state) }
            item { StreakAndHoursCard(state) }
            item { PlannedVsActualCard(state) }
            item { SubjectProgressCard(state) }
            item { TopNeglectedCard(state) }
            item { HeatmapCard(state) }
            item { PeakWindowCard(state) }
        }
    }
}

@Composable
private fun FilterChipsRow(
    filterOptions: List<Pair<String, String>>,
    selectedFilter: String,
    onSelect: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        filterOptions.forEach { (key, label) ->
            val isSelected = selectedFilter == key
            FilterChip(
                selected = isSelected,
                onClick = { onSelect(key) },
                label = { Text(label, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                shape = RoundedCornerShape(10.dp)
            )
        }
    }
}

@Composable
private fun AiPaceSummaryCard(explanation: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f))
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Column {
                Text(
                    text = "AI Study Pace Summary",
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
private fun ScholarRankCard(state: ProgressUiState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("⚡", fontSize = 22.sp)
                    Column {
                        Text(
                            text = "LEVEL ${state.scholarRank.level} · ${state.scholarRank.rankTitle.uppercase()}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                        Text(
                            text = "${state.totalXp} Total XP",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
                StatusPill(
                    text = "${state.scholarRank.currentLevelXp}/${state.scholarRank.nextLevelXp} XP",
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            LinearProgressIndicator(
                progress = { state.scholarRank.progressPercent },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f)
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "ACADEMIC MILESTONES",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
            )
            Spacer(modifier = Modifier.height(6.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.achievements) { badge ->
                    val isUnlocked = badge.unlockedAtUtc != null
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isUnlocked) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surface.copy(alpha = 0.4f),
                        tonalElevation = if (isUnlocked) 2.dp else 0.dp,
                        modifier = Modifier.width(108.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = badge.badgeIcon,
                                fontSize = 20.sp,
                                modifier = Modifier.padding(bottom = 2.dp)
                            )
                            Text(
                                text = badge.title,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                textAlign = TextAlign.Center,
                                color = if (isUnlocked) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PersonalizationCard(
    chronotype: ChronotypeResult,
    burnout: BurnoutAssessment
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(chronotype.chronotype.emoji, fontSize = 20.sp)
                    Text(
                        text = "AI COGNITIVE PROFILE",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                StatusPill(
                    text = chronotype.confidence,
                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                    contentColor = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "${chronotype.chronotype.title} · ${chronotype.chronotype.optimalTimeRange}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = chronotype.summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(burnout.riskLevel.emoji, fontSize = 16.sp)
                    Text(
                        text = "Fatigue Guard: ${burnout.riskLevel.label}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = "${String.format("%.1f", burnout.recent7DaysHours)}h in 7d",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = burnout.recoveryRecommendation,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun StudyBuddyCard(
    buddyData: StudyBuddyData?,
    comparison: BuddyComparisonResult?,
    onShare: () -> Unit,
    onAddBuddy: () -> Unit,
    onClearBuddy: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("👥", fontSize = 20.sp)
                    Text(
                        text = "STUDY BUDDY & ACCOUNTABILITY",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(onClick = onShare, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Share Study Card",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (buddyData != null && comparison != null) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Accountability Duo: ${buddyData.name}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            IconButton(onClick = onClearBuddy, modifier = Modifier.size(24.dp)) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Remove Buddy",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text("BUDDY XP", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("${buddyData.xp} XP", fontWeight = FontWeight.Bold)
                            }
                            Column {
                                Text("BUDDY STREAK", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("${buddyData.streakDays} Days 🔥", fontWeight = FontWeight.Bold)
                            }
                            Column {
                                Text("FOCUS HOURS", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("${String.format("%.1f", buddyData.focusMinutes / 60f)} hrs", fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = comparison.encouragementMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            } else {
                Text(
                    text = "Study with a friend to maintain streaks and challenge each other. Share your progress card or compare with a peer's stats.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onShare,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Share Card")
                    }
                    Button(
                        onClick = onAddBuddy,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Group, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Compare Buddy")
                    }
                }
            }
        }
    }
}

@Composable
private fun SyllabusCoverageCard(state: ProgressUiState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = "SYLLABUS COVERAGE",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = "${state.syllabusCoveragePercent.toInt()}%",
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "${state.completedTopicsCount} of ${state.totalTopicsCount} topics covered",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { state.syllabusCoveragePercent / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(12.dp)
                    .clip(RoundedCornerShape(6.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
            )
        }
    }
}

@Composable
private fun StreakAndHoursCard(state: ProgressUiState) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.LocalFireDepartment, contentDescription = null, tint = WarningAmber, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("ACTIVE STREAK", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${state.streakData.currentStreakDays} days",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Best: ${state.streakData.longestStreakDays} days",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Card(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.BarChart, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("TOTAL FOCUS", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(modifier = Modifier.height(4.dp))
                val hours = state.totalStudyMinutes / 60
                val mins = state.totalStudyMinutes % 60
                Text(
                    text = "${hours}h ${mins}m",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${state.streakData.totalActiveDays} study days logged",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun PlannedVsActualCard(state: ProgressUiState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Planned vs. Actual Adherence",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                StatusPill(
                    text = "${state.plannedVsActual.adherencePercent.toInt()}% pace",
                    containerColor = if (state.plannedVsActual.adherencePercent >= 80f) SuccessGreen.copy(alpha = 0.15f) else WarningAmber.copy(alpha = 0.15f),
                    contentColor = if (state.plannedVsActual.adherencePercent >= 80f) SuccessGreen else WarningAmber
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Target: ${state.plannedVsActual.totalPlannedMinutes}m · Actual: ${state.plannedVsActual.totalActualMinutes}m",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Trend bars
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                val maxBarMins = (state.plannedVsActual.dailyTrend.maxOfOrNull { maxOf(it.plannedMinutes, it.actualMinutes) } ?: 180).coerceAtLeast(60)

                state.plannedVsActual.dailyTrend.forEach { point ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            modifier = Modifier.height(80.dp)
                        ) {
                            val plannedFraction = (point.plannedMinutes.toFloat() / maxBarMins.toFloat()).coerceIn(0.05f, 1f)
                            Box(
                                modifier = Modifier
                                    .width(7.dp)
                                    .fillMaxHeight(plannedFraction)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                            )

                            val actualFraction = (point.actualMinutes.toFloat() / maxBarMins.toFloat()).coerceIn(0.05f, 1f)
                            Box(
                                modifier = Modifier
                                    .width(7.dp)
                                    .fillMaxHeight(actualFraction)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(if (point.actualMinutes >= point.plannedMinutes) SuccessGreen else MaterialTheme.colorScheme.primary)
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = point.dayLabel,
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 9.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SubjectProgressCard(state: ProgressUiState) {
    if (state.subjectProgressList.isEmpty()) return

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Subject Breakdown",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))

            state.subjectProgressList.forEach { subj ->
                val parsedColor = try {
                    Color(android.graphics.Color.parseColor(subj.colorHex))
                } catch (e: Exception) {
                    MaterialTheme.colorScheme.primary
                }

                Column(modifier = Modifier.padding(vertical = 6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = subj.subjectName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            text = "${subj.coveragePercent.toInt()}% (${subj.completedTopics}/${subj.totalTopics})",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { subj.coveragePercent / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = parsedColor,
                        trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                    )
                }
            }
        }
    }
}

@Composable
private fun TopNeglectedCard(state: ProgressUiState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Subject Allocation Balance", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("TOP STUDIED", style = MaterialTheme.typography.labelSmall, color = SuccessGreen, fontWeight = FontWeight.Bold)
                    Text(state.topAndNeglected.topSubjectName, fontWeight = FontWeight.SemiBold)
                    Text("${state.topAndNeglected.topSubjectMinutes}m recorded", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Column {
                    Text("MOST NEGLECTED", style = MaterialTheme.typography.labelSmall, color = WarningAmber, fontWeight = FontWeight.Bold)
                    Text(state.topAndNeglected.neglectedSubjectName, fontWeight = FontWeight.SemiBold)
                    Text("${state.topAndNeglected.neglectedSubjectMinutes}m recorded", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun HeatmapCard(state: ProgressUiState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Study Heatmap (Time of Day)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            Text("Session density across time windows this week.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            Spacer(modifier = Modifier.height(12.dp))

            val dayHeaders = listOf("M", "T", "W", "T", "F", "S", "S")
            val slotLabels = listOf("06–09", "09–12", "12–15", "15–18", "18–22")

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Spacer(modifier = Modifier.width(36.dp))
                dayHeaders.forEach { d ->
                    Text(d, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.width(28.dp))
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            slotLabels.forEachIndexed { slotIdx, slotName ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(slotName, style = MaterialTheme.typography.labelSmall, fontSize = 9.sp, modifier = Modifier.width(36.dp))
                    (1..7).forEach { dayIdx ->
                        val slotData = state.heatmapSlots.find { it.dayOfWeek == dayIdx && it.slotIndex == slotIdx }
                        val intensityColor = when (slotData?.intensity ?: 0) {
                            1 -> MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                            2 -> MaterialTheme.colorScheme.primary.copy(alpha = 0.70f)
                            3 -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                        }
                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(intensityColor)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PeakWindowCard(state: ProgressUiState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "PEAK STUDY WINDOW",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                StatusPill(
                    text = state.peakWindow.confidenceLevel,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = state.peakWindow.windowName,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                text = if (state.peakWindowExplanation.isNotBlank()) state.peakWindowExplanation else state.peakWindow.recommendation,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
            )
        }
    }
}

@Composable
private fun AddStudyBuddyDialog(
    onDismiss: () -> Unit,
    onSave: (StudyBuddyData) -> Unit
) {
    var rawInput by remember { mutableStateOf("") }
    var buddyName by remember { mutableStateOf("") }
    var buddyXp by remember { mutableStateOf("") }
    var buddyStreak by remember { mutableStateOf("") }
    var buddyMinutes by remember { mutableStateOf("") }
    var parseError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("👥", fontSize = 20.sp)
                Text("Add Study Buddy", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Paste your buddy's shared stats token or enter their stats manually:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = rawInput,
                    onValueChange = { input ->
                        rawInput = input
                        val parsed = PersonalizationEngine.parseBuddyStatsToken(input)
                        if (parsed != null) {
                            buddyName = parsed.name
                            buddyXp = parsed.xp.toString()
                            buddyStreak = parsed.streakDays.toString()
                            buddyMinutes = parsed.focusMinutes.toString()
                            parseError = null
                        }
                    },
                    label = { Text("Paste Shared Token (Optional)") },
                    placeholder = { Text("[BUDDY-STATS:NAME=Alex:XP=850...]") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = buddyName,
                    onValueChange = { buddyName = it },
                    label = { Text("Buddy Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = buddyXp,
                        onValueChange = { buddyXp = it.filter { ch -> ch.isDigit() } },
                        label = { Text("XP") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = buddyStreak,
                        onValueChange = { buddyStreak = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Streak (d)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = buddyMinutes,
                    onValueChange = { buddyMinutes = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Focus Minutes") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (parseError != null) {
                    Text(parseError!!, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val name = buddyName.trim()
                    val xp = buddyXp.toIntOrNull() ?: 0
                    val streak = buddyStreak.toIntOrNull() ?: 0
                    val mins = buddyMinutes.toIntOrNull() ?: 0

                    if (name.isBlank()) {
                        parseError = "Please enter buddy name."
                    } else {
                        onSave(StudyBuddyData(name = name, xp = xp, streakDays = streak, focusMinutes = mins))
                    }
                }
            ) {
                Text("Save Buddy")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
