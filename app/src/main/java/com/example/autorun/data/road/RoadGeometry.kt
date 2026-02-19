package com.example.autorun.data.road

import com.example.autorun.config.GamePerformanceSettings
import com.example.autorun.config.GameSettings
import com.example.autorun.core.CourseManager
import com.example.autorun.core.GameState
import kotlin.math.abs

/**
 * 【RoadGeometry】
 * 道路の3D投影計算と座標バッファの保持を担当するデータクラス。
 */
object RoadGeometry {
    const val MAX_SAMPLES = 4000

    val leftShoulderX = FloatArray(MAX_SAMPLES + 1)
    val rightShoulderX = FloatArray(MAX_SAMPLES + 1)
    val leftOuterX = FloatArray(MAX_SAMPLES + 1)
    val leftInnerX = FloatArray(MAX_SAMPLES + 1)
    val rightInnerX = FloatArray(MAX_SAMPLES + 1)
    val rightOuterX = FloatArray(MAX_SAMPLES + 1)
    val centerX = FloatArray(MAX_SAMPLES + 1)

    val oppLeftShoulderX = FloatArray(MAX_SAMPLES + 1)
    val oppRightShoulderX = FloatArray(MAX_SAMPLES + 1)
    val oppLeftOuterX = FloatArray(MAX_SAMPLES + 1)
    val oppLeftInnerX = FloatArray(MAX_SAMPLES + 1)
    val oppRightInnerX = FloatArray(MAX_SAMPLES + 1)
    val oppRightOuterX = FloatArray(MAX_SAMPLES + 1)
    val oppCenterX = FloatArray(MAX_SAMPLES + 1)

    val yCoords = FloatArray(MAX_SAMPLES + 1)
    val zCoords = FloatArray(MAX_SAMPLES + 1)
    
    var currentSampleCount = 0
    var maxLeftXIdx = -1
    var maxRightXIdx = -1

