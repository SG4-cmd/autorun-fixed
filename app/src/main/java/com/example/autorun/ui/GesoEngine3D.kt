package com.example.autorun.ui

import android.content.Context
import android.opengl.GLES20
import android.opengl.Matrix
import com.example.autorun.core.GameState
import kotlin.math.cos
import kotlin.math.sin

/**
 * 【GesoEngine3D】
 * 3Dグラフィックエンジンの心臓部。glTF形式のモデル読み込みに対応。
 */
object GesoEngine3D {
    private var program: Int = 0
    private val vPMatrix = FloatArray(16)
    private val projectionMatrix = FloatArray(16)
    private val viewMatrix = FloatArray(16)

    var mvpMatrixHandle: Int = -1
    var positionHandle: Int = -1
    var colorHandle: Int = -1

    fun init(context: Context) {
        GLES20.glClearColor(0.53f, 0.81f, 0.92f, 1.0f)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        program = GLESShader.createProgram(GLESShader.VERTEX_SHADER_CODE, GLESShader.FRAGMENT_SHADER_CODE)
        GLES20.glUseProgram(program)

        mvpMatrixHandle = GLES20.glGetUniformLocation(program, "uMVPMatrix")
        positionHandle = GLES20.glGetAttribLocation(program, "vPosition")
        colorHandle = GLES20.glGetAttribLocation(program, "vColor")

        // 業界標準のglTF (glb) モデルを読み込む
        loadDefaultModel(context)
    }

    private fun loadDefaultModel(context: Context) {
        // assets/car.glb を読み込む
        val model = GltfLoader.loadGlb(context, "car.glb")
        
        if (model != null) {
            Vehicle3DRenderer.setModelData(model.vertices, model.colors, model.indices)
        } else {
            // ファイルがない場合のフォールバック（以前の立方体データをインデックス形式で生成）
            val dummyVertices = floatArrayOf(
                -0.5f, -0.5f,  0.5f,   0.5f, -0.5f,  0.5f,   0.5f,  0.5f,  0.5f,  -0.5f,  0.5f,  0.5f, // Front
                -0.5f, -0.5f, -0.5f,   0.5f, -0.5f, -0.5f,   0.5f,  0.5f, -0.5f,  -0.5f,  0.5f, -0.5f  // Back
            )
            val dummyIndices = shortArrayOf(
                0, 1, 2, 0, 2, 3, // Front
                4, 5, 6, 4, 6, 7, // Back
                3, 2, 6, 3, 6, 7, // Top
                0, 1, 5, 0, 5, 4, // Bottom
                0, 3, 7, 0, 7, 4, // Left
                1, 2, 6, 1, 6, 5  // Right
            )
            // 立方体の全頂点（24頂点）が必要だが、ここでは簡略化して8頂点+インデックスで構成
            val fullVertices = floatArrayOf(
                -0.5f, -0.5f,  0.5f,  0.5f, -0.5f,  0.5f,  0.5f,  0.5f,  0.5f, -0.5f,  0.5f,  0.5f,
                -0.5f, -0.5f, -0.5f,  0.5f, -0.5f, -0.5f,  0.5f,  0.5f, -0.5f, -0.5f,  0.5f, -0.5f
            )
            val colors = FloatArray((fullVertices.size / 3) * 4) { i -> if (i % 4 == 3) 1.0f else 1.0f }
            Vehicle3DRenderer.setModelData(fullVertices, colors, dummyIndices)
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
