package com.focusstudy.app.feature.onboarding

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.focusstudy.app.core.design.theme.SuccessGreen
import com.focusstudy.app.core.design.theme.WarningAmber
import com.focusstudy.app.feature.syllabus.ParsedTopicItem
import com.focusstudy.app.feature.syllabus.SyllabusParser

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    viewModel: OnboardingViewModel,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(state.isCompleted) {
        if (state.isCompleted) {
            onFinished()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Focus Study Setup",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    if (state.currentStep > 1) {
                        IconButton(onClick = { viewModel.previousStep() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                actions = {
                    Text(
                        text = "Step ${state.currentStep} of 6",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(end = 16.dp)
                    )
                }
            )
        },
        bottomBar = {
            Surface(
                tonalElevation = 4.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (state.currentStep > 1) {
                        OutlinedButton(
                            onClick = { viewModel.previousStep() },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Previous")
                        }
                    } else {
                        Spacer(modifier = Modifier.width(1.dp))
                    }

                    if (state.currentStep < 6) {
                        Button(
                            onClick = { viewModel.nextStep() },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Continue")
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
                        }
                    } else {
                        Button(
                            onClick = { viewModel.completeOnboardingAndBuildPlan() },
                            enabled = !state.isBuildingPlan && state.parsedTopics.isNotEmpty(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            if (state.isBuildingPlan) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Building Plan...")
                            } else {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Build My Study Plan")
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp)
        ) {
            // Step Progress Line
            LinearProgressIndicator(
                progress = { state.currentStep / 6f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
            )

            Spacer(modifier = Modifier.height(20.dp))

            when (state.currentStep) {
                1 -> Step1ExamType(state, viewModel)
                2 -> Step2ExamDate(state, viewModel)
                3 -> Step3AvailableTime(state, viewModel)
                4 -> Step4SessionPreference(state, viewModel)
                5 -> Step5SyllabusInput(state, viewModel)
                6 -> Step6TopicReview(state, viewModel)
            }
        }
    }
}

@Composable
fun Step1ExamType(state: OnboardingUiState, viewModel: OnboardingViewModel) {
    val examTypes = listOf(
        "University Exam",
        "School Exam",
        "Competitive Exam",
        "Certification",
        "Entrance Exam",
        "Interview Prep",
        "Custom"
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Turn your syllabus into a plan.",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "What exam or goal are you preparing for?",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(20.dp))

        OutlinedTextField(
            value = state.examTitle,
            onValueChange = { viewModel.setExamTitle(it) },
            label = { Text("Exam / Course Title") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text("Exam Category", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(8.dp))

        examTypes.forEach { type ->
            val isSelected = state.examType == type
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clickable { viewModel.setExamType(type) },
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                ),
                border = if (isSelected) CardDefaults.outlinedCardBorder() else null
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(type, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                    if (isSelected) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

@Composable
fun Step2ExamDate(state: OnboardingUiState, viewModel: OnboardingViewModel) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "When is your exam?",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "We will structure macro milestones and buffer days leading up to this date.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(24.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "${state.daysRemaining} DAYS REMAINING",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "${state.daysRemaining} Days",
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = "Planned study window: ~${state.daysRemaining / 7} weeks",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text("Adjust days remaining:", style = MaterialTheme.typography.labelLarge)
        Slider(
            value = state.daysRemaining.toFloat(),
            onValueChange = { viewModel.setDaysRemaining(it.toInt()) },
            valueRange = 7f..180f,
            steps = 25
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text("Tentative Date", fontWeight = FontWeight.SemiBold)
                Text("Allow adaptive adjustments if date changes", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(
                checked = state.isTentativeDate,
                onCheckedChange = { viewModel.setTentativeDate(it) }
            )
        }
    }
}

@Composable
fun Step3AvailableTime(state: OnboardingUiState, viewModel: OnboardingViewModel) {
    val windows = listOf(
        "Early Morning (06:00 - 08:30)",
        "Morning (07:00 - 10:00)",
        "Afternoon (14:00 - 17:00)",
        "Evening (17:30 - 20:30)",
        "Night (21:00 - 23:30)"
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "How much time can you study?",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Realistic availability prevents burnout and plan abandonment.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(20.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Weekday Target: ${state.dailyWeekdayHours.toInt()} hours / day",
                    fontWeight = FontWeight.SemiBold
                )
                Slider(
                    value = state.dailyWeekdayHours,
                    onValueChange = { viewModel.setStudyHours(it, state.dailyWeekendHours) },
                    valueRange = 1f..8f,
                    steps = 7
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Weekend Target: ${state.dailyWeekendHours.toInt()} hours / day",
                    fontWeight = FontWeight.SemiBold
                )
                Slider(
                    value = state.dailyWeekendHours,
                    onValueChange = { viewModel.setStudyHours(state.dailyWeekdayHours, it) },
                    valueRange = 1f..10f,
                    steps = 9
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text("Preferred Peak Study Window", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(8.dp))

        windows.forEach { win ->
            val isSelected = state.preferredStudyWindow == win
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp)
                    .clickable { viewModel.setStudyWindow(win) },
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(win, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                    if (isSelected) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

@Composable
fun Step4SessionPreference(state: OnboardingUiState, viewModel: OnboardingViewModel) {
    val presets = listOf(
        Pair(25, 5),
        Pair(30, 5),
        Pair(45, 10),
        Pair(50, 10),
        Pair(60, 10)
    )

    val intensities = listOf("Balanced", "Exam-First", "Weakness-First", "Low-Burnout")

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Session length & Intensity",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Choose your focus interval and pace preference.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(20.dp))

        Text("Focus / Break Ratio", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            presets.forEach { (sessionMin, breakMin) ->
                val isSelected = state.sessionDurationMinutes == sessionMin
                OutlinedButton(
                    onClick = { viewModel.setSessionPreference(sessionMin, breakMin) },
                    modifier = Modifier.weight(1f),
                    colors = if (isSelected) ButtonDefaults.outlinedButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                    else ButtonDefaults.outlinedButtonColors(),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                ) {
                    Text(
                        "$sessionMin/$breakMin",
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        Text("Planning Intensity Style", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(8.dp))

        intensities.forEach { style ->
            val isSelected = state.studyIntensity == style
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clickable { viewModel.setStudyIntensity(style) },
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(style, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                        val desc = when (style) {
                            "Exam-First" -> "Prioritizes high exam weightage chapters early."
                            "Weakness-First" -> "Allocates more repetitions to harder subjects."
                            "Low-Burnout" -> "Gentler ramp-up with generous breaks and revision buffers."
                            else -> "Equal balance across all syllabus units."
                        }
                        Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (isSelected) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

@Composable
fun Step5SyllabusInput(state: OnboardingUiState, viewModel: OnboardingViewModel) {
    val context = LocalContext.current

    // Document Picker launcher for PDF, Markdown, or text files (FSTUDY-003-02, FSTUDY-014-05)
    val documentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { viewModel.importContentFromUri(context, it) }
    }

    // Image Picker launcher for syllabus photos, screenshots, or notes (FSTUDY-003-03, FSTUDY-014-05)
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { viewModel.importContentFromUri(context, it) }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Ingest your syllabus",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Upload a PDF, snap a photo, paste text, or load our sample template.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Action Toolbar: PDF Import, Image Scan, Sample
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = { documentPickerLauncher.launch("*/*") },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp)
            ) {
                Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("PDF / Doc", style = MaterialTheme.typography.labelSmall)
            }

            OutlinedButton(
                onClick = { imagePickerLauncher.launch("image/*") },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp)
            ) {
                Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Scan Image", style = MaterialTheme.typography.labelSmall)
            }

            TextButton(
                onClick = {
                    val sample = SyllabusParser.sampleComputerScienceSyllabus()
                    viewModel.updateSyllabusText(sample)
                },
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp)
            ) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("CS Sample", style = MaterialTheme.typography.labelSmall)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Error Banner
        if (state.importErrorMessage != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                shape = RoundedCornerShape(10.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = state.importErrorMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                    IconButton(
                        onClick = { viewModel.clearImportMessages() },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Dismiss", modifier = Modifier.size(16.dp))
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Success Banner
        if (state.importSuccessMessage != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SuccessGreen.copy(alpha = 0.15f)),
                shape = RoundedCornerShape(10.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            tint = SuccessGreen,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = state.importSuccessMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = SuccessGreen,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    IconButton(
                        onClick = { viewModel.clearImportMessages() },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Dismiss", modifier = Modifier.size(16.dp))
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        if (state.isImporting) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(modifier = Modifier.width(10.dp))
                Text("Validating and parsing syllabus...", style = MaterialTheme.typography.bodySmall)
            }
        }

        OutlinedTextField(
            value = state.syllabusRawText,
            onValueChange = { viewModel.updateSyllabusText(it) },
            placeholder = { Text("Or paste your syllabus here...\n\nUnit 1: Mathematics\nCalculus\nAlgebra...") },
            modifier = Modifier
                .fillMaxWidth()
                .height(230.dp),
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "✓ Extracted ${state.parsedTopics.size} topics ready for review",
            style = MaterialTheme.typography.labelMedium,
            color = SuccessGreen,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun Step6TopicReview(state: OnboardingUiState, viewModel: OnboardingViewModel) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Review extracted topics",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "We found ${state.parsedTopics.size} topics across ${state.parsedTopics.map { it.subjectName }.distinct().size} subjects. Adjust difficulties or remove topics.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(state.parsedTopics, key = { it.id }) { topic ->
                ParsedTopicCard(topic, onDifficultyChanged = { diff ->
                    viewModel.updateTopicDifficulty(topic.id, diff)
                }, onRemove = {
                    viewModel.removeTopic(topic.id)
                })
            }
        }
    }
}

@Composable
fun ParsedTopicCard(
    topic: ParsedTopicItem,
    onDifficultyChanged: (String) -> Unit,
    onRemove: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${topic.subjectName} · ${topic.unitTitle}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = topic.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                IconButton(onClick = onRemove) {
                    Icon(Icons.Default.Delete, contentDescription = "Remove", tint = MaterialTheme.colorScheme.error)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf("easy", "medium", "hard").forEach { diff ->
                    val isSelected = topic.difficulty == diff
                    SuggestionChip(
                        onClick = { onDifficultyChanged(diff) },
                        label = {
                            Text(
                                diff.replaceFirstChar { it.uppercase() },
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        colors = if (isSelected) SuggestionChipDefaults.suggestionChipColors(
                            containerColor = when (diff) {
                                "easy" -> SuccessGreen.copy(alpha = 0.2f)
                                "hard" -> WarningAmber.copy(alpha = 0.2f)
                                else -> MaterialTheme.colorScheme.primaryContainer
                            }
                        ) else SuggestionChipDefaults.suggestionChipColors()
                    )
                }
            }
        }
    }
}
