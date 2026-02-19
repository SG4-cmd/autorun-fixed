package com.example.autorun.data.environment

import com.example.autorun.config.GamePerformanceSettings
import com.example.autorun.core.GameState

/**
 * 【BackgroundGeometry】
 * 山や背景オブジェクトの配置・スクロール座標計算を担当するデータクラス。
 */
object BackgroundGeometry {

    /**
     * 各山の現在のX座標を計算してリストで返す
     */
    fun getMountainPositions(w: Float, state: GameState): FloatArray {
        val positions = FloatArray(GamePerformanceSettings.MOUNTAIN_COUNT)
        val mountainScroll = (state.playerDistance * 0.006f)
        val mCurveShift = -state.totalCurve * 450f
        
        for (i in 0 until GamePerformanceSettings.MOUNTAIN_COUNT) {
            val basePosX = (i * w * 0.8f)
            var cx = (basePosX - mountainScroll + mCurveShift) % (w * 2.4f)
            if (cx < 0f) cx += w * 2.4f
            positions[i] = cx
        }
        return positions
    }
}
