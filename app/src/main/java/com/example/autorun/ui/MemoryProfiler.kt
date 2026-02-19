package com.example.autorun.ui

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Debug
import com.example.autorun.core.GameState
import java.util.Locale

/**
 * 【MemoryProfiler: システムメモリ使用量の可視化ツール】
 * 開発者モード向けにコンパクトに情報を表示します。
 */
object MemoryProfiler {
    private val bgPaint = Paint().apply {
        color = Color.BLACK
        alpha = 180
        style = Paint.Style.FILL
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.GREEN
        textSize = 18f
    }
    private val barPaint = Paint().apply {
        style = Paint.Style.FILL
    }

    private var lastUpdateTime = 0L
    private val memoryInfo = Debug.MemoryInfo()

    private var javaHeap = 0
    private var nativeHeap = 0
    private var codeSize = 0
    private var graphicsSize = 0
    private var databaseSize = 0
    private var totalPss = 0

    fun draw(canvas: Canvas, state: GameState, x: Float, y: Float) {
        if (!state.isDeveloperMode) return

        updateMemoryStats()

        val width = 320f
        val height = 210f
        val rect = RectF(x, y, x + width, y + height)
        canvas.drawRoundRect(rect, 10f, 10f, bgPaint)

        val margin = 15f
        var currentY = y + 25f

        drawStat(canvas, "TOTAL PSS", totalPss, x + margin, currentY, width - margin * 2, Color.WHITE)
        currentY += 30f
        drawStat(canvas, "Java Heap", javaHeap, x + margin, currentY, width - margin * 2, 0xFF00E5FF.toInt())
        currentY += 28f
        drawStat(canvas, "Native", nativeHeap, x + margin, currentY, width - margin * 2, Color.YELLOW)
        currentY += 28f
        drawStat(canvas, "Graphics", graphicsSize, x + margin, currentY, width - margin * 2, 0xFFFF00FF.toInt())
        currentY += 28f
        drawStat(canvas, "Code/DEX", codeSize, x + margin, currentY, width - margin * 2, Color.GREEN)
        currentY += 28f
        drawStat(canvas, "Database", databaseSize, x + margin, currentY, width - margin * 2, 0xFF64FFDA.toInt())
    }

    private fun updateMemoryStats() {
        val now = System.currentTimeMillis()
        if (now - lastUpdateTime < 1000) return
        lastUpdateTime = now

        Debug.getMemoryInfo(memoryInfo)
        javaHeap = memoryInfo.getMemoryStat("summary.java-heap")?.toIntOrNull() ?: 0
        nativeHeap = memoryInfo.getMemoryStat("summary.native-heap")?.toIntOrNull() ?: 0
        codeSize = memoryInfo.getMemoryStat("summary.code")?.toIntOrNull() ?: 0
        graphicsSize = memoryInfo.getMemoryStat("summary.graphics")?.toIntOrNull() ?: 0
        databaseSize = memoryInfo.getMemoryStat("summary.database")?.toIntOrNull() ?: 0
        totalPss = memoryInfo.totalPss
    }

    private fun drawStat(canvas: Canvas, label: String, valueKb: Int, x: Float, y: Float, maxWidth: Float, color: Int) {
        val valueMb = valueKb / 1024f
        val text = "$label: ${String.format(Locale.US, "%.1f", valueMb)} MB"
        
        textPaint.color = color
        canvas.drawText(text, x, y, textPaint)

        val barMaxMb = 128f
        val barWidth = (valueMb / barMaxMb * maxWidth).coerceAtMost(maxWidth)
        barPaint.color = color
        barPaint.alpha = 60
        canvas.drawRect(x, y + 6f, x + barWidth, y + 12f, barPaint)
    }
}
