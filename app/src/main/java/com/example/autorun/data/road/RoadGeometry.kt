package com.example.autorun.data.road

import com.example.autorun.config.GamePerformanceSettings
import com.example.autorun.config.GameSettings
import com.example.autorun.core.CourseManager
import com.example.autorun.core.GameState
import kotlin.math.*

/**
 * 【RoadGeometry】
 * 3D投影エンジン。計算負荷を最小限に抑えた高速投影。
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
        val wHalf = w * 0.5f
        
        val resStepFactor = GameSettings.getResolutionStepFactor()
        val maxSamples = (GamePerformanceSettings.ROAD_QUALITY / resStepFactor).toInt().coerceIn(200, MAX_SAMPLES)
        
        val cosH = cos(state.playerWorldHeading.toDouble()).toFloat()
        val sinH = sin(state.playerWorldHeading.toDouble()).toFloat()
        
        val camWorldX = state.playerWorldX + (state.camXOffset * cosH + state.camZOffset * sinH)
        val camWorldZ = state.playerWorldZ + (-state.camXOffset * sinH + state.camZOffset * cosH)
        val camWorldY = state.playerWorldY + GameSettings.CAMERA_HEIGHT + state.camYOffset
        
        val camAbsH = state.playerWorldHeading + state.camYawOffset
        val camAbsP = state.cameraPitch + state.camPitchOffset
        
        val cosCamH = cos(camAbsH.toDouble()).toFloat()
        val sinCamH = sin(camAbsH.toDouble()).toFloat()
        val cosCamP = cos(camAbsP.toDouble()).toFloat()
        val sinCamP = sin(camAbsP.toDouble()).toFloat()

        var sampleCount = 0
        var currentRelDist = -50f

        val oppOffsetM = roadW + (GameSettings.SHOULDER_WIDTH_RIGHT * 2f) + GameSettings.MEDIAN_WIDTH
        val halfW = roadW * 0.5f
        val lineW = GameSettings.LANE_MARKER_WIDTH
        val sL = GameSettings.SHOULDER_WIDTH_LEFT
        val sR = GameSettings.SHOULDER_WIDTH_RIGHT

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

            // 基準点の差分
            val dx0 = rWorldX - camWorldX
            val dz0 = rWorldZ - camWorldZ
            val dy0 = rWorldY - camWorldY
            
            // 基準点のカメラ回転
            val rx0 = dx0 * cosCamH - dz0 * sinCamH
            val rzTmp0 = dx0 * sinCamH + dz0 * cosCamH
            val ry0 = dy0 * cosCamP - rzTmp0 * sinCamP
            val rz0 = dy0 * sinCamP + rzTmp0 * cosCamP
            
            if (rz0 <= 1.0f) {
                currentRelDist += dz
                continue
            }

            // 道路方向ベクトル(perp)の投影係数
            val perpX = cos(rH.toDouble()).toFloat()
            val perpZ = -sin(rH.toDouble()).toFloat()
            
            // perpベクトルのカメラ回転成分
            val prx = perpX * cosCamH - perpZ * sinCamH
            val przTmp = perpX * sinCamH + perpZ * cosCamH
            val pry = -przTmp * sinCamP
            val prz = przTmp * cosCamP

            fun getXFast(offsetX: Float): Float {
                val rz = rz0 + prz * offsetX
                if (rz <= 0.1f) return wHalf
                val scale = fov / rz
                return wHalf + (rx0 + prx * offsetX) * scale * stretch
            }

            centerX[sampleCount] = wHalf + rx0 * (fov / rz0) * stretch
            yCoords[sampleCount] = horizon - ry0 * (fov / rz0)
            zCoords[sampleCount] = currentRelDist

            leftInnerX[sampleCount] = getXFast(-halfW)
            rightInnerX[sampleCount] = getXFast(halfW)
            leftOuterX[sampleCount] = getXFast(-halfW - lineW)
            rightOuterX[sampleCount] = getXFast(halfW + lineW)
            leftShoulderX[sampleCount] = getXFast(-halfW - lineW - sL)
            rightShoulderX[sampleCount] = getXFast(halfW + lineW + sR)

            oppCenterX[sampleCount] = getXFast(oppOffsetM)
            oppLeftShoulderX[sampleCount] = getXFast(oppOffsetM - halfW - lineW - sR)
            oppRightShoulderX[sampleCount] = getXFast(oppOffsetM + halfW + lineW + sL)

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
