package com.example.dadstripplanner3

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import kotlin.coroutines.resume

// MODIFIED: Data class to hold loaded preference values (Date/Time fields removed)
data class LastQueryPreferences(
    val useCurrentLocation: Boolean,
    val originDisplay: String?,
    val originType: String?,
    val originValue: String?,
    val destDisplay: String?,
    val destType: String?,
    val destValue: String?
    // Removed: selectedTimeMillis, isDateTimeManuallySet, selectedTimeType
)

class TripRepository(
    private val context: Context,
    private val tfnswApiService: TfNSWApiService
) {

    private val sharedPreferences: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    private val gson = Gson()

    // --- SharedPreferences Methods for Last Query ---
    // MODIFIED: Signature and implementation to remove date/time persistence
    fun saveLastTripQueryDetails(
        originDisplay: String, originType: String, originValue: String,
        destDisplay: String, destType: String, destValue: String,
        useCurrentLocation: Boolean
        // Removed: selectedMillis, isDateTimeManuallySet, selectedTimeType parameters
    ) {
        with(sharedPreferences.edit()) {
            putString(KEY_LAST_ORIGIN_DISPLAY, originDisplay)
            putString(KEY_LAST_ORIGIN_TYPE, originType)
            putString(KEY_LAST_ORIGIN_VALUE, originValue)
            putString(KEY_LAST_DEST_DISPLAY, destDisplay)
            putString(KEY_LAST_DEST_TYPE, destType)
            putString(KEY_LAST_DEST_VALUE, destValue)
            putBoolean(KEY_LAST_USE_CURRENT_LOCATION, useCurrentLocation)
            // REMOVED SharedPreferences puts for date/time
            apply()
        }
        Log.d("RepositoryPrefs", "Saved Last Query: Origin=$originDisplay, Dest=$destDisplay, UseCurrentLoc=$useCurrentLocation")
    }

    // MODIFIED: Return type and implementation to remove date/time persistence
    fun loadLastTripQueryDetails(): LastQueryPreferences {
        val useCurrentLocation = sharedPreferences.getBoolean(KEY_LAST_USE_CURRENT_LOCATION, true)
        val originDisplay = sharedPreferences.getString(KEY_LAST_ORIGIN_DISPLAY, null)
        val originType = sharedPreferences.getString(KEY_LAST_ORIGIN_TYPE, null)
        val originValue = sharedPreferences.getString(KEY_LAST_ORIGIN_VALUE, null)
        val destDisplay = sharedPreferences.getString(KEY_LAST_DEST_DISPLAY, DEFAULT_DESTINATION_ADDRESS)
        val destType = sharedPreferences.getString(KEY_LAST_DEST_TYPE, DEFAULT_DESTINATION_TYPE)
        val destValue = sharedPreferences.getString(KEY_LAST_DEST_VALUE, DEFAULT_DESTINATION_ID)

        Log.d("RepositoryPrefs", "Loaded: UseCurrentLoc=$useCurrentLocation, Origin=$originDisplay, Dest=$destDisplay")

        return LastQueryPreferences(
            useCurrentLocation = useCurrentLocation,
            originDisplay = originDisplay,
            originType = originType,
            originValue = originValue,
            destDisplay = destDisplay,
            destType = destType,
            destValue = destValue
            // Removed: selectedTimeMillis, isDateTimeManuallySet, selectedTimeType from object creation
        )
    }

    // --- Favorites Methods ---
    fun addFavorite(favorite: FavoriteTripQuery): Boolean {
        val favorites = getFavorites().toMutableList()
        if (favorites.any { it.favoriteName.equals(favorite.favoriteName, ignoreCase = true) }) {
            Log.w("TripRepository", "Favorite with name '${favorite.favoriteName}' already exists.")
            return false
        }
        favorites.add(favorite)
        saveFavoritesList(favorites)
        Log.d("TripRepository", "Added favorite: ${favorite.favoriteName}")
        return true
    }

    fun getFavorites(): List<FavoriteTripQuery> {
        val jsonFavorites = sharedPreferences.getString(KEY_FAVORITES_LIST, null)
        return if (jsonFavorites != null) {
            try {
                val type = object : TypeToken<List<FavoriteTripQuery>>() {}.type
                gson.fromJson(jsonFavorites, type) ?: emptyList()
            } catch (e: Exception) {
                Log.e("TripRepository", "Error deserializing favorites", e)
                emptyList()
            }
        } else {
            emptyList()
        }
    }

    fun removeFavorite(favoriteName: String): Boolean {
        val favorites = getFavorites().toMutableList()
        val initialSize = favorites.size
        favorites.removeAll { it.favoriteName.equals(favoriteName, ignoreCase = true) }
        if (favorites.size < initialSize) {
            saveFavoritesList(favorites)
            Log.d("TripRepository", "Removed favorite: $favoriteName")
            return true
        }
        Log.w("TripRepository", "Favorite not found for removal: $favoriteName")
        return false
    }

    private fun saveFavoritesList(favorites: List<FavoriteTripQuery>) {
        val jsonFavorites = gson.toJson(favorites)
        with(sharedPreferences.edit()) {
            putString(KEY_FAVORITES_LIST, jsonFavorites)
            apply()
        }
    }

    // --- API Call Methods ---
    suspend fun findLocations(apiKey: String, searchTerm: String): Result<StopFinderResponse> = withContext(Dispatchers.IO) {
        suspendCancellableCoroutine { continuation ->
            val authHeader = "apikey $apiKey"
            val call = tfnswApiService.findLocations(authorization = authHeader, searchTerm = searchTerm)
            call.enqueue(object : Callback<StopFinderResponse> {
                override fun onResponse(call: Call<StopFinderResponse>, response: Response<StopFinderResponse>) {
                    if (response.isSuccessful) {
                        response.body()?.let { if (continuation.isActive) continuation.resume(Result.success(it)) }
                            ?: if (continuation.isActive) continuation.resume(Result.failure(Exception("Response body is null for stop finder"))) else { }
                    } else {
                        val errorMsg = "Stop finder API Error: ${response.code()} - ${response.message()} - ${response.errorBody()?.string()}"
                        if (continuation.isActive) continuation.resume(Result.failure(Exception(errorMsg)))
                    }
                }
                override fun onFailure(call: Call<StopFinderResponse>, t: Throwable) {
                    if (continuation.isActive) continuation.resume(Result.failure(t))
                }
            })
            continuation.invokeOnCancellation { call.cancel() }
        }
    }

    suspend fun planTrip(
        apiKey: String, originType: String, originValue: String,
        destinationType: String, destinationValue: String, date: String,
        time: String, depArrMacro: String, tfNSWTR: String = "true",
        calcNumberOfTrips: Int = 5
    ): Result<TripResponse> = withContext(Dispatchers.IO) {
        suspendCancellableCoroutine { continuation ->
            val authHeader = "apikey $apiKey"
            val call = tfnswApiService.planTrip(
                authorization = authHeader, date = date, time = time, depArrMacro = depArrMacro,
                originType = originType, originValue = originValue,
                destinationType = destinationType, destinationValue = destinationValue,
                calcNumberOfTrips = calcNumberOfTrips, tfNSWTR = tfNSWTR
            )
            call.enqueue(object : Callback<TripResponse> {
                override fun onResponse(call: Call<TripResponse>, response: Response<TripResponse>) {
                    if (response.isSuccessful) {
                        response.body()?.let { if (continuation.isActive) continuation.resume(Result.success(it)) }
                            ?: if (continuation.isActive) continuation.resume(Result.failure(Exception("Response body is null for trip plan"))) else {}
                    } else {
                        val errorMsg = "Trip plan API Error: ${response.code()} - ${response.message()} - ${response.errorBody()?.string()}"
                        if (continuation.isActive) continuation.resume(Result.failure(Exception(errorMsg)))
                    }
                }
                override fun onFailure(call: Call<TripResponse>, t: Throwable) {
                    if (continuation.isActive) continuation.resume(Result.failure(t))
                }
            })
            continuation.invokeOnCancellation { call.cancel() }
        }
    }

    companion object {
        private const val PREFS_NAME = "TripPlannerPrefs"
        private const val KEY_LAST_ORIGIN_DISPLAY = "lastOriginDisplay"
        private const val KEY_LAST_ORIGIN_TYPE = "lastOriginType"
        private const val KEY_LAST_ORIGIN_VALUE = "lastOriginValue"
        private const val KEY_LAST_DEST_DISPLAY = "lastDestDisplay"
        private const val KEY_LAST_DEST_TYPE = "lastDestType"
        private const val KEY_LAST_DEST_VALUE = "lastDestValue"
        private const val KEY_LAST_USE_CURRENT_LOCATION = "lastUseCurrentLocation"
        // REMOVED Date/Time SharedPreferences Keys
        private const val KEY_FAVORITES_LIST = "favoritesList"

        const val DEFAULT_DESTINATION_ADDRESS = "Hornsby Station, Hornsby"
        const val DEFAULT_DESTINATION_ID = "207720"
        const val DEFAULT_DESTINATION_TYPE = "stop"
    }
}