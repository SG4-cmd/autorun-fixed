package com.example.autorun.config

import android.content.Context
import com.example.autorun.R

/**
 * 【GamePerformanceSettings: パフォーマンス・軽量化設定】
 * 動作が重い場合、ここ数値を調整することで軽量化できます。
 */
object GamePerformanceSettings {

    // --- 描画負荷の調整 ---

    // 道路の分割数 (点線を完璧に滑らかにするため、サンプリングバッファ上限の4000まで引き上げ)
    var ROAD_QUALITY = 4000

    // 背景オブジェクトの数
    var MOUNTAIN_COUNT = 3
    var CLOUD_COUNT = 4

    // --- 計算負荷の調整 ---

    // 履歴ログのサンプリング間隔 (m)
    var LOG_INTERVAL_METERS = 20f

    // 物理演算の更新間隔
    var PHYSICS_DT = 1f / 60f

    fun init(context: Context) {
        ROAD_QUALITY = context.getString(R.string.road_quality).toInt()
        MOUNTAIN_COUNT = context.getString(R.string.mountain_count).toInt()
        CLOUD_COUNT = context.getString(R.string.cloud_count).toInt()
        LOG_INTERVAL_METERS = context.getString(R.string.log_interval_meters).toFloat()
        PHYSICS_DT = context.getString(R.string.physics_dt).toFloat()
    }
}
