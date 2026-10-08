package com.focusstudy.app

import android.app.Application
import com.focusstudy.app.core.di.AppContainer
import com.focusstudy.app.core.di.DefaultAppContainer

import android.content.pm.ApplicationInfo
import com.focusstudy.app.core.notification.NotificationHelper
import com.focusstudy.app.core.security.AppCheckConfig
import com.focusstudy.app.core.security.SafeLogger
import com.focusstudy.app.core.worker.StudyWorkScheduler

class FocusStudyApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        val isDebug = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        AppCheckConfig.initialize(this, isDebug)
        SafeLogger.isDebugMode = isDebug

        container = DefaultAppContainer(this)
        NotificationHelper.createNotificationChannels(this)
        StudyWorkScheduler.schedulePeriodicWork(this)
    }
}
