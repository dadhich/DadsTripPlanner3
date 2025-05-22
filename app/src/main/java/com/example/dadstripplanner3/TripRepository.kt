package com.example.dadstripplanner3

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.dadstripplanner3.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

// Data class to hold loaded preference values
data class LastQueryPreferences(
    val useCurrentLocation: Boolean,
    val originDisplay: String?,
    val originType: String?,
    val originValue: String?,
    val destDisplay: String?,
    val destType: String?,
    val destValue: String?,
    val selectedTimeMillis: Long,
    val isDateTimeManuallySet: Boolean,
    val selectedTimeType: String // "dep" or "arr"
)

class TripRepository(
    private val context: Context, // For SharedPreferences
    private val tfnswApiService: TfNSWApiService // For API calls
) {

    private val sharedPreferences: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    // --- SharedPreferences Methods (from previous step, ensure they are complete) ---
    fun saveLastTripQueryDetails(
        originDisplay: String, originType: String, originValue: String,
        destDisplay: String, destType: String, destValue: String,
        useCurrentLocation: Boolean,
        selectedMillis: Long, isDateTimeManuallySet: Boolean, selectedTimeType: String
    ) {
        with(sharedPreferences.edit()) {
            putString(KEY_LAST_ORIGIN_DISPLAY, originDisplay)
            putString(KEY_LAST_ORIGIN_TYPE, originType)
            putString(KEY_LAST_ORIGIN_VALUE, originValue)
            putString(KEY_LAST_DEST_DISPLAY, destDisplay)
            putString(KEY_LAST_DEST_TYPE, destType)
            putString(KEY_LAST_DEST_VALUE, destValue)
            putBoolean(KEY_LAST_USE_CURRENT_LOCATION, useCurrentLocation)
            putLong(KEY_LAST_SELECTED_DATE_MILLIS, selectedMillis)
            putBoolean(KEY_IS_DATETIME_MANUALLY_SET, isDateTimeManuallySet)
            putString(KEY_LAST_SELECTED_TIME_TYPE, selectedTimeType)
            apply()
        }
        Log.d("RepositoryPrefs", "Saved: Origin=$originDisplay, Dest=$destDisplay, UseCurrentLoc=$useCurrentLocation, TimeMillis=$selectedMillis, isManualTimeSet=$isDateTimeManuallySet, TimeType=$selectedTimeType")
    }

    fun loadLastTripQueryDetails(): LastQueryPreferences {
        val useCurrentLocation = sharedPreferences.getBoolean(KEY_LAST_USE_CURRENT_LOCATION, true)
        val originDisplay = sharedPreferences.getString(KEY_LAST_ORIGIN_DISPLAY, null)
        val originType = sharedPreferences.getString(KEY_LAST_ORIGIN_TYPE, null)
        val originValue = sharedPreferences.getString(KEY_LAST_ORIGIN_VALUE, null)
        val destDisplay = sharedPreferences.getString(KEY_LAST_DEST_DISPLAY, DEFAULT_DESTINATION_ADDRESS)
        val destType = sharedPreferences.getString(KEY_LAST_DEST_TYPE, DEFAULT_DESTINATION_TYPE)
        val destValue = sharedPreferences.getString(KEY_LAST_DEST_VALUE, DEFAULT_DESTINATION_ID)
        val selectedTimeMillis = sharedPreferences.getLong(KEY_LAST_SELECTED_DATE_MILLIS, -1L)
        val isDateTimeManuallySet = sharedPreferences.getBoolean(KEY_IS_DATETIME_MANUALLY_SET, false)
        val selectedTimeType = sharedPreferences.getString(KEY_LAST_SELECTED_TIME_TYPE, "dep") ?: "dep"

        return LastQueryPreferences(
            useCurrentLocation, originDisplay, originType, originValue,
            destDisplay, destType, destValue,
            selectedTimeMillis, isDateTimeManuallySet, selectedTimeType
        )
    }


    // --- API Call Methods IMPLEMENTED ---
    suspend fun findLocations(apiKey: String, searchTerm: String): Result<StopFinderResponse> = withContext(Dispatchers.IO) {
        suspendCancellableCoroutine { continuation ->
            val authHeader = "apikey $apiKey"
            Log.d("TripRepository", "findLocations: searchTerm='$searchTerm', authHeader='$authHeader'")
            val call = tfnswApiService.findLocations(
                authorization = authHeader,
                searchTerm = searchTerm
            )
            call.enqueue(object : Callback<StopFinderResponse> {
                override fun onResponse(call: Call<StopFinderResponse>, response: Response<StopFinderResponse>) {
                    if (response.isSuccessful) {
                        response.body()?.let {
                            Log.d("TripRepository", "findLocations success: ${it.locations?.size} locations")
                            continuation.resume(Result.success(it))
                        } ?: continuation.resume(Result.failure(Exception("Response body is null for stop finder")))
                    } else {
                        val errorBody = response.errorBody()?.string() ?: "Unknown error body"
                        val errorMsg = "Stop finder API Error: ${response.code()} - ${response.message()} - $errorBody"
                        Log.e("TripRepository", errorMsg)
                        continuation.resume(Result.failure(Exception(errorMsg)))
                    }
                }

                override fun onFailure(call: Call<StopFinderResponse>, t: Throwable) {
                    Log.e("TripRepository", "Stop finder Network Failure: ${t.message}", t)
                    continuation.resume(Result.failure(t))
                }
            })
            continuation.invokeOnCancellation {
                call.cancel()
                Log.d("TripRepository", "findLocations call cancelled")
            }
        }
    }

    suspend fun planTrip(
        apiKey: String,
        originType: String, originValue: String,
        destinationType: String, destinationValue: String,
        date: String, time: String, depArrMacro: String,
        tfNSWTR: String = "true", calcNumberOfTrips: Int = 5
    ): Result<TripResponse> = withContext(Dispatchers.IO) {
        suspendCancellableCoroutine { continuation ->
            val authHeader = "apikey $apiKey"
            Log.d("TripRepository", "planTrip: Origin=$originType:$originValue, Dest=$destinationType:$destinationValue, Date=$date, Time=$time, Mode=$depArrMacro, Auth=$authHeader")
            val call = tfnswApiService.planTrip(
                authorization = authHeader,
                date = date,
                time = time,
                depArrMacro = depArrMacro,
                originType = originType,
                originValue = originValue,
                destinationType = destinationType,
                destinationValue = destinationValue,
                calcNumberOfTrips = calcNumberOfTrips,
                tfNSWTR = tfNSWTR
            )

            call.enqueue(object : Callback<TripResponse> {
                override fun onResponse(call: Call<TripResponse>, response: Response<TripResponse>) {
                    if (response.isSuccessful) {
                        response.body()?.let {
                            Log.d("TripRepository", "planTrip success: ${it.journeys?.size} journeys")
                            continuation.resume(Result.success(it))
                        } ?: continuation.resume(Result.failure(Exception("Response body is null for trip plan")))
                    } else {
                        val errorBody = response.errorBody()?.string() ?: "Unknown error body"
                        val errorMsg = "Trip plan API Error: ${response.code()} - ${response.message()} - $errorBody"
                        Log.e("TripRepository", errorMsg)
                        continuation.resume(Result.failure(Exception(errorMsg)))
                    }
                }

                override fun onFailure(call: Call<TripResponse>, t: Throwable) {
                    Log.e("TripRepository", "Trip plan Network Failure: ${t.message}", t)
                    continuation.resume(Result.failure(t))
                }
            })
            continuation.invokeOnCancellation {
                call.cancel()
                Log.d("TripRepository", "planTrip call cancelled")
            }
        }
    }

    companion object {
        // SharedPreferences Keys
        private const val PREFS_NAME = "TripPlannerPrefs" // Referenced by MainActivity for init
        private const val KEY_LAST_ORIGIN_DISPLAY = "lastOriginDisplay"
        private const val KEY_LAST_ORIGIN_TYPE = "lastOriginType"
        private const val KEY_LAST_ORIGIN_VALUE = "lastOriginValue"
        private const val KEY_LAST_DEST_DISPLAY = "lastDestDisplay"
        private const val KEY_LAST_DEST_TYPE = "lastDestType"
        private const val KEY_LAST_DEST_VALUE = "lastDestValue"
        private const val KEY_LAST_USE_CURRENT_LOCATION = "lastUseCurrentLocation"
        private const val KEY_LAST_SELECTED_DATE_MILLIS = "lastSelectedDateMillis"
        private const val KEY_IS_DATETIME_MANUALLY_SET = "isDateTimeManuallySet"
        private const val KEY_LAST_SELECTED_TIME_TYPE = "lastSelectedTimeType"

        // Default destination constants, can be accessed by ViewModel
        const val DEFAULT_DESTINATION_ADDRESS = "Hornsby Station, Hornsby"
        const val DEFAULT_DESTINATION_ID = "207720"
        const val DEFAULT_DESTINATION_TYPE = "stop"
    }
}