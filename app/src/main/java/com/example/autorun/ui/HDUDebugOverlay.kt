package com.example.autorun.ui

import android.graphics.*
import android.os.Debug
import com.example.autorun.audio.EngineSoundRenderer
import com.example.autorun.config.DeveloperSettings
import com.example.autorun.config.GameSettings
import com.example.autorun.core.CourseManager
import com.example.autorun.core.GameState
import java.util.Locale
import kotlin.math.*

/**
 * 【HDUDebugOverlay: システムモニタ】
 * FPS、メモリ、エンジンスペックなどをリアルタイム表示。
 */
object HDUDebugOverlay {
    private val paint = Paint().apply { isAntiAlias = true }
    private val wavePath = Path()

    enum class Category(val label: String) {
        OVERVIEW("概要"),
        PHYSICS("物理演算"),
        SOUND("音要素"),
        EQ_SETTINGS("EQ/Filter"),
        MEMORY("メモリ")
    }

    var currentCategory = Category.OVERVIEW

    private const val SIDEBAR_WIDTH = 220f
    private const val DRAG_HANDLE_HEIGHT = 50f
    private const val RESIZE_HANDLE_SIZE = 60f

    val panelRect = RectF()
    val dragHandleRect = RectF()
    val resizeHandleRect = RectF()
    private val categoryRects = mutableMapOf<Category, RectF>()
    private val eqSliderRects = mutableMapOf<Int, RectF>()
    
    private val engineToggleRect = RectF()
    private val exhaustToggleRect = RectF()
    private val turboToggleRect = RectF()
    private val windToggleRect = RectF()
    private val brakeToggleRect = RectF()
    private val tireToggleRect = RectF()
    
    private val jitterToggleRect = RectF()
    private val ghostToggleRect = RectF()

    private val memInfo = Debug.MemoryInfo()
    private val memoryHistory = FloatArray(120)
    private var historyIdx = 0

    fun draw(canvas: Canvas, state: GameState, fps: Int, engineSound: EngineSoundRenderer?) {
        val posX = GameSettings.UI_POS_DEBUG_PANEL.x
        val posY = GameSettings.UI_POS_DEBUG_PANEL.y
        val pW = GameSettings.UI_WIDTH_DEBUG_PANEL
        val pH = GameSettings.UI_HEIGHT_DEBUG_PANEL

        panelRect.set(posX, posY, posX + pW, posY + pH)
        dragHandleRect.set(posX, posY, posX + pW, posY + DRAG_HANDLE_HEIGHT)
        resizeHandleRect.set(panelRect.right - RESIZE_HANDLE_SIZE, panelRect.bottom - RESIZE_HANDLE_SIZE, panelRect.right, panelRect.bottom)

        paint.reset(); paint.isAntiAlias = true
        paint.color = Color.BLACK; paint.alpha = 220; paint.style = Paint.Style.FILL
        canvas.drawRoundRect(panelRect, 12f, 12f, paint)
        paint.color = Color.CYAN; paint.style = Paint.Style.STROKE; paint.strokeWidth = 3f; paint.alpha = 255
        canvas.drawRoundRect(panelRect, 12f, 12f, paint)

        paint.style = Paint.Style.FILL; paint.color = Color.DKGRAY; paint.alpha = 150
        canvas.drawRoundRect(dragHandleRect, 12f, 12f, paint)
        paint.color = Color.YELLOW; paint.textSize = 22f; paint.typeface = Typeface.DEFAULT_BOLD
        canvas.drawText("SYSTEM MONITOR", dragHandleRect.left + 25f, dragHandleRect.centerY() + 10f, paint)

        val sidebarRect = RectF(posX, posY + DRAG_HANDLE_HEIGHT, posX + SIDEBAR_WIDTH, posY + pH)
        paint.color = Color.BLACK; paint.alpha = 80; canvas.drawRect(sidebarRect, paint)

        var categoryY = sidebarRect.top + 10f
        val categoryHeight = (sidebarRect.height() - 20f) / Category.entries.size - 10f
        Category.entries.forEach { cat ->
            val catRect = RectF(sidebarRect.left + 8f, categoryY, sidebarRect.right - 8f, categoryY + categoryHeight)
            categoryRects[cat] = catRect
            if (currentCategory == cat) {
                paint.color = Color.CYAN; paint.alpha = 130; canvas.drawRoundRect(catRect, 8f, 8f, paint)
            }
            paint.color = Color.WHITE; paint.textSize = 24f; paint.typeface = Typeface.DEFAULT
            canvas.drawText(cat.label, catRect.left + 15f, catRect.centerY() + 10f, paint)
            categoryY += categoryHeight + 8f
        }

        val contentRect = RectF(posX + SIDEBAR_WIDTH + 20f, posY + DRAG_HANDLE_HEIGHT + 20f, posX + pW - 20f, posY + pH - 20f)
        drawContent(canvas, contentRect, state, fps, engineSound)

        paint.reset(); paint.color = Color.CYAN; paint.alpha = 180; paint.style = Paint.Style.FILL
        val p = Path()
        p.moveTo(resizeHandleRect.right, resizeHandleRect.top)
        p.lineTo(resizeHandleRect.right, resizeHandleRect.bottom)
        p.lineTo(resizeHandleRect.left, resizeHandleRect.bottom)
        p.close()
        canvas.drawPath(p, paint)
    }

