package com.focusstudy.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.focusstudy.app.core.design.theme.FocusStudyTheme
import com.focusstudy.app.feature.main.MainScreen
import com.focusstudy.app.feature.onboarding.OnboardingScreen
import com.focusstudy.app.feature.onboarding.OnboardingViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val appContainer = (application as FocusStudyApp).container
        val onboardingViewModel = OnboardingViewModel(
            db = appContainer.database,
            preferencesManager = appContainer.userPreferencesManager
        )

        setContent {
            val preferences by appContainer.userPreferencesManager.userPreferencesFlow
                .collectAsState(initial = null)

            FocusStudyTheme(themeMode = preferences?.themeMode ?: "system") {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    when {
                        preferences == null -> {
                            // Initial blank surface while DataStore preferences load
                        }
                        !preferences!!.isOnboardingCompleted -> {
                            OnboardingScreen(
                                viewModel = onboardingViewModel,
                                onFinished = {
                                    // preferencesFlow will reactively emit isOnboardingCompleted = true
                                }
                            )
                        }
                        else -> {
                            MainScreen(appContainer = appContainer)
                        }
                    }
                }
            }
        }
    }
}
