package com.focusstudy.app.core.di

import android.content.Context
import com.focusstudy.app.core.ai.AiCoachService
import com.focusstudy.app.core.ai.AiInsightService
import com.focusstudy.app.core.ai.AiPlannerService
import com.focusstudy.app.core.ai.AiReplanService
import com.focusstudy.app.core.auth.AuthManager
import com.focusstudy.app.core.auth.LocalAuthManager
import com.focusstudy.app.core.database.AppDatabase
import com.focusstudy.app.core.datastore.UserPreferencesManager
import com.focusstudy.app.core.repository.*

interface AppContainer {
    val database: AppDatabase
    val userPreferencesManager: UserPreferencesManager
    val syllabusRepository: SyllabusRepository
    val studyPlanRepository: StudyPlanRepository
    val progressRepository: ProgressRepository
    val userSettingsRepository: UserSettingsRepository
    val aiPlannerService: AiPlannerService
    val aiReplanService: AiReplanService
    val aiInsightService: AiInsightService
    val aiCoachService: AiCoachService
    val authManager: AuthManager
}

class DefaultAppContainer(private val context: Context) : AppContainer {
    override val database: AppDatabase by lazy {
        AppDatabase.getInstance(context)
    }

    override val userPreferencesManager: UserPreferencesManager by lazy {
        UserPreferencesManager(context)
    }

    override val syllabusRepository: SyllabusRepository by lazy {
        SyllabusRepositoryImpl(database)
    }

    override val studyPlanRepository: StudyPlanRepository by lazy {
        StudyPlanRepositoryImpl(database)
    }

    override val progressRepository: ProgressRepository by lazy {
        ProgressRepositoryImpl(database)
    }

    override val userSettingsRepository: UserSettingsRepository by lazy {
        UserSettingsRepositoryImpl(userPreferencesManager)
    }

    override val aiPlannerService: AiPlannerService by lazy {
        AiPlannerService(database)
    }

    override val aiReplanService: AiReplanService by lazy {
        AiReplanService(database)
    }

    override val aiInsightService: AiInsightService by lazy {
        AiInsightService(database)
    }

    override val aiCoachService: AiCoachService by lazy {
        AiCoachService(database, userPreferencesManager)
    }

    override val authManager: AuthManager by lazy {
        LocalAuthManager()
    }
}
