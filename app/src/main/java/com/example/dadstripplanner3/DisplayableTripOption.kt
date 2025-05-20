package com.example.dadstripplanner3

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
    val transportModesSummary: String, // e.g., "Walk • Bus 575 • Train T1"
    val primaryPublicTransportInfo: String?, // NEW: e.g., "Bus 575" or "Train T1"
    val interchanges: Int
) : Parcelable