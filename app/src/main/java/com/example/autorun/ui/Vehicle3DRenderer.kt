package com.example.autorun.ui

import android.opengl.GLES20
import android.opengl.Matrix
import com.example.autorun.core.GameState
import com.example.autorun.data.vehicle.VehicleDatabase
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer

/**
 * 【Vehicle3DRenderer】
 * glTF形式などの高度な3Dモデルを、プロ仕様のインデックス描画で実行します。
 */
object Vehicle3DRenderer {

    private var vertexBuffer: FloatBuffer? = null
    private var colorBuffer: FloatBuffer? = null
    private var indexBuffer: ShortBuffer? = null
    private var indexCount: Int = 0

    private val modelMatrix = FloatArray(16)
    private val mvpMatrix = FloatArray(16)

    /**
     * プロ仕様のインデックス描画データをセットします。
     */
    fun setModelData(vertices: FloatArray, colors: FloatArray, indices: ShortArray) {
        indexCount = indices.size
        
        vertexBuffer = ByteBuffer.allocateDirect(vertices.size * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
                put(vertices)
                position(0)
            }
            
        colorBuffer = ByteBuffer.allocateDirect(colors.size * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
                put(colors)
                position(0)
            }

        indexBuffer = ByteBuffer.allocateDirect(indices.size * 2)
            .order(ByteOrder.nativeOrder()).asShortBuffer().apply {
                put(indices)
                position(0)
            }
    }

    fun draw(vPMatrix: FloatArray, state: GameState) {
        val vBuf = vertexBuffer ?: return
        val cBuf = colorBuffer ?: return
        val iBuf = indexBuffer ?: return
        val specs = VehicleDatabase.getSelectedVehicle()
        
        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.translateM(modelMatrix, 0, state.playerWorldX, state.playerWorldY + 0.3f, state.playerWorldZ)
        
        val rotationDeg = -Math.toDegrees(state.playerWorldHeading.toDouble()).toFloat()
        Matrix.rotateM(modelMatrix, 0, rotationDeg, 0f, 1f, 0f)
        Matrix.scaleM(modelMatrix, 0, specs.widthM, specs.heightM, specs.lengthM)
        
        Matrix.multiplyMM(mvpMatrix, 0, vPMatrix, 0, modelMatrix, 0)

        GLES20.glVertexAttribPointer(GesoEngine3D.positionHandle, 3, GLES20.GL_FLOAT, false, 0, vBuf)
        GLES20.glEnableVertexAttribArray(GesoEngine3D.positionHandle)
        
        GLES20.glVertexAttribPointer(GesoEngine3D.colorHandle, 4, GLES20.GL_FLOAT, false, 0, cBuf)
        GLES20.glEnableVertexAttribArray(GesoEngine3D.colorHandle)
        
        GLES20.glUniformMatrix4fv(GesoEngine3D.mvpMatrixHandle, 1, false, mvpMatrix, 0)
        
        // インデックス描画（要素描画）を実行。これが最も効率的な描画方法です。
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, indexCount, GLES20.GL_UNSIGNED_SHORT, iBuf)
    }
}
