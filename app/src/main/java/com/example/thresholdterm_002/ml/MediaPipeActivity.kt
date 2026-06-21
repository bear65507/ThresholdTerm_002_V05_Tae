package com.example.thresholdterm_002.ml

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
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
import com.example.thresholdterm_002.IntentExtras
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MediaPipeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTimerBinding
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var viewModel: HomeViewModel

    private var poseLandmarker: PoseLandmarker? = null
    private val postureAnalyzer = FocusPostureAnalyzer()

    private val requestCameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            Toast.makeText(this, "카메라 권한 승인 완료", Toast.LENGTH_SHORT).show()
            if (viewModel.statusText.value?.contains("집중") == true) {
                startCameraStream()
            }
        } else {
            Toast.makeText(this, "카메라 권한이 거부되었습니다.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTimerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.title = "MediaPipe AI 타이머"

        cameraExecutor = Executors.newSingleThreadExecutor()
        viewModel = ViewModelProvider(this)[HomeViewModel::class.java]

        initMediaPipeEngine()
        bindIntentProfile()
        bindTimerSystem()
        checkPermissionOnCreate()
    }

    private fun initMediaPipeEngine() {
        try {
            val baseOptions = BaseOptions.builder()
                .setModelAssetPath("pose_landmarker_lite.task")
                .build()

            val options = PoseLandmarker.PoseLandmarkerOptions.builder()
                .setBaseOptions(baseOptions)
                .setRunningMode(RunningMode.LIVE_STREAM)
                .setResultListener { result, _ ->
                    calculateFocusScore(result)
                }
                .build()

            poseLandmarker = PoseLandmarker.createFromOptions(this, options)
        } catch (e: Exception) {
            Toast.makeText(this, "AI 모델 로드 실패. assets 폴더를 확인하세요.", Toast.LENGTH_LONG).show()
        }
    }

    private fun bindIntentProfile() {
        val region = listOf(
            intent.getStringExtra(IntentExtras.EXTRA_SIDO).orEmpty(),
            intent.getStringExtra(IntentExtras.EXTRA_SIGUNGU).orEmpty(),
            intent.getStringExtra(IntentExtras.EXTRA_EUP_MYEON_DONG).orEmpty()
        ).filter { it.isNotBlank() }.joinToString(" ")

        val studentStatus = intent.getStringExtra(IntentExtras.EXTRA_STUDENT_STATUS).orEmpty()
        val sourceScreen = intent.getStringExtra("EXTRA_LAUNCHER_FROM").orEmpty()

        binding.textTimerProfile.text = "전달받은 정보: $region / $studentStatus / 출발 화면: $sourceScreen"
    }

    private fun bindTimerSystem() {
        viewModel.timerText.observe(this) { binding.textTimerActivityTime.text = it }
        viewModel.statusText.observe(this) { binding.textTimerActivityStatus.text = it }

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
            Toast.makeText(this, "공부 세션이 정상 저장되었습니다.", Toast.LENGTH_SHORT).show()
            stopCameraHardware()
            finish()
        }

        binding.chipTimer15Minutes.setOnClickListener { viewModel.selectDuration(15) }
        binding.chipTimer25Minutes.setOnClickListener { viewModel.selectDuration(25) }
        binding.chipTimer50Minutes.setOnClickListener { viewModel.selectDuration(50) }

        binding.buttonTimerActivityStart.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                viewModel.startFocusSession()
                binding.viewFinder.visibility = View.VISIBLE
                startCameraStream()
            } else {
                requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
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

    private fun checkPermissionOnCreate() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun startCameraStream() {
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
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .build()

                imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                    val bitmap = imageProxy.toBitmap()
                    val timestamp = System.currentTimeMillis()

                    if (bitmap != null && poseLandmarker != null) {
                        val mpImage = BitmapImageBuilder(bitmap).build()
                        poseLandmarker?.detectAsync(mpImage, timestamp)
                    }
                    imageProxy.close()
                }

                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis)
                } catch (xc: Exception) {
                    Toast.makeText(this, "카메라 렌더링 결합 오류", Toast.LENGTH_SHORT).show()
                }

            }, ContextCompat.getMainExecutor(this))
        }
    }

    private fun calculateFocusScore(result: PoseLandmarkerResult) {
        val landmarks = result.landmarks().firstOrNull()

        if (landmarks == null || landmarks.isEmpty()) {
            runOnUiThread {
                binding.textTimerActivityFocus.text = "집중 흐림 (0점)\n카메라 범위 안에 올바르게 앉아주세요."
            }
            return
        }

        val noseLandmark = landmarks.getOrNull(0)
        val leftShoulderLandmark = landmarks.getOrNull(11)
        val rightShoulderLandmark = landmarks.getOrNull(12)

        val parsedLandmarks = mutableListOf<PoseLandmark>()

        noseLandmark?.let { parsedLandmarks.add(PoseLandmark("nose", it.x(), it.y(), it.presence().orElse(1.0f))) }
        leftShoulderLandmark?.let { parsedLandmarks.add(PoseLandmark("left_shoulder", it.x(), it.y(), it.presence().orElse(1.0f))) }
        rightShoulderLandmark?.let { parsedLandmarks.add(PoseLandmark("right_shoulder", it.x(), it.y(), it.presence().orElse(1.0f))) }

        val feedback = postureAnalyzer.analyzeMediaPipeLandmarks(parsedLandmarks, false)

        runOnUiThread {
            binding.textTimerActivityFocus.text =
                "${feedback.title} (${feedback.score}점)\n${feedback.message}"
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
        poseLandmarker?.close()
    }
}