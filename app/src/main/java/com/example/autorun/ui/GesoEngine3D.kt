package com.example.autorun.ui

import android.opengl.GLES20
import android.opengl.Matrix
import com.example.autorun.core.GameState
import kotlin.math.cos
import kotlin.math.sin

/**
 * 【GesoEngine3D】
 * 3Dグラフィックエンジンの心臓部。
 * カメラ制御と3Dオブジェクトのレンダリングサイクルを管理します。
 */
object GesoEngine3D {
    private var program: Int = 0
    private val vPMatrix = FloatArray(16)
    private val projectionMatrix = FloatArray(16)
    private val viewMatrix = FloatArray(16)

    // シェーダー内のハンドル
    var mvpMatrixHandle: Int = -1
    var positionHandle: Int = -1
    var colorHandle: Int = -1

    fun init() {
        // 背景色を空の色に設定
        GLES20.glClearColor(0.53f, 0.81f, 0.92f, 1.0f)
        // デプスバッファ（奥行き判定）を有効化
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        
        program = GLESShader.createProgram(GLESShader.VERTEX_SHADER_CODE, GLESShader.FRAGMENT_SHADER_CODE)
        GLES20.glUseProgram(program)

        // ハンドルの取得
        mvpMatrixHandle = GLES20.glGetUniformLocation(program, "uMVPMatrix")
        positionHandle = GLES20.glGetAttribLocation(program, "vPosition")
        colorHandle = GLES20.glGetAttribLocation(program, "vColor")
    }

    fun onSurfaceChanged(width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        val ratio: Float = width.toFloat() / height.toFloat()
        // 遠近投影行列の設定 (視野角, アスペクト比, 近面, 遠面)
        Matrix.frustumM(projectionMatrix, 0, -ratio, ratio, -1f, 1f, 1.0f, 2000f)
    }

    fun draw(state: GameState) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        GLES20.glUseProgram(program)

        // カメラ（視点）の計算
        // プレイヤーの背後から追従するカメラ
        val camH = state.playerWorldHeading + state.camYawOffset
        val camP = state.cameraPitch + state.camPitchOffset
        
        // 自車の座標
        val px = state.playerWorldX
        val py = state.playerWorldY
        val pz = state.playerWorldZ
        
        // カメラの位置 (自車の後ろ5m、高さ2m)
        val dist = -5.0f + state.camZOffset
        val camX = px + sin(state.playerWorldHeading.toDouble()).toFloat() * dist + state.camXOffset
        val camY = py + 2.0f + state.camYOffset
        val camZ = pz + cos(state.playerWorldHeading.toDouble()).toFloat() * dist

        // 注視点 (自車の少し先)
        val lookX = px + sin(state.playerWorldHeading.toDouble()).toFloat() * 10f
        val lookY = py + 1.0f
        val lookZ = pz + cos(state.playerWorldHeading.toDouble()).toFloat() * 10f

        // ビュー行列の更新
        Matrix.setLookAtM(viewMatrix, 0, camX, camY, camZ, lookX, lookY, lookZ, 0f, 1f, 0f)
        // プロジェクション行列とビュー行列を掛け合わせる
        Matrix.multiplyMM(vPMatrix, 0, projectionMatrix, 0, viewMatrix, 0)

        // 道路の描画
        Road3DRenderer.draw(vPMatrix, state)
        
        // 車両の描画
        Vehicle3DRenderer.draw(vPMatrix, state)
    }
}
