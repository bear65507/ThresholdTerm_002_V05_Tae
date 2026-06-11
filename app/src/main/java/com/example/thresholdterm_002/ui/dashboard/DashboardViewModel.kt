package com.example.thresholdterm_002.ui.dashboard

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.thresholdterm_002.data.model.DailyStudyStat
import com.example.thresholdterm_002.repository.StudyRepository

class DashboardViewModel : ViewModel() {

    private val repository = StudyRepository.instance

    private val _summaryText = MutableLiveData<String>()
    val summaryText: LiveData<String> = _summaryText

    private val _leaderboardText = MutableLiveData<String>()
    val leaderboardText: LiveData<String> = _leaderboardText

    private val _dailyStats = MutableLiveData<List<DailyStudyStat>>()
    val dailyStats: LiveData<List<DailyStudyStat>> = _dailyStats

    fun refresh() {
        val stats = repository.getStats()
        _summaryText.value = "총 ${stats.totalMinutes}분 공부 | ${stats.sessionCount}회 완료 | 평균 집중도 ${stats.averageFocusScore}점"
        _dailyStats.value = repository.getDailyStats()
        _leaderboardText.value = repository.getLeaderboard()
            .mapIndexed { index, competitor ->
                "${index + 1}. ${competitor.name}  ${competitor.totalMinutes}분  집중도 ${competitor.focusScore}점"
            }
            .joinToString(separator = "\n")
    }
}
