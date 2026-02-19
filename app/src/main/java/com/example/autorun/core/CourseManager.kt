package com.example.autorun.core

import android.content.Context
import com.example.autorun.R
import com.example.autorun.config.GameSettings
import org.xmlpull.v1.XmlPullParser
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * 【CourseManager: コースデータの管理】
 * XMLからセクションタイプ（STRAIGHT/CURVE/UP等）を読み込み、レンダラーに提供します。
 */
object CourseManager {

    data class PreparedSection(
        val type: String,
        val length: Int, // segments
        val lanes: Int,
        val curve: Float,
        val startHeight: Float,
        val endHeight: Float,
        val startIndex: Int
    )

    data class RoadObject(
        val type: String,
        val posKm: Float,
        val label: String,
        val lengthM: Float
    )

    private var roadSections: List<PreparedSection> = emptyList()
    private var roadObjects: List<RoadObject> = emptyList()
    private var lastSectionCache: PreparedSection? = null
    
    private var worldX: FloatArray = FloatArray(0)
    private var worldZ: FloatArray = FloatArray(0)
    private var worldHeading: FloatArray = FloatArray(0)

    var courseName: String = "Unknown Course"
        private set

    fun init(context: Context) {
        if (roadSections.isNotEmpty()) return
        val sections = mutableListOf<PreparedSection>()
        val objects = mutableListOf<RoadObject>()
        val parser = context.resources.getXml(R.xml.course_tohoku)
        var currentIdx = 0
        var currentH = 0f
        try {
            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG) {
                    when (parser.name) {
                        "course" -> courseName = parser.getAttributeValue(null, "name") ?: "Unknown Course"
                        "section" -> {
                            val type = parser.getAttributeValue(null, "type") ?: "STRAIGHT"
                            val lengthM = parser.getAttributeValue(null, "length_m").toFloat()
                            val lanes = parser.getAttributeValue(null, "lanes").toInt()
                            val curve = parser.getAttributeValue(null, "curve").toFloat()
                            val slope = parser.getAttributeValue(null, "slope").toFloat()
                            val segments = (lengthM / GameSettings.SEGMENT_LENGTH).toInt()
                            val endH = currentH + (slope * lengthM)
                            sections.add(PreparedSection(type, segments, lanes, curve, currentH, endH, currentIdx))
                            currentH = endH
                            currentIdx += segments
                        }
                        "object" -> objects.add(RoadObject(parser.getAttributeValue(null, "type"), parser.getAttributeValue(null, "pos_km").toFloat(), parser.getAttributeValue(null, "label"), parser.getAttributeValue(null, "length_m")?.toFloat() ?: 0f))
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) { e.printStackTrace() } finally { parser.close() }
        roadSections = sections; roadObjects = objects
        
        calculateWorldCoordinates()
    }

    private fun calculateWorldCoordinates() {
        val totalSegs = getTotalSegments()
        worldX = FloatArray(totalSegs + 1)
        worldZ = FloatArray(totalSegs + 1)
        worldHeading = FloatArray(totalSegs + 1)
        
        var curX = 0f; var curZ = 0f; var curH = 0f
        val segLen = GameSettings.SEGMENT_LENGTH
        
        for (i in 0 until totalSegs) {
            worldX[i] = curX; worldZ[i] = curZ; worldHeading[i] = curH
            val curve = getCurve(i.toFloat())
            curH += curve * segLen
            curX += sin(curH.toDouble()).toFloat() * segLen
            curZ += cos(curH.toDouble()).toFloat() * segLen
        }
        worldX[totalSegs] = curX; worldZ[totalSegs] = curZ; worldHeading[totalSegs] = curH
    }

    fun getSection(index: Int): PreparedSection {
        val cache = lastSectionCache
        if (cache != null && index >= cache.startIndex && index < cache.startIndex + cache.length) return cache
        var low = 0; var high = roadSections.size - 1
        while (low <= high) {
            val mid = (low + high) / 2; val s = roadSections[mid]
            if (index < s.startIndex) high = mid - 1
            else if (index >= s.startIndex + s.length) low = mid + 1
            else { lastSectionCache = s; return s }
        }
        return if (roadSections.isNotEmpty()) roadSections.last() else PreparedSection("STRAIGHT", 100, 2, 0f, 0f, 0f, 0)
    }

    fun getSectionType(index: Float): String = getSection(index.toInt()).type
    fun getTotalSegments(): Int = roadSections.lastOrNull()?.let { it.startIndex + it.length } ?: 0
    fun getTotalDistance(): Float = getTotalSegments() * GameSettings.SEGMENT_LENGTH
    fun getLanes(index: Float): Int = getSection(index.toInt()).lanes
    fun getCurve(index: Float): Float {
        val s = getSection(index.toInt()); val t = ((index - s.startIndex) / s.length).coerceIn(0f, 1f)
        val ease = when { t < 0.2f -> t / 0.2f; t > 0.8f -> (1.0f - t) / 0.2f; else -> 1.0f }
        return s.curve * ease
    }
    fun getHeight(index: Float): Float {
        val s = getSection(index.toInt()); val t = ((index - s.startIndex) / s.length).coerceIn(0f, 1f); val smoothT = t * t * (3 - 2 * t)
        return s.startHeight + (s.endHeight - s.startHeight) * smoothT
    }
    fun getCurrentAngle(index: Float): Float {
        val s = getSection(index.toInt()); val t = ((index - s.startIndex) / s.length).coerceIn(0f, 1f); val slopeFactor = 6 * t * (1 - t)
        val currentSlope = (s.endHeight - s.startHeight) / (s.length * GameSettings.SEGMENT_LENGTH) * slopeFactor
        return (atan2(currentSlope, 1f) * 180.0 / PI).toFloat()
    }
    fun getRoadWorldX(index: Float): Float {
        val i = index.toInt().coerceIn(0, worldX.size - 1); val nextI = (i + 1).coerceIn(0, worldX.size - 1); val t = index - i
        return worldX[i] + (worldX[nextI] - worldX[i]) * t
    }
    fun getRoadWorldZ(index: Float): Float {
        val i = index.toInt().coerceIn(0, worldZ.size - 1); val nextI = (i + 1).coerceIn(0, worldZ.size - 1); val t = index - i
        return worldZ[i] + (worldZ[nextI] - worldZ[i]) * t
    }
    fun getRoadWorldHeading(index: Float): Float {
        val i = index.toInt().coerceIn(0, worldHeading.size - 1); val nextI = (i + 1).coerceIn(0, worldHeading.size - 1); val t = index - i
        return worldHeading[i] + (worldHeading[nextI] - worldHeading[i]) * t
    }
    fun getRoadObjects(): List<RoadObject> = roadObjects
}
