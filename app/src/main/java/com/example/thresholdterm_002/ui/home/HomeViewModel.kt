package com.example.thresholdterm_002.ui.home

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.thresholdterm_002.ml.FocusSignal
import com.example.thresholdterm_002.repository.StudyRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class FocusSessionResult(
    val durationMinutes: Int,
    val focusScore: Int
)

class HomeViewModel : ViewModel() {

    private val repository = StudyRepository.instance
    private var timerJob: Job? = null
    private var focusCheckJob: Job? = null
    private var selectedMinutes = 25
    private var remainingMillis = selectedMinutes * MILLIS_PER_MINUTE
    private var running = false
    private var focusScoreSum = 0
    private var focusCheckCount = 0

    private val _timerText = MutableLiveData(formatTime(remainingMillis))
    val timerText: LiveData<String> = _timerText

    private val _statusText = MutableLiveData("공부 시간을 정하고 집중 모드를 시작하세요.")
    val statusText: LiveData<String> = _statusText

    private val _focusInsightText = MutableLiveData("타이머를 켜면 시선과 자세 분석이 함께 시작됩니다.")
    val focusInsightText: LiveData<String> = _focusInsightText

    private val _selectedMinutesText = MutableLiveData("${selectedMinutes}분 집중")
    val selectedMinutesText: LiveData<String> = _selectedMinutesText

    private val _completedSession = MutableLiveData<FocusSessionResult>()
    val completedSession: LiveData<FocusSessionResult> = _completedSession

    fun selectDuration(minutes: Int) {
        if (running) return
        selectedMinutes = minutes
        remainingMillis = minutes * MILLIS_PER_MINUTE
        _timerText.value = formatTime(remainingMillis)
        _selectedMinutesText.value = "${selectedMinutes}분 집중"
        _statusText.value = "집중 시간을 ${minutes}분으로 설정했습니다."
    }

    fun startFocusSession(minutes: Int = selectedMinutes) {
        if (running) return
        selectedMinutes = minutes
        remainingMillis = if (remainingMillis <= 0L) minutes * MILLIS_PER_MINUTE else remainingMillis
        running = true
        focusScoreSum = 0
        focusCheckCount = 0
        _statusText.value = "집중 중입니다. 다른 앱은 잠시 멀리 두세요."
        _focusInsightText.value = "AI 집중 감지 작동 중: 얼굴 방향과 어깨 균형을 확인합니다."

        focusCheckJob = viewModelScope.launch {
            while (isActive) {
                val feedback = repository.checkFocus(createSampleFocusSignal())
                focusScoreSum += feedback.score
                focusCheckCount += 1
                _focusInsightText.value = "${feedback.title} (${feedback.score}점)\n${feedback.message}"
                delay(FOCUS_CHECK_INTERVAL_MILLIS)
            }
        }

        timerJob = viewModelScope.launch {
            while (remainingMillis > 0L && isActive) {
                delay(1000L)
                remainingMillis = (remainingMillis - 1000L).coerceAtLeast(0L)
                _timerText.value = formatTime(remainingMillis)
            }

            completeFocusSession()
        }
    }

    fun pauseFocusSession() {
        timerJob?.cancel()
        focusCheckJob?.cancel()
        running = false
        _statusText.value = "일시정지되었습니다. 준비되면 다시 시작하세요."
        _focusInsightText.value = "AI 집중 감지가 잠시 멈췄습니다."
    }

    fun resetFocusSession(minutes: Int = 25) {
        timerJob?.cancel()
        focusCheckJob?.cancel()
        running = false
        selectedMinutes = minutes
        remainingMillis = minutes * MILLIS_PER_MINUTE
        _timerText.value = formatTime(remainingMillis)
        _selectedMinutesText.value = "${selectedMinutes}분 집중"
        _statusText.value = "타이머가 초기화되었습니다."
        _focusInsightText.value = "타이머를 켜면 시선과 자세 분석이 함께 시작됩니다."
    }

    override fun onCleared() {
        timerJob?.cancel()
        focusCheckJob?.cancel()
        super.onCleared()
    }

    private fun completeFocusSession() {
        focusCheckJob?.cancel()
        running = false
        remainingMillis = 0L
        val averageFocusScore = if (focusCheckCount == 0) 90 else focusScoreSum / focusCheckCount
        _timerText.value = "00:00"
        repository.saveStudySession(
            durationMinutes = selectedMinutes,
            focusScore = averageFocusScore,
            memo = "타이머 완료"
        )
        _statusText.value = "세션 완료! 공부 시간이 저장되었습니다."
        _focusInsightText.value = "평균 집중도 ${averageFocusScore}점으로 기록되었습니다."
        _completedSession.value = FocusSessionResult(selectedMinutes, averageFocusScore)
    }

    private fun createSampleFocusSignal(): FocusSignal {
        val totalMillis = (selectedMinutes * MILLIS_PER_MINUTE).toFloat()
        val progressRatio = 1f - (remainingMillis.toFloat() / totalMillis)
        return FocusSignal(
            faceVisible = true,
            phoneDetected = false,
            headDownRatio = (0.12f + progressRatio * 0.08f).coerceIn(0f, 1f),
            shoulderTiltRatio = (0.05f + progressRatio * 0.04f).coerceIn(0f, 1f)
        )
    }

    private fun formatTime(millis: Long): String {
        val totalSeconds = millis / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return "%02d:%02d".format(minutes, seconds)
    }

    companion object {
        private const val MILLIS_PER_MINUTE = 60_000L
        private const val FOCUS_CHECK_INTERVAL_MILLIS = 5_000L
    }
}
