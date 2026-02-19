package com.example.autorun.ui

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 【GltfLoader】
 * .glb (Binary glTF) 形式のモデルを解析します。
 * 柔軟なアトリビュート検索機能を備えた堅牢な実装です。
 */
object GltfLoader {

    class ModelData(val vertices: FloatArray, val indices: ShortArray, val colors: FloatArray)

    fun loadGlb(context: Context, fileName: String): ModelData? {
        return try {
            val inputStream = context.assets.open(fileName)
            val bytes = inputStream.readBytes()
            inputStream.close()

            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

            // 1. Header 検証
            val magic = buffer.int
            if (magic != 0x46546C67) {
                Log.e("GltfLoader", "Invalid Magic: ${Integer.toHexString(magic)}")
                return null
            }
            val version = buffer.int
            val totalLength = buffer.int

            // 2. JSON Chunk 解析
            val jsonChunkLength = buffer.int
            val jsonChunkType = buffer.int
            if (jsonChunkType != 0x4E4F534A) {
                Log.e("GltfLoader", "JSON chunk not found")
                return null
            }
            
            val jsonBytes = ByteArray(jsonChunkLength)
            buffer.get(jsonBytes)
            val jsonString = String(jsonBytes)
            val json = JSONObject(jsonString)

            // 3. Binary Chunk 位置特定
            val binChunkLength = buffer.int
            val binChunkType = buffer.int
            // glbによってはパディングがあるため、現在位置を保存
            val binBuffer = buffer.slice().order(ByteOrder.LITTLE_ENDIAN)

            // --- メッシュ解析 ---
            val meshes = json.getJSONArray("meshes")
            val mesh = meshes.getJSONObject(0)
            val primitives = mesh.getJSONArray("primitives")
            val primitive = primitives.getJSONObject(0)
            val attributes = primitive.getJSONObject("attributes")

            // POSITION 属性の検索 (必須)
            if (!attributes.has("POSITION")) {
                Log.e("GltfLoader", "No POSITION attribute found")
                return null
            }
            val posAccessorIdx = attributes.getInt("POSITION")
            val vertices = getFloatArray(json, binBuffer, posAccessorIdx)

            // indices (インデックス) の検索 (推奨)
            val indices = if (primitive.has("indices")) {
                val indicesIdx = primitive.getInt("indices")
                getShortArray(json, binBuffer, indicesIdx)
            } else {
                // インデックスがない場合は、0,1,2...と連番を生成
                ShortArray(vertices.size / 3) { it.toShort() }
            }

            // カラーデータの生成 (モデルを赤色で表示)
            val colors = FloatArray((vertices.size / 3) * 4) { i ->
                when (i % 4) {
                    0 -> 1.0f // R
                    1 -> 0.2f // G
                    2 -> 0.2f // B
                    else -> 1.0f // A
                }
            }

            Log.i("GltfLoader", "Successfully loaded $fileName: ${vertices.size/3} vertices")
            ModelData(vertices, indices, colors)
        } catch (e: Exception) {
            Log.e("GltfLoader", "Error parsing GLB: ${e.message}")
            e.printStackTrace()
            null
        }
    }

    private fun getFloatArray(json: JSONObject, binBuffer: ByteBuffer, accessorIdx: Int): FloatArray {
        val accessor = json.getJSONArray("accessors").getJSONObject(accessorIdx)
        val bufferViewIdx = accessor.getInt("bufferView")
        val count = accessor.getInt("count")
        val bufferView = json.getJSONArray("bufferViews").getJSONObject(bufferViewIdx)
        
        val byteOffset = bufferView.optInt("byteOffset", 0)
        val accessorByteOffset = accessor.optInt("byteOffset", 0)
        
        binBuffer.position(byteOffset + accessorByteOffset)
        
        val result = FloatArray(count * 3)
        for (i in 0 until count * 3) {
            result[i] = binBuffer.float
        }
        return result
    }

    private fun getShortArray(json: JSONObject, binBuffer: ByteBuffer, accessorIdx: Int): ShortArray {
        val accessor = json.getJSONArray("accessors").getJSONObject(accessorIdx)
        val bufferViewIdx = accessor.getInt("bufferView")
        val count = accessor.getInt("count")
        val bufferView = json.getJSONArray("bufferViews").getJSONObject(bufferViewIdx)
        
        val byteOffset = bufferView.optInt("byteOffset", 0)
        val accessorByteOffset = accessor.optInt("byteOffset", 0)
        val componentType = accessor.getInt("componentType") // 5123 = Unsigned Short
        
        binBuffer.position(byteOffset + accessorByteOffset)
        
        val result = ShortArray(count)
        if (componentType == 5123) { // UNSIGNED_SHORT
            for (i in 0 until count) result[i] = binBuffer.short
        } else if (componentType == 5125) { // UNSIGNED_INT (2.0で一般的)
            for (i in 0 until count) {
                result[i] = (binBuffer.int and 0xFFFF).toShort()
            }
        }
        return result
    }
}
