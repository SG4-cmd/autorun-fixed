package com.example.autorun.config

import android.content.Context
import android.graphics.Color
import android.graphics.PointF
import com.example.autorun.R
import com.example.autorun.core.CarPhysics
import com.example.autorun.core.CourseManager
import com.example.autorun.data.parts.PartsDatabase
import com.example.autorun.data.vehicle.VehicleDatabase
import com.example.autorun.ui.BackgroundRenderer
import com.example.autorun.ui.RoadRenderer
import kotlin.math.PI

object GameSettings {
    // --- 物理設定 ---
    var ACCEL = 1.2f
    var BRAKING_POWER = 15.0f
    var COASTING_DECEL = 0.2f
    var OFF_ROAD_DECEL = 1.0f
    var CENTRIFUGAL_FORCE = 0.25f 
    var CENTRIFUGAL_RESPONSE = 6.0f
    var CENTRIFUGAL_SPEED_COEFF = 0.4f
    var BANK_COEFF = 0.5f 
    var BASE_GRIP_LIMIT = 0.12f 
    var SLIDE_COEFF = 3.5f      

    // --- ステアリング設定 ---
    var STEER_MAX_ANGLE = 135f
    var STEER_LATERAL_COEFF = 3.0f 
    var STEER_SPEED_SCALING = 10f

    // --- 道路・カメラ設定 ---
    var SEGMENT_LENGTH = 1.0f
    var ROAD_WIDTH = 7.0f
    var SINGLE_LANE_WIDTH = 3.5f
    var MEDIAN_WIDTH = 0.5f 
    var SHOULDER_WIDTH_LEFT = 2.5f
    var SHOULDER_WIDTH_RIGHT = 1.0f
    var LANE_MARKER_WIDTH = 0.2f 
    var DRAW_DISTANCE = 1000 
    var CAMERA_HEIGHT = 1.5f

    // --- 天体・方位設定 ---
    var SUN_REAL_DIAMETER_KM = 1392700.0
    var SUN_DISTANCE_KM = 149600000.0
    var SUN_ANGULAR_RADIUS = (SUN_REAL_DIAMETER_KM / (2.0 * SUN_DISTANCE_KM)).toFloat()
    var SUN_VISUAL_EXAGGERATION = 15.0f
    var SUN_WORLD_HEADING = (PI * 0.25).toFloat()

    // --- 影・光沢の設定 ---
    var SHOW_SHADOWS = true
    var SHADOW_OFFSET_CM = -5f
    var SHADOW_WIDTH_RATIO = 1.2f
    var SHADOW_LENGTH_RATIO = 1.0f
    var SHADOW_BLUR_RADIUS = 20f
    var SHADOW_CORNER_RADIUS = 15f
    var SHADOW_TRAPEZOID_RATIO = 0.7f
    var CAR_GLOSS_ALPHA = 100
    var CAR_GLOSS_BLUR = 25f

    // --- ステアリング（ハンドル）描画設定 ---
    var STEER_WHEEL_ALPHA = 204
    var STEER_WHEEL_THICKNESS = 80f

    // --- 車両ビジュアル設定 ---
    var WHEEL_HEIGHT_OFFSET = 3f
    var WHEEL_X_OFFSET = 47f

    // --- レイアウト相対座標 (割合: 0.0 - 1.0) ---
    var UI_POS_STATUS = PointF(0.013f, 0.018f)
    var UI_POS_MAP = PointF(0.010f, 0.227f)
    var UI_POS_SETTINGS = PointF(0.354f, 0.023f)
    var UI_POS_COMPASS = PointF(0.406f, 0.023f)
    var UI_POS_RPM = PointF(0.781f, 0.014f)
    var UI_POS_SPEED = PointF(0.870f, 0.009f)
    var UI_POS_BOOST = PointF(0.711f, 0.023f)
    var UI_POS_THROTTLE = PointF(0.958f, 0.463f)
    var UI_POS_BRAKE = PointF(0.635f, 0.768f)
    var UI_POS_STEER = PointF(0.167f, 0.972f)
    
