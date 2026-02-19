package com.example.autorun.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import com.example.autorun.R
import com.example.autorun.config.GameSettings
import com.example.autorun.core.GameState
import com.example.autorun.data.vehicle.VehicleDatabase
import kotlin.math.cos
import kotlin.math.sin

/**
 * 【HDUMeters】
 * 個別のメーター・UIパーツの描画を担当するオブジェクト。
 */
object HDUMeters {
    private var meterBgColor = Color.parseColor("#1A1A1A")

    private val bgPaint = Paint().apply { isAntiAlias = true; style = Paint.Style.FILL }
    private val borderPaint = Paint().apply { isAntiAlias = true; style = Paint.Style.STROKE; strokeWidth = 10f; color = Color.BLACK }
    private val redZonePaint = Paint().apply { isAntiAlias = true; style = Paint.Style.STROKE; strokeWidth = 8f; color = Color.RED }
    private val textPaint = Paint().apply { isAntiAlias = true; color = Color.WHITE; style = Paint.Style.FILL }
    private val needlePaint = Paint().apply { isAntiAlias = true; style = Paint.Style.FILL; color = Color.RED }
    private val brakeStrokePaint = Paint().apply { isAntiAlias = true; style = Paint.Style.STROKE }
    private val brakeFillPaint = Paint().apply { isAntiAlias = true; style = Paint.Style.FILL }
    private val tapePaint = Paint().apply { isAntiAlias = true; color = Color.WHITE; style = Paint.Style.FILL; alpha = 240 }
    
    private val genericPaint = Paint().apply { isAntiAlias = true; isFilterBitmap = true }
    private val needlePath = Path().apply { moveTo(0f, 0.5f); lineTo(0f, -0.5f); lineTo(1.0f, 0f); close() }
    private val brakePath = Path()
    private val tempRect = RectF()

    private var steerWheelBitmap: Bitmap? = null
    private var lastSteerWheelThickness = -1f
    private var lastSteerWheelRadius = -1f

    fun init(context: Context) {
        meterBgColor = Color.parseColor(context.getString(R.string.status_box_color))
        bgPaint.color = meterBgColor
        needlePaint.color = Color.parseColor(context.getString(R.string.gauge_needle_color))
        redZonePaint.color = Color.parseColor(context.getString(R.string.gauge_needle_color))
    }

    fun drawTachometer(canvas: Canvas, state: GameState, rect: RectF) {
        val specs = VehicleDatabase.getSelectedVehicle()
        drawRPMGauge(canvas, state.engineRPM, specs.maxRedzone, specs.minRedzone, rect.centerX(), rect.centerY(), rect.width() / 2f)
    }

    fun drawSpeedometer(canvas: Canvas, state: GameState, rect: RectF, font: Typeface?) {
        drawSpeedGauge(canvas, state.calculatedSpeedKmH, 320f, rect.centerX(), rect.centerY(), rect.width() / 2f, font)
    }

    fun drawBoostGauge(canvas: Canvas, state: GameState, rect: RectF) {
        drawBoostGauge(canvas, state.turboBoost, -1.0f, 2.0f, rect.centerX(), rect.centerY(), rect.width() / 2f)
    }

    fun drawSteeringWheel(canvas: Canvas, state: GameState, rect: RectF) {
        val alpha = GameSettings.UI_ALPHA_STEER
        val radius = rect.width() / 2f
        val thickness = GameSettings.STEER_WHEEL_THICKNESS * GameSettings.UI_SCALE_STEER
        prepareSteerWheelBitmap(thickness, radius)
        val bitmap = steerWheelBitmap ?: return
        
        // 修正点: ハンドルの回転方向を入力(steeringInput)と同期 (マイナスを削除)
        val totalRotation = state.steeringInput * GameSettings.STEER_MAX_ANGLE
        
        genericPaint.alpha = (255 * alpha).toInt()
        canvas.save()
        canvas.translate(rect.centerX(), rect.centerY())
        canvas.rotate(totalRotation)
        val offset = bitmap.width / 2f
        canvas.drawBitmap(bitmap, -offset, -offset, genericPaint)
        val tapeW = 14f * GameSettings.UI_SCALE_STEER
        tapePaint.alpha = (240 * alpha).toInt()
        canvas.drawRect(-tapeW / 2, -radius - thickness / 2 - 5f, tapeW / 2, -radius + thickness / 2 + 5f, tapePaint)
        canvas.restore()
    }

