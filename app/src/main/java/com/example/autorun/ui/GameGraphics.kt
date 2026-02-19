package com.example.autorun.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RectF
import android.graphics.Typeface
import android.widget.Toast
import com.example.autorun.audio.EngineSoundRenderer
import com.example.autorun.audio.MusicPlayer
import com.example.autorun.config.DeveloperSettings
import com.example.autorun.config.GameSettings
import com.example.autorun.core.GameState
import com.example.autorun.config.GameUIParameters
import com.example.autorun.core.CourseManager
import com.example.autorun.data.road.RoadGeometry
import com.example.autorun.data.vehicle.VehicleDatabase
import com.example.autorun.data.vehicle.VehicleSpecs

/**
 * 【GameGraphics: グラフィック描画の中枢】
 * 車両の描画（ステアリング連動タイヤ含む）と全体レイアウトを管理。
 */
object GameGraphics {

    private const val COLOR_BRAKE_OVERLAY = 0x66FF0000
    private val COLOR_WHEEL = 0xFF111111.toInt()

    private object DrawingConstants {
        const val PLAYER_CAR_Y_ANCHOR = 0.92f
        const val WHEEL_HEIGHT_TO_CAR_HEIGHT_RATIO = 0.35f
        const val WHEEL_Y_OFFSET_TO_CAR_HEIGHT_RATIO = 0.65f
        const val WHEEL_X_OFFSET_RATIO = 0.15f
        const val WHEEL_X_OFFSET_RATIO_COMPLEMENT = 0.85f 
        const val CAR_SIZE_SCREEN_RATIO = 0.1936f
        // パフォーマンス向上のため、描画レイヤー数を削減 (25 -> 10)
        const val CAR_3D_LAYERS = 10
    }

    private val mainPaint = Paint().apply { isAntiAlias = true; isFilterBitmap = true }
    private val wheelPaint = Paint().apply { isAntiAlias = true; color = COLOR_WHEEL }
    private val brakePaint = Paint().apply { isAntiAlias = true; colorFilter = PorterDuffColorFilter(COLOR_BRAKE_OVERLAY.toInt(), PorterDuff.Mode.SRC_ATOP) }

    private val developerModeTouchArea = RectF(20f, 150f, 20f + GameUIParameters.STATUS_BOX_WIDTH, 150f + GameUIParameters.STATUS_BOX_HEIGHT)
    private val guardrailToggleArea = RectF()
    private val postToggleArea = RectF()
    private val playerDstRect = RectF()
    private val opponentDstRect = RectF()

    private var playerBitmap: Bitmap? = null
    private var lastLoadedResId: Int = -1

    init {
        guardrailToggleArea.set(developerModeTouchArea.left, developerModeTouchArea.top, developerModeTouchArea.right, developerModeTouchArea.centerY())
        postToggleArea.set(developerModeTouchArea.left, developerModeTouchArea.centerY(), developerModeTouchArea.right, developerModeTouchArea.bottom)
    }

    private fun loadResources(context: Context) {
        val specs = VehicleDatabase.getSelectedVehicle()
        if (specs.imageResId != lastLoadedResId || playerBitmap == null) {
            playerBitmap = BitmapFactory.decodeResource(context.resources, specs.imageResId)
            lastLoadedResId = specs.imageResId
        }
        BackgroundRenderer.loadResources(context)
    }

