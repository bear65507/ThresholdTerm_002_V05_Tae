package com.example.thresholdterm_002.data.remote

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment

class StudyResourceDownloadManager(
    private val context: Context
) {

    fun enqueueStudyResource(title: String, url: String): Long {
        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle(title)
            .setDescription("공부 자료를 다운로드합니다.")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, title)

        val manager = context.getSystemService(DownloadManager::class.java)
        return manager.enqueue(request)
    }
}