    fun drawBrakePedal(canvas: Canvas, state: GameState, rect: RectF, font: Typeface?) {
        val sB = GameSettings.UI_SCALE_BRAKE
        val alpha = GameSettings.UI_ALPHA_BRAKE
        val clipW = rect.width() * 0.3f
        val clipH = rect.height() * 0.3f
        val slimRight = rect.right - (clipW * 0.5f)
        brakePath.reset()
        brakePath.moveTo(rect.left, rect.top)
        brakePath.lineTo(slimRight, rect.top)
        brakePath.lineTo(slimRight, rect.bottom - clipH)
        brakePath.lineTo(slimRight - (clipW * 0.5f), rect.bottom - clipH)
        brakePath.lineTo(slimRight - (clipW * 0.5f), rect.bottom)
        brakePath.lineTo(rect.left, rect.bottom)
        brakePath.close()
        brakeStrokePaint.color = Color.BLACK; brakeStrokePaint.alpha = (100 * alpha).toInt(); brakeStrokePaint.strokeWidth = 12f * sB; canvas.drawPath(brakePath, brakeStrokePaint)
        brakeStrokePaint.color = Color.WHITE; brakeStrokePaint.alpha = (180 * alpha).toInt(); brakeStrokePaint.strokeWidth = 3f * sB; canvas.drawPath(brakePath, brakeStrokePaint)
        brakeFillPaint.color = if (state.isBraking) Color.parseColor("#AA0000") else Color.parseColor("#333333"); brakeFillPaint.alpha = (255 * alpha).toInt(); canvas.drawPath(brakePath, brakeFillPaint)
        brakeStrokePaint.color = Color.BLACK; brakeStrokePaint.alpha = (150 * alpha).toInt(); brakeStrokePaint.strokeWidth = 6f * sB
        val slitMargin = 20f * sB
        for (i in 1..4) { 
            val y = rect.top + (rect.height() / 5) * i
            if (y < rect.bottom - clipH) canvas.drawLine(rect.left + slitMargin, y, slimRight - slitMargin, y, brakeStrokePaint) 
            else canvas.drawLine(rect.left + slitMargin, y, slimRight - (clipW * 0.5f) - slitMargin, y, brakeStrokePaint)
        }
        textPaint.color = Color.WHITE; textPaint.alpha = (200 * alpha).toInt(); textPaint.textSize = 24f * sB; textPaint.typeface = font
        canvas.drawText("BRAKE", rect.left + 15f * sB, rect.bottom - 15f * sB, textPaint)
    }

    private fun prepareSteerWheelBitmap(thickness: Float, radius: Float) {
        if (steerWheelBitmap != null && lastSteerWheelThickness == thickness && lastSteerWheelRadius == radius) return
        steerWheelBitmap?.recycle()
        val size = ((radius + thickness) * 2.2f).toInt()
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val cx = size / 2f; val cy = size / 2f
        val hubRadius = radius * 0.24f; val spokeThickness = thickness * 0.75f
        genericPaint.reset(); genericPaint.isAntiAlias = true; genericPaint.style = Paint.Style.FILL
        genericPaint.shader = RadialGradient(cx, cy, hubRadius, intArrayOf(Color.parseColor("#444444"), Color.BLACK), null, Shader.TileMode.CLAMP)
        canvas.drawCircle(cx, cy, hubRadius, genericPaint)
        genericPaint.shader = null; genericPaint.style = Paint.Style.STROKE; genericPaint.strokeWidth = thickness
        genericPaint.shader = RadialGradient(cx, cy, radius + thickness/2, intArrayOf(Color.parseColor("#333333"), Color.BLACK, Color.parseColor("#222222")), floatArrayOf(0.85f, 0.92f, 1.0f), Shader.TileMode.CLAMP)
        canvas.drawCircle(cx, cy, radius, genericPaint)
        genericPaint.shader = null; genericPaint.strokeWidth = spokeThickness; genericPaint.color = Color.BLACK; genericPaint.alpha = GameSettings.STEER_WHEEL_ALPHA
        canvas.drawLine(cx - radius + thickness/2, cy, cx - hubRadius + 10f, cy, genericPaint)
        canvas.drawLine(cx + hubRadius - 10f, cy, cx + radius - thickness/2, cy, genericPaint)
        canvas.drawLine(cx, cy + hubRadius - 10f, cx, cy + radius - thickness/2, genericPaint)
        genericPaint.shader = null; genericPaint.style = Paint.Style.STROKE; genericPaint.strokeWidth = thickness * 0.3f; genericPaint.color = Color.WHITE
        val glossOval = RectF(cx - radius, cy - radius, cx + radius, cy + radius)
        genericPaint.alpha = 40; canvas.drawArc(glossOval, 210f, 120f, false, genericPaint)
        genericPaint.alpha = 20; canvas.drawArc(glossOval, 45f, 30f, false, genericPaint)
        steerWheelBitmap = bitmap; lastSteerWheelThickness = thickness; lastSteerWheelRadius = radius
    }

