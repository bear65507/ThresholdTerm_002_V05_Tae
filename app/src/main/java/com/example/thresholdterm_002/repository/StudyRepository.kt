package com.example.thresholdterm_002.repository

import android.content.Context
import com.example.thresholdterm_002.data.local.StudyDatabase
import com.example.thresholdterm_002.data.local.UserProfile
import com.example.thresholdterm_002.data.model.CalendarDayStudyStat
import com.example.thresholdterm_002.data.model.Competitor
import com.example.thresholdterm_002.data.model.DailyStudyStat
import com.example.thresholdterm_002.data.model.FocusFeedback
import com.example.thresholdterm_002.data.model.LibraryPlace
import com.example.thresholdterm_002.data.model.StudySession
import com.example.thresholdterm_002.data.model.StudyStats
import com.example.thresholdterm_002.data.model.StudySubject
import com.example.thresholdterm_002.data.model.SubjectStudyStat
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
                memo = memo,
                subject = "집중 타이머"
            )
        )
    }

    fun saveSubjectStudySession(
        subject: String,
        durationMinutes: Int,
        memo: String = "과목별 스톱워치",
        startedAtMillis: Long = System.currentTimeMillis()
    ) {
        database.insertSession(
            StudySession(
                id = System.currentTimeMillis(),
                startedAtMillis = startedAtMillis,
                durationMinutes = durationMinutes,
                focusScore = 100,
                memo = memo,
                subject = subject.ifBlank { "기타" }
            )
        )
    }

    fun getStats(): StudyStats = database.getStats()

    fun getDailyStats(): List<DailyStudyStat> = database.getDailyStats()

    fun getSubjectStats(): List<SubjectStudyStat> = database.getSubjectStats()

    fun getMonthlyCalendarStats(year: Int, month: Int): List<CalendarDayStudyStat> {
        return database.getMonthlyCalendarStats(year, month)
    }

    fun getSubjectStatsForDate(dateLabel: String): List<SubjectStudyStat> {
        return database.getSubjectStatsForDate(dateLabel)
    }

    fun getSubjects(): List<StudySubject> = database.getSubjects()

    fun addSubject(name: String) = database.addSubject(name)

    fun deleteSubject(name: String) = database.deleteSubject(name)

    fun getLeaderboard(): List<Competitor> = database.getLeaderboard()

    fun getRecommendedLibraries(
        profile: UserProfile?,
        latitude: Double? = null,
        longitude: Double? = null
    ): List<LibraryPlace> {
        return libraryApiClient.getRecommendedLibraries(
            profile = profile,
            latitude = latitude,
            longitude = longitude
        )
    }

    fun getLibrariesByCurrentLocation(latitude: Double, longitude: Double): List<LibraryPlace> {
        return libraryApiClient.getLibrariesByCurrentLocation(latitude, longitude)
    }

    fun getLibrariesByProfileRegion(profile: UserProfile?): List<LibraryPlace> {
        return libraryApiClient.getLibrariesByProfileRegion(profile)
    }

    fun getLibrarySearchError(): String? = NationalLibraryApiClient.getLastErrorMessage()

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
