package com.example.autorun.ui

import android.graphics.Color
import android.opengl.GLES20
import com.example.autorun.config.GameSettings
import com.example.autorun.core.CourseManager
import com.example.autorun.core.GameState
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.cos
import kotlin.math.sin

object Road3DRenderer {

    private const val MAX_VERTICES = 200000 
    private val vertexBuffer: FloatBuffer = ByteBuffer.allocateDirect(MAX_VERTICES * 3 * 4)
        .order(ByteOrder.nativeOrder()).asFloatBuffer()
    private val colorBuffer: FloatBuffer = ByteBuffer.allocateDirect(MAX_VERTICES * 4 * 4)
        .order(ByteOrder.nativeOrder()).asFloatBuffer()

    private var vertexCount = 0

    fun draw(mvpMatrix: FloatArray, state: GameState) {
        vertexCount = 0
        vertexBuffer.clear()
        colorBuffer.clear()

        val playerDist = state.playerDistance
        val segLen = GameSettings.SEGMENT_LENGTH
        val roadW = GameSettings.ROAD_WIDTH
        val halfW = roadW * 0.5f
        val laneW = GameSettings.LANE_MARKER_WIDTH
        val grassW = 50f
        
        val gH = GameSettings.GUARDRAIL_HEIGHT
        val gPH = GameSettings.GUARDRAIL_PLATE_HEIGHT

        // 修正点: 描画開始位置を playerDist - 50f に変更し、車体後方も描画対象に含める
        val startIdx = ((playerDist - 50f) / segLen).toInt().coerceAtLeast(0)
        val endIdx = ((playerDist + 500f) / segLen).toInt().coerceAtMost(CourseManager.getTotalSegments() - 1)

        for (i in startIdx until endIdx) {
            val dist = i * segLen
            val r1 = getRoadPoints(i.toFloat(), halfW)
            val r2 = getRoadPoints((i + 1).toFloat(), halfW)

            // 1. 道路本体
            addQuad(r1.lx, r1.y, r1.lz, r1.rx, r1.y, r1.rz, r2.lx, r2.y, r2.lz, r2.rx, r2.y, r2.rz, GameSettings.COLOR_ROAD_DARK)

            // 2. 白線
            if ((dist % 20f) < 10f) {
                val cl1 = getRoadPoints(i.toFloat(), laneW * 0.5f)
                val cl2 = getRoadPoints((i + 1).toFloat(), laneW * 0.5f)
                addQuad(cl1.lx, cl1.y + 0.01f, cl1.lz, cl1.rx, cl1.y + 0.01f, cl1.rz, 
                        cl2.lx, cl2.y + 0.01f, cl2.lz, cl2.rx, cl2.y + 0.01f, cl2.rz, Color.WHITE)
            }

            // 3. 芝生
            val gL1 = getRoadPoints(i.toFloat(), halfW + grassW)
            val gL2 = getRoadPoints((i + 1).toFloat(), halfW + grassW)
            addQuad(gL1.lx, r1.y - 0.02f, gL1.lz, r1.lx, r1.y - 0.02f, r1.lz, gL2.lx, r2.y - 0.02f, gL2.lz, r2.lx, r2.y - 0.02f, r2.lz, GameSettings.COLOR_GRASS_DARK)
            addQuad(r1.rx, r1.y - 0.02f, r1.rz, gL1.rx, r1.y - 0.02f, gL1.rz, r2.rx, r2.y - 0.02f, r2.rz, gL2.rx, r2.y - 0.02f, r2.rz, GameSettings.COLOR_GRASS_DARK)

            // 4. ガードレール
            addQuad(r1.lx, r1.y + gH, r1.lz, r1.lx, r1.y + gH - gPH, r1.lz,
                    r2.lx, r2.y + gH, r2.lz, r2.lx, r2.y + gH - gPH, r2.lz, GameSettings.COLOR_GUARDRAIL_PLATE)
            addQuad(r1.rx, r1.y + gH, r1.rz, r1.rx, r1.y + gH - gPH, r1.rz,
                    r2.rx, r2.y + gH, r2.rz, r2.rx, r2.y + gH - gPH, r2.rz, GameSettings.COLOR_GUARDRAIL_PLATE)
        }
        render(mvpMatrix)
    }

    private data class RoadPts(val lx: Float, val rx: Float, val lz: Float, val rz: Float, val y: Float)
    private fun getRoadPoints(idx: Float, offset: Float): RoadPts {
        val x = CourseManager.getRoadWorldX(idx); val z = CourseManager.getRoadWorldZ(idx); val y = CourseManager.getHeight(idx); val h = CourseManager.getRoadWorldHeading(idx)
        val px = cos(h.toDouble()).toFloat() * offset; val pz = -sin(h.toDouble()).toFloat() * offset
        return RoadPts(x - px, x + px, z - pz, z + pz, y)
    }
    private fun addQuad(x1: Float, y1: Float, z1: Float, x2: Float, y2: Float, z2: Float, x3: Float, y3: Float, z3: Float, x4: Float, y4: Float, z4: Float, color: Int) {
        addTriangle(x1, y1, z1, x2, y2, z2, x3, y3, z3, color); addTriangle(x2, y2, z2, x4, y4, z4, x3, y3, z3, color)
    }
    private fun addTriangle(x1: Float, y1: Float, z1: Float, x2: Float, y2: Float, z2: Float, x3: Float, y3: Float, z3: Float, color: Int) {
        if (vertexCount + 3 >= MAX_VERTICES) return
        vertexBuffer.put(x1); vertexBuffer.put(y1); vertexBuffer.put(z1); vertexBuffer.put(x2); vertexBuffer.put(y2); vertexBuffer.put(z2); vertexBuffer.put(x3); vertexBuffer.put(y3); vertexBuffer.put(z3)
        val r = Color.red(color) / 255f; val g = Color.green(color) / 255f; val b = Color.blue(color) / 255f; val a = Color.alpha(color) / 255f
        repeat(3) { colorBuffer.put(r); colorBuffer.put(g); colorBuffer.put(b); colorBuffer.put(a) }
        vertexCount += 3
    }
    private fun render(mvpMatrix: FloatArray) {
        if (vertexCount == 0) return
        vertexBuffer.position(0); colorBuffer.position(0)
        GLES20.glVertexAttribPointer(GesoEngine3D.positionHandle, 3, GLES20.GL_FLOAT, false, 0, vertexBuffer)
        GLES20.glEnableVertexAttribArray(GesoEngine3D.positionHandle)
        GLES20.glVertexAttribPointer(GesoEngine3D.colorHandle, 4, GLES20.GL_FLOAT, false, 0, colorBuffer)
        GLES20.glEnableVertexAttribArray(GesoEngine3D.colorHandle)
        GLES20.glUniformMatrix4fv(GesoEngine3D.mvpMatrixHandle, 1, false, mvpMatrix, 0)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, vertexCount)
    }
}