    fun drawAll(canvas: Canvas, w: Float, h: Float, state: GameState, currentFps: Int,
                steerBarRect: RectF, brakeRect: RectF, accelRect: RectF,
                font7Bar: Typeface?, fontWarrior: Typeface?, fontJapanese: Typeface?, 
                context: Context, engineSound: EngineSoundRenderer?, musicPlayer: MusicPlayer?
    ) {
        loadResources(context)
        val playerDist = state.playerDistance
        val segLen = GameSettings.SEGMENT_LENGTH
        val slope = CourseManager.getCurrentAngle(playerDist / segLen)
        val horizon = h * 0.5f + (slope * 10f)
        
        BackgroundRenderer.draw(canvas, w, h, horizon, state)
        
        val specs = VehicleDatabase.getSelectedVehicle()
        if (playerBitmap != null) {
            val targetCarScreenWidth = w * DrawingConstants.CAR_SIZE_SCREEN_RATIO
            val pixelsPerMeter = targetCarScreenWidth / specs.widthM
            GameSettings.ROAD_STRETCH = (pixelsPerMeter * GameSettings.ROAD_WIDTH) / (w * 0.5f)
        }
        
        RoadRenderer.draw(canvas, w, h, state, horizon)
        drawOpponentCars(canvas, w, h, state, specs)
        drawPlayer(canvas, w, h, state, specs, horizon)
        HDU.draw(canvas, w, h, state, currentFps, font7Bar, fontWarrior, fontJapanese, engineSound, musicPlayer, context)
    }

    private fun drawOpponentCars(canvas: Canvas, w: Float, h: Float, state: GameState, specs: VehicleSpecs) {
        val bitmap = playerBitmap ?: return
        val playerDist = state.playerDistance; val fov = GameSettings.FOV; val stretch = GameSettings.ROAD_STRETCH
        for (car in state.opponentCars) {
            val relativeZ = car.getRelativeZ(playerDist)
            if (relativeZ <= 0.1f || relativeZ > GameSettings.DRAW_DISTANCE) continue
            val p = RoadGeometry.interpolate(relativeZ) ?: continue
            val scale = fov / (relativeZ + 0.1f); val carW = scale * specs.widthM * stretch; val carH = carW * (bitmap.height.toFloat() / bitmap.width.toFloat())
            val carX = p.centerX + (car.laneOffset * scale * stretch) - (carW / 2f); val carY = p.y - carH
            opponentDstRect.set(carX, carY, carX + carW, carY + carH)
            mainPaint.alpha = ((1.0f - (relativeZ / GameSettings.DRAW_DISTANCE)) * 255).toInt().coerceIn(0, 255)
            canvas.drawBitmap(bitmap, null, opponentDstRect, mainPaint)
            mainPaint.alpha = 255
        }
    }

