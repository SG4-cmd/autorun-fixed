package com.example.autorun.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import com.example.autorun.R
import com.example.autorun.audio.EngineSoundRenderer
import com.example.autorun.audio.MusicPlayer
import com.example.autorun.config.GameSettings
import com.example.autorun.core.GameState
import com.example.autorun.utils.GameUtils
import kotlin.math.abs

/**
 * 【HDU (Heads-Up Display)】
 * 統合UI管理クラス。各モジュールを呼び出して描画。
 */
object HDU {
    private val paint = Paint().apply { isAntiAlias = true }
    
    private var cachedThrottleShader: Shader? = null
    private val lastThrottleBarRect = RectF()
    private var throttleColors = intArrayOf(0xFF888800.toInt(), 0xFF880000.toInt())

    val statusRect = RectF()
    val mapRect = RectF()
    val settingsRect = RectF()      
    val settingsVisualRect = RectF() 
    val brakeRect = RectF()
    val rpmRect = RectF()
    val speedRect = RectF()
    val boostRect = RectF()
    val steerRect = RectF()
    val throttleRect = RectF()
    val compassRect = RectF()

    fun init(context: Context) {
        try {
            throttleColors = intArrayOf(
                Color.parseColor(context.getString(R.string.color_throttle_high)),
                Color.parseColor(context.getString(R.string.color_throttle_low))
            )
        } catch (e: Exception) {}
        HDUMap.init(context)
        HDUMeters.init(context)
        HDUSettings.init(context)
    }

    fun draw(canvas: Canvas, w: Float, h: Float, state: GameState, currentFps: Int, 
             fontSevenSegment: Typeface?, fontWarrior: Typeface?, fontJapanese: Typeface?,
             engineSound: EngineSoundRenderer?, musicPlayer: MusicPlayer?, context: Context) {
             
        drawCornerR(canvas, w, state, fontWarrior, fontSevenSegment)
        HDUMap.draw(canvas, state, mapRect, compassRect, fontWarrior, musicPlayer, context)
        HDUMeters.drawTachometer(canvas, state, rpmRect)
        HDUMeters.drawSpeedometer(canvas, state, speedRect, fontWarrior)
        HDUMeters.drawBoostGauge(canvas, state, boostRect)
        HDUMeters.drawSteeringWheel(canvas, state, steerRect)
        HDUMeters.drawBrakePedal(canvas, state, brakeRect, fontWarrior)
        drawThrottleIndicator(canvas, state, throttleRect)

        if (state.isDeveloperMode) {
            HDUDebugOverlay.draw(canvas, state, currentFps, engineSound)
        }
        
        // ナビ以外の設定アイコン（settingsVisualRect）の描画を停止
        // HDUSettings.drawSettingsButton(canvas, settingsVisualRect)

        if (state.isLayoutMode) HDULayoutEditor.draw(canvas, w, h, state, this)
        if (state.isSettingsOpen) HDUSettings.drawSettingsMenu(canvas, w, h, state, fontJapanese ?: fontWarrior)
    }

    fun updateLayoutRects(w: Float, h: Float) {
        val sS = GameSettings.UI_SCALE_STATUS; val sM = GameSettings.UI_SCALE_MAP
        val sSet = GameSettings.UI_SCALE_SETTINGS; val sC = GameSettings.UI_SCALE_COMPASS
        val sB = GameSettings.UI_SCALE_BRAKE; val sR = GameSettings.UI_SCALE_RPM
        val sP = GameSettings.UI_SCALE_SPEED; val sBO = GameSettings.UI_SCALE_BOOST
        val sT = GameSettings.UI_SCALE_THROTTLE; val sW = GameSettings.UI_SCALE_STEER

        statusRect.set(GameSettings.UI_POS_STATUS.x * w, GameSettings.UI_POS_STATUS.y * h, GameSettings.UI_POS_STATUS.x * w + 420f * sS, GameSettings.UI_POS_STATUS.y * h + 220f * sS)
        val mapW = 512f * sM; val mapH = 288f * sM
        mapRect.set(GameSettings.UI_POS_MAP.x * w, GameSettings.UI_POS_MAP.y * h, GameSettings.UI_POS_MAP.x * w + mapW, GameSettings.UI_POS_MAP.y * h + mapH)
        val setX = GameSettings.UI_POS_SETTINGS.x * w; val setY = GameSettings.UI_POS_SETTINGS.y * h
        settingsVisualRect.set(setX, setY, setX + 80f * sSet, setY + 80f * sSet)
        settingsRect.set(setX - 20f * sSet, setY - 20f * sSet, setX + 100f * sSet, setY + 100f * sSet)
        val compassW = 450f * sC
        compassRect.set(GameSettings.UI_POS_COMPASS.x * w, GameSettings.UI_POS_COMPASS.y * h, GameSettings.UI_POS_COMPASS.x * w + compassW, GameSettings.UI_POS_COMPASS.y * h + 60f * sC)
        brakeRect.set(GameSettings.UI_POS_BRAKE.x * w, GameSettings.UI_POS_BRAKE.y * h, GameSettings.UI_POS_BRAKE.x * w + 380f * sB, GameSettings.UI_POS_BRAKE.y * h + 120f * sB)
        rpmRect.set(GameSettings.UI_POS_RPM.x * w, GameSettings.UI_POS_RPM.y * h, GameSettings.UI_POS_RPM.x * w + 220f * sR, GameSettings.UI_POS_RPM.y * h + 220f * sR)
        speedRect.set(GameSettings.UI_POS_SPEED.x * w, GameSettings.UI_POS_SPEED.y * h, GameSettings.UI_POS_SPEED.x * w + 320f * sP, GameSettings.UI_POS_SPEED.y * h + 320f * sP)
        boostRect.set(GameSettings.UI_POS_BOOST.x * w, GameSettings.UI_POS_BOOST.y * h, GameSettings.UI_POS_BOOST.x * w + 200f * sBO, GameSettings.UI_POS_BOOST.y * h + 200f * sBO)
        val steerSize = 320f * sW
        steerRect.set(GameSettings.UI_POS_STEER.x * w - steerSize, GameSettings.UI_POS_STEER.y * h - steerSize, GameSettings.UI_POS_STEER.x * w + steerSize, GameSettings.UI_POS_STEER.y * h + steerSize)
        throttleRect.set(GameSettings.UI_POS_THROTTLE.x * w, GameSettings.UI_POS_THROTTLE.y * h, GameSettings.UI_POS_THROTTLE.x * w + 50f * sT, GameSettings.UI_POS_THROTTLE.y * h + 320f * sT)
        HDULayoutEditor.updateRects(GameSettings.UI_POS_EDITOR_PANEL.x, GameSettings.UI_POS_EDITOR_PANEL.y)
    }

