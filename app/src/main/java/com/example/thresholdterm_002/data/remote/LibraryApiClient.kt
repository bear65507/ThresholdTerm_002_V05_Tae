package com.example.thresholdterm_002.data.remote

import com.example.thresholdterm_002.BuildConfig
import com.example.thresholdterm_002.data.local.UserProfile
import com.example.thresholdterm_002.data.model.LibraryPlace
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

interface LibraryApiClient {
    fun getRecommendedLibraries(
        profile: UserProfile? = null,
        latitude: Double? = null,
        longitude: Double? = null
    ): List<LibraryPlace>

    fun getLibrariesByCurrentLocation(
        latitude: Double,
        longitude: Double
    ): List<LibraryPlace>

    fun getLibrariesByProfileRegion(
        profile: UserProfile?
    ): List<LibraryPlace>
}

class NationalLibraryApiClient : LibraryApiClient {

    override fun getRecommendedLibraries(
        profile: UserProfile?,
        latitude: Double?,
        longitude: Double?
    ): List<LibraryPlace> {
        return if (latitude != null && longitude != null) {
            getLibrariesByCurrentLocation(latitude, longitude)
        } else {
            getLibrariesByProfileRegion(profile)
        }
    }

    override fun getLibrariesByCurrentLocation(
        latitude: Double,
        longitude: Double
    ): List<LibraryPlace> {
        lastErrorMessage = null
        listOf("도서관", "공공도서관").forEach { query ->
            val nearbyLibraries = requestKakaoKeywordSearch(query, latitude, longitude)
            if (nearbyLibraries.isNotEmpty()) return nearbyLibraries
        }
        if (lastErrorMessage == null) {
            lastErrorMessage = "카카오 API 응답은 정상이나 현재 위치 주변 검색 결과가 비어 있습니다."
        }
        return emptyList()
    }

    override fun getLibrariesByProfileRegion(profile: UserProfile?): List<LibraryPlace> {
        lastErrorMessage = null
        val regionQuery = profile?.let { "${it.sido} ${it.sigungu} 도서관" } ?: "도서관"
        val fallbackQueries = profile?.let {
            listOf(
                "${it.sido} ${it.sigungu} 도서관",
                "${it.sido} ${it.sigungu} 공공도서관",
                "${it.sigungu} 도서관",
                "${it.sigungu} 공공도서관"
            ).distinct()
        } ?: listOf("도서관", "공공도서관")

        fallbackQueries.forEach { query ->
            val regionLibraries = requestKakaoKeywordSearch(query)
            if (regionLibraries.isNotEmpty()) return regionLibraries
        }
        if (lastErrorMessage == null) {
            lastErrorMessage = "카카오 API 응답은 정상이나 검색 결과가 비어 있습니다. 검색어: $regionQuery"
        }
        return emptyList()
    }

    companion object {
        const val NATIONAL_LIBRARY_API_NAME = "도서관 정보나루 API"
        const val MAP_API_NAME = "카카오맵 API"
        private const val KAKAO_KEYWORD_SEARCH_URL = "https://dapi.kakao.com/v2/local/search/keyword.json"
        val NATIONAL_LIBRARY_API_KEY: String = BuildConfig.LIBRARY_API_KEY
        private var lastErrorMessage: String? = null

        private fun requestKakaoKeywordSearch(
            query: String,
            latitude: Double? = null,
            longitude: Double? = null
        ): List<LibraryPlace> {
            if (BuildConfig.KAKAO_API_KEY.isBlank()) {
                lastErrorMessage = "카카오 REST API 키가 BuildConfig에 비어 있습니다."
                return emptyList()
            }

            val queryParameters = mutableListOf(
                "query=${query.encodeUrl()}",
                "size=10"
            )
            if (latitude != null && longitude != null) {
                queryParameters += "x=$longitude"
                queryParameters += "y=$latitude"
                queryParameters += "radius=20000"
                queryParameters += "sort=distance"
            }

            val connection = (URL("$KAKAO_KEYWORD_SEARCH_URL?${queryParameters.joinToString("&")}")
                .openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 5000
                readTimeout = 5000
                setRequestProperty("Authorization", "KakaoAK ${BuildConfig.KAKAO_API_KEY}")
            }

            return runCatching {
                val responseCode = connection.responseCode
                val responseText = if (responseCode in 200..299) {
                    connection.inputStream.bufferedReader().use { it.readText() }
                } else {
                    connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                }
                if (responseCode !in 200..299) {
                    lastErrorMessage = buildKakaoErrorMessage(responseCode, responseText)
                    emptyList()
                } else {
                    val documents = JSONObject(responseText).getJSONArray("documents")
                    buildList {
                        for (index in 0 until documents.length()) {
                            val item = documents.getJSONObject(index)
                            val latitudeValue = item.optString("y").toDoubleOrNull()
                            val longitudeValue = item.optString("x").toDoubleOrNull()
                            if (latitudeValue != null && longitudeValue != null) {
                                add(
                                    LibraryPlace(
                                        name = item.optString("place_name"),
                                        address = item.optString("road_address_name")
                                            .ifBlank { item.optString("address_name") },
                                        openInfo = buildOpenInfo(item),
                                        latitude = latitudeValue,
                                        longitude = longitudeValue,
                                        distanceKm = item.optString("distance")
                                            .toDoubleOrNull()
                                            ?.div(1000.0),
                                        kakaoPlaceId = item.optString("id").takeIf { it.isNotBlank() }
                                    )
                                )
                            }
                        }
                    }
                }
            }.getOrElse {
                lastErrorMessage = "카카오 API 요청 실패: ${it.localizedMessage ?: it.javaClass.simpleName}"
                emptyList()
            }.also {
                connection.disconnect()
            }
        }

        fun getLastErrorMessage(): String? = lastErrorMessage

        private fun buildOpenInfo(item: JSONObject): String {
            val phone = item.optString("phone")
            val url = item.optString("place_url")
            return listOf(
                "카카오 지도 검색 결과",
                phone.takeIf { it.isNotBlank() }?.let { "전화 $it" },
                url.takeIf { it.isNotBlank() }?.let { "상세 $it" }
            ).filterNotNull().joinToString(" / ")
        }

        private fun buildKakaoErrorMessage(responseCode: Int, responseText: String): String {
            val detail = extractErrorMessage(responseText)
            return when (responseCode) {
                401 -> "카카오 API 오류 401: REST API 키가 잘못됐거나 앱에 반영되지 않았습니다. $detail"
                403 -> "카카오 API 오류 403: 이 REST API 키로 카카오 로컬 검색 API 사용 권한이 없습니다. 카카오 Developers에서 해당 앱의 카카오맵/로컬 API 사용 설정을 확인해 주세요. $detail"
                429 -> "카카오 API 오류 429: 호출 한도를 초과했습니다. 잠시 후 다시 시도해 주세요. $detail"
                else -> "카카오 API 오류 $responseCode: $detail"
            }
        }

        private fun extractErrorMessage(responseText: String): String {
            if (responseText.isBlank()) return "응답 본문 없음"
            return runCatching {
                val json = JSONObject(responseText)
                json.optString("message").ifBlank { json.optString("error") }
            }.getOrDefault(responseText).take(160)
        }

        private fun String.encodeUrl(): String = URLEncoder.encode(this, "UTF-8")
    }
}
