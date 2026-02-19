package com.example.autorun.ui

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import com.example.autorun.config.GameSettings
import com.example.autorun.core.GameState

/**
 * 【HDULayoutEditor】
 * UI配置の編集（レイアウトモード）を担当するクラス。
 */
object HDULayoutEditor {
    private val paint = Paint().apply { isAntiAlias = true }
    
    val editorPanelRect = RectF()
    val editorHandleRect = RectF() 
    val layoutSaveRect = RectF()
    val layoutResetRect = RectF()
    val layoutExitRect = RectF()
    val sliderAlphaRect = RectF()
    val sliderScaleRect = RectF()

    fun updateRects(px: Float, py: Float) {
        val panelW = 600f; val panelH = 520f
        editorPanelRect.set(px, py, px + panelW, py + panelH)
        val handleSize = 80f; editorHandleRect.set(px + panelW - handleSize, py, px + panelW, py + handleSize)
        val contentX = px + 30f; var curY = py + 40f
        sliderAlphaRect.set(contentX, curY + 40f, contentX + panelW - 120f, curY + 80f)
        curY += 130f
        sliderScaleRect.set(contentX, curY + 40f, contentX + panelW - 120f, curY + 80f)
        curY += 160f
        val btnW = 170f; val btnH = 80f; val sp = 15f
        layoutSaveRect.set(contentX, curY, contentX + btnW, curY + btnH)
        layoutResetRect.set(contentX + btnW + sp, curY, contentX + btnW * 2 + sp, curY + btnH)
        layoutExitRect.set(contentX + btnW * 2 + sp * 2, curY, contentX + btnW * 3 + sp * 2, curY + btnH)
    }

    fun draw(canvas: Canvas, w: Float, h: Float, state: GameState, hdu: HDU) {
        paint.reset(); paint.isAntiAlias = true; paint.color = Color.BLACK; paint.alpha = 100; paint.style = Paint.Style.FILL
        canvas.drawRect(0f, 0f, w, h, paint)
        paint.style = Paint.Style.STROKE; paint.strokeWidth = 4f; paint.alpha = 200
        
        val uiList = listOf(
            Pair(hdu.statusRect, 0), Pair(hdu.mapRect, 1), Pair(hdu.settingsRect, 2), 
            Pair(hdu.brakeRect, 3), Pair(hdu.compassRect, 5), Pair(hdu.rpmRect, 6), 
            Pair(hdu.speedRect, 7), Pair(hdu.steerRect, 8), Pair(hdu.throttleRect, 9),
            Pair(hdu.boostRect, 10)
        )
        
        for (ui in uiList) { 
            paint.color = if (state.selectedUiId == ui.second) Color.GREEN else Color.YELLOW
            paint.strokeWidth = if (state.selectedUiId == ui.second) 8f else 4f
            canvas.drawRect(ui.first, paint)
        }
        
        paint.reset(); paint.isAntiAlias = true; paint.color = Color.BLACK; paint.alpha = 230; paint.style = Paint.Style.FILL
        canvas.drawRoundRect(editorPanelRect, 20f, 20f, paint)
        paint.color = Color.WHITE; paint.alpha = 80; paint.style = Paint.Style.STROKE; paint.strokeWidth = 3f
        canvas.drawRoundRect(editorPanelRect, 20f, 20f, paint)
        paint.style = Paint.Style.FILL; paint.color = Color.CYAN; paint.textSize = 24f; paint.typeface = Typeface.DEFAULT_BOLD
        canvas.drawText("UI EDITOR", editorPanelRect.left + 30f, editorPanelRect.top + 45f, paint)
        paint.color = Color.DKGRAY; paint.style = Paint.Style.FILL
        canvas.drawRoundRect(editorHandleRect, 15f, 15f, paint)
        paint.color = Color.WHITE; paint.style = Paint.Style.STROKE; paint.strokeWidth = 4f; canvas.drawRoundRect(editorHandleRect, 15f, 15f, paint)
        val cx = editorHandleRect.centerX(); val cy = editorHandleRect.centerY(); val s = 20f
        canvas.drawLine(cx - s, cy, cx + s, cy, paint); canvas.drawLine(cx, cy - s, cx, cy + s, paint)
        
        if (state.selectedUiId != -1) {
            val (cA, sP) = getUiValues(state.selectedUiId)
            drawSlider(canvas, sliderAlphaRect, "Opacity", cA)
            drawSlider(canvas, sliderScaleRect, "Scale", sP)
        } else {
            paint.color = Color.GRAY; paint.style = Paint.Style.FILL; paint.textSize = 30f; paint.typeface = Typeface.DEFAULT
            canvas.drawText("Tap a UI part to start editing", editorPanelRect.left + 50f, editorPanelRect.top + 200f, paint)
        }
        drawEditorButton(canvas, layoutSaveRect, "SAVE", Color.GREEN)
        drawEditorButton(canvas, layoutResetRect, "RESET", Color.LTGRAY)
        drawEditorButton(canvas, layoutExitRect, "EXIT", Color.RED)
    }

