package com.example.dadstripplanner3 // Your package name

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize // Useful if you ever need to pass this object via Intents, though not strictly necessary for SharedPreferences with Gson
data class FavoriteTripQuery(
    val favoriteName: String,         // User-defined name for this favorite (e.g., "Home to Work")
    val originDisplayName: String,    // The text shown to the user for the origin (e.g., "My Current Location", or an address/POI name)
    val originType: String,           // Type for the API call (e.g., "coord", "stop", "any")
    val originValue: String,          // Value for the API call (e.g., "lon:lat:EPSG:4326", stop ID, or address string)
    val destinationDisplayName: String, // The text shown to the user for the destination
    val destinationType: String,      // Type for the API call
    val destinationValue: String,     // Value for the API call
    val isOriginCurrentLocation: Boolean = false // Flag to know if origin was "My Current Location"
) : Parcelable