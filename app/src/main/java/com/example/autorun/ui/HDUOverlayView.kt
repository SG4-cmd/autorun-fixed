package com.example.autorun.ui

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.content.res.ResourcesCompat
import com.example.autorun.R
import com.example.autorun.core.GameState
import kotlin.math.atan2

/**
 * 【HDUOverlayView】
 * 3Dエンジンの上にUIを重ね、タッチ操作を一括管理します。
 */
class HDUOverlayView(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    private var gameState: GameState? = null
    private val font7Bar by lazy { try { ResourcesCompat.getFont(context, R.font.gotikakutto_005_851) } catch (e: Exception) { null } }
    private val fontWarrior by lazy { try { ResourcesCompat.getFont(context, R.font.gotikakutto_005_851) } catch (e: Exception) { null } }

    private val steerStartAngle = mutableMapOf<Int, Float>()
    private val steerInitialInput = mutableMapOf<Int, Float>()
    private val throttleStartPosY = mutableMapOf<Int, Float>()

    fun setGameState(state: GameState) {
        this.gameState = state
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

        HDU.draw(canvas, w, h, state, 0, font7Bar, fontWarrior, fontWarrior, null, null, context)
        invalidate()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val state = gameState ?: return false
        val action = event.actionMasked
        val actionIndex = event.actionIndex
        val x = event.getX(actionIndex)
        val y = event.getY(actionIndex)
        val pointerId = event.getPointerId(actionIndex)

        when (action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
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
                steerStartAngle.remove(pointerId)
                throttleStartPosY.remove(pointerId)
                if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                    state.activeCameraDirs.clear()
                }
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
                // 修正点: delta の加算方向を反転させ、操作感を一致させる
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

        performClick()
        return true
    }
    
    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}