    private fun drawContent(canvas: Canvas, rect: RectF, state: GameState, fps: Int, engineSound: EngineSoundRenderer?) {
        paint.reset(); paint.isAntiAlias = true; paint.textSize = 22f; paint.typeface = Typeface.MONOSPACE; paint.color = Color.WHITE
        when (currentCategory) {
            Category.OVERVIEW -> drawOverview(canvas, rect, state, fps)
            Category.PHYSICS -> drawPhysics(canvas, rect, state)
            Category.SOUND -> drawSoundToggles(canvas, rect)
            Category.EQ_SETTINGS -> drawEqSettings(canvas, rect, engineSound)
            Category.MEMORY -> drawMemoryInfo(canvas, rect)
        }
    }

    private fun drawOverview(canvas: Canvas, rect: RectF, state: GameState, fps: Int) {
        var y = rect.top; val lH = 45f
        paint.textSize = 22f
        drawValueBar(canvas, "UI FPS", state.currentFps.toFloat(), 120f, rect.left, y, rect.width(), Color.GREEN); y += lH
        drawValueBar(canvas, "ENG FPS", state.engineFps.toFloat(), 120f, rect.left, y, rect.width(), Color.parseColor("#44FF88")); y += lH
        drawValueBar(canvas, "SPD KMH", state.calculatedSpeedKmH, 320f, rect.left, y, rect.width(), Color.CYAN); y += lH
        drawValueBar(canvas, "ENG RPM", state.engineRPM, 9000f, rect.left, y, rect.width(), Color.YELLOW); y += lH
        drawValueBar(canvas, "THROTTLE", state.throttle * 100f, 100f, rect.left, y, rect.width(), Color.MAGENTA); y += lH
        drawValueBar(canvas, "BOOST  ", (state.turboBoost + 1.0f) * 50f, 100f, rect.left, y, rect.width(), Color.RED); y += lH
        
        val distKm = state.playerDistance / 1000f
        val totalKm = CourseManager.getTotalDistance() / 1000f
        drawValueBar(canvas, "DIST KM", distKm, totalKm, rect.left, y, rect.width(), Color.WHITE); y += lH
        
        paint.color = Color.WHITE
        canvas.drawText("GEAR: ${state.currentGear}", rect.left, y + 25f, paint)
        canvas.drawText("LAT : ${String.format(Locale.US, "%.2f", state.lateralVelocity)}", rect.left + 200f, y + 25f, paint)
    }

    private fun drawPhysics(canvas: Canvas, rect: RectF, state: GameState) {
        var y = rect.top; val lH = 60f
        paint.textSize = 22f
        drawValueBar(canvas, "THR ", state.throttle * 100f, 100f, rect.left, y, rect.width(), Color.MAGENTA); y += lH
        drawValueBar(canvas, "TRQ ", state.currentTorqueNm, 600f, rect.left, y, rect.width(), Color.parseColor("#FF8800")); y += lH
        drawValueBar(canvas, "LAT ", abs(state.lateralVelocity), 5f, rect.left, y, rect.width(), Color.CYAN); y += lH
        val alt = CourseManager.getHeight(state.playerDistance / GameSettings.SEGMENT_LENGTH)
        drawValueBar(canvas, "ALT ", alt, 500f, rect.left, y, rect.width(), Color.LTGRAY)
    }

