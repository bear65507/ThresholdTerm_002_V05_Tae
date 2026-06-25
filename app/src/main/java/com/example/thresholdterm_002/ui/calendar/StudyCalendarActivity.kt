package com.example.thresholdterm_002.ui.calendar

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.GridLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.thresholdterm_002.databinding.ActivityStudyCalendarBinding
import com.example.thresholdterm_002.repository.StudyRepository
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class StudyCalendarActivity : AppCompatActivity() {

    private lateinit var binding: ActivityStudyCalendarBinding
    private val repository = StudyRepository.instance
    private val visibleMonth = Calendar.getInstance(Locale.KOREA)
    private val monthTitleFormat = SimpleDateFormat("yyyy년 M월", Locale.KOREA)
    private val dateLabelFormat = SimpleDateFormat("yyyy-MM-dd", Locale.KOREA)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStudyCalendarBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.title = "공부 달력"

        visibleMonth.set(Calendar.DAY_OF_MONTH, 1)
        bindButtons()
        renderWeekdays()
        renderCalendar()
    }

    private fun bindButtons() {
        binding.buttonCalendarPrevious.setOnClickListener {
            visibleMonth.add(Calendar.MONTH, -1)
            renderCalendar()
        }
        binding.buttonCalendarToday.setOnClickListener {
            visibleMonth.timeInMillis = System.currentTimeMillis()
            visibleMonth.set(Calendar.DAY_OF_MONTH, 1)
            renderCalendar()
        }
        binding.buttonCalendarNext.setOnClickListener {
            visibleMonth.add(Calendar.MONTH, 1)
            renderCalendar()
        }
    }

    private fun renderWeekdays() {
        binding.gridCalendarWeekdays.removeAllViews()
        listOf("일", "월", "화", "수", "목", "금", "토").forEach { label ->
            binding.gridCalendarWeekdays.addView(
                TextView(this).apply {
                    text = label
                    gravity = Gravity.CENTER
                    textSize = 14f
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                },
                cellParams()
            )
        }
    }

    private fun renderCalendar() {
        binding.textCalendarMonth.text = monthTitleFormat.format(visibleMonth.time)
        binding.gridCalendarDays.removeAllViews()

        val year = visibleMonth.get(Calendar.YEAR)
        val month = visibleMonth.get(Calendar.MONTH) + 1
        val statByDate = repository.getMonthlyCalendarStats(year, month)
            .associateBy { it.dateLabel }

        val firstDay = visibleMonth.clone() as Calendar
        val firstDayOffset = firstDay.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY
        repeat(firstDayOffset) {
            binding.gridCalendarDays.addView(TextView(this), cellParams())
        }

        val lastDay = visibleMonth.getActualMaximum(Calendar.DAY_OF_MONTH)
        for (day in 1..lastDay) {
            val date = visibleMonth.clone() as Calendar
            date.set(Calendar.DAY_OF_MONTH, day)
            val dateLabel = dateLabelFormat.format(date.time)
            val totalMinutes = statByDate[dateLabel]?.totalMinutes ?: 0
            binding.gridCalendarDays.addView(
                createDayCell(day, totalMinutes, dateLabel),
                cellParams()
            )
        }
    }

    private fun createDayCell(day: Int, totalMinutes: Int, dateLabel: String): TextView {
        return TextView(this).apply {
            text = if (totalMinutes > 0) {
                "${day}일\n${totalMinutes}분"
            } else {
                "${day}일\n-"
            }
            gravity = Gravity.CENTER
            textSize = 13f
            minHeight = 86
            setPadding(4, 8, 4, 8)
            setBackgroundColor(if (totalMinutes > 0) Color.rgb(232, 246, 241) else Color.rgb(248, 248, 248))
            setOnClickListener {
                renderDateDetail(dateLabel, totalMinutes)
            }
        }
    }

    private fun renderDateDetail(dateLabel: String, totalMinutes: Int) {
        binding.textCalendarSelectedSummary.text = "$dateLabel 총 공부시간: ${totalMinutes}분"
        val subjectStats = repository.getSubjectStatsForDate(dateLabel)
        binding.textCalendarSubjectDetail.text = if (subjectStats.isEmpty()) {
            "이 날짜에는 저장된 공부 기록이 없습니다."
        } else {
            subjectStats.joinToString(separator = "\n") {
                "${it.subject}: ${it.totalMinutes}분"
            }
        }
    }

    private fun cellParams(): GridLayout.LayoutParams {
        return GridLayout.LayoutParams().apply {
            width = 0
            height = GridLayout.LayoutParams.WRAP_CONTENT
            columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
            setMargins(3, 3, 3, 3)
        }
    }
}
