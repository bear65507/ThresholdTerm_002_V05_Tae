package com.example.thresholdterm_002.ml

import com.example.thresholdterm_002.data.model.FocusFeedback
import kotlin.math.abs

data class PoseLandmark(
    val name: String,
    val x: Float,
    val y: Float,
    val visibility: Float
)

data class FocusSignal(
    val faceVisible: Boolean,
    val phoneDetected: Boolean,
    val headDownRatio: Float,
    val shoulderTiltRatio: Float
)

class FocusPostureAnalyzer {

    fun analyze(signal: FocusSignal): FocusFeedback {
        var score = 100
        if (!signal.faceVisible) score -= 25
        if (signal.phoneDetected) score -= 35
        score -= (signal.headDownRatio.coerceIn(0f, 1f) * 25).toInt()
        score -= (signal.shoulderTiltRatio.coerceIn(0f, 1f) * 15).toInt()

        val finalScore = score.coerceIn(0, 100)
        return formatFeedback(finalScore)
    }

    /*
     * 1. 얼굴 및 상체 핵심 랜드마크(코, 양쪽 어깨) 중 하나라도 손실되면 즉시 대폭 감점 (-40점)
     * 2. 고개 들기(Head Up) 감지 임계값을 현실적인 수치(0.02)로 낮추고 페널티 가중치 강화
     * 3. 어깨 기울기 및 스마트폰 감지 결합
     */
    fun analyzeMediaPipeLandmarks(landmarks: List<PoseLandmark>, phoneDetected: Boolean): FocusFeedback {
        val nose = landmarks.firstOrNull { it.name == "nose" }
        val leftShoulder = landmarks.firstOrNull { it.name == "left_shoulder" }
        val rightShoulder = landmarks.firstOrNull { it.name == "right_shoulder" }

        val minConfidence = 0.75f
        val isBodyFullyDetected = (nose != null && nose.visibility > minConfidence) &&
                (leftShoulder != null && leftShoulder.visibility > minConfidence) &&
                (rightShoulder != null && rightShoulder.visibility > minConfidence)

        var score = 100

        if (phoneDetected) {
            score -= 35
        }

        // 카메라에 얼굴 전체나 상체가 온전히 나오지 않으면 가차 없이 -40점 감점 (최대 60점 제한)
        if (!isBodyFullyDetected) {
            score -= 40
            return formatFeedback(score.coerceIn(0, 100))
        }

        // 양쪽 어깨와 코가 모두 완벽히 잡힌 상태에서만 세부 자세(고개 각도) 분석 진행
        if (leftShoulder != null && rightShoulder != null && nose != null) {

            // 어깨 수평도 계산
            val shoulderTilt = abs(leftShoulder.y - rightShoulder.y).coerceIn(0f, 1f)
            score -= (shoulderTilt * 20).toInt()

            // 어깨선 평균 높이 계산
            val avgShoulderY = (leftShoulder.y + rightShoulder.y) / 2f

            val expectedNormalNoseY = avgShoulderY - 0.21f

            val noseOffset = nose.y - expectedNormalNoseY

            // 고개 들기(Head Up) 감지 감도 및 패널티 정밀 조정
            if (noseOffset < -0.02f) {
                val headUpPenalty = (abs(noseOffset) * 250).toInt().coerceIn(10, 25)
                score -= headUpPenalty
            }
            // 고개를 아래로 숙였을 때
            else if (noseOffset > 0.03f) {
                val headDown = noseOffset.coerceIn(0f, 1f)
                val rawHeadDownPenalty = (headDown * 30).toInt()

                // 얼굴 전체가 정상 범위에 있다면 필기나 독서로 감안하여 80% 감면 적용
                score -= (rawHeadDownPenalty * 0.2f).toInt()
            }
        }

        val finalScore = score.coerceIn(0, 100)
        return formatFeedback(finalScore)
    }

    private fun formatFeedback(finalScore: Int): FocusFeedback {
        return when {
            finalScore >= 85 -> FocusFeedback(finalScore, "집중 상태 좋음", "시선과 자세가 안정적입니다. 지금 페이스를 유지하세요.")
            finalScore >= 65 -> FocusFeedback(finalScore, "집중 보통", "고개가 중심을 벗어나거나 자세가 틀어졌습니다. 모니터를 정면으로 바라보세요.")
            else -> FocusFeedback(finalScore, "집중 흐림", "얼굴 일부 미검출 혹은 고개 들기/숙임이 심합니다. 카메라 정중앙에 바르게 앉아주세요.")
        }
    }
}