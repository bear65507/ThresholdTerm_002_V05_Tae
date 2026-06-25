package com.example.thresholdterm_002.data.local

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.example.thresholdterm_002.data.model.CalendarDayStudyStat
import com.example.thresholdterm_002.data.model.Competitor
import com.example.thresholdterm_002.data.model.DailyStudyStat
import com.example.thresholdterm_002.data.model.StudySession
import com.example.thresholdterm_002.data.model.StudyStats
import com.example.thresholdterm_002.data.model.StudySubject
import com.example.thresholdterm_002.data.model.SubjectStudyStat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class StudyDatabase private constructor(context: Context) :
    SQLiteOpenHelper(context.applicationContext, DATABASE_NAME, null, DATABASE_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABLE_STUDY_SESSIONS (
                $COLUMN_ID INTEGER PRIMARY KEY,
                $COLUMN_STARTED_AT INTEGER NOT NULL,
                $COLUMN_DURATION_MINUTES INTEGER NOT NULL,
                $COLUMN_FOCUS_SCORE INTEGER NOT NULL,
                $COLUMN_MEMO TEXT NOT NULL,
                $COLUMN_SUBJECT TEXT NOT NULL DEFAULT '$DEFAULT_SUBJECT'
            )
            """.trimIndent()
        )
        createSubjectsTable(db)
        seedDefaultSubjects(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL(
                "ALTER TABLE $TABLE_STUDY_SESSIONS ADD COLUMN $COLUMN_SUBJECT TEXT NOT NULL DEFAULT '$DEFAULT_SUBJECT'"
            )
        }
        if (oldVersion < 3) {
            createSubjectsTable(db)
            seedDefaultSubjects(db)
        }
    }

    private fun createSubjectsTable(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_STUDY_SUBJECTS (
                $COLUMN_SUBJECT_ID INTEGER PRIMARY KEY,
                $COLUMN_SUBJECT_NAME TEXT NOT NULL UNIQUE
            )
            """.trimIndent()
        )
    }

    private fun seedDefaultSubjects(db: SQLiteDatabase) {
        listOf("국어", "영어", "수학", "과학", "사회").forEach { subject ->
            val values = ContentValues().apply {
                put(COLUMN_SUBJECT_ID, subject.hashCode().toLong())
                put(COLUMN_SUBJECT_NAME, subject)
            }
            db.insertWithOnConflict(
                TABLE_STUDY_SUBJECTS,
                null,
                values,
                SQLiteDatabase.CONFLICT_IGNORE
            )
        }
    }

    fun insertSession(session: StudySession) {
        val values = ContentValues().apply {
            put(COLUMN_ID, session.id)
            put(COLUMN_STARTED_AT, session.startedAtMillis)
            put(COLUMN_DURATION_MINUTES, session.durationMinutes)
            put(COLUMN_FOCUS_SCORE, session.focusScore)
            put(COLUMN_MEMO, session.memo)
            put(COLUMN_SUBJECT, session.subject.ifBlank { DEFAULT_SUBJECT })
        }
        writableDatabase.insertWithOnConflict(
            TABLE_STUDY_SESSIONS,
            null,
            values,
            SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    fun getSessions(): List<StudySession> {
        val sessions = mutableListOf<StudySession>()
        readableDatabase.query(
            TABLE_STUDY_SESSIONS,
            null,
            null,
            null,
            null,
            null,
            "$COLUMN_STARTED_AT DESC"
        ).use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(COLUMN_ID)
            val startedAtIndex = cursor.getColumnIndexOrThrow(COLUMN_STARTED_AT)
            val durationIndex = cursor.getColumnIndexOrThrow(COLUMN_DURATION_MINUTES)
            val focusScoreIndex = cursor.getColumnIndexOrThrow(COLUMN_FOCUS_SCORE)
            val memoIndex = cursor.getColumnIndexOrThrow(COLUMN_MEMO)
            val subjectIndex = cursor.getColumnIndexOrThrow(COLUMN_SUBJECT)
            while (cursor.moveToNext()) {
                sessions += StudySession(
                    id = cursor.getLong(idIndex),
                    startedAtMillis = cursor.getLong(startedAtIndex),
                    durationMinutes = cursor.getInt(durationIndex),
                    focusScore = cursor.getInt(focusScoreIndex),
                    memo = cursor.getString(memoIndex),
                    subject = cursor.getString(subjectIndex)
                )
            }
        }
        return sessions
    }

    fun getSubjectStats(): List<SubjectStudyStat> {
        val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.KOREA)
        return getSessions()
            .groupBy { session ->
                formatter.format(session.startedAtMillis) to session.subject.ifBlank { DEFAULT_SUBJECT }
            }
            .map { (key, sessions) ->
                SubjectStudyStat(
                    dateLabel = key.first,
                    subject = key.second,
                    totalMinutes = sessions.sumOf { it.durationMinutes }
                )
            }
            .sortedWith(
                compareByDescending<SubjectStudyStat> { it.dateLabel }
                    .thenBy { it.subject }
            )
    }

    fun getMonthlyCalendarStats(year: Int, month: Int): List<CalendarDayStudyStat> {
        val monthPrefix = "%04d-%02d".format(year, month)
        val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.KOREA)
        return getSessions()
            .filter { formatter.format(it.startedAtMillis).startsWith(monthPrefix) }
            .groupBy { formatter.format(it.startedAtMillis) }
            .map { (dateLabel, sessions) ->
                CalendarDayStudyStat(
                    dateLabel = dateLabel,
                    totalMinutes = sessions.sumOf { it.durationMinutes }
                )
            }
    }

    fun getSubjectStatsForDate(dateLabel: String): List<SubjectStudyStat> {
        val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.KOREA)
        return getSessions()
            .filter { formatter.format(it.startedAtMillis) == dateLabel }
            .groupBy { it.subject.ifBlank { DEFAULT_SUBJECT } }
            .map { (subject, sessions) ->
                SubjectStudyStat(
                    dateLabel = dateLabel,
                    subject = subject,
                    totalMinutes = sessions.sumOf { it.durationMinutes }
                )
            }
            .sortedBy { it.subject }
    }

    fun addSubject(name: String) {
        val trimmedName = name.trim()
        if (trimmedName.isBlank()) return
        val values = ContentValues().apply {
            put(COLUMN_SUBJECT_ID, System.currentTimeMillis())
            put(COLUMN_SUBJECT_NAME, trimmedName)
        }
        writableDatabase.insertWithOnConflict(
            TABLE_STUDY_SUBJECTS,
            null,
            values,
            SQLiteDatabase.CONFLICT_IGNORE
        )
    }

    fun deleteSubject(name: String) {
        val trimmedName = name.trim()
        if (trimmedName.isBlank()) return
        writableDatabase.delete(
            TABLE_STUDY_SUBJECTS,
            "$COLUMN_SUBJECT_NAME = ?",
            arrayOf(trimmedName)
        )
    }

    fun getSubjects(): List<StudySubject> {
        val subjects = mutableListOf<StudySubject>()
        readableDatabase.query(
            TABLE_STUDY_SUBJECTS,
            null,
            null,
            null,
            null,
            null,
            "$COLUMN_SUBJECT_NAME ASC"
        ).use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(COLUMN_SUBJECT_ID)
            val nameIndex = cursor.getColumnIndexOrThrow(COLUMN_SUBJECT_NAME)
            while (cursor.moveToNext()) {
                subjects += StudySubject(
                    id = cursor.getLong(idIndex),
                    name = cursor.getString(nameIndex)
                )
            }
        }
        return subjects
    }

    fun getDailyStats(days: Int = 7, goalMinutes: Int = DEFAULT_DAILY_GOAL_MINUTES): List<DailyStudyStat> {
        val sessions = getSessions()
        val formatter = SimpleDateFormat("M/d", Locale.KOREA)
        val calendar = Calendar.getInstance(Locale.KOREA)
        val dayStarts = (days - 1 downTo 0).map { daysAgo ->
            calendar.timeInMillis = System.currentTimeMillis()
            calendar.add(Calendar.DAY_OF_YEAR, -daysAgo)
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            calendar.timeInMillis
        }

        return dayStarts.map { dayStart ->
            val dayEnd = dayStart + MILLIS_PER_DAY
            val sessionsForDay = sessions.filter { it.startedAtMillis in dayStart until dayEnd }
            val totalMinutes = sessionsForDay.sumOf { it.durationMinutes }
            val averageScore = if (sessionsForDay.isEmpty()) {
                0
            } else {
                sessionsForDay.sumOf { it.focusScore } / sessionsForDay.size
            }
            DailyStudyStat(
                label = formatter.format(dayStart),
                totalMinutes = totalMinutes,
                goalMinutes = goalMinutes,
                averageFocusScore = averageScore
            )
        }
    }

    fun getStats(): StudyStats {
        val sessions = getSessions()
        val totalMinutes = sessions.sumOf { it.durationMinutes }
        val averageScore = if (sessions.isEmpty()) {
            0
        } else {
            sessions.sumOf { it.focusScore } / sessions.size
        }
        return StudyStats(
            totalMinutes = totalMinutes,
            sessionCount = sessions.size,
            averageFocusScore = averageScore
        )
    }

    fun getLeaderboard(): List<Competitor> {
        val myStats = getStats()
        return listOf(
            Competitor("나", myStats.totalMinutes, myStats.averageFocusScore),
            Competitor("민준", 420, 86),
            Competitor("서연", 360, 91),
            Competitor("지우", 315, 79),
            Competitor("하준", 260, 83)
        ).sortedWith(compareByDescending<Competitor> { it.totalMinutes }.thenByDescending { it.focusScore })
    }

    companion object {
        private const val DATABASE_NAME = "study_sessions.db"
        private const val DATABASE_VERSION = 3
        private const val TABLE_STUDY_SESSIONS = "study_sessions"
        private const val TABLE_STUDY_SUBJECTS = "study_subjects"
        private const val COLUMN_ID = "id"
        private const val COLUMN_SUBJECT_ID = "subject_id"
        private const val COLUMN_SUBJECT_NAME = "name"
        private const val COLUMN_STARTED_AT = "started_at_millis"
        private const val COLUMN_DURATION_MINUTES = "duration_minutes"
        private const val COLUMN_FOCUS_SCORE = "focus_score"
        private const val COLUMN_MEMO = "memo"
        private const val COLUMN_SUBJECT = "subject"
        private const val DEFAULT_SUBJECT = "집중 타이머"
        private const val DEFAULT_DAILY_GOAL_MINUTES = 180
        private const val MILLIS_PER_DAY = 24 * 60 * 60 * 1000L

        @Volatile
        private var instance: StudyDatabase? = null

        fun getInstance(context: Context): StudyDatabase {
            return instance ?: synchronized(this) {
                instance ?: StudyDatabase(context).also { instance = it }
            }
        }
    }
}
