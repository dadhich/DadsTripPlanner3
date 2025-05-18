package com.example.dadstripplanner3

data class TripOption(
    val duration: String,
    val departureTime: String,
    val departureLocation: String,
    val status: String, // e.g., "On time", "10:42 running 2 mins late", "Real-time data unavailable"
    val arrivalTime: String,
    val arrivalLocation: String,
    val transportModes: String, // e.g., "Train T1, Bus 575"
    val isLate: Boolean = false, // To help decide status color
    val isRealTimeDataUnavailable: Boolean = false // Another status indicator
)