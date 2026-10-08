package com.focusstudy.app.feature.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.focusstudy.app.core.design.theme.StatusPill
import com.focusstudy.app.core.design.theme.SuccessGreen
import com.focusstudy.app.core.design.theme.WarningAmber

enum class TutorialTab(val title: String, val icon: ImageVector) {
    VOICE_COACH("Voice Coach", Icons.Default.Mic),
    TODAY_DASHBOARD("Daily Flow", Icons.Default.Home),
    ADAPTIVE_PLAN("Planner", Icons.Default.DateRange),
    FOCUS_TIMER("Focus Timer", Icons.Default.Timer),
    THEMES("Dark Themes", Icons.Default.DarkMode)
}

@Composable
fun AppTutorialDialog(
    onDismiss: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(TutorialTab.VOICE_COACH) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.88f),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "How Focus Study Works",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Interactive Guide & Voice Command Cheatsheet",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close guide")
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Scrollable Segmented Category Bar
                ScrollableTabRow(
                    selectedTabIndex = selectedTab.ordinal,
                    edgePadding = 0.dp,
                    divider = {},
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TutorialTab.entries.forEach { tab ->
                        Tab(
                            selected = selectedTab == tab,
                            onClick = { selectedTab = tab },
                            text = {
                                Text(
                                    tab.title,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (selectedTab == tab) FontWeight.Bold else FontWeight.Medium
                                )
                            },
                            icon = {
                                Icon(tab.icon, contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Tab Content Body
                Box(modifier = Modifier.weight(1f)) {
                    when (selectedTab) {
                        TutorialTab.VOICE_COACH -> VoiceCoachTutorialContent()
                        TutorialTab.TODAY_DASHBOARD -> TodayDashboardTutorialContent()
                        TutorialTab.ADAPTIVE_PLAN -> AdaptivePlanTutorialContent()
                        TutorialTab.FOCUS_TIMER -> FocusTimerTutorialContent()
                        TutorialTab.THEMES -> ThemesTutorialContent()
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Bottom Dismiss Action
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Got It — Ready to Study", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun VoiceCoachTutorialContent() {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Mic, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Zero-Effort Voice Logging",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "You don't need to manually research dates or fill out complex forms. Tap the mic icon or type naturally, and the AI logs sessions directly into your local database.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item {
            Text("Voice Command Examples:", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        }

        item {
            TutorialCommandCard(
                command = "\"Coding from 11:30 to 2:00, enter this in data\"",
                explanation = "AI calculates 150 minutes (2h 30m), awards +300 XP, updates your Scholar ranking, and writes the session to your Room database."
            )
        }

        item {
            TutorialCommandCard(
                command = "\"Read my schedule\" or \"Read things\"",
                explanation = "AI summarizes today's study blocks and uses Android Text-to-Speech to read the schedule aloud to you hands-free."
            )
        }

        item {
            TutorialCommandCard(
                command = "\"Arrange a schedule\" or \"Rebuild my week\"",
                explanation = "AI re-optimizes your upcoming calendar across available morning and evening study windows."
            )
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                Text(
                    text = "Tip: Tap the speaker icon on any message to hear it read aloud.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun TodayDashboardTutorialContent() {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            TutorialStepCard(
                number = "1",
                title = "Exam Countdown & Syllabus Completion",
                description = "Always know exactly how many days remain before finals, and track syllabus coverage progress in real-time."
            )
        }
        item {
            TutorialStepCard(
                number = "2",
                title = "Daily Target Minutes Bar",
                description = "Displays planned vs completed study hours. The progress bar updates dynamically as you complete sessions."
            )
        }
        item {
            TutorialStepCard(
                number = "3",
                title = "Next Recommended Session (\"Study Now\")",
                description = "Eliminates decision fatigue. A single tap launches your highest-priority topic directly into the Focus Timer."
            )
        }
        item {
            TutorialStepCard(
                number = "4",
                title = "AI Daily Focus Brief",
                description = "Every morning, Gemini synthesizes a 3-sentence actionable strategy highlighting urgent topics and revision targets."
            )
        }
    }
}

@Composable
private fun AdaptivePlanTutorialContent() {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            TutorialStepCard(
                number = "1",
                title = "6 Macro Learning Phases",
                description = "Topics automatically flow through Foundations, Deep Learning, Practice, Spaced Revision, Mock Testing, and Final Review with buffer days reserved before the exam."
            )
        }
        item {
            TutorialStepCard(
                number = "2",
                title = "Session Locking & Drag Adjustments",
                description = "Tap the lock icon on any session to prevent AI algorithms from shifting it when rebalancing your calendar."
            )
        }
        item {
            TutorialStepCard(
                number = "3",
                title = "Adaptive AI Replan on Schedule Slips",
                description = "If you miss study windows, the risk engine detects schedule slips and generates a non-destructive replan with clear before-and-after diffs."
            )
        }
    }
}

@Composable
private fun FocusTimerTutorialContent() {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            TutorialStepCard(
                number = "1",
                title = "Distraction-Free Focus Ring",
                description = "Interactive countdown timer with crisp visual progress ring, pause/resume controls, and elapsed minute counter."
            )
        }
        item {
            TutorialStepCard(
                number = "2",
                title = "Haptic & Audio Feedback",
                description = "Subtle tactile vibration pulses and completion audio chimes guide your intervals without looking at your screen."
            )
        }
        item {
            TutorialStepCard(
                number = "3",
                title = "Scholar XP & Streak Rewards",
                description = "Completing sessions adds Scholar XP, advances your rank (Novice to Grandmaster), and keeps your study streak alive."
            )
        }
    }
}

@Composable
private fun ThemesTutorialContent() {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("Selectable Dark & Light Menus", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "Visit Settings & Data anytime to choose between 4 crafted appearance modes:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item {
            TutorialThemeOptionCard(
                icon = Icons.Default.SettingsSuggest,
                title = "System Default",
                description = "Matches your Android device's system-wide dark or light schedule automatically."
            )
        }

        item {
            TutorialThemeOptionCard(
                icon = Icons.Default.DarkMode,
                title = "Dark Slate Mode",
                description = "Deep slate blue-gray palette designed for relaxed evening study sessions."
            )
        }

        item {
            TutorialThemeOptionCard(
                icon = Icons.Default.Contrast,
                title = "Midnight OLED (Pure Black)",
                description = "True #000000 AMOLED black background with obsidian cards. Saves maximum battery and offers ultra-crisp contrast."
            )
        }

        item {
            TutorialThemeOptionCard(
                icon = Icons.Default.LightMode,
                title = "Paper Light Mode",
                description = "Crisp, clean academic white surface with high-legibility slate typography."
            )
        }
    }
}

@Composable
private fun TutorialCommandCard(
    command: String,
    explanation: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = command,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = explanation,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun TutorialStepCard(
    number: String,
    title: String,
    description: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = number,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun TutorialThemeOptionCard(
    icon: ImageVector,
    title: String,
    description: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(2.dp))
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