    private fun drawPlayer(canvas: Canvas, w: Float, h: Float, state: GameState, specs: VehicleSpecs, horizon: Float) {
        val pixelsPerMeter = (w * 0.5f * GameSettings.ROAD_STRETCH) / GameSettings.ROAD_WIDTH
        val carW = pixelsPerMeter * specs.widthM
        val original = playerBitmap
        val carH = original?.let { carW * (it.height.toFloat() / it.width.toFloat()) } ?: (carW * 0.4f)
        
        val carX = w / 2 - carW / 2
        val carBaseY = h * DrawingConstants.PLAYER_CAR_Y_ANCHOR - carH
        val carBodyY = carBaseY + state.carVerticalShake + state.visualPitch
        val sunRelHeading = GameSettings.SUN_WORLD_HEADING - state.playerWorldHeading
        
        ShadowRenderer.drawCarShadow(canvas, playerBitmap, carX, carBaseY, carW, carH, sunRelHeading, specs.heightM, specs.lengthM, pixelsPerMeter)
        
        canvas.save()
        canvas.rotate(state.visualTilt + state.roadShake, carX + carW / 2, carBaseY + carH / 2)
        
        // --- タイヤ（ホイール）の描画 ---
        val wheelW = pixelsPerMeter * specs.tireWidthM
        val wheelH = carH * DrawingConstants.WHEEL_HEIGHT_TO_CAR_HEIGHT_RATIO
        val wheelY = carBaseY + carH * DrawingConstants.WHEEL_Y_OFFSET_TO_CAR_HEIGHT_RATIO + GameSettings.WHEEL_HEIGHT_OFFSET
        val treadOffset = GameSettings.WHEEL_X_OFFSET
        val tireTurnAngle = state.steeringInput * 25f // 視覚的なタイヤの切れ角

        // 左前輪
        canvas.save()
        val wheelLX = carX + carW * DrawingConstants.WHEEL_X_OFFSET_RATIO - treadOffset
        canvas.rotate(tireTurnAngle, wheelLX + wheelW/2, wheelY + wheelH/2)
        canvas.drawRoundRect(wheelLX, wheelY, wheelLX + wheelW, wheelY + wheelH, 4f, 4f, wheelPaint)
        canvas.restore()

        // 右前輪
        canvas.save()
        val wheelRX = carX + carW * DrawingConstants.WHEEL_X_OFFSET_RATIO_COMPLEMENT - wheelW + treadOffset
        canvas.rotate(tireTurnAngle, wheelRX + wheelW/2, wheelY + wheelH/2)
        canvas.drawRoundRect(wheelRX, wheelY, wheelRX + wheelW, wheelY + wheelH, 4f, 4f, wheelPaint)
        canvas.restore()
        
        // 車体描画 (擬似3D厚み)
        original?.let { src ->
            val numLayers = DrawingConstants.CAR_3D_LAYERS
            val z0 = GameSettings.FOV / pixelsPerMeter
            val vpX = w / 2f
            
            for (i in numLayers downTo 0) {
                val dist = (i.toFloat() / numLayers) * specs.lengthM
                val ratio = z0 / (z0 + dist)
                val layerW = carW * ratio
                val layerH = carH * ratio
                val layerX = vpX + (carX + carW / 2f - vpX) * ratio - layerW / 2f
                val layerBottomY = horizon + (carBaseY + carH - horizon) * ratio
                val layerBodyY = layerBottomY - layerH + (state.carVerticalShake + state.visualPitch) * ratio
                
                playerDstRect.set(layerX, layerBodyY, layerX + layerW, layerBodyY + layerH)
                if (i > 0) {
                    val darken = 0.7f + 0.3f * (1.0f - i.toFloat() / numLayers)
                    val c = (darken * 255).toInt().coerceIn(0, 255)
                    mainPaint.colorFilter = PorterDuffColorFilter(Color.rgb(c, c, c), PorterDuff.Mode.MULTIPLY)
                } else {
                    mainPaint.colorFilter = null
                }
                canvas.drawBitmap(src, null, playerDstRect, mainPaint)
            }
            mainPaint.colorFilter = null
            
            if (state.isBraking) {
                playerDstRect.set(carX, carBodyY, carX + carW, carBodyY + carH)
                canvas.drawBitmap(src, null, playerDstRect, brakePaint)
            }
        }

        ShadowRenderer.drawCarGloss(canvas, carX, carBodyY, carW, carH, sunRelHeading)
        canvas.restore()
    }

