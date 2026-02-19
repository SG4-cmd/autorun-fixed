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
 * モデルの比率を維持したまま、全長(lengthM)を基準にスケーリング。
 */
object Vehicle3DRenderer {

    private var vertexBuffer: FloatBuffer? = null
    private var colorBuffer: FloatBuffer? = null
    private var normalBuffer: FloatBuffer? = null
    private var indexBuffer: ShortBuffer? = null
    private var indexCount: Int = 0
    private var isGltfModel: Boolean = false

    private val modelMatrix = FloatArray(16)
    private val mvpMatrix = FloatArray(16)

    fun setModelData(vertices: FloatArray, colors: FloatArray, indices: ShortArray, normals: FloatArray, isGltf: Boolean = true) {
        indexCount = indices.size
        isGltfModel = isGltf
        
        vertexBuffer = ByteBuffer.allocateDirect(vertices.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply { put(vertices); position(0) }
        colorBuffer = ByteBuffer.allocateDirect(colors.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply { put(colors); position(0) }
        normalBuffer = ByteBuffer.allocateDirect(normals.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply { put(normals); position(0) }
        indexBuffer = ByteBuffer.allocateDirect(indices.size * 2).order(ByteOrder.nativeOrder()).asShortBuffer().apply { put(indices); position(0) }
    }

    fun draw(vPMatrix: FloatArray, state: GameState) {
        val vBuf = vertexBuffer ?: return
        val cBuf = colorBuffer ?: return
        val nBuf = normalBuffer ?: return
        val iBuf = indexBuffer ?: return
        val specs = VehicleDatabase.getSelectedVehicle()
        
        Matrix.setIdentityM(modelMatrix, 0)
        
        // 1. 移動
        val yOffset = if (isGltfModel) 0.0f else 0.3f
        Matrix.translateM(modelMatrix, 0, state.playerWorldX, state.playerWorldY + yOffset, state.playerWorldZ)
        
        // 2. 進行方向（ヘディング）
        val rotationDeg = -Math.toDegrees(state.playerWorldHeading.toDouble()).toFloat()
        Matrix.rotateM(modelMatrix, 0, rotationDeg, 0f, 1f, 0f)

        // 3. 起き上がり補正（X軸回転）
        if (isGltfModel) {
            Matrix.rotateM(modelMatrix, 0, -90f, 1f, 0f, 0f)
        }

        // 4. 【重要】比率を維持した全長スケーリング
        // GltfLoader側で「最大辺=1.0」としてアスペクト比を維持したまま正規化されています。
        // そのため、全長(lengthM)を全軸に均等に掛けることで、歪まずに正しいサイズになります。
        val scale = specs.lengthM
        Matrix.scaleM(modelMatrix, 0, scale, scale, scale)
        
        Matrix.multiplyMM(mvpMatrix, 0, vPMatrix, 0, modelMatrix, 0)

        GLES20.glVertexAttribPointer(GesoEngine3D.positionHandle, 3, GLES20.GL_FLOAT, false, 0, vBuf)
        GLES20.glEnableVertexAttribArray(GesoEngine3D.positionHandle)
        GLES20.glVertexAttribPointer(GesoEngine3D.colorHandle, 4, GLES20.GL_FLOAT, false, 0, cBuf)
        GLES20.glEnableVertexAttribArray(GesoEngine3D.colorHandle)
        
        if (GesoEngine3D.normalHandle != -1) {
            GLES20.glEnableVertexAttribArray(GesoEngine3D.normalHandle)
            GLES20.glVertexAttribPointer(GesoEngine3D.normalHandle, 3, GLES20.GL_FLOAT, false, 0, nBuf)
        }
        
        GLES20.glUniformMatrix4fv(GesoEngine3D.mvpMatrixHandle, 1, false, mvpMatrix, 0)
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, indexCount, GLES20.GL_UNSIGNED_SHORT, iBuf)
    }
}
