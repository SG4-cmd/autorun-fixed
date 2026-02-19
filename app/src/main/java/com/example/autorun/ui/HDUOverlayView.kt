package com.example.autorun.ui

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.content.res.ResourcesCompat
import com.example.autorun.R
import com.example.autorun.audio.EngineSoundRenderer
import com.example.autorun.audio.MusicPlayer
import com.example.autorun.core.GameState
import kotlin.math.atan2

/**
 * 【HDUOverlayView】
 * 3Dエンジンの上にUIを重ね、タッチ操作を一括管理します。
 */
class HDUOverlayView(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    private var gameState: GameState? = null
    private var engineSound: EngineSoundRenderer? = null
    private var musicPlayer: MusicPlayer? = null
    
    private val font7Bar by lazy { try { ResourcesCompat.getFont(context, R.font.gotikakutto_005_851) } catch (e: Exception) { null } }
    private val fontWarrior by lazy { try { ResourcesCompat.getFont(context, R.font.gotikakutto_005_851) } catch (e: Exception) { null } }

    private val steerStartAngle = mutableMapOf<Int, Float>()
    private val steerInitialInput = mutableMapOf<Int, Float>()
    private val throttleStartPosY = mutableMapOf<Int, Float>()

    private var lastFpsUpdateTime = 0L
    private var frameCount = 0
    private var currentFps = 0

    // デベロッパーモード切り替え用
    private var isSpeedometerTouching = false
    private var isTachometerTouching = false
    private var dualTouchStartTime = 0L

    fun setGameState(state: GameState) {
        this.gameState = state
    }
    
    fun setEngineSound(engineSound: EngineSoundRenderer) {
        this.engineSound = engineSound
    }
    
    fun setMusicPlayer(player: MusicPlayer) {
        this.musicPlayer = player
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        val w = (right - left).toFloat()
        val h = (bottom - top).toFloat()
        if (w > 0 && h > 0) {
            HDU.updateLayoutRects(w, h)
        }
    }

    override fun onDraw(canvas: Canvas) {
        val state = gameState ?: return
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        calculateFps(state)
        checkDeveloperModeActivation(state)

        HDU.draw(canvas, w, h, state, currentFps, font7Bar, fontWarrior, fontWarrior, engineSound, musicPlayer, context)
        invalidate()
    }

    private fun checkDeveloperModeActivation(state: GameState) {
        if (isSpeedometerTouching && isTachometerTouching) {
            if (dualTouchStartTime == 0L) {
                dualTouchStartTime = System.currentTimeMillis()
            } else if (System.currentTimeMillis() - dualTouchStartTime > 1000) {
                state.isDeveloperMode = !state.isDeveloperMode
                isSpeedometerTouching = false
                isTachometerTouching = false
                dualTouchStartTime = 0L
            }
        } else {
            dualTouchStartTime = 0L
        }
    }

    private fun calculateFps(state: GameState) {
        val now = System.currentTimeMillis()
        if (lastFpsUpdateTime == 0L) {
            lastFpsUpdateTime = now
            return
        }
        
        frameCount++
        if (now - lastFpsUpdateTime >= 1000) {
            currentFps = frameCount
            state.currentFps = currentFps
            frameCount = 0
            lastFpsUpdateTime = now
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val state = gameState ?: return false
        val action = event.actionMasked
        val actionIndex = event.actionIndex
        val x = event.getX(actionIndex)
        val y = event.getY(actionIndex)
        val pointerId = event.getPointerId(actionIndex)

        // GesoEngine3Dに一本化したタッチ判定を優先
        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN) {
             if (GesoEngine3D.handleTouch(x, y, state, true)) return true
        }

        when (action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                if (HDU.speedRect.contains(x, y)) isSpeedometerTouching = true
                if (HDU.rpmRect.contains(x, y)) isTachometerTouching = true

                if (HDUMap.handleTouch(x, y, state, false, context, musicPlayer)) {
                    return true
                }

                when {
                    HDU.camUpRect.contains(x, y) -> state.activeCameraDirs.add(0)
                    HDU.camDownRect.contains(x, y) -> state.activeCameraDirs.add(1)
                    HDU.camLeftRect.contains(x, y) -> state.activeCameraDirs.add(2)
                    HDU.camRightRect.contains(x, y) -> state.activeCameraDirs.add(3)
                    HDU.camForwardRect.contains(x, y) -> state.activeCameraDirs.add(4)
                    HDU.camBackwardRect.contains(x, y) -> state.activeCameraDirs.add(5)
                    HDU.camResetRect.contains(x, y) -> state.resetCamera()
                    HDU.steerRect.contains(x, y) -> {
                        steerStartAngle[pointerId] = Math.toDegrees(atan2((y - HDU.steerRect.centerY()).toDouble(), (x - HDU.steerRect.centerX()).toDouble())).toFloat()
                        steerInitialInput[pointerId] = state.steeringInput
                    }
                    x > width * 0.5f -> throttleStartPosY[pointerId] = y
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> {
                if (HDU.speedRect.contains(x, y)) isSpeedometerTouching = false
                if (HDU.rpmRect.contains(x, y)) isTachometerTouching = false
                
                if (event.pointerCount <= 1) {
                    isSpeedometerTouching = false
                    isTachometerTouching = false
                }

                if (action != MotionEvent.ACTION_CANCEL) {
                    if (HDUMap.handleTouch(x, y, state, true, context, musicPlayer)) {
                        cleanUpPointer(pointerId)
                        return true
                    }
                }

                cleanUpPointer(pointerId)
                
                if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                    state.activeCameraDirs.clear()
                    state.radioBtnDir = 0
                    state.radioBtnDownStartTime = 0
                } else {
                    refreshCameraDirs(event, actionIndex, state)
                }
            }
            MotionEvent.ACTION_MOVE -> {
                updateMeterTouchState(event)
            }
        }

        handleDrivingInput(event, state)
        performClick()
        return true
    }
    
    private fun updateMeterTouchState(event: MotionEvent) {
        var speedFound = false
        var rpmFound = false
        for (i in 0 until event.pointerCount) {
            val tx = event.getX(i)
            val ty = event.getY(i)
            if (HDU.speedRect.contains(tx, ty)) speedFound = true
            if (HDU.rpmRect.contains(tx, ty)) rpmFound = true
        }
        isSpeedometerTouching = speedFound
        isTachometerTouching = rpmFound
    }

    private fun cleanUpPointer(pointerId: Int) {
        steerStartAngle.remove(pointerId)
        throttleStartPosY.remove(pointerId)
    }

    private fun refreshCameraDirs(event: MotionEvent, actionIndex: Int, state: GameState) {
        state.activeCameraDirs.clear()
        for (i in 0 until event.pointerCount) {
            if (i == actionIndex) continue
            val tx = event.getX(i); val ty = event.getY(i)
            if (HDU.camUpRect.contains(tx, ty)) state.activeCameraDirs.add(0)
            if (HDU.camDownRect.contains(tx, ty)) state.activeCameraDirs.add(1)
            if (HDU.camLeftRect.contains(tx, ty)) state.activeCameraDirs.add(2)
            if (HDU.camRightRect.contains(tx, ty)) state.activeCameraDirs.add(3)
            if (HDU.camForwardRect.contains(tx, ty)) state.activeCameraDirs.add(4)
            if (HDU.camBackwardRect.contains(tx, ty)) state.activeCameraDirs.add(5)
        }
    }

    private fun handleDrivingInput(event: MotionEvent, state: GameState) {
        var sInput = 0f; var tInput = 0f
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
            if (HDU.brakeRect.contains(tx, ty)) state.isBraking = true
        }
        state.rawSteeringInput = if (isSteeringActive) sInput else 0f
        state.rawThrottleInput = if (isThrottleActive) tInput else 0f
        // ブレーキ判定の修正：ループ内でリセットしないように注意
        var anyBrake = false
        for (i in 0 until event.pointerCount) {
            if (HDU.brakeRect.contains(event.getX(i), event.getY(i))) anyBrake = true
        }
        state.isBraking = anyBrake
    }
    
    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}