    private fun drawValueBar(canvas: Canvas, label: String, value: Float, maxVal: Float, x: Float, y: Float, width: Float, color: Int) {
        paint.color = Color.WHITE
        canvas.drawText(String.format(Locale.US, "%-8s: %6.1f", label, value), x, y + 25f, paint)
        
        val barX = x + 240f
        val barW = (width - 260f).coerceAtLeast(10f)
        val barH = 30f
        val progress = if (maxVal > 0) (value / maxVal).coerceIn(0f, 1f) else 0f
        
        paint.color = Color.DKGRAY; paint.alpha = 100; paint.style = Paint.Style.FILL
        canvas.drawRect(barX, y, barX + barW, y + barH, paint)
        paint.color = Color.GRAY; paint.alpha = 255; paint.style = Paint.Style.STROKE; paint.strokeWidth = 1f
        canvas.drawRect(barX, y, barX + barW, y + barH, paint)
        paint.color = color; paint.alpha = 200; paint.style = Paint.Style.FILL
        canvas.drawRect(barX, y, barX + barW * progress, y + barH, paint)
    }

    private fun drawSoundToggles(canvas: Canvas, rect: RectF) {
        var y = rect.top; val lH = 50f
        paint.textSize = 24f; paint.typeface = Typeface.DEFAULT_BOLD
        paint.color = Color.WHITE; canvas.drawText("ENGINE SOUND", rect.left, y + 35f, paint)
        engineToggleRect.set(rect.left + 300f, y, rect.left + 450f, y + 45f)
        drawToggleButton(canvas, engineToggleRect, DeveloperSettings.isEngineSoundEnabled); y += lH
        paint.textSize = 20f; paint.typeface = Typeface.DEFAULT
        paint.color = Color.LTGRAY; canvas.drawText("  - PITCH JITTER", rect.left, y + 30f, paint)
        jitterToggleRect.set(rect.left + 300f, y, rect.left + 450f, y + 35f)
        drawToggleButton(canvas, jitterToggleRect, DeveloperSettings.isEngineJitterEnabled); y += 40f
        paint.color = Color.LTGRAY; canvas.drawText("  - GHOST DELAY", rect.left, y + 30f, paint)
        ghostToggleRect.set(rect.left + 300f, y, rect.left + 450f, y + 35f)
        drawToggleButton(canvas, ghostToggleRect, DeveloperSettings.isEngineGhostEnabled); y += lH + 5f
        paint.textSize = 24f; paint.typeface = Typeface.DEFAULT_BOLD
        paint.color = Color.WHITE; canvas.drawText("EXHAUST SOUND", rect.left, y + 35f, paint)
        exhaustToggleRect.set(rect.left + 300f, y, rect.left + 450f, y + 45f)
        drawToggleButton(canvas, exhaustToggleRect, DeveloperSettings.isExhaustSoundEnabled); y += lH
        canvas.drawText("TURBO SOUND", rect.left, y + 35f, paint)
        turboToggleRect.set(rect.left + 300f, y, rect.left + 450f, y + 45f)
        drawToggleButton(canvas, turboToggleRect, DeveloperSettings.isTurboSoundEnabled); y += lH
        canvas.drawText("WIND SOUND", rect.left, y + 35f, paint)
        windToggleRect.set(rect.left + 300f, y, rect.left + 450f, y + 45f)
        drawToggleButton(canvas, windToggleRect, DeveloperSettings.isWindSoundEnabled); y += lH
        canvas.drawText("BRAKE SOUND", rect.left, y + 35f, paint)
        brakeToggleRect.set(rect.left + 300f, y, rect.left + 450f, y + 45f)
        drawToggleButton(canvas, brakeToggleRect, DeveloperSettings.isBrakeSoundEnabled); y += lH
        canvas.drawText("TIRE SQUEAL", rect.left, y + 35f, paint)
        tireToggleRect.set(rect.left + 300f, y, rect.left + 450f, y + 45f)
        drawToggleButton(canvas, tireToggleRect, DeveloperSettings.isTireSquealEnabled)
    }

