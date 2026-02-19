package com.example.autorun.ui

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 【GltfLoader: プロフェッショナル版】
 * GLB内の全メッシュ・全プリミティブを統合して読み込みます。
 */
object GltfLoader {

    class ModelData(val vertices: FloatArray, val indices: ShortArray, val colors: FloatArray)

    fun loadGlb(context: Context, fileName: String): ModelData? {
        return try {
            val inputStream = context.assets.open(fileName)
            val bytes = inputStream.readBytes()
            inputStream.close()

            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

            // Header (12 bytes)
            if (buffer.int != 0x46546C67) throw Exception("GLBマジックナンバーが不正です")
            buffer.int // version
            buffer.int // totalLength

            // JSON Chunk
            val jsonLength = buffer.int
            if (buffer.int != 0x4E4F534A) throw Exception("JSONチャンクが見つかりません")
            val jsonBytes = ByteArray(jsonLength)
            buffer.get(jsonBytes)
            val json = JSONObject(String(jsonBytes))

            // BIN Chunk
            buffer.int // binLength
            if (buffer.int != 0x004E4942) throw Exception("BINチャンクが見つかりません")
            val binBuffer = buffer.slice().order(ByteOrder.LITTLE_ENDIAN)

            val allVertices = mutableListOf<Float>()
            val allIndices = mutableListOf<Short>()

            val meshes = json.getJSONArray("meshes")
            
            // 全てのメッシュとプリミティブを走査
            for (m in 0 until meshes.length()) {
                val primitives = meshes.getJSONObject(m).getJSONArray("primitives")
                for (p in 0 until primitives.length()) {
                    val primitive = primitives.getJSONObject(p)
                    val attributes = primitive.getJSONObject("attributes")
                    
                    if (!attributes.has("POSITION")) continue

                    // 頂点データのオフセット
                    val currentVertexOffset = (allVertices.size / 3).toShort()
                    
                    // POSITION 読み込み
                    val posAccessorIdx = attributes.getInt("POSITION")
                    val vData = getFloatArray(json, binBuffer, posAccessorIdx)
                    allVertices.addAll(vData.toList())

                    // INDICES 読み込み
                    if (primitive.has("indices")) {
                        val indicesIdx = primitive.getInt("indices")
                        val iData = getShortArray(json, binBuffer, indicesIdx)
                        // インデックスにオフセットを加えて追加
                        for (idx in iData) {
                            allIndices.add((idx + currentVertexOffset).toShort())
                        }
                    } else {
                        // インデックスがない場合は連番生成
                        for (i in 0 until vData.size / 3) {
                            allIndices.add((i + currentVertexOffset).toShort())
                        }
                    }
                }
            }

            if (allVertices.isEmpty()) throw Exception("頂点データが空です")

            // カラー生成（全パーツ一律の色）
            val finalColors = FloatArray((allVertices.size / 3) * 4) { i ->
                when (i % 4) {
                    0 -> 0.6f; 1 -> 0.6f; 2 -> 0.7f; else -> 1.0f 
                }
            }

            Log.i("GltfLoader", "成功: $fileName (${allVertices.size / 3} 頂点, ${allIndices.size / 3} ポリゴン)")
            ModelData(allVertices.toFloatArray(), allIndices.toShortArray(), finalColors)

        } catch (e: Exception) {
            Log.e("GltfLoader", "読み込みエラー ($fileName): ${e.message}")
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
