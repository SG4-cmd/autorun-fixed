package com.example.autorun.data.road

import com.example.autorun.config.GamePerformanceSettings
import com.example.autorun.config.GameSettings
import com.example.autorun.core.CourseManager
import com.example.autorun.core.GameState
import kotlin.math.*

/**
 * 【RoadGeometry】
 * 3D投影エンジン。
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
        
        val resStepFactor = GameSettings.getResolutionStepFactor()
        val maxSamples = (GamePerformanceSettings.ROAD_QUALITY / resStepFactor).toInt().coerceIn(200, MAX_SAMPLES)
        
        val camX = state.playerWorldX
        val camZ = state.playerWorldZ
        val camY = state.playerWorldY + GameSettings.CAMERA_HEIGHT
        
        // 修正：カメラの向きに対して逆方向に回転させて投影する (World to Camera transform)
        val camH = state.playerWorldHeading
        val cosCam = cos(camH.toDouble()).toFloat()
        val sinCam = sin(camH.toDouble()).toFloat()

        var sampleCount = 0
        var currentZOffset = 0f

        val oppOffsetM = roadW + (GameSettings.SHOULDER_WIDTH_RIGHT * 2f) + GameSettings.MEDIAN_WIDTH

        while (currentZOffset < GameSettings.DRAW_DISTANCE && sampleCount < maxSamples) {
            val dz = when {
                currentZOffset < 50f -> 0.5f
                currentZOffset < 200f -> 1.5f
                else -> (currentZOffset / 100f).coerceIn(2.0f, 15.0f)
            }
            
            val segmentIdx = (playerDist + currentZOffset) / segLen
            if (segmentIdx >= CourseManager.getTotalSegments()) break

            val rWorldX = CourseManager.getRoadWorldX(segmentIdx)
            val rWorldZ = CourseManager.getRoadWorldZ(segmentIdx)
            val rWorldY = CourseManager.getHeight(segmentIdx)
            val rH = CourseManager.getRoadWorldHeading(segmentIdx)

            val dx = rWorldX - camX
            val dz_ = rWorldZ - camZ

            // 修正：回転行列の適用 (Heading方向の正文化)
            // rx = dx * cos(H) - dz * sin(H)
            // rz = dx * sin(H) + dz * cos(H)
            val rxCenter = dx * cosCam - dz_ * sinCam
            val rzCenter = dx * sinCam + dz_ * cosCam
            
            if (rzCenter <= 1.0f) {
                currentZOffset += dz
                continue
            }

            val scale = fov / rzCenter
            val screenY = horizon - (rWorldY - camY) * scale
            
            // 道路の横方向ベクトル
            val perpX = cos(rH.toDouble()).toFloat()
            val perpZ = -sin(rH.toDouble()).toFloat()

            centerX[sampleCount] = (w * 0.5f) + rxCenter * scale * stretch
            yCoords[sampleCount] = screenY
            zCoords[sampleCount] = currentZOffset

            val halfW = roadW * 0.5f
            val lineW = GameSettings.LANE_MARKER_WIDTH
            val sL = GameSettings.SHOULDER_WIDTH_LEFT
            val sR = GameSettings.SHOULDER_WIDTH_RIGHT

            fun getX(offsetX: Float): Float {
                val vx = rWorldX + perpX * offsetX
                val vz = rWorldZ + perpZ * offsetX
                val dxv = vx - camX
                val dzv = vz - camZ
                val rxv = dxv * cosCam - dzv * sinCam
                val rzv = dxv * sinCam + dzv * cosCam
                return if (rzv > 1.0f) (w * 0.5f) + rxv * (fov / rzv) * stretch else centerX[sampleCount]
            }

            leftInnerX[sampleCount] = getX(-halfW)
            rightInnerX[sampleCount] = getX(halfW)
            leftOuterX[sampleCount] = getX(-halfW - lineW)
            rightOuterX[sampleCount] = getX(halfW + lineW)
            leftShoulderX[sampleCount] = getX(-halfW - lineW - sL)
            rightShoulderX[sampleCount] = getX(halfW + lineW + sR)

            oppCenterX[sampleCount] = getX(oppOffsetM)
            oppLeftShoulderX[sampleCount] = getX(oppOffsetM - halfW - lineW - sR)
            oppRightShoulderX[sampleCount] = getX(oppOffsetM + halfW + lineW + sL)

            sampleCount++
            currentZOffset += dz
        }
        currentSampleCount = sampleCount
        return sampleCount
    }

    fun interpolate(zTarget: Float): InterpResult? {
        if (currentSampleCount < 2) return null
        var i = 0
        while (i < currentSampleCount - 1 && zCoords[i + 1] < zTarget) i++
        if (i >= currentSampleCount - 1) return null
        val t = (zTarget - zCoords[i]) / (zCoords[i + 1] - zCoords[i])
        fun f(a: FloatArray) = a[i] + (a[i+1] - a[i]) * t
        return InterpResult(f(leftShoulderX), f(rightShoulderX), f(oppLeftShoulderX), f(oppRightShoulderX), f(centerX), f(yCoords), GameSettings.FOV / (zTarget + 0.1f))
    }

    data class InterpResult(val lx: Float, val rx: Float, val oppLx: Float, val oppRx: Float, val centerX: Float, val y: Float, val scale: Float)
}