    private fun drawRPMGauge(canvas: Canvas, rpm: Float, maxRpm: Float, minRedzone: Float, cx: Float, cy: Float, radius: Float) {
        val sG = GameSettings.UI_SCALE_RPM; val alpha = GameSettings.UI_ALPHA_RPM
        bgPaint.alpha = (255 * alpha).toInt(); canvas.drawCircle(cx, cy, radius, bgPaint)
        borderPaint.strokeWidth = 10f * sG; borderPaint.alpha = (255 * alpha).toInt(); canvas.drawCircle(cx, cy, radius + 3f * sG, borderPaint)
        val startAngle = 135f; val sweepAngle = 270f; val redZoneStartAngle = startAngle + (minRedzone / maxRpm) * sweepAngle
        tempRect.set(cx - radius + 5f * sG, cy - radius + 5f * sG, cx + radius - 5f * sG, cy + radius - 5f * sG)
        redZonePaint.strokeWidth = 8f * sG; redZonePaint.alpha = (255 * alpha).toInt(); canvas.drawArc(tempRect, redZoneStartAngle, sweepAngle - (redZoneStartAngle - startAngle), false, redZonePaint)
        textPaint.color = Color.WHITE; textPaint.alpha = (255 * alpha).toInt(); textPaint.textSize = radius * 0.22f; textPaint.typeface = Typeface.DEFAULT_BOLD
        val maxLabel = (maxRpm / 1000).toInt()
        for (i in 0..maxLabel) {
            val angle = startAngle + (i * 1000f / maxRpm) * sweepAngle; val rad = Math.toRadians(angle.toDouble()); val cosA = cos(rad).toFloat(); val sinA = sin(rad).toFloat()
            canvas.drawLine(cx + (radius * 0.85f * cosA), cy + (radius * 0.85f * sinA), cx + (radius * 0.98f * cosA), cy + (radius * 0.98f * sinA), textPaint)
            val tx = cx + (radius * 0.65f * cosA); val ty = cy + (radius * 0.65f * sinA); val label = i.toString(); canvas.drawText(label, tx - textPaint.measureText(label) / 2f, ty + textPaint.textSize / 3f, textPaint)
        }
        val needleAngle = startAngle + (rpm / maxRpm).coerceIn(0f, 1f) * sweepAngle; needlePaint.alpha = (255 * alpha).toInt()
        drawTaperedNeedle(canvas, cx, cy, radius * 0.85f, needleAngle, radius * 0.1f)
        brakeFillPaint.color = Color.BLACK; brakeFillPaint.alpha = (255 * alpha).toInt(); canvas.drawCircle(cx, cy, radius * 0.12f, brakeFillPaint)
    }

