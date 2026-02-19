package com.example.autorun.core

import com.example.autorun.config.GamePerformanceSettings
import com.example.autorun.config.GameSettings
import com.example.autorun.data.vehicle.VehicleDatabase
import java.util.LinkedList
import kotlin.math.*
import java.util.Random

/**
 * 【GameState】
 * 現在地をXYZ座標で管理し、タイヤの切れ角に応じた旋回物理を制御。
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

    // --- 3D世界座標 & カメラ姿勢 ---
    var playerWorldX = 0f
    var playerWorldZ = 0f
    var playerWorldY = 0f
    var playerWorldHeading = 0f 
    var playerHeading: Float get() = playerWorldHeading; set(v) { playerWorldHeading = v }
    var playerHeadingDegrees = 0f 

    var cameraPitch = 0f // 上下角
    var cameraRoll = 0f  // ロール角

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

        // エンジン始動判定
        if (!isManualTransmission && isStalled && rawThrottleInput > 0.01f) {
            isStalled = false; engineRPM = 800f 
        }

        throttle += (rawThrottleInput - throttle) * 6.0f * dt
        throttle = throttle.coerceIn(0f, 1f)

        currentTorqueNm = CarPhysics.calculateTorque(engineRPM, isStalled, isTurboActive, throttle)
        val rpmResult = CarPhysics.calculateRPM(engineRPM, currentTorqueNm, currentGear, currentSpeedMs, dt, isStalled, isChangingGear, isManualTransmission)
        engineRPM = rpmResult.first
        isStalled = rpmResult.second

        // 3D旋回物理: ハンドルを切った分だけ方位角(Heading)を変化させる
        steeringInput = rawSteeringInput.coerceIn(-1.0f, 1.0f)
        carVisualRotation = steeringInput * 5f 

        if (currentSpeedMs > 0.5f) {
            // タイヤの切れ角による旋回率の計算
            val turnRate = (steeringInput * 0.9f) * (currentSpeedMs / 15f).coerceIn(0.4f, 1.5f)
            playerWorldHeading += turnRate * dt
        }

        // 世界座標(XYZ)の更新
        playerWorldX += sin(playerWorldHeading.toDouble()).toFloat() * currentSpeedMs * dt
        playerWorldZ += cos(playerWorldHeading.toDouble()).toFloat() * currentSpeedMs * dt
        
        val currentSegFloat = (playerDistance / GameSettings.SEGMENT_LENGTH).coerceAtLeast(0f)
        playerWorldY = CourseManager.getHeight(currentSegFloat)
        
        currentRoadCurve = CourseManager.getCurve(currentSegFloat)
        val roadH = CourseManager.getRoadWorldHeading(currentSegFloat)
        val speedAlongRoad = currentSpeedMs * cos((playerWorldHeading - roadH).toDouble()).toFloat()
        playerDistance += speedAlongRoad * dt
        totalCurve += currentRoadCurve * (speedAlongRoad * dt / GameSettings.SEGMENT_LENGTH)

        // 道路中心からの相対的なズレ(playerX)を計算
        val idealRoadX = CourseManager.getRoadWorldX(currentSegFloat)
        val idealRoadZ = CourseManager.getRoadWorldZ(currentSegFloat)
        val dx = playerWorldX - idealRoadX
        val dz = playerWorldZ - idealRoadZ
        playerX = dx * cos(roadH.toDouble()).toFloat() - dz * sin(roadH.toDouble()).toFloat()

        // カメラ姿勢の計算
        val accel = CarPhysics.calculateAcceleration(if (isChangingGear) 0f else currentTorqueNm, isBraking, currentSpeedMs, specs.weightKg, 0f, currentGear, playerX)
        currentSpeedMs = (currentSpeedMs + accel * dt).coerceAtLeast(0f)
        calculatedSpeedKmH = currentSpeedMs * 3.6f

        visualPitch += ((accel / 12f) - visualPitch) * 0.15f
        cameraPitch = (visualPitch * 0.5f) + CourseManager.getCurrentAngle(currentSegFloat) * 0.0174f
        cameraRoll = steeringInput * -0.05f * (currentSpeedMs / 20f).coerceIn(0f, 1f)
        visualTilt = cameraRoll * 57.3f 

        visualEngineRPM += (engineRPM - visualEngineRPM) * 0.45f
        visualSpeedKmH += (calculatedSpeedKmH - visualSpeedKmH) * 0.25f
        playerHeadingDegrees = Math.toDegrees(playerWorldHeading.toDouble()).toFloat()
        roadShake = if (isStalled) 0f else (sin(gameTimeMillis * 0.04f) * (0.04f + (currentTorqueNm / specs.maxTorqueNm * 0.08f))).toFloat()
    }
}
