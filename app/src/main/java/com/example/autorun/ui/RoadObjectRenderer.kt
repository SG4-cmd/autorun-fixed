package com.example.autorun.ui

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.example.autorun.config.DeveloperSettings
import com.example.autorun.config.GameSettings
import com.example.autorun.core.CourseManager
import com.example.autorun.data.road.RoadObjectData
import java.util.Calendar
import kotlin.math.*

/**
 * 【RoadObjectRenderer】
 * ガードレールやリフレクター等の道路付帯オブジェクトの描画を専門に行うクラス。
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

    private class PostData(val x: Float, val y: Float, val w: Float, val h: Float)
    private class ReflectorData(val x: Float, val y: Float, val radius: Float)
    private class AntiGlarePlateData(val x: Float, val y: Float, val w: Float, val h: Float, val postX: Float, val postW: Float, val postH: Float)
    private class SignData(val x: Float, val y: Float, val w: Float, val h: Float, val poleX: Float, val poleW: Float, val poleH: Float)
    
    private val postList = mutableListOf<PostData>()
    private val reflectorList = mutableListOf<ReflectorData>()
    private val antiGlareList = mutableListOf<AntiGlarePlateData>()
    private val signList = mutableListOf<SignData>()

    private const val MAX_FLOAT_COUNT = 96000 
    private val leftVertices = FloatArray(MAX_FLOAT_COUNT)
    private val rightVertices = FloatArray(MAX_FLOAT_COUNT)
    private var leftVertexCount = 0
    private var rightVertexCount = 0
    private var currentSampleCount = 0

    fun reset() {
        leftVertexCount = 0; rightVertexCount = 0
        postList.clear(); reflectorList.clear(); antiGlareList.clear(); signList.clear()
        ShadowRenderer.reset(); RoadObjectData.clear(); currentSampleCount = 0
    }

    private fun getDayFactor(): Float {
        val calendar = Calendar.getInstance()
        val secondsOfDay = calendar.get(Calendar.HOUR_OF_DAY) * 3600 +
                           calendar.get(Calendar.MINUTE) * 60 +
                           calendar.get(Calendar.SECOND)
        val sunAngle = (secondsOfDay.toDouble() / 86400.0 * 2.0 * PI - PI / 2.0).toFloat()
        return (sin(sunAngle).coerceIn(-1f, 1f) + 1f) / 2f
    }

    private fun applyNightDim(paint: Paint, baseColor: Int, dayFactor: Float) {
        val b = (0.2f + 0.8f * dayFactor).coerceIn(0.1f, 1.0f)
        paint.color = Color.rgb(
            (Color.red(baseColor) * b).toInt(),
            (Color.green(baseColor) * b).toInt(),
            (Color.blue(baseColor) * b).toInt()
        )
    }

    fun draw(canvas: Canvas) {
        val df = getDayFactor()
        applyNightDim(guardrailPlatePaint, GameSettings.COLOR_GUARDRAIL_PLATE, df)
        applyNightDim(guardrailPostPaint, GameSettings.COLOR_GUARDRAIL_POST, df)
        applyNightDim(reflectorPaint, GameSettings.COLOR_REFLECTOR, df)
        applyNightDim(antiGlarePaint, Color.parseColor("#004D40"), df)
        applyNightDim(antiGlarePostPaint, Color.parseColor("#CCCCCC"), df)
        applyNightDim(mirrorFillPaint, GameSettings.COLOR_GUARDRAIL_PLATE, df)
        applyNightDim(signBoardPaint, Color.parseColor("#007A33"), df)
        applyNightDim(signBorderPaint, Color.WHITE, df)

        ShadowRenderer.draw(canvas)
        if (currentSampleCount > 0) {
            CurveMirrorFiller.drawIntersectionFill(canvas, RoadObjectData.rightMirrorUpperX, RoadObjectData.rightMirrorUpperY, RoadObjectData.rightMirrorLowerX, RoadObjectData.rightMirrorLowerY, currentSampleCount, mirrorFillPaint, isRight = true)
            CurveMirrorFiller.drawIntersectionFill(canvas, RoadObjectData.leftMirrorUpperX, RoadObjectData.leftMirrorUpperY, RoadObjectData.leftMirrorLowerX, RoadObjectData.leftMirrorLowerY, currentSampleCount, mirrorFillPaint, isRight = false)
        }

        for (i in signList.size - 1 downTo 0) {
            val s = signList[i]
            canvas.drawRect(s.poleX, s.y - s.poleH, s.poleX + s.poleW, s.y, antiGlarePostPaint)
            canvas.drawRect(s.x, s.y - s.poleH, s.x + s.w, s.y - s.poleH + s.h, signBoardPaint)
            canvas.drawRect(s.x, s.y - s.poleH, s.x + s.w, s.y - s.poleH + s.h, signBorderPaint)
            val px = s.w * 0.15f; val lh = s.h * 0.12f
            canvas.drawRect(s.x + px, s.y - s.poleH + s.h * 0.25f, s.x + s.w - px, s.y - s.poleH + s.h * 0.25f + lh, guardrailPlatePaint)
            canvas.drawRect(s.x + px, s.y - s.poleH + s.h * 0.55f, s.x + s.w - px, s.y - s.poleH + s.h * 0.55f + lh, guardrailPlatePaint)
        }

        for (i in antiGlareList.size - 1 downTo 0) {
            val plate = antiGlareList[i]
            canvas.drawRect(plate.postX, plate.y, plate.postX + plate.postW, plate.y + plate.postH, antiGlarePostPaint)
            canvas.drawRect(plate.x, plate.y - plate.h, plate.x + plate.w, plate.y, antiGlarePaint)
        }

        if (DeveloperSettings.showGuardrailPosts) {
            for (i in postList.size - 1 downTo 0) {
                val post = postList[i]
                canvas.drawRect(post.x, post.y - post.h, post.x + post.w, post.y, guardrailPostPaint)
            }
        }
        if (leftVertexCount > 0) canvas.drawVertices(Canvas.VertexMode.TRIANGLES, leftVertexCount, leftVertices, 0, null, 0, null, 0, null, 0, 0, guardrailPlatePaint)
        if (rightVertexCount > 0) canvas.drawVertices(Canvas.VertexMode.TRIANGLES, rightVertexCount, rightVertices, 0, null, 0, null, 0, null, 0, 0, guardrailPlatePaint)

        for (i in reflectorList.size - 1 downTo 0) {
            val ref = reflectorList[i]
            canvas.drawCircle(ref.x, ref.y, ref.radius, reflectorPaint)
        }
    }

    fun addGuardrailSegment(lx1: Float, lx2: Float, rx1: Float, rx2: Float, y1: Float, y2: Float, y1_bottom: Float, y2_bottom: Float, sunRelHeading: Float) {
        val plateH = GameSettings.GUARDRAIL_PLATE_HEIGHT; val scale = (y1_bottom - y1) / plateH; val scalePrev = (y2_bottom - y2) / plateH
        val offsetM = 0.12f; val offsetL = offsetM * scale * GameSettings.ROAD_STRETCH; val offsetLPrev = offsetM * scalePrev * GameSettings.ROAD_STRETCH
        val offsetR = -offsetM * scale * GameSettings.ROAD_STRETCH; val offsetRPrev = -offsetM * scalePrev * GameSettings.ROAD_STRETCH
        if (leftVertexCount + 12 <= MAX_FLOAT_COUNT) {
            val sx1 = lx1 + offsetL; val sx2 = lx2 + offsetLPrev
            leftVertices[leftVertexCount++] = sx1; leftVertices[leftVertexCount++] = y1_bottom
            leftVertices[leftVertexCount++] = sx2; leftVertices[leftVertexCount++] = y2_bottom
            leftVertices[leftVertexCount++] = sx2; leftVertices[leftVertexCount++] = y2
            leftVertices[leftVertexCount++] = sx1; leftVertices[leftVertexCount++] = y1_bottom
            leftVertices[leftVertexCount++] = sx2; leftVertices[leftVertexCount++] = y2
            leftVertices[leftVertexCount++] = sx1; leftVertices[leftVertexCount++] = y1
        }
        if (rightVertexCount + 12 <= MAX_FLOAT_COUNT) {
            val sx1 = rx1 + offsetR; val sx2 = rx2 + offsetRPrev
            rightVertices[rightVertexCount++] = sx1; rightVertices[rightVertexCount++] = y1_bottom
            rightVertices[rightVertexCount++] = sx2; rightVertices[rightVertexCount++] = y2_bottom
            rightVertices[rightVertexCount++] = sx2; rightVertices[rightVertexCount++] = y2
            rightVertices[rightVertexCount++] = sx1; rightVertices[rightVertexCount++] = y1_bottom
            rightVertices[rightVertexCount++] = sx2; rightVertices[rightVertexCount++] = y2
            rightVertices[rightVertexCount++] = sx1; rightVertices[rightVertexCount++] = y1
        }
    }

    fun addPostPath(playerDist: Float, y: Float, z: Float, zPrev: Float, leftX: Float, rightX: Float, topY: Float, sunRelHeading: Float, isOpposite: Boolean = false) {
        val scale = GameSettings.FOV / (z + 0.1f); val postInterval = GameSettings.GUARDRAIL_POST_INTERVAL; val distPrev = playerDist + zPrev; val distCurr = playerDist + z
        if ((distPrev / postInterval).toInt() != (distCurr / postInterval).toInt()) {
            val postW = GameSettings.GUARDRAIL_POST_DIAMETER * scale * GameSettings.ROAD_STRETCH; val postH = y - topY
            if (DeveloperSettings.showGuardrailPosts) { if (!isOpposite) postList.add(PostData(leftX - postW, y, postW, postH)) else postList.add(PostData(rightX, y, postW, postH)) }
        }
        
        // 案内看板 (ループ設置を廃止し、施設の1km前と500m前に設置)
        if (!isOpposite) {
            for (obj in CourseManager.getRoadObjects()) {
                if (obj.type in listOf("SA", "PA", "IC", "JCT", "TB")) {
                    val pos1k = obj.posKm * 1000f - 1000f
                    val pos500 = obj.posKm * 1000f - 500f
                    
                    if ((distPrev < pos1k && distCurr >= pos1k) || (distPrev < pos500 && distCurr >= pos500)) {
                        val sW = 4.5f * scale * GameSettings.ROAD_STRETCH
                        val sH = 3.0f * scale
                        val pH = 7.0f * scale
                        val pW = 0.3f * scale * GameSettings.ROAD_STRETCH
                        val bx = leftX - 2.0f * scale * GameSettings.ROAD_STRETCH - sW
                        signList.add(SignData(bx, y, sW, sH, bx + sW / 2f - pW / 2f, pW, pH))
                    }
                }
            }
        }

        val plateInterval = 1.0f
        if ((distPrev / plateInterval).toInt() != (distCurr / plateInterval).toInt()) {
            val plateW = 0.15f * scale * GameSettings.ROAD_STRETCH; val plateH = 0.40f * scale; val plateTopY = y - 1.6f * scale
            val stickRadiusM = 0.04f; val stickW = stickRadiusM * 2f * scale * GameSettings.ROAD_STRETCH; val stickH = 1.6f * scale - plateH
            if (!isOpposite) { val medianHalfW = (GameSettings.MEDIAN_WIDTH / 2f) * scale * GameSettings.ROAD_STRETCH; val baseX = rightX + medianHalfW - (stickW / 2f); antiGlareList.add(AntiGlarePlateData(x = baseX + (stickW / 2f) - (plateW / 2f), y = plateTopY + plateH, w = plateW, h = plateH, postX = baseX, postW = stickW, postH = stickH)) }
        }
        val refInterval = GameSettings.REFLECTOR_INTERVAL
        if ((distPrev / refInterval).toInt() != (distCurr / refInterval).toInt()) {
            val refSize = GameSettings.REFLECTOR_DIAMETER * scale * GameSettings.ROAD_STRETCH
            if (!isOpposite) reflectorList.add(ReflectorData(leftX, topY, refSize * 0.5f)) else reflectorList.add(ReflectorData(rightX, topY, refSize * 0.5f))
        }
    }

    fun updateMirrorSample(index: Int, rx1: Float, ry1: Float, rx2: Float, ry2: Float, lx1: Float, ly1: Float, lx2: Float, ly2: Float) {
        RoadObjectData.rightMirrorUpperX[index] = rx1; RoadObjectData.rightMirrorUpperY[index] = ry1; RoadObjectData.rightMirrorLowerX[index] = rx2; RoadObjectData.rightMirrorLowerY[index] = ry2
        RoadObjectData.leftMirrorUpperX[index] = lx1; RoadObjectData.leftMirrorUpperY[index] = ly1; RoadObjectData.leftMirrorLowerX[index] = lx2; RoadObjectData.leftMirrorLowerY[index] = ly2
        currentSampleCount = index + 1
    }
}
