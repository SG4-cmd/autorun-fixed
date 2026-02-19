package com.example.autorun.ui

import android.content.Context
import android.graphics.Color
import android.opengl.GLES20
import android.opengl.Matrix
import com.example.autorun.config.GameSettings
import com.example.autorun.core.CourseManager
import com.example.autorun.core.GameState
import com.example.autorun.data.vehicle.VehicleDatabase
import com.example.autorun.data.vehicle.VehicleSpecs
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer
import kotlin.math.cos
import kotlin.math.sin

/**
 * 【GesoEngine3D】
 * 3Dグラフィックエンジンの中枢。道路、車両、オブジェクトの描画を統合管理。
 */
object GesoEngine3D {
    // --- 共通シェーダーハンドル ---
    var mvpMatrixHandle: Int = -1
    var positionHandle: Int = -1
    var colorHandle: Int = -1
    var normalHandle: Int = -1
    private var program: Int = 0

    // --- 描画マトリクス ---
    private val vPMatrix = FloatArray(16)
    private val projectionMatrix = FloatArray(16)
    private val viewMatrix = FloatArray(16)
    private val modelMatrix = FloatArray(16)
    private val mvpMatrix = FloatArray(16)

    // --- 車両モデルデータ保持 ---
    private var vehicleModel: GltfLoader.ModelData? = null
    private var currentModelPath: String? = null

