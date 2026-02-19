package com.example.autorun.ui

import android.content.Context
import android.graphics.PixelFormat
import android.opengl.GLSurfaceView
import android.util.AttributeSet
import android.view.MotionEvent
import androidx.core.content.res.ResourcesCompat
import com.example.autorun.R
import com.example.autorun.audio.*
import com.example.autorun.config.GamePerformanceSettings
import com.example.autorun.config.GameSettings
import com.example.autorun.core.GameState
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.atan2

/**
 * 【GameView: 3D描画と物理・音声の統合ビュー】
 * GLSurfaceViewを継承し、ハードウェア加速された3Dグラフィックスを表示します。
 */
class GameView(context: Context, attrs: AttributeSet? = null) : GLSurfaceView(context, attrs), GLSurfaceView.Renderer {

    private val state = GameState()
    private val engineSound = EngineSoundRenderer()
    private val exhaustSound = ExhaustSoundRenderer()
    private val turboSound = TurboSoundRenderer()
    private val windSound = WindSoundRenderer()
    private val brakeSound = BrakeSoundRenderer()
    private val tireSquealSound = TireSquealRenderer()
    private val musicPlayer = MusicPlayer(context)

    // 物理シミュレーション用のタイマー
    private var lastFrameTimeNanos: Long = 0L
    private var accumulator: Float = 0f
    private val fixedDt = GamePerformanceSettings.PHYSICS_DT

    // 入力管理
    private val steerStartAngle = mutableMapOf<Int, Float>()
    private val steerInitialInput = mutableMapOf<Int, Float>()
    private val throttleStartPosY = mutableMapOf<Int, Float>()
    private val activeCameraDirs = mutableSetOf<Int>()

    init {
        // OpenGL ES 2.0を使用するように設定
        setEGLContextClientVersion(2)
        setRenderer(this)
        // 常に描画を更新
        renderMode = RENDERMODE_CONTINUOUSLY
    }

    fun getGameState(): GameState = state

    // --- GLSurfaceView.Renderer の実装 ---

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GesoEngine3D.init()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GesoEngine3D.onSurfaceChanged(width, height)
        // UIの座標系も更新（将来的にUIをオーバーレイする場合に備えて）
        HDU.updateLayoutRects(width.toFloat(), height.toFloat())
    }

    override fun onDrawFrame(gl: GL10?) {
        // 物理計算の更新
        updatePhysics()
        
        // 3Dシーンの描画
        GesoEngine3D.draw(state)
    }

    private fun updatePhysics() {
        val currentTime = System.nanoTime()
        if (lastFrameTimeNanos == 0L) lastFrameTimeNanos = currentTime
        val elapsedSeconds = (currentTime - lastFrameTimeNanos) / 1_000_000_000f
        lastFrameTimeNanos = currentTime
        
        accumulator += elapsedSeconds.coerceAtMost(0.25f)
        while (accumulator >= fixedDt) {
            updateCameraByTouch()
            state.update()
            accumulator -= fixedDt
        }
        
        // 音声の更新
        updateSounds()
    }

    private fun updateSounds() {
        engineSound.update(state.engineRPM, state.throttle, state.isTurboActive)
        exhaustSound.update(state.engineRPM, state.throttle)
        turboSound.update(state.engineRPM, state.throttle, if (state.isTurboActive) state.turboBoost else 0f)
        windSound.update(state.calculatedSpeedKmH)
        brakeSound.update(state.isBraking, state.calculatedSpeedKmH)
        tireSquealSound.update(state.tireSlipRatio, state.calculatedSpeedKmH, state.isBraking)
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

    // --- タッチ入力の処理 ---

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
                    x < width * 0.45f -> { /* 左側ステアリングエリアの予備 */ }
                    else -> throttleStartPosY[pointerId] = y
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                activeCameraDirs.clear()
                steerStartAngle.remove(pointerId)
                throttleStartPosY.remove(pointerId)
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
