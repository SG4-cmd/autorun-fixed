package com.example.autorun.core

import android.content.Context
import com.example.autorun.R
import com.example.autorun.config.GameSettings
import com.example.autorun.data.vehicle.VehicleDatabase
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/**
 * 【CarPhysics: 車両物理演算エンジン】
 */
object CarPhysics {
    
    // 物理パラメータ
    private var idleRPM = 800f
    private var engineInertia = 0.14f
    private var clutchRigidity = 25.0f
    private var rollingResistCoeff = 0.012f
    private var airDensity = 1.225f
    private var gravity = 9.8f

    fun init(context: Context) {
        idleRPM = context.getString(R.string.phys_idle_rpm).toFloat()
        engineInertia = context.getString(R.string.phys_engine_inertia).toFloat()
        clutchRigidity = context.getString(R.string.phys_clutch_rigidity).toFloat()
        rollingResistCoeff = context.getString(R.string.phys_rolling_resist_coeff).toFloat()
        airDensity = context.getString(R.string.phys_air_density).toFloat()
        gravity = context.getString(R.string.phys_gravity).toFloat()
    }

    fun calculateTorque(rpm: Float, isStalled: Boolean, isTurboActive: Boolean, throttle: Float): Float {
        if (isStalled) return 0f
        val specs = VehicleDatabase.getSelectedVehicle()
        val turboMultiplier = if (isTurboActive) specs.turboBoostMultiplier else 1.0f

        val rpmFactor = when {
            rpm < idleRPM -> 0.3f
            rpm < 1200 -> 0.4f
            rpm < 2500 -> 0.4f + ((rpm - 1200f) / 1300f) * 0.4f
            rpm < specs.torquePeakRPM -> 0.8f + ((rpm - 2500f) / (specs.torquePeakRPM - 2500f)) * 0.2f
            else -> 1.0f - ((rpm - specs.torquePeakRPM) / (specs.maxRedzone - specs.torquePeakRPM)) * 0.4f
        }

        val driveTorque = specs.maxTorqueNm * rpmFactor * turboMultiplier * throttle

        // --- エンジンブレーキの導入 (より現実的なモデル) ---
        val engineBrakeTorque = if (throttle < 0.15f && rpm > idleRPM) {
            // 1. 基本強度を排気量から算出 (基準2000cc)
            val displacementFactor = (specs.displacementCc / 2000f).coerceIn(0.5f, 2.5f)
            
            // 2. エンジンブレーキの基本係数 (排気量を考慮)
            val baseBrakeStrength = 0.08f * displacementFactor

            // 3. 回転数に応じた強度の変化 (高回転ほど強く)
            val rpmRatio = ((rpm - idleRPM) / (specs.maxRedzone - idleRPM)).pow(1.2f).coerceIn(0f, 1.5f)
            
            // 4. スロットル開度に応じた補正
            val throttleFactor = (1f - throttle / 0.15f).coerceIn(0f, 1f)
            
            // 5. 最終的な負のトルクを計算
            -specs.maxTorqueNm * baseBrakeStrength * rpmRatio * throttleFactor
        } else {
            0f
        }

        return driveTorque + engineBrakeTorque
    }

