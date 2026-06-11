package com.example.thresholdterm_002.ui.library

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.thresholdterm_002.IntentExtras
import com.example.thresholdterm_002.data.local.ProfileStore
import com.example.thresholdterm_002.data.local.UserProfile
import com.example.thresholdterm_002.data.model.LibraryPlace
import com.example.thresholdterm_002.data.remote.GoogleMapNavigator
import com.example.thresholdterm_002.databinding.ActivityLibraryBinding
import com.example.thresholdterm_002.repository.StudyRepository

class LibraryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLibraryBinding
    private val repository = StudyRepository.instance
    private val mapNavigator = GoogleMapNavigator()
    private var libraries: List<LibraryPlace> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLibraryBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.title = "도서관 검색"

        val profile = getProfileFromIntent()
        bindProfileText(profile)
        renderLibraries(profile)

        binding.buttonLibraryActivityOpenMap.setOnClickListener {
            val firstLibrary = libraries.firstOrNull()
            if (firstLibrary == null) {
                Toast.makeText(this, "도서관 목록이 없습니다.", Toast.LENGTH_SHORT).show()
            } else {
                mapNavigator.openPlace(this, firstLibrary)
            }
        }
    }

    private fun getProfileFromIntent(): UserProfile? {
        val sido = intent.getStringExtra(IntentExtras.EXTRA_SIDO).orEmpty()
        val sigungu = intent.getStringExtra(IntentExtras.EXTRA_SIGUNGU).orEmpty()
        val eupMyeonDong = intent.getStringExtra(IntentExtras.EXTRA_EUP_MYEON_DONG).orEmpty()
        val studentStatus = intent.getStringExtra(IntentExtras.EXTRA_STUDENT_STATUS).orEmpty()
        return if (sido.isBlank() || sigungu.isBlank() || eupMyeonDong.isBlank() || studentStatus.isBlank()) {
            ProfileStore(this).getProfile()
        } else {
            UserProfile(sido, sigungu, eupMyeonDong, studentStatus)
        }
    }

    private fun bindProfileText(profile: UserProfile?) {
        val sourceScreen = intent.getStringExtra(IntentExtras.EXTRA_SOURCE_SCREEN).orEmpty()
        binding.textLibraryActivityProfile.text = profile?.let {
            "전달받은 정보: ${it.regionLabel} / ${it.studentStatus} / 출발 화면: $sourceScreen"
        } ?: "전달받은 지역 정보가 없어 기본 추천을 표시합니다."
    }

    private fun renderLibraries(profile: UserProfile?) {
        binding.textLibraryActivityRegion.text = profile?.let {
            "${it.regionLabel} 주변 도서관을 보여주는 중입니다."
        } ?: "지역 설정이 없어 기본 추천 도서관을 보여줍니다."

        libraries = repository.getRecommendedLibraries(profile)
        binding.textLibraryActivityList.text = libraries.joinToString(separator = "\n\n") {
            "${it.name}\n${it.address}\n${it.openInfo}"
        }
    }
}
