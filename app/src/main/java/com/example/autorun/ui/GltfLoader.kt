package com.example.autorun.ui

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 【GltfLoader】
 * モデルを読み込み、サイズを1.0に正規化してデータベースでの制御を容易にします。
 */
object GltfLoader {

    class ModelData(val vertices: FloatArray, val indices: ShortArray, val colors: FloatArray)

    fun loadGlb(context: Context, fileName: String): ModelData? {
        return try {
            val inputStream = context.assets.open(fileName)
            val bytes = inputStream.readBytes()
            inputStream.close()

            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            if (buffer.int != 0x46546C67) throw Exception("Invalid Magic")
            buffer.int; buffer.int

            val jsonLength = buffer.int
            if (buffer.int != 0x4E4F534A) throw Exception("No JSON")
            val jsonBytes = ByteArray(jsonLength)
            buffer.get(jsonBytes)
            val json = JSONObject(String(jsonBytes))

            buffer.int; buffer.int
            val binBuffer = buffer.slice().order(ByteOrder.LITTLE_ENDIAN)

            val allVertices = mutableListOf<Float>()
            val allIndices = mutableListOf<Short>()
            val meshes = json.getJSONArray("meshes")
            
            for (m in 0 until meshes.length()) {
                val primitives = meshes.getJSONObject(m).getJSONArray("primitives")
                for (p in 0 until primitives.length()) {
                    val primitive = primitives.getJSONObject(p)
                    val attributes = primitive.getJSONObject("attributes")
                    if (!attributes.has("POSITION")) continue

                    val currentVertexOffset = (allVertices.size / 3).toShort()
                    val vData = getFloatArray(json, binBuffer, attributes.getInt("POSITION"))
                    allVertices.addAll(vData.toList())

                    if (primitive.has("indices")) {
                        val iData = getShortArray(json, binBuffer, primitive.getInt("indices"))
                        for (idx in iData) allIndices.add((idx + currentVertexOffset).toShort())
                    } else {
                        for (i in 0 until vData.size / 3) allIndices.add((i + currentVertexOffset).toShort())
                    }
                }
            }

            // --- プロ仕様の正規化プロセス ---
            val vArray = allVertices.toFloatArray()
            var minX = Float.MAX_VALUE; var maxX = Float.MIN_VALUE
            var minY = Float.MAX_VALUE; var maxY = Float.MIN_VALUE
            var minZ = Float.MAX_VALUE; var maxZ = Float.MIN_VALUE

            for (i in vArray.indices step 3) {
                if (vArray[i] < minX) minX = vArray[i]; if (vArray[i] > maxX) maxX = vArray[i]
                if (vArray[i+1] < minY) minY = vArray[i+1]; if (vArray[i+1] > maxY) maxY = vArray[i+1]
                if (vArray[i+2] < minZ) minZ = vArray[i+2]; if (vArray[i+2] > maxZ) maxZ = vArray[i+2]
            }

            val sizeX = maxX - minX; val sizeY = maxY - minY; val sizeZ = maxZ - minZ
            val centerX = (maxX + minX) / 2f
            val centerZ = (maxZ + minZ) / 2f

            for (i in vArray.indices step 3) {
                // 中心を(0,0)に、底を0にする。さらに各軸を1.0の範囲にスケーリング
                vArray[i] = (vArray[i] - centerX) / sizeX
                vArray[i+1] = (vArray[i+1] - minY) / sizeY
                vArray[i+2] = (vArray[i+2] - centerZ) / sizeZ
            }

            val colors = FloatArray((vArray.size / 3) * 4) { i -> if (i % 4 == 3) 1.0f else 0.8f }
            ModelData(vArray, allIndices.toShortArray(), colors)
        } catch (e: Exception) {
            Log.e("GltfLoader", "Load Error: ${e.message}"); null
        }
    }

    private fun getFloatArray(json: JSONObject, binBuffer: ByteBuffer, accessorIdx: Int): FloatArray {
        val accessor = json.getJSONArray("accessors").getJSONObject(accessorIdx)
        val view = json.getJSONArray("bufferViews").getJSONObject(accessor.getInt("bufferView"))
        binBuffer.position(view.optInt("byteOffset", 0) + accessor.optInt("byteOffset", 0))
        return FloatArray(accessor.getInt("count") * 3) { binBuffer.float }
    }

    private fun getShortArray(json: JSONObject, binBuffer: ByteBuffer, accessorIdx: Int): ShortArray {
        val accessor = json.getJSONArray("accessors").getJSONObject(accessorIdx)
        val view = json.getJSONArray("bufferViews").getJSONObject(accessor.getInt("bufferView"))
        val type = accessor.getInt("componentType")
        binBuffer.position(view.optInt("byteOffset", 0) + accessor.optInt("byteOffset", 0))
        return ShortArray(accessor.getInt("count")) { if (type == 5123) binBuffer.short else (binBuffer.int and 0xFFFF).toShort() }
    }
}
