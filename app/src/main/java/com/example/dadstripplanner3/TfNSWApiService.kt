package com.example.dadstripplanner3 // Your package name

import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

interface TfNSWApiService {

    @GET("v1/tp/trip")
    fun planTrip(
        @Header("Authorization") authorization: String,

        @Query("outputFormat") outputFormat: String = "rapidJSON",
        @Query("coordOutputFormat") coordOutputFormat: String = "EPSG:4326",
        @Query("depArrMacro") depArrMacro: String = "dep",
        @Query("itdDate") date: String, // YYYYMMDD
        @Query("itdTime") time: String, // HHMM

        @Query("type_origin") originType: String,
        @Query("name_origin") originValue: String, // For coord: "LONGITUDE:LATITUDE:EPSG:4326"

        @Query("type_destination") destinationType: String,
        @Query("name_destination") destinationValue: String,

        @Query("calcNumberOfTrips") calcNumberOfTrips: Int = 5,
        // --- ENSURE THIS PARAMETER IS PRESENT AND CORRECT ---
        @Query("TfNSWTR") tfNSWTR: String = "true"
    ): Call<TripResponse>

    // --- NEW: Method for StopFinder API ---
    /**
     * Finds stops, addresses, and points of interest based on a search term.
     * Useful for autocomplete/auto-suggest functionality.
     *
     * Base URL: https://api.transport.nsw.gov.au/
     * Endpoint: /v1/tp/stop_finder
     * Method: GET
     * Authentication: Header "Authorization: apikey YOUR_KEY_HERE"
     */
    @GET("v1/tp/stop_finder") // As per Swagger basePath + endpoint
    fun findLocations(
        @Header("Authorization") authorization: String, // e.g., "apikey YOUR_API_KEY"
        @Query("name_sf") searchTerm: String, // The user's typed input
        @Query("type_sf") searchType: String = "any", // "any", "stop", "address", "poi", "coord" - "any" is a good default for general search [cite: 637, 638]
        @Query("outputFormat") outputFormat: String = "rapidJSON", // Required [cite: 632]
        @Query("coordOutputFormat") coordOutputFormat: String = "EPSG:4326", // Required [cite: 643]
        @Query("TfNSWSF") tfNSWSF: String = "true", // Recommended for web-like behaviour, default is 'true' [cite: 645]
        @Query("version") version: String = "10.2.1.42" // Optional: specifies API version, default from Swagger [cite: 647, 517]
    ): Call<StopFinderResponse> // Expecting the StopFinderResponse data class
}