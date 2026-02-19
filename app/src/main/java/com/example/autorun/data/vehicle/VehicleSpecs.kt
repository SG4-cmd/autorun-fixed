package com.example.autorun.data.vehicle

import androidx.annotation.DrawableRes
import com.example.autorun.data.parts.TireSpecs
import com.example.autorun.data.parts.EngineSpecs
import com.example.autorun.data.parts.MufflerSpecs
import com.example.autorun.data.parts.SuspensionSpecs
import com.example.autorun.data.parts.TransmissionSpecs

/**
 * 現在の装備パーツを管理するデータクラス
 */
data class Equipment(
    var selectedTire: TireSpecs,
    var selectedEngine: EngineSpecs,
    var selectedMuffler: MufflerSpecs,
    var selectedSuspension: SuspensionSpecs,
    var selectedTransmission: TransmissionSpecs
)

/**
 * 【VehicleSpecs: 車の性能諸元データ】
 */
data class VehicleSpecs(
    val id: String,
    val name: String,
    @DrawableRes val imageResId: Int,
    val modelPath: String, // 3Dモデルのパス (assets内)
    val engineName: String,
    val driveType: String,
    val isRHD: Boolean = true, // 右ハンドル車かどうか
    val seriesName: String,
    val modelGeneration: Int,
    val displacementCc: Int,
    val isDOHC: Boolean,
    val hasEFI: Boolean,
    
    // ターボ設定
    val hasTurbo: Boolean,
    val turboBoostRpm: Float,
    val turboBoostMultiplier: Float,
    
    val weightKg: Float,
    val widthM: Float,
    val heightM: Float, // 車高（最低地上高など、影のオフセットに使用）
    val lengthM: Float, // 全長（影の伸びに使用）
    val maxPowerHp: Float,
    val maxTorqueNm: Float,
    val torquePeakRPM: Float,
    val gearRatios: Array<Float>,
    val finalRatio: Float,
    val minRedzone: Float,
    val maxRedzone: Float,
    
    // タイヤ・空気抵抗設定
    val tireDiameterM: Float,
    val tireWidthM: Float, // タイヤ幅 (m)
    val dragCd: Float,
    val frontalArea: Float,
    val centrifugalForceMultiplier: Float,
    val tireGripMultiplier: Float = 1.0f // タイヤのグリップ性能
)
