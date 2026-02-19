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
 * データベースの車両スペック（全幅・全高・全長）を3Dモデルに正確に反映させます。
 */
object Vehicle3DRenderer {

    private var vertexBuffer: FloatBuffer? = null
    private var colorBuffer: FloatBuffer? = null
    private var indexBuffer: ShortBuffer? = null
    private var indexCount: Int = 0
    private var isGltfModel: Boolean = false

    private val modelMatrix = FloatArray(16)
    private val mvpMatrix = FloatArray(16)

    fun setModelData(vertices: FloatArray, colors: FloatArray, indices: ShortArray, isGltf: Boolean = true) {
        indexCount = indices.size
        isGltfModel = isGltf
        
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
        
        // 1. 位置合わせ（ワールド座標への移動）
        // GltfLoaderで底辺が y=0 になるように正規化されているため、そのまま playerWorldY で接地面に合います
        val yOffset = if (isGltfModel) 0.0f else 0.3f
        Matrix.translateM(modelMatrix, 0, state.playerWorldX, state.playerWorldY + yOffset, state.playerWorldZ)
        
        // 2. 車両の向き（ヘディング回転）
        val rotationDeg = -Math.toDegrees(state.playerWorldHeading.toDouble()).toFloat()
        Matrix.rotateM(modelMatrix, 0, rotationDeg, 0f, 1f, 0f)

        // 3. モデル固有の補正回転
        // glTF (Y-up) とゲームエンジン (Y-up) で軸が一致しているため、-90度回転は不要です。
        // もしモデルが後ろを向いている場合は、ここで 180度回転 (0, 1, 0) を追加してください。
        // Matrix.rotateM(modelMatrix, 0, 180f, 0f, 1f, 0f)

        // 4. 【重要】データベースのスペック（m）に合わせてスケーリング
        // GltfLoaderで 各軸 1.0 に正規化されているため、スペック値をそのまま掛ければ正確なサイズ（メートル）になります
        Matrix.scaleM(modelMatrix, 0, specs.widthM, specs.heightM, specs.lengthM)
        
        Matrix.multiplyMM(mvpMatrix, 0, vPMatrix, 0, modelMatrix, 0)

        GLES20.glVertexAttribPointer(GesoEngine3D.positionHandle, 3, GLES20.GL_FLOAT, false, 0, vBuf)
        GLES20.glEnableVertexAttribArray(GesoEngine3D.positionHandle)
        GLES20.glVertexAttribPointer(GesoEngine3D.colorHandle, 4, GLES20.GL_FLOAT, false, 0, cBuf)
        GLES20.glEnableVertexAttribArray(GesoEngine3D.colorHandle)
        GLES20.glUniformMatrix4fv(GesoEngine3D.mvpMatrixHandle, 1, false, mvpMatrix, 0)
        
        // GL_UNSIGNED_SHORT を使用 (65535頂点まで対応)
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, indexCount, GLES20.GL_UNSIGNED_SHORT, iBuf)
    }
}
