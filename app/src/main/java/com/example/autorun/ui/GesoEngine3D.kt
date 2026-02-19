package com.example.autorun.ui

import android.content.Context
import android.opengl.GLES20
import android.opengl.Matrix
import com.example.autorun.core.GameState
import kotlin.math.cos
import kotlin.math.sin

/**
 * 【GesoEngine3D】
 * 3Dグラフィックエンジンの心臓部。アルファブレンディングを有効化。
 */
object GesoEngine3D {
    private var program: Int = 0
    private val vPMatrix = FloatArray(16)
    private val projectionMatrix = FloatArray(16)
    private val viewMatrix = FloatArray(16)

    var mvpMatrixHandle: Int = -1
    var positionHandle: Int = -1
    var colorHandle: Int = -1
    var normalHandle: Int = -1

    fun init(context: Context) {
        GLES20.glClearColor(0.53f, 0.81f, 0.92f, 1.0f)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        
        // --- 透過（アルファブレンディング）の有効化 ---
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)

        program = GLESShader.createProgram(GLESShader.VERTEX_SHADER_CODE, GLESShader.FRAGMENT_SHADER_CODE)
        GLES20.glUseProgram(program)

        mvpMatrixHandle = GLES20.glGetUniformLocation(program, "uMVPMatrix")
        positionHandle = GLES20.glGetAttribLocation(program, "vPosition")
        colorHandle = GLES20.glGetAttribLocation(program, "vColor")
        normalHandle = GLES20.glGetAttribLocation(program, "vNormal")

        loadDefaultModel(context)
    }

    private fun loadDefaultModel(context: Context) {
        val model = GltfLoader.loadGlb(context, "car.glb")
        if (model != null) {
            Vehicle3DRenderer.setModelData(model.vertices, model.colors, model.indices, model.normals)
        }
    }

    fun onSurfaceChanged(width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        val ratio: Float = width.toFloat() / height.toFloat()
        Matrix.frustumM(projectionMatrix, 0, -ratio, ratio, -1f, 1f, 1.0f, 2000f)
    }

    fun draw(state: GameState) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        GLES20.glUseProgram(program)

        val px = state.playerWorldX
        val py = state.playerWorldY
        val pz = state.playerWorldZ
        
        val baseDistance = 5.0f + state.camZOffset
        val baseHeight = 2.0f + state.camYOffset
        
        val totalYaw = state.playerWorldHeading + state.camYawOffset
        val totalPitch = state.camPitchOffset

        val offsetX = sin(totalYaw.toDouble()).toFloat() * cos(totalPitch.toDouble()).toFloat() * baseDistance
        val offsetY = sin(totalPitch.toDouble()).toFloat() * baseDistance + baseHeight
        val offsetZ = cos(totalYaw.toDouble()).toFloat() * cos(totalPitch.toDouble()).toFloat() * baseDistance

        val camX = px - offsetX + state.camXOffset
        val camY = py + offsetY
        val camZ = pz - offsetZ

        val lookX = px
        val lookY = py + 1.0f
        val lookZ = pz

        Matrix.setLookAtM(viewMatrix, 0, camX, camY, camZ, lookX, lookY, lookZ, 0f, 1f, 0f)
        Matrix.multiplyMM(vPMatrix, 0, projectionMatrix, 0, viewMatrix, 0)

        Road3DRenderer.draw(vPMatrix, state)
        Vehicle3DRenderer.draw(vPMatrix, state)
    }
}
