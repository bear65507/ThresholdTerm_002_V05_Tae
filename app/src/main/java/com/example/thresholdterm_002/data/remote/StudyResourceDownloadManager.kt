package com.example.thresholdterm_002.data.remote

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment

class StudyResourceDownloadManager(
    private val context: Context
) {

    fun enqueueStudyResource(
        title: String,
        url: String,
        mimeType: String = "application/pdf",
        userAgent: String? = null,
        cookies: String? = null
    ): Long {
        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle(title)
            .setDescription("공부 자료를 다운로드합니다.")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(false)
            .setMimeType(mimeType)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, title)

        userAgent?.takeIf { it.isNotBlank() }?.let {
            request.addRequestHeader("User-Agent", it)
        }
        cookies?.takeIf { it.isNotBlank() }?.let {
            request.addRequestHeader("Cookie", it)
        }

        val manager = context.getSystemService(DownloadManager::class.java)
        return manager.enqueue(request)
    }

    fun downloadLatestSuneungKorean(): Long {
        return enqueueStudyResource(
            title = "2026_suneung_korean_previous_exam.pdf",
            url = SUNEUNG_KOREAN_PDF_URL
        )
    }

    fun downloadLatestMockKorean(): Long {
        return enqueueStudyResource(
            title = "2027_june_mock_evaluation_korean_previous_exam.pdf",
            url = MOCK_EVALUATION_KOREAN_PDF_URL
        )
    }

    companion object {
        private const val SUNEUNG_KOREAN_PDF_URL =
            "https://www.suneung.re.kr/boardCnts/fileDown.do?fileSeq=60defdef6d83db1b756f841089563c5a"
        private const val MOCK_EVALUATION_KOREAN_PDF_URL =
            "https://www.suneung.re.kr/boardCnts/fileDown.do?fileSeq=6c834eb227f48cfed7dda45485325977"
    }
}
