package com.example.autorun.ui

import android.content.Context
import android.opengl.GLSurfaceView
import android.util.AttributeSet
import android.view.MotionEvent
import com.example.autorun.audio.*
import com.example.autorun.config.GamePerformanceSettings
import com.example.autorun.core.GameState
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * 【GameView: 3D描画層】
 * 物理演算とOpenGL描画を担当します。タッチイベントはオーバーレイ層(HDUOverlayView)で処理されます。
 */
class GameView(context: Context, attrs: AttributeSet? = null) : GLSurfaceView(context, attrs), GLSurfaceView.Renderer {

    private val state = GameState()
    private val engineSound = EngineSoundRenderer()
    private val exhaustSound = ExhaustSoundRenderer()
    private val turboSound = TurboSoundRenderer()
    private val windSound = WindSoundRenderer()
    private val brakeSound = BrakeSoundRenderer()
    private val tireSquealSound = TireSquealRenderer()

    private var lastFrameTimeNanos: Long = 0L
    private var accumulator: Float = 0f
    private val fixedDt = GamePerformanceSettings.PHYSICS_DT

    init {
        setEGLContextClientVersion(2)
        setRenderer(this)
        renderMode = RENDERMODE_CONTINUOUSLY
    }

    fun getGameState(): GameState = state

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GesoEngine3D.init()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GesoEngine3D.onSurfaceChanged(width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        updatePhysics()
        GesoEngine3D.draw(state)
    }

    private fun updatePhysics() {
        val currentTime = System.nanoTime()
        if (lastFrameTimeNanos == 0L) lastFrameTimeNanos = currentTime
        val elapsedSeconds = (currentTime - lastFrameTimeNanos) / 1_000_000_000f
        lastFrameTimeNanos = currentTime
        
        accumulator += elapsedSeconds.coerceAtMost(0.25f)
        while (accumulator >= fixedDt) {
            updateCameraByGameState() // GameStateの状態に基づいてカメラを動かす
            state.update()
            accumulator -= fixedDt
        }
        updateSounds()
    }

    private fun updateCameraByGameState() {
        val speed = 0.05f
        val dirs = state.activeCameraDirs
        if (dirs.contains(0)) state.camPitchOffset -= speed
        if (dirs.contains(1)) state.camPitchOffset += speed
        if (dirs.contains(2)) state.camYawOffset -= speed
        if (dirs.contains(3)) state.camYawOffset += speed
        if (dirs.contains(4)) state.camZOffset += 0.5f
        if (dirs.contains(5)) state.camZOffset -= 0.5f
    }

    private fun updateSounds() {
        engineSound.update(state.engineRPM, state.throttle, state.isTurboActive)
        exhaustSound.update(state.engineRPM, state.throttle)
        turboSound.update(state.engineRPM, state.throttle, if (state.isTurboActive) state.turboBoost else 0f)
        windSound.update(state.calculatedSpeedKmH)
        brakeSound.update(state.isBraking, state.calculatedSpeedKmH)
        tireSquealSound.update(state.tireSlipRatio, state.calculatedSpeedKmH, state.isBraking)
    }

    // タッチイベントは HDUOverlayView が処理するため、ここでは false を返して透過させる
    override fun onTouchEvent(event: MotionEvent): Boolean = false
}
