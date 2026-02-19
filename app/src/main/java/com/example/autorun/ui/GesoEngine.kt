package com.example.autorun.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RectF
import android.graphics.Typeface
import com.example.autorun.audio.EngineSoundRenderer
import com.example.autorun.audio.MusicPlayer
import com.example.autorun.config.DeveloperSettings
import com.example.autorun.config.GameSettings
import com.example.autorun.core.GameState
import com.example.autorun.config.GameUIParameters
import com.example.autorun.core.CourseManager
import com.example.autorun.data.vehicle.VehicleDatabase

/**
 * 【GesoEngine: グラフィック描画の中枢】
 */
object GesoEngine {

    private const val COLOR_BRAKE_OVERLAY = 0x66FF0000
    private val mainPaint = Paint().apply { isAntiAlias = true }
    private val brakePaint = Paint().apply {
        isAntiAlias = true
        colorFilter = PorterDuffColorFilter(COLOR_BRAKE_OVERLAY, PorterDuff.Mode.SRC_ATOP)
    }

    private var playerBitmap: Bitmap? = null
    private var lastLoadedResId: Int = -1

    private val developerModeTouchArea = RectF(20f, 150f, 20f + GameUIParameters.STATUS_BOX_WIDTH, 150f + GameUIParameters.STATUS_BOX_HEIGHT)
    private val guardrailToggleArea = RectF()
    private val postToggleArea = RectF()
    private val miniMapTouchArea = RectF(20f, 150f, 20f + 420f, 150f + 400f)
    private val settingsTouchArea = RectF()
    private val playerDstRect = RectF()

    init {
        guardrailToggleArea.set(developerModeTouchArea.left, developerModeTouchArea.top, developerModeTouchArea.right, developerModeTouchArea.centerY())
        postToggleArea.set(developerModeTouchArea.left, developerModeTouchArea.centerY(), developerModeTouchArea.right, developerModeTouchArea.bottom)
    }

    fun loadVehicleResources(context: Context) {
        val specs = VehicleDatabase.getSelectedVehicle()
        if (specs.imageResId != lastLoadedResId) {
            playerBitmap = BitmapFactory.decodeResource(context.resources, specs.imageResId)
            lastLoadedResId = specs.imageResId
        }
    }

    fun drawAll(canvas: Canvas, w: Float, h: Float, state: GameState, currentFps: Int,
                steerBarRect: RectF, brakeRect: RectF, accelRect: RectF,
                font_seven_segment: Typeface?, font_warrior: Typeface?, font_japanese: Typeface?,
                engineSound: EngineSoundRenderer?, musicPlayer: MusicPlayer?, context: Context
    ) {
        val playerDist = state.playerDistance
        val segLen = GameSettings.SEGMENT_LENGTH
        val slope = CourseManager.getCurrentAngle(playerDist / segLen)
        val horizon = h * 0.5f + (slope * 10f)

        val compassCenterX = w / 2f
        val compassWidth = 400f
        val settingsSize = 60f
        val margin = 20f
        settingsTouchArea.set(
            compassCenterX - compassWidth / 2f - settingsSize - margin,
            20f,
            compassCenterX - compassWidth / 2f - margin,
            20f + settingsSize
        )

        BackgroundRenderer.draw(canvas, w, h, horizon, state)
        RoadRenderer.draw(canvas, w, h, state, horizon)
        drawPlayer(canvas, w, h, state)
        HDU.draw(canvas, w, h, state, currentFps, font_seven_segment, font_warrior, font_japanese, engineSound, musicPlayer, context)
    }

    private fun drawPlayer(canvas: Canvas, w: Float, h: Float, state: GameState) {
        val specs = VehicleDatabase.getSelectedVehicle()
        val bitmap = playerBitmap ?: return
        val baseRoadScreenWidth = w * 0.35f * GameSettings.ROAD_STRETCH
        val singleLaneScreenWidth = baseRoadScreenWidth * (GameSettings.SINGLE_LANE_WIDTH / GameSettings.ROAD_WIDTH)
        val carW = singleLaneScreenWidth * (specs.widthM / GameSettings.SINGLE_LANE_WIDTH)
        val carH = carW * (bitmap.height.toFloat() / bitmap.width.toFloat())
        val carX = w / 2 - carW / 2
        val carY = h * 0.92f - carH
        val sunRelHeading = GameSettings.SUN_WORLD_HEADING - state.playerHeading
        val pixelsPerMeter = singleLaneScreenWidth / GameSettings.SINGLE_LANE_WIDTH
        ShadowRenderer.drawCarShadow(canvas, bitmap, carX, carY, carW, carH, sunRelHeading, specs.heightM, specs.lengthM, pixelsPerMeter)
        canvas.save()
        // ハンドルを曲げた分だけ車体を回転させる (carVisualRotation) を追加
        canvas.rotate(state.visualTilt + state.roadShake + state.carVisualRotation, carX + carW / 2, carY + carH / 2)
        playerDstRect.set(carX, carY, carX + carW, carY + carH)
        canvas.drawBitmap(bitmap, null, playerDstRect, mainPaint)
        if (state.isBraking) canvas.drawBitmap(bitmap, null, playerDstRect, brakePaint)
        ShadowRenderer.drawCarGloss(canvas, carX, carY, carW, carH, sunRelHeading)
        canvas.restore() 
    }

    fun handleTouch(x: Float, y: Float, state: GameState, isTapEvent: Boolean, context: Context): Boolean {
        if (state.isSettingsOpen) {
            if (isTapEvent) {
                if (HDU.handleSettingsTouch(x, y, state)) return true
                state.isSettingsOpen = false; return true
            }
            return true
        }
        if (settingsTouchArea.contains(x, y)) { if (isTapEvent) state.isSettingsOpen = !state.isSettingsOpen; return true }
        if (miniMapTouchArea.contains(x, y)) { if (isTapEvent) state.isMapLongRange = !state.isMapLongRange; return true }
        if (!state.isDeveloperMode) return false
        if (isTapEvent) {
            if (guardrailToggleArea.contains(x, y)) { DeveloperSettings.showRightGuardrail = !DeveloperSettings.showRightGuardrail; return true }
            else if (postToggleArea.contains(x, y)) { DeveloperSettings.showGuardrailPosts = !DeveloperSettings.showGuardrailPosts; return true }
        } else return guardrailToggleArea.contains(x, y) || postToggleArea.contains(x, y)
        return false
    }
}
