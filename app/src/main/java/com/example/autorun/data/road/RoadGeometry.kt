package com.example.autorun.data.road

import com.example.autorun.config.GamePerformanceSettings
import com.example.autorun.config.GameSettings
import com.example.autorun.core.CourseManager
import com.example.autorun.core.GameState
import kotlin.math.*

/**
 * 【RoadGeometry】
 * 3D投影エンジン。自車の後方も含め、自由なカメラ位置・角度から投影を行う。
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
        
        // カメラの絶対位置 (自車位置 + オフセット)
        val cosH = cos(state.playerWorldHeading.toDouble()).toFloat()
        val sinH = sin(state.playerWorldHeading.toDouble()).toFloat()
        
        val camWorldX = state.playerWorldX + (state.camXOffset * cosH + state.camZOffset * sinH)
        val camWorldZ = state.playerWorldZ + (-state.camXOffset * sinH + state.camZOffset * cosH)
        val camWorldY = state.playerWorldY + GameSettings.CAMERA_HEIGHT + state.camYOffset
        
        // カメラの絶対方位 (車体方位 + オフセット)
        val camAbsH = state.playerWorldHeading + state.camYawOffset
        val camAbsP = state.cameraPitch + state.camPitchOffset
        
        val cosCamH = cos(camAbsH.toDouble()).toFloat()
        val sinCamH = sin(camAbsH.toDouble()).toFloat()
        val cosCamP = cos(camAbsP.toDouble()).toFloat()
        val sinCamP = sin(camAbsP.toDouble()).toFloat()

        var sampleCount = 0
        var currentRelDist = -50f // カメラ自由移動のため、より後ろから描画

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

            fun projectPoint(wx: Float, wz: Float, wy: Float): Pair<Float, Float>? {
                val dx = wx - camWorldX
                val dz_ = wz - camWorldZ
                val dy = wy - camWorldY
                
                // 方位回転
                val rx = dx * cosCamH - dz_ * sinCamH
                val rzTmp = dx * sinCamH + dz_ * cosCamH
                
                // ピッチ回転
                val ryFinal = dy * cosCamP - rzTmp * sinCamP
                val rzFinal = dy * sinCamP + rzTmp * cosCamP
                
                if (rzFinal <= 1.0f) return null
                
                val scale = fov / rzFinal
                return Pair((w * 0.5f) + rx * scale * stretch, horizon - ryFinal * scale)
            }

            val centerProj = projectPoint(rWorldX, rWorldZ, rWorldY)
            if (centerProj == null) {
                currentRelDist += dz
                continue
            }
            
            centerX[sampleCount] = centerProj.first
            yCoords[sampleCount] = centerProj.second
            zCoords[sampleCount] = currentRelDist

            val perpX = cos(rH.toDouble()).toFloat()
            val perpZ = -sin(rH.toDouble()).toFloat()
            val halfW = roadW * 0.5f
            val lineW = GameSettings.LANE_MARKER_WIDTH
            val sL = GameSettings.SHOULDER_WIDTH_LEFT
            val sR = GameSettings.SHOULDER_WIDTH_RIGHT

            fun getX(offsetX: Float): Float {
                return projectPoint(rWorldX + perpX * offsetX, rWorldZ + perpZ * offsetX, rWorldY)?.first ?: centerX[sampleCount]
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
