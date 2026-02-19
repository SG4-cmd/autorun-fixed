package com.example.autorun.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.Choreographer
import android.view.MotionEvent
import android.view.View
import androidx.core.content.res.ResourcesCompat
import com.example.autorun.R
import com.example.autorun.audio.BrakeSoundRenderer
import com.example.autorun.audio.EngineSoundRenderer
import com.example.autorun.audio.ExhaustSoundRenderer
import com.example.autorun.audio.MusicPlayer
import com.example.autorun.audio.TireSquealRenderer
import com.example.autorun.audio.TurboSoundRenderer
import com.example.autorun.audio.WindSoundRenderer
import com.example.autorun.config.GamePerformanceSettings
import com.example.autorun.config.GameSettings
import com.example.autorun.core.GameState
import kotlin.math.abs

class GameView(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    private val state = GameState()
    private val engineSound = EngineSoundRenderer()
    private val exhaustSound = ExhaustSoundRenderer()
    private val turboSound = TurboSoundRenderer()
    private val windSound = WindSoundRenderer()
    private val brakeSound = BrakeSoundRenderer()
    private val tireSquealSound = TireSquealRenderer()
    private val musicPlayer = MusicPlayer(context)
    private var frameCount = 0
    private var lastFpsUpdateTime = 0L
    private var currentFps = 0
    private val steerBarRect = RectF()
    private val brakeRect = RectF()

    private val font7Bar: Typeface? by lazy {
        try { ResourcesCompat.getFont(context, R.font.gotikakutto_005_851) } catch (e: Exception) { Typeface.DEFAULT }
    }
    private val fontWarrior: Typeface? by lazy {
        try { ResourcesCompat.getFont(context, R.font.gotikakutto_005_851) } catch (e: Exception) { Typeface.DEFAULT }
    }
    private val fontGotika: Typeface? by lazy {
        try { ResourcesCompat.getFont(context, R.font.gotikakutto_005_851) } catch (e: Exception) { Typeface.DEFAULT }
    }

    private val debugTouchPointerIds = mutableSetOf<Int>()
    private val steerStartPosX = mutableMapOf<Int, Float>()
    private val throttleStartPosY = mutableMapOf<Int, Float>()

    private var draggingUiId: Int = -1 
    private var dragLastX = 0f
    private var dragLastY = 0f
    private var isMoved = false
    private var isDraggingPanel = false
    private var isDraggingDebugPanel = false
    private var isResizingDebugPanel = false
    private var activeSliderId = -1 
    private var sliderTouchOffsetX = 0f 

    private var isPinching = false
    private var lastPinchDist = 0f

    private var offscreenBitmap: Bitmap? = null
    private var offscreenCanvas: Canvas? = null
    private val renderPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private var lastTargetWidth = -1
    private var lastTargetHeight = -1

    private var lastFrameTimeNanos: Long = 0L
    private var accumulator: Float = 0f
    private val fixedDt = GamePerformanceSettings.PHYSICS_DT

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (lastFrameTimeNanos == 0L) lastFrameTimeNanos = frameTimeNanos
            val elapsedSeconds = (frameTimeNanos - lastFrameTimeNanos) / 1_000_000_000f
            lastFrameTimeNanos = frameTimeNanos
            val frameTime = elapsedSeconds.coerceAtMost(0.25f)
            accumulator += frameTime
            while (accumulator >= fixedDt) {
                state.update()
                accumulator -= fixedDt
            }
            engineSound.update(state.engineRPM, state.throttle, state.isTurboActive)
            exhaustSound.update(state.engineRPM, state.throttle)
            val boost = if (state.isTurboActive) (state.engineRPM / 9000f * state.throttle).coerceIn(0f, 1f) else 0f
            turboSound.update(state.engineRPM, state.throttle, boost)
            windSound.update(state.calculatedSpeedKmH)
            brakeSound.update(state.isBraking, state.calculatedSpeedKmH)
            tireSquealSound.update(state.tireSlipRatio, state.calculatedSpeedKmH, state.isBraking)
            
            invalidate()
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    init {
        setLayerType(LAYER_TYPE_HARDWARE, null)
        Choreographer.getInstance().postFrameCallback(frameCallback)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        engineSound.start()
        exhaustSound.start()
        turboSound.start()
        windSound.start()
        brakeSound.start()
        tireSquealSound.start()
        // musicPlayer.start() // 起動時の自動再生を停止
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        engineSound.stop()
        exhaustSound.stop()
        turboSound.stop()
        windSound.stop()
        brakeSound.stop()
        tireSquealSound.stop()
        musicPlayer.stop()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (visibility == VISIBLE) {
            engineSound.start()
            exhaustSound.start()
            turboSound.start()
            windSound.start()
            brakeSound.start()
            tireSquealSound.start()
            // 起動時・復帰時の自動再生を行わない
        } else {
            engineSound.stop()
            exhaustSound.stop()
            turboSound.stop()
            windSound.stop()
            brakeSound.stop()
            tireSquealSound.stop()
            musicPlayer.stop()
        }
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        val w = width.toFloat(); val h = height.toFloat()
        if (w <= 0 || h <= 0) return
        HDU.updateLayoutRects(w, h)
        val btnW = w * 0.12f; val btnH = h * 0.18f; val margin = 40f
        steerBarRect.set(margin, h - btnH - margin, margin + btnW * 2.2f, h - margin)
    }

    override fun onDraw(canvas: Canvas) {
        updateFps()
        val w = width.toFloat(); val h = height.toFloat()
        if (w == 0f || h == 0f) return
        brakeRect.set(HDU.brakeRect)
        val targetH = GameSettings.getTargetHeight(h)
        if (targetH < h) {
            val scale = targetH.toFloat() / h
            val targetW = (w * scale).toInt()
            if (offscreenBitmap == null || lastTargetWidth != targetW || lastTargetHeight != targetH) {
                offscreenBitmap?.recycle()
                offscreenBitmap = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.RGB_565)
                offscreenCanvas = Canvas(offscreenBitmap!!)
                lastTargetWidth = targetW
                lastTargetHeight = targetH
            }
            val bufferCanvas = offscreenCanvas!!
            bufferCanvas.save(); bufferCanvas.scale(scale, scale)
            GameGraphics.drawAll(bufferCanvas, w, h, state, currentFps, steerBarRect, brakeRect, RectF(), font7Bar, fontWarrior, fontGotika, context, engineSound, musicPlayer)
            bufferCanvas.restore()
            canvas.drawBitmap(offscreenBitmap!!, null, Rect(0, 0, width, height), renderPaint)
        } else {
            GameGraphics.drawAll(canvas, w, h, state, currentFps, steerBarRect, brakeRect, RectF(), font7Bar, fontWarrior, fontGotika, context, engineSound, musicPlayer)
        }
    }

    private fun updateFps() {
        val now = System.currentTimeMillis(); frameCount++
        if (now - lastFpsUpdateTime >= 1000) { currentFps = frameCount; frameCount = 0; lastFpsUpdateTime = now }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val action = event.actionMasked
        val actionIndex = event.actionIndex
        val x = event.getX(actionIndex)
        val y = event.getY(actionIndex)
        val pointerId = event.getPointerId(actionIndex)

        if (state.isDeveloperMode) {
            when (action) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                    if (HDUDebugOverlay.resizeHandleRect.contains(x, y)) {
                        isResizingDebugPanel = true
                        dragLastX = x; dragLastY = y
                        return true
                    }
                    if (HDUDebugOverlay.dragHandleRect.contains(x, y)) {
                        isDraggingDebugPanel = true
                        dragLastX = x; dragLastY = y
                        return true
                    }
                    if (HDUDebugOverlay.panelRect.contains(x, y)) {
                        HDUDebugOverlay.handleTouch(x, y, engineSound)
                        return true
                    }
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = x - dragLastX; val dy = y - dragLastY
                    if (isResizingDebugPanel) {
                        GameSettings.UI_WIDTH_DEBUG_PANEL = (GameSettings.UI_WIDTH_DEBUG_PANEL + dx).coerceAtLeast(600f)
                        GameSettings.UI_HEIGHT_DEBUG_PANEL = (GameSettings.UI_HEIGHT_DEBUG_PANEL + dy).coerceAtLeast(400f)
                        dragLastX = x; dragLastY = y
                        return true
                    }
                    if (isDraggingDebugPanel) {
                        GameSettings.UI_POS_DEBUG_PANEL.offset(dx, dy)
                        dragLastX = x; dragLastY = y
                        return true
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                    isDraggingDebugPanel = false
                    isResizingDebugPanel = false
                }
            }
        }

        if (state.isLayoutMode) {
            when (action) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                    if (event.pointerCount == 1) {
                        dragLastX = x; dragLastY = y; isMoved = false
                        if (state.selectedUiId != -1 && HDULayoutEditor.editorPanelRect.contains(x, y)) {
                            val values = HDU.getUiValues(state.selectedUiId)
                            val thumbXAlpha = HDULayoutEditor.sliderAlphaRect.left + HDULayoutEditor.sliderAlphaRect.width() * values.first
                            if (abs(x - thumbXAlpha) < 37f && abs(y - HDULayoutEditor.sliderAlphaRect.centerY()) < 37f) {
                                activeSliderId = 0; sliderTouchOffsetX = x - thumbXAlpha; return true
                            }
                            val thumbXScale = HDULayoutEditor.sliderScaleRect.left + HDULayoutEditor.sliderScaleRect.width() * values.second
                            if (abs(x - thumbXScale) < 37f && abs(y - HDULayoutEditor.sliderScaleRect.centerY()) < 37f) {
                                activeSliderId = 1; sliderTouchOffsetX = x - thumbXScale; return true
                            }
                        }
                        val hitId = findHitUiId(x, y)
                        if (hitId != -1) { draggingUiId = hitId; state.selectedUiId = hitId; return true }
                        if (HDULayoutEditor.editorPanelRect.contains(x, y)) {
                            if (HDULayoutEditor.editorHandleRect.contains(x, y)) { isDraggingPanel = true; return true }
                            if (GameGraphics.handleTouch(x, y, state, false, context, musicPlayer)) { draggingUiId = -2; return true }
                            return true 
                        }
                        return true
                    } else if (event.pointerCount == 2 && draggingUiId >= 0) { isPinching = true; lastPinchDist = getPinchDist(event) }
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = x - dragLastX; val dy = y - dragLastY
                    if (abs(dx) > 3f || abs(dy) > 3f) isMoved = true
                    if (activeSliderId != -1) { GameGraphics.updateSliderValue(activeSliderId, x - sliderTouchOffsetX, state); HDU.updateLayoutRects(width.toFloat(), height.toFloat()); return true }
                    if (isDraggingPanel) { GameSettings.UI_POS_EDITOR_PANEL.offset(dx, dy); dragLastX = x; dragLastY = y; HDU.updateLayoutRects(width.toFloat(), height.toFloat()); return true }
                    if (draggingUiId == -2) { GameGraphics.handleTouch(x, y, state, false, context, musicPlayer); return true }
                    if (isPinching && event.pointerCount >= 2 && draggingUiId >= 0) {
                        val currentDist = getPinchDist(event); val deltaDist = currentDist - lastPinchDist
                        updateUiScale(draggingUiId, deltaDist / 800f); lastPinchDist = currentDist; HDU.updateLayoutRects(width.toFloat(), height.toFloat()); return true
                    } else if (draggingUiId >= 0) { 
                        // 移動量を画面サイズで割って「比率」の変化として加算
                        offsetUiPos(draggingUiId, dx / width.toFloat(), dy / height.toFloat())
                        dragLastX = x; dragLastY = y; HDU.updateLayoutRects(width.toFloat(), height.toFloat()); return true 
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                    val wasDraggingPanel = isDraggingPanel; val wasSliderActive = activeSliderId != -1
                    activeSliderId = -1; isDraggingPanel = false
                    if (draggingUiId == -2) { if (!isMoved) GameGraphics.handleTouch(x, y, state, true, context, musicPlayer); draggingUiId = -1; return true }
                    if (event.pointerCount <= 1) { if (!isMoved && !wasDraggingPanel && !wasSliderActive) GameGraphics.handleTouch(x, y, state, true, context, musicPlayer); draggingUiId = -1; isPinching = false }
                }
            }
            return true
        }

        when (action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                if (GameGraphics.handleTouch(x, y, state, false, context, musicPlayer)) debugTouchPointerIds.add(pointerId)
                else if (x < width * 0.45f) steerStartPosX[pointerId] = x
                else if (x >= width * 0.45f && !brakeRect.contains(x, y)) throttleStartPosY[pointerId] = y
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                if (debugTouchPointerIds.contains(pointerId)) { GameGraphics.handleTouch(x, y, state, true, context, musicPlayer); debugTouchPointerIds.remove(pointerId) }
                steerStartPosX.remove(pointerId); throttleStartPosY.remove(pointerId)
            }
            MotionEvent.ACTION_CANCEL -> { debugTouchPointerIds.clear(); steerStartPosX.clear(); throttleStartPosY.clear() }
        }

        var sInput = 0f; var tInput = 0f; var br = false; var pSpeedo = false; var pRPM = false
        var isSteeringActive = false; var isThrottleActive = false; var throttleX = 0f; var throttleY = 0f
        for (i in 0 until event.pointerCount) {
            val id = event.getPointerId(i); if (debugTouchPointerIds.contains(id)) continue
            val pointerAction = event.actionMasked
            if ((pointerAction == MotionEvent.ACTION_POINTER_UP || pointerAction == MotionEvent.ACTION_UP) && actionIndex == i) continue
            val tx = event.getX(i); val ty = event.getY(i)
            if (HDU.rpmRect.contains(tx, ty)) pRPM = true
            if (HDU.speedRect.contains(tx, ty)) pSpeedo = true
            if (steerStartPosX.containsKey(id)) { val deltaX = tx - steerStartPosX[id]!!; sInput = (deltaX / (width / 4f)).coerceIn(-1.0f, 1.0f); isSteeringActive = true }
            if (throttleStartPosY.containsKey(id)) { val deltaY = throttleStartPosY[id]!! - ty; tInput = (deltaY / (height / 9f)).coerceIn(0f, 1.0f); isThrottleActive = true; throttleX = tx; throttleY = ty }
            if (brakeRect.contains(tx, ty)) br = true
        }
        state.rawSteeringInput = if (isSteeringActive) sInput else 0f; state.rawThrottleInput = if (isThrottleActive) tInput else 0f
        state.isBraking = br; state.isPressingSpeedo = pSpeedo; state.isPressingRPMO = pRPM
        state.isThrottleTouching = isThrottleActive; state.throttleTouchX = throttleX; state.throttleTouchY = throttleY
        return true
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
            HDU.boostRect.contains(x, y) -> 10
            else -> -1
        }
    }

    private fun offsetUiPos(id: Int, dx: Float, dy: Float) {
        when (id) {
            0 -> GameSettings.UI_POS_STATUS.offset(dx, dy)
            1 -> GameSettings.UI_POS_MAP.offset(dx, dy)
            2 -> GameSettings.UI_POS_SETTINGS.offset(dx, dy)
            3 -> GameSettings.UI_POS_BRAKE.offset(dx, dy)
            5 -> GameSettings.UI_POS_COMPASS.offset(dx, dy)
            6 -> GameSettings.UI_POS_RPM.offset(dx, dy)
            7 -> GameSettings.UI_POS_SPEED.offset(dx, dy)
            8 -> GameSettings.UI_POS_STEER.offset(dx, dy)
            9 -> GameSettings.UI_POS_THROTTLE.offset(dx, dy)
            10 -> GameSettings.UI_POS_BOOST.offset(dx, dy)
        }
    }

    private fun updateUiScale(id: Int, delta: Float) {
        when (id) {
            0 -> GameSettings.UI_SCALE_STATUS = (GameSettings.UI_SCALE_STATUS + delta).coerceIn(0.5f, 2.5f)
            1 -> GameSettings.UI_SCALE_MAP = (GameSettings.UI_SCALE_MAP + delta).coerceIn(0.5f, 2.5f)
            2 -> GameSettings.UI_SCALE_SETTINGS = (GameSettings.UI_SCALE_SETTINGS + delta).coerceIn(0.5f, 2.5f)
            3 -> GameSettings.UI_SCALE_BRAKE = (GameSettings.UI_SCALE_BRAKE + delta).coerceIn(0.5f, 2.5f)
            5 -> GameSettings.UI_SCALE_COMPASS = (GameSettings.UI_SCALE_COMPASS + delta).coerceIn(0.5f, 2.5f)
            6 -> GameSettings.UI_SCALE_RPM = (GameSettings.UI_SCALE_RPM + delta).coerceIn(0.5f, 2.5f)
            7 -> GameSettings.UI_SCALE_SPEED = (GameSettings.UI_SCALE_SPEED + delta).coerceIn(0.5f, 2.5f)
            8 -> GameSettings.UI_SCALE_STEER = (GameSettings.UI_SCALE_STEER + delta).coerceIn(0.5f, 2.5f)
            9 -> GameSettings.UI_SCALE_THROTTLE = (GameSettings.UI_SCALE_THROTTLE + delta).coerceIn(0.5f, 2.5f)
            10 -> GameSettings.UI_SCALE_BOOST = (GameSettings.UI_SCALE_BOOST + delta).coerceIn(0.5f, 2.5f)
        }
    }

    private fun getPinchDist(event: MotionEvent): Float {
        if (event.pointerCount < 2) return 0f
        val dx = event.getX(0) - event.getX(1); val dy = event.getY(0) - event.getY(1)
        return Math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()
    }
}
