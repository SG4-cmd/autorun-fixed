package com.example.autorun.ui

import android.content.Context
import android.graphics.*
import com.example.autorun.R
import com.example.autorun.config.DeveloperSettings
import com.example.autorun.config.GameSettings
import com.example.autorun.core.GameState
import kotlin.math.*

/**
 * 【HDUSettings】
 * 設定メニューの表示と操作を担当するクラス。
 */
object HDUSettings {
    private val paint = Paint().apply { isAntiAlias = true }
    
    enum class Category(val label: String) {
        GRAPHICS("グラフィック"),
        CONTROLS("操作設定")
        // SOUND カテゴリーは GameSettings で 100% 固定としたため削除
    }

    private var currentCategory = Category.GRAPHICS
    
    private const val SIDEBAR_WIDTH = 220f
    private const val HEADER_HEIGHT = 60f
    
    private val panelRect = RectF()
    private val categoryRects = mutableMapOf<Category, RectF>()
    private val settingItemRects = mutableListOf<Triple<SettingItem, RectF, RectF>>() // Item, Minus, Plus

    private val graphicSettings = mutableListOf<SettingItem>()
    private val controlSettings = mutableListOf<SettingItem>()

    class SettingItem(val label: String, val getValue: () -> Float, val setValue: (Float) -> Unit, val step: Float, val min: Float, val max: Float)

    fun init(context: Context) {
        setupSettingItems()
    }

    private fun setupSettingItems() {
        if (graphicSettings.isNotEmpty()) return
        
        // GRAPHICS
        graphicSettings.add(SettingItem("解像度", { GameSettings.RESOLUTION_INDEX.toFloat() }, { GameSettings.RESOLUTION_INDEX = it.toInt().coerceIn(0, GameSettings.RESOLUTIONS.size - 1) }, 1f, 0f, (GameSettings.RESOLUTIONS.size - 1).toFloat()))
        graphicSettings.add(SettingItem("表示距離", { GameSettings.DRAW_DISTANCE.toFloat() }, { GameSettings.DRAW_DISTANCE = it.toInt().coerceIn(500, 5000) }, 250f, 500f, 5000f))
        graphicSettings.add(SettingItem("陰影", { if (GameSettings.SHOW_SHADOWS) 1f else 0f }, { GameSettings.SHOW_SHADOWS = it > 0.5f }, 1f, 0f, 1f))
        graphicSettings.add(SettingItem("画角 (FOV)", { GameSettings.FOV }, { GameSettings.FOV = it.coerceIn(200f, 1500f) }, 50f, 200f, 1500f))
        
        // CONTROLS
        controlSettings.add(SettingItem("配置設定 (Layout)", { 0f }, { }, 0f, 0f, 0f))
    }

    fun drawSettingsButton(canvas: Canvas, rect: RectF) {
        val alpha = GameSettings.UI_ALPHA_SETTINGS
        paint.reset(); paint.isAntiAlias = true; paint.color = Color.BLACK; paint.alpha = (180 * alpha).toInt(); paint.style = Paint.Style.FILL; canvas.drawRoundRect(rect, 15f, 15f, paint)
        paint.color = Color.WHITE; paint.alpha = (255 * alpha).toInt(); paint.style = Paint.Style.STROKE; paint.strokeWidth = 4f
        val cx = rect.centerX(); val cy = rect.centerY(); val r = rect.width() * 0.28f; canvas.drawCircle(cx, cy, r, paint)
        for (i in 0 until 8) {
            val a = i * 45f; val rd = Math.toRadians(a.toDouble()); val x1 = cx + (r * 0.8f * cos(rd)).toFloat(); val y1 = cy + (r * 0.8f * sin(rd)).toFloat(); val x2 = cx + (r * 1.5f * cos(rd)).toFloat(); val y2 = cy + (r * 1.5f * sin(rd)).toFloat()
            paint.strokeWidth = 10f; canvas.drawLine(x1, y1, x2, y2, paint)
        }
        paint.strokeWidth = 4f; canvas.drawCircle(cx, cy, r * 0.45f, paint)
    }