    // --- 道路描画用バッファ (Road3DRendererから統合) ---
    private const val MAX_ROAD_VERTICES = 400000 
    private val roadVertexBuffer: FloatBuffer = ByteBuffer.allocateDirect(MAX_ROAD_VERTICES * 3 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
    private val roadColorBuffer: FloatBuffer = ByteBuffer.allocateDirect(MAX_ROAD_VERTICES * 4 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
    private var roadVertexCount = 0

    fun init(context: Context) {
        GLES20.glClearColor(0.53f, 0.81f, 0.92f, 1.0f)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)

        program = GLESShader.createProgram(GLESShader.VERTEX_SHADER_CODE, GLESShader.FRAGMENT_SHADER_CODE)
        GLES20.glUseProgram(program)

        mvpMatrixHandle = GLES20.glGetUniformLocation(program, "uMVPMatrix")
        positionHandle = GLES20.glGetAttribLocation(program, "vPosition")
        colorHandle = GLES20.glGetAttribLocation(program, "vColor")
        normalHandle = GLES20.glGetAttribLocation(program, "vNormal")

        loadSelectedVehicleModel(context)
    }

    fun loadSelectedVehicleModel(context: Context) {
        val specs = VehicleDatabase.getSelectedVehicle()
        if (specs.modelPath == currentModelPath) return
        vehicleModel = GltfLoader.loadGlb(context, specs.modelPath)
        currentModelPath = specs.modelPath
    }

    fun onSurfaceChanged(width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        val ratio: Float = width.toFloat() / height.toFloat()
        Matrix.frustumM(projectionMatrix, 0, -ratio, ratio, -1f, 1f, 1.0f, 2000f)
    }

    fun draw(state: GameState, context: Context) {
        loadSelectedVehicleModel(context)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        GLES20.glUseProgram(program)

        updateCamera(state)

        // 1. 道路の描画
        drawRoad(state)
        // 2. 自車の描画
        drawPlayerVehicle(state)
    }

    private fun updateCamera(state: GameState) {
        val px = state.playerWorldX; val py = state.playerWorldY; val pz = state.playerWorldZ
        val baseDistance = 5.0f + state.camZOffset
        val baseHeight = 2.0f + state.camYOffset
        val totalYaw = state.playerWorldHeading + state.camYawOffset
        val totalPitch = state.camPitchOffset

        val ox = sin(totalYaw.toDouble()).toFloat() * cos(totalPitch.toDouble()).toFloat() * baseDistance
        val oy = sin(totalPitch.toDouble()).toFloat() * baseDistance + baseHeight
        val oz = cos(totalYaw.toDouble()).toFloat() * cos(totalPitch.toDouble()).toFloat() * baseDistance

        Matrix.setLookAtM(viewMatrix, 0, px - ox, py + oy, pz - oz, px, py + 1.0f, pz, 0f, 1f, 0f)
        Matrix.multiplyMM(vPMatrix, 0, projectionMatrix, 0, viewMatrix, 0)
    }

    private fun drawRoad(state: GameState) {
        roadVertexCount = 0
        roadVertexBuffer.clear(); roadColorBuffer.clear()

        val playerDist = state.playerDistance
        val segLen = GameSettings.SEGMENT_LENGTH
        val startIdx = ((playerDist - 50f) / segLen).toInt().coerceAtLeast(0)
        val endIdx = ((playerDist + 800f) / segLen).toInt().coerceAtMost(CourseManager.getTotalSegments() - 1)

        for (i in startIdx until endIdx) {
            val lanes = CourseManager.getLanes(i.toFloat()); val roadW = lanes * GameSettings.SINGLE_LANE_WIDTH
            val r1 = getRoadPoints(i.toFloat(), roadW * 0.5f); val r2 = getRoadPoints((i + 1).toFloat(), roadW * 0.5f)
            addRoadQuad(r1.lx, r1.y, r1.lz, r1.rx, r1.y, r1.rz, r2.lx, r2.y, r2.lz, r2.rx, r2.y, r2.rz, GameSettings.COLOR_ROAD_DARK)
        }

        if (roadVertexCount > 0) {
            roadVertexBuffer.position(0); roadColorBuffer.position(0)
            GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 0, roadVertexBuffer)
            GLES20.glEnableVertexAttribArray(positionHandle)
            GLES20.glVertexAttribPointer(colorHandle, 4, GLES20.GL_FLOAT, false, 0, roadColorBuffer)
            GLES20.glEnableVertexAttribArray(colorHandle)
            GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, vPMatrix, 0)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, roadVertexCount)
        }
    }

    private fun drawPlayerVehicle(state: GameState) {
        val model = vehicleModel ?: return
        val specs = VehicleDatabase.getSelectedVehicle()
        
        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.translateM(modelMatrix, 0, state.playerWorldX, state.playerWorldY, state.playerWorldZ)
        Matrix.rotateM(modelMatrix, 0, -Math.toDegrees(state.playerWorldHeading.toDouble()).toFloat(), 0f, 1f, 0f)

        val scale = specs.lengthM
        val yOffset = -model.minZ * scale + (state.carVerticalShake * 0.05f)
        Matrix.translateM(modelMatrix, 0, 0f, yOffset, 0f)
        Matrix.rotateM(modelMatrix, 0, -90f, 1f, 0f, 0f)
        Matrix.rotateM(modelMatrix, 0, Math.toDegrees(state.visualPitch.toDouble()).toFloat(), 0f, 1f, 0f)
        Matrix.scaleM(modelMatrix, 0, scale, scale, scale)

        drawPart(vPMatrix, modelMatrix, model.vertices, model.colors, model.indices, model.indices.size)
    }

    private fun drawPart(vP: FloatArray, m: FloatArray, v: FloatArray, c: FloatArray, i: ShortArray, iCount: Int) {
        Matrix.multiplyMM(mvpMatrix, 0, vP, 0, m, 0)
        val vBuf = ByteBuffer.allocateDirect(v.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply { put(v); position(0) }
        val cBuf = ByteBuffer.allocateDirect(c.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply { put(c); position(0) }
        val iBuf = ByteBuffer.allocateDirect(i.size * 2).order(ByteOrder.nativeOrder()).asShortBuffer().apply { put(i); position(0) }

        GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 0, vBuf)
        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glVertexAttribPointer(colorHandle, 4, GLES20.GL_FLOAT, false, 0, cBuf)
        GLES20.glEnableVertexAttribArray(colorHandle)
        GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, mvpMatrix, 0)
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, iCount, GLES20.GL_UNSIGNED_SHORT, iBuf)
    }

    private data class RoadPts(val lx: Float, val rx: Float, val lz: Float, val rz: Float, val y: Float)
    private fun getRoadPoints(idx: Float, offset: Float): RoadPts {
        val x = CourseManager.getRoadWorldX(idx); val z = CourseManager.getRoadWorldZ(idx); val y = CourseManager.getHeight(idx); val h = CourseManager.getRoadWorldHeading(idx)
        val px = cos(h.toDouble()).toFloat() * offset; val pz = -sin(h.toDouble()).toFloat() * offset
        return RoadPts(x - px, x + px, z - pz, z + pz, y)
    }

    private fun addRoadQuad(x1: Float, y1: Float, z1: Float, x2: Float, y2: Float, z2: Float, x3: Float, y3: Float, z3: Float, x4: Float, y4: Float, z4: Float, color: Int) {
        if (roadVertexCount + 6 >= MAX_ROAD_VERTICES) return
        val r = Color.red(color)/255f; val g = Color.green(color)/255f; val b = Color.blue(color)/255f; val a = Color.alpha(color)/255f
        fun v(x: Float, y: Float, z: Float) { roadVertexBuffer.put(x); roadVertexBuffer.put(y); roadVertexBuffer.put(z); roadColorBuffer.put(r); roadColorBuffer.put(g); roadColorBuffer.put(b); roadColorBuffer.put(a); roadVertexCount++ }
        v(x1, y1, z1); v(x2, y2, z2); v(x3, y3, z3); v(x2, y2, z2); v(x4, y4, z4); v(x3, y3, z3)
    }

    fun handleTouch(x: Float, y: Float, state: GameState, isTapEvent: Boolean): Boolean = HDU.handleTouch(x, y, state, isTapEvent)
}
