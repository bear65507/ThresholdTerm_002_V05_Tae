package com.example.thresholdterm_002.ui.stats

import android.os.Bundle
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.thresholdterm_002.IntentExtras
import com.example.thresholdterm_002.data.model.DailyStudyStat
import com.example.thresholdterm_002.databinding.ActivityStatsBinding
import com.example.thresholdterm_002.repository.StudyRepository

class StatsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityStatsBinding
    private val repository = StudyRepository.instance

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStatsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.title = "공부 시간 집계"

        bindProfileText()
        renderStats()
    }

    private fun bindProfileText() {
        val region = listOf(
            intent.getStringExtra(IntentExtras.EXTRA_SIDO).orEmpty(),
            intent.getStringExtra(IntentExtras.EXTRA_SIGUNGU).orEmpty(),
            intent.getStringExtra(IntentExtras.EXTRA_EUP_MYEON_DONG).orEmpty()
        ).filter { it.isNotBlank() }.joinToString(" ")
        val studentStatus = intent.getStringExtra(IntentExtras.EXTRA_STUDENT_STATUS).orEmpty()
        val sourceScreen = intent.getStringExtra(IntentExtras.EXTRA_SOURCE_SCREEN).orEmpty()
        binding.textStatsProfile.text = "전달받은 정보: $region / $studentStatus / 출발 화면: $sourceScreen"
    }

    private fun renderStats() {
        val stats = repository.getStats()
        binding.textStatsSummary.text =
            "총 ${stats.totalMinutes}분 공부 | ${stats.sessionCount}회 완료 | 평균 집중도 ${stats.averageFocusScore}점"
        renderDailyChart(repository.getDailyStats())
        binding.textStatsLeaderboard.text = repository.getLeaderboard()
            .mapIndexed { index, competitor ->
                "${index + 1}. ${competitor.name}  ${competitor.totalMinutes}분  집중도 ${competitor.focusScore}점"
            }
            .joinToString(separator = "\n")
    }

    private fun renderDailyChart(stats: List<DailyStudyStat>) {
        binding.statsDailyChartContainer.removeAllViews()
        stats.forEach { stat ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, 10, 0, 10)
            }
            val label = TextView(this).apply {
                text = "${stat.label}  ${stat.totalMinutes}/${stat.goalMinutes}분  집중도 ${stat.averageFocusScore}점"
                textSize = 14f
            }
            val progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
                max = stat.goalMinutes
                progress = stat.totalMinutes.coerceAtMost(stat.goalMinutes)
            }
            row.addView(label)
            row.addView(
                progress,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            )
            binding.statsDailyChartContainer.addView(row)
        }
    }
}