    fun drawSettingsMenu(canvas: Canvas, w: Float, h: Float, state: GameState, font: Typeface?) {
        val menuW = 900f; val menuH = 600f
        panelRect.set((w - menuW) / 2f, (h - menuH) / 2f, (w + menuW) / 2f, (h + menuH) / 2f)

        // 1. ウィンドウ背景
        paint.reset(); paint.isAntiAlias = true
        paint.color = Color.BLACK; paint.alpha = 230; paint.style = Paint.Style.FILL
        canvas.drawRoundRect(panelRect, 15f, 15f, paint)
        paint.color = Color.CYAN; paint.style = Paint.Style.STROKE; paint.strokeWidth = 3f; paint.alpha = 255
        canvas.drawRoundRect(panelRect, 15f, 15f, paint)

        // 2. ヘッダー
        val headerRect = RectF(panelRect.left, panelRect.top, panelRect.right, panelRect.top + HEADER_HEIGHT)
        paint.style = Paint.Style.FILL; paint.color = Color.DKGRAY; paint.alpha = 150
        canvas.drawRoundRect(headerRect, 15f, 15f, paint)
        paint.color = Color.YELLOW; paint.textSize = 24f; paint.typeface = Typeface.DEFAULT_BOLD
        canvas.drawText("GAME SETTINGS", headerRect.left + 25f, headerRect.centerY() + 10f, paint)

        // 3. サイドバー
        val sidebarRect = RectF(panelRect.left, panelRect.top + HEADER_HEIGHT, panelRect.left + SIDEBAR_WIDTH, panelRect.bottom)
        paint.color = Color.BLACK; paint.alpha = 100; canvas.drawRect(sidebarRect, paint)

        var categoryY = sidebarRect.top + 10f
        val categoryHeight = 80f
        Category.entries.forEach { cat ->
            val catRect = RectF(sidebarRect.left + 10f, categoryY, sidebarRect.right - 10f, categoryY + categoryHeight)
            categoryRects[cat] = catRect
            if (currentCategory == cat) {
                paint.color = Color.CYAN; paint.alpha = 130; canvas.drawRoundRect(catRect, 10f, 10f, paint)
            }
            paint.color = Color.WHITE; paint.textSize = 24f; paint.typeface = Typeface.DEFAULT
            canvas.drawText(cat.label, catRect.left + 20f, catRect.centerY() + 10f, paint)
            categoryY += categoryHeight + 10f
        }

        // 4. メインコンテンツ
        val contentRect = RectF(panelRect.left + SIDEBAR_WIDTH + 20f, panelRect.top + HEADER_HEIGHT + 20f, panelRect.right - 20f, panelRect.bottom - 20f)
        drawContent(canvas, contentRect, state, font)
        
        paint.textSize = 18f; paint.color = Color.LTGRAY; paint.typeface = Typeface.DEFAULT
        val closeText = "Tap outside to close"
        canvas.drawText(closeText, panelRect.centerX() - paint.measureText(closeText) / 2f + SIDEBAR_WIDTH/2f, panelRect.bottom - 15f, paint)
    }

    private fun drawContent(canvas: Canvas, rect: RectF, state: GameState, font: Typeface?) {
        settingItemRects.clear()
        val items = when (currentCategory) {
            Category.GRAPHICS -> graphicSettings
            Category.CONTROLS -> controlSettings
        }
        var y = rect.top
        val itemHeight = 80f
        
        paint.textSize = 26f; paint.typeface = Typeface.DEFAULT_BOLD; paint.color = Color.WHITE
        
        for (item in items) {
            paint.color = if (item.label.contains("Layout") && state.isLayoutMode) Color.YELLOW else Color.WHITE
            canvas.drawText(item.label, rect.left, y + 35f, paint)
            
            val valStr = when {
                item.label.contains("解像度") -> "${GameSettings.RESOLUTIONS[GameSettings.RESOLUTION_INDEX]}p"
                item.label.contains("Layout") -> if (state.isLayoutMode) "EDITING" else "START"
                item.label.contains("陰影") -> if (item.getValue() > 0.5f) "ON" else "OFF"
                else -> "%.1f".format(item.getValue())
            }
            canvas.drawText(valStr, rect.left + 300f, y + 35f, paint)

            val minusRect = RectF()
            val plusRect = RectF()
            
            if (!item.label.contains("Layout")) {
                minusRect.set(rect.right - 180f, y, rect.right - 100f, y + 50f)
                plusRect.set(rect.right - 90f, y, rect.right - 10f, y + 50f)
                
                paint.style = Paint.Style.FILL; paint.color = Color.DKGRAY; canvas.drawRoundRect(minusRect, 8f, 8f, paint)
                paint.color = Color.WHITE; paint.textSize = 30f; canvas.drawText("-", minusRect.centerX() - 8f, minusRect.centerY() + 10f, paint)
                
                paint.color = Color.DKGRAY; canvas.drawRoundRect(plusRect, 8f, 8f, paint)
                paint.color = Color.WHITE; canvas.drawText("+", plusRect.centerX() - 12f, plusRect.centerY() + 10f, paint)
            } else {
                plusRect.set(rect.right - 220f, y, rect.right - 10f, y + 50f)
                paint.style = Paint.Style.FILL; paint.color = Color.DKGRAY; canvas.drawRoundRect(plusRect, 8f, 8f, paint)
                paint.color = Color.WHITE; paint.textSize = 22f
                val btnText = if (state.isLayoutMode) "FINISH" else "MOVE UI"
                canvas.drawText(btnText, plusRect.centerX() - paint.measureText(btnText)/2f, plusRect.centerY() + 8f, paint)
            }
            
            settingItemRects.add(Triple(item, minusRect, plusRect))
            y += itemHeight
        }
    }

    fun handleSettingsTouch(x: Float, y: Float, state: GameState): Boolean {
        if (!state.isSettingsOpen) return false
        
        // サイドバーのタッチ判定
        categoryRects.forEach { (cat, rect) ->
            if (rect.contains(x, y)) {
                currentCategory = cat
                return true
            }
        }

        // 項目操作のタッチ判定
        for (triple in settingItemRects) {
            val item = triple.first
            val minus = triple.second
            val plus = triple.third
            
            if (minus.contains(x, y) || plus.contains(x, y)) {
                if (item.label.contains("Layout")) {
                    state.isLayoutMode = !state.isLayoutMode
                    if (state.isLayoutMode) {
                        state.isSettingsOpen = false
                        state.selectedUiId = -1
                    }
                    return true
                }
                if (minus.contains(x, y)) {
                    item.setValue((item.getValue() - item.step).coerceIn(item.min, item.max))
                } else {
                    item.setValue((item.getValue() + item.step).coerceIn(item.min, item.max))
                }
                return true
            }
        }

        return panelRect.contains(x, y)
    }
}
