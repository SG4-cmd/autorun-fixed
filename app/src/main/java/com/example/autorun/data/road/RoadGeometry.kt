package com.example.autorun.data.road

import com.example.autorun.config.GamePerformanceSettings
import com.example.autorun.config.GameSettings
import com.example.autorun.core.CourseManager
import com.example.autorun.core.GameState
import kotlin.math.*

/**
 * 【RoadGeometry】
 * 3D投影エンジン。自車の後方も含めて座標変換を行う。
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
        val camH = state.playerWorldHeading
        
        val cosCam = cos(camH.toDouble()).toFloat()
        val sinCam = sin(camH.toDouble()).toFloat()
        val cosP = cos(state.cameraPitch.toDouble()).toFloat()
        val sinP = sin(state.cameraPitch.toDouble()).toFloat()

        var sampleCount = 0
        var currentRelDist = -20f // 自車より20m手前からサンプリング開始

        val oppOffsetM = roadW + (GameSettings.SHOULDER_WIDTH_RIGHT * 2f) + GameSettings.MEDIAN_WIDTH

        while (currentRelDist < GameSettings.DRAW_DISTANCE && sampleCount < maxSamples) {
            val dz = when {
                currentRelDist < 20f -> 0.5f
                currentRelDist < 100f -> 1.5f
                else -> (currentRelDist / 100f).coerceIn(2.0f, 15.0f)
            }
            
            val segmentIdx = (playerDist + currentRelDist) / segLen
            if (segmentIdx >= CourseManager.getTotalSegments() || segmentIdx < 0) {
                currentRelDist += dz
                continue
            }

            val rWorldX = CourseManager.getRoadWorldX(segmentIdx)
            val rWorldZ = CourseManager.getRoadWorldZ(segmentIdx)
            val rWorldY = CourseManager.getHeight(segmentIdx)
            val rH = CourseManager.getRoadWorldHeading(segmentIdx)

            // プロジェクション計算 (関数化せずにインラインまたはフラグで制御)
            val dxCenter = rWorldX - camX
            val dzCenter = rWorldZ - camZ
            val dyCenter = rWorldY - camY
            
            val rxCenter = dxCenter * cosCam - dzCenter * sinCam
            val rzTmpCenter = dxCenter * sinCam + dzCenter * cosCam
            val ryCenter = dyCenter * cosP - rzTmpCenter * sinP
            val rzCenter = dyCenter * sinP + rzTmpCenter * cosP
            
            if (rzCenter <= 1.0f) {
                currentRelDist += dz
                continue
            }

            val scaleCenter = fov / rzCenter
            centerX[sampleCount] = (w * 0.5f) + rxCenter * scaleCenter * stretch
            yCoords[sampleCount] = horizon - ryCenter * scaleCenter
            zCoords[sampleCount] = currentRelDist

            val perpX = cos(rH.toDouble()).toFloat()
            val perpZ = -sin(rH.toDouble()).toFloat()
            val halfW = roadW * 0.5f
            val lineW = GameSettings.LANE_MARKER_WIDTH
            val sL = GameSettings.SHOULDER_WIDTH_LEFT
            val sR = GameSettings.SHOULDER_WIDTH_RIGHT

            fun projectX(offsetX: Float): Float {
                val wx = rWorldX + perpX * offsetX
                val wz = rWorldZ + perpZ * offsetX
                val dx = wx - camX
                val dz = wz - camZ
                val rx = dx * cosCam - dz * sinCam
                val rzTmp = dx * sinCam + dz * cosCam
                // Y軸回転(Pitch)の影響も考慮したRZでスケールを出す
                val rz = (rWorldY - camY) * sinP + rzTmp * cosP
                return if (rz > 1.0f) (w * 0.5f) + rx * (fov / rz) * stretch else centerX[sampleCount]
            }

            leftInnerX[sampleCount] = projectX(-halfW)
            rightInnerX[sampleCount] = projectX(halfW)
            leftOuterX[sampleCount] = projectX(-halfW - lineW)
            rightOuterX[sampleCount] = projectX(halfW + lineW)
            leftShoulderX[sampleCount] = projectX(-halfW - lineW - sL)
            rightShoulderX[sampleCount] = projectX(halfW + lineW + sR)

            oppCenterX[sampleCount] = projectX(oppOffsetM)
            oppLeftShoulderX[sampleCount] = projectX(oppOffsetM - halfW - lineW - sR)
            oppRightShoulderX[sampleCount] = projectX(oppOffsetM + halfW + lineW + sL)

            sampleCount++
            currentRelDist += dz
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
