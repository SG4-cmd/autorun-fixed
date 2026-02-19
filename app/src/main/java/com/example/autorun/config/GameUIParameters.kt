package com.example.autorun.config

import android.content.Context
import android.graphics.Color
import com.example.autorun.R

/**
 * 【GameUIParameters: 画面表示（UI）の見た目を決める定数集】
 * メーターのサイズ、最大値、UIの色や配置に関する設定をまとめています。
 */
object GameUIParameters {

    // 【1. ステータスボックス（左上の情報欄）】
    var STATUS_BOX_WIDTH = 380f
    var STATUS_BOX_HEIGHT = 650f
    var STATUS_TEXT_SIZE = 32f
    var STATUS_BOX_ALPHA = 160
    var STATUS_BOX_COLOR = Color.BLACK
    var STATUS_TEXT_COLOR = Color.WHITE
    var FPS_TEXT_COLOR = Color.YELLOW
    var TORQUE_TEXT_COLOR = Color.CYAN

    // 【2. メーター類の設定】
    var SPEEDO_RADIUS = 180f
    var RPMO_RADIUS = SPEEDO_RADIUS * 0.8f
    var BOOST_RADIUS = SPEEDO_RADIUS * 0.5f

    var SPEEDO_MAX_VALUE = 320f
    var RPMO_MAX_VALUE = 10000f
    var BOOST_MAX_VALUE = 2.0f

    var GAUGE_DIAL_COLOR = Color.BLACK
    var GAUGE_NEEDLE_COLOR = Color.RED
    var GAUGE_TEXT_COLOR = Color.WHITE
    var BOOST_TEXT_COLOR = Color.CYAN

    // 【3. 操作ボタン】
    var BUTTON_ALPHA_PRESSED = 220
    var BUTTON_ALPHA_NORMAL = 140
    var BUTTON_CORNER_RADIUS = 20f
    var COLOR_BTN_DEFAULT = Color.DKGRAY
    var COLOR_BTN_ACCEL = Color.GREEN
    var COLOR_BTN_BRAKE = Color.RED
    var COLOR_BTN_TEXT = Color.WHITE

    // 【4. ミニマップ】
    var NAV_SIZE = 450f
    var NAV_ALPHA = 160
    var NAV_ROAD_COLOR = Color.WHITE
    var NAV_PLAYER_COLOR = Color.RED
    var NAV_ZOOM = 0.5f

    // 【5. 高低差グラフ】
    var LOG_WIDTH = 400f
    var LOG_HEIGHT = 150f
    var LOG_LINE_COLOR = Color.GREEN
    var LOG_MARKER_COLOR = Color.RED

    // 【6. その他】
    var CURVE_PERCENT_MAX = 100f

    fun init(context: Context) {
        STATUS_BOX_WIDTH = context.getString(R.string.status_box_width).toFloat()
        STATUS_BOX_HEIGHT = context.getString(R.string.status_box_height).toFloat()
        STATUS_TEXT_SIZE = context.getString(R.string.status_text_size).toFloat()
        STATUS_BOX_ALPHA = context.getString(R.string.status_box_alpha).toInt()
        STATUS_BOX_COLOR = Color.parseColor(context.getString(R.string.status_box_color))
        STATUS_TEXT_COLOR = Color.parseColor(context.getString(R.string.status_text_color))
        FPS_TEXT_COLOR = Color.parseColor(context.getString(R.string.fps_text_color))
        TORQUE_TEXT_COLOR = Color.parseColor(context.getString(R.string.torque_text_color))

        SPEEDO_RADIUS = context.getString(R.string.speedo_radius).toFloat()
        RPMO_RADIUS = context.getString(R.string.rpmo_radius).toFloat()
        BOOST_RADIUS = context.getString(R.string.boost_radius).toFloat()

        SPEEDO_MAX_VALUE = context.getString(R.string.speedo_max_value).toFloat()
        RPMO_MAX_VALUE = context.getString(R.string.rpmo_max_value).toFloat()
        BOOST_MAX_VALUE = context.getString(R.string.boost_max_value).toFloat()

        GAUGE_DIAL_COLOR = Color.parseColor(context.getString(R.string.gauge_dial_color))
        GAUGE_NEEDLE_COLOR = Color.parseColor(context.getString(R.string.gauge_needle_color))
        GAUGE_TEXT_COLOR = Color.parseColor(context.getString(R.string.gauge_text_color))
        BOOST_TEXT_COLOR = Color.parseColor(context.getString(R.string.boost_text_color))

        BUTTON_ALPHA_PRESSED = context.getString(R.string.button_alpha_pressed).toInt()
        BUTTON_ALPHA_NORMAL = context.getString(R.string.button_alpha_normal).toInt()
        BUTTON_CORNER_RADIUS = context.getString(R.string.button_corner_radius).toFloat()
        COLOR_BTN_DEFAULT = Color.parseColor(context.getString(R.string.color_btn_default))
        COLOR_BTN_ACCEL = Color.parseColor(context.getString(R.string.color_btn_accel))
        COLOR_BTN_BRAKE = Color.parseColor(context.getString(R.string.color_btn_brake))
        COLOR_BTN_TEXT = Color.parseColor(context.getString(R.string.color_btn_text))

        NAV_SIZE = context.getString(R.string.nav_size).toFloat()
        NAV_ALPHA = context.getString(R.string.nav_alpha).toInt()
        NAV_ROAD_COLOR = Color.parseColor(context.getString(R.string.nav_road_color))
        NAV_PLAYER_COLOR = Color.parseColor(context.getString(R.string.nav_player_color))
        NAV_ZOOM = context.getString(R.string.nav_zoom).toFloat()

        LOG_WIDTH = context.getString(R.string.log_width).toFloat()
        LOG_HEIGHT = context.getString(R.string.log_height).toFloat()
        LOG_LINE_COLOR = Color.parseColor(context.getString(R.string.log_line_color))
        LOG_MARKER_COLOR = Color.parseColor(context.getString(R.string.log_marker_color))
        CURVE_PERCENT_MAX = context.getString(R.string.curve_percent_max).toFloat()
    }
}