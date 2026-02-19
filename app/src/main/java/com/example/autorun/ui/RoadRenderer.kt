package com.example.autorun.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import com.example.autorun.R
import com.example.autorun.config.GameSettings
import com.example.autorun.core.GameState
import com.example.autorun.data.road.RoadGeometry
import java.util.Calendar
import kotlin.math.*

/**
 * 【RoadRenderer】
 * 道路のビジュアルを描画し、同時に付帯オブジェクトの座標をRendererに送る。
 */
object RoadRenderer {
    private val asphaltPaint = Paint().apply {
        color = GameSettings.COLOR_ROAD_DARK
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    private val whitePaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    private val concretePaint = Paint().apply {
        color = Color.parseColor("#999999")
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    private val skidMarkPaint = Paint().apply {
        color = GameSettings.COLOR_SKIDMARK
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        isAntiAlias = true
    }
    
    // ヘッドライト用のPaint
    private val headlightPaint = Paint().apply {
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val roadPath = Path().apply { fillType = Path.FillType.WINDING }
    private val medianConcretePath = Path().apply { fillType = Path.FillType.WINDING }
    private val rumblePath = Path().apply { fillType = Path.FillType.WINDING }
    private val lanePath = Path().apply { fillType = Path.FillType.WINDING }

    fun init(context: Context) {
        val colorConcrete = context.getString(R.string.color_concrete)
        concretePaint.color = Color.parseColor(colorConcrete)
        asphaltPaint.color = GameSettings.COLOR_ROAD_DARK
        whitePaint.color = GameSettings.COLOR_LANE
        skidMarkPaint.color = GameSettings.COLOR_SKIDMARK
    }

    fun draw(canvas: Canvas, w: Float, h: Float, state: GameState, horizon: Float) {
        // 時刻による明るさの計算
        val calendar = Calendar.getInstance()
        val secondsOfDay = calendar.get(Calendar.HOUR_OF_DAY) * 3600 +
                           calendar.get(Calendar.MINUTE) * 60 +
                           calendar.get(Calendar.SECOND)
        val sunAngle = (secondsOfDay.toDouble() / 84600.0 * 2.0 * PI - PI).toFloat()
        val dayFactor = (sin(sunAngle).coerceIn(-1f, 1f) + 1f) / 2f
        
        // 道路の色を時刻に合わせて暗くする
        updatePaintsBrightness(dayFactor)

        val sampleCount = RoadGeometry.compute(w, h, state, horizon)
        if (sampleCount < 2) return

        roadPath.reset()
        medianConcretePath.reset()
        rumblePath.reset()
        lanePath.reset()
        RoadObjectRenderer.reset()

        val playerDist = state.playerDistance
        val fov = GameSettings.FOV
        val stretch = GameSettings.ROAD_STRETCH
        val laneMarkerW = GameSettings.LANE_MARKER_WIDTH
        val dashLen = GameSettings.LANE_DASH_LENGTH.toFloat()
        val totalPattern = dashLen + GameSettings.LANE_GAP_LENGTH.toFloat()
        val sunRelHeading = GameSettings.SUN_WORLD_HEADING - state.playerHeading

        val yCoords = RoadGeometry.yCoords
        val zCoords = RoadGeometry.zCoords
        val leftShoulderX = RoadGeometry.leftShoulderX
        val rightShoulderX = RoadGeometry.rightShoulderX
        val leftOuterX = RoadGeometry.leftOuterX
        val leftInnerX = RoadGeometry.leftInnerX
        val rightInnerX = RoadGeometry.rightInnerX
        val rightOuterX = RoadGeometry.rightOuterX
        val centerX = RoadGeometry.centerX
        val oppLeftShoulderX = RoadGeometry.oppLeftShoulderX
        val oppRightShoulderX = RoadGeometry.oppRightShoulderX
        val oppCenterX = RoadGeometry.oppCenterX

        for (i in 1 until sampleCount) {
            val y = yCoords[i]; val yPrev = yCoords[i - 1]
            val z = zCoords[i]; val scale = fov / (z + 0.1f); val scalePrev = fov / (zCoords[i-1] + 0.1f)

            roadPath.moveTo(leftShoulderX[i], y); roadPath.lineTo(rightShoulderX[i], y)
            roadPath.lineTo(rightShoulderX[i - 1], yPrev); roadPath.lineTo(leftShoulderX[i - 1], yPrev); roadPath.close()

            roadPath.moveTo(oppLeftShoulderX[i], y); roadPath.lineTo(oppRightShoulderX[i], y)
            roadPath.lineTo(oppRightShoulderX[i - 1], yPrev); roadPath.lineTo(oppLeftShoulderX[i - 1], yPrev); roadPath.close()

            roadPath.moveTo(rightShoulderX[i], y); roadPath.lineTo(oppLeftShoulderX[i], y)
            roadPath.lineTo(oppLeftShoulderX[i - 1], yPrev); roadPath.lineTo(rightShoulderX[i - 1], yPrev); roadPath.close()

            val medianCenterX = (rightShoulderX[i] + oppLeftShoulderX[i]) / 2f
            val medianCenterXPrev = (rightShoulderX[i-1] + oppLeftShoulderX[i-1]) / 2f
            val mHalfW = (GameSettings.MEDIAN_WIDTH * scale * stretch) / 2f
            val mHalfWPrev = (GameSettings.MEDIAN_WIDTH * scalePrev * stretch) / 2f
            medianConcretePath.moveTo(medianCenterX - mHalfW, y); medianConcretePath.lineTo(medianCenterX + mHalfW, y)
            medianConcretePath.lineTo(medianCenterXPrev + mHalfWPrev, yPrev); medianConcretePath.lineTo(medianCenterXPrev - mHalfWPrev, yPrev); medianConcretePath.close()

            rumblePath.moveTo(leftOuterX[i], y); rumblePath.lineTo(leftInnerX[i], y)
            rumblePath.lineTo(leftInnerX[i - 1], yPrev); rumblePath.lineTo(leftOuterX[i - 1], yPrev); rumblePath.close()
            rumblePath.moveTo(rightInnerX[i], y); rumblePath.lineTo(rightOuterX[i], y)
            rumblePath.lineTo(rightOuterX[i - 1], yPrev); rumblePath.lineTo(rightInnerX[i - 1], yPrev); rumblePath.close()

            if ((playerDist + zCoords[i-1]) % totalPattern < dashLen) {
                val dashHalfW = (laneMarkerW * scale * stretch) / 2f
                val dashHalfWPrev = (laneMarkerW * scalePrev * stretch) / 2f
                lanePath.moveTo(centerX[i] - dashHalfW, y); lanePath.lineTo(centerX[i] + dashHalfW, y)
                lanePath.lineTo(centerX[i - 1] + dashHalfWPrev, yPrev); lanePath.lineTo(centerX[i - 1] - dashHalfWPrev, yPrev); lanePath.close()
                lanePath.moveTo(oppCenterX[i] - dashHalfW, y); lanePath.lineTo(oppCenterX[i] + dashHalfW, y)
                lanePath.lineTo(oppCenterX[i - 1] + dashHalfWPrev, yPrev); lanePath.lineTo(oppCenterX[i - 1] - dashHalfWPrev, yPrev); lanePath.close()
            }
            
            val h_ = GameSettings.GUARDRAIL_HEIGHT * scale
            RoadObjectRenderer.addPostPath(playerDist, y, z, zCoords[i-1], leftShoulderX[i], rightShoulderX[i], y - h_, sunRelHeading, isOpposite = false)
            RoadObjectRenderer.addPostPath(playerDist, y, z, zCoords[i-1], oppLeftShoulderX[i], oppRightShoulderX[i], y - h_, sunRelHeading, isOpposite = true)
        }

        canvas.drawPath(roadPath, asphaltPaint)
        canvas.drawPath(medianConcretePath, concretePaint)
        canvas.drawPath(rumblePath, whitePaint)
        canvas.drawPath(lanePath, whitePaint)

        // --- ヘッドライト描画 (夜間のみ) ---
        if (dayFactor < 0.4f) {
            drawHeadlights(canvas, w, h, dayFactor)
        }

        drawSkidMarks(canvas, w, state, playerDist)

        val postInterval = GameSettings.GUARDRAIL_POST_INTERVAL
        val startPostIdx = (playerDist / postInterval).toInt()
        val endPostIdx = ((playerDist + GameSettings.DRAW_DISTANCE) / postInterval).toInt()
        for (pIdx in endPostIdx downTo startPostIdx) {
            val worldZStart = pIdx * postInterval; val z1 = worldZStart - playerDist; val z2 = z1 + postInterval + GameSettings.GUARDRAIL_SLEEVE_WIDTH
            if (z1 < 0 && z2 < 0) continue
            if (z1 > GameSettings.DRAW_DISTANCE) continue
            val p1 = RoadGeometry.interpolate(z1.coerceAtLeast(0.1f)); val p2 = RoadGeometry.interpolate(z2.coerceAtMost(GameSettings.DRAW_DISTANCE.toFloat()))
            if (p1 != null && p2 != null) {
                val h1 = GameSettings.GUARDRAIL_HEIGHT * p1.scale; val h2 = GameSettings.GUARDRAIL_HEIGHT * p2.scale
                val ph1 = GameSettings.GUARDRAIL_PLATE_HEIGHT * p1.scale; val ph2 = GameSettings.GUARDRAIL_PLATE_HEIGHT * p2.scale
                RoadObjectRenderer.addGuardrailSegment(p1.lx, p2.lx, p1.lx, p2.lx, p1.y - h1, p2.y - h2, p1.y - h1 + ph1, p2.y - h2 + ph2, sunRelHeading)
                RoadObjectRenderer.addGuardrailSegment(p1.oppRx, p2.oppRx, p1.oppRx, p2.oppRx, p1.y - h1, p2.y - h2, p1.y - h1 + ph1, p2.y - h2 + ph2, sunRelHeading)
            }
        }
        RoadObjectRenderer.draw(canvas)
    }

    private fun updatePaintsBrightness(dayFactor: Float) {
        val b = (0.2f + 0.8f * dayFactor).coerceIn(0.1f, 1.0f)
        fun applyB(c: Int) = Color.rgb((Color.red(c) * b).toInt(), (Color.green(c) * b).toInt(), (Color.blue(c) * b).toInt())
        asphaltPaint.color = applyB(GameSettings.COLOR_ROAD_DARK)
        whitePaint.color = applyB(GameSettings.COLOR_LANE)
        concretePaint.color = applyB(Color.parseColor("#999999"))
        skidMarkPaint.color = applyB(GameSettings.COLOR_SKIDMARK)
    }

    private fun drawHeadlights(canvas: Canvas, w: Float, h: Float, dayFactor: Float) {
        val alpha = ((0.4f - dayFactor) / 0.4f * 120).toInt().coerceIn(0, 150)
        val centerX = w / 2f; val centerY = h * 0.95f
        val radiusW = w * 0.6f; val radiusH = h * 0.4f
        headlightPaint.shader = RadialGradient(centerX, centerY, radiusW, 
            intArrayOf(Color.argb(alpha, 255, 255, 200), Color.TRANSPARENT), 
            floatArrayOf(0.3f, 1.0f), Shader.TileMode.CLAMP)
        canvas.save(); canvas.scale(1f, 0.4f, centerX, centerY)
        canvas.drawCircle(centerX, centerY, radiusW, headlightPaint); canvas.restore()
    }

    private fun drawSkidMarks(canvas: Canvas, w: Float, state: GameState, playerDist: Float) {
        if (state.skidMarks.size < 2) return
        val treadHalfW = 0.75f; val stretch = GameSettings.ROAD_STRETCH; val tireW = 0.22f
        var prevMark: GameState.SkidMark? = null; var pPrevL: Pair<Float, Float>? = null; var pPrevR: Pair<Float, Float>? = null
        for (mark in state.skidMarks) {
            val z = mark.distance - playerDist
            if (z < 0.1f || z > GameSettings.DRAW_DISTANCE) { prevMark = null; pPrevL = null; pPrevR = null; continue }
            val res = RoadGeometry.interpolate(z)
            if (res == null) { prevMark = null; pPrevL = null; pPrevR = null; continue }
            val screenCenterX = res.centerX + (mark.playerX - state.playerX) * res.scale * stretch
            val offsetInPixels = treadHalfW * res.scale * stretch
            val curLX = screenCenterX - offsetInPixels; val curRX = screenCenterX + offsetInPixels; val curY = res.y
            if (prevMark != null && mark.opacity > 0f && prevMark.opacity > 0f) {
                val alpha = (mark.opacity * 180).toInt().coerceIn(0, 255)
                skidMarkPaint.alpha = alpha; skidMarkPaint.strokeWidth = tireW * res.scale * stretch
                pPrevL?.let { canvas.drawLine(it.first, it.second, curLX, curY, skidMarkPaint) }
                pPrevR?.let { canvas.drawLine(it.first, it.second, curRX, curY, skidMarkPaint) }
            }
            pPrevL = Pair(curLX, curY); pPrevR = Pair(curRX, curY); prevMark = mark
        }
    }
}
