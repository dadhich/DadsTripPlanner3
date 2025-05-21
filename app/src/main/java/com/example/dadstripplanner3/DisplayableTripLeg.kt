package com.example.dadstripplanner3 // Your package name

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class DisplayableTripLeg(
    // Core Info
    val modeEmoji: String, // e.g., 🚶, 🚌, 🚆, ⛴️
    val modeName: String, // e.g., "Walk", "Bus 575", "Train T1"
    val durationMinutes: Int, // Duration of this leg in minutes

    // Origin of this leg
    val originName: String,
    val originTimeFormatted: String, // Formatted departure/start time for this leg

    // Destination of this leg
    val destinationName: String,
    val destinationTimeFormatted: String, // Formatted arrival/end time for this leg

    // Specific to Public Transport legs
    val lineDestination: String?, // Headsign of the PT service, e.g., "Hornsby"
    val stopSequenceCount: Int = 0, // Number of intermediate stops (if applicable)
    val realTimeStatus: String?, // e.g., "On time", "5 min late", "Scheduled"
    val isRealTime: Boolean,

    // Specific to Walk legs
    val distanceMeters: Int?,
    val pathDescriptions: List<String>? // Simplified list of textual walking directions
    // We can add more fields later if needed, e.g., platform info, specific stop IDs, etc.
) : Parcelable