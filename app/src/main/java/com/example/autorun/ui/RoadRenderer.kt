package com.example.autorun.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
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
 * 高速ポリゴン描画(drawVertices)を採用した最速レンダラー。
 */
object RoadRenderer {
    private val asphaltPaint = Paint().apply { style = Paint.Style.FILL; isAntiAlias = true }
    private val whitePaint = Paint().apply { style = Paint.Style.FILL; isAntiAlias = true }
    private val concretePaint = Paint().apply { style = Paint.Style.FILL; isAntiAlias = true }
    private val skidMarkPaint = Paint().apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; isAntiAlias = true }
    private val headlightPaint = Paint().apply { style = Paint.Style.FILL; isAntiAlias = true }

    private const val MAX_VERTICES = RoadGeometry.MAX_SAMPLES * 6 * 2
    private val roadVertices = FloatArray(MAX_VERTICES)
    private val medianVertices = FloatArray(MAX_VERTICES)
    private val rumbleVertices = FloatArray(MAX_VERTICES)
    private val laneVertices = FloatArray(MAX_VERTICES)

    private var colorConcreteDefault = Color.parseColor("#999999")
    private var lastDayFactor = -1f

    fun init(context: Context) {
        val colorConcreteStr = context.getString(R.string.color_concrete)
        colorConcreteDefault = Color.parseColor(colorConcreteStr)
        asphaltPaint.color = GameSettings.COLOR_ROAD_DARK
        whitePaint.color = GameSettings.COLOR_LANE
        skidMarkPaint.color = GameSettings.COLOR_SKIDMARK
        concretePaint.color = colorConcreteDefault
    }

    fun draw(canvas: Canvas, w: Float, h: Float, state: GameState, horizon: Float) {
        val calendar = Calendar.getInstance()
        val secondsOfDay = calendar.get(Calendar.HOUR_OF_DAY) * 3600 +
                           calendar.get(Calendar.MINUTE) * 60 +
                           calendar.get(Calendar.SECOND)
        val sunAngle = (secondsOfDay.toDouble() / 86400.0 * 2.0 * PI - PI / 2.0).toFloat()
        val dayFactor = (sin(sunAngle).coerceIn(-1f, 1f) + 1f) / 2f
        
        if (abs(dayFactor - lastDayFactor) > 0.005f) {
            updatePaintsBrightness(dayFactor)
            lastDayFactor = dayFactor
        }

        val sampleCount = RoadGeometry.compute(w, h, state, horizon)
        if (sampleCount < 2) return

        RoadObjectRenderer.reset()
        val playerDist = state.playerDistance
        val fov = GameSettings.FOV
        val stretch = GameSettings.ROAD_STRETCH
        val laneMarkerW = GameSettings.LANE_MARKER_WIDTH
        val dashLen = GameSettings.LANE_DASH_LENGTH.toFloat()
        val totalPattern = dashLen + GameSettings.LANE_GAP_LENGTH.toFloat()
        val sunRelHeading = GameSettings.SUN_WORLD_HEADING - state.playerHeading

        var rvIdx = 0; var mvIdx = 0; var ruvIdx = 0; var lvIdx = 0

        for (i in 1 until sampleCount) {
            val y = RoadGeometry.yCoords[i]; val yP = RoadGeometry.yCoords[i - 1]
            val z = RoadGeometry.zCoords[i]; val scale = fov / (z + 0.1f)
            val zP = RoadGeometry.zCoords[i - 1]; val scaleP = fov / (zP + 0.1f)

            fun addRect(arr: FloatArray, idx: Int, x1: Float, x2: Float, x1P: Float, x2P: Float): Int {
                var cur = idx
                if (cur + 12 > MAX_VERTICES) return cur
                arr[cur++] = x1;  arr[cur++] = y
                arr[cur++] = x1P; arr[cur++] = yP
                arr[cur++] = x2P; arr[cur++] = yP
                arr[cur++] = x1;  arr[cur++] = y
                arr[cur++] = x2P; arr[cur++] = yP
                arr[cur++] = x2;  arr[cur++] = y
                return cur
            }

            rvIdx = addRect(roadVertices, rvIdx, RoadGeometry.leftShoulderX[i], RoadGeometry.rightShoulderX[i], RoadGeometry.leftShoulderX[i-1], RoadGeometry.rightShoulderX[i-1])
            rvIdx = addRect(roadVertices, rvIdx, RoadGeometry.oppLeftShoulderX[i], RoadGeometry.oppRightShoulderX[i], RoadGeometry.oppLeftShoulderX[i-1], RoadGeometry.oppRightShoulderX[i-1])
            rvIdx = addRect(roadVertices, rvIdx, RoadGeometry.rightShoulderX[i], RoadGeometry.oppLeftShoulderX[i], RoadGeometry.rightShoulderX[i-1], RoadGeometry.oppLeftShoulderX[i-1])

            val mHW = (GameSettings.MEDIAN_WIDTH * scale * stretch) * 0.5f
            val mHWP = (GameSettings.MEDIAN_WIDTH * scaleP * stretch) * 0.5f
            val mCX = (RoadGeometry.rightShoulderX[i] + RoadGeometry.oppLeftShoulderX[i]) * 0.5f
            val mCXP = (RoadGeometry.rightShoulderX[i-1] + RoadGeometry.oppLeftShoulderX[i-1]) * 0.5f
            mvIdx = addRect(medianVertices, mvIdx, mCX - mHW, mCX + mHW, mCXP - mHWP, mCXP + mHWP)

            ruvIdx = addRect(rumbleVertices, ruvIdx, RoadGeometry.leftOuterX[i], RoadGeometry.leftInnerX[i], RoadGeometry.leftOuterX[i-1], RoadGeometry.leftInnerX[i-1])
            ruvIdx = addRect(rumbleVertices, ruvIdx, RoadGeometry.rightInnerX[i], RoadGeometry.rightOuterX[i], RoadGeometry.rightInnerX[i-1], RoadGeometry.rightOuterX[i-1])

            if ((playerDist + zP) % totalPattern < dashLen) {
                val dW = (laneMarkerW * scale * stretch) * 0.5f
                val dWP = (laneMarkerW * scaleP * stretch) * 0.5f
                lvIdx = addRect(laneVertices, lvIdx, RoadGeometry.centerX[i] - dW, RoadGeometry.centerX[i] + dW, RoadGeometry.centerX[i-1] - dWP, RoadGeometry.centerX[i-1] + dWP)
                lvIdx = addRect(laneVertices, lvIdx, RoadGeometry.oppCenterX[i] - dW, RoadGeometry.oppCenterX[i] + dW, RoadGeometry.oppCenterX[i-1] - dWP, RoadGeometry.oppCenterX[i-1] + dWP)
            }
            
            RoadObjectRenderer.addPostPath(playerDist, y, z, zP, RoadGeometry.leftShoulderX[i], RoadGeometry.rightShoulderX[i], y - GameSettings.GUARDRAIL_HEIGHT * scale, sunRelHeading, false)
            RoadObjectRenderer.addPostPath(playerDist, y, z, zP, RoadGeometry.oppLeftShoulderX[i], RoadGeometry.oppRightShoulderX[i], y - GameSettings.GUARDRAIL_HEIGHT * scale, sunRelHeading, true)
        }

        if (rvIdx > 0) canvas.drawVertices(Canvas.VertexMode.TRIANGLES, rvIdx, roadVertices, 0, null, 0, null, 0, null, 0, 0, asphaltPaint)
        if (mvIdx > 0) canvas.drawVertices(Canvas.VertexMode.TRIANGLES, mvIdx, medianVertices, 0, null, 0, null, 0, null, 0, 0, concretePaint)
        if (ruvIdx > 0) canvas.drawVertices(Canvas.VertexMode.TRIANGLES, ruvIdx, rumbleVertices, 0, null, 0, null, 0, null, 0, 0, whitePaint)
        if (lvIdx > 0) canvas.drawVertices(Canvas.VertexMode.TRIANGLES, lvIdx, laneVertices, 0, null, 0, null, 0, null, 0, 0, whitePaint)

        if (dayFactor < 0.4f) drawHeadlights(canvas, w, h, dayFactor)
        drawSkidMarks(canvas, w, state, playerDist)

        val postInt = GameSettings.GUARDRAIL_POST_INTERVAL
        val startP = (playerDist / postInt).toInt()
        val endP = ((playerDist + GameSettings.DRAW_DISTANCE) / postInt).toInt()
        for (pIdx in endP downTo startP) {
            val wZ1 = pIdx * postInt; val z1 = wZ1 - playerDist; val z2 = z1 + postInt + GameSettings.GUARDRAIL_SLEEVE_WIDTH
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
        RoadObjectRenderer.draw(canvas, dayFactor)
    }

    private fun updatePaintsBrightness(dayFactor: Float) {
        val b = (0.2f + 0.8f * dayFactor).coerceIn(0.1f, 1.0f)
        fun applyB(c: Int): Int = Color.rgb((Color.red(c) * b).toInt(), (Color.green(c) * b).toInt(), (Color.blue(c) * b).toInt())
        asphaltPaint.color = applyB(GameSettings.COLOR_ROAD_DARK)
        whitePaint.color = applyB(GameSettings.COLOR_LANE)
        concretePaint.color = applyB(colorConcreteDefault)
        skidMarkPaint.color = applyB(GameSettings.COLOR_SKIDMARK)
    }

    private fun drawHeadlights(canvas: Canvas, w: Float, h: Float, dayFactor: Float) {
        val alpha = ((0.4f - dayFactor) / 0.4f * 120).toInt().coerceIn(0, 150)
        val centerX = w * 0.5f; val centerY = h * 0.95f
        val radiusW = w * 0.6f
        headlightPaint.shader = RadialGradient(centerX, centerY, radiusW, 
            intArrayOf(Color.argb(alpha, 255, 255, 200), Color.TRANSPARENT), 
            floatArrayOf(0.3f, 1.0f), Shader.TileMode.CLAMP)
        canvas.save(); canvas.scale(1f, 0.4f, centerX, centerY)
        canvas.drawCircle(centerX, centerY, radiusW, headlightPaint); canvas.restore()
    }

    private fun drawSkidMarks(canvas: Canvas, w: Float, state: GameState, playerDist: Float) {
        if (state.skidMarks.size < 2) return
        val tHW = 0.75f; val str = GameSettings.ROAD_STRETCH; val tW = 0.22f
        var pM: GameState.SkidMark? = null; var pL: Pair<Float, Float>? = null; var pR: Pair<Float, Float>? = null
        for (mark in state.skidMarks) {
            val z = mark.distance - playerDist
            if (z < 0.1f || z > GameSettings.DRAW_DISTANCE) { pM = null; pL = null; pR = null; }
            else {
                val res = RoadGeometry.interpolate(z)
                if (res == null) { pM = null; pL = null; pR = null; }
                else {
                    val sCX = res.centerX + (mark.playerX - state.playerX) * res.scale * str
                    val oP = tHW * res.scale * str; val cLX = sCX - oP; val cRX = sCX + oP; val cY = res.y
                    if (pM != null && mark.opacity > 0f && pM!!.opacity > 0f) {
                        skidMarkPaint.alpha = (mark.opacity * 180).toInt().coerceIn(0, 255)
                        skidMarkPaint.strokeWidth = tW * res.scale * str
                        pL?.let { canvas.drawLine(it.first, it.second, cLX, cY, skidMarkPaint) }
                        pR?.let { canvas.drawLine(it.first, it.second, cRX, cY, skidMarkPaint) }
                    }
                    pL = Pair(cLX, cY); pR = Pair(cRX, cY); pM = mark
                }
            }
        }
    }
}
