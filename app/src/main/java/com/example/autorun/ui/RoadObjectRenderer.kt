package com.example.autorun.ui

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.example.autorun.config.DeveloperSettings
import com.example.autorun.config.GameSettings
import com.example.autorun.core.CourseManager
import com.example.autorun.data.road.RoadObjectData
import kotlin.math.*

/**
 * 【RoadObjectRenderer】
 * 道路付帯オブジェクトの描画を専門に行うクラス。
 * 頂点配列による一括描画で高速化。
 */
object RoadObjectRenderer {
    private val guardrailPlatePaint = Paint().apply { style = Paint.Style.FILL; isAntiAlias = true }
    private val guardrailPostPaint = Paint().apply { style = Paint.Style.FILL; isAntiAlias = true }
    private val reflectorPaint = Paint().apply { style = Paint.Style.FILL; isAntiAlias = true }
    private val antiGlarePaint = Paint().apply { style = Paint.Style.FILL; isAntiAlias = true }
    private val antiGlarePostPaint = Paint().apply { style = Paint.Style.FILL; isAntiAlias = true }
    private val mirrorFillPaint = Paint().apply { style = Paint.Style.FILL; isAntiAlias = true }
    private val signBoardPaint = Paint().apply { style = Paint.Style.FILL; isAntiAlias = true }
    private val signBorderPaint = Paint().apply { style = Paint.Style.STROKE; strokeWidth = 3f; isAntiAlias = true }

    // 高速描画用バッファ
    private const val MAX_FLOAT_COUNT = 128000
    private val leftVertices = FloatArray(MAX_FLOAT_COUNT)
    private val rightVertices = FloatArray(MAX_FLOAT_COUNT)
    private val postVertices = FloatArray(MAX_FLOAT_COUNT)
    
    private var leftVertexCount = 0
    private var rightVertexCount = 0
    private var postVertexCount = 0

    // 標識などの静的オブジェクトは数が少ないためリストを維持するが、再利用を検討
    private class SignData(val x: Float, val y: Float, val w: Float, val h: Float, val poleX: Float, val poleW: Float, val poleH: Float)
    private class AntiGlarePlateData(val x: Float, val y: Float, val w: Float, val h: Float, val postX: Float, val postW: Float, val postH: Float)
    
    private val antiGlareList = mutableListOf<AntiGlarePlateData>()
    private val signList = mutableListOf<SignData>()

    private var currentSampleCount = 0
    private var cachedDayFactor = 0.5f

    fun reset() {
        leftVertexCount = 0; rightVertexCount = 0; postVertexCount = 0
        antiGlareList.clear(); signList.clear()
        ShadowRenderer.reset(); RoadObjectData.clear(); currentSampleCount = 0
    }

    private fun applyNightDim(paint: Paint, baseColor: Int, dayFactor: Float) {
        val b = (0.2f + 0.8f * dayFactor).coerceIn(0.1f, 1.0f)
        paint.color = Color.rgb((Color.red(baseColor) * b).toInt(), (Color.green(baseColor) * b).toInt(), (Color.blue(baseColor) * b).toInt())
    }

    fun draw(canvas: Canvas, dayFactor: Float) {
        cachedDayFactor = dayFactor
        applyNightDim(guardrailPlatePaint, GameSettings.COLOR_GUARDRAIL_PLATE, dayFactor)
        applyNightDim(guardrailPostPaint, GameSettings.COLOR_GUARDRAIL_POST, dayFactor)
        applyNightDim(reflectorPaint, GameSettings.COLOR_REFLECTOR, dayFactor)
        applyNightDim(antiGlarePaint, Color.parseColor("#004D40"), dayFactor)
        applyNightDim(antiGlarePostPaint, Color.parseColor("#CCCCCC"), dayFactor)
        applyNightDim(mirrorFillPaint, GameSettings.COLOR_GUARDRAIL_PLATE, dayFactor)
        applyNightDim(signBoardPaint, Color.parseColor("#007A33"), dayFactor)
        applyNightDim(signBorderPaint, Color.WHITE, dayFactor)

        ShadowRenderer.draw(canvas)
        
        // ミラー塗りつぶし
        if (currentSampleCount > 0) {
            CurveMirrorFiller.drawIntersectionFill(canvas, RoadObjectData.rightMirrorUpperX, RoadObjectData.rightMirrorUpperY, RoadObjectData.rightMirrorLowerX, RoadObjectData.rightMirrorLowerY, currentSampleCount, mirrorFillPaint, true)
            CurveMirrorFiller.drawIntersectionFill(canvas, RoadObjectData.leftMirrorUpperX, RoadObjectData.leftMirrorUpperY, RoadObjectData.leftMirrorLowerX, RoadObjectData.leftMirrorLowerY, currentSampleCount, mirrorFillPaint, false)
        }

        // 標識描画 (数は少ないため通常のdrawRect)
        for (i in signList.indices) {
            val s = signList[i]
            canvas.drawRect(s.poleX, s.y - s.poleH, s.poleX + s.poleW, s.y, antiGlarePostPaint)
            canvas.drawRect(s.x, s.y - s.poleH, s.x + s.w, s.y - s.poleH + s.h, signBoardPaint)
            canvas.drawRect(s.x, s.y - s.poleH, s.x + s.w, s.y - s.poleH + s.h, signBorderPaint)
        }

        // 遮光板描画
        for (i in antiGlareList.indices) {
            val p = antiGlareList[i]
            canvas.drawRect(p.postX, p.y, p.postX + p.postW, p.y + p.postH, antiGlarePostPaint)
            canvas.drawRect(p.x, p.y - p.h, p.x + p.w, p.y, antiGlarePaint)
        }

        // ガードレール支柱の一括描画
        if (DeveloperSettings.showGuardrailPosts && postVertexCount > 0) {
            canvas.drawVertices(Canvas.VertexMode.TRIANGLES, postVertexCount, postVertices, 0, null, 0, null, 0, null, 0, 0, guardrailPostPaint)
        }

        // ガードレール本体の一括描画
        if (leftVertexCount > 0) canvas.drawVertices(Canvas.VertexMode.TRIANGLES, leftVertexCount, leftVertices, 0, null, 0, null, 0, null, 0, 0, guardrailPlatePaint)
        if (rightVertexCount > 0) canvas.drawVertices(Canvas.VertexMode.TRIANGLES, rightVertexCount, rightVertices, 0, null, 0, null, 0, null, 0, 0, guardrailPlatePaint)
    }

