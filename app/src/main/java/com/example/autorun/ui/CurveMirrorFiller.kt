package com.example.autorun.ui

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import com.example.autorun.config.GameSettings

/**
 * 【CurveMirrorFiller】
 * ガードレールの隙間を埋めるための描画補助クラス。
 */
object CurveMirrorFiller {

    private val guardrailLinePaint = Paint().apply {
        color = GameSettings.COLOR_GUARDRAIL_PLATE
        style = Paint.Style.STROKE
        strokeWidth = 2f
        isAntiAlias = true
    }

    private val linePath = Path()

    fun drawIntersectionFill(
        canvas: Canvas,
        xCoords1: FloatArray, yCoords1: FloatArray,
        xCoords2: FloatArray, yCoords2: FloatArray,
        count: Int,
        paint: Paint,
        isRight: Boolean = true
    ) {
        // データが不足している場合は描画しない
        if (count < 2) return

        // Xが最大（または最小）となるインデックスを探す
        var bestX = if (isRight) -Float.MAX_VALUE else Float.MAX_VALUE
        var bestIdx = -1

        for (i in 1 until count) {
            val x = xCoords1[i]
            if (x == 0f) continue 

            if (isRight) {
                if (x > bestX) {
                    bestX = x
                    bestIdx = i
                }
            } else {
                if (x < bestX) {
                    bestX = x
                    bestIdx = i
                }
            }
        }

        if (bestIdx != -1) {
            val prevIdx = (bestIdx - 1).coerceAtLeast(0)
            
            // 塗りつぶし対象となる4点
            val p1x = xCoords1[bestIdx]
            val p1y = yCoords1[bestIdx]
            val p2x = xCoords2[bestIdx]
            val p2y = yCoords2[bestIdx]
            val p3x = xCoords1[prevIdx]
            val p3y = yCoords1[prevIdx]
            val p4x = xCoords2[prevIdx]
            val p4y = yCoords2[prevIdx]

            // 1. 4点の内側をガードレール色で塗りつぶす
            linePath.reset()
            linePath.moveTo(p1x, p1y)
            linePath.lineTo(p2x, p2y)
            linePath.lineTo(p4x, p4y)
            linePath.lineTo(p3x, p3y)
            linePath.close()
            canvas.drawPath(linePath, paint)

            // 2. 頂点間を細い線で結ぶ（以前の黄色バーの置き換え）
            linePath.reset()
            linePath.moveTo(p1x, p1y)
            linePath.lineTo(p2x, p2y)
            canvas.drawPath(linePath, guardrailLinePaint)
        }
    }
}
