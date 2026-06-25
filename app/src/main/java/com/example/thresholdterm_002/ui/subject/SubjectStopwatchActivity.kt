package com.example.thresholdterm_002.ui.subject

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.thresholdterm_002.databinding.ActivitySubjectStopwatchBinding
import com.example.thresholdterm_002.repository.StudyRepository
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class SubjectStopwatchActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySubjectStopwatchBinding
    private val repository = StudyRepository.instance
    private val preferences by lazy { getSharedPreferences(PREFERENCES_NAME, MODE_PRIVATE) }
    private val handler = Handler(Looper.getMainLooper())
    private val subjectStates = linkedMapOf<String, SubjectStopwatchState>()
    private var activeStudyDayLabel = ""

    private val ticker = object : Runnable {
        override fun run() {
            resetIfStudyDayChanged()
            val now = System.currentTimeMillis()
            subjectStates.values.forEach { state ->
                if (state.running) {
                    state.elapsedMillis += now - state.lastTickMillis
                    state.lastTickMillis = now
                    persistSubjectState(state)
                    autoSaveMinuteDelta(state)
                }
            }
            renderRunningTimes()
            handler.postDelayed(this, TICK_INTERVAL_MILLIS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySubjectStopwatchBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.title = "과목별 공부 기록"

        activeStudyDayLabel = currentStudyDayLabel()
        binding.textSubjectStopwatchDate.text = "공부일: $activeStudyDayLabel (새벽 5시 초기화)"
        binding.buttonAddSubject.setOnClickListener {
            addSubject()
        }
        renderSubjectRows()
        renderRecords()
        handler.post(ticker)
    }

    private fun renderSubjectRows() {
        val savedSubjects = repository.getSubjects().map { it.name }
        val previousStates = subjectStates.toMap()
        subjectStates.clear()
        binding.containerSubjectStopwatchRows.removeAllViews()

        savedSubjects.forEach { subject ->
            val state = previousStates[subject] ?: restoreSubjectState(subject)
            subjectStates[subject] = state
            binding.containerSubjectStopwatchRows.addView(createSubjectRow(state))
        }

        if (savedSubjects.isEmpty()) {
            binding.textSubjectStopwatchStatus.text = "저장된 과목이 없습니다. 새 과목을 추가해 주세요."
        }
        renderRunningTimes()
    }

    private fun createSubjectRow(state: SubjectStopwatchState): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 16, 0, 16)
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val subjectText = TextView(this).apply {
            text = state.subject
            textSize = 18f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        val timeText = TextView(this).apply {
            text = formatTime(state.elapsedMillis)
            textSize = 20f
            gravity = Gravity.END
        }
        state.timeText = timeText
        header.addView(
            subjectText,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )
        header.addView(
            timeText,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 10, 0, 0)
        }
        controls.addView(createButton("시작") { startSubject(state) }, buttonParams())
        controls.addView(createButton("정지") { pauseSubject(state) }, buttonParams())
        controls.addView(createButton("삭제") { deleteSubject(state) }, buttonParams())

        row.addView(header)
        row.addView(controls)
        return row
    }

    private fun createButton(label: String, onClick: () -> Unit): Button {
        return Button(this).apply {
            text = label
            setOnClickListener { onClick() }
        }
    }

    private fun buttonParams(): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginEnd = 6
        }
    }

    private fun startSubject(state: SubjectStopwatchState) {
        if (state.running) return
        state.running = true
        state.lastTickMillis = System.currentTimeMillis()
        persistSubjectState(state)
        binding.textSubjectStopwatchStatus.text = "${state.subject} 공부 시간을 기록 중입니다."
    }

    private fun pauseSubject(state: SubjectStopwatchState) {
        if (state.running) {
            val now = System.currentTimeMillis()
            state.elapsedMillis += now - state.lastTickMillis
        }
        state.running = false
        persistSubjectState(state)
        renderRunningTimes()
        binding.textSubjectStopwatchStatus.text = "${state.subject} 스톱워치를 정지했습니다."
    }

    private fun deleteSubject(state: SubjectStopwatchState) {
        if (state.running || state.elapsedMillis > 0L) {
            Toast.makeText(this, "오늘 진행 중인 시간이 있는 과목은 삭제할 수 없습니다.", Toast.LENGTH_SHORT).show()
            return
        }
        repository.deleteSubject(state.subject)
        clearSubjectState(state.subject)
        binding.textSubjectStopwatchStatus.text = "${state.subject} 과목을 목록에서 삭제했습니다. 기존 기록은 유지됩니다."
        renderSubjectRows()
    }

    private fun addSubject() {
        val subject = binding.inputNewSubjectName.text?.toString()?.trim().orEmpty()
        if (subject.isBlank()) {
            Toast.makeText(this, "추가할 과목명을 입력해주세요.", Toast.LENGTH_SHORT).show()
            return
        }
        repository.addSubject(subject)
        binding.inputNewSubjectName.setText("")
        binding.textSubjectStopwatchStatus.text = "$subject 과목을 목록에 추가했습니다."
        renderSubjectRows()
    }

    private fun renderRunningTimes() {
        var totalMillis = 0L
        subjectStates.values.forEach { state ->
            state.timeText?.text = formatTime(state.elapsedMillis)
            totalMillis += state.elapsedMillis
        }
        binding.textSubjectTotalTime.text = formatTime(totalMillis)
    }

    private fun autoSaveMinuteDelta(state: SubjectStopwatchState) {
        val currentMinutes = (state.elapsedMillis / MILLIS_PER_MINUTE).toInt()
        val deltaMinutes = currentMinutes - state.persistedMinutes
        if (deltaMinutes <= 0) return

        repository.saveSubjectStudySession(
            subject = state.subject,
            durationMinutes = deltaMinutes,
            memo = "과목별 스톱워치 자동 저장",
            startedAtMillis = currentStudyDayTimestamp()
        )
        state.persistedMinutes = currentMinutes
        persistSubjectState(state)
        binding.textSubjectStopwatchStatus.text = "${state.subject} ${deltaMinutes}분 자동 저장됨"
        renderRecords()
    }

    private fun resetIfStudyDayChanged() {
        val currentDayLabel = currentStudyDayLabel()
        if (currentDayLabel == activeStudyDayLabel) return

        activeStudyDayLabel = currentDayLabel
        binding.textSubjectStopwatchDate.text = "공부일: $activeStudyDayLabel (새벽 5시 초기화)"
        subjectStates.values.forEach { state ->
            state.elapsedMillis = 0L
            state.persistedMinutes = 0
            state.running = false
            state.lastTickMillis = 0L
            persistSubjectState(state)
        }
        binding.textSubjectStopwatchStatus.text = "새벽 5시가 지나 과목별 스톱워치가 자동 초기화되었습니다."
        renderRunningTimes()
    }

    private fun restoreSubjectState(subject: String): SubjectStopwatchState {
        val savedDayLabel = preferences.getString(keyFor(subject, KEY_STUDY_DAY), "").orEmpty()
        if (savedDayLabel != activeStudyDayLabel) {
            return SubjectStopwatchState(subject = subject)
        }
        return SubjectStopwatchState(
            subject = subject,
            elapsedMillis = preferences.getLong(keyFor(subject, KEY_ELAPSED_MILLIS), 0L),
            persistedMinutes = preferences.getInt(keyFor(subject, KEY_PERSISTED_MINUTES), 0)
        )
    }

    private fun persistSubjectState(state: SubjectStopwatchState) {
        preferences.edit()
            .putString(keyFor(state.subject, KEY_STUDY_DAY), activeStudyDayLabel)
            .putLong(keyFor(state.subject, KEY_ELAPSED_MILLIS), state.elapsedMillis)
            .putInt(keyFor(state.subject, KEY_PERSISTED_MINUTES), state.persistedMinutes)
            .apply()
    }

    private fun clearSubjectState(subject: String) {
        preferences.edit()
            .remove(keyFor(subject, KEY_STUDY_DAY))
            .remove(keyFor(subject, KEY_ELAPSED_MILLIS))
            .remove(keyFor(subject, KEY_PERSISTED_MINUTES))
            .apply()
    }

    private fun renderRecords() {
        val records = repository.getSubjectStats()
        binding.textSubjectStopwatchRecords.text = if (records.isEmpty()) {
            "아직 저장된 과목별 기록이 없습니다."
        } else {
            records.joinToString(separator = "\n") {
                "${it.dateLabel}  ${it.subject}: ${it.totalMinutes}분"
            }
        }
    }

    private fun formatTime(millis: Long): String {
        val totalSeconds = millis / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return "%02d:%02d:%02d".format(hours, minutes, seconds)
    }

    private fun currentStudyDayLabel(): String {
        return DATE_FORMAT.format(currentStudyDayCalendar().time)
    }

    private fun currentStudyDayTimestamp(): Long {
        val calendar = currentStudyDayCalendar()
        calendar.set(Calendar.HOUR_OF_DAY, 12)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }

    private fun currentStudyDayCalendar(): Calendar {
        return Calendar.getInstance(Locale.KOREA).apply {
            if (get(Calendar.HOUR_OF_DAY) < RESET_HOUR) {
                add(Calendar.DAY_OF_YEAR, -1)
            }
        }
    }

    private fun keyFor(subject: String, suffix: String): String {
        return "${subject.hashCode()}_$suffix"
    }

    override fun onDestroy() {
        handler.removeCallbacks(ticker)
        super.onDestroy()
    }

    companion object {
        private const val TICK_INTERVAL_MILLIS = 500L
        private const val MILLIS_PER_MINUTE = 60_000L
        private const val RESET_HOUR = 5
        private const val PREFERENCES_NAME = "subject_stopwatch_state"
        private const val KEY_STUDY_DAY = "study_day"
        private const val KEY_ELAPSED_MILLIS = "elapsed_millis"
        private const val KEY_PERSISTED_MINUTES = "persisted_minutes"
        private val DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd", Locale.KOREA)
    }
}

private data class SubjectStopwatchState(
    val subject: String,
    var elapsedMillis: Long = 0L,
    var persistedMinutes: Int = 0,
    var running: Boolean = false,
    var lastTickMillis: Long = 0L,
    var timeText: TextView? = null
)
