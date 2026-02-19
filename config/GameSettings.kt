package com.example.autorun.config

import android.graphics.Color

object GameSettings {
    // --- 物理設定 ---
    const val ACCEL = 1.2f
    const val BRAKING_POWER = 15.0f
    const val COASTING_DECEL = 0.2f
    const val OFF_ROAD_DECEL = 1.0f
    var CENTRIFUGAL_FORCE = 0.1f        // 遠心力の基本強度 (ハンドルを切らないと外側に膨らむように強化)
    var CENTRIFUGAL_RESPONSE = 6.0f     // 遠心力の追従速度（数値が大きいほどクイック、小さいほど滑らか）
    var CENTRIFUGAL_SPEED_COEFF = 0.4f  // 速度に対する遠心力の感度係数

    // --- ステアリング設定 ---
    var STEER_MAX_ANGLE = 135f        // 視覚的なハンドルの最大回転角 (度)
    var STEER_LATERAL_COEFF = 1.2f    // 横移動の基本係数 (数値が大きいほどクイックに動く)
    var STEER_SPEED_SCALING = 10f    // 速度による感度補正の基準値 (km/h)

    // --- 道路・カメラ設定 ---
    const val SEGMENT_LENGTH = 1.0f
    const val ROAD_WIDTH = 7.0f      // 合計道路幅
    const val SINGLE_LANE_WIDTH = 3.5f // 1車線の幅 (メートル)
    const val SHOULDER_WIDTH_LEFT = 2.5f // 左側の路肩幅 (2.5m)
    const val SHOULDER_WIDTH_RIGHT = 1.0f // 右側の路肩幅 (1.0m)
    const val LANE_MARKER_WIDTH = 0.2f      // 白線の幅 (20cm)
    const val DRAW_DISTANCE = 800    // 800M先まで見えるように設定
    const val CAMERA_HEIGHT = 1.5f

    // --- 白線パターン設定 (メートル単位) ---
    const val LANE_DASH_LENGTH = 8   // 白線の長さ (8m)
    const val LANE_GAP_LENGTH = 12   // 空白部分の長さ (12m)

    // 描画パラメータ（調整可能）
    var FOV = 600f
    var ROAD_STRETCH = 2f          // 手前の広がり方の拡大率

    const val RUMBLE_LENGTH = 3

    // --- 配色 ---
    val COLOR_SKY = Color.parseColor("#87CEEB")
    val COLOR_GRASS_DARK = Color.parseColor("#228B22")
    val COLOR_GRASS_LIGHT = Color.parseColor("#32CD32")
    val COLOR_ROAD_DARK = Color.parseColor("#444444")
    val COLOR_ROAD_LIGHT = Color.parseColor("#4C4C4C")
    val COLOR_LANE = Color.WHITE
}
