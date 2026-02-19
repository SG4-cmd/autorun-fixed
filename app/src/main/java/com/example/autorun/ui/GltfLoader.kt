package com.example.autorun.ui

import android.content.Context
import android.opengl.Matrix
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 【GltfLoader: プロフェッショナル版】
 * GLB(glTF 2.0)ファイルを解析し、頂点、インデックス、カラー情報を抽出します。
 * チャンクのパディング、インターリーブされたデータ、ノードの行列変換に対応しています。
 */
object GltfLoader {

    class ModelData(val vertices: FloatArray, val indices: ShortArray, val colors: FloatArray)

    fun loadGlb(context: Context, fileName: String): ModelData? {
        return try {
            val inputStream = context.assets.open(fileName)
            val bytes = inputStream.readBytes()
            inputStream.close()

            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            
            // Header
            if (buffer.int != 0x46546C67) throw Exception("Invalid GLB magic")
            val version = buffer.int
            val totalLength = buffer.int

            var json: JSONObject? = null
            var binBuffer: ByteBuffer? = null

            // Chunks
            while (buffer.remaining() >= 8) {
                val chunkLength = buffer.int
                val chunkType = buffer.int
                
                if (chunkType == 0x4E4F534A) { // JSON
                    val jsonBytes = ByteArray(chunkLength)
                    buffer.get(jsonBytes)
                    json = JSONObject(String(jsonBytes).trim())
                } else if (chunkType == 0x004E4942) { // BIN
                    binBuffer = buffer.slice().order(ByteOrder.LITTLE_ENDIAN)
                    buffer.position(buffer.position() + chunkLength)
                } else {
                    buffer.position(buffer.position() + chunkLength)
                }
                
                // Align to 4 bytes
                val padding = (4 - (chunkLength % 4)) % 4
                if (padding > 0 && buffer.remaining() >= padding) {
                    buffer.position(buffer.position() + padding)
                }
            }

            if (json == null || binBuffer == null) {
                Log.e("GltfLoader", "Missing JSON or BIN chunk in $fileName")
                return null
            }

            val allVertices = mutableListOf<Float>()
            val allIndices = mutableListOf<Short>()
            val allColors = mutableListOf<Float>()

            val nodes = json.optJSONArray("nodes")
            val meshes = json.optJSONArray("meshes")
            
            // ノードを走査してメッシュとトランスフォームを処理
            if (nodes != null) {
                for (i in 0 until nodes.length()) {
                    val node = nodes.getJSONObject(i)
                    if (node.has("mesh")) {
                        val meshIdx = node.getInt("mesh")
                        val mesh = meshes!!.getJSONObject(meshIdx)
                        val matrix = getNodeMatrix(node)
                        
                        processMesh(json, binBuffer, mesh, matrix, allVertices, allIndices, allColors)
                    }
                }
            } else if (meshes != null) {
                // ノードがない場合は全メッシュをそのまま読み込む
                val identity = FloatArray(16).apply { Matrix.setIdentityM(this, 0) }
                for (i in 0 until meshes.length()) {
                    processMesh(json, binBuffer, meshes.getJSONObject(i), identity, allVertices, allIndices, allColors)
                }
            }

            // --- 正規化と軸補正 ---
            val vArray = allVertices.toFloatArray()
            if (vArray.isEmpty()) return null

            var minX = Float.MAX_VALUE; var maxX = -Float.MAX_VALUE
            var minY = Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
            var minZ = Float.MAX_VALUE; var maxZ = -Float.MAX_VALUE

            for (i in vArray.indices step 3) {
                minX = minOf(minX, vArray[i]); maxX = maxOf(maxX, vArray[i])
                minY = minOf(minY, vArray[i+1]); maxY = maxOf(maxY, vArray[i+1])
                minZ = minOf(minZ, vArray[i+2]); maxZ = maxOf(maxZ, vArray[i+2])
            }

            val sizeX = (maxX - minX).coerceAtLeast(0.001f)
            val sizeY = (maxY - minY).coerceAtLeast(0.001f)
            val sizeZ = (maxZ - minZ).coerceAtLeast(0.001f)

            // 各軸独立正規化: 
            // X: [-0.5, 0.5] (中心合わせ)
            // Y: [0, 1.0] (底合わせ)
            // Z: [-0.5, 0.5] (中心合わせ)
            for (i in vArray.indices step 3) {
                vArray[i] = (vArray[i] - (maxX + minX) / 2f) / sizeX
                vArray[i+1] = (vArray[i+1] - minY) / sizeY
                vArray[i+2] = (vArray[i+2] - (maxZ + minZ) / 2f) / sizeZ
            }

            Log.i("GltfLoader", "Success: $fileName, Verts: ${vArray.size/3}, Tris: ${allIndices.size/3}")
            return ModelData(vArray, allIndices.toShortArray(), allColors.toFloatArray())
        } catch (e: Exception) {
            Log.e("GltfLoader", "Error: ${e.message}")
            e.printStackTrace()
            return null
        }
    }

