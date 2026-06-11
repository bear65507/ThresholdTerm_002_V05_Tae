package com.example.thresholdterm_002

import android.app.Application
import com.example.thresholdterm_002.repository.StudyRepository

class FocusStudyApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        StudyRepository.initialize(this)
    }
}
