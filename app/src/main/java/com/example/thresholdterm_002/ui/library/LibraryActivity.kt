package com.example.thresholdterm_002.ui.library

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Bundle
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.appcompat.app.AppCompatActivity
import com.example.thresholdterm_002.IntentExtras
import com.example.thresholdterm_002.data.local.ProfileStore
import com.example.thresholdterm_002.data.local.UserProfile
import com.example.thresholdterm_002.data.model.LibraryPlace
import com.example.thresholdterm_002.data.remote.KakaoMapNavigator
import com.example.thresholdterm_002.databinding.ActivityLibraryBinding
import com.example.thresholdterm_002.repository.StudyRepository

class LibraryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLibraryBinding
    private val repository = StudyRepository.instance
    private val mapNavigator = KakaoMapNavigator()
    private var libraries: List<LibraryPlace> = emptyList()
    private var latestSearchId = 0
    private var activeProfile: UserProfile? = null
    private var activeSearchMode = LibrarySearchMode.CURRENT_LOCATION

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLibraryBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.title = "도서관 검색"

        activeProfile = getProfileFromIntent()
        bindProfileText(activeProfile)
        bindSearchModeToggle()
        searchLibrariesByCurrentLocation()

        binding.buttonLibraryActivityOpenMap.setOnClickListener {
            val firstLibrary = libraries.firstOrNull()
            if (firstLibrary == null) {
                Toast.makeText(this, "도서관 목록이 없습니다.", Toast.LENGTH_SHORT).show()
            } else {
                mapNavigator.openPlace(this, firstLibrary)
            }
        }
    }

    private fun bindSearchModeToggle() {
        binding.toggleLibrarySearchMode.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            when (checkedId) {
                binding.buttonLibraryModeCurrentLocation.id -> searchLibrariesByCurrentLocation()
                binding.buttonLibraryModeProfileRegion.id -> searchLibrariesByProfileRegion()
            }
        }
    }

    private fun getProfileFromIntent(): UserProfile? {
        val sido = intent.getStringExtra(IntentExtras.EXTRA_SIDO).orEmpty()
        val sigungu = intent.getStringExtra(IntentExtras.EXTRA_SIGUNGU).orEmpty()
        val eupMyeonDong = intent.getStringExtra(IntentExtras.EXTRA_EUP_MYEON_DONG).orEmpty()
        val studentStatus = intent.getStringExtra(IntentExtras.EXTRA_STUDENT_STATUS).orEmpty()
        return if (sido.isBlank() || sigungu.isBlank() || studentStatus.isBlank()) {
            ProfileStore(this).getProfile()
        } else {
            UserProfile(sido, sigungu, eupMyeonDong, studentStatus)
        }
    }

    private fun bindProfileText(profile: UserProfile?) {
        val sourceScreen = intent.getStringExtra(IntentExtras.EXTRA_SOURCE_SCREEN).orEmpty()
        binding.textLibraryActivityProfile.text = profile?.let {
            "전달받은 정보: ${it.regionLabel} / ${it.studentStatus} / 출발 화면: $sourceScreen"
        } ?: "전달받은 지역 정보가 없어 카카오 지도 API에서 도서관을 검색합니다."
    }

    private fun searchLibrariesByCurrentLocation() {
        activeSearchMode = LibrarySearchMode.CURRENT_LOCATION
        if (hasLocationPermission()) {
            val location = findLastKnownLocation()
            if (location == null) {
                libraries = emptyList()
                binding.textLibraryActivityRegion.text = "현위치 기준 검색을 선택했습니다."
                binding.textLibraryActivityList.text = "현재 위치를 가져오지 못했습니다. 기기의 위치 기능을 켠 뒤 다시 시도해 주세요."
            } else {
                renderLibraries(mode = LibrarySearchMode.CURRENT_LOCATION, location = location)
            }
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ),
                REQUEST_LOCATION_PERMISSION
            )
        }
    }

    private fun searchLibrariesByProfileRegion() {
        activeSearchMode = LibrarySearchMode.PROFILE_REGION
        renderLibraries(mode = LibrarySearchMode.PROFILE_REGION, location = null)
    }

    private fun renderLibraries(mode: LibrarySearchMode, location: Location?) {
        binding.textLibraryActivityRegion.text = when {
            mode == LibrarySearchMode.CURRENT_LOCATION -> "카카오 지도 API에서 현재 위치 기준 가까운 도서관을 검색 중입니다."
            activeProfile != null -> "카카오 지도 API에서 ${activeProfile?.regionLabel} 지역 도서관을 검색 중입니다."
            else -> "설정 지역 정보가 없어 카카오 지도 API에서 도서관을 검색 중입니다."
        }
        binding.textLibraryActivityList.text = "검색 중..."

        val searchId = ++latestSearchId
        Thread {
            val result = when (mode) {
                LibrarySearchMode.CURRENT_LOCATION -> {
                    if (location == null) {
                        emptyList()
                    } else {
                        repository.getLibrariesByCurrentLocation(location.latitude, location.longitude)
                    }
                }
                LibrarySearchMode.PROFILE_REGION -> repository.getLibrariesByProfileRegion(activeProfile)
            }
            val errorMessage = repository.getLibrarySearchError()
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                if (searchId != latestSearchId) return@runOnUiThread
                libraries = result
                binding.textLibraryActivityRegion.text = when {
                    mode == LibrarySearchMode.CURRENT_LOCATION -> "카카오 지도 API 기준 현재 위치에서 가까운 도서관입니다."
                    activeProfile != null -> "카카오 지도 API 기준 ${activeProfile?.regionLabel} 지역 도서관입니다."
                    else -> "카카오 지도 API 기준 도서관 검색 결과입니다."
                }
                binding.textLibraryActivityList.text = if (result.isEmpty()) {
                    errorMessage ?: "검색 결과가 없습니다. 위치 권한, 네트워크 연결, 카카오 REST API 키를 확인해 주세요."
                } else {
                    result.joinToString(separator = "\n\n") {
                        val distanceText = it.distanceKm
                            ?.let { distance -> "\n현재 위치에서 약 %.1fkm".format(distance) }
                            .orEmpty()
                        "${it.name}\n${it.address}\n${it.openInfo}$distanceText"
                    }
                }
            }
        }.start()
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
    }

    private fun findLastKnownLocation(): Location? {
        val locationManager = getSystemService(LocationManager::class.java)
        return listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .mapNotNull { provider ->
                runCatching {
                    if (locationManager.isProviderEnabled(provider)) {
                        locationManager.getLastKnownLocation(provider)
                    } else {
                        null
                    }
                }.getOrNull()
            }
            .maxByOrNull { it.time }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_LOCATION_PERMISSION) {
            if (grantResults.any { it == PackageManager.PERMISSION_GRANTED }) {
                searchLibrariesByCurrentLocation()
            } else {
                libraries = emptyList()
                binding.textLibraryActivityRegion.text = "현위치 기준 검색을 선택했습니다."
                binding.textLibraryActivityList.text = "위치 권한이 없어 현위치 기준 도서관 검색을 할 수 없습니다. 설정 지역 기준 모드를 선택해 주세요."
            }
        }
    }

    companion object {
        private const val REQUEST_LOCATION_PERMISSION = 2001
    }
}

private enum class LibrarySearchMode {
    CURRENT_LOCATION,
    PROFILE_REGION
}
