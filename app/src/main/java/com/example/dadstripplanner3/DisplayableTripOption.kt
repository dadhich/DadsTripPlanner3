package com.example.dadstripplanner3

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class DisplayableTripOption(
    val overallDurationInMinutes: Int, // We'll keep this for now, might use it elsewhere or remove later
    val departureTimeFormatted: String,
    val arrivalTimeFormatted: String,
    val effectiveOriginName: String,
    val effectiveDestinationName: String,
    val transportModesSummary: String,
    val departureStatus: String,
    val isLate: Boolean,
    val isRealTimeDataUnavailable: Boolean,
    val interchanges: Int,
    val firstLegDepartureEpochMillis: Long // NEW FIELD: To store departure time for "departs in" calculation
) : Parcelable