    private fun getNodeMatrix(node: JSONObject): FloatArray {
        val matrix = FloatArray(16)
        if (node.has("matrix")) {
            val arr = node.getJSONArray("matrix")
            for (i in 0 until 16) matrix[i] = arr.getDouble(i).toFloat()
        } else {
            Matrix.setIdentityM(matrix, 0)
            if (node.has("translation")) {
                val t = node.getJSONArray("translation")
                Matrix.translateM(matrix, 0, t.getDouble(0).toFloat(), t.getDouble(1).toFloat(), t.getDouble(2).toFloat())
            }
            if (node.has("rotation")) {
                val r = node.getJSONArray("rotation")
                val rotMatrix = FloatArray(16)
                // Quaternion to Matrix conversion (simplified for GL)
                val qx = r.getDouble(0).toFloat(); val qy = r.getDouble(1).toFloat(); val qz = r.getDouble(2).toFloat(); val qw = r.getDouble(3).toFloat()
                val xx = qx * qx; val xy = qx * qy; val xz = qx * qz; val xw = qx * qw
                val yy = qy * qy; val yz = qy * qz; val yw = qy * qw
                val zz = qz * qz; val zw = qz * qw
                rotMatrix[0] = 1 - 2 * (yy + zz); rotMatrix[1] = 2 * (xy + zw); rotMatrix[2] = 2 * (xz - yw); rotMatrix[3] = 0f
                rotMatrix[4] = 2 * (xy - zw); rotMatrix[5] = 1 - 2 * (xx + zz); rotMatrix[6] = 2 * (yz + xw); rotMatrix[7] = 0f
                rotMatrix[8] = 2 * (xz + yw); rotMatrix[9] = 2 * (yz - xw); rotMatrix[10] = 1 - 2 * (xx + yy); rotMatrix[11] = 0f
                rotMatrix[12] = 0f; rotMatrix[13] = 0f; rotMatrix[14] = 0f; rotMatrix[15] = 1f
                val temp = matrix.clone()
                Matrix.multiplyMM(matrix, 0, temp, 0, rotMatrix, 0)
            }
            if (node.has("scale")) {
                val s = node.getJSONArray("scale")
                Matrix.scaleM(matrix, 0, s.getDouble(0).toFloat(), s.getDouble(1).toFloat(), s.getDouble(2).toFloat())
            }
        }
        return matrix
    }

