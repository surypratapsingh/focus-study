package com.focusstudy.app.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.focusstudy.app.core.database.dao.*
import com.focusstudy.app.core.database.entity.*

@Database(
    entities = [
        StudentProfile::class,
        Exam::class,
        Subject::class,
        UnitEntity::class,
        Topic::class,
        Subtopic::class,
        StudyPlan::class,
        StudyPlanDay::class,
        StudySession::class,
        StudyAttempt::class,
        AvailabilityWindow::class,
        StudyPreference::class,
        ProgressSnapshot::class,
        ActivityEvent::class,
        AiInsight::class,
        AiPlanVersion::class,
        Achievement::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun examDao(): ExamDao
    abstract fun syllabusDao(): SyllabusDao
    abstract fun studyPlanDao(): StudyPlanDao
    abstract fun studyAttemptDao(): StudyAttemptDao
    abstract fun availabilityDao(): AvailabilityDao
    abstract fun progressDao(): ProgressDao
    abstract fun aiInsightDao(): AiInsightDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "focus_study.db"
                ).fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
