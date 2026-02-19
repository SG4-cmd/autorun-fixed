package com.example.autorun.ui

import android.content.Context
import android.opengl.Matrix
import android.util.Log
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 【GltfLoader: プロフェッショナル版】
 * 各軸を独立して正規化し、VehicleDatabaseのスペック値と完全に連動するように改善。
 */
object GltfLoader {

    class ModelData(val vertices: FloatArray, val indices: ShortArray, val colors: FloatArray, val normals: FloatArray)

    fun loadGlb(context: Context, fileName: String): ModelData? {
        return try {
            val inputStream = context.assets.open(fileName)
            val bytes = inputStream.readBytes()
            inputStream.close()

            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            if (buffer.int != 0x46546C67) throw Exception("Invalid GLB magic")
            buffer.int; buffer.int

            var json: JSONObject? = null
            var binBuffer: ByteBuffer? = null

            while (buffer.remaining() >= 8) {
                val chunkLength = buffer.int
                val chunkType = buffer.int
                if (chunkType == 0x4E4F534A) {
                    val jsonBytes = ByteArray(chunkLength)
                    buffer.get(jsonBytes)
                    json = JSONObject(String(jsonBytes).trim())
                } else if (chunkType == 0x004E4942) {
                    binBuffer = buffer.slice().order(ByteOrder.LITTLE_ENDIAN)
                    buffer.position(buffer.position() + chunkLength)
                } else {
                    buffer.position(buffer.position() + chunkLength)
                }
                val padding = (4 - (chunkLength % 4)) % 4
                if (padding > 0 && buffer.remaining() >= padding) buffer.position(buffer.position() + padding)
            }

            if (json == null || binBuffer == null) return null

            val allVertices = mutableListOf<Float>()
            val allIndices = mutableListOf<Short>()
            val allColors = mutableListOf<Float>()
            val allNormals = mutableListOf<Float>()

            val nodes = json.optJSONArray("nodes")
            val meshes = json.optJSONArray("meshes")
            val identity = FloatArray(16).apply { Matrix.setIdentityM(this, 0) }

            if (nodes != null && meshes != null) {
                for (i in 0 until nodes.length()) {
                    val node = nodes.getJSONObject(i)
                    if (node.has("mesh")) {
                        val matrix = getNodeMatrix(node)
                        processMesh(json, binBuffer, meshes.getJSONObject(node.getInt("mesh")), matrix, allVertices, allIndices, allColors, allNormals)
                    }
                }
            } else if (meshes != null) {
                for (i in 0 until meshes.length()) processMesh(json, binBuffer, meshes.getJSONObject(i), identity, allVertices, allIndices, allColors, allNormals)
            }

            val vArray = allVertices.toFloatArray()
            if (vArray.isEmpty()) return null

            // --- 軸別正規化 (スペック連動用) ---
            var minX = Float.MAX_VALUE; var maxX = -Float.MAX_VALUE
            var minY = Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
            var minZ = Float.MAX_VALUE; var maxZ = -Float.MAX_VALUE

            for (i in vArray.indices step 3) {
                minX = minOf(minX, vArray[i]); maxX = maxOf(maxX, vArray[i])
                minY = minOf(minY, vArray[i+1]); maxY = maxOf(maxY, vArray[i+1])
                minZ = minOf(minZ, vArray[i+2]); maxZ = maxOf(maxZ, vArray[i+2])
            }

            val sX = (maxX - minX).coerceAtLeast(0.001f)
            val sY = (maxY - minY).coerceAtLeast(0.001f)
            val sZ = (maxZ - minZ).coerceAtLeast(0.001f)

            for (i in vArray.indices step 3) {
                vArray[i] = (vArray[i] - (maxX + minX) / 2f) / sX
                vArray[i+1] = (vArray[i+1] - minY) / sY
                vArray[i+2] = (vArray[i+2] - (maxZ + minZ) / 2f) / sZ
            }

            Log.i("GltfLoader", "Done: $fileName, Verts: ${vArray.size/3}")
            return ModelData(vArray, allIndices.toShortArray(), allColors.toFloatArray(), allNormals.toFloatArray())
        } catch (e: Exception) {
            Log.e("GltfLoader", "Error: ${e.message}"); null
        }
    }