    private fun drawToggleButton(canvas: Canvas, rect: RectF, enabled: Boolean) {
        paint.style = if (enabled) Paint.Style.FILL else Paint.Style.STROKE
        paint.strokeWidth = 2f; paint.color = if (enabled) Color.GREEN else Color.GRAY
        canvas.drawRoundRect(rect, 10f, 10f, paint)
        paint.style = Paint.Style.FILL; paint.color = Color.WHITE; paint.textSize = 18f
        val text = if (enabled) "ON" else "OFF"
        canvas.drawText(text, rect.centerX() - paint.measureText(text)/2f, rect.centerY() + 7f, paint)
    }
    
    private fun drawMemoryInfo(canvas: Canvas, rect: RectF) {
        Debug.getMemoryInfo(memInfo)
        val total = memInfo.totalPss / 1024f
        val javaHeap = try { memInfo.getMemoryStat("summary.java-heap").toInt() / 1024f } catch (e: Exception) { 0f }
        val graphics = try { memInfo.getMemoryStat("summary.graphics").toInt() / 1024f } catch (e: Exception) { 0f }
        val code = try { memInfo.getMemoryStat("summary.code").toInt() / 1024f } catch (e: Exception) { 0f }
        val others = try { memInfo.getMemoryStat("summary.private-other").toInt() / 1024f } catch (e: Exception) { 0f }
        memoryHistory[historyIdx] = total; historyIdx = (historyIdx + 1) % memoryHistory.size
        var y = rect.top; val lH = 35f
        paint.color = Color.WHITE; paint.textSize = 22f; paint.typeface = Typeface.MONOSPACE
        canvas.drawText(String.format(Locale.US, "TOTAL     : %7.2f MB", total), rect.left, y, paint); y += lH
        canvas.drawText(String.format(Locale.US, "JAVA HEAP : %7.2f MB", javaHeap), rect.left, y, paint); y += lH
        canvas.drawText(String.format(Locale.US, "GRAPHICS  : %7.2f MB", graphics), rect.left, y, paint); y += lH
        canvas.drawText(String.format(Locale.US, "CODE      : %7.2f MB", code), rect.left, y, paint); y += lH
        canvas.drawText(String.format(Locale.US, "OTHERS    : %7.2f MB", others), rect.left, y, paint); y += lH
        y += 20f; val graphRect = RectF(rect.left, y, rect.right, rect.bottom - 20f)
        drawMemoryGraph(canvas, graphRect)
    }

    private fun drawMemoryGraph(canvas: Canvas, gRect: RectF) {
        if (gRect.height() <= 0) return
        paint.style = Paint.Style.FILL; paint.color = Color.DKGRAY; paint.alpha = 100; canvas.drawRect(gRect, paint)
        paint.style = Paint.Style.STROKE; paint.color = Color.GRAY; paint.strokeWidth = 1f; paint.alpha = 255; canvas.drawRect(gRect, paint)
        val maxVal = (memoryHistory.maxOrNull() ?: 100f).coerceAtLeast(10f) * 1.2f; val stepX = gRect.width() / (memoryHistory.size - 1)
        wavePath.reset()
        for (i in memoryHistory.indices) { val idx = (historyIdx + i) % memoryHistory.size; val vx = gRect.left + i * stepX; val vy = gRect.bottom - (memoryHistory[idx] / maxVal) * gRect.height(); if (i == 0) wavePath.moveTo(vx, vy) else wavePath.lineTo(vx, vy) }
        paint.color = Color.YELLOW; paint.strokeWidth = 2f; canvas.drawPath(wavePath, paint)
        paint.style = Paint.Style.FILL; paint.textSize = 18f; paint.color = Color.YELLOW; canvas.drawText(String.format(Locale.US, "MAX: %.1f MB", maxVal / 1.2f), gRect.left + 10f, gRect.top + 25f, paint)
    }

