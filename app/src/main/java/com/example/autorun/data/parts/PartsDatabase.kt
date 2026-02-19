package com.example.autorun.data.parts

import android.content.Context
import com.example.autorun.R
import org.xmlpull.v1.XmlPullParser

/**
 * 【PartsDatabase: パーツデータベース】
 * ゲーム内に登場する全ての交換パーツを管理します。
 */
object PartsDatabase {

    var tires = mutableListOf<TireSpecs>()
    var engines = mutableListOf<EngineSpecs>()
    var mufflers = mutableListOf<MufflerSpecs>()
    var suspensions = mutableListOf<SuspensionSpecs>()
    var transmissions = mutableListOf<TransmissionSpecs>()

    fun init(context: Context) {
        if (tires.isNotEmpty()) return // 既に初期化済みの場合はスキップ

        val parser = context.resources.getXml(R.xml.parts)
        var eventType = parser.eventType
        var currentCategory = ""

        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    when (parser.name) {
                        "tires", "engines", "mufflers", "suspensions", "transmissions" -> {
                            currentCategory = parser.name
                        }
                        "item" -> {
                            when (currentCategory) {
                                "tires" -> tires.add(TireSpecs(
                                    id = parser.getAttributeValue(null, "id"),
                                    name = parser.getAttributeValue(null, "name"),
                                    gripMultiplier = parser.getAttributeValue(null, "grip").toFloat(),
                                    tireDiameterM = parser.getAttributeValue(null, "diameter").toFloat(),
                                    price = parser.getAttributeValue(null, "price").toInt()
                                ))
                                "engines" -> engines.add(EngineSpecs(
                                    id = parser.getAttributeValue(null, "id"),
                                    name = parser.getAttributeValue(null, "name"),
                                    maxPowerHp = parser.getAttributeValue(null, "power").toFloat(),
                                    maxTorqueNm = parser.getAttributeValue(null, "torque").toFloat(),
                                    torquePeakRPM = parser.getAttributeValue(null, "peakRpm").toFloat(),
                                    displacementCc = parser.getAttributeValue(null, "displacement").toInt(),
                                    weightKg = parser.getAttributeValue(null, "weight").toFloat(),
                                    price = parser.getAttributeValue(null, "price").toInt()
                                ))
                                "mufflers" -> mufflers.add(MufflerSpecs(
                                    id = parser.getAttributeValue(null, "id"),
                                    name = parser.getAttributeValue(null, "name"),
                                    powerMultiplier = parser.getAttributeValue(null, "powerMult").toFloat(),
                                    weightReductionKg = parser.getAttributeValue(null, "weightReduc").toFloat(),
                                    price = parser.getAttributeValue(null, "price").toInt()
                                ))
                                "suspensions" -> suspensions.add(SuspensionSpecs(
                                    id = parser.getAttributeValue(null, "id"),
                                    name = parser.getAttributeValue(null, "name"),
                                    handlingImprovement = parser.getAttributeValue(null, "improve").toFloat(),
                                    price = parser.getAttributeValue(null, "price").toInt()
                                ))
                                "transmissions" -> transmissions.add(TransmissionSpecs(
                                    id = parser.getAttributeValue(null, "id"),
                                    name = parser.getAttributeValue(null, "name"),
                                    gearRatios = parser.getAttributeValue(null, "ratios").split(",").map { it.toFloat() }.toTypedArray(),
                                    finalRatio = parser.getAttributeValue(null, "final").toFloat(),
                                    price = parser.getAttributeValue(null, "price").toInt()
                                ))
                            }
                        }
                    }
                }
            }
            eventType = parser.next()
        }
    }

    fun getTireIndex(id: String) = tires.indexOfFirst { it.id == id }.coerceAtLeast(0)
    fun getEngineIndex(id: String) = engines.indexOfFirst { it.id == id }.coerceAtLeast(0)
    fun getMufflerIndex(id: String) = mufflers.indexOfFirst { it.id == id }.coerceAtLeast(0)
    fun getSuspensionIndex(id: String) = suspensions.indexOfFirst { it.id == id }.coerceAtLeast(0)
    fun getTransmissionIndex(id: String) = transmissions.indexOfFirst { it.id == id }.coerceAtLeast(0)
}
