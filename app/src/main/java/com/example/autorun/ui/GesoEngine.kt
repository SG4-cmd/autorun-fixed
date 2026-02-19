package com.example.autorun.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.RectF
import android.graphics.Typeface
import com.example.autorun.audio.EngineSoundRenderer
import com.example.autorun.audio.MusicPlayer
import com.example.autorun.config.DeveloperSettings
import com.example.autorun.core.GameState

/**
 * 【GesoEngine: グラフィック描画の中枢】
 * 3Dへの移行に伴い、UI描画のみを担当するように役割が変更されました。
 */
object GesoEngine {

    private val developerModeTouchArea = RectF(20f, 150f, 20f + 420f, 150f + 220f)
    private val guardrailToggleArea = RectF()
    private val postToggleArea = RectF()
    private val miniMapTouchArea = RectF(20f, 150f, 20f + 420f, 150f + 400f)
    private val settingsTouchArea = RectF()

    init {
        guardrailToggleArea.set(developerModeTouchArea.left, developerModeTouchArea.top, developerModeTouchArea.right, developerModeTouchArea.centerY())
        postToggleArea.set(developerModeTouchArea.left, developerModeTouchArea.centerY(), developerModeTouchArea.right, developerModeTouchArea.bottom)
    }

    fun drawAll(canvas: Canvas, w: Float, h: Float, state: GameState, currentFps: Int,
                font_seven_segment: Typeface?, font_warrior: Typeface?, font_japanese: Typeface?,
                engineSound: EngineSoundRenderer?, musicPlayer: MusicPlayer?, context: Context
    ) {
        // 3Dエンジンが背景と道路を描画するため、ここでの描画処理はUI(HDU)のみ
        HDU.draw(canvas, w, h, state, currentFps, font_seven_segment, font_warrior, font_japanese, engineSound, musicPlayer, context)
    }

    fun handleTouch(x: Float, y: Float, state: GameState, isTapEvent: Boolean, context: Context): Boolean {
        if (state.isSettingsOpen) {
            if (isTapEvent) {
                if (HDU.handleSettingsTouch(x, y, state)) return true
                state.isSettingsOpen = false; return true
            }
            return true
        }
        if (settingsTouchArea.contains(x, y)) { if (isTapEvent) state.isSettingsOpen = !state.isSettingsOpen; return true }
        if (miniMapTouchArea.contains(x, y)) { if (isTapEvent) state.isMapLongRange = !state.isMapLongRange; return true }
        if (!state.isDeveloperMode) return false
        if (isTapEvent) {
            if (guardrailToggleArea.contains(x, y)) { DeveloperSettings.showRightGuardrail = !DeveloperSettings.showRightGuardrail; return true }
            else if (postToggleArea.contains(x, y)) { DeveloperSettings.showGuardrailPosts = !DeveloperSettings.showGuardrailPosts; return true }
        } else return guardrailToggleArea.contains(x, y) || postToggleArea.contains(x, y)
        return false
    }
}
