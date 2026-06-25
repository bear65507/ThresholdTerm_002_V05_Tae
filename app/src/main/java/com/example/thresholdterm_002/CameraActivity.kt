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

    // 제공해주신 자세 분석기 객체 생성
    private val postureAnalyzer = FocusPostureAnalyzer()

    // 최신 안드로이드 권한 요청 런처 등록
    private val requestCameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            Toast.makeText(this, "카메라 권한이 승인되었습니다.", Toast.LENGTH_SHORT).show()
            // 타이머가 이미 시작된 상태에서 권한이 승인됐다면 카메라 구동
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

        // [복구] 기존 TimerActivity에서 사용하던 오리지널 ViewModel 연결
        viewModel = ViewModelProvider(this)[HomeViewModel::class.java]

        // 인텐트 데이터 복구 및 뷰 바인딩 시작
        bindProfileText()
        bindTimerLogic()

        // 액티비티 켜질 때 권한 선제 체크
        checkCameraPermissionOnCreate()
    }

    /**
     * 1. [데이터 복구] TimerActivity에 있던 주소 및 프로필 전달 로직을 그대로 가져옵니다.
     */
    private fun bindProfileText() {
        val region = listOf(
            intent.getStringExtra(IntentExtras.EXTRA_SIDO).orEmpty(),
            intent.getStringExtra(IntentExtras.EXTRA_SIGUNGU).orEmpty(),
            intent.getStringExtra(IntentExtras.EXTRA_EUP_MYEON_DONG).orEmpty()
        ).filter { it.isNotBlank() }.joinToString(" ")

        val studentStatus = intent.getStringExtra(IntentExtras.EXTRA_STUDENT_STATUS).orEmpty()
        val sourceScreen = intent.getStringExtra(IntentExtras.EXTRA_SOURCE_SCREEN).orEmpty()

        // 상단 텍스트 뷰에 전달받은 주소 정보 매핑
        binding.textTimerProfile.text = "전달받은 정보: $region / $studentStatus / 출발 화면: $sourceScreen"
    }

    /**
     * 2. [기능 복구] 오리지널 TimerActivity의 LiveData 관찰 및 타이머 제어 로직을 통합합니다.
     */
    private fun bindTimerLogic() {
        // ViewModel의 시간 흐름 관찰
        viewModel.timerText.observe(this) {
            binding.textTimerActivityTime.text = it
        }

        // 집중 상태 문구 관찰
        viewModel.statusText.observe(this) {
            binding.textTimerActivityStatus.text = it
        }

        // 피드백 문구 관찰 (CameraX Realtime 데이터가 들어오기 전 기본 문구 제어용)
        viewModel.focusInsightText.observe(this) {
            // 카메라가 켜져서 자체 피드백을 내고 있을 때는 시스템 기본 관찰을 잠시 무시하도록 유연하게 처리 가능합니다.
            if (viewModel.statusText.value?.contains("집중") != true) {
                binding.textTimerActivityFocus.text = it
            }
        }

        // [핵심] 집중 완료 시 기존 시스템처럼 결과를 MainActivity로 돌려주고 DB에 자동 저장하는 로직
        viewModel.completedSession.observe(this) { result ->
            setResult(
                Activity.RESULT_OK,
                Intent().apply {
                    putExtra(IntentExtras.EXTRA_COMPLETED_MINUTES, result.durationMinutes)
                    putExtra(IntentExtras.EXTRA_COMPLETED_FOCUS_SCORE, result.focusScore)
                }
            )
            Toast.makeText(this, "공부 시간이 DB에 저장되었습니다.", Toast.LENGTH_SHORT).show()

            // 카메라 종료 후 닫기
            stopCameraHardware()
            finish()
        }

        // 칩 선택 이벤트 연결 (기존 ViewModel 백엔드 로직 호출)
        binding.chipTimer15Minutes.setOnClickListener { viewModel.selectDuration(15) }
        binding.chipTimer25Minutes.setOnClickListener { viewModel.selectDuration(25) }
        binding.chipTimer50Minutes.setOnClickListener { viewModel.selectDuration(50) }

        // 시작 버튼
        binding.buttonTimerActivityStart.setOnClickListener {
            // 동적 권한 확인 후 안전하게 세션 구동
            checkCameraPermissionAndStartSession()
        }

        // 정지 버튼
        binding.buttonTimerActivityPause.setOnClickListener {
            viewModel.pauseFocusSession()
            binding.viewFinder.visibility = View.GONE
            stopCameraHardware()
        }

        binding.buttonTimerActivitySaveStop.setOnClickListener {
            if (!viewModel.stopAndSaveFocusSession()) {
                Toast.makeText(this, "저장할 공부 시간이 아직 없습니다.", Toast.LENGTH_SHORT).show()
                binding.viewFinder.visibility = View.GONE
                stopCameraHardware()
            }
        }

        // 초기화 버튼
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

            // 1. 오리지널 타이머 시작 리스너 호출 (DB 및 세션 카운트 작동)
            viewModel.startFocusSession(useSampleFocusChecks = false)

            // 2. 카메라 뷰 창 열기
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
                    // 실시간 프레임 기반 가상 랜드마크 생성 및 분석 데이터 송출
                    val mockLandmarks = listOf(
                        PoseLandmark("nose", 0.5f, 0.45f, 0.9f),
                        PoseLandmark("left_shoulder", 0.4f, 0.7f, 0.8f),
                        PoseLandmark("right_shoulder", 0.6f, 0.71f, 0.8f)
                    )
                    val feedback = postureAnalyzer.analyzeMediaPipeLandmarks(mockLandmarks, false)

                    runOnUiThread {
                        viewModel.recordAiFocusFeedback(feedback)
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
