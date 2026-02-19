package com.example.autorun.core

import com.example.autorun.config.GamePerformanceSettings
import com.example.autorun.config.GameSettings
import com.example.autorun.data.vehicle.VehicleDatabase
import java.util.LinkedList
import kotlin.math.*
import java.util.Random

/**
 * 【GameState】
 * 現在地XYZ座標と、360度見渡せる自由なカメラオフセットを管理。
 */
class GameState {
    
    enum class NavMode { HOME, MAP, MUSIC, RADIO }

    var playerDistance = 0f
    var playerX = 0f 
    var currentSpeedMs = 0f
    var calculatedSpeedKmH = 0f

    var visualPitch = 0f // ピッチの描画用遅延変数
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

    // --- 3D世界座標 & 方位 ---
    var playerWorldX = 0f
    var playerWorldZ = 0f
    var playerWorldY = 0f
    var playerWorldHeading = 0f 
    var playerHeading: Float get() = playerWorldHeading; set(v) { playerWorldHeading = v }
    var playerHeadingDegrees = 0f 

    // --- カメラ操作系 (オフセット) ---
    var camXOffset = 0f
    var camYOffset = 0f
    var camZOffset = 0f
    var camYawOffset = 0f   
    var camPitchOffset = 0f 

    var cameraPitch = 0f 
    var cameraRoll = 0f
    var lateralVelocity = 0f // 互換用

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

    fun resetCamera() {
        camXOffset = 0f; camYOffset = 0f; camZOffset = 0f
        camYawOffset = 0f; camPitchOffset = 0f
    }

    fun update() {
        val dt = GamePerformanceSettings.PHYSICS_DT
        val specs = VehicleDatabase.getSelectedVehicle()
        gameTimeMillis = (System.nanoTime() - startTimeNanos) / 1_000_000L

        if (!isManualTransmission && isStalled && rawThrottleInput > 0.01f) {
            isStalled = false; engineRPM = 800f 
        }

        throttle += (rawThrottleInput - throttle) * 6.0f * dt
        throttle = throttle.coerceIn(0f, 1f)

        currentTorqueNm = CarPhysics.calculateTorque(engineRPM, isStalled, isTurboActive, throttle)
        val rpmResult = CarPhysics.calculateRPM(engineRPM, currentTorqueNm, currentGear, currentSpeedMs, dt, isStalled, isChangingGear, isManualTransmission)
        engineRPM = rpmResult.first
        isStalled = rpmResult.second

        // 3D旋回物理
        steeringInput = rawSteeringInput.coerceIn(-1.0f, 1.0f)
        if (currentSpeedMs > 0.5f) {
            val turnRate = (steeringInput * 0.85f) * (currentSpeedMs / 18f).coerceIn(0.4f, 1.2f)
            playerWorldHeading += turnRate * dt
        }

        playerWorldX += sin(playerWorldHeading.toDouble()).toFloat() * currentSpeedMs * dt
        playerWorldZ += cos(playerWorldHeading.toDouble()).toFloat() * currentSpeedMs * dt
        
        val currentSegFloat = (playerDistance / GameSettings.SEGMENT_LENGTH).coerceAtLeast(0f)
        playerWorldY = CourseManager.getHeight(currentSegFloat)
        
        currentRoadCurve = CourseManager.getCurve(currentSegFloat)
        val roadH = CourseManager.getRoadWorldHeading(currentSegFloat)
        val speedAlongRoad = currentSpeedMs * cos((playerWorldHeading - roadH).toDouble()).toFloat()
        playerDistance += speedAlongRoad * dt
        totalCurve += currentRoadCurve * (speedAlongRoad * dt / GameSettings.SEGMENT_LENGTH)

        val idealRoadX = CourseManager.getRoadWorldX(currentSegFloat)
        val idealRoadZ = CourseManager.getRoadWorldZ(currentSegFloat)
        playerX = (playerWorldX - idealRoadX) * cos(roadH.toDouble()).toFloat() - (playerWorldZ - idealRoadZ) * sin(roadH.toDouble()).toFloat()

        val accel = CarPhysics.calculateAcceleration(if (isChangingGear) 0f else currentTorqueNm, isBraking, currentSpeedMs, specs.weightKg, 0f, currentGear, playerX)
        currentSpeedMs = (currentSpeedMs + accel * dt).coerceAtLeast(0f)
        calculatedSpeedKmH = currentSpeedMs * 3.6f

        // カメラ姿勢
        visualPitch += ((accel / 12f) - visualPitch) * 0.15f
        cameraPitch = (visualPitch * 0.5f) + CourseManager.getCurrentAngle(currentSegFloat) * 0.0174f
        playerHeadingDegrees = Math.toDegrees(playerWorldHeading.toDouble()).toFloat()
    }
}
