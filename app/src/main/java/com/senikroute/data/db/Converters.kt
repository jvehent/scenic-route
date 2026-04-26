package com.senikroute.data.db

import androidx.room.TypeConverter
import com.senikroute.data.model.BoundingBox
import com.senikroute.data.model.VehicleReqs
import com.senikroute.data.model.VehicleSummary
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

class Converters {

    @TypeConverter
    fun boundingBoxToString(b: BoundingBox?): String? =
        b?.let { json.encodeToString(BoundingBox.serializer(), it) }

    @TypeConverter
    fun stringToBoundingBox(s: String?): BoundingBox? =
        s?.let { json.decodeFromString(BoundingBox.serializer(), it) }

    @TypeConverter
    fun vehicleReqsToString(v: VehicleReqs?): String? =
        v?.let { json.encodeToString(VehicleReqs.serializer(), it) }

    @TypeConverter
    fun stringToVehicleReqs(s: String?): VehicleReqs? =
        s?.let { json.decodeFromString(VehicleReqs.serializer(), it) }

    @TypeConverter
    fun vehicleSummaryToString(v: VehicleSummary?): String? =
        v?.let { json.encodeToString(VehicleSummary.serializer(), it) }

    @TypeConverter
    fun stringToVehicleSummary(s: String?): VehicleSummary? =
        s?.let { json.decodeFromString(VehicleSummary.serializer(), it) }

    @TypeConverter
    fun stringListToString(list: List<String>): String =
        json.encodeToString(ListSerializer(String.serializer()), list)

    @TypeConverter
    fun stringToStringList(s: String): List<String> =
        if (s.isBlank()) emptyList()
        else json.decodeFromString(ListSerializer(String.serializer()), s)
}
