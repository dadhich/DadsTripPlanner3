package com.example.dadstripplanner3 // Your package name

import com.google.gson.annotations.SerializedName

// --- Overall API Response Structure ---
data class TripResponse(
    @SerializedName("version") val version: String?,
    @SerializedName("systemMessages") val systemMessages: List<SystemMessage>?,
    @SerializedName("journeys") val journeys: List<Journey>?
)

data class SystemMessage(
    @SerializedName("type") val type: String?,
    @SerializedName("module") val module: String?,
    @SerializedName("code") val code: Int?,
    @SerializedName("text") val text: String?
)

// --- Journey: Represents one complete trip option ---
data class Journey(
    @SerializedName("rating") val rating: Int?, // Seems to be 0 in the log
    @SerializedName("isAdditional") val isAdditional: Boolean?, // false in the log
    // @SerializedName("interchanges") val interchanges: Int?, // Present in log, could be useful
    @SerializedName("legs") val legs: List<Leg>?
)

// --- Leg: Represents a segment of a journey ---
data class Leg(
    @SerializedName("duration") val durationSeconds: Long?, // API provides in seconds
    @SerializedName("distance") val distanceMetres: Int?,

    @SerializedName("origin") val origin: LegPoint?,
    @SerializedName("destination") val destination: LegPoint?,

    @SerializedName("transportation") val transportation: Transportation?,
    @SerializedName("coords") val polylineCoordinates: List<List<Double>>?, // List of [lat, lon] pairs for the leg's path
    @SerializedName("stopSequence") val stopSequence: List<LegPoint>?, // Sequence of stops for PT legs
    @SerializedName("pathDescriptions") val pathDescriptions: List<PathDescription>?, // For walking directions
    @SerializedName("isRealtimeControlled") val isRealtimeControlled: Boolean?,
    @SerializedName("realtimeStatus") val realtimeStatus: List<String>? // e.g. ["MONITORED"]
    // Add @SerializedName("infos") val infos: List<LegInfoMessage>? if needed
)

// --- LegPoint: Represents an origin or destination point of a leg (stop, address, POI) ---
data class LegPoint(
    @SerializedName("id") val id: String?, // Can be a stop ID, streetID, etc.
    @SerializedName("isGlobalId") val isGlobalId: Boolean? = null, // For stops
    @SerializedName("name") val name: String?,
    @SerializedName("disassembledName") val disassembledName: String?,
    @SerializedName("type") val type: String?, // e.g., "street", "platform", "stop"
    @SerializedName("coord") val coordinates: List<Double>?, // [LATITUDE, LONGITUDE] - important order!
    @SerializedName("parent") val parent: ParentLocation?, // Contains info about parent station/locality

    @SerializedName("departureTimePlanned") val departureTimePlanned: String?, // ISO 8601 DateTime String
    @SerializedName("departureTimeEstimated") val departureTimeEstimated: String?, // ISO 8601 DateTime String
    @SerializedName("arrivalTimePlanned") val arrivalTimePlanned: String?,     // ISO 8601 DateTime String
    @SerializedName("arrivalTimeEstimated") val arrivalTimeEstimated: String?,   // ISO 8601 DateTime String

    @SerializedName("platform") val platform: String?, // Often in properties or directly
    @SerializedName("productClasses") val productClasses: List<Int>?, // e.g., [5, 11] for bus, school bus
    @SerializedName("properties") val properties: LegPointProperties?
)

data class LegPointProperties(
    @SerializedName("WheelchairAccess") val wheelchairAccess: String?, // "true" or "false"
    @SerializedName("occupancy") val occupancy: String?, // e.g. "MANY_SEATS"
    @SerializedName("platform") val platformDetails: String?, // Sometimes platform is here
    @SerializedName("stopId") val stopId: String? // Global stop ID might be here under parent
    // Add other properties as seen in logs/Swagger
)

data class ParentLocation(
    @SerializedName("id") val id: String?,
    @SerializedName("name") val name: String?,
    @SerializedName("disassembledName") val disassembledName: String?,
    @SerializedName("type") val type: String?, // e.g., "locality", "stop"
    @SerializedName("parent") val grandParent: ParentLocation?, // For nested parents
    @SerializedName("properties") val properties: ParentProperties?
)

data class ParentProperties(
    @SerializedName("stopId") val stopId: String?
)

// --- Transportation: Details about the mode of transport for a leg ---
data class Transportation(
    @SerializedName("id") val id: String?, // e.g., "nsw:37575: :H:sj2"
    @SerializedName("name") val name: String?, // Full name, e.g., "Sydney Buses Network 575"
    @SerializedName("disassembledName") val disassembledName: String?, // Short name, e.g., "575"
    @SerializedName("number") val number: String?, // Route number, e.g., "575"
    @SerializedName("description") val description: String?, // e.g., "Macquarie University to Hornsby via Turramurra"
    @SerializedName("product") val product: Product?,
    @SerializedName("operator") val operator: Operator?,
    @SerializedName("destination") val lineDestination: LineDestination?, // Headsign of this specific service run
    @SerializedName("properties") val properties: TransportationProperties?
)

data class Product(
    @SerializedName("id") val idNumeric: Int?, // Internal product ID
    @SerializedName("class") val transportClass: Int?, // e.g., 100 for footpath, 5 for Bus, 1 for Train
    @SerializedName("name") val name: String?, // e.g., "footpath", "Sydney Buses Network"
    @SerializedName("iconId") val iconId: Int?
)

data class Operator(
    @SerializedName("id") val id: String?,
    @SerializedName("name") val name: String? // e.g., "CDC NSW R14"
)

data class LineDestination( // Destination of the transport line/service
    @SerializedName("id") val id: String?,
    @SerializedName("name") val name: String?, // e.g., "Hornsby Station"
    @SerializedName("type") val type: String? // e.g., "stop"
)

data class TransportationProperties(
    @SerializedName("RealtimeTripId") val realtimeTripId: String?,
    @SerializedName("tripCode") val tripCode: Int?, // Or String?
    @SerializedName("globalId") val globalId: String?
    // Add other properties as needed
)

// For walking leg path descriptions
data class PathDescription(
    @SerializedName("turnDirection") val turnDirection: String?,
    @SerializedName("manoeuvre") val manoeuvre: String?,
    @SerializedName("name") val name: String?, // Street name or description
    @SerializedName("duration") val durationSeconds: Long?,
    @SerializedName("distance") val distanceMetres: Int?
    // Other fields like coord, skyDirection, cumDistance, cumDuration can be added if needed
)

// You might also need classes for LegInfoMessage, Fare, etc., if you plan to use that data.
// For now, this covers the core journey structure.