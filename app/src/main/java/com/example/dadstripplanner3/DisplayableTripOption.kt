package com.example.dadstripplanner3 // Your package name

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class DisplayableTripOption(
    // Overall Journey Info
    val overallJourneyDepartureTimeFormatted: String,
    val overallJourneyArrivalTimeFormatted: String,
    val overallJourneyOriginName: String,
    val overallJourneyDestinationName: String,
    val totalTripDurationInMinutes: Int,

    // First Public Transport (PT) Leg Specific Info
    val firstPTLegDepartureStopName: String?,
    val firstPTLegEstimatedDepartureTimeFormatted: String?,
    val firstPTLegScheduledDepartureTimeFormatted: String?,
    val firstPTLegStatusMessage: String?,
    val firstPTLegDepartureEpochMillis: Long,
    val isPTLegLate: Boolean,
    val isPTLegRealTimeDataUnavailable: Boolean,

    // Summary Info
    val transportModesSummary: String,
    val primaryPublicTransportInfo: String?,
    val interchanges: Int,

    // --- NEW FIELD for detailed legs ---
    val legs: List<DisplayableTripLeg> // List of detailed, displayable legs for this trip option

) : Parcelable