    fun compute(w: Float, h: Float, state: GameState, horizon: Float): Int {
        val playerDist = state.playerDistance
        val segLen = GameSettings.SEGMENT_LENGTH
        val roadW = GameSettings.ROAD_WIDTH
        val fov = GameSettings.FOV
        val stretch = GameSettings.ROAD_STRETCH
        
        val resFactor = GameSettings.getResolutionStepFactor()
        // 描画品質に応じてサンプル数を調整
        val maxSamples = (GamePerformanceSettings.ROAD_QUALITY / resFactor).toInt().coerceIn(200, MAX_SAMPLES)
        
        // カメラ位置の計算（スムーズな高低差反映）
        val camWorldY = CourseManager.getHeight(playerDist / segLen) + GameSettings.CAMERA_HEIGHT

        var currentZ = 0f
        var curDX = 0f
        var curX = 0f
        var minY = h
        var sampleCount = 0

        maxLeftXIdx = -1
        maxRightXIdx = -1
        var maxLeftX = -Float.MAX_VALUE
        var maxRightX = -Float.MAX_VALUE

        val oppositeOffsetM = roadW + (GameSettings.SHOULDER_WIDTH_RIGHT * 2f) + GameSettings.MEDIAN_WIDTH

        while (currentZ < GameSettings.DRAW_DISTANCE && sampleCount < maxSamples) {
            // サンプリングステップの最適化（奥に行くほどステップを広げるが、急激な変化を避ける）
            val baseDz = when {
                currentZ < 100f -> 0.2f
                currentZ < 300f -> 0.5f
                else -> (currentZ / 400f).coerceIn(1.0f, 3.0f)
            }
            val dz = baseDz * resFactor
            
            val z = currentZ
            val segmentIdx = (playerDist + z) / segLen
            
            val curve = CourseManager.getCurve(segmentIdx)
            val nextDX = curDX + curve * dz
            val nextX = curX + nextDX * dz
            
            val scale = fov / (z + 0.1f)
            val worldY = CourseManager.getHeight(segmentIdx)
            val y = horizon + (camWorldY - worldY) * scale
            
            // スキップ閾値を下げて精度を向上（カクつき防止）
            val skipThreshold = 0.02f * resFactor
            if (sampleCount > 0 && z > 100f && abs(y - yCoords[sampleCount - 1]) < skipThreshold) {
                curDX = nextDX; curX = nextX; currentZ += dz
                continue
            }

            // オクルージョン（手前の道路が奥を隠す）の計算を厳密化
            val drawY = if (y < minY) { minY = y; y } else minY
            yCoords[sampleCount] = drawY
            zCoords[sampleCount] = z

            val screenX = (w * 0.5f) + (curX - state.playerX) * scale * stretch
            val screenW = roadW * scale * stretch
            val lineW = (GameSettings.LANE_MARKER_WIDTH / roadW) * screenW
            val sWLeft = (GameSettings.SHOULDER_WIDTH_LEFT / roadW) * screenW
            val sWRight = (GameSettings.SHOULDER_WIDTH_RIGHT / roadW) * screenW

            centerX[sampleCount] = screenX
            leftInnerX[sampleCount] = screenX - screenW * 0.5f
            rightInnerX[sampleCount] = screenX + screenW * 0.5f
            leftOuterX[sampleCount] = leftInnerX[sampleCount] - lineW
            rightOuterX[sampleCount] = rightInnerX[sampleCount] + lineW
            leftShoulderX[sampleCount] = leftOuterX[sampleCount] - sWLeft
            rightShoulderX[sampleCount] = rightOuterX[sampleCount] + sWRight

            val oppCenterXWorld = curX + oppositeOffsetM
            val oppScreenX = (w * 0.5f) + (oppCenterXWorld - state.playerX) * scale * stretch
            
            oppCenterX[sampleCount] = oppScreenX
            oppLeftInnerX[sampleCount] = oppScreenX - screenW * 0.5f
            oppRightInnerX[sampleCount] = oppScreenX + screenW * 0.5f
            oppLeftOuterX[sampleCount] = oppLeftInnerX[sampleCount] - lineW
            oppRightOuterX[sampleCount] = oppRightInnerX[sampleCount] + lineW
            oppLeftShoulderX[sampleCount] = oppLeftOuterX[sampleCount] - sWRight
            oppRightShoulderX[sampleCount] = oppRightOuterX[sampleCount] + sWLeft

            val lx = leftShoulderX[sampleCount]
            val rx = oppRightShoulderX[sampleCount]
            if (lx > maxLeftX) { maxLeftX = lx; maxLeftXIdx = sampleCount }
            if (rx > maxRightX) { maxRightX = rx; maxRightXIdx = sampleCount }

            sampleCount++
            curDX = nextDX; curX = nextX; currentZ += dz
            if (drawY < -200) break // 画面外に十分出たら終了
        }
        currentSampleCount = sampleCount
        return sampleCount
    }

    fun interpolate(zTarget: Float): InterpResult? {
        if (currentSampleCount < 2) return null
        var i = 0
        while (i < currentSampleCount - 1 && zCoords[i + 1] < zTarget) { i++ }
        if (i >= currentSampleCount - 1) return null
        
        val z1 = zCoords[i]; val z2 = zCoords[i + 1]
        val t = (zTarget - z1) / (z2 - z1)
        
        return InterpResult(
            lx = leftShoulderX[i] + (leftShoulderX[i + 1] - leftShoulderX[i]) * t,
            rx = rightShoulderX[i] + (rightShoulderX[i + 1] - rightShoulderX[i]) * t,
            oppLx = oppLeftShoulderX[i] + (oppLeftShoulderX[i + 1] - oppLeftShoulderX[i]) * t,
            oppRx = oppRightShoulderX[i] + (oppRightShoulderX[i + 1] - oppRightShoulderX[i]) * t,
            centerX = centerX[i] + (centerX[i + 1] - centerX[i]) * t,
            y = yCoords[i] + (yCoords[i + 1] - yCoords[i]) * t,
            scale = GameSettings.FOV / (zTarget + 0.1f)
        )
    }

    data class InterpResult(val lx: Float, val rx: Float, val oppLx: Float, val oppRx: Float, val centerX: Float, val y: Float, val scale: Float)
}
