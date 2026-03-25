package com.schoolsync.parent.data.model

/**
 * Transport assignment for a student.
 * Path: Schools/{schoolCode}/Operations/Transport/Assignments/{studentId}/
 */
data class TransportInfo(
    val studentId: String = "",
    val routeName: String = "",
    val routeNumber: String = "",
    val busNumber: String = "",
    val driverName: String = "",
    val driverPhone: String = "",
    val conductorName: String = "",
    val conductorPhone: String = "",
    val pickupPoint: String = "",
    val pickupTime: String = "",
    val dropPoint: String = "",
    val dropTime: String = "",
    val vehicleNumber: String = "",
    val rawData: Map<String, Any?> = emptyMap()
) {
    val hasTransport: Boolean get() = routeName.isNotBlank() || busNumber.isNotBlank()

    companion object {
        fun fromMap(studentId: String, data: Map<String, Any?>): TransportInfo {
            return TransportInfo(
                studentId = studentId,
                routeName = (data["route_name"] ?: data["routeName"] ?: data["Route"] ?: "").toString(),
                routeNumber = (data["route_number"] ?: data["routeNumber"] ?: data["route_no"] ?: "").toString(),
                busNumber = (data["bus_number"] ?: data["busNumber"] ?: data["Bus"] ?: "").toString(),
                driverName = (data["driver_name"] ?: data["driverName"] ?: data["Driver"] ?: "").toString(),
                driverPhone = (data["driver_phone"] ?: data["driverPhone"] ?: data["driver_contact"] ?: "").toString(),
                conductorName = (data["conductor_name"] ?: data["conductorName"] ?: data["Conductor"] ?: "").toString(),
                conductorPhone = (data["conductor_phone"] ?: data["conductorPhone"] ?: data["conductor_contact"] ?: "").toString(),
                pickupPoint = (data["pickup_point"] ?: data["pickupPoint"] ?: data["stop"] ?: data["Stop"] ?: "").toString(),
                pickupTime = (data["pickup_time"] ?: data["pickupTime"] ?: "").toString(),
                dropPoint = (data["drop_point"] ?: data["dropPoint"] ?: "").toString(),
                dropTime = (data["drop_time"] ?: data["dropTime"] ?: "").toString(),
                vehicleNumber = (data["vehicle_number"] ?: data["vehicleNumber"] ?: data["vehicle_no"] ?: "").toString(),
                rawData = data
            )
        }

        fun empty(studentId: String = ""): TransportInfo = TransportInfo(studentId = studentId)
    }
}
