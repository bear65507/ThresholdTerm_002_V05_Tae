package com.example.thresholdterm_002.data.remote

import com.example.thresholdterm_002.BuildConfig
import com.example.thresholdterm_002.data.local.UserProfile
import com.example.thresholdterm_002.data.model.LibraryPlace

interface LibraryApiClient {
    fun getRecommendedLibraries(
        profile: UserProfile? = null,
        latitude: Double? = null,
        longitude: Double? = null
    ): List<LibraryPlace>
}

class NationalLibraryApiClient : LibraryApiClient {

    override fun getRecommendedLibraries(
        profile: UserProfile?,
        latitude: Double?,
        longitude: Double?
    ): List<LibraryPlace> {
        val selectedRegionLibraries = profile?.let { userProfile ->
            sampleLibraries.filter { library ->
                library.address.contains(userProfile.sigungu) || library.address.contains(userProfile.sido)
            }
        }.orEmpty()

        return selectedRegionLibraries.ifEmpty { sampleLibraries.take(3) }
    }

    companion object {
        const val NATIONAL_LIBRARY_API_NAME = "도서관 정보나루 API"
        const val MAP_API_NAME = "카카오맵 API"
        val NATIONAL_LIBRARY_API_KEY: String = BuildConfig.LIBRARY_API_KEY

        private val sampleLibraries = listOf(
            LibraryPlace(
                name = "서울도서관",
                address = "서울특별시 중구 세종대로 110",
                openInfo = "평일 09:00-21:00 / 주말 09:00-18:00",
                latitude = 37.5663,
                longitude = 126.9779
            ),
            LibraryPlace(
                name = "국립중앙도서관",
                address = "서울특별시 서초구 반포대로 201",
                openInfo = "월-토 09:00-18:00",
                latitude = 37.4976,
                longitude = 127.0035
            ),
            LibraryPlace(
                name = "정독도서관",
                address = "서울특별시 종로구 북촌로5길 48",
                openInfo = "평일 09:00-20:00 / 주말 09:00-17:00",
                latitude = 37.5811,
                longitude = 126.9836
            ),
            LibraryPlace(
                name = "부산광역시립시민도서관",
                address = "부산광역시 부산진구 월드컵대로 462",
                openInfo = "화-일 09:00-18:00 / 월요일 휴관",
                latitude = 35.1842,
                longitude = 129.0503
            ),
            LibraryPlace(
                name = "대전광역시 한밭도서관",
                address = "대전광역시 중구 서문로 10",
                openInfo = "평일 09:00-22:00 / 주말 09:00-18:00",
                latitude = 36.3085,
                longitude = 127.4097
            ),
            LibraryPlace(
                name = "수원시 중앙도서관",
                address = "경기도 수원시 팔달구 팔달산로 318",
                openInfo = "화-일 09:00-18:00 / 월요일 휴관",
                latitude = 37.2789,
                longitude = 127.0142
            ),
            LibraryPlace(
                name = "제주특별자치도 한라도서관",
                address = "제주특별자치도 제주시 오남로 221",
                openInfo = "화-일 09:00-18:00 / 월요일 휴관",
                latitude = 33.4813,
                longitude = 126.5308
            )
        )
    }
}
