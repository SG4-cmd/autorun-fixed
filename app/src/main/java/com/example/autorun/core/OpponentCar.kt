package com.example.autorun.core

/**
 * 【OpponentCar: 分身車両のデータモデル】
 */
class OpponentCar(
    var worldDistance: Float, // 世界座標での走行距離 (m)
    var laneOffset: Float,    // 車線位置 (メイン車線内でのオフセット)
    var speedMs: Float        // 速度 (m/s)
) {
    fun update(dt: Float) {
        // 同じ方向に向かって走る（距離が増加）
        worldDistance += speedMs * dt
    }

    /**
     * プレイヤーからの相対的な距離（Z座標）を計算
     */
    fun getRelativeZ(playerDistance: Float): Float {
        return worldDistance - playerDistance
    }
}