    private fun getUiValues(id: Int): Pair<Float, Float> {
        val (alpha, scale) = when (id) {
            0 -> GameSettings.UI_ALPHA_STATUS to GameSettings.UI_SCALE_STATUS
            1 -> GameSettings.UI_ALPHA_MAP to GameSettings.UI_SCALE_MAP
            2 -> GameSettings.UI_ALPHA_SETTINGS to GameSettings.UI_SCALE_SETTINGS
            3 -> GameSettings.UI_ALPHA_BRAKE to GameSettings.UI_SCALE_BRAKE
            5 -> GameSettings.UI_ALPHA_COMPASS to GameSettings.UI_SCALE_COMPASS
            6 -> GameSettings.UI_ALPHA_RPM to GameSettings.UI_SCALE_RPM
            7 -> GameSettings.UI_ALPHA_SPEED to GameSettings.UI_SCALE_SPEED
            8 -> GameSettings.UI_ALPHA_STEER to GameSettings.UI_SCALE_STEER
            9 -> GameSettings.UI_ALPHA_THROTTLE to GameSettings.UI_SCALE_THROTTLE
            10 -> GameSettings.UI_ALPHA_BOOST to GameSettings.UI_SCALE_BOOST
            else -> 1.0f to 1.0f
        }
        return Pair(alpha, (scale - 0.5f) / 2.0f)
    }

    private fun drawSlider(canvas: Canvas, rect: RectF, label: String, progress: Float) {
        paint.reset(); paint.isAntiAlias = true; paint.color = Color.GRAY; paint.alpha = 100; paint.style = Paint.Style.FILL
        val trH = 12f; canvas.drawRoundRect(rect.left, rect.centerY() - trH/2, rect.right, rect.centerY() + trH/2, 6f, 6f, paint)
        paint.color = Color.CYAN; paint.alpha = 200; canvas.drawRoundRect(rect.left, rect.centerY() - trH/2, rect.left + rect.width() * progress.coerceIn(0f, 1f), rect.centerY() + trH/2, 6f, 6f, paint)
        val tx = rect.left + rect.width() * progress.coerceIn(0f, 1f)
        paint.color = Color.WHITE; paint.alpha = 255; paint.style = Paint.Style.FILL; canvas.drawCircle(tx, rect.centerY(), 32f, paint)
        paint.color = Color.BLACK; paint.style = Paint.Style.STROKE; paint.strokeWidth = 3f; canvas.drawCircle(tx, rect.centerY(), 32f, paint)
        paint.style = Paint.Style.FILL; paint.color = Color.WHITE; paint.textSize = 24f; canvas.drawText(label, rect.left, rect.top + 5f, paint)
        val pct = "${(progress * 100).toInt()}%"; canvas.drawText(pct, rect.right - paint.measureText(pct), rect.top + 5f, paint)
    }
    
    private fun drawEditorButton(canvas: Canvas, rect: RectF, text: String, color: Int) {
        paint.reset(); paint.isAntiAlias = true; paint.color = Color.BLACK; paint.alpha = 200; paint.style = Paint.Style.FILL
        canvas.drawRoundRect(rect, 15f, 15f, paint)
        paint.color = color; paint.style = Paint.Style.STROKE; paint.strokeWidth = 4f; canvas.drawRoundRect(rect, 15f, 15f, paint)
        paint.style = Paint.Style.FILL; paint.textSize = 26f; paint.typeface = Typeface.DEFAULT_BOLD
        val tw = paint.measureText(text); canvas.drawText(text, rect.centerX() - tw / 2f, rect.centerY() + 10f, paint)
    }
}