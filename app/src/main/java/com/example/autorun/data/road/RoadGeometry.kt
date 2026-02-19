package com.example.autorun.data.road

import com.example.autorun.config.GamePerformanceSettings
import com.example.autorun.config.GameSettings
import com.example.autorun.core.CourseManager
import com.example.autorun.core.GameState
import kotlin.math.*

/**
 * 【RoadGeometry】
 * 道路の3D投影計算を担当。世界座標系に基づいた完全な3D変換を行う。
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

    fun compute(w: Float, h: Float, state: GameState, horizon: Float): Int {
        val playerDist = state.playerDistance
        val segLen = GameSettings.SEGMENT_LENGTH
        val roadW = GameSettings.ROAD_WIDTH
        val fov = GameSettings.FOV
        val stretch = GameSettings.ROAD_STRETCH
        
        val resFactor = GameSettings.getResolutionStepFactor()
        val maxSamples = (GamePerformanceSettings.ROAD_QUALITY / resFactor).toInt().coerceIn(200, MAX_SAMPLES)
        
        val camWorldX = state.playerWorldX
        val camWorldZ = state.playerWorldZ
        val camWorldY = state.playerWorldY + GameSettings.CAMERA_HEIGHT
        val camHeading = state.playerWorldHeading // ラジアン

        var currentZ = 0f
        var minY = h
        var sampleCount = 0

        val oppositeOffsetM = roadW + (GameSettings.SHOULDER_WIDTH_RIGHT * 2f) + GameSettings.MEDIAN_WIDTH

        while (currentZ < GameSettings.DRAW_DISTANCE && sampleCount < maxSamples) {
            val dz = when {
                currentZ < 100f -> 0.5f
                currentZ < 300f -> 1.0f
                else -> (currentZ / 200f).coerceIn(2.0f, 10.0f)
            } * resFactor
            
            val zOffset = currentZ
            val segmentIdx = (playerDist + zOffset) / segLen
            if (segmentIdx >= CourseManager.getTotalSegments()) break

            // 道路中心の3D座標
            val rWorldX = CourseManager.getRoadWorldX(segmentIdx)
            val rWorldZ = CourseManager.getRoadWorldZ(segmentIdx)
            val rWorldY = CourseManager.getHeight(segmentIdx)
            val rHeading = CourseManager.getRoadWorldHeading(segmentIdx)

            // カメラ相対座標への変換
            val dx = rWorldX - camWorldX
            val dz_world = rWorldZ - camWorldZ
            
            // カメラの向きに合わせて回転
            val cosH = cos(-camHeading.toDouble()).toFloat()
            val sinH = sin(-camHeading.toDouble()).toFloat()
            
            val relX = dx * cosH - dz_world * sinH
            val relZ = dx * sinH + dz_world * cosH
            
            if (relZ <= 0.1f) {
                currentZ += dz
                continue
            }

            val scale = fov / relZ
            val screenY = horizon + (camWorldY - rWorldY) * scale
            
            // オクルージョン
            val drawY = if (screenY < minY) { minY = screenY; screenY } else minY
            yCoords[sampleCount] = drawY
            zCoords[sampleCount] = relZ // 相対Zを保存

            // 道路の幅方向のベクトル（道路の向きに垂直）
            val perpX = cos(rHeading.toDouble()).toFloat()
            val perpZ = -sin(rHeading.toDouble()).toFloat()
            
            // 垂直ベクトルのカメラ相対回転
            val relPerpX = perpX * cosH - perpZ * sinH
            
            fun projectX(worldOffsetX: Float): Float {
                val worldX_ = relX + relPerpX * worldOffsetX
                return (w * 0.5f) + worldX_ * scale * stretch
            }

            val halfW = roadW * 0.5f
            val lineW = GameSettings.LANE_MARKER_WIDTH
            val sWLeft = GameSettings.SHOULDER_WIDTH_LEFT
            val sWRight = GameSettings.SHOULDER_WIDTH_RIGHT

            centerX[sampleCount] = projectX(0f)
            leftInnerX[sampleCount] = projectX(-halfW)
            rightInnerX[sampleCount] = projectX(halfW)
            leftOuterX[sampleCount] = projectX(-halfW - lineW)
            rightOuterX[sampleCount] = projectX(halfW + lineW)
            leftShoulderX[sampleCount] = projectX(-halfW - lineW - sWLeft)
            rightShoulderX[sampleCount] = projectX(halfW + lineW + sWRight)

            // 対向車線
            val oppRelX = relX + perpX * oppositeOffsetM * cosH - (-sin(rHeading.toDouble()).toFloat()) * oppositeOffsetM * sinH
            // 簡易的に offset 加算で対応
            fun projectOppX(worldOffsetX: Float): Float {
                val worldX_ = relX + relPerpX * (oppositeOffsetM + worldOffsetX)
                return (w * 0.5f) + worldX_ * scale * stretch
            }

            oppCenterX[sampleCount] = projectOppX(0f)
            oppLeftInnerX[sampleCount] = projectOppX(-halfW)
            oppRightInnerX[sampleCount] = projectOppX(halfW)
            oppLeftOuterX[sampleCount] = projectOppX(-halfW - lineW)
            oppRightOuterX[sampleCount] = projectOppX(halfW + lineW)
            oppLeftShoulderX[sampleCount] = projectOppX(-halfW - lineW - sWRight)
            oppRightShoulderX[sampleCount] = projectOppX(halfW + lineW + sWLeft)

            sampleCount++
            currentZ += dz
            if (drawY < -500) break
        }
        currentSampleCount = sampleCount
        return sampleCount
    }

    fun interpolate(zTarget: Float): InterpResult? {
        if (currentSampleCount < 2) return null
        var i = 0
        // zCoords は相対Z（カメラからの距離）
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
