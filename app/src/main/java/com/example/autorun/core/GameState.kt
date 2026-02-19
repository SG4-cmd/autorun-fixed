package com.example.autorun.core

import com.example.autorun.utils.GameUtils
import com.example.autorun.config.GamePerformanceSettings
import com.example.autorun.config.GameSettings
import com.example.autorun.data.vehicle.VehicleDatabase
import java.util.LinkedList
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sin
import java.util.Random

/**
 * 【GameState: ゲームの動的状態管理】
 */
class GameState {
    
    enum class NavMode { HOME, MAP, MUSIC, RADIO }

    var playerDistance = 0f
    var playerX = 0f
    var targetPlayerX = 0f
    var currentSpeedMs = 0f
    var calculatedSpeedKmH = 0f

    var lateralVelocity = 0f 
    var visualPitch = 0f     
    var visualEngineRPM = 0f
    var visualSpeedKmH = 0f
    var currentAirPressureHpa = 1013.25f

    var isAccelerating = false 
    var isBraking = false
    var steeringInput = 0f 
    var rawSteeringInput = 0f 
    var rawThrottleInput = 0f 
    var throttle = 0f
    
    var throttleTouchX = 0f
    var throttleTouchY = 0f
    var isThrottleTouching = false

    // --- UI状態 ---
    var navMode = NavMode.HOME 
    var isMapLongRange = false
    var isSettingsOpen = false
    var isLayoutMode = false 
    var selectedUiId = -1 

    // --- システム情報 ---
    var batteryLevel = 0
    var currentFps = 60

    // --- ライト状態 ---
    var isHighBeam = false

    // --- ラジオ状態 ---
    var radioBand = GameSettings.RADIO_BAND
    var radioFrequency = GameSettings.RADIO_FREQUENCY
    val RADIO_MIN = 76.0f
    val RADIO_MAX = 95.0f
    
    // ラジオ長押し管理
    var radioBtnDownStartTime = 0L
    var radioBtnDir = 0 // -1: down, 1: up, 0: none
    var lastFreqChangeTime = 0L

    var isDeveloperMode = false
    var gaugesLongPressStartTime = 0L
    var isPressingSpeedo = false
    var isPressingRPMO = false

    var isManualTransmission = false
    var isChangingGear = false
    var gearChangeTimer = 0f
    var currentGear = 1
    private var targetGear = 1

    var isStalled = true
    var engineRPM = 0f
    var currentTorqueNm = 0f
    var dragForceN = 0f
    var isTurboActive = false
    var turboBoost = -0.6f 
    private var turbineInertia = 0f 
    
    data class SkidMark(val distance: Float, val playerX: Float, val opacity: Float)
    val skidMarks = LinkedList<SkidMark>()
    var tireSlipRatio = 0f

    var currentRoadCurve = 0f
    var totalCurve = 0f 
    var playerHeading = 0f 
    var playerHeadingDegrees = 0f 
    var playerHeadingAngleMap = 0f 
    
    var visualTilt = 0f
    var roadShake = 0f 
    var carVerticalShake = 0f 
    private var smoothedCentrifugal = 0f 
    var gameTimeMillis = 0L
    private var startTimeNanos = System.nanoTime()
    private val random = Random()

    var minAltSoFar = 0f
    var maxAltSoFar = 0f
    val altitudeLog = LinkedList<Float>()
    val LOG_SEGMENTS = 500

    val opponentCars = mutableListOf<OpponentCar>()

