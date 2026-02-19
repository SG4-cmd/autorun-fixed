package com.example.autorun.ui

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 【GltfLoader: プロフェッショナル版】
 * GLB内の全メッシュを統合し、正しい中心座標に補正して読み込みます。
 */
object GltfLoader {

    class ModelData(val vertices: FloatArray, val indices: ShortArray, val colors: FloatArray)

    fun loadGlb(context: Context, fileName: String): ModelData? {
        return try {
            val inputStream = context.assets.open(fileName)
            val bytes = inputStream.readBytes()
            inputStream.close()

            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

            if (buffer.int != 0x46546C67) throw Exception("GLBマジックナンバーが不正です")
            buffer.int // version
            buffer.int // totalLength

            val jsonLength = buffer.int
            if (buffer.int != 0x4E4F534A) throw Exception("JSONチャンクが見つかりません")
            val jsonBytes = ByteArray(jsonLength)
            buffer.get(jsonBytes)
            val json = JSONObject(String(jsonBytes))

            buffer.int // binLength
            if (buffer.int != 0x004E4942) throw Exception("BINチャンクが見つかりません")
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
                    
                    // 頂点データを追加
                    allVertices.addAll(vData.toList())

                    if (primitive.has("indices")) {
                        val iData = getShortArray(json, binBuffer, primitive.getInt("indices"))
                        for (idx in iData) {
                            allIndices.add((idx + currentVertexOffset).toShort())
                        }
                    } else {
                        for (i in 0 until vData.size / 3) {
                            allIndices.add((i + currentVertexOffset).toShort())
                        }
                    }
                }
            }

            if (allVertices.isEmpty()) throw Exception("頂点データが空です")

            // --- 中心座標と接地（地面）の補正 ---
            val verticesArray = allVertices.toFloatArray()
            var minY = Float.MAX_VALUE
            for (i in 1 until verticesArray.size step 3) {
                if (verticesArray[i] < minY) minY = verticesArray[i]
            }

            // 全頂点のY座標を補正（タイヤの底がY=0になるように）
            for (i in 1 until verticesArray.size step 3) {
                verticesArray[i] -= minY
            }

            val finalColors = FloatArray((verticesArray.size / 3) * 4) { i ->
                when (i % 4) {
                    0 -> 0.7f; 1 -> 0.1f; 2 -> 0.1f; else -> 1.0f // 車体の基本色：赤
                }
            }

            Log.i("GltfLoader", "成功: $fileName (${verticesArray.size / 3} 頂点)")
            ModelData(verticesArray, allIndices.toShortArray(), finalColors)

        } catch (e: Exception) {
            Log.e("GltfLoader", "読み込みエラー: ${e.message}")
            null
        }
    }

    private fun getFloatArray(json: JSONObject, binBuffer: ByteBuffer, accessorIdx: Int): FloatArray {
        val accessor = json.getJSONArray("accessors").getJSONObject(accessorIdx)
        val bufferViewIdx = accessor.getInt("bufferView")
        val count = accessor.getInt("count")
        val bufferView = json.getJSONArray("bufferViews").getJSONObject(bufferViewIdx)
        val offset = bufferView.optInt("byteOffset", 0) + accessor.optInt("byteOffset", 0)
        binBuffer.position(offset)
        return FloatArray(count * 3) { binBuffer.float }
    }

    private fun getShortArray(json: JSONObject, binBuffer: ByteBuffer, accessorIdx: Int): ShortArray {
        val accessor = json.getJSONArray("accessors").getJSONObject(accessorIdx)
        val bufferViewIdx = accessor.getInt("bufferView")
        val count = accessor.getInt("count")
        val bufferView = json.getJSONArray("bufferViews").getJSONObject(bufferViewIdx)
        val componentType = accessor.getInt("componentType")
        val offset = bufferView.optInt("byteOffset", 0) + accessor.optInt("byteOffset", 0)
        binBuffer.position(offset)
        return ShortArray(count) {
            if (componentType == 5123) binBuffer.short 
            else if (componentType == 5125) (binBuffer.int and 0xFFFF).toShort()
            else 0
        }
    }
}
