package com.example.autorun.ui

import android.graphics.Color
import android.opengl.GLES20
import android.opengl.Matrix
import com.example.autorun.core.GameState
import com.example.autorun.data.vehicle.VehicleDatabase
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.cos
import kotlin.math.sin

/**
 * 【Vehicle3DRenderer】
 * 3D空間内にプレイヤー車両を描画します。
 */
object Vehicle3DRenderer {

    private val vertexBuffer: FloatBuffer = ByteBuffer.allocateDirect(36 * 3 * 4)
        .order(ByteOrder.nativeOrder()).asFloatBuffer()
    private val colorBuffer: FloatBuffer = ByteBuffer.allocateDirect(36 * 4 * 4)
        .order(ByteOrder.nativeOrder()).asFloatBuffer()

    private val modelMatrix = FloatArray(16)
    private val mvpMatrix = FloatArray(16)

    init {
        // 立方体の頂点データ (仮の車両モデル)
        val v = floatArrayOf(
            -0.5f, -0.5f, -0.5f,  0.5f, -0.5f, -0.5f,  0.5f,  0.5f, -0.5f, -0.5f,  0.5f, -0.5f,
            -0.5f, -0.5f,  0.5f,  0.5f, -0.5f,  0.5f,  0.5f,  0.5f,  0.5f, -0.5f,  0.5f,  0.5f,
            -0.5f, -0.5f, -0.5f, -0.5f,  0.5f, -0.5f, -0.5f,  0.5f,  0.5f, -0.5f, -0.5f,  0.5f,
             0.5f, -0.5f, -0.5f,  0.5f,  0.5f, -0.5f,  0.5f,  0.5f,  0.5f,  0.5f, -0.5f,  0.5f,
            -0.5f, -0.5f, -0.5f,  0.5f, -0.5f, -0.5f,  0.5f, -0.5f,  0.5f, -0.5f, -0.5f,  0.5f,
            -0.5f,  0.5f, -0.5f,  0.5f,  0.5f, -0.5f,  0.5f,  0.5f,  0.5f, -0.5f,  0.5f,  0.5f
        )
        // インデックス描画を簡略化するため、全36頂点を直接入れる
        val indices = intArrayOf(
            0,1,2, 0,2,3, 4,5,6, 4,6,7, 8,9,10, 8,10,11, 
            12,13,14, 12,14,15, 16,17,18, 16,18,19, 20,21,22, 20,22,23
        )
        for (idx in indices) {
            vertexBuffer.put(v[idx * 3]); vertexBuffer.put(v[idx * 3 + 1]); vertexBuffer.put(v[idx * 3 + 2])
        }
        
        val c = Color.RED
        val r = Color.red(c) / 255f; val g = Color.green(c) / 255f; val b = Color.blue(c) / 255f; val a = Color.alpha(c) / 255f
        repeat(36) { colorBuffer.put(r); colorBuffer.put(g); colorBuffer.put(b); colorBuffer.put(a) }
    }

    fun draw(vPMatrix: FloatArray, state: GameState) {
        val specs = VehicleDatabase.getSelectedVehicle()
        
        Matrix.setIdentityM(modelMatrix, 0)
        // 車両のワールド座標への移動
        Matrix.translateM(modelMatrix, 0, state.playerWorldX, state.playerWorldY + 0.3f, state.playerWorldZ)
        // 車両の回転 (Heading)
        Matrix.rotateM(modelMatrix, 0, Math.toDegrees(state.playerWorldHeading.toDouble()).toFloat(), 0f, 1f, 0f)
        // 車両のサイズのスケーリング
        Matrix.scaleM(modelMatrix, 0, specs.widthM, specs.heightM, specs.lengthM)

        Matrix.multiplyMM(mvpMatrix, 0, vPMatrix, 0, modelMatrix, 0)

        vertexBuffer.position(0); colorBuffer.position(0)
        GLES20.glVertexAttribPointer(GesoEngine3D.positionHandle, 3, GLES20.GL_FLOAT, false, 0, vertexBuffer)
        GLES20.glEnableVertexAttribArray(GesoEngine3D.positionHandle)
        GLES20.glVertexAttribPointer(GesoEngine3D.colorHandle, 4, GLES20.GL_FLOAT, false, 0, colorBuffer)
        GLES20.glEnableVertexAttribArray(GesoEngine3D.colorHandle)
        GLES20.glUniformMatrix4fv(GesoEngine3D.mvpMatrixHandle, 1, false, mvpMatrix, 0)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 36)
    }
}
