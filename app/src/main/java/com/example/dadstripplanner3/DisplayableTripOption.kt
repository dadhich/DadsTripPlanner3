package com.example.dadstripplanner3 // Your package name

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class DisplayableTripOption(
    val overallDurationInMinutes: Int,
    val departureTimeFormatted: String, // e.g., "10:44 AM"
    val arrivalTimeFormatted: String,   // e.g., "10:53 AM"
    val effectiveOriginName: String,    // Name of the first significant point
    val effectiveDestinationName: String, // Name of the last significant point
    val transportModesSummary: String,  // e.g., "Walk, Bus 575, Train T1"
    val departureStatus: String,        // e.g., "On time", "2 min late", "Data unavailable"
    val isLate: Boolean,
    val isRealTimeDataUnavailable: Boolean,
    val interchanges: Int // Number of changes between public transport modes
    // You can add more fields here if your UI needs them, e.g., total distance.
) : Parcelable