    var UI_POS_EDITOR_PANEL = PointF(100f, 100f)
    var UI_POS_DEBUG_PANEL = PointF(50f, 50f)
    var UI_WIDTH_DEBUG_PANEL = 1000f
    var UI_HEIGHT_DEBUG_PANEL = 600f

    // --- UI倍率設定 ---
    var UI_SCALE_STATUS = 1.0f
    var UI_SCALE_MAP = 1.0f
    var UI_SCALE_SETTINGS = 1.0f
    var UI_SCALE_COMPASS = 1.0f
    var UI_SCALE_RPM = 0.85f
    var UI_SCALE_SPEED = 1.15f
    var UI_SCALE_BOOST = 0.7f
    var UI_SCALE_BRAKE = 1.0f
    var UI_SCALE_THROTTLE = 1.0f
    var UI_SCALE_STEER = 1.0f
    
    // --- UI透明度設定 ---
    var UI_ALPHA_STATUS = 0.8f
    var UI_ALPHA_MAP = 0.8f
    var UI_ALPHA_SETTINGS = 0.8f
    var UI_ALPHA_COMPASS = 0.8f
    var UI_ALPHA_RPM = 0.8f
    var UI_ALPHA_SPEED = 0.8f
    var UI_ALPHA_BOOST = 0.8f
    var UI_ALPHA_BRAKE = 0.8f
    var UI_ALPHA_THROTTLE = 0.8f
    var UI_ALPHA_STEER = 0.8f

    // --- サウンド設定 ---
    const val ENGINE_SOUND_VOLUME = 1.0f
    const val MUSIC_VOLUME = 1.0f
    const val WIND_SOUND_VOLUME = 0.6f

    // --- 永続化データ (保存用) ---
    var RADIO_BAND = "FM"
    var RADIO_FREQUENCY = 80.0f
    var PLAYER_DISTANCE = 0f

    // --- ガードレール・リフレクター設定 ---
    var GUARDRAIL_HEIGHT = 0.85f
    var GUARDRAIL_PLATE_HEIGHT = 0.35f
    var GUARDRAIL_POST_DIAMETER = 0.1398f 
    var GUARDRAIL_POST_INTERVAL = 2.0f   
    var GUARDRAIL_SLEEVE_WIDTH = 0.5f    
    var REFLECTOR_INTERVAL = 50.0f       
    var REFLECTOR_DIAMETER = 0.1f        
    var COLOR_REFLECTOR = Color.parseColor("#FFCC00") 

    // --- 白線パターン設定 ---
    var LANE_DASH_LENGTH = 8
    var LANE_GAP_LENGTH = 12

    // 描画パラメータ
    var FOV = 600f
    var ROAD_STRETCH = 2f
    var RESOLUTIONS = listOf(480, 1080, 2160)
    var RESOLUTION_INDEX = 1

    // --- 配色 ---
    var COLOR_SKY = Color.parseColor("#87CEEB")
    var COLOR_GRASS_DARK = Color.parseColor("#228B22")
    var COLOR_GRASS_LIGHT = Color.parseColor("#32CD32")
    var COLOR_ROAD_DARK = Color.parseColor("#444444")
    var COLOR_ROAD_LIGHT = Color.parseColor("#4C4C4C")
    var COLOR_LANE = Color.WHITE
    var COLOR_GUARDRAIL_PLATE = Color.WHITE
    var COLOR_GUARDRAIL_POST = Color.parseColor("#CCCCCC")
    var COLOR_SKIDMARK = Color.parseColor("#1A1A1A")

    fun init(context: Context) {
        loadDefaults(context)
        loadUserPreferences(context)
        DeveloperSettings.init(context)
        GameUIParameters.init(context)
        PartsDatabase.init(context)
        VehicleDatabase.init(context)
        CourseManager.init(context)
        BackgroundRenderer.init(context)
        RoadRenderer.init(context)
        CarPhysics.init(context)
    }