    private fun processMesh(json: JSONObject, binBuffer: ByteBuffer, mesh: JSONObject, matrix: FloatArray, allVertices: MutableList<Float>, allIndices: MutableList<Short>, allColors: MutableList<Float>) {
        val primitives = mesh.getJSONArray("primitives")
        for (p in 0 until primitives.length()) {
            val primitive = primitives.getJSONObject(p)
            val attributes = primitive.getJSONObject("attributes")
            
            val baseVertexIdx = (allVertices.size / 3).toShort()
            
            // POSITION
            val vData = getFloatArray(json, binBuffer, attributes.getInt("POSITION"))
            val transformedV = FloatArray(3)
            for (i in vData.indices step 3) {
                // 行列による座標変換
                transformedV[0] = vData[i]; transformedV[1] = vData[i+1]; transformedV[2] = vData[i+2]
                val resultV = FloatArray(4)
                Matrix.multiplyMV(resultV, 0, matrix, 0, floatArrayOf(transformedV[0], transformedV[1], transformedV[2], 1f), 0)
                allVertices.add(resultV[0]); allVertices.add(resultV[1]); allVertices.add(resultV[2])
            }

            // INDICES
            if (primitive.has("indices")) {
                val iData = getIndicesArray(json, binBuffer, primitive.getInt("indices"))
                for (idx in iData) {
                    allIndices.add((idx + baseVertexIdx).toShort())
                }
            } else {
                for (i in 0 until vData.size / 3) allIndices.add((baseVertexIdx + i).toShort())
            }

            // COLORS
            if (attributes.has("COLOR_0")) {
                val cData = getColorArray(json, binBuffer, attributes.getInt("COLOR_0"))
                for (c in cData) allColors.add(c)
            } else {
                for (i in 0 until vData.size / 3) {
                    allColors.add(0.7f); allColors.add(0.7f); allColors.add(0.7f); allColors.add(1.0f)
                }
            }
        }
    }

    private fun getFloatArray(json: JSONObject, binBuffer: ByteBuffer, accessorIdx: Int): FloatArray {
        val accessor = json.getJSONArray("accessors").getJSONObject(accessorIdx)
        val bufferView = json.getJSONArray("bufferViews").getJSONObject(accessor.getInt("bufferView"))
        val count = accessor.getInt("count")
        val type = accessor.getString("type")
        val numComp = when(type) { "VEC2" -> 2; "VEC3" -> 3; "VEC4" -> 4; else -> 1 }
        
        val offset = bufferView.optInt("byteOffset", 0) + accessor.optInt("byteOffset", 0)
        val stride = bufferView.optInt("byteStride", 0).let { if (it == 0) numComp * 4 else it }
        
        val res = FloatArray(count * numComp)
        for (i in 0 until count) {
            binBuffer.position(offset + i * stride)
            for (j in 0 until numComp) res[i * numComp + j] = binBuffer.float
        }
        return res
    }

    private fun getColorArray(json: JSONObject, binBuffer: ByteBuffer, accessorIdx: Int): FloatArray {
        val accessor = json.getJSONArray("accessors").getJSONObject(accessorIdx)
        val bufferView = json.getJSONArray("bufferViews").getJSONObject(accessor.getInt("bufferView"))
        val count = accessor.getInt("count")
        val type = accessor.getString("type")
        val compType = accessor.getInt("componentType")
        val numIn = if (type == "VEC4") 4 else 3
        
        val offset = bufferView.optInt("byteOffset", 0) + accessor.optInt("byteOffset", 0)
        val compSize = when(compType) { 5126 -> 4; 5123 -> 2; 5121 -> 1; else -> 4 }
        val stride = bufferView.optInt("byteStride", 0).let { if (it == 0) numIn * compSize else it }
        
        val res = FloatArray(count * 4) // RGBA
        for (i in 0 until count) {
            binBuffer.position(offset + i * stride)
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

    private fun getIndicesArray(json: JSONObject, binBuffer: ByteBuffer, accessorIdx: Int): IntArray {
        val accessor = json.getJSONArray("accessors").getJSONObject(accessorIdx)
        val bufferView = json.getJSONArray("bufferViews").getJSONObject(accessor.getInt("bufferView"))
        val count = accessor.getInt("count")
        val compType = accessor.getInt("componentType")
        
        val offset = bufferView.optInt("byteOffset", 0) + accessor.optInt("byteOffset", 0)
        val compSize = when(compType) { 5125 -> 4; 5123 -> 2; 5121 -> 1; else -> 2 }
        val stride = bufferView.optInt("byteStride", 0).let { if (it == 0) compSize else it }
        
        val res = IntArray(count)
        for (i in 0 until count) {
            binBuffer.position(offset + i * stride)
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