    private fun drawSpeedGauge(canvas: Canvas, value: Float, maxVal: Float, cx: Float, cy: Float, radius: Float, typeface: Typeface?) {
        val sG = GameSettings.UI_SCALE_SPEED; val alpha = GameSettings.UI_ALPHA_SPEED
        bgPaint.alpha = (255 * alpha).toInt(); canvas.drawCircle(cx, cy, radius, bgPaint)
        borderPaint.strokeWidth = 10f * sG; borderPaint.alpha = (255 * alpha).toInt(); canvas.drawCircle(cx, cy, radius + 3f * sG, borderPaint)
        borderPaint.color = Color.WHITE; borderPaint.strokeWidth = 3f * sG; canvas.drawCircle(cx, cy, radius, borderPaint)
        borderPaint.color = Color.BLACK; textPaint.typeface = typeface; textPaint.textSize = radius * 0.45f; textPaint.alpha = (255 * alpha).toInt()
        val valStr = value.toInt().toString(); canvas.drawText(valStr, cx - textPaint.measureText(valStr) / 2f, cy + radius * 0.3f, textPaint)
        textPaint.textSize = radius * 0.15f; canvas.drawText("km/h", cx - textPaint.measureText("km/h") / 2f, cy + radius * 0.6f, textPaint)
        val needleAngle = 135f + (value / maxVal).coerceIn(0f, 1f) * 270f; needlePaint.alpha = (255 * alpha).toInt()
        drawTaperedNeedle(canvas, cx, cy, radius * 0.85f, needleAngle, radius * 0.08f)
        brakeFillPaint.color = Color.BLACK; brakeFillPaint.alpha = (255 * alpha).toInt(); canvas.drawCircle(cx, cy, radius * 0.12f, brakeFillPaint)
    }

    private fun drawBoostGauge(canvas: Canvas, value: Float, minVal: Float, maxVal: Float, cx: Float, cy: Float, radius: Float) {
        val sG = GameSettings.UI_SCALE_BOOST; val alpha = GameSettings.UI_ALPHA_BOOST
        bgPaint.alpha = (255 * alpha).toInt(); canvas.drawCircle(cx, cy, radius, bgPaint)
        borderPaint.strokeWidth = 10f * sG; borderPaint.alpha = (255 * alpha).toInt(); canvas.drawCircle(cx, cy, radius + 3f * sG, borderPaint)
        borderPaint.color = Color.WHITE; borderPaint.strokeWidth = 3f * sG; canvas.drawCircle(cx, cy, radius, borderPaint)
        borderPaint.color = Color.BLACK
        textPaint.color = Color.WHITE; textPaint.alpha = (255 * alpha).toInt(); textPaint.textSize = radius * 0.22f; textPaint.typeface = Typeface.DEFAULT_BOLD
        val range = maxVal - minVal
        val labels = listOf(-1.0f, -0.5f, 0.0f, 0.5f, 1.0f, 1.5f, 2.0f)
        for (labelVal in labels) {
            val ratio = (labelVal - minVal) / range
            val angle = 135f + ratio * 270f
            val rad = Math.toRadians(angle.toDouble()); val cosA = cos(rad).toFloat(); val sinA = sin(rad).toFloat()
            val lineLength = if (labelVal == labelVal.toInt().toFloat()) 0.15f else 0.08f
            canvas.drawLine(cx + (radius * (1f - lineLength) * cosA), cy + (radius * (1f - lineLength) * sinA), cx + (radius * 0.98f * cosA), cy + (radius * 0.98f * sinA), textPaint)
            if (labelVal == -1.0f || labelVal == 0.0f || labelVal == 1.0f || labelVal == 2.0f) {
                val labelText = if (labelVal == 0.0f) "0" else "%.1f".format(labelVal)
                val tx = cx + (radius * 0.65f * cosA); val ty = cy + (radius * 0.65f * sinA); canvas.drawText(labelText, tx - textPaint.measureText(labelText) / 2f, ty + textPaint.textSize / 3f, textPaint)
            }
        }
        textPaint.textSize = radius * 0.18f; canvas.drawText("bar", cx - textPaint.measureText("bar") / 2f, cy + radius * 0.35f, textPaint); canvas.drawText("BOOST", cx - textPaint.measureText("BOOST") / 2f, cy + radius * 0.55f, textPaint)
        val needleRatio = (value - minVal) / range
        val needleAngle = 135f + needleRatio.coerceIn(0f, 1f) * 270f; needlePaint.alpha = (255 * alpha).toInt()
        drawTaperedNeedle(canvas, cx, cy, radius * 0.85f, needleAngle, radius * 0.08f)
        brakeFillPaint.color = Color.BLACK; brakeFillPaint.alpha = (255 * alpha).toInt(); canvas.drawCircle(cx, cy, radius * 0.12f, brakeFillPaint)
    }

    private fun drawTaperedNeedle(canvas: Canvas, cx: Float, cy: Float, length: Float, angleDeg: Float, baseWidth: Float) {
        canvas.save(); canvas.translate(cx, cy); canvas.rotate(angleDeg); canvas.scale(length, baseWidth); canvas.drawPath(needlePath, needlePaint); canvas.restore()
    }
}