    private fun getNodeMatrix(node: JSONObject): FloatArray {
        val matrix = FloatArray(16)
        Matrix.setIdentityM(matrix, 0)
        if (node.has("matrix")) {
            val arr = node.getJSONArray("matrix")
            for (i in 0 until 16) matrix[i] = arr.getDouble(i).toFloat()
        } else {
            if (node.has("translation")) {
                val t = node.getJSONArray("translation")
                Matrix.translateM(matrix, 0, t.getDouble(0).toFloat(), t.getDouble(1).toFloat(), t.getDouble(2).toFloat())
            }
            if (node.has("rotation")) {
                val r = node.getJSONArray("rotation")
                val qx = r.getDouble(0).toFloat(); val qy = r.getDouble(1).toFloat(); val qz = r.getDouble(2).toFloat(); val qw = r.getDouble(3).toFloat()
                val rotM = FloatArray(16)
                val xx=qx*qx; val xy=qx*qy; val xz=qx*qz; val xw=qx*qw; val yy=qy*qy; val yz=qy*qz; val yw=qy*qw; val zz=qz*qz; val zw=qz*qw
                rotM[0]=1-2*(yy+zz); rotM[1]=2*(xy+zw); rotM[2]=2*(xz-yw); rotM[3]=0f
                rotM[4]=2*(xy-zw); rotM[5]=1-2*(xx+zz); rotM[6]=2*(yz+xw); rotM[7]=0f
                rotM[8]=2*(xz+yw); rotM[9]=2*(yz-xw); rotM[10]=1-2*(xx+yy); rotM[11]=0f
                rotM[12]=0f; rotM[13]=0f; rotM[14]=0f; rotM[15]=1f
                val temp = matrix.clone(); Matrix.multiplyMM(matrix, 0, temp, 0, rotM, 0)
            }
            if (node.has("scale")) {
                val s = node.getJSONArray("scale")
                Matrix.scaleM(matrix, 0, s.getDouble(0).toFloat(), s.getDouble(1).toFloat(), s.getDouble(2).toFloat())
            }
        }
        return matrix
    }

    private fun processMesh(json: JSONObject, binBuffer: ByteBuffer, mesh: JSONObject, matrix: FloatArray, allVertices: MutableList<Float>, allIndices: MutableList<Short>, allColors: MutableList<Float>, allNormals: MutableList<Float>) {
        val primitives = mesh.getJSONArray("primitives")
        for (p in 0 until primitives.length()) {
            val primitive = primitives.getJSONObject(p)
            val attributes = primitive.getJSONObject("attributes")
            val baseIdx = (allVertices.size / 3).toShort()
            
            val vData = getFloatArray(json, binBuffer, attributes.getInt("POSITION"))
            for (i in vData.indices step 3) {
                val res = FloatArray(4)
                Matrix.multiplyMV(res, 0, matrix, 0, floatArrayOf(vData[i], vData[i+1], vData[i+2], 1f), 0)
                allVertices.add(res[0]); allVertices.add(res[1]); allVertices.add(res[2])
            }

            if (attributes.has("NORMAL")) {
                val nData = getFloatArray(json, binBuffer, attributes.getInt("NORMAL"))
                val nMatrix = matrix.clone().apply { this[12]=0f; this[13]=0f; this[14]=0f }
                for (i in nData.indices step 3) {
                    val res = FloatArray(4)
                    Matrix.multiplyMV(res, 0, nMatrix, 0, floatArrayOf(nData[i], nData[i+1], nData[i+2], 0f), 0)
                    allNormals.add(res[0]); allNormals.add(res[1]); allNormals.add(res[2])
                }
            } else {
                repeat(vData.size / 3) { allNormals.add(0f); allNormals.add(1f); allNormals.add(0f) }
            }

            if (primitive.has("indices")) {
                val iData = getIndicesArray(json, binBuffer, primitive.getInt("indices"))
                for (idx in iData) allIndices.add((idx + baseIdx).toShort())
            } else {
                repeat(vData.size / 3) { i -> allIndices.add((baseIdx + i).toShort()) }
            }

            if (attributes.has("COLOR_0")) {
                val cData = getColorArray(json, binBuffer, attributes.getInt("COLOR_0"))
                for (c in cData) allColors.add(c)
            } else {
                repeat(vData.size / 3) { allColors.add(0.7f); allColors.add(0.7f); allColors.add(0.7f); allColors.add(1.0f) }
            }
        }
    }

