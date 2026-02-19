package com.example.autorun.utils

import java.util.Locale

/**
 * 【GameUtils: ゲーム共通の便利な道具箱】
 * 時間のフォーマットや速度調整などの計算のみを担当しています。
 */
object GameUtils {

    // 【1. 奥スクロール速度の調整関数】
    // ---------------------------------------------------------
    fun getAdjustedScrollSpeed(realSpeedMs: Float): Float {
        val multiplier = 0.9f
        return realSpeedMs * multiplier
    }

    // 【2. 時間のフォーマット】
    // ---------------------------------------------------------
    fun formatTime(millis: Long): String {
        val totalSec = millis / 1000
        val min = totalSec / 60
        val sec = totalSec % 60
        val msec = (millis % 1000) / 10
        return String.Companion.format(Locale.US, "%02d:%02d.%02d", min, sec, msec)
    }
}