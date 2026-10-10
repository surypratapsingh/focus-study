package com.focusstudy.app.feature.recall

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.focusstudy.app.core.database.entity.Subject
import com.focusstudy.app.core.database.entity.Subtopic
import com.focusstudy.app.core.database.entity.Topic
import com.focusstudy.app.core.design.theme.StatusPill
import com.focusstudy.app.core.design.theme.SuccessGreen
import com.focusstudy.app.core.design.theme.WarningAmber
import kotlinx.coroutines.launch

@Composable
fun ActiveRecallDialog(
    topic: Topic,
    subject: Subject?,
    subtopics: List<Subtopic> = emptyList(),
    apiKey: String = "",
    onDismiss: () -> Unit,
    onCompleted: (masteryPercent: Int, earnedXp: Int) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(true) }
    var deck by remember { mutableStateOf<ActiveRecallDeck?>(null) }
    var currentCardIndex by remember { mutableIntStateOf(0) }
    var isAnswerRevealed by remember { mutableStateOf(false) }
    var showHint by remember { mutableStateOf(false) }
    var isCompleted by remember { mutableStateOf(false) }
    var finalMasteryPercent by remember { mutableIntStateOf(0) }
    var finalEarnedXp by remember { mutableIntStateOf(0) }

    val advanceCard: (List<ActiveRecallCard>) -> Unit = { cardList ->
        if (currentCardIndex + 1 < cardList.size) {
            currentCardIndex += 1
            isAnswerRevealed = false
            showHint = false
        } else {
            finalMasteryPercent = ActiveRecallEngine.calculateDeckMastery(cardList)
            finalEarnedXp = ActiveRecallEngine.calculateEarnedXp(cardList)
            isCompleted = true
        }
    }

    LaunchedEffect(topic.id) {
        isLoading = true
        val generatedDeck = if (apiKey.isNotBlank()) {
            ActiveRecallEngine.generateAiDeck(topic, subject, subtopics, apiKey)
        } else {
            ActiveRecallEngine.generateHeuristicDeck(topic, subject, subtopics)
        }
        deck = generatedDeck
        isLoading = false
    }

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
                // Top Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("🧠", fontSize = 20.sp)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Active Recall Sprint",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${subject?.name ?: "Subject"} · ${topic.name}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (isLoading) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = if (apiKey.isNotBlank()) "Generating tailored AI flashcards..." else "Preparing offline recall deck...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else if (isCompleted) {
                    // Completion View
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .clip(CircleShape)
                                .background(SuccessGreen.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("🏆", fontSize = 42.sp)
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Recall Sprint Completed!",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Active memory retrieval reinforces long-term neural retention.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        // Score & XP Card
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "$finalMasteryPercent%",
                                    fontSize = 44.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (finalMasteryPercent >= 75) SuccessGreen else MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "Recall Mastery Score",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold
                                )

                                Spacer(modifier = Modifier.height(12.dp))
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                                Spacer(modifier = Modifier.height(12.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceAround
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            text = "+$finalEarnedXp XP",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = SuccessGreen
                                        )
                                        Text("Scholar XP", style = MaterialTheme.typography.labelSmall)
                                    }
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            text = "${deck?.cards?.size ?: 5} Cards",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text("Evaluated", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        Button(
                            onClick = {
                                onCompleted(finalMasteryPercent, finalEarnedXp)
                                onDismiss()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Save & Update Progress", fontWeight = FontWeight.Bold)
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedButton(
                            onClick = {
                                currentCardIndex = 0
                                isAnswerRevealed = false
                                showHint = false
                                isCompleted = false
                                deck?.cards?.forEach { it.masteryLevel = 0 }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Review Deck Again")
                        }
                    }
                } else {
                    // Flashcard Sprint View
                    val currentDeck = deck
                    val cards = currentDeck?.cards ?: emptyList()
                    val card = cards.getOrNull(currentCardIndex)

                    if (card != null) {
                        // Progress Bar & Counter
                        val progress = (currentCardIndex + 1).toFloat() / cards.size.toFloat()
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Card ${currentCardIndex + 1} of ${cards.size}",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                StatusPill(
                                    text = if (currentDeck?.isAiGenerated == true) "BYOK GEMINI" else "OFFLINE HEURISTIC",
                                    containerColor = if (currentDeck?.isAiGenerated == true)
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                    else MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = if (currentDeck?.isAiGenerated == true)
                                        MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Active Card Display
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(20.dp)
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        StatusPill(
                                            text = card.conceptTag.uppercase(),
                                            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                            contentColor = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = card.difficulty.replaceFirstChar { it.uppercase() },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(16.dp))

                                    // Question Text
                                    Text(
                                        text = card.question,
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        lineHeight = 28.sp
                                    )

                                    // Hint accordion
                                    if (!card.hint.isNullOrBlank()) {
                                        Spacer(modifier = Modifier.height(12.dp))
                                        if (showHint) {
                                            Card(
                                                modifier = Modifier.fillMaxWidth(),
                                                colors = CardDefaults.cardColors(
                                                    containerColor = WarningAmber.copy(alpha = 0.12f)
                                                ),
                                                shape = RoundedCornerShape(10.dp)
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(12.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Icon(
                                                        Icons.Default.Lightbulb,
                                                        contentDescription = null,
                                                        tint = WarningAmber,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text(
                                                        text = card.hint,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurface
                                                    )
                                                }
                                            }
                                        } else {
                                            TextButton(
                                                onClick = { showHint = true },
                                                contentPadding = PaddingValues(0.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.Lightbulb,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Show Hint", style = MaterialTheme.typography.labelMedium)
                                            }
                                        }
                                    }

                                    // Answer Reveal Section
                                    AnimatedVisibility(
                                        visible = isAnswerRevealed,
                                        enter = fadeIn() + expandVertically()
                                    ) {
                                        Column(modifier = Modifier.padding(top = 16.dp)) {
                                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                                            Spacer(modifier = Modifier.height(14.dp))
                                            Text(
                                                text = "Model Concept & Answer:",
                                                style = MaterialTheme.typography.labelLarge,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Text(
                                                text = card.answer,
                                                style = MaterialTheme.typography.bodyMedium,
                                                lineHeight = 22.sp
                                            )
                                        }
                                    }
                                }

                                // Flip / Rating Action Buttons
                                Column(modifier = Modifier.padding(top = 16.dp)) {
                                    if (!isAnswerRevealed) {
                                        Button(
                                            onClick = { isAnswerRevealed = true },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(50.dp),
                                            shape = RoundedCornerShape(14.dp)
                                        ) {
                                            Icon(Icons.Default.Visibility, contentDescription = null)
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Reveal Answer & Self-Assess", fontWeight = FontWeight.SemiBold)
                                        }
                                    } else {
                                        Text(
                                            text = "Evaluate your recall accuracy:",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.padding(bottom = 8.dp)
                                        )
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            OutlinedButton(
                                                onClick = {
                                                    card.masteryLevel = 1
                                                    advanceCard(cards)
                                                },
                                                modifier = Modifier.weight(1f),
                                                shape = RoundedCornerShape(12.dp)
                                            ) {
                                                Text("🔴 Hard\n+10 XP", textAlign = TextAlign.Center, fontSize = 11.sp)
                                            }

                                            FilledTonalButton(
                                                onClick = {
                                                    card.masteryLevel = 2
                                                    advanceCard(cards)
                                                },
                                                modifier = Modifier.weight(1f),
                                                shape = RoundedCornerShape(12.dp)
                                            ) {
                                                Text("🟡 Good\n+20 XP", textAlign = TextAlign.Center, fontSize = 11.sp)
                                            }

                                            Button(
                                                onClick = {
                                                    card.masteryLevel = 3
                                                    advanceCard(cards)
                                                },
                                                modifier = Modifier.weight(1f),
                                                shape = RoundedCornerShape(12.dp),
                                                colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen)
                                            ) {
                                                Text("🟢 Easy\n+30 XP", textAlign = TextAlign.Center, fontSize = 11.sp, color = Color.White)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
