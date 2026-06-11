package com.example.thresholdterm_002.repository

import android.content.Context
import com.example.thresholdterm_002.data.local.StudyDatabase
import com.example.thresholdterm_002.data.local.UserProfile
import com.example.thresholdterm_002.data.model.Competitor
import com.example.thresholdterm_002.data.model.DailyStudyStat
import com.example.thresholdterm_002.data.model.FocusFeedback
import com.example.thresholdterm_002.data.model.LibraryPlace
import com.example.thresholdterm_002.data.model.StudySession
import com.example.thresholdterm_002.data.model.StudyStats
import com.example.thresholdterm_002.data.remote.LibraryApiClient
import com.example.thresholdterm_002.data.remote.NationalLibraryApiClient
import com.example.thresholdterm_002.ml.FocusPostureAnalyzer
import com.example.thresholdterm_002.ml.FocusSignal

class StudyRepository(
    private val database: StudyDatabase,
    private val libraryApiClient: LibraryApiClient = NationalLibraryApiClient(),
    private val focusPostureAnalyzer: FocusPostureAnalyzer = FocusPostureAnalyzer()
) {

    fun saveStudySession(durationMinutes: Int, focusScore: Int, memo: String = "") {
        database.insertSession(
            StudySession(
                id = System.currentTimeMillis(),
                startedAtMillis = System.currentTimeMillis(),
                durationMinutes = durationMinutes,
                focusScore = focusScore,
                memo = memo
            )
        )
    }

    fun getStats(): StudyStats = database.getStats()

    fun getDailyStats(): List<DailyStudyStat> = database.getDailyStats()

    fun getLeaderboard(): List<Competitor> = database.getLeaderboard()

    fun getRecommendedLibraries(profile: UserProfile?): List<LibraryPlace> {
        return libraryApiClient.getRecommendedLibraries(profile = profile)
    }

    fun checkFocus(signal: FocusSignal): FocusFeedback = focusPostureAnalyzer.analyze(signal)

    companion object {
        @Volatile
        private var sharedInstance: StudyRepository? = null

        val instance: StudyRepository
            get() = sharedInstance ?: error("StudyRepository must be initialized from Application.")

        fun initialize(context: Context) {
            if (sharedInstance == null) {
                synchronized(this) {
                    if (sharedInstance == null) {
                        sharedInstance = StudyRepository(
                            database = StudyDatabase.getInstance(context.applicationContext)
                        )
                    }
                }
            }
        }
    }
}