    private fun loadDefaults(context: Context) {
        ACCEL = context.getString(R.string.accel).toFloat()
        BRAKING_POWER = context.getString(R.string.braking_power).toFloat()
        COASTING_DECEL = context.getString(R.string.coasting_decel).toFloat()
        OFF_ROAD_DECEL = context.getString(R.string.off_road_decel).toFloat()
        CENTRIFUGAL_FORCE = context.getString(R.string.centrifugal_force).toFloat()
        CENTRIFUGAL_RESPONSE = context.getString(R.string.centrifugal_response).toFloat()
        CENTRIFUGAL_SPEED_COEFF = context.getString(R.string.centrifugal_speed_coeff).toFloat()
        BANK_COEFF = context.getString(R.string.bank_coeff).toFloat()
        BASE_GRIP_LIMIT = context.getString(R.string.base_grip_limit).toFloat()
        SLIDE_COEFF = context.getString(R.string.slide_coeff).toFloat()

        STEER_MAX_ANGLE = context.getString(R.string.steer_max_angle).toFloat()
        STEER_LATERAL_COEFF = context.getString(R.string.steer_lateral_coeff).toFloat()
        STEER_SPEED_SCALING = context.getString(R.string.steer_speed_scaling).toFloat()

        SEGMENT_LENGTH = context.getString(R.string.segment_length).toFloat()
        ROAD_WIDTH = context.getString(R.string.road_width).toFloat()
        SINGLE_LANE_WIDTH = context.getString(R.string.single_lane_width).toFloat()
        MEDIAN_WIDTH = context.getString(R.string.median_width).toFloat()
        SHOULDER_WIDTH_LEFT = context.getString(R.string.shoulder_width_left).toFloat()
        SHOULDER_WIDTH_RIGHT = context.getString(R.string.shoulder_width_right).toFloat()
        LANE_MARKER_WIDTH = context.getString(R.string.lane_marker_width).toFloat()
        DRAW_DISTANCE = context.getString(R.string.draw_distance).toInt()
        CAMERA_HEIGHT = context.getString(R.string.camera_height).toFloat()

        GUARDRAIL_HEIGHT = context.getString(R.string.guardrail_height).toFloat()
        GUARDRAIL_PLATE_HEIGHT = context.getString(R.string.guardrail_plate_height).toFloat()
        GUARDRAIL_POST_DIAMETER = context.getString(R.string.guardrail_post_diameter).toFloat()
        GUARDRAIL_POST_INTERVAL = context.getString(R.string.guardrail_post_interval).toFloat()
        GUARDRAIL_SLEEVE_WIDTH = context.getString(R.string.guardrail_sleeve_width).toFloat()
        REFLECTOR_INTERVAL = context.getString(R.string.reflector_interval).toFloat()
        REFLECTOR_DIAMETER = context.getString(R.string.reflector_diameter).toFloat()
        COLOR_REFLECTOR = Color.parseColor(context.getString(R.string.color_reflector))
        LANE_DASH_LENGTH = context.getString(R.string.lane_dash_length).toInt()
        LANE_GAP_LENGTH = context.getString(R.string.lane_gap_length).toInt()

        COLOR_SKY = Color.parseColor(context.getString(R.string.color_sky))
        COLOR_GRASS_DARK = Color.parseColor(context.getString(R.string.color_grass_dark))
        COLOR_GRASS_LIGHT = Color.parseColor(context.getString(R.string.color_grass_light))
        COLOR_ROAD_DARK = Color.parseColor(context.getString(R.string.color_road_dark))
        COLOR_ROAD_LIGHT = Color.parseColor(context.getString(R.string.color_road_light))
        COLOR_LANE = Color.parseColor(context.getString(R.string.color_lane))
        COLOR_GUARDRAIL_PLATE = Color.parseColor(context.getString(R.string.color_guardrail_plate))
        COLOR_GUARDRAIL_POST = Color.parseColor(context.getString(R.string.color_guardrail_post))
        COLOR_SKIDMARK = Color.parseColor(context.getString(R.string.color_shadow))

        UI_POS_STATUS = parsePointF(context.getString(R.string.ui_pos_status))
        UI_POS_MAP = parsePointF(context.getString(R.string.ui_pos_map))
        UI_POS_SETTINGS = parsePointF(context.getString(R.string.ui_pos_settings))
        UI_POS_COMPASS = parsePointF(context.getString(R.string.ui_pos_compass))
        UI_POS_RPM = parsePointF(context.getString(R.string.ui_pos_rpm))
        UI_POS_SPEED = parsePointF(context.getString(R.string.ui_pos_speed))
        UI_POS_BOOST = parsePointF(context.getString(R.string.ui_pos_boost))
        UI_POS_THROTTLE = parsePointF(context.getString(R.string.ui_pos_throttle))
        UI_POS_BRAKE = PointF(0.635f, 0.768f)
        UI_POS_STEER = PointF(0.167f, 0.972f)

        UI_SCALE_STATUS = context.getString(R.string.ui_scale_status).toFloat()
        UI_SCALE_MAP = context.getString(R.string.ui_scale_map).toFloat()
        UI_SCALE_SETTINGS = try { context.getString(R.string.ui_scale_settings).toFloat() } catch(e: Exception) { 1.0f }
        UI_SCALE_COMPASS = try { context.getString(R.string.ui_scale_compass).toFloat() } catch(e: Exception) { 1.0f }
        UI_SCALE_RPM = context.getString(R.string.ui_scale_rpm).toFloat()
        UI_SCALE_SPEED = context.getString(R.string.ui_scale_speed).toFloat()
        UI_SCALE_BOOST = context.getString(R.string.ui_scale_boost).toFloat()
        UI_SCALE_BRAKE = context.getString(R.string.ui_scale_brake).toFloat()
        UI_SCALE_THROTTLE = context.getString(R.string.ui_scale_throttle).toFloat()
        UI_SCALE_STEER = context.getString(R.string.ui_scale_steer).toFloat()
        
        val alpha = try { context.getString(R.string.ui_alpha_default).toFloat() } catch(e: Exception) { 0.8f }
        UI_ALPHA_STATUS = alpha; UI_ALPHA_MAP = alpha; UI_ALPHA_SETTINGS = alpha; UI_ALPHA_COMPASS = alpha
        UI_ALPHA_RPM = alpha; UI_ALPHA_SPEED = alpha; UI_ALPHA_BOOST = alpha; UI_ALPHA_BRAKE = alpha
        UI_ALPHA_THROTTLE = alpha; UI_ALPHA_STEER = alpha
    }

