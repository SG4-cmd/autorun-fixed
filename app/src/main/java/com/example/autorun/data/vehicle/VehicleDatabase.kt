package com.example.autorun.data.vehicle

import android.content.Context
import com.example.autorun.R
import com.example.autorun.data.parts.PartsDatabase
import org.xmlpull.v1.XmlPullParser

/**
 * 【VehicleDatabase: 車両データベース】
 */
object VehicleDatabase {

    var vehicles = mutableListOf<VehicleSpecs>()
    var selectedVehicleIndex = 0

    var currentEquipment: Equipment? = null

    fun init(context: Context) {
        if (vehicles.isNotEmpty()) return

        val parser = context.resources.getXml(R.xml.vehicles)
        var eventType = parser.eventType

        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG && parser.name == "vehicle") {
                val imageName = parser.getAttributeValue(null, "image")
                val imageResId = context.resources.getIdentifier(imageName, "drawable", context.packageName)

                vehicles.add(VehicleSpecs(
                    id = parser.getAttributeValue(null, "id"),
                    name = parser.getAttributeValue(null, "name"),
                    imageResId = imageResId,
                    modelPath = parser.getAttributeValue(null, "modelPath") ?: "car.glb",
                    engineName = parser.getAttributeValue(null, "engineName"),
                    driveType = parser.getAttributeValue(null, "driveType"),
                    isRHD = parser.getAttributeValue(null, "isRHD").toBoolean(),
                    seriesName = parser.getAttributeValue(null, "seriesName"),
                    modelGeneration = parser.getAttributeValue(null, "modelGeneration").toInt(),
                    displacementCc = parser.getAttributeValue(null, "displacementCc").toInt(),
                    isDOHC = parser.getAttributeValue(null, "isDOHC").toBoolean(),
                    hasEFI = parser.getAttributeValue(null, "hasEFI").toBoolean(),
                    hasTurbo = parser.getAttributeValue(null, "hasTurbo").toBoolean(),
                    turboBoostRpm = parser.getAttributeValue(null, "turboBoostRpm").toFloat(),
                    turboBoostMultiplier = parser.getAttributeValue(null, "turboBoostMultiplier").toFloat(),
                    weightKg = parser.getAttributeValue(null, "weightKg").toFloat(),
                    widthM = parser.getAttributeValue(null, "widthM").toFloat(),
                    heightM = parser.getAttributeValue(null, "heightM").toFloat(),
                    lengthM = parser.getAttributeValue(null, "lengthM").toFloat(),
                    maxPowerHp = parser.getAttributeValue(null, "maxPowerHp").toFloat(),
                    maxTorqueNm = parser.getAttributeValue(null, "maxTorqueNm").toFloat(),
                    torquePeakRPM = parser.getAttributeValue(null, "torquePeakRPM").toFloat(),
                    gearRatios = parser.getAttributeValue(null, "gearRatios").split(",").map { it.toFloat() }.toTypedArray(),
                    finalRatio = parser.getAttributeValue(null, "finalRatio").toFloat(),
                    minRedzone = parser.getAttributeValue(null, "minRedzone").toFloat(),
                    maxRedzone = parser.getAttributeValue(null, "maxRedzone").toFloat(),
                    tireDiameterM = parser.getAttributeValue(null, "tireDiameterM").toFloat(),
                    tireWidthM = parser.getAttributeValue(null, "tireWidthM").toFloat(),
                    dragCd = parser.getAttributeValue(null, "dragCd").toFloat(),
                    frontalArea = parser.getAttributeValue(null, "frontalArea").toFloat(),
                    centrifugalForceMultiplier = parser.getAttributeValue(null, "centrifugalForceMultiplier").toFloat(),
                    tireGripMultiplier = parser.getAttributeValue(null, "tireGripMultiplier").toFloat()
                ))
            }
            eventType = parser.next()
        }

        currentEquipment = Equipment(
            selectedTire = PartsDatabase.tires[0],
            selectedEngine = PartsDatabase.engines[0],
            selectedMuffler = PartsDatabase.mufflers[0],
            selectedSuspension = PartsDatabase.suspensions[0],
            selectedTransmission = PartsDatabase.transmissions[0]
        )
    }

    fun getSelectedVehicle(): VehicleSpecs {
        val baseVehicle = vehicles[selectedVehicleIndex]
        return currentEquipment?.let { applyTuning(baseVehicle, it) } ?: baseVehicle
    }

    private fun applyTuning(base: VehicleSpecs, equip: Equipment): VehicleSpecs {
        val tunedPower = equip.selectedEngine.maxPowerHp * equip.selectedMuffler.powerMultiplier
        val baseBodyWeight = base.weightKg - PartsDatabase.engines[0].weightKg
        val totalWeight = baseBodyWeight + equip.selectedEngine.weightKg - equip.selectedMuffler.weightReductionKg
        val tunedHandling = base.centrifugalForceMultiplier * equip.selectedSuspension.handlingImprovement

        return base.copy(
            maxPowerHp = tunedPower,
            weightKg = if (totalWeight > 500f) totalWeight else 500f,
            tireDiameterM = equip.selectedTire.tireDiameterM,
            tireWidthM = base.tireWidthM,
            gearRatios = equip.selectedTransmission.gearRatios,
            finalRatio = equip.selectedTransmission.finalRatio,
            centrifugalForceMultiplier = tunedHandling,
            tireGripMultiplier = base.tireGripMultiplier * equip.selectedTire.gripMultiplier
        )
    }

    fun setTire(index: Int) {
        if (index in PartsDatabase.tires.indices) {
            currentEquipment?.selectedTire = PartsDatabase.tires[index]
        }
    }

    fun setEngine(index: Int) {
        if (index in PartsDatabase.engines.indices) {
            currentEquipment?.selectedEngine = PartsDatabase.engines[index]
        }
    }

    fun setMuffler(index: Int) {
        if (index in PartsDatabase.mufflers.indices) {
            currentEquipment?.selectedMuffler = PartsDatabase.mufflers[index]
        }
    }

    fun setSuspension(index: Int) {
        if (index in PartsDatabase.suspensions.indices) {
            currentEquipment?.selectedSuspension = PartsDatabase.suspensions[index]
        }
    }

    fun setTransmission(index: Int) {
        if (index in PartsDatabase.transmissions.indices) {
            currentEquipment?.selectedTransmission = PartsDatabase.transmissions[index]
        }
    }
}
