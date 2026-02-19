package com.example.autorun.ui

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.os.BatteryManager
import com.example.autorun.R
import com.example.autorun.audio.MusicPlayer
import com.example.autorun.config.GameSettings
import com.example.autorun.core.CourseManager
import com.example.autorun.core.GameState
import com.example.autorun.data.radio.RadioDatabase
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

object HDUMap {
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = Color.BLACK; alpha = 180 }
    private val btnPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = Color.DKGRAY; alpha = 200 }
    private val activeBtnPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = Color.BLUE; alpha = 200 }
    private val bandBtnPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = Color.parseColor("#444444"); alpha = 220 }
    private val btnTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textAlign = Paint.Align.CENTER }
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = Color.WHITE }
    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.CYAN }
    private val infoPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val visualizerPaint = Paint().apply { color = Color.GREEN; alpha = 150 }
    private val roadPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 6f; color = Color.WHITE }
    private val passedRoadPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 6f; color = Color.RED }
    private val pointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = Color.GREEN }
    private val goalPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = Color.RED }
    private val flagStaffPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2f; color = Color.parseColor("#666666") }
    private val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = Color.RED }
    
    private val compassBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = Color.BLACK; alpha = 0 }
    private val compassBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2f; color = Color.WHITE; alpha = 0 }
    private val compassTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = Color.WHITE; textSize = 28f; alpha = 0 }
    private val compassIndicatorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 0f; color = Color.TRANSPARENT; alpha = 0 }

    private val navTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textAlign = Paint.Align.RIGHT }
    
    // 通知用
    private val notifyBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = Color.parseColor("#CC000033") }
    private val notifyTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.YELLOW; textAlign = Paint.Align.LEFT }

    private val miniMapPath = Path(); private val passedPath = Path(); private val arrowPath = Path(); private val flagPath = Path(); private val iconPath = Path()
    private var lastArrowSize = 0f; private var cachedCourseSegments = -1; private val coursePointsX = mutableListOf<Float>()
    private var cachedMinX = 0f; private var cachedMaxX = 0f

    private val btnHomeRect = RectF(); private val btnMapRect = RectF(); private val btnMusicRect = RectF(); private val btnRadioRect = RectF()
    val btnPrevRect = RectF(); val btnPlayRect = RectF(); val btnNextRect = RectF()
    private val btnBandRect = RectF(); private val btnSettingsRect = RectF(); private val btnLightRect = RectF()

    private val jstZone = TimeZone.getTimeZone("Asia/Tokyo")
    private val directions = listOf("N" to 0f, "NE" to 45f, "E" to 90f, "SE" to 135f, "S" to 180f, "SW" to 225f, "W" to 270f, "NW" to 315f)

    private val calendar = Calendar.getInstance(jstZone)
    private var lastMinute = -1; private var timeStrCache = ""
    private var lastRadioFreq = -1f; private var lastRadioBand = ""

    fun init(context: Context) {
        RadioDatabase.init(context)
        val colorMapRoad = context.getString(R.string.nav_road_color)
        val colorPlayer = context.getString(R.string.nav_player_color)
        roadPaint.color = Color.parseColor(colorMapRoad); arrowPaint.color = Color.parseColor(colorPlayer)
        goalPaint.color = Color.parseColor(colorPlayer)
        bgPaint.alpha = context.getString(R.string.nav_alpha).toInt()
    }

    private fun updateButtonRects(mapRect: RectF, state: GameState) {
        val uiScale = GameSettings.UI_SCALE_MAP; val sideBtnW = 80f * uiScale; val sideBtnH = mapRect.height() / 4f; val margin = 5f * uiScale
        btnHomeRect.set(mapRect.right + margin, mapRect.top, mapRect.right + margin + sideBtnW, mapRect.top + sideBtnH - margin)
        btnMapRect.set(mapRect.right + margin, mapRect.top + sideBtnH, mapRect.right + margin + sideBtnW, mapRect.top + 2 * sideBtnH - margin)
        btnMusicRect.set(mapRect.right + margin, mapRect.top + 2 * sideBtnH, mapRect.right + margin + sideBtnW, mapRect.top + 3 * sideBtnH - margin)
        btnRadioRect.set(mapRect.right + margin, mapRect.top + 3 * sideBtnH, mapRect.right + margin + sideBtnW, mapRect.bottom)
        
        if (state.navMode != GameState.NavMode.MAP && state.navMode != GameState.NavMode.HOME) {
            val ctrlBtnW = 100f * uiScale; val ctrlBtnH = 60f * uiScale; val centerX = mapRect.centerX(); val spacing = 20f * uiScale
            val centerY = mapRect.top + 205f * uiScale 
            btnPlayRect.set(centerX - ctrlBtnW / 2f, centerY - ctrlBtnH / 2f, centerX + ctrlBtnW / 2f, centerY + ctrlBtnH / 2f)
            btnPrevRect.set(btnPlayRect.left - spacing - ctrlBtnW, btnPlayRect.top, btnPlayRect.left - spacing, btnPlayRect.bottom)
            btnNextRect.set(btnPlayRect.right + spacing, btnPlayRect.top, btnPlayRect.right + spacing + ctrlBtnW, btnPlayRect.bottom)
            btnBandRect.set(mapRect.right - 110f * uiScale, mapRect.top + 20f * uiScale, mapRect.right - 20f * uiScale, mapRect.top + 70f * uiScale)
        } else if (state.navMode == GameState.NavMode.HOME) {
            val iconSize = 44f * uiScale
            btnSettingsRect.set(mapRect.right - iconSize - 35f * uiScale, mapRect.top + 35f * uiScale, mapRect.right - 35f * uiScale, mapRect.top + 35f * uiScale + iconSize)
            // ライト切り替えボタンをナビの下（サイドボタンの下）に配置。今回はナビ画面下部に横並びで配置
            btnLightRect.set(mapRect.left + 35f * uiScale, mapRect.bottom - 80f * uiScale, mapRect.left + 200f * uiScale, mapRect.bottom - 20f * uiScale)
        }
    }

    fun draw(canvas: Canvas, state: GameState, mapRect: RectF, compassRect: RectF, fontWarrior: Typeface?, musicPlayer: MusicPlayer?, context: Context) {
        updateButtonRects(mapRect, state)
        if (state.navMode == GameState.NavMode.RADIO && state.radioBand == "FM") {
            if (state.radioFrequency != lastRadioFreq || state.radioBand != lastRadioBand) {
                val station = RadioDatabase.getStationByFrequency(state.radioBand, state.radioFrequency)
                if (station != null) playStation(context, station, musicPlayer, state.radioBand)
                else musicPlayer?.stop()
                lastRadioFreq = state.radioFrequency; lastRadioBand = state.radioBand
                GameSettings.saveRadioSettings(context, state.radioBand, state.radioFrequency)
            }
        }
        when (state.navMode) {
            GameState.NavMode.HOME -> drawHome(canvas, state, mapRect, fontWarrior, context)
            GameState.NavMode.MAP -> drawMiniMap(canvas, state, mapRect, fontWarrior)
            GameState.NavMode.MUSIC -> drawMusicPlayer(canvas, state, mapRect, fontWarrior, musicPlayer)
            GameState.NavMode.RADIO -> drawRadio(canvas, state, mapRect, fontWarrior, musicPlayer)
        }
        drawSideButtons(canvas, fontWarrior, state)
        drawCompass(canvas, state, compassRect, fontWarrior)
        
        // 施設接近通知の描画
        drawFacilityNotification(canvas, state, mapRect, fontWarrior)
    }

    private fun drawFacilityNotification(canvas: Canvas, state: GameState, mapRect: RectF, font: Typeface?) {
        val uiScale = GameSettings.UI_SCALE_MAP
        val playerKm = state.playerDistance / 1000f
        
        val nearbyObject = CourseManager.getRoadObjects().find { obj ->
            val dist = obj.posKm - playerKm
            (dist in 0.95f..1.05f) || (dist in 0.45f..0.55f)
        }

        if (nearbyObject != null) {
            val dist = nearbyObject.posKm - playerKm
            val distLabel = if (dist > 0.75f) "1KM先" else "500M先"
            val message = "${distLabel}、${nearbyObject.label}があります"
            
            notifyTextPaint.typeface = font
            notifyTextPaint.textSize = 26f * uiScale
            
            val textWidth = notifyTextPaint.measureText(message)
            val rectW = textWidth + 40f * uiScale
            val rectH = 50f * uiScale
            val rect = RectF(mapRect.right + 100f * uiScale, mapRect.top + 20f * uiScale, mapRect.right + 100f * uiScale + rectW, mapRect.top + 20f * uiScale + rectH)
            
            canvas.drawRoundRect(rect, 10f * uiScale, 10f * uiScale, notifyBgPaint)
            canvas.drawText(message, rect.left + 20f * uiScale, rect.centerY() + 10f * uiScale, notifyTextPaint)
        }
    }

    private fun drawHome(canvas: Canvas, state: GameState, mapRect: RectF, font: Typeface?, context: Context) {
        val uiScale = GameSettings.UI_SCALE_MAP; canvas.drawRoundRect(mapRect, 15f * uiScale, 15f * uiScale, bgPaint)
        titlePaint.typeface = font; titlePaint.textSize = 28f * uiScale; titlePaint.textAlign = Paint.Align.LEFT; titlePaint.color = Color.CYAN
        canvas.drawText("SYSTEM HOME", mapRect.left + 35f * uiScale, mapRect.top + 50f * uiScale, titlePaint)
        
        val now = Date()
        val sdfDate = SimpleDateFormat("yyyy/MM/dd (E)", Locale.JAPANESE).apply { timeZone = jstZone }
        val sdfTime = SimpleDateFormat("HH:mm:ss", Locale.JAPANESE).apply { timeZone = jstZone }
        
        infoPaint.typeface = font; infoPaint.color = Color.WHITE
        infoPaint.textSize = 32f * uiScale; canvas.drawText(sdfDate.format(now), mapRect.left + 35f * uiScale, mapRect.top + 110f * uiScale, infoPaint)
        infoPaint.textSize = 64f * uiScale; canvas.drawText(sdfTime.format(now), mapRect.left + 35f * uiScale, mapRect.top + 180f * uiScale, infoPaint)
        
        val ifilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val batteryStatus = context.registerReceiver(null, ifilter)
        val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        state.batteryLevel = level
        
        drawSettingsIcon(canvas, btnSettingsRect, uiScale)
        
        val battW = 60f * uiScale; val battH = 30f * uiScale
        val battRect = RectF(btnSettingsRect.left - battW - 25f * uiScale, btnSettingsRect.centerY() - battH / 2f, btnSettingsRect.left - 25f * uiScale, btnSettingsRect.centerY() + battH / 2f)
        drawBatteryIcon(canvas, battRect, state.batteryLevel, uiScale)
        
        infoPaint.textSize = 22f * uiScale
        canvas.drawText("FRAME: ${state.currentFps} FPS", mapRect.left + 35f * uiScale, mapRect.top + 230f * uiScale, infoPaint)

        canvas.drawRoundRect(btnLightRect, 8f * uiScale, 8f * uiScale, if (state.isHighBeam) activeBtnPaint else btnPaint)
        btnTextPaint.textSize = 20f * uiScale
        canvas.drawText(if (state.isHighBeam) "HIGH BEAM" else "LOW BEAM", btnLightRect.centerX(), btnLightRect.centerY() + 8f * uiScale, btnTextPaint)
    }

    private fun drawBatteryIcon(canvas: Canvas, rect: RectF, level: Int, uiScale: Float) {
        iconPaint.style = Paint.Style.STROKE; iconPaint.strokeWidth = 2f * uiScale; iconPaint.color = Color.WHITE
        canvas.drawRoundRect(rect, 4f * uiScale, 4f * uiScale, iconPaint)
        val tipW = 4f * uiScale; val tipH = rect.height() * 0.4f
        canvas.drawRect(rect.right, rect.centerY() - tipH / 2f, rect.right + tipW, rect.centerY() + tipH / 2f, iconPaint)
        
        iconPaint.style = Paint.Style.FILL
        val step = (level / 20).coerceIn(0, 5)
        val margin = 3f * uiScale; val blockW = (rect.width() - margin * 6f) / 5f
        for (i in 0 until step) {
            val left = rect.left + margin + i * (blockW + margin)
            canvas.drawRect(left, rect.top + margin, left + blockW, rect.bottom - margin, iconPaint)
        }
    }

    private fun drawSettingsIcon(canvas: Canvas, rect: RectF, uiScale: Float) {
        iconPaint.style = Paint.Style.STROKE; iconPaint.strokeWidth = 3f * uiScale; iconPaint.color = Color.WHITE
        canvas.drawCircle(rect.centerX(), rect.centerY(), rect.width() * 0.28f, iconPaint)
        val cx = rect.centerX(); val cy = rect.centerY(); val r = rect.width() * 0.42f
        for (i in 0 until 8) {
            val angle = (i * 45.0) * PI / 180.0
            val x1 = (cx + (r * 0.65f) * cos(angle)).toFloat(); val y1 = (cy + (r * 0.65f) * sin(angle)).toFloat()
            val x2 = (cx + r * cos(angle)).toFloat(); val y2 = (cy + r * sin(angle)).toFloat()
            canvas.drawLine(x1, y1, x2, y2, iconPaint)
        }
    }

    private fun playStation(context: Context, station: com.example.autorun.data.radio.RadioStation, musicPlayer: MusicPlayer?, band: String) {
        if (station.type == "local" && station.resourceName != null) {
            val resId = context.resources.getIdentifier(station.resourceName, "raw", context.packageName)
            if (resId != 0) musicPlayer?.playRawResource(resId, band)
        } else if (station.type == "stream" && station.streamUrl != null) {
            musicPlayer?.playStream(station.streamUrl, band)
        }
    }

    private fun drawRadio(canvas: Canvas, state: GameState, mapRect: RectF, font: Typeface?, musicPlayer: MusicPlayer?) {
        val uiScale = GameSettings.UI_SCALE_MAP; canvas.drawRoundRect(mapRect, 15f * uiScale, 15f * uiScale, bgPaint)
        titlePaint.typeface = font; titlePaint.textSize = 28f * uiScale; titlePaint.color = Color.CYAN; titlePaint.textAlign = Paint.Align.LEFT
        canvas.drawText("RADIO MODE", mapRect.left + 35f * uiScale, mapRect.top + 50f * uiScale, titlePaint)
        canvas.drawRoundRect(btnBandRect, 5f * uiScale, 5f * uiScale, bandBtnPaint)
        btnTextPaint.textSize = 24f * uiScale; canvas.drawText(state.radioBand, btnBandRect.centerX(), btnBandRect.centerY() + 10f * uiScale, btnTextPaint)
        val station = RadioDatabase.getStationByFrequency(state.radioBand, state.radioFrequency)
        infoPaint.color = Color.WHITE; infoPaint.textSize = 54f * uiScale; infoPaint.typeface = font
        canvas.drawText("%.1f MHz".format(state.radioFrequency), mapRect.left + 35f * uiScale, mapRect.top + 120f * uiScale, infoPaint)
        infoPaint.textSize = 32f * uiScale; infoPaint.color = if (station != null) Color.YELLOW else Color.GRAY
        canvas.drawText(station?.name ?: "NO SIGNAL", mapRect.left + 35f * uiScale, mapRect.top + 165f * uiScale, infoPaint)
        drawPlayerControls(canvas, state, musicPlayer, uiScale)
    }

    private fun drawMusicPlayer(canvas: Canvas, state: GameState, mapRect: RectF, fontWarrior: Typeface?, musicPlayer: MusicPlayer?) {
        val uiScale = GameSettings.UI_SCALE_MAP; canvas.drawRoundRect(mapRect, 15f * uiScale, 15f * uiScale, bgPaint)
        titlePaint.typeface = fontWarrior; titlePaint.textSize = 28f * uiScale; titlePaint.color = Color.CYAN; titlePaint.textAlign = Paint.Align.LEFT
        canvas.drawText("MUSIC PLAYER", mapRect.left + 35f * uiScale, mapRect.top + 50f * uiScale, titlePaint)
        infoPaint.color = Color.WHITE; infoPaint.textSize = 44f * uiScale
        val trackName = musicPlayer?.getCurrentTrackName() ?: "NO MEDIA"
        var displayTrack = trackName
        if (infoPaint.measureText(displayTrack) > mapRect.width() - 70f * uiScale) {
             while (infoPaint.measureText(displayTrack + "...") > mapRect.width() - 70f * uiScale && displayTrack.isNotEmpty()) { displayTrack = displayTrack.dropLast(1) }
             displayTrack += "..."
        }
        canvas.drawText(displayTrack, mapRect.left + 35f * uiScale, mapRect.top + 115f * uiScale, infoPaint)
        drawPlayerControls(canvas, state, musicPlayer, uiScale)
        val barCount = 20; val barW = (mapRect.width() - 70f * uiScale) / barCount
        for (i in 0 until barCount) {
            val h = (10f + (Math.random() * 35f).toFloat()) * uiScale
            canvas.drawRect(mapRect.left + 35f * uiScale + i * barW, mapRect.bottom - 20f * uiScale - h, mapRect.left + 35f * uiScale + (i + 1) * barW - 4f * uiScale, mapRect.bottom - 20f * uiScale, visualizerPaint)
        }
    }

    private fun drawPlayerControls(canvas: Canvas, state: GameState, musicPlayer: MusicPlayer?, uiScale: Float) {
        canvas.drawRoundRect(btnPrevRect, 8f * uiScale, 8f * uiScale, btnPaint)
        canvas.drawRoundRect(btnPlayRect, 8f * uiScale, 8f * uiScale, btnPaint)
        canvas.drawRoundRect(btnNextRect, 8f * uiScale, 8f * uiScale, btnPaint)
        val isStationValid = state.navMode != GameState.NavMode.RADIO || RadioDatabase.getStationByFrequency(state.radioBand, state.radioFrequency) != null
        drawControlIcon(canvas, btnPrevRect, "prev", uiScale)
        drawControlIcon(canvas, btnPlayRect, if (musicPlayer?.isPlaying == true && isStationValid) "pause" else "play", uiScale)
        drawControlIcon(canvas, btnNextRect, "next", uiScale)
    }

    private fun drawControlIcon(canvas: Canvas, rect: RectF, type: String, uiScale: Float) {
        val size = 24f * uiScale; val cx = rect.centerX(); val cy = rect.centerY(); iconPath.reset()
        when (type) {
            "play" -> { iconPath.moveTo(cx - size * 0.4f, cy - size * 0.6f); iconPath.lineTo(cx + size * 0.6f, cy); iconPath.lineTo(cx - size * 0.4f, cy + size * 0.6f); iconPath.close() }
            "pause" -> { val w = size * 0.3f; val h = size * 1.2f; val gap = size * 0.2f; canvas.drawRect(cx - gap - w, cy - h / 2f, cx - gap, cy + h / 2f, iconPaint); canvas.drawRect(cx + gap, cy - h / 2f, cx + gap + w, cy + h / 2f, iconPaint); return }
            "next" -> { iconPath.moveTo(cx - size * 0.6f, cy - size * 0.5f); iconPath.lineTo(cx, cy); iconPath.lineTo(cx - size * 0.6f, cy + size * 0.5f); iconPath.close(); iconPath.moveTo(cx, cy - size * 0.5f); iconPath.lineTo(cx + size * 0.6f, cy); iconPath.lineTo(cx, cy + size * 0.5f); iconPath.close() }
            "prev" -> { iconPath.moveTo(cx + size * 0.6f, cy - size * 0.5f); iconPath.lineTo(cx, cy); iconPath.lineTo(cx + size * 0.6f, cy + size * 0.5f); iconPath.close(); iconPath.moveTo(cx, cy - size * 0.5f); iconPath.lineTo(cx - size * 0.6f, cy); iconPath.lineTo(cx, cy + size * 0.5f); iconPath.close() }
        }
        canvas.drawPath(iconPath, iconPaint)
    }

    private fun drawMiniMap(canvas: Canvas, state: GameState, mapRect: RectF, font: Typeface?) {
        val uiScale = GameSettings.UI_SCALE_MAP; canvas.drawRoundRect(mapRect, 15f * uiScale, 15f * uiScale, bgPaint)
        calendar.timeInMillis = System.currentTimeMillis()
        val minute = calendar.get(Calendar.MINUTE)
        if (minute != lastMinute) { lastMinute = minute; timeStrCache = "%02d:%02d".format(calendar.get(Calendar.HOUR_OF_DAY), minute) }
        navTextPaint.typeface = font; navTextPaint.textSize = 28f * uiScale; navTextPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText(timeStrCache, mapRect.right - 20f * uiScale, mapRect.top + 45f * uiScale, navTextPaint)
        
        // 次の施設までの距離を表示
        val playerKm = state.playerDistance / 1000f
        val nextObject = CourseManager.getRoadObjects().find { it.posKm > playerKm }
        if (nextObject != null) {
            val dist = nextObject.posKm - playerKm
            navTextPaint.textAlign = Paint.Align.LEFT
            val nextInfo = "次: ${nextObject.label} %.1f km".format(dist)
            canvas.drawText(nextInfo, mapRect.left + 20f * uiScale, mapRect.bottom - 50f * uiScale, navTextPaint)
        }

        val totalDist = CourseManager.getTotalDistance()
        val remainingDist = (totalDist - state.playerDistance).coerceAtLeast(0f)
        navTextPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("残り %.2f km".format(remainingDist / 1000f), mapRect.left + 20f * uiScale, mapRect.bottom - 20f * uiScale, navTextPaint)

        canvas.save(); canvas.clipRect(mapRect)
        val startSegment = (state.playerDistance / GameSettings.SEGMENT_LENGTH).toInt(); val totalSegments = CourseManager.getTotalSegments(); val mapW = mapRect.width(); val mapH = mapRect.height(); val mapX = mapRect.left; val mapY = mapRect.top
        val mx: Float; val my: Float; val pad = 40f * uiScale
        if (state.isMapLongRange) {
            if (cachedCourseSegments != totalSegments) updateCourseCache(totalSegments)
            val contentW = cachedMaxX - cachedMinX; val scaleX = (mapW - pad * 2) / maxOf(1f, contentW); val scaleY = (mapH - pad * 2) / totalSegments.toFloat(); val scale = minOf(scaleX, scaleY); val offsetX = mapX + pad + ((mapW - pad * 2) - contentW * scale) / 2f - cachedMinX * scale; val drawBottomY = mapY + mapH - pad - ((mapH - pad * 2) - totalSegments * scale) / 2f; val playerRelX = if (startSegment in coursePointsX.indices) coursePointsX[startSegment] else 0f
            mx = offsetX + playerRelX * scale; my = drawBottomY - startSegment * scale
            passedPath.reset(); passedPath.moveTo(offsetX, drawBottomY)
            for (i in 1..startSegment) if (i % 20 == 0 || i == startSegment) passedPath.lineTo(offsetX + coursePointsX[i] * scale, drawBottomY - i * scale)
            passedRoadPaint.strokeWidth = 6f * uiScale; canvas.drawPath(passedPath, passedRoadPaint)
            miniMapPath.reset()
            if (startSegment < totalSegments) {
                miniMapPath.moveTo(mx, my); for (i in (startSegment + 1)..totalSegments) if (i % 20 == 0 || i == totalSegments) miniMapPath.lineTo(offsetX + coursePointsX[i] * scale, drawBottomY - i * scale)
                roadPaint.strokeWidth = 6f * uiScale; canvas.drawPath(miniMapPath, roadPaint)
            }
            canvas.drawCircle(offsetX, drawBottomY, 12f * uiScale, pointPaint); val gx = offsetX + coursePointsX[totalSegments] * scale; val gy = drawBottomY - totalSegments * scale; drawGoalFlag(canvas, gx, gy, uiScale)
        } else {
            mx = mapX + mapW / 2f; my = mapY + mapH * 0.6f; val scale = uiScale * 0.5f; miniMapPath.reset(); miniMapPath.moveTo(mx, my)
            var fDX = 0f; var fX = 0f; for (i in 1..800) { fDX += CourseManager.getCurve((startSegment + i).toFloat()); fX += fDX; if (i % 15 == 0) miniMapPath.lineTo(mx + fX * scale, my - i * scale) }
            miniMapPath.moveTo(mx, my); var bDX = 0f; var bX = 0f; var startPointX = Float.NaN; var startPointY = Float.NaN; for (i in 1..600) { val target = startSegment - i; if (target < -1) break; bDX -= CourseManager.getCurve((target + 1).toFloat()); bX -= bDX; if (i % 15 == 0) miniMapPath.lineTo(mx + bX * scale, my + i * scale); if (target == 0) { startPointX = mx + bX * scale; startPointY = my + i * scale } }
            roadPaint.strokeWidth = 10f * uiScale; canvas.drawPath(miniMapPath, roadPaint); if (!startPointX.isNaN()) canvas.drawCircle(startPointX, startPointY, 18f * uiScale, pointPaint)
        }
        canvas.restore()
    }

    private fun drawSideButtons(canvas: Canvas, font: Typeface?, state: GameState) {
        val uiScale = GameSettings.UI_SCALE_MAP
        canvas.drawRoundRect(btnHomeRect, 10f * uiScale, 10f * uiScale, if (state.navMode == GameState.NavMode.HOME) activeBtnPaint else btnPaint)
        canvas.drawRoundRect(btnMapRect, 10f * uiScale, 10f * uiScale, if (state.navMode == GameState.NavMode.MAP) activeBtnPaint else btnPaint)
        canvas.drawRoundRect(btnMusicRect, 10f * uiScale, 10f * uiScale, if (state.navMode == GameState.NavMode.MUSIC) activeBtnPaint else btnPaint)
        canvas.drawRoundRect(btnRadioRect, 10f * uiScale, 10f * uiScale, if (state.navMode == GameState.NavMode.RADIO) activeBtnPaint else btnPaint)
        btnTextPaint.typeface = font; btnTextPaint.textSize = 24f * uiScale
        canvas.drawText("HOME", btnHomeRect.centerX(), btnHomeRect.centerY() + 10f * uiScale, btnTextPaint)
        canvas.drawText("MAP", btnMapRect.centerX(), btnMapRect.centerY() + 10f * uiScale, btnTextPaint)
        canvas.drawText("MUS", btnMusicRect.centerX(), btnMusicRect.centerY() + 10f * uiScale, btnTextPaint)
        canvas.drawText("RAD", btnRadioRect.centerX(), btnRadioRect.centerY() + 10f * uiScale, btnTextPaint)
    }

    private fun updateCourseCache(totalSegments: Int) {
        coursePointsX.clear(); coursePointsX.add(0f); var scDX = 0f; var scX = 0f; var minX = 0f; var maxX = 0f; for (i in 1..totalSegments) { scDX += CourseManager.getCurve(i.toFloat()); scX += scDX; coursePointsX.add(scX); minX = minOf(minX, scX); maxX = maxOf(maxX, scX) }; cachedCourseSegments = totalSegments; cachedMinX = minX; cachedMaxX = maxX
    }

    private fun drawGoalFlag(canvas: Canvas, x: Float, y: Float, uiScale: Float) {
        val size = 25f * uiScale; flagPath.reset(); flagPath.moveTo(x, y); flagPath.lineTo(x, y - size * 2); flagPath.lineTo(x + size, y - size * 1.5f); flagPath.lineTo(x, y - size); flagPath.close(); canvas.drawPath(flagPath, goalPaint); flagStaffPaint.strokeWidth = 2f * uiScale; canvas.drawLine(x, y, x, y - size * 2, flagStaffPaint)
    }

    private fun updateTrianglePath(size: Float) {
        if (size == lastArrowSize) return
        lastArrowSize = size; arrowPath.reset(); arrowPath.moveTo(0f, -size); arrowPath.lineTo(-size * 0.8f, size * 0.8f); arrowPath.lineTo(size * 0.8f, size * 0.8f); arrowPath.close()
    }

    private fun drawCompass(canvas: Canvas, state: GameState, compassRect: RectF, fontWarrior: Typeface?) {
        val uiScale = GameSettings.UI_SCALE_COMPASS; canvas.drawRoundRect(compassRect, 10f * uiScale, 10f * uiScale, compassBgPaint); canvas.drawRoundRect(compassRect, 10f * uiScale, 10f * uiScale, compassBorderPaint); compassTextPaint.typeface = fontWarrior; compassTextPaint.textSize = 28f * uiScale; val headingDeg = state.playerHeadingDegrees; val cx = compassRect.centerX(); canvas.save(); canvas.clipRect(compassRect)
        for (dir in directions) { var diff = dir.second - headingDeg; while (diff < -180) diff += 360; while (diff > 180) diff -= 360; if (Math.abs(diff) < 90f) { val x = cx + (diff / 90f) * (compassRect.width() / 2f); canvas.drawText(dir.first, x - compassTextPaint.measureText(dir.first) / 2f, compassRect.centerY() + 12f * uiScale, compassTextPaint); canvas.drawLine(x, compassRect.top, x, compassRect.top + 10f * uiScale, compassTextPaint) } }
        canvas.restore()
    }

    fun handleTouch(x: Float, y: Float, state: GameState, isTapEvent: Boolean, context: Context, musicPlayer: MusicPlayer?): Boolean {
        updateButtonRects(HDU.mapRect, state)
        val isAnyBtnHit = btnHomeRect.contains(x, y) || btnMapRect.contains(x, y) || btnMusicRect.contains(x, y) || btnRadioRect.contains(x, y) || 
                         (state.navMode != GameState.NavMode.MAP && state.navMode != GameState.NavMode.HOME && (btnPlayRect.contains(x, y) || btnPrevRect.contains(x, y) || btnNextRect.contains(x, y) || btnBandRect.contains(x, y))) ||
                         (state.navMode == GameState.NavMode.HOME && (btnSettingsRect.contains(x, y) || btnLightRect.contains(x, y)))
        if (!isTapEvent) {
            if (state.navMode == GameState.NavMode.RADIO) {
                if (btnPrevRect.contains(x, y)) { state.radioBtnDir = -1; state.radioBtnDownStartTime = System.currentTimeMillis(); state.lastFreqChangeTime = state.radioBtnDownStartTime; return true }
                else if (btnNextRect.contains(x, y)) { state.radioBtnDir = 1; state.radioBtnDownStartTime = System.currentTimeMillis(); state.lastFreqChangeTime = state.radioBtnDownStartTime; return true }
            }
        } else {
            val wasLongPress = state.radioBtnDownStartTime > 0 && (System.currentTimeMillis() - state.radioBtnDownStartTime > 500)
            state.radioBtnDir = 0; state.radioBtnDownStartTime = 0
            if (btnHomeRect.contains(x, y)) { state.navMode = GameState.NavMode.HOME; return true }
            if (btnMapRect.contains(x, y)) { state.navMode = GameState.NavMode.MAP; return true }
            if (btnMusicRect.contains(x, y)) { state.navMode = GameState.NavMode.MUSIC; return true }
            if (btnRadioRect.contains(x, y)) { state.navMode = GameState.NavMode.RADIO; return true }
            if (state.navMode == GameState.NavMode.HOME) {
                if (btnSettingsRect.contains(x, y)) { state.isSettingsOpen = true; return true }
                if (btnLightRect.contains(x, y)) { 
                    state.isHighBeam = !state.isHighBeam
                    return true 
                }
            }
            if (state.navMode == GameState.NavMode.MUSIC) {
                if (btnPlayRect.contains(x, y)) { musicPlayer?.togglePlayPause(); return true }
                if (btnPrevRect.contains(x, y)) { musicPlayer?.skipToPrevious(); return true }
                if (btnNextRect.contains(x, y)) { musicPlayer?.skipToNext(); return true }
            }
            if (state.navMode == GameState.NavMode.RADIO) {
                if (btnBandRect.contains(x, y)) { 
                    state.radioBand = if (state.radioBand == "FM") "PM" else "FM"
                    GameSettings.saveRadioSettings(context, state.radioBand, state.radioFrequency)
                    musicPlayer?.stop(); return true 
                }
                if (btnPlayRect.contains(x, y)) {
                    val station = RadioDatabase.getStationByFrequency(state.radioBand, state.radioFrequency)
                    if (station != null) playStation(context, station, musicPlayer, state.radioBand)
                    else musicPlayer?.stop(); return true
                }
                if (!wasLongPress) {
                    if (btnPrevRect.contains(x, y)) { 
                        state.radioFrequency = (state.radioFrequency - 0.1f).coerceIn(state.RADIO_MIN, state.RADIO_MAX)
                        GameSettings.saveRadioSettings(context, state.radioBand, state.radioFrequency)
                        musicPlayer?.stop(); return true 
                    }
                    else if (btnNextRect.contains(x, y)) { 
                        state.radioFrequency = (state.radioFrequency + 0.1f).coerceAtMost(state.RADIO_MAX)
                        GameSettings.saveRadioSettings(context, state.radioBand, state.radioFrequency)
                        musicPlayer?.stop(); return true 
                    }
                }
            }
        }
        return isAnyBtnHit
    }
}
