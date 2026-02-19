package com.example.autorun.data.parts

/**
 * タイヤ: グリップ力と外径に影響
 */
data class TireSpecs(
    val id: String,
    val name: String,
    val gripMultiplier: Float,    // 遠心力への耐性 (1.0が標準)
    val tireDiameterM: Float,     // タイヤ外径 (m)
    val price: Int
)

/**
 * エンジン: 基本出力と重量に影響
 */
data class EngineSpecs(
    val id: String,
    val name: String,
    val maxPowerHp: Float,
    val maxTorqueNm: Float,
    val torquePeakRPM: Float,
    val displacementCc: Int,
    val weightKg: Float,
    val price: Int
)

/**
 * マフラー: 出力の向上率と重量に影響
 */
data class MufflerSpecs(
    val id: String,
    val name: String,
    val powerMultiplier: Float,   // 馬力向上倍率 (1.05 = 5%アップ)
    val weightReductionKg: Float, // 軽量化量 (kg)
    val price: Int
)

/**
 * サスペンション: 旋回性能（遠心力マルチプライヤー）に影響
 */
data class SuspensionSpecs(
    val id: String,
    val name: String,
    val handlingImprovement: Float, // 数値が低いほど遠心力に強くなる (0.9 = 10%向上)
    val price: Int
)

/**
 * トランスミッション: ギア比に影響
 */
data class TransmissionSpecs(
    val id: String,
    val name: String,
    val gearRatios: Array<Float>,
    val finalRatio: Float,
    val price: Int
)
