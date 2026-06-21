package com.example.thresholdterm_002

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import com.example.thresholdterm_002.databinding.ActivityTimerBinding
import com.example.thresholdterm_002.ml.FocusPostureAnalyzer
import com.example.thresholdterm_002.ml.PoseLandmark
import com.example.thresholdterm_002.ui.home.HomeViewModel
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTimerBinding
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var viewModel: HomeViewModel

    private val postureAnalyzer = FocusPostureAnalyzer()

    private val requestCameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            Toast.makeText(this, "카메라 권한이 승인되었습니다.", Toast.LENGTH_SHORT).show()

            if (viewModel.statusText.value?.contains("집중") == true) {
                startCameraWithPost()
            }
        } else {
            Toast.makeText(this, "카메라 권한이 거부되었습니다. 설정에서 허용해 주세요.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTimerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.title = "카메라 분석 타이머"

        cameraExecutor = Executors.newSingleThreadExecutor()

        viewModel = ViewModelProvider(this)[HomeViewModel::class.java]

        bindProfileText()
        bindTimerLogic()

        checkCameraPermissionOnCreate()
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

    private fun bindTimerLogic() {
        viewModel.timerText.observe(this) {
            binding.textTimerActivityTime.text = it
        }

        viewModel.statusText.observe(this) {
            binding.textTimerActivityStatus.text = it
        }

        viewModel.focusInsightText.observe(this) {
            if (viewModel.statusText.value?.contains("집중") != true) {
                binding.textTimerActivityFocus.text = it
            }
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

            stopCameraHardware()
            finish()
        }

        binding.chipTimer15Minutes.setOnClickListener { viewModel.selectDuration(15) }
        binding.chipTimer25Minutes.setOnClickListener { viewModel.selectDuration(25) }
        binding.chipTimer50Minutes.setOnClickListener { viewModel.selectDuration(50) }

        binding.buttonTimerActivityStart.setOnClickListener {
            checkCameraPermissionAndStartSession()
        }

        binding.buttonTimerActivityPause.setOnClickListener {
            viewModel.pauseFocusSession()
            binding.viewFinder.visibility = View.GONE
            stopCameraHardware()
        }

        binding.buttonTimerActivityReset.setOnClickListener {
            viewModel.resetFocusSession()
            binding.viewFinder.visibility = View.GONE
            stopCameraHardware()
        }
    }

    private fun checkCameraPermissionOnCreate() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED) {
            requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun checkCameraPermissionAndStartSession() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED) {

            viewModel.startFocusSession()

            binding.viewFinder.visibility = View.VISIBLE
            startCameraWithPost()
        } else {
            requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun startCameraWithPost() {
        binding.viewFinder.post {
            val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

            cameraProviderFuture.addListener({
                val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
                val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(binding.viewFinder.surfaceProvider)
                }

                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                    val mockLandmarks = listOf(
                        PoseLandmark("nose", 0.5f, 0.45f, 0.9f),
                        PoseLandmark("left_shoulder", 0.4f, 0.7f, 0.8f),
                        PoseLandmark("right_shoulder", 0.6f, 0.71f, 0.8f)
                    )
                    val feedback = postureAnalyzer.analyzeMediaPipeLandmarks(mockLandmarks, false)

                    runOnUiThread {
                        binding.textTimerActivityFocus.text =
                            "${feedback.title} (${feedback.score}점)\n${feedback.message}"
                    }
                    imageProxy.close()
                }

                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        this, cameraSelector, preview, imageAnalysis
                    )
                } catch (xc: Exception) {
                    Toast.makeText(this, "카메라 파이프라인 결합 오류", Toast.LENGTH_SHORT).show()
                }

            }, ContextCompat.getMainExecutor(this))
        }
    }

    private fun stopCameraHardware() {
        try {
            ProcessCameraProvider.getInstance(this).get().unbindAll()
        } catch (e: Exception) { }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}