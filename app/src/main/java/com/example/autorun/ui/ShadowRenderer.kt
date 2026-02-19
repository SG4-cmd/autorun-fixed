package com.example.autorun.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import com.example.autorun.R
import com.example.autorun.config.GameSettings
import kotlin.math.cos
import kotlin.math.sin

/**
 * 【ShadowRenderer】
 * 太陽光や車体状況に基づき、影および車体光沢の描画を行うクラス。
 */
object ShadowRenderer {
    private val shadowPaint = Paint().apply {
        color = Color.parseColor("#99000000")
        isAntiAlias = false
        isDither = false
        style = Paint.Style.FILL
    }
    
    private val darkenPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
        color = Color.BLACK
        alpha = 4 
        xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP)
    }

    private val glossPaint = Paint().apply {
        isAntiAlias = false
        style = Paint.Style.FILL
        xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP)
    }
    
    private val shadowPath = Path().apply { fillType = Path.FillType.WINDING }
    private val carShadowPath = Path()
    private val tempRect = RectF()
    private val shadowMatrix = Matrix()

    private var currentBlurRadius = -1f
    private var currentGlossBlur = -1f
    
    private var blurScaling = 0.8f
    private var shadowLenFactor = 0.8f

    fun init(context: Context) {
        shadowPaint.color = Color.parseColor(context.getString(R.string.color_shadow))
        darkenPaint.alpha = context.getString(R.string.shadow_darken_alpha).toInt()
        blurScaling = context.getString(R.string.shadow_blur_scaling).toFloat()
        shadowLenFactor = context.getString(R.string.shadow_length_factor).toFloat()
    }

    fun reset() {
        shadowPath.reset()
    }

    fun drawCarShadow(canvas: Canvas, bitmap: Bitmap?, carX: Float, carY: Float, carW: Float, carH: Float, sunRelHeading: Float, carHeightM: Float, carLengthM: Float, pixelsPerMeter: Float) {
        if (!GameSettings.SHOW_SHADOWS) return

        val factor = GameSettings.getResolutionStepFactor()
        val targetBlur = (GameSettings.SHADOW_BLUR_RADIUS / factor) * blurScaling

        if (currentBlurRadius != targetBlur) {
            currentBlurRadius = targetBlur
            shadowPaint.maskFilter = if (currentBlurRadius > 0.5f) {
                BlurMaskFilter(currentBlurRadius, BlurMaskFilter.Blur.NORMAL)
            } else null
        }

        val baseW = carW * GameSettings.SHADOW_WIDTH_RATIO
        val topW = baseW * GameSettings.SHADOW_TRAPEZOID_RATIO
        val shadowH = (carW * 0.5f) * (carLengthM / 4.5f).coerceIn(0.8f, 1.5f) * GameSettings.SHADOW_LENGTH_RATIO

        canvas.save()
        val offsetPixels = (GameSettings.SHADOW_OFFSET_CM / 100f) * pixelsPerMeter
        canvas.translate(carX + carW / 2, carY + carH - offsetPixels)
        
        carShadowPath.reset()
        val halfBaseW = baseW / 2
        val halfTopW = topW / 2
        tempRect.set(-halfBaseW, -shadowH, halfBaseW, 0f)
        val r = GameSettings.SHADOW_CORNER_RADIUS
        carShadowPath.addRoundRect(tempRect, r, r, Path.Direction.CW)

        val src = floatArrayOf(-halfBaseW, 0f, halfBaseW, 0f, halfBaseW, -shadowH, -halfBaseW, -shadowH)
        val dst = floatArrayOf(-halfBaseW, 0f, halfBaseW, 0f, halfTopW, -shadowH, -halfTopW, -shadowH)
        shadowMatrix.setPolyToPoly(src, 0, dst, 0, 4)
        
        canvas.concat(shadowMatrix)
        canvas.drawPath(carShadowPath, shadowPaint)
        canvas.restore()
    }

    fun drawCarGloss(canvas: Canvas, carX: Float, carY: Float, carW: Float, carH: Float, sunRelHeading: Float) {
        if (!GameSettings.SHOW_SHADOWS) return

        tempRect.set(carX, carY, carX + carW, carY + carH)
        canvas.drawRect(tempRect, darkenPaint)

        if (GameSettings.CAR_GLOSS_ALPHA <= 0) return

        val factor = GameSettings.getResolutionStepFactor()
        val targetGlossBlur = (GameSettings.CAR_GLOSS_BLUR / factor) * blurScaling

        if (currentGlossBlur != targetGlossBlur) {
            currentGlossBlur = targetGlossBlur
            glossPaint.maskFilter = if (currentGlossBlur > 0.5f) {
                BlurMaskFilter(currentGlossBlur, BlurMaskFilter.Blur.NORMAL)
            } else null
        }

        val cosSun = cos(sunRelHeading.toDouble()).toFloat()
        val sinSun = sin(sunRelHeading.toDouble()).toFloat()

        val glossW = carW * 0.6f
        val glossH = carH * 0.2f
        val gx = carX + carW / 2 + (sinSun * carW * 0.35f)
        val gy = carY + carH * 0.3f + (cosSun * carH * 0.25f)

        glossPaint.color = Color.WHITE
        glossPaint.alpha = GameSettings.CAR_GLOSS_ALPHA

        canvas.save()
        tempRect.set(gx - glossW / 2, gy - glossH / 2, gx + glossW / 2, gy + glossH / 2)
        canvas.rotate(-sinSun * 12f, gx, gy)
        canvas.drawOval(tempRect, glossPaint)
        canvas.restore()
    }

    fun addGuardrailShadow(leftX: Float, leftXPrev: Float, rightX: Float, rightXPrev: Float, bottomY: Float, bottomYPrev: Float, height: Float, heightPrev: Float, sunRelHeading: Float, showRight: Boolean) {
        if (!GameSettings.SHOW_SHADOWS) return
        val dx = -sin(sunRelHeading.toDouble()).toFloat() * height * shadowLenFactor
        val dy = cos(sunRelHeading.toDouble()).toFloat() * height * shadowLenFactor * 0.3f
        val dxPrev = -sin(sunRelHeading.toDouble()).toFloat() * heightPrev * shadowLenFactor
        val dyPrev = cos(sunRelHeading.toDouble()).toFloat() * heightPrev * shadowLenFactor * 0.3f
        shadowPath.moveTo(leftX, bottomY); shadowPath.lineTo(leftXPrev, bottomYPrev); shadowPath.lineTo(leftXPrev + dxPrev, bottomYPrev + dyPrev); shadowPath.lineTo(leftX + dx, bottomY + dy); shadowPath.close()
        if (showRight) {
            shadowPath.moveTo(rightX, bottomY); shadowPath.lineTo(rightXPrev, bottomYPrev); shadowPath.lineTo(rightXPrev + dxPrev, bottomYPrev + dyPrev); shadowPath.lineTo(rightX + dx, bottomY + dy); shadowPath.close()
        }
    }

    fun addPostShadow(x: Float, y: Float, width: Float, height: Float, sunRelHeading: Float) {
        if (!GameSettings.SHOW_SHADOWS) return
        val sdx = -sin(sunRelHeading.toDouble()).toFloat() * height; val sdy = cos(sunRelHeading.toDouble()).toFloat() * height * 0.3f
        shadowPath.moveTo(x, y); shadowPath.lineTo(x + width, y); shadowPath.lineTo(x + width + sdx, y + sdy); shadowPath.lineTo(x + sdx, y + sdy); shadowPath.close()
    }

    fun draw(canvas: Canvas) {
        if (!GameSettings.SHOW_SHADOWS) return
        val originalFilter = shadowPaint.maskFilter
        shadowPaint.maskFilter = null
        canvas.drawPath(shadowPath, shadowPaint)
        shadowPaint.maskFilter = originalFilter
    }
}
