package com.example.thresholdterm_002.data.model

data class StudySession(
    val id: Long,
    val startedAtMillis: Long,
    val durationMinutes: Int,
    val focusScore: Int,
    val memo: String = "",
    val subject: String = "집중 타이머"
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

data class SubjectStudyStat(
    val dateLabel: String,
    val subject: String,
    val totalMinutes: Int
)

data class CalendarDayStudyStat(
    val dateLabel: String,
    val totalMinutes: Int
)

data class StudySubject(
    val id: Long,
    val name: String
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
    val longitude: Double,
    val distanceKm: Double? = null,
    val kakaoPlaceId: String? = null
)

data class FocusFeedback(
    val score: Int,
    val title: String,
    val message: String
)
