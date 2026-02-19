package com.example.autorun.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LightingColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import com.example.autorun.R
import com.example.autorun.config.GameSettings
import com.example.autorun.core.GameState
import java.util.Calendar
import java.util.TimeZone
import kotlin.math.*

object BackgroundRenderer {
    private val skyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val mountainPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val sunPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val moonPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = Color.parseColor("#FFFFE0") }
    private val sunGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val starPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; style = Paint.Style.FILL }
    
    private var mountainBitmap: Bitmap? = null
    private val srcRect = Rect()
    private val destRect = RectF()
    
    private val jstTimeZone = TimeZone.getTimeZone("Asia/Tokyo")
    private val calendar = Calendar.getInstance(jstTimeZone)
    private val stars = List(100) { Pair(Math.random().toFloat(), Math.random().toFloat()) }

    fun init(context: Context) {
        loadResources(context)
    }

    fun loadResources(context: Context) {
        try {
            val resName = context.getString(R.string.bg_mountain_image)
            val resId = context.resources.getIdentifier(resName, "drawable", context.packageName)
            if (resId != 0) {
                mountainBitmap = BitmapFactory.decodeResource(context.resources, resId)
            }
        } catch (e: Exception) { e.printStackTrace() }
    }

    fun draw(canvas: Canvas, screenWidth: Float, screenHeight: Float, horizon: Float, state: GameState) {
        calendar.timeInMillis = System.currentTimeMillis()
        val secondsOfDay = calendar.get(Calendar.HOUR_OF_DAY) * 3600 +
                           calendar.get(Calendar.MINUTE) * 60 +
                           calendar.get(Calendar.SECOND)
        
        val angleForFactor = (secondsOfDay.toDouble() / 86400.0 * 2.0 * PI - PI / 2.0).toFloat()
        val dayFactor = (sin(angleForFactor).coerceIn(-1f, 1f) + 1f) / 2f
        
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val (topColor, bottomColor) = getSkyColors(hour, dayFactor)
        
        skyPaint.shader = LinearGradient(0f, 0f, 0f, screenHeight, topColor, bottomColor, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, screenWidth, screenHeight, skyPaint)

        if (dayFactor < 0.3f) {
            starPaint.alpha = ((0.3f - dayFactor) / 0.3f * 255).toInt().coerceIn(0, 255)
            for (star in stars) {
                canvas.drawCircle(star.first * screenWidth, star.second * horizon, 2f, starPaint)
            }
        }

        val sunVisualX = (screenWidth / 2f) + (GameSettings.SUN_WORLD_HEADING - state.playerHeading) * screenWidth / (PI.toFloat() / 2f)
        val sunVisualY = horizon - (screenHeight * 0.45f * sin(angleForFactor))
        
        if (dayFactor > 0.05f) {
            // 太陽の大きさをさらに半分に縮小 (5f -> 2.5f)
            val sunRadius = 2.5f * GameSettings.SUN_VISUAL_EXAGGERATION
            sunPaint.color = Color.WHITE
            canvas.drawCircle(sunVisualX, sunVisualY, sunRadius, sunPaint)
            
            sunGlowPaint.shader = android.graphics.RadialGradient(sunVisualX, sunVisualY, sunRadius * 4f, 
                Color.parseColor("#66FFFFFF"), Color.TRANSPARENT, Shader.TileMode.CLAMP)
            canvas.drawCircle(sunVisualX, sunVisualY, sunRadius * 4f, sunGlowPaint)
        }

        if (dayFactor < 0.5f) {
            val moonAngle = angleForFactor + PI.toFloat()
            val moonVisualX = (screenWidth / 2f) + (GameSettings.SUN_WORLD_HEADING - state.playerHeading + PI.toFloat()) * screenWidth / (PI.toFloat() / 2f)
            val moonVisualY = horizon - (screenHeight * 0.45f * sin(moonAngle))
            val moonRadius = 4f * GameSettings.SUN_VISUAL_EXAGGERATION
            
            moonPaint.alpha = ((0.5f - dayFactor) * 2f * 255).toInt().coerceIn(0, 255)
            canvas.drawCircle(moonVisualX, moonVisualY, moonRadius, moonPaint)
            moonPaint.color = interpolateColor(topColor, bottomColor, 0.5f)
            canvas.drawCircle(moonVisualX - moonRadius * 0.4f, moonVisualY, moonRadius, moonPaint)
            moonPaint.color = Color.parseColor("#FFFFE0")
        }

        mountainBitmap?.let { bmp ->
            val brightnessFactor = (0.4f + 0.6f * dayFactor).coerceIn(0.1f, 1.0f)
            val brightness = (brightnessFactor * 255).toInt()
            
            val filterColor = interpolateColor(Color.rgb(brightness, brightness, brightness), 
                                             interpolateColor(topColor, bottomColor, 0.5f), 
                                             0.3f)
            mountainPaint.colorFilter = LightingColorFilter(filterColor, 0)

            val bmpW = bmp.width; val bmpH = bmp.height
            val scrollX = (state.playerHeading * 0.5f + state.playerDistance * 0.0001f) % 1f
            val viewH = screenHeight * 0.4f
            val drawY = horizon + screenHeight * 0.05f
            
            val offset = (scrollX * bmpW).toInt()
            srcRect.set(offset, 0, min(offset + bmpW / 2, bmpW), bmpH)
            destRect.set(0f, drawY - viewH, screenWidth, drawY)
            canvas.drawBitmap(bmp, srcRect, destRect, mountainPaint)
            
            if (offset + bmpW / 2 > bmpW) {
                val remaining = (offset + bmpW / 2) - bmpW
                srcRect.set(0, 0, remaining, bmpH)
                val ratio = remaining.toFloat() / (bmpW / 2f)
                destRect.set(screenWidth * (1f - ratio), drawY - viewH, screenWidth, drawY)
                canvas.drawBitmap(bmp, srcRect, destRect, mountainPaint)
            }
        }
    }

    private fun getSkyColors(hour: Int, dayFactor: Float): Pair<Int, Int> {
        return when (hour) {
            in 0..3 -> Color.parseColor("#000005") to Color.parseColor("#000015")
            in 4..5 -> Color.parseColor("#050520") to Color.parseColor("#4B0082")
            in 6..7 -> Color.parseColor("#FF4500") to Color.parseColor("#FFD700")
            in 8..15 -> Color.parseColor("#1E90FF") to Color.parseColor("#87CEEB")
            in 16..17 -> Color.parseColor("#FF8C00") to Color.parseColor("#FF4500")
            in 18..19 -> Color.parseColor("#4B0082") to Color.parseColor("#000033")
            else -> Color.parseColor("#000011") to Color.parseColor("#000033")
        }
    }
    
    private fun interpolateColor(c1: Int, c2: Int, f: Float): Int {
        val a = (Color.alpha(c1) + (Color.alpha(c2) - Color.alpha(c1)) * f).toInt()
        val r = (Color.red(c1) + (Color.red(c2) - Color.red(c1)) * f).toInt()
        val g = (Color.green(c1) + (Color.green(c2) - Color.green(c1)) * f).toInt()
        val b = (Color.blue(c1) + (Color.blue(c2) - Color.blue(c1)) * f).toInt()
        return Color.argb(a, r, g, b)
    }
}
