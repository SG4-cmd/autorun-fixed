package com.example.autorun.ui

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 【GltfLoader: プロフェッショナル版】
 * モデルを正しく正規化し、かつ色（頂点カラー）情報も読み込みます。
 */
object GltfLoader {

    class ModelData(val vertices: FloatArray, val indices: ShortArray, val colors: FloatArray)

    fun loadGlb(context: Context, fileName: String): ModelData? {
        return try {
            val inputStream = context.assets.open(fileName)
            val bytes = inputStream.readBytes()
            inputStream.close()

            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            if (buffer.int != 0x46546C67) throw Exception("Invalid GLB")
            buffer.int; buffer.int

            val jsonLength = buffer.int
            buffer.int // JSON type
            val jsonBytes = ByteArray(jsonLength)
            buffer.get(jsonBytes)
            val json = JSONObject(String(jsonBytes))

            buffer.int // BIN length
            buffer.int // BIN type
            val binBuffer = buffer.slice().order(ByteOrder.LITTLE_ENDIAN)

            val allVertices = mutableListOf<Float>()
            val allIndices = mutableListOf<Short>()
            val allColors = mutableListOf<Float>()

            val meshes = json.getJSONArray("meshes")
            for (m in 0 until meshes.length()) {
                val primitives = meshes.getJSONObject(m).getJSONArray("primitives")
                for (p in 0 until primitives.length()) {
                    val primitive = primitives.getJSONObject(p)
                    val attributes = primitive.getJSONObject("attributes")
                    
                    val currentVertexOffset = (allVertices.size / 3).toShort()
                    
                    // 頂点座標
                    val vData = getFloatArray(json, binBuffer, attributes.getInt("POSITION"))
                    allVertices.addAll(vData.toList())

                    // インデックス
                    if (primitive.has("indices")) {
                        val iData = getShortArray(json, binBuffer, primitive.getInt("indices"))
                        for (idx in iData) allIndices.add((idx + currentVertexOffset).toShort())
                    }

                    // 頂点カラー (COLOR_0)
                    if (attributes.has("COLOR_0")) {
                        val cData = getFloatArray(json, binBuffer, attributes.getInt("COLOR_0"), isColor = true)
                        allColors.addAll(cData.toList())
                    } else {
                        // カラーがない場合はデフォルトのグレーを追加
                        for (i in 0 until vData.size / 3) {
                            allColors.add(0.7f); allColors.add(0.7f); allColors.add(0.7f); allColors.add(1.0f)
                        }
                    }
                }
            }

            // --- 正規化と軸補正 ---
            val vArray = allVertices.toFloatArray()
            var minX = Float.MAX_VALUE; var maxX = -Float.MAX_VALUE
            var minY = Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
            var minZ = Float.MAX_VALUE; var maxZ = -Float.MAX_VALUE

            for (i in vArray.indices step 3) {
                minX = minOf(minX, vArray[i]); maxX = maxOf(maxX, vArray[i])
                minY = minOf(minY, vArray[i+1]); maxY = maxOf(maxY, vArray[i+1])
                minZ = minOf(minZ, vArray[i+2]); maxZ = maxOf(maxZ, vArray[i+2])
            }

            // 長さが0にならないようにガード
            val sizeX = (maxX - minX).let { if (it == 0f) 1f else it }
            val sizeY = (maxY - minY).let { if (it == 0f) 1f else it }
            val sizeZ = (maxZ - minZ).let { if (it == 0f) 1f else it }

            for (i in vArray.indices step 3) {
                // 中心を0に、底を0にする（スケーリング前に実施）
                vArray[i] = (vArray[i] - (maxX + minX) / 2f) / sizeX
                vArray[i+1] = (vArray[i+1] - minY) / sizeY
                vArray[i+2] = (vArray[i+2] - (maxZ + minZ) / 2f) / sizeZ
            }

            Log.i("GltfLoader", "Done: $fileName, Verts: ${vArray.size/3}")
            ModelData(vArray, allIndices.toShortArray(), allColors.toFloatArray())
        } catch (e: Exception) {
            Log.e("GltfLoader", "Err: ${e.message}"); null
        }
    }

    private fun getFloatArray(json: JSONObject, binBuffer: ByteBuffer, accessorIdx: Int, isColor: Boolean = false): FloatArray {
        val accessor = json.getJSONArray("accessors").getJSONObject(accessorIdx)
        val view = json.getJSONArray("bufferViews").getJSONObject(accessor.getInt("bufferView"))
        val count = accessor.getInt("count")
        val type = accessor.getString("type") // "VEC3" or "VEC4"
        val numComponents = if (type == "VEC4") 4 else 3
        
        binBuffer.position(view.optInt("byteOffset", 0) + accessor.optInt("byteOffset", 0))
        
        val result = FloatArray(count * numComponents)
        for (i in result.indices) result[i] = binBuffer.float
        return result
    }

    private fun getShortArray(json: JSONObject, binBuffer: ByteBuffer, accessorIdx: Int): ShortArray {
        val accessor = json.getJSONArray("accessors").getJSONObject(accessorIdx)
        val view = json.getJSONArray("bufferViews").getJSONObject(accessor.getInt("bufferView"))
        val count = accessor.getInt("count")
        val componentType = accessor.getInt("componentType")
        binBuffer.position(view.optInt("byteOffset", 0) + accessor.optInt("byteOffset", 0))
        return ShortArray(count) {
            if (componentType == 5123) binBuffer.short else (binBuffer.int and 0xFFFF).toShort()
        }
    }
}