    fun calculateRPM(
        currentRPM: Float,
        engineTorque: Float,
        gear: Int,
        speedMs: Float,
        dt: Float,
        isStalled: Boolean,
        isClutchDisengaged: Boolean,
        isManual: Boolean
    ): Pair<Float, Boolean> {
        if (isStalled) return Pair(0f, true)
        val specs = VehicleDatabase.getSelectedVehicle()

        val gearRatio = specs.gearRatios.getOrNull(gear) ?: 1.0f
        val totalRatio = gearRatio * specs.finalRatio
        val tireCircum = specs.tireDiameterM * Math.PI.toFloat()
        val mechanicalRPM = (speedMs / tireCircum) * totalRatio * 60f
        val targetMechanicalRPM = max(mechanicalRPM, idleRPM)

        var nextRPM: Float
        if (isClutchDisengaged) {
            val friction = (currentRPM / 1000f) * 65f + 40f
            if (currentRPM > specs.maxRedzone) {
                val drop = 8000f * dt
                nextRPM = currentRPM - drop + (Random.Default.nextFloat() * 200f)
            } else {
                val effectiveTorque = if (!isManual) 0f else engineTorque
                val deltaRPM = (effectiveTorque * 22f / engineInertia) - (friction * 15f)
                nextRPM = currentRPM + deltaRPM * dt
                if (!isManual) {
                    val syncForce = 5.0f
                    nextRPM += (targetMechanicalRPM - nextRPM) * syncForce * dt
                }
            }
        } else {
            nextRPM = currentRPM + (targetMechanicalRPM - currentRPM) * clutchRigidity * dt
            if (nextRPM > specs.maxRedzone) {
                nextRPM = specs.maxRedzone - (Random.Default.nextFloat() * 50f)
            }
        }

        // --- エンジン回転数の自然な揺らぎを追加 ---
        // アイドリング時と走行時で揺らぎの大きさを変える
        val fluctuation = if (currentRPM < idleRPM + 100f) {
            (Random.Default.nextFloat() - 0.5f) * 6f // アイドリング時の揺らぎ
        } else {
            (Random.Default.nextFloat() - 0.5f) * 4f // 走行時の揺らぎ
        }
        nextRPM += fluctuation
        
        return Pair(max(nextRPM, idleRPM), false)
    }

    fun calculateAcceleration(
        torqueNm: Float,
        isBrake: Boolean,
        speedMs: Float,
        weight: Float,
        slopeDeg: Float,
        gear: Int,
        playerX: Float
    ): Float {
        val specs = VehicleDatabase.getSelectedVehicle()
        val gearRatio = specs.gearRatios.getOrNull(gear) ?: 1.0f
        val driveForce = (torqueNm * gearRatio * specs.finalRatio) / (specs.tireDiameterM / 2f)

        val dragForce = 0.5f * specs.dragCd * specs.frontalArea * airDensity * speedMs * speedMs
        val rollingResist = weight * gravity * rollingResistCoeff
        val slopeForce = weight * gravity * sin(Math.toRadians(slopeDeg.toDouble()).toFloat())

        val leftLimit = -(GameSettings.ROAD_WIDTH / 2f + GameSettings.SHOULDER_WIDTH_LEFT)
        val rightLimit = (GameSettings.ROAD_WIDTH / 2f + GameSettings.SHOULDER_WIDTH_RIGHT)
        val isOffRoad = playerX < leftLimit || playerX > rightLimit
        
        val offRoadDrag = if (isOffRoad) GameSettings.OFF_ROAD_DECEL * weight else 0f
        val brakeForce = if (isBrake) weight * GameSettings.BRAKING_POWER else 0f

        return (driveForce - dragForce - rollingResist - brakeForce - slopeForce - offRoadDrag) / weight
    }

    fun selectGearByRPM(currentGear: Int, rpm: Float, speedKmH: Float, throttle: Float, isStalled: Boolean): Int {
        if (isStalled) return 1
        val specs = VehicleDatabase.getSelectedVehicle()
        val upshiftPoint = if (throttle > 0.8f) specs.minRedzone - 300f else specs.minRedzone * 0.6f
        if (rpm > upshiftPoint && currentGear < specs.gearRatios.size - 1) return currentGear + 1
        val downshiftPoint = if (throttle > 0.9f) 3500f else 1800f
        if (rpm < downshiftPoint && currentGear > 1) {
            val ratio = specs.gearRatios[currentGear - 1] / specs.gearRatios[currentGear]
            if (rpm * ratio < specs.minRedzone - 500f) return currentGear - 1
        }
        return currentGear
    }

    fun isTurboKicking(rpm: Float, isAccel: Boolean): Boolean {
        val specs = VehicleDatabase.getSelectedVehicle()
        return isAccel && specs.hasTurbo && rpm >= specs.turboBoostRpm
    }
}
