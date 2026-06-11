package com.example.thresholdterm_002.data.model

data class StudySession(
    val id: Long,
    val startedAtMillis: Long,
    val durationMinutes: Int,
    val focusScore: Int,
    val memo: String = ""
)

data class StudyStats(
    val totalMinutes: Int,
    val sessionCount: Int,
    val averageFocusScore: Int
)

data class DailyStudyStat(
    val label: String,
    val totalMinutes: Int,
    val goalMinutes: Int,
    val averageFocusScore: Int
)

data class Competitor(
    val name: String,
    val totalMinutes: Int,
    val focusScore: Int
)

data class LibraryPlace(
    val name: String,
    val address: String,
    val openInfo: String,
    val latitude: Double,
    val longitude: Double
)

data class FocusFeedback(
    val score: Int,
    val title: String,
    val message: String
)