    fun handleTouch(x: Float, y: Float, state: GameState, isTapEvent: Boolean, context: Context, musicPlayer: MusicPlayer?): Boolean {
        if (state.isLayoutMode) {
            val hitButton = HDULayoutEditor.layoutSaveRect.contains(x, y) || HDULayoutEditor.layoutResetRect.contains(x, y) || HDULayoutEditor.layoutExitRect.contains(x, y)
            if (hitButton) {
                if (isTapEvent) {
                    when {
                        HDULayoutEditor.layoutSaveRect.contains(x, y) -> { GameSettings.saveLayout(context); state.isLayoutMode = false; Toast.makeText(context, "Layout Saved", Toast.LENGTH_SHORT).show() }
                        HDULayoutEditor.layoutResetRect.contains(x, y) -> { GameSettings.resetLayout(context); Toast.makeText(context, "Layout Reset", Toast.LENGTH_SHORT).show() }
                        HDULayoutEditor.layoutExitRect.contains(x, y) -> { GameSettings.init(context); state.isLayoutMode = false; Toast.makeText(context, "Changes Canceled", Toast.LENGTH_SHORT).show() }
                    }
                }
                return true 
            }
            if (HDULayoutEditor.editorHandleRect.contains(x, y)) return true
            if (isTapEvent) {
                val hitId = findHitUiId(x, y)
                if (hitId != -1) { state.selectedUiId = hitId; return true }
            }
            if (HDULayoutEditor.editorPanelRect.contains(x, y)) return true
        }

        if (state.isSettingsOpen) {
            if (isTapEvent) {
                if (HDU.handleSettingsTouch(x, y, state)) return true
                state.isSettingsOpen = false; return true
            }
            return true
        }

        if (HDU.settingsRect.contains(x, y)) { if (isTapEvent) state.isSettingsOpen = !state.isSettingsOpen; return true }
        if (HDUMap.handleTouch(x, y, state, isTapEvent, context, musicPlayer)) return true
        if (HDU.mapRect.contains(x, y)) { if (isTapEvent) state.isMapLongRange = !state.isMapLongRange; return true }

        if (!state.isDeveloperMode) return false
        if (isTapEvent) {
            if (developerModeTouchArea.contains(x, y)) {
                if (guardrailToggleArea.contains(x, y)) DeveloperSettings.showRightGuardrail = !DeveloperSettings.showRightGuardrail
                else if (postToggleArea.contains(x, y)) DeveloperSettings.showGuardrailPosts = !DeveloperSettings.showGuardrailPosts
                return true
            }
        }
        return developerModeTouchArea.contains(x, y)
    }

    fun updateSliderValue(sliderId: Int, x: Float, state: GameState) {
        val rect = if (sliderId == 0) HDULayoutEditor.sliderAlphaRect else HDULayoutEditor.sliderScaleRect
        val progress = ((x - rect.left) / rect.width()).coerceIn(0f, 1f)
        if (sliderId == 0) updateUiAlpha(state.selectedUiId, progress)
        else updateUiScale(state.selectedUiId, 0.5f + progress * 2.0f)
    }
    
    private fun findHitUiId(x: Float, y: Float): Int {
        return when {
            HDU.statusRect.contains(x, y) -> 0
            HDU.mapRect.contains(x, y) -> 1
            HDU.settingsRect.contains(x, y) -> 2
            HDU.brakeRect.contains(x, y) -> 3
            HDU.compassRect.contains(x, y) -> 5
            HDU.rpmRect.contains(x, y) -> 6
            HDU.speedRect.contains(x, y) -> 7
            HDU.steerRect.contains(x, y) -> 8
            HDU.throttleRect.contains(x, y) -> 9
            else -> -1
        }
    }
    
    private fun updateUiAlpha(id: Int, alpha: Float) {
        when (id) {
            0 -> GameSettings.UI_ALPHA_STATUS = alpha; 1 -> GameSettings.UI_ALPHA_MAP = alpha; 3 -> GameSettings.UI_ALPHA_BRAKE = alpha
            6 -> GameSettings.UI_ALPHA_RPM = alpha; 7 -> GameSettings.UI_ALPHA_SPEED = alpha; 8 -> GameSettings.UI_ALPHA_STEER = alpha; 9 -> GameSettings.UI_ALPHA_THROTTLE = alpha
        }
    }
    
    private fun updateUiScale(id: Int, scale: Float) {
        when (id) {
            0 -> GameSettings.UI_SCALE_STATUS = scale; 1 -> GameSettings.UI_SCALE_MAP = scale; 3 -> GameSettings.UI_SCALE_BRAKE = scale
            6 -> GameSettings.UI_SCALE_RPM = scale; 7 -> GameSettings.UI_SCALE_SPEED = scale; 8 -> GameSettings.UI_SCALE_STEER = scale; 9 -> GameSettings.UI_SCALE_THROTTLE = scale
        }
    }
}