    fun update() {
        val dt = GamePerformanceSettings.PHYSICS_DT
        val specs = VehicleDatabase.getSelectedVehicle()
        gameTimeMillis = (System.nanoTime() - startTimeNanos) / 1_000_000L

        // --- ラジオ長押し選局ロジック ---
        if (radioBtnDir != 0) {
            val now = System.currentTimeMillis()
            if (radioBtnDownStartTime > 0 && now - radioBtnDownStartTime > 500) {
                if (now - lastFreqChangeTime > 100) {
                    radioFrequency = (radioFrequency + 0.1f * radioBtnDir).coerceIn(RADIO_MIN, RADIO_MAX)
                    lastFreqChangeTime = now
                }
            }
        }

        if (!isManualTransmission && isStalled && rawThrottleInput > 0.01f) {
            isStalled = false
        }

        if (isPressingSpeedo && isPressingRPMO) {
            if (gaugesLongPressStartTime == 0L) gaugesLongPressStartTime = System.currentTimeMillis()
            else if (System.currentTimeMillis() - gaugesLongPressStartTime >= 3000L) {
                isDeveloperMode = !isDeveloperMode
                gaugesLongPressStartTime = 0L
                isPressingSpeedo = false; isPressingRPMO = false
            }
        } else { gaugesLongPressStartTime = 0L }

        if (!isManualTransmission && !isChangingGear && !isStalled) {
            targetGear = CarPhysics.selectGearByRPM(currentGear, engineRPM, calculatedSpeedKmH, throttle, isStalled)
        }

        if (targetGear != currentGear && !isChangingGear && !isStalled) {
            isChangingGear = true
            gearChangeTimer = 0.5f
        }

        if (isChangingGear) {
            gearChangeTimer -= dt
            if (gearChangeTimer <= 0) {
                isChangingGear = false
                if (targetGear > currentGear) currentGear++
                else if (targetGear < currentGear) currentGear--
            }
        }

        val throttleSpeed = if (rawThrottleInput > throttle) 5.0f else 8.0f
        throttle += (rawThrottleInput - throttle) * throttleSpeed * dt
        throttle = throttle.coerceIn(0f, 1f)

        if (isStalled) {
            turboBoost = 0f; turbineInertia = 0f
        } else {
            val exhaustEnergy = ((engineRPM / 8000f).pow(1.5f) * (0.2f + throttle * 0.8f)).coerceIn(0f, 1.2f)
            val inertiaChangeSpeed = if (exhaustEnergy > turbineInertia) 0.8f else 1.5f
            turbineInertia += (exhaustEnergy - turbineInertia) * inertiaChangeSpeed * dt
            val thresholdRPM = specs.turboBoostRpm.coerceAtLeast(2000f)
            val rpmFactor = ((engineRPM - thresholdRPM) / 3000f).coerceIn(0f, 1f)
            val targetPositiveBoost = (turbineInertia * rpmFactor * 1.5f).coerceIn(0f, 1.2f)
            val targetNegativeBoost = if (throttle < 0.15f) {
                -((engineRPM - 900f) / 8000f * 0.8f + 0.2f).coerceIn(0f, 0.9f)
            } else {
                0f
            }
            val targetBoost = if (targetNegativeBoost < 0) targetNegativeBoost else targetPositiveBoost
            val boostResponse = if (targetBoost < turboBoost) 12f else 3f 
            turboBoost += (targetBoost - turboBoost) * boostResponse * dt
        }
        
        isTurboActive = turboBoost > 0.1f && specs.hasTurbo
        currentTorqueNm = CarPhysics.calculateTorque(engineRPM, isStalled, isTurboActive, throttle)
        val rpmResult = CarPhysics.calculateRPM(engineRPM, currentTorqueNm, currentGear, currentSpeedMs, dt, isStalled, isChangingGear, isManualTransmission)
        engineRPM = rpmResult.first
        isStalled = rpmResult.second

        val currentSegFloat = playerDistance / GameSettings.SEGMENT_LENGTH
        val slopeDeg = CourseManager.getCurrentAngle(currentSegFloat)
        val effectiveTorque = if (isChangingGear) 0f else currentTorqueNm
        val accel = CarPhysics.calculateAcceleration(effectiveTorque, isBraking, currentSpeedMs, specs.weightKg, slopeDeg, currentGear, playerX)

        currentSpeedMs = (currentSpeedMs + accel * dt).coerceAtLeast(0f)
        calculatedSpeedKmH = currentSpeedMs * 3.6f
        playerDistance += GameUtils.getAdjustedScrollSpeed(currentSpeedMs) * dt

        visualEngineRPM += (engineRPM - visualEngineRPM) * 0.45f
        visualSpeedKmH += (calculatedSpeedKmH - visualSpeedKmH) * 0.25f

        val weightFactor = specs.weightKg / 1400f
        val brakeEffectFactor = if (isBraking) (calculatedSpeedKmH / 100f).coerceIn(0.2f, 1.5f) else 1.0f
        val targetPitch = (accel / 10f) * 3.75f * weightFactor * brakeEffectFactor
        visualPitch += (targetPitch - visualPitch) * 0.15f

        if (rawSteeringInput != 0f) {
            val sign = if (rawSteeringInput < 0) -1f else 1f
            steeringInput += (sign * abs(rawSteeringInput).pow(2.2f) - steeringInput) * 10.0f * dt
        } else {
            val recovery = steeringInput * 5.0f * dt
            steeringInput = if (abs(steeringInput) < abs(recovery)) 0f else steeringInput - recovery
        }
        steeringInput = steeringInput.coerceIn(-1.0f, 1.0f)

        currentRoadCurve = CourseManager.getCurve(currentSegFloat)
        val rawCentrifugal = (currentSpeedMs * currentSpeedMs) * currentRoadCurve * weightFactor * specs.centrifugalForceMultiplier * GameSettings.CENTRIFUGAL_FORCE * GameSettings.CENTRIFUGAL_SPEED_COEFF * 0.8f
        smoothedCentrifugal += (rawCentrifugal - smoothedCentrifugal) * GameSettings.CENTRIFUGAL_RESPONSE * dt

        if (calculatedSpeedKmH >= 1.0f) {
            val speedFactor = (calculatedSpeedKmH / GameSettings.STEER_SPEED_SCALING).coerceIn(0.2f, 2.5f)
            val steerForce = steeringInput * GameSettings.STEER_LATERAL_COEFF * speedFactor
            val totalLateralForce = steerForce - smoothedCentrifugal
            val lateralAccel = totalLateralForce / weightFactor
            lateralVelocity += lateralAccel * dt
            lateralVelocity *= (1.0f - 0.15f * dt * 60f).coerceIn(0f, 1f)
            playerX += lateralVelocity * dt
            val roadInertia = currentRoadCurve * currentSpeedMs * dt
            playerX -= roadInertia
            applyTireSlipInertia(totalLateralForce, weightFactor, specs.tireGripMultiplier, dt)
        } else {
            tireSlipRatio = 0f
        }

        val brakeSkidEffect = if (isBraking && calculatedSpeedKmH > 20f) 0.4f else 0f
        val skidOpacity = (tireSlipRatio + brakeSkidEffect).coerceIn(0f, 1f)
        if (skidOpacity > 0.05f) {
            skidMarks.add(SkidMark(playerDistance, playerX, skidOpacity))
            if (skidMarks.size > 1000) skidMarks.removeFirst()
        } else if (skidMarks.isNotEmpty() && skidMarks.last().opacity > 0f) {
            skidMarks.add(SkidMark(playerDistance, playerX, 0f))
            if (skidMarks.size > 1000) skidMarks.removeFirst()
        }

        val targetBank = -currentRoadCurve * currentSpeedMs * GameSettings.BANK_COEFF
        val lateralInertiaForce = (lateralVelocity * 0.5f) + (smoothedCentrifugal * 0.5f)
        val targetTilt = (lateralInertiaForce - targetBank).coerceIn(-3.0f, 3.0f)
        visualTilt += (targetTilt - visualTilt) * 0.15f

        val leftWall = -(GameSettings.ROAD_WIDTH / 2f + GameSettings.SHOULDER_WIDTH_LEFT) + (specs.widthM / 2f)
        val rightWall = (GameSettings.ROAD_WIDTH / 2f + GameSettings.SHOULDER_WIDTH_RIGHT) - (specs.widthM / 2f)

        if (playerX < leftWall) {
            playerX = leftWall; lateralVelocity *= -0.2f
            if (currentSpeedMs > 1.0f) currentSpeedMs *= 0.985f
        } else if (playerX > rightWall) {
            playerX = rightWall; lateralVelocity *= -0.2f
            if (currentSpeedMs > 1.0f) currentSpeedMs *= 0.985f
        }

        playerHeading += currentRoadCurve * currentSpeedMs * dt
        totalCurve += currentRoadCurve * (currentSpeedMs * dt / GameSettings.SEGMENT_LENGTH)
        playerHeadingDegrees = Math.toDegrees(playerHeading.toDouble()).toFloat()

        roadShake = if (isStalled) 0f else (sin(gameTimeMillis * 0.04f) * (0.04f + (currentTorqueNm / specs.maxTorqueNm * 0.08f))).toFloat()
        carVerticalShake = if (isStalled) 0f else {
            val speedFactor = (calculatedSpeedKmH / 100f).coerceIn(0.1f, 1.0f)
            ((random.nextFloat() - 0.5f) * 2.0f * speedFactor + (visualEngineRPM / 8000f) * (random.nextFloat() - 0.5f) * 3.0f) / 3f
        }

        dragForceN = 0.5f * specs.dragCd * specs.frontalArea * 1.225f * currentSpeedMs * currentSpeedMs
    }

    private fun applyTireSlipInertia(totalForce: Float, weightFactor: Float, tireGripMultiplier: Float, dt: Float) {
        val lateralG = abs(totalForce) / weightFactor
        val gripLimit = GameSettings.BASE_GRIP_LIMIT * tireGripMultiplier
        tireSlipRatio = if (lateralG > gripLimit) { (lateralG - gripLimit) / 5f } else { 0f }.coerceIn(0f, 1f)
        if (lateralG > gripLimit) {
            val slipPower = (lateralG - gripLimit) * GameSettings.SLIDE_COEFF
            val direction = if (totalForce > 0) 1f else -1f
            playerX += direction * slipPower * dt
        }
    }

    fun shiftUp() { if (isManualTransmission && !isStalled && !isChangingGear && currentGear < (VehicleDatabase.getSelectedVehicle().gearRatios.size - 1)) targetGear = currentGear + 1 }
    fun shiftDown() { if (isManualTransmission && !isStalled && !isChangingGear && currentGear > 1) targetGear = currentGear - 1 }
}