    private fun loadUserPreferences(context: Context) {
        val prefs = context.getSharedPreferences("game_settings", Context.MODE_PRIVATE)
        UI_POS_STATUS = loadRelativePointF(prefs, "ui_pos_status", UI_POS_STATUS)
        UI_POS_MAP = loadRelativePointF(prefs, "ui_pos_map", UI_POS_MAP)
        UI_POS_SETTINGS = loadRelativePointF(prefs, "ui_pos_settings", UI_POS_SETTINGS)
        UI_POS_COMPASS = loadRelativePointF(prefs, "ui_pos_compass", UI_POS_COMPASS)
        UI_POS_RPM = loadRelativePointF(prefs, "ui_pos_rpm", UI_POS_RPM)
        UI_POS_SPEED = loadRelativePointF(prefs, "ui_pos_speed", UI_POS_SPEED)
        UI_POS_BOOST = loadRelativePointF(prefs, "ui_pos_boost", UI_POS_BOOST)
        UI_POS_THROTTLE = loadRelativePointF(prefs, "ui_pos_throttle", UI_POS_THROTTLE)
        UI_POS_BRAKE = loadRelativePointF(prefs, "ui_pos_brake", UI_POS_BRAKE)
        UI_POS_STEER = loadRelativePointF(prefs, "ui_pos_steer", UI_POS_STEER)

        UI_SCALE_STATUS = prefs.getFloat("ui_scale_status", UI_SCALE_STATUS)
        UI_SCALE_MAP = prefs.getFloat("ui_scale_map", UI_SCALE_MAP)
        UI_SCALE_SETTINGS = prefs.getFloat("ui_scale_settings", UI_SCALE_SETTINGS)
        UI_SCALE_COMPASS = prefs.getFloat("ui_scale_compass", UI_SCALE_COMPASS)
        UI_SCALE_RPM = prefs.getFloat("ui_scale_rpm", UI_SCALE_RPM)
        UI_SCALE_SPEED = prefs.getFloat("ui_scale_speed", UI_SCALE_SPEED)
        UI_SCALE_BOOST = prefs.getFloat("ui_scale_boost", UI_SCALE_BOOST)
        UI_SCALE_BRAKE = prefs.getFloat("ui_scale_brake", UI_SCALE_BRAKE)
        UI_SCALE_THROTTLE = prefs.getFloat("ui_scale_throttle", UI_SCALE_THROTTLE)
        UI_SCALE_STEER = prefs.getFloat("ui_scale_steer", UI_SCALE_STEER)

        RADIO_BAND = prefs.getString("radio_band", "FM") ?: "FM"
        RADIO_FREQUENCY = prefs.getFloat("radio_frequency", 80.0f)
        PLAYER_DISTANCE = prefs.getFloat("player_distance", 0f)
    }