    fun getUiValues(id: Int): Pair<Float, Float> {
        val (alpha, scale) = when (id) {
            0 -> GameSettings.UI_ALPHA_STATUS to GameSettings.UI_SCALE_STATUS
            1 -> GameSettings.UI_ALPHA_MAP to GameSettings.UI_SCALE_MAP
            2 -> GameSettings.UI_ALPHA_SETTINGS to GameSettings.UI_SCALE_SETTINGS
            3 -> GameSettings.UI_ALPHA_BRAKE to GameSettings.UI_SCALE_BRAKE
            5 -> GameSettings.UI_ALPHA_COMPASS to GameSettings.UI_SCALE_COMPASS
            6 -> GameSettings.UI_ALPHA_RPM to GameSettings.UI_SCALE_RPM
            7 -> GameSettings.UI_ALPHA_SPEED to GameSettings.UI_SCALE_SPEED
            8 -> GameSettings.UI_ALPHA_STEER to GameSettings.UI_SCALE_STEER
            9 -> GameSettings.UI_ALPHA_THROTTLE to GameSettings.UI_SCALE_THROTTLE
            10 -> GameSettings.UI_ALPHA_BOOST to GameSettings.UI_SCALE_BOOST
            else -> 1.0f to 1.0f
        }
        return Pair(alpha, (scale - 0.5f) / 2.0f)
    }
    
    private fun drawThrottleIndicator(canvas: Canvas, state: GameState, rect: RectF) {
        paint.reset(); paint.isAntiAlias = true
        paint.color = Color.BLACK; paint.alpha = (150 * GameSettings.UI_ALPHA_THROTTLE).toInt(); paint.style = Paint.Style.FILL
        canvas.drawRoundRect(rect, 6f, 6f, paint)
        paint.color = Color.WHITE; paint.alpha = (100 * GameSettings.UI_ALPHA_THROTTLE).toInt(); paint.style = Paint.Style.STROKE; paint.strokeWidth = 2f
        canvas.drawRoundRect(rect, 6f, 6f, paint)
        if (state.throttle > 0f) {
            if (cachedThrottleShader == null || lastThrottleBarRect != rect) {
                lastThrottleBarRect.set(rect)
                cachedThrottleShader = LinearGradient(rect.left, rect.bottom, rect.left, rect.top, throttleColors, null, Shader.TileMode.CLAMP)
            }
            val fH = rect.height() * state.throttle
            paint.reset(); paint.isAntiAlias = true; paint.shader = cachedThrottleShader; paint.style = Paint.Style.FILL; paint.alpha = (255 * GameSettings.UI_ALPHA_THROTTLE).toInt()
            canvas.drawRoundRect(rect.left, rect.bottom - fH, rect.right, rect.bottom, 6f, 6f, paint)
            paint.shader = null
        }
    }

    private fun drawCornerR(canvas: Canvas, w: Float, state: GameState, fontWarrior: Typeface?, fontSevenSegment: Typeface?) {
        val curve = abs(state.currentRoadCurve); if (curve < 0.001f) return 
        val radius = (1.0f / curve).toInt(); val text = "R $radius"
        paint.reset(); paint.isAntiAlias = true; paint.color = Color.BLACK; paint.alpha = 180; paint.style = Paint.Style.FILL; val m = 30f; val rect = RectF(w - 220f - m, m, w - m, m + 100f); canvas.drawRoundRect(rect, 15f, 15f, paint)
        paint.alpha = 255; paint.color = Color.YELLOW; paint.typeface = fontWarrior; paint.textSize = 28f; canvas.drawText("CURVE", rect.left + 20f, rect.top + 35f, paint)
        paint.typeface = fontSevenSegment; paint.textSize = 48f; canvas.drawText(text, rect.left + 20f, rect.top + 85f, paint)
    }

    fun handleSettingsTouch(x: Float, y: Float, state: GameState): Boolean {
        return HDUSettings.handleSettingsTouch(x, y, state)
    }
}
