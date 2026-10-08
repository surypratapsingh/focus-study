package com.focusstudy.app.feature.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import com.focusstudy.app.core.di.AppContainer
import com.focusstudy.app.feature.ai.AiCoachScreen
import com.focusstudy.app.feature.focus.FocusScreen
import com.focusstudy.app.feature.planner.PlannerScreen
import com.focusstudy.app.feature.progress.ProgressScreen
import com.focusstudy.app.feature.settings.SettingsScreen
import com.focusstudy.app.feature.today.TodayScreen

enum class MainDestination(val title: String, val icon: ImageVector) {
    TODAY("Today", Icons.Default.Home),
    PLAN("Plan", Icons.Default.DateRange),
    FOCUS("Focus", Icons.Default.Timer),
    PROGRESS("Progress", Icons.Default.Insights),
    AI("AI Coach", Icons.Default.SmartToy)
}

@Composable
fun MainScreen(
    appContainer: AppContainer,
    modifier: Modifier = Modifier
) {
    var selectedDestination by remember { mutableStateOf(MainDestination.TODAY) }
    var selectedSessionForFocus by remember { mutableStateOf<com.focusstudy.app.core.database.entity.StudySession?>(null) }
    var showSettings by remember { mutableStateOf(false) }

    val configuration = LocalConfiguration.current
    val isWideScreen = configuration.screenWidthDp >= 600

    if (showSettings) {
        SettingsScreen(
            appContainer = appContainer,
            onBack = { showSettings = false },
            modifier = modifier
        )
    } else if (isWideScreen) {
        Row(modifier = modifier.fillMaxSize()) {
            NavigationRail(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.fillMaxHeight()
            ) {
                Spacer(modifier = Modifier.height(12.dp))
                MainDestination.entries.forEach { destination ->
                    NavigationRailItem(
                        selected = selectedDestination == destination,
                        onClick = { selectedDestination = destination },
                        icon = { Icon(destination.icon, contentDescription = destination.title) },
                        label = { Text(destination.title) }
                    )
                }
            }
            VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                when (selectedDestination) {
                    MainDestination.TODAY -> TodayScreen(
                        appContainer = appContainer,
                        modifier = Modifier.fillMaxSize(),
                        onNavigateToFocus = { session ->
                            selectedSessionForFocus = session
                            selectedDestination = MainDestination.FOCUS
                        },
                        onNavigateToSettings = { showSettings = true }
                    )
                    MainDestination.PLAN -> PlannerScreen(
                        appContainer = appContainer,
                        modifier = Modifier.fillMaxSize()
                    )
                    MainDestination.FOCUS -> FocusScreen(
                        appContainer = appContainer,
                        modifier = Modifier.fillMaxSize(),
                        initialSession = selectedSessionForFocus,
                        onSessionCompleted = {
                            selectedSessionForFocus = null
                            selectedDestination = MainDestination.TODAY
                        }
                    )
                    MainDestination.PROGRESS -> ProgressScreen(
                        appContainer = appContainer,
                        modifier = Modifier.fillMaxSize()
                    )
                    MainDestination.AI -> AiCoachScreen(
                        appContainer = appContainer,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    } else {
        Scaffold(
            modifier = modifier.fillMaxSize(),
            bottomBar = {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ) {
                    MainDestination.entries.forEach { destination ->
                        NavigationBarItem(
                            selected = selectedDestination == destination,
                            onClick = { selectedDestination = destination },
                            icon = { Icon(destination.icon, contentDescription = destination.title) },
                            label = { Text(destination.title) }
                        )
                    }
                }
            }
        ) { innerPadding ->
            val contentModifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)

            when (selectedDestination) {
                MainDestination.TODAY -> TodayScreen(
                    appContainer = appContainer,
                    modifier = contentModifier,
                    onNavigateToFocus = { session ->
                        selectedSessionForFocus = session
                        selectedDestination = MainDestination.FOCUS
                    },
                    onNavigateToSettings = { showSettings = true }
                )
                MainDestination.PLAN -> PlannerScreen(
                    appContainer = appContainer,
                    modifier = contentModifier
                )
                MainDestination.FOCUS -> FocusScreen(
                    appContainer = appContainer,
                    modifier = contentModifier,
                    initialSession = selectedSessionForFocus,
                    onSessionCompleted = {
                        selectedSessionForFocus = null
                        selectedDestination = MainDestination.TODAY
                    }
                )
                MainDestination.PROGRESS -> ProgressScreen(
                    appContainer = appContainer,
                    modifier = contentModifier
                )
                MainDestination.AI -> AiCoachScreen(
                    appContainer = appContainer,
                    modifier = contentModifier
                )
            }
        }
    }
}
