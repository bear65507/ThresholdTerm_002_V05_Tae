package com.example.thresholdterm_002.ui.timer

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.example.thresholdterm_002.IntentExtras
import com.example.thresholdterm_002.databinding.ActivityTimerBinding
import com.example.thresholdterm_002.ui.home.HomeViewModel

class TimerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTimerBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTimerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.title = "집중 타이머"

        val viewModel = ViewModelProvider(this)[HomeViewModel::class.java]
        bindProfileText()
        bindTimer(viewModel)
    }

    private fun bindProfileText() {
        val region = listOf(
            intent.getStringExtra(IntentExtras.EXTRA_SIDO).orEmpty(),
            intent.getStringExtra(IntentExtras.EXTRA_SIGUNGU).orEmpty(),
            intent.getStringExtra(IntentExtras.EXTRA_EUP_MYEON_DONG).orEmpty()
        ).filter { it.isNotBlank() }.joinToString(" ")
        val studentStatus = intent.getStringExtra(IntentExtras.EXTRA_STUDENT_STATUS).orEmpty()
        val sourceScreen = intent.getStringExtra(IntentExtras.EXTRA_SOURCE_SCREEN).orEmpty()
        binding.textTimerProfile.text = "전달받은 정보: $region / $studentStatus / 출발 화면: $sourceScreen"
    }

    private fun bindTimer(viewModel: HomeViewModel) {
        viewModel.timerText.observe(this) {
            binding.textTimerActivityTime.text = it
        }
        viewModel.statusText.observe(this) {
            binding.textTimerActivityStatus.text = it
        }
        viewModel.focusInsightText.observe(this) {
            binding.textTimerActivityFocus.text = it
        }
        viewModel.completedSession.observe(this) { result ->
            setResult(
                Activity.RESULT_OK,
                Intent().apply {
                    putExtra(IntentExtras.EXTRA_COMPLETED_MINUTES, result.durationMinutes)
                    putExtra(IntentExtras.EXTRA_COMPLETED_FOCUS_SCORE, result.focusScore)
                }
            )
            Toast.makeText(this, "공부 시간이 DB에 저장되었습니다.", Toast.LENGTH_SHORT).show()
            finish()
        }

        binding.chipTimer15Minutes.setOnClickListener {
            viewModel.selectDuration(15)
        }
        binding.chipTimer25Minutes.setOnClickListener {
            viewModel.selectDuration(25)
        }
        binding.chipTimer50Minutes.setOnClickListener {
            viewModel.selectDuration(50)
        }
        binding.buttonTimerActivityStart.setOnClickListener {
            viewModel.startFocusSession()
        }
        binding.buttonTimerActivityPause.setOnClickListener {
            viewModel.pauseFocusSession()
        }
        binding.buttonTimerActivityReset.setOnClickListener {
            viewModel.resetFocusSession()
        }
    }
}