    fun addGuardrailSegment(lx1: Float, lx2: Float, rx1: Float, rx2: Float, y1: Float, y2: Float, y1B: Float, y2B: Float, sunH: Float) {
        fun addV(arr: FloatArray, count: Int, x1: Float, x2: Float, y1: Float, y2: Float, y1b: Float, y2b: Float): Int {
            var c = count
            if (c + 12 > MAX_FLOAT_COUNT) return c
            arr[c++] = x1;  arr[c++] = y1b
            arr[c++] = x2;  arr[c++] = y2b
            arr[c++] = x2;  arr[c++] = y2
            arr[c++] = x1;  arr[c++] = y1b
            arr[c++] = x2;  arr[c++] = y2
            arr[c++] = x1;  arr[c++] = y1
            return c
        }
        val sc = (y1B - y1) / GameSettings.GUARDRAIL_PLATE_HEIGHT; val scP = (y2B - y2) / GameSettings.GUARDRAIL_PLATE_HEIGHT
        val off = 0.12f * GameSettings.ROAD_STRETCH; val offL = off * sc; val offLP = off * scP; val offR = -off * sc; val offRP = -off * scP
        leftVertexCount = addV(leftVertices, leftVertexCount, lx1 + offL, lx2 + offLP, y1, y2, y1B, y2B)
        rightVertexCount = addV(rightVertices, rightVertexCount, rx1 + offR, rx2 + offRP, y1, y2, y1B, y2B)
    }

    fun addPostPath(pDist: Float, y: Float, z: Float, zP: Float, lX: Float, rX: Float, topY: Float, sunH: Float, isOpp: Boolean) {
        val sc = GameSettings.FOV / (z + 0.1f); val pInt = GameSettings.GUARDRAIL_POST_INTERVAL; val dP = pDist + zP; val dC = pDist + z
        if ((dP / pInt).toInt() != (dC / pInt).toInt()) {
            val pW = GameSettings.GUARDRAIL_POST_DIAMETER * sc * GameSettings.ROAD_STRETCH; val pH = y - topY
            if (DeveloperSettings.showGuardrailPosts && postVertexCount + 12 <= MAX_FLOAT_COUNT) {
                val px = if (!isOpp) lX - pW else rX
                var c = postVertexCount
                postVertices[c++] = px;      postVertices[c++] = y
                postVertices[c++] = px + pW; postVertices[c++] = y
                postVertices[c++] = px + pW; postVertices[c++] = y - pH
                postVertices[c++] = px;      postVertices[c++] = y
                postVertices[c++] = px + pW; postVertices[c++] = y - pH
                postVertices[c++] = px;      postVertices[c++] = y - pH
                postVertexCount = c
            }
        }
        
        // 標識 (低頻度のためリスト管理)
        if (!isOpp) {
            for (obj in CourseManager.getRoadObjects()) {
                val p1k = obj.posKm * 1000f - 1000f; val p500 = obj.posKm * 1000f - 500f
                if ((dP < p1k && dC >= p1k) || (dP < p500 && dC >= p500)) {
                    val sW = 4.5f * sc * GameSettings.ROAD_STRETCH; val sH = 3.0f * sc; val pH = 7.0f * sc; val pW = 0.3f * sc * GameSettings.ROAD_STRETCH; val bx = lX - 2.0f * sc * GameSettings.ROAD_STRETCH - sW
                    signList.add(SignData(bx, y, sW, sH, bx + sW * 0.5f - pW * 0.5f, pW, pH))
                }
            }
        }

        // 遮光板
        if ((dP / 1.0f).toInt() != (dC / 1.0f).toInt()) {
            val pW = 0.15f * sc * GameSettings.ROAD_STRETCH; val pH = 0.40f * sc; val tY = y - 1.6f * sc; val sW = 0.08f * sc * GameSettings.ROAD_STRETCH; val sH = 1.6f * sc - pH
            if (!isOpp) { val mHW = (GameSettings.MEDIAN_WIDTH * 0.5f) * sc * GameSettings.ROAD_STRETCH; val bX = rX + mHW - sW * 0.5f; antiGlareList.add(AntiGlarePlateData(bX + sW * 0.5f - pW * 0.5f, tY + pH, pW, pH, bX, sW, sH)) }
        }
    }

    fun updateMirrorSample(idx: Int, rx1: Float, ry1: Float, rx2: Float, ry2: Float, lx1: Float, ly1: Float, lx2: Float, ly2: Float) {
        RoadObjectData.rightMirrorUpperX[idx] = rx1; RoadObjectData.rightMirrorUpperY[idx] = ry1; RoadObjectData.rightMirrorLowerX[idx] = rx2; RoadObjectData.rightMirrorLowerY[idx] = ry2
        RoadObjectData.leftMirrorUpperX[idx] = lx1; RoadObjectData.leftMirrorUpperY[idx] = ly1; RoadObjectData.leftMirrorLowerX[idx] = lx2; RoadObjectData.leftMirrorLowerY[idx] = ly2
        currentSampleCount = idx + 1
    }
}
