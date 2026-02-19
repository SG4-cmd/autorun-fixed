package com.example.autorun.ui

import android.content.Context
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * 【ModelLoader】
 * .obj 形式のモデルファイルを読み込み、頂点配列を生成します。
 */
object ModelLoader {

    fun loadObj(context: Context, fileName: String): FloatArray {
        val vertices = mutableListOf<Float>()
        val normals = mutableListOf<Float>()
        val faces = mutableListOf<Int>()
        val finalVertices = mutableListOf<Float>()

        try {
            val reader = BufferedReader(InputStreamReader(context.assets.open(fileName)))
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                val tokens = line!!.split("\\s+".toRegex())
                if (tokens.isEmpty()) continue

                when (tokens[0]) {
                    "v" -> { // 頂点
                        vertices.add(tokens[1].toFloat())
                        vertices.add(tokens[2].toFloat())
                        vertices.add(tokens[3].toFloat())
                    }
                    "f" -> { // 面 (三角形想定)
                        for (i in 1..3) {
                            val parts = tokens[i].split("/")
                            val vIdx = parts[0].toInt() - 1
                            faces.add(vIdx)
                        }
                    }
                }
            }
            reader.close()

            // 描画用のフラットな配列に変換
            for (vIdx in faces) {
                finalVertices.add(vertices[vIdx * 3])
                finalVertices.add(vertices[vIdx * 3 + 1])
                finalVertices.add(vertices[vIdx * 3 + 2])
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return floatArrayOf() // 失敗時は空
        }

        return finalVertices.toFloatArray()
    }

    /**
     * 読み込んだモデルに対して、仮のカラーデータを生成します。
     */
    fun generateDummyColors(vertexCount: Int): FloatArray {
        val colors = FloatArray(vertexCount * 4)
        for (i in 0 until vertexCount) {
            colors[i * 4] = 0.8f     // R
            colors[i * 4 + 1] = 0.1f // G
            colors[i * 4 + 2] = 0.1f // B
            colors[i * 4 + 3] = 1.0f // A
        }
        return colors
    }
}
