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
import kotlin.math.atan2

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
    private val steerStartAngle = mutableMapOf<Int, Float>()
    private val steerInitialInput = mutableMapOf<Int, Float>()
    private val throttleStartPosY = mutableMapOf<Int, Float>()

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
                updateCameraByTouch() 
                state.update()
                accumulator -= fixedDt
            }
            engineSound.update(state.engineRPM, state.throttle, state.isTurboActive)
            exhaustSound.update(state.engineRPM, state.throttle)
            turboSound.update(state.engineRPM, state.throttle, if (state.isTurboActive) state.turboBoost else 0f)
            windSound.update(state.calculatedSpeedKmH)
            brakeSound.update(state.isBraking, state.calculatedSpeedKmH)
            tireSquealSound.update(state.tireSlipRatio, state.calculatedSpeedKmH, state.isBraking)
            invalidate()
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    private val activeCameraDirs = mutableSetOf<Int>() 

    init {
        setLayerType(LAYER_TYPE_HARDWARE, null)
        Choreographer.getInstance().postFrameCallback(frameCallback)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        val w = width.toFloat(); val h = height.toFloat(); if (w <= 0 || h <= 0) return
        HDU.updateLayoutRects(w, h)
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        if (w == 0f || h == 0f) return
        GameGraphics.drawAll(canvas, w, h, state, currentFps, steerBarRect, HDU.brakeRect, RectF(), font7Bar, fontWarrior, fontGotika, context, engineSound, musicPlayer)
    }

    private fun updateCameraByTouch() {
        val speed = 0.05f
        if (activeCameraDirs.contains(0)) state.camPitchOffset -= speed
        if (activeCameraDirs.contains(1)) state.camPitchOffset += speed
        if (activeCameraDirs.contains(2)) state.camYawOffset -= speed
        if (activeCameraDirs.contains(3)) state.camYawOffset += speed
        if (activeCameraDirs.contains(4)) state.camZOffset += 0.5f
        if (activeCameraDirs.contains(5)) state.camZOffset -= 0.5f
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val action = event.actionMasked
        val actionIndex = event.actionIndex
        val x = event.getX(actionIndex)
        val y = event.getY(actionIndex)
        val pointerId = event.getPointerId(actionIndex)

        when (action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                when {
                    HDU.camUpRect.contains(x, y) -> activeCameraDirs.add(0)
                    HDU.camDownRect.contains(x, y) -> activeCameraDirs.add(1)
                    HDU.camLeftRect.contains(x, y) -> activeCameraDirs.add(2)
                    HDU.camRightRect.contains(x, y) -> activeCameraDirs.add(3)
                    HDU.camForwardRect.contains(x, y) -> activeCameraDirs.add(4)
                    HDU.camBackwardRect.contains(x, y) -> activeCameraDirs.add(5)
                    HDU.camResetRect.contains(x, y) -> state.resetCamera()
                    HDU.steerRect.contains(x, y) -> {
                        steerStartAngle[pointerId] = Math.toDegrees(atan2((y - HDU.steerRect.centerY()).toDouble(), (x - HDU.steerRect.centerX()).toDouble())).toFloat()
                        steerInitialInput[pointerId] = state.steeringInput
                    }
                    x < width * 0.45f -> steerStartPosX[pointerId] = x
                    else -> throttleStartPosY[pointerId] = y
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                activeCameraDirs.clear() 
                steerStartPosX.remove(pointerId); steerStartAngle.remove(pointerId); throttleStartPosY.remove(pointerId)
            }
        }

        var sInput = 0f; var tInput = 0f; var br = false
        var isSteeringActive = false; var isThrottleActive = false
        for (i in 0 until event.pointerCount) {
            val id = event.getPointerId(i)
            val tx = event.getX(i); val ty = event.getY(i)
            if (steerStartAngle.containsKey(id)) {
                val currentAngle = Math.toDegrees(atan2((ty - HDU.steerRect.centerY()).toDouble(), (tx - HDU.steerRect.centerX()).toDouble())).toFloat()
                var delta = currentAngle - steerStartAngle[id]!!
                if (delta > 180f) delta -= 360f else if (delta < -180f) delta += 360f
                sInput = (steerInitialInput[id]!! + delta / 135f).coerceIn(-1f, 1f)
                isSteeringActive = true
            }
            if (throttleStartPosY.containsKey(id)) {
                tInput = ((throttleStartPosY[id]!! - ty) / (height / 8f)).coerceIn(0f, 1f)
                isThrottleActive = true
            }
            if (HDU.brakeRect.contains(tx, ty)) br = true
        }
        state.rawSteeringInput = if (isSteeringActive) sInput else 0f
        state.rawThrottleInput = if (isThrottleActive) tInput else 0f
        state.isBraking = br
        return true
    }
}
