package com.example.autorun.data.radio

import android.content.Context
import android.util.Xml
import com.example.autorun.R
import org.xmlpull.v1.XmlPullParser

data class RadioStation(
    val id: String,
    val name: String,
    val band: String, // FM or PM
    val frequency: Float,
    val region: String,
    val type: String,
    val resourceName: String?,
    val streamUrl: String?
)

object RadioDatabase {
    private val stations = mutableListOf<RadioStation>()

    fun init(context: Context) {
        stations.clear()
        val parser = context.resources.getXml(R.xml.radio_stations)
        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG && parser.name == "station") {
                val id = parser.getAttributeValue(null, "id") ?: ""
                val name = parser.getAttributeValue(null, "name") ?: ""
                val band = parser.getAttributeValue(null, "band") ?: "FM"
                val frequency = parser.getAttributeValue(null, "frequency")?.toFloatOrNull() ?: 0f
                val region = parser.getAttributeValue(null, "region") ?: ""
                val type = parser.getAttributeValue(null, "type") ?: ""
                val resourceName = parser.getAttributeValue(null, "resource_name")
                val streamUrl = parser.getAttributeValue(null, "stream_url")
                stations.add(RadioStation(id, name, band, frequency, region, type, resourceName, streamUrl))
            }
            eventType = parser.next()
        }
    }

    fun getStationByFrequency(band: String, frequency: Float): RadioStation? {
        // バンドと周波数の両方が一致する局を検索
        return stations.find { it.band == band && Math.abs(it.frequency - frequency) < 0.05f }
    }
}
