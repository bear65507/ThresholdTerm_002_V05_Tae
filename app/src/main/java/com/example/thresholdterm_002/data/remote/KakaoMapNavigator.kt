package com.example.thresholdterm_002.data.remote

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.example.thresholdterm_002.data.model.LibraryPlace

class KakaoMapNavigator {

    fun openPlace(context: Context, place: LibraryPlace) {
        val appUri = Uri.parse(place.kakaoPlaceId?.let { "kakaomap://place?id=$it" }
            ?: "kakaomap://look?p=${place.latitude},${place.longitude}")
        val webUri = Uri.parse(place.kakaoPlaceId?.let { "https://m.map.kakao.com/scheme/place?id=$it" }
            ?: "https://m.map.kakao.com/scheme/look?p=${place.latitude},${place.longitude}")

        val appIntent = Intent(Intent.ACTION_VIEW, appUri)
        val webIntent = Intent(Intent.ACTION_VIEW, webUri)

        try {
            context.startActivity(appIntent)
        } catch (exception: ActivityNotFoundException) {
            runCatching {
                context.startActivity(webIntent)
            }.onFailure {
                Toast.makeText(context, "카카오맵을 열 수 없습니다.", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