    private fun getFloatArray(json: JSONObject, binBuffer: ByteBuffer, idx: Int): FloatArray {
        val acc = json.getJSONArray("accessors").getJSONObject(idx)
        val bv = json.getJSONArray("bufferViews").getJSONObject(acc.getInt("bufferView"))
        val count = acc.getInt("count")
        val type = acc.getString("type")
        val num = when(type) { "VEC2"->2; "VEC3"->3; "VEC4"->4; else->1 }
        val off = bv.optInt("byteOffset", 0) + acc.optInt("byteOffset", 0)
        val stride = bv.optInt("byteStride", 0).let { if (it == 0) num * 4 else it }
        val res = FloatArray(count * num)
        for (i in 0 until count) {
            binBuffer.position(off + i * stride)
            for (j in 0 until num) res[i * num + j] = binBuffer.float
        }
        return res
    }

    private fun getColorArray(json: JSONObject, binBuffer: ByteBuffer, idx: Int): FloatArray {
        val acc = json.getJSONArray("accessors").getJSONObject(idx)
        val bv = json.getJSONArray("bufferViews").getJSONObject(acc.getInt("bufferView"))
        val count = acc.getInt("count")
        val numIn = if (acc.getString("type") == "VEC4") 4 else 3
        val compType = acc.getInt("componentType")
        val off = bv.optInt("byteOffset", 0) + acc.optInt("byteOffset", 0)
        val compS = when(compType) { 5126->4; 5123->2; 5121->1; else->4 }
        val stride = bv.optInt("byteStride", 0).let { if (it == 0) numIn * compS else it }
        val res = FloatArray(count * 4)
        for (i in 0 until count) {
            binBuffer.position(off + i * stride)
            for (j in 0 until 4) {
                if (j < numIn) {
                    res[i * 4 + j] = when(compType) {
                        5126 -> binBuffer.float
                        5123 -> (binBuffer.short.toInt() and 0xFFFF) / 65535f
                        5121 -> (binBuffer.get().toInt() and 0xFF) / 255f
                        else -> 1.0f
                    }
                } else res[i * 4 + j] = 1.0f
            }
        }
        return res
    }

    private fun getIndicesArray(json: JSONObject, binBuffer: ByteBuffer, idx: Int): IntArray {
        val acc = json.getJSONArray("accessors").getJSONObject(idx)
        val bv = json.getJSONArray("bufferViews").getJSONObject(acc.getInt("bufferView"))
        val count = acc.getInt("count")
        val compType = acc.getInt("componentType")
        val off = bv.optInt("byteOffset", 0) + acc.optInt("byteOffset", 0)
        val compS = when(compType) { 5125->4; 5123->2; 5121->1; else->2 }
        val stride = bv.optInt("byteStride", 0).let { if (it == 0) compS else it }
        val res = IntArray(count)
        for (i in 0 until count) {
            binBuffer.position(off + i * stride)
            res[i] = when(compType) {
                5125 -> binBuffer.int
                5123 -> binBuffer.short.toInt() and 0xFFFF
                5121 -> binBuffer.get().toInt() and 0xFF
                else -> binBuffer.short.toInt() and 0xFFFF
            }
        }
        return res
    }
}