    private fun loadRelativePointF(prefs: android.content.SharedPreferences, key: String, default: PointF): PointF {
        val s = prefs.getString(key, null) ?: return default
        val p = parsePointF(s)
        return if (p.x > 1.0f || p.y > 1.0f) default else p
    }

    fun saveLayout(context: Context) {
        val prefs = context.getSharedPreferences("game_settings", Context.MODE_PRIVATE).edit()
        savePointF(prefs, "ui_pos_status", UI_POS_STATUS)
        savePointF(prefs, "ui_pos_map", UI_POS_MAP)
        savePointF(prefs, "ui_pos_settings", UI_POS_SETTINGS)
        savePointF(prefs, "ui_pos_compass", UI_POS_COMPASS)
        savePointF(prefs, "ui_pos_rpm", UI_POS_RPM)
        savePointF(prefs, "ui_pos_speed", UI_POS_SPEED)
        savePointF(prefs, "ui_pos_boost", UI_POS_BOOST)
        savePointF(prefs, "ui_pos_throttle", UI_POS_THROTTLE)
        savePointF(prefs, "ui_pos_brake", UI_POS_BRAKE)
        savePointF(prefs, "ui_pos_steer", UI_POS_STEER)
        
        prefs.putFloat("ui_scale_status", UI_SCALE_STATUS)
        prefs.putFloat("ui_scale_map", UI_SCALE_MAP)
        prefs.putFloat("ui_scale_settings", UI_SCALE_SETTINGS)
        prefs.putFloat("ui_scale_compass", UI_SCALE_COMPASS)
        prefs.putFloat("ui_scale_rpm", UI_SCALE_RPM)
        prefs.putFloat("ui_scale_speed", UI_SCALE_SPEED)
        prefs.putFloat("ui_scale_boost", UI_SCALE_BOOST)
        prefs.putFloat("ui_scale_brake", UI_SCALE_BRAKE)
        prefs.putFloat("ui_scale_throttle", UI_SCALE_THROTTLE)
        prefs.putFloat("ui_scale_steer", UI_SCALE_STEER)
        prefs.apply()
    }

    fun saveRadioSettings(context: Context, band: String, freq: Float) {
        RADIO_BAND = band
        RADIO_FREQUENCY = freq
        val prefs = context.getSharedPreferences("game_settings", Context.MODE_PRIVATE).edit()
        prefs.putString("radio_band", band)
        prefs.putFloat("radio_frequency", freq)
        prefs.apply()
    }

    fun savePlayerDistance(context: Context, distance: Float) {
        PLAYER_DISTANCE = distance
        val prefs = context.getSharedPreferences("game_settings", Context.MODE_PRIVATE).edit()
        prefs.putFloat("player_distance", distance)
        prefs.apply()
    }

    fun resetLayout(context: Context) {
        loadDefaults(context)
        saveLayout(context)
    }

    private fun savePointF(editor: android.content.SharedPreferences.Editor, key: String, p: PointF) {
        editor.putString(key, "${p.x},${p.y}")
    }

    private fun parsePointF(s: String): PointF {
        val parts = s.split(",")
        return PointF(parts[0].trim().toFloat(), parts[1].trim().toFloat())
    }

    fun getTargetHeight(screenHeight: Float): Int {
        val h = RESOLUTIONS[RESOLUTION_INDEX]
        return if (h > screenHeight) screenHeight.toInt() else h
    }

    fun getResolutionStepFactor(): Float {
        return 2160f / RESOLUTIONS[RESOLUTION_INDEX].toFloat()
    }
}
