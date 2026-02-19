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

/**
 * 【Road3DRenderer: 東北道完全再現版】
 * 車線数、白線パターン、路肩、ガードレール支柱を3Dで反映。
 */
object Road3DRenderer {

    private const val MAX_VERTICES = 800000 
    private val vertexBuffer: FloatBuffer = ByteBuffer.allocateDirect(MAX_VERTICES * 3 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
    private val colorBuffer: FloatBuffer = ByteBuffer.allocateDirect(MAX_VERTICES * 4 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
    private val normalBuffer: FloatBuffer = ByteBuffer.allocateDirect(MAX_VERTICES * 3 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()

    private var vertexCount = 0

    fun draw(mvpMatrix: FloatArray, state: GameState) {
        vertexCount = 0
        vertexBuffer.clear(); colorBuffer.clear(); normalBuffer.clear()

        val playerDist = state.playerDistance
        val segLen = GameSettings.SEGMENT_LENGTH
        val startIdx = ((playerDist - 50f) / segLen).toInt().coerceAtLeast(0)
        val endIdx = ((playerDist + 800f) / segLen).toInt().coerceAtMost(CourseManager.getTotalSegments() - 1)

        val roadObjects = CourseManager.getRoadObjects()

        for (i in startIdx until endIdx) {
            val dist = i * segLen
            val lanes = CourseManager.getLanes(i.toFloat())
            val laneW = GameSettings.SINGLE_LANE_WIDTH
            val roadW = lanes * laneW
            val shoulderL = GameSettings.SHOULDER_WIDTH_LEFT
            val shoulderR = GameSettings.SHOULDER_WIDTH_RIGHT
            
            val r1 = getRoadPoints(i.toFloat(), roadW * 0.5f)
            val r2 = getRoadPoints((i + 1).toFloat(), roadW * 0.5f)
            val h = CourseManager.getRoadWorldHeading(i.toFloat())

            val tunnel = roadObjects.find { it.type == "TUNNEL" && dist >= it.posKm * 1000f && dist <= (it.posKm * 1000f + it.lengthM) }
            val isTunnel = tunnel != null

            // 1. 走行路面 (各車線を個別に描画して白線を挟む)
            val roadColor = if (isTunnel) Color.DKGRAY else GameSettings.COLOR_ROAD_DARK
            addQuad(r1.lx, r1.y, r1.lz, r1.rx, r1.y, r1.rz, r2.lx, r2.y, r2.lz, r2.rx, r2.y, r2.rz, roadColor)

            // 2. 白線 (高速道路規格: 8m実線 / 12m空白)
            if (dist % 20f < 8f) {
                for (l in 1 until lanes) {
                    val offset = -roadW * 0.5f + l * laneW
                    val w1 = getRoadPoints(i.toFloat(), offset)
                    val w2 = getRoadPoints((i + 1).toFloat(), offset)
                    val markW = 0.15f
                    addQuad(w1.lx - markW, w1.y + 0.02f, w1.lz, w1.lx + markW, w1.y + 0.02f, w1.lz,
                            w2.lx - markW, w2.y + 0.02f, w2.lz, w2.lx + markW, w2.y + 0.02f, w2.lz, Color.WHITE)
                }
            }

            // 3. 路肩と中央分離帯
            val sL1 = getRoadPoints(i.toFloat(), roadW * 0.5f + shoulderL)
            val sL2 = getRoadPoints((i + 1).toFloat(), roadW * 0.5f + shoulderL)
            addQuad(sL1.lx, r1.y, sL1.lz, r1.lx, r1.y, r1.lz, sL2.lx, r2.y, sL2.lz, r2.lx, r2.y, r2.lz, roadColor) // 左路肩

            // 4. ガードレールと支柱 (2m間隔)
            if (!isTunnel) {
                drawGuardrailWithPosts(r1.lx - 0.2f, r2.lx - 0.2f, r1.y, r2.y, r1.lz, r2.lz, dist)
            } else {
                drawTunnelSegment(r1, r2)
            }

            // 5. 施設看板
            checkAndAddSigns(dist, r1, h, roadObjects)
        }
        render(mvpMatrix)
    }

    private fun drawGuardrailWithPosts(x1: Float, x2: Float, y1: Float, y2: Float, z1: Float, z2: Float, dist: Float) {
        val gH = 0.8f
        val pW = 0.1f
        // レール板
        addQuad(x1, y1 + gH, z1, x1, y1 + gH - 0.3f, z1, x2, y2 + gH, z2, x2, y2 + gH - 0.3f, z2, Color.WHITE)
        // 支柱 (2mごと)
        if (dist % 2.0f < 0.1f) {
            addQuad(x1 - pW, y1, z1, x1 + pW, y1, z1, x1 - pW, y1 + gH, z1, x1 + pW, y1 + gH, z1, Color.LTGRAY)
        }
    }

    private fun drawTunnelSegment(r1: RoadPts, r2: RoadPts) {
        val wallH = 5.0f
        addQuad(r1.lx, r1.y, r1.lz, r1.lx, r1.y + wallH, r1.lz, r2.lx, r2.y, r2.lz, r2.lx, r2.y + wallH, r2.lz, Color.GRAY)
        addQuad(r1.rx, r1.y, r1.rz, r1.rx, r1.y + wallH, r1.rz, r2.rx, r2.y, r2.lz, r2.rx, r2.y + wallH, r2.rz, Color.GRAY)
        addQuad(r1.lx, r1.y + wallH, r1.lz, r1.rx, r1.y + wallH, r1.rz, r2.lx, r2.y + wallH, r2.lz, r2.rx, r2.y + wallH, r2.rz, Color.LTGRAY)
    }

    private fun checkAndAddSigns(dist: Float, p: RoadPts, h: Float, objects: List<CourseManager.RoadObject>) {
        for (obj in objects) {
            val targetDist = obj.posKm * 1000f
            if (dist in (targetDist - 1001f)..(targetDist - 999f) || dist in (targetDist - 501f)..(targetDist - 499f)) {
                val signW = 6f; val signH = 3.5f; val poleH = 6f
                val ux = -cos(h.toDouble()).toFloat(); val uz = sin(h.toDouble()).toFloat()
                val bx = p.lx + ux * 8f; val bz = p.lz + uz * 8f
                addQuad(bx, p.y + poleH, bz, bx + signW, p.y + poleH, bz, bx, p.y + poleH + signH, bz, bx + signW, p.y + poleH + signH, bz, Color.parseColor("#007A33"))
            }
        }
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
        vertexBuffer.put(x1); vertexBuffer.put(y1); vertexBuffer.put(z1)
        vertexBuffer.put(x2); vertexBuffer.put(y2); vertexBuffer.put(z2)
        vertexBuffer.put(x3); vertexBuffer.put(y3); vertexBuffer.put(z3)
        val r = Color.red(color) / 255f; val g = Color.green(color) / 255f; val b = Color.blue(color) / 255f; val a = Color.alpha(color) / 255f
        repeat(3) { colorBuffer.put(r); colorBuffer.put(g); colorBuffer.put(b); colorBuffer.put(a) }
        repeat(3) { normalBuffer.put(0f); normalBuffer.put(1f); normalBuffer.put(0f) }
        vertexCount += 3
    }

    private fun render(mvpMatrix: FloatArray) {
        if (vertexCount == 0) return
        vertexBuffer.position(0); colorBuffer.position(0); normalBuffer.position(0)
        GLES20.glVertexAttribPointer(GesoEngine3D.positionHandle, 3, GLES20.GL_FLOAT, false, 0, vertexBuffer)
        GLES20.glEnableVertexAttribArray(GesoEngine3D.positionHandle)
        GLES20.glVertexAttribPointer(GesoEngine3D.colorHandle, 4, GLES20.GL_FLOAT, false, 0, colorBuffer)
        GLES20.glEnableVertexAttribArray(GesoEngine3D.colorHandle)
        GLES20.glVertexAttribPointer(GesoEngine3D.normalHandle, 3, GLES20.GL_FLOAT, false, 0, normalBuffer)
        GLES20.glEnableVertexAttribArray(GesoEngine3D.normalHandle)
        GLES20.glUniformMatrix4fv(GesoEngine3D.mvpMatrixHandle, 1, false, mvpMatrix, 0)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, vertexCount)
    }
}
