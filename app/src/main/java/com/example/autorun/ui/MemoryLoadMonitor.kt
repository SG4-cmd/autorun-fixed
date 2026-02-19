package com.example.autorun.ui

import android.graphics.*
import android.os.Debug
import java.util.Locale

/**
 * 【MemoryLoadMonitor】
 * システムのメモリ負荷状況をリアルタイムに可視化する専門モジュール。
 * トータル、JAVA HEAP, NATIVE, Graphic, Code, Other の6項目を表示します。
 */
object MemoryLoadMonitor {
    private val bgPaint = Paint().apply {
        color = Color.BLACK
        alpha = 160
        style = Paint.Style.FILL
    }
    private val borderPaint = Paint().apply {
        color = Color.WHITE
        alpha = 80
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 22f
        typeface = Typeface.MONOSPACE
    }
    private val barPaint = Paint().apply {
        style = Paint.Style.FILL
    }

    private val memInfo = Debug.MemoryInfo()
    private var lastUpdate = 0L

    // メモリ統計データ (KB)
    private var totalPss = 0
    private var javaHeap = 0
    private var nativeHeap = 0
    private var graphics = 0
    private var codeSize = 0
    private var otherSize = 0

    /**
     * 指定された座標にメモリ負荷状況を描画します。
     */
    fun draw(canvas: Canvas, x: Float, y: Float) {
        updateMemoryStats()

        val width = 380f
        val lineH = 40f
        val height = lineH * 6 + 25f
        
        // 背景パネル
        val rect = RectF(x, y, x + width, y + height)
        canvas.drawRoundRect(rect, 12f, 12f, bgPaint)
        canvas.drawRoundRect(rect, 12f, 12f, borderPaint)

        var currentY = y + 35f
        val margin = 20f
        val barMaxWidth = width - margin * 2
        val referenceMax = 512 * 1024 // 512MBをバーの100%基準とする

        // 各項目の描画
        drawStatRow(canvas, "TOTAL", totalPss, x + margin, currentY, barMaxWidth, Color.WHITE, referenceMax)
        currentY += lineH
        drawStatRow(canvas, "JAVA HEAP", javaHeap, x + margin, currentY, barMaxWidth, 0xFF00E5FF.toInt(), referenceMax)
        currentY += lineH
        drawStatRow(canvas, "NATIVE", nativeHeap, x + margin, currentY, barMaxWidth, Color.YELLOW, referenceMax)
        currentY += lineH
        drawStatRow(canvas, "GRAPHIC", graphics, x + margin, currentY, barMaxWidth, 0xFFFF00FF.toInt(), referenceMax)
        currentY += lineH
        drawStatRow(canvas, "CODE", codeSize, x + margin, currentY, barMaxWidth, Color.GREEN, referenceMax)
        currentY += lineH
        drawStatRow(canvas, "OTHER", otherSize, x + margin, currentY, barMaxWidth, Color.LTGRAY, referenceMax)
    }

    private fun updateMemoryStats() {
        val now = System.currentTimeMillis()
        if (now - lastUpdate < 1000) return // 1秒おきに更新
        lastUpdate = now

        Debug.getMemoryInfo(memInfo)
        totalPss = memInfo.totalPss
        javaHeap = getSafeStat("summary.java-heap")
        nativeHeap = getSafeStat("summary.native-heap")
        graphics = getSafeStat("summary.graphics")
        codeSize = getSafeStat("summary.code")
        otherSize = getSafeStat("summary.private-other")
    }

    private fun getSafeStat(key: String): Int {
        return try {
            memInfo.getMemoryStat(key)?.toInt() ?: 0
        } catch (e: Exception) {
            0
        }
    }

    private fun drawStatRow(canvas: Canvas, label: String, valueKb: Int, x: Float, y: Float, width: Float, color: Int, max: Int) {
        val mb = valueKb / 1024f
        val text = String.format(Locale.US, "%-9s:%6.1fMB", label, mb)
        
        textPaint.color = color
        canvas.drawText(text, x, y, textPaint)

        // 視覚化バー
        val barY = y + 8f
        val barHeight = 6f
        barPaint.color = Color.DKGRAY
        barPaint.alpha = 80
        canvas.drawRect(x, barY, x + width, barY + barHeight, barPaint)

        val progress = (valueKb.toFloat() / max).coerceIn(0f, 1f)
        barPaint.color = color
        barPaint.alpha = 160
        canvas.drawRect(x, barY, x + width * progress, barY + barHeight, barPaint)
    }
}
