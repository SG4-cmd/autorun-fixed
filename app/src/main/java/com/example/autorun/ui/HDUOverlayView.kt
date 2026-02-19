package com.example.autorun.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.PorterDuff
import android.util.AttributeSet
import android.view.View
import androidx.core.content.res.ResourcesCompat
import com.example.autorun.R
import com.example.autorun.core.GameState

/**
 * 【HDUOverlayView】
 * 3Dエンジンの上にメーター類（2D UI）を重ねて表示します。
 */
class HDUOverlayView(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    private var gameState: GameState? = null
    private val font7Bar by lazy { try { ResourcesCompat.getFont(context, R.font.gotikakutto_005_851) } catch (e: Exception) { null } }
    private val fontWarrior by lazy { try { ResourcesCompat.getFont(context, R.font.gotikakutto_005_851) } catch (e: Exception) { null } }

    fun setGameState(state: GameState) {
        this.gameState = state
    }

    override fun onDraw(canvas: Canvas) {
        val state = gameState ?: return
        val w = width.toFloat()
        val h = height.toFloat()

        // HDU（メーター類）の描画。既存のHDU.drawをそのまま利用します。
        HDU.draw(canvas, w, h, state, 0, font7Bar, fontWarrior, fontWarrior, null, null, context)
        
        // 常に再描画
        invalidate()
    }
}
