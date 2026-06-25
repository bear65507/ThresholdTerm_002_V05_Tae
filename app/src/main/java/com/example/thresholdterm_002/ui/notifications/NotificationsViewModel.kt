package com.example.thresholdterm_002.ui.notifications

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.thresholdterm_002.data.local.UserProfile
import com.example.thresholdterm_002.data.model.LibraryPlace
import com.example.thresholdterm_002.ml.FocusSignal
import com.example.thresholdterm_002.repository.StudyRepository

class NotificationsViewModel : ViewModel() {

    private val repository = StudyRepository.instance

    private val _libraries = MutableLiveData<List<LibraryPlace>>(emptyList())
    val libraries: LiveData<List<LibraryPlace>> = _libraries

    private val _focusFeedback = MutableLiveData("자세 감지를 시작하면 집중 점수가 표시됩니다.")
    val focusFeedback: LiveData<String> = _focusFeedback

    private val _regionText = MutableLiveData("저장된 지역 정보를 불러오고 있습니다.")
    val regionText: LiveData<String> = _regionText
    private var latestSearchId = 0

    fun loadLibraries(profile: UserProfile?) {
        _regionText.value = profile?.let {
            "${it.regionLabel} 기준 카카오 지도 API에서 도서관을 검색 중입니다."
        } ?: "지역 설정이 없어 카카오 지도 API에서 도서관을 검색 중입니다."

        val searchId = ++latestSearchId
        Thread {
            val result = repository.getRecommendedLibraries(profile)
            val errorMessage = repository.getLibrarySearchError()
            if (searchId == latestSearchId) {
                _libraries.postValue(result)
                _regionText.postValue(
                    if (result.isEmpty()) {
                        errorMessage ?: "검색 결과가 없습니다. 네트워크 연결과 카카오 REST API 키를 확인해 주세요."
                    } else {
                        profile?.let {
                            "${it.regionLabel} 기준 카카오 지도 API 도서관 검색 결과입니다."
                        } ?: "카카오 지도 API 도서관 검색 결과입니다."
                    }
                )
            }
        }.start()
    }

    fun runSampleFocusCheck() {
        val feedback = repository.checkFocus(
            FocusSignal(
                faceVisible = true,
                phoneDetected = false,
                headDownRatio = 0.18f,
                shoulderTiltRatio = 0.08f
            )
        )
        _focusFeedback.value = "${feedback.title} (${feedback.score}점)\n${feedback.message}"
    }
}