    private fun drawEqSettings(canvas: Canvas, rect: RectF, engineSound: EngineSoundRenderer?) {
        if (engineSound == null) return
        var currentY = rect.top; paint.color = Color.WHITE; paint.textSize = 20f; paint.typeface = Typeface.DEFAULT_BOLD
        canvas.drawText("10-BAND GRAPHIC EQ", rect.left, currentY, paint); currentY += 40f
        val numBands = 10; val sW = (rect.width() - 80f) / numBands; val sH = (rect.bottom - currentY - 40f).coerceAtLeast(100f)
        paint.textAlign = Paint.Align.RIGHT; paint.textSize = 14f; paint.strokeWidth = 1f
        for (db in listOf(0, -20, -40, -60, -80)) { val ratio = (db + 80f) / 80f; val gy = currentY + sH * (1f - ratio); paint.color = Color.WHITE; paint.alpha = 50; canvas.drawLine(rect.left + 75f, gy, rect.right, gy, paint); paint.alpha = 180; canvas.drawText("${db}dB", rect.left + 70f, gy + 5f, paint) }
        paint.textAlign = Paint.Align.LEFT
        for(i in 0 until numBands) { val sX = rect.left + 100f + i * sW + sW / 2f; val sRect = RectF(sX - 25f, currentY, sX + 25f, currentY + sH); eqSliderRects[i] = sRect; drawEqSlider(canvas, engineSound.eqFreqs[i], engineSound.eqGains[i], sRect) }
    }

    private fun drawEqSlider(canvas: Canvas, freq: Float, value: Float, rect: RectF) {
        paint.color = Color.WHITE; paint.textSize = 14f; val label = if (freq < 1000) "%.0f".format(freq) else "%.0fk".format(freq / 1000f); canvas.drawText(label, rect.centerX() - paint.measureText(label) / 2f, rect.top - 10f, paint)
        paint.color = Color.DKGRAY; paint.alpha = 100; paint.style = Paint.Style.FILL; canvas.drawRoundRect(rect, 8f, 8f, paint)
        val progress = (value + 80f) / 80f; val thumbY = rect.bottom - rect.height() * progress; val thumbRect = RectF(rect.left + 3f, thumbY - 10f, rect.right - 3f, thumbY + 10f); paint.color = Color.CYAN; paint.alpha = 255; canvas.drawRoundRect(thumbRect, 6f, 6f, paint)
        paint.color = Color.WHITE; paint.textSize = 12f; val valText = "%.1f".format(value); canvas.drawText(valText, rect.centerX() - paint.measureText(valText) / 2f, rect.bottom + 20f, paint)
    }

    fun handleTouch(x: Float, y: Float, engineSound: EngineSoundRenderer?): Boolean {
        categoryRects.forEach { (cat, rect) -> if (rect.contains(x, y)) { currentCategory = cat; return true } }
        when (currentCategory) {
            Category.SOUND -> {
                if (engineToggleRect.contains(x, y)) { DeveloperSettings.isEngineSoundEnabled = !DeveloperSettings.isEngineSoundEnabled; return true }
                if (exhaustToggleRect.contains(x, y)) { DeveloperSettings.isExhaustSoundEnabled = !DeveloperSettings.isExhaustSoundEnabled; return true }
                if (turboToggleRect.contains(x, y)) { DeveloperSettings.isTurboSoundEnabled = !DeveloperSettings.isTurboSoundEnabled; return true }
                if (windToggleRect.contains(x, y)) { DeveloperSettings.isWindSoundEnabled = !DeveloperSettings.isWindSoundEnabled; return true }
                if (brakeToggleRect.contains(x, y)) { DeveloperSettings.isBrakeSoundEnabled = !DeveloperSettings.isBrakeSoundEnabled; return true }
                if (tireToggleRect.contains(x, y)) { DeveloperSettings.isTireSquealEnabled = !DeveloperSettings.isTireSquealEnabled; return true }
                if (jitterToggleRect.contains(x, y)) { DeveloperSettings.isEngineJitterEnabled = !DeveloperSettings.isEngineJitterEnabled; return true }
                if (ghostToggleRect.contains(x, y)) { DeveloperSettings.isEngineGhostEnabled = !DeveloperSettings.isEngineGhostEnabled; return true }
            }
            Category.EQ_SETTINGS -> {
                if (engineSound == null) return false
                eqSliderRects.forEach { (idx, rect) -> if (rect.contains(x, y)) { val prg = 1f - ((y - rect.top) / rect.height()).coerceIn(0f, 1f); engineSound.eqGains[idx] = (-80f + prg * 80f).coerceIn(-80f, 0f); engineSound.updateFilters(); return true } }
            }
            else -> {}
        }
        return false
    }
}
