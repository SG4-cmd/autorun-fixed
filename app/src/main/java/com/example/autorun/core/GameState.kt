package com.example.autorun.core

import com.example.autorun.utils.GameUtils
import com.example.autorun.config.GamePerformanceSettings
import com.example.autorun.config.GameSettings
import com.example.autorun.data.vehicle.VehicleDatabase
import java.util.LinkedList
import kotlin.math.*
import java.util.Random

/**
 * 【GameState: ゲームの動的状態管理】
 * 3D空間における車両の物理挙動とシステム状態を管理。
 */
class GameState {
    
    enum class NavMode { HOME, MAP, MUSIC, RADIO }

    var playerDistance = 0f
    var playerX = 0f 
    var currentSpeedMs = 0f
    var calculatedSpeedKmH = 0f

    var lateralVelocity = 0f 
    var visualPitch = 0f     
    var visualEngineRPM = 0f
    var visualSpeedKmH = 0f

    var isAccelerating = false 
    var isBraking = false
    var steeringInput = 0f 
    var rawSteeringInput = 0f 
    var rawThrottleInput = 0f 
    var throttle = 0f
    
    var throttleTouchX = 0f
    var throttleTouchY = 0f
    var isThrottleTouching = false

    // --- 3D世界座標 ---
    var playerWorldX = 0f
    var playerWorldZ = 0f
    var playerWorldY = 0f
    var playerWorldHeading = 0f 
    var playerHeading: Float get() = playerWorldHeading; set(v) { playerWorldHeading = v }

    val opponentCars = mutableListOf<OpponentCar>()

    // --- システム・UI状態 ---
    var navMode = NavMode.HOME 
    var isMapLongRange = false
    var isSettingsOpen = false
    var isLayoutMode = false 
    var selectedUiId = -1 
    var currentFps = 60
    var batteryLevel = 100
    var isHighBeam = false
    
    // --- ラジオ状態 ---
    var radioBand = GameSettings.RADIO_BAND
    var radioFrequency = GameSettings.RADIO_FREQUENCY
    val RADIO_MIN = 76.0f
    val RADIO_MAX = 95.0f
    var radioBtnDownStartTime = 0L
    var radioBtnDir = 0
    var lastFreqChangeTime = 0L

    var isDeveloperMode = false
    var isPressingSpeedo = false
    var isPressingRPMO = false
    var gaugesLongPressStartTime = 0L

    var isManualTransmission = false
    var isChangingGear = false
    var gearChangeTimer = 0f
    var currentGear = 1
    private var targetGear = 1

    var isStalled = true
    var engineRPM = 0f
    var currentTorqueNm = 0f
    var isTurboActive = false
    var turboBoost = -0.6f 
    private var turbineInertia = 0f 
    
    data class SkidMark(val distance: Float, val playerX: Float, val opacity: Float)
    val skidMarks = LinkedList<SkidMark>()
    var tireSlipRatio = 0f

    var currentRoadCurve = 0f
    var totalCurve = 0f 
    var playerHeadingDegrees = 0f 
    var visualTilt = 0f
    var carVisualRotation = 0f
    var roadShake = 0f 
    var carVerticalShake = 0f 
    var gameTimeMillis = 0L
    private var startTimeNanos = System.nanoTime()
    private val random = Random()

