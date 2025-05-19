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
}