package com.example.thresholdterm_002.ui

import android.content.Context
import android.content.Intent
import com.example.thresholdterm_002.IntentExtras
import com.example.thresholdterm_002.data.local.ProfileStore
import com.example.thresholdterm_002.ui.library.LibraryActivity
import com.example.thresholdterm_002.ui.stats.StatsActivity
import com.example.thresholdterm_002.ui.timer.TimerActivity

object ActivityLauncher {

    fun openTimer(context: Context, sourceScreen: String) {
        context.startActivity(createProfileIntent(context, TimerActivity::class.java, sourceScreen))
    }

    fun openStats(context: Context, sourceScreen: String) {
        context.startActivity(createProfileIntent(context, StatsActivity::class.java, sourceScreen))
    }

    fun openLibrary(context: Context, sourceScreen: String) {
        context.startActivity(createProfileIntent(context, LibraryActivity::class.java, sourceScreen))
    }

    private fun createProfileIntent(
        context: Context,
        activityClass: Class<*>,
        sourceScreen: String
    ): Intent {
        return createIntent(context, activityClass, sourceScreen)
    }

    fun createIntent(
        context: Context,
        activityClass: Class<*>,
        sourceScreen: String
    ): Intent {
        val profile = ProfileStore(context).getProfile()
        return Intent(context, activityClass).apply {
            putExtra(IntentExtras.EXTRA_SOURCE_SCREEN, sourceScreen)
            putExtra(IntentExtras.EXTRA_SIDO, profile?.sido.orEmpty())
            putExtra(IntentExtras.EXTRA_SIGUNGU, profile?.sigungu.orEmpty())
            putExtra(IntentExtras.EXTRA_EUP_MYEON_DONG, profile?.eupMyeonDong.orEmpty())
            putExtra(IntentExtras.EXTRA_STUDENT_STATUS, profile?.studentStatus.orEmpty())
        }
    }
}
