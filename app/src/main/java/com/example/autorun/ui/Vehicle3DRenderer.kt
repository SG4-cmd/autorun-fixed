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
 * モデルを回転させた後の「底面」を計算し、地面にピッタリ接地させます。
 * 加減速によるピッチングや路面の凹凸挙動も反映します。
 */
object Vehicle3DRenderer {

    private var vertexBuffer: FloatBuffer? = null
    private var colorBuffer: FloatBuffer? = null
    private var normalBuffer: FloatBuffer? = null
    private var indexBuffer: ShortBuffer? = null
    private var indexCount: Int = 0
    private var isGltfModel: Boolean = false
    
    // 正規化後の高さ情報（接地計算用）
    private var modelMinZ: Float = -0.5f
    private var modelMaxZ: Float = 0.5f

    private val modelMatrix = FloatArray(16)
    private val mvpMatrix = FloatArray(16)

    fun setModelData(data: GltfLoader.ModelData, isGltf: Boolean = true) {
        indexCount = data.indices.size
        isGltfModel = isGltf
        modelMinZ = data.minZ
        modelMaxZ = data.maxZ
        
        vertexBuffer = ByteBuffer.allocateDirect(data.vertices.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply { put(data.vertices); position(0) }
        colorBuffer = ByteBuffer.allocateDirect(data.colors.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply { put(data.colors); position(0) }
        normalBuffer = ByteBuffer.allocateDirect(data.normals.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply { put(data.normals); position(0) }
        indexBuffer = ByteBuffer.allocateDirect(data.indices.size * 2).order(ByteOrder.nativeOrder()).asShortBuffer().apply { put(data.indices); position(0) }
    }

    fun draw(vPMatrix: FloatArray, state: GameState) {
        val vBuf = vertexBuffer ?: return
        val cBuf = colorBuffer ?: return
        val nBuf = normalBuffer ?: return
        val iBuf = indexBuffer ?: return
        val specs = VehicleDatabase.getSelectedVehicle()
        
        Matrix.setIdentityM(modelMatrix, 0)
        
        // 1. 位置合わせ（ワールド座標）
        // ベースの高さ(playerWorldY)に移動
        Matrix.translateM(modelMatrix, 0, state.playerWorldX, state.playerWorldY, state.playerWorldZ)
        
        // 2. 進行方向（ヘディング）
        val rotationDeg = -Math.toDegrees(state.playerWorldHeading.toDouble()).toFloat()
        Matrix.rotateM(modelMatrix, 0, rotationDeg, 0f, 1f, 0f)

        // 3. 【接地補正 + 挙動反映】
        // モデルは中央(0,0,0)にあり、-90度X回転させると、元のZ軸が「高さ(Y)」になります。
        val scale = specs.lengthM
        val yOffset = -modelMinZ * scale 
        
        // 路面の凹凸やピッチングによる垂直方向の動きを追加
        val bounceY = state.carVerticalShake * 0.05f 
        Matrix.translateM(modelMatrix, 0, 0f, yOffset + bounceY, 0f)

        // 4. 起き上がり補正 & ピッチング回転
        if (isGltfModel) {
            Matrix.rotateM(modelMatrix, 0, -90f, 1f, 0f, 0f)
            // 加減速によるピッチング（前後の傾き）を反映
            Matrix.rotateM(modelMatrix, 0, Math.toDegrees(state.visualPitch.toDouble()).toFloat(), 0f, 1f, 0f)
        }

        // 5. スケーリング
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