    fun update() {
        val dt = GamePerformanceSettings.PHYSICS_DT
        val specs = VehicleDatabase.getSelectedVehicle()
        gameTimeMillis = (System.nanoTime() - startTimeNanos) / 1_000_000L

        // ラジオ選局ロジック
        if (radioBtnDir != 0) {
            val now = System.currentTimeMillis()
            if (radioBtnDownStartTime > 0 && now - radioBtnDownStartTime > 500) {
                if (now - lastFreqChangeTime > 100) {
                    radioFrequency = (radioFrequency + 0.1f * radioBtnDir).coerceIn(RADIO_MIN, RADIO_MAX)
                    lastFreqChangeTime = now
                }
            }
        }

        // エンジン始動判定
        if (!isManualTransmission && isStalled && rawThrottleInput > 0.01f) {
            isStalled = false
            engineRPM = 800f 
        }

        // スロットル
        val throttleSpeed = if (rawThrottleInput > throttle) 5.0f else 8.0f
        throttle += (rawThrottleInput - throttle) * throttleSpeed * dt
        throttle = throttle.coerceIn(0f, 1f)

        // 変速
        if (!isManualTransmission && !isChangingGear && !isStalled) {
            targetGear = CarPhysics.selectGearByRPM(currentGear, engineRPM, calculatedSpeedKmH, throttle, isStalled)
        }
        if (targetGear != currentGear && !isChangingGear && !isStalled) {
            isChangingGear = true; gearChangeTimer = 0.5f
        }
        if (isChangingGear) {
            gearChangeTimer -= dt
            if (gearChangeTimer <= 0) {
                isChangingGear = false
                if (targetGear > currentGear) currentGear++ else if (targetGear < currentGear) currentGear--
            }
        }

        // ターボ・トルク計算
        if (!isStalled && specs.hasTurbo) {
            val exhaustEnergy = ((engineRPM / 8000f).pow(1.5f) * (0.2f + throttle * 0.8f)).coerceIn(0f, 1.2f)
            turbineInertia += (exhaustEnergy - turbineInertia) * 0.8f * dt
            val thresholdRPM = specs.turboBoostRpm
            val rpmFactor = ((engineRPM - thresholdRPM) / 3000f).coerceIn(0f, 1f)
            val targetBoost = if (throttle < 0.15f) -0.6f else (turbineInertia * rpmFactor * 1.5f).coerceIn(0f, 1.2f)
            turboBoost += (targetBoost - turboBoost) * 5f * dt
            isTurboActive = turboBoost > 0.1f
        }

        currentTorqueNm = CarPhysics.calculateTorque(engineRPM, isStalled, isTurboActive, throttle)
        val rpmResult = CarPhysics.calculateRPM(engineRPM, currentTorqueNm, currentGear, currentSpeedMs, dt, isStalled, isChangingGear, isManualTransmission)
        engineRPM = rpmResult.first
        isStalled = rpmResult.second

        // 加速度・速度・距離
        val currentSegFloat = (playerDistance / GameSettings.SEGMENT_LENGTH).coerceAtLeast(0f)
        val slopeDeg = CourseManager.getCurrentAngle(currentSegFloat)
        val accel = CarPhysics.calculateAcceleration(if (isChangingGear) 0f else currentTorqueNm, isBraking, currentSpeedMs, specs.weightKg, slopeDeg, currentGear, playerX)

        currentSpeedMs = (currentSpeedMs + accel * dt).coerceAtLeast(0f)
        calculatedSpeedKmH = currentSpeedMs * 3.6f

        // ハンドルと方位
        steeringInput = rawSteeringInput.coerceIn(-1.0f, 1.0f)
        if (currentSpeedMs > 0.5f) {
            val turnSensitivity = 0.9f 
            val turnRate = (steeringInput * turnSensitivity) * (currentSpeedMs / 15f).coerceIn(0.4f, 1.5f)
            playerWorldHeading += turnRate * dt
        }

        // 世界座標
        playerWorldX += sin(playerWorldHeading.toDouble()).toFloat() * currentSpeedMs * dt
        playerWorldZ += cos(playerWorldHeading.toDouble()).toFloat() * currentSpeedMs * dt
        
        currentRoadCurve = CourseManager.getCurve(currentSegFloat)
        val roadH = CourseManager.getRoadWorldHeading(currentSegFloat)
        val angleDiff = playerWorldHeading - roadH
        val speedAlongRoad = currentSpeedMs * cos(angleDiff.toDouble()).toFloat()
        playerDistance += speedAlongRoad * dt
        totalCurve += currentRoadCurve * (speedAlongRoad * dt / GameSettings.SEGMENT_LENGTH)

        val idealRoadX = CourseManager.getRoadWorldX(currentSegFloat)
        val idealRoadZ = CourseManager.getRoadWorldZ(currentSegFloat)
        val dx = playerWorldX - idealRoadX
        val dz = playerWorldZ - idealRoadZ
        playerX = dx * cos(roadH.toDouble()).toFloat() - dz * sin(roadH.toDouble()).toFloat()

        // 描画用プロパティ
        visualEngineRPM += (engineRPM - visualEngineRPM) * 0.45f
        visualSpeedKmH += (calculatedSpeedKmH - visualSpeedKmH) * 0.25f
        playerHeadingDegrees = Math.toDegrees(playerWorldHeading.toDouble()).toFloat()

        val weightFactor = specs.weightKg / 1400f
        val targetPitch = (accel / 10f) * 3.75f * weightFactor
        visualPitch += (targetPitch - visualPitch) * 0.15f

        val leftWall = -(GameSettings.ROAD_WIDTH / 2f + GameSettings.SHOULDER_WIDTH_LEFT) + (specs.widthM / 2f)
        val rightWall = (GameSettings.ROAD_WIDTH / 2f + GameSettings.SHOULDER_WIDTH_RIGHT) - (specs.widthM / 2f)
        if (playerX < leftWall) { playerX = leftWall; currentSpeedMs *= 0.98f }
        else if (playerX > rightWall) { playerX = rightWall; currentSpeedMs *= 0.98f }
        
        roadShake = if (isStalled) 0f else (sin(gameTimeMillis * 0.04f) * (0.04f + (currentTorqueNm / specs.maxTorqueNm * 0.08f))).toFloat()
    }
}
