package com.example.dadstripplanner3 // Your package name

import retrofit2.Call // Import Retrofit Call
import retrofit2.Callback // Import Retrofit Callback
import retrofit2.Response // Import Retrofit Response
import java.text.SimpleDateFormat // For date and time formatting
import java.util.Calendar // For getting current date and time
import java.util.Locale // For date/time formatting
import java.time.Duration // For durations
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.dadstripplanner3.databinding.ActivityMainBinding
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsRequest
import com.google.android.gms.location.LocationSettingsResponse
import com.google.android.gms.location.Priority
import com.google.android.gms.location.SettingsClient
import com.google.android.gms.tasks.Task

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    // Variables to store current location details
    private var currentFetchedLocationString: String? = null
    private var currentActualLocation: Location? = null

    // For handling location updates
    private var locationCallback: LocationCallback? = null
    private var requestingLocationUpdates = false

    companion object {
        const val EXTRA_SOURCE_LOCATION = "com.example.dadstripplanner3.SOURCE_LOCATION"
        const val EXTRA_DESTINATION_LOCATION = "com.example.dadstripplanner3.DESTINATION_LOCATION"
        const val EXTRA_TRIP_OPTIONS_LIST = "com.example.dadstripplanner3.TRIP_OPTIONS_LIST" // New key
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1001
    }

    private val locationSettingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        Log.d("LocationSettings", "Returned from location settings dialog with result code: ${result.resultCode}")
        if (result.resultCode == AppCompatActivity.RESULT_OK) {
            Log.d("LocationSettings", "User confirmed settings change (RESULT_OK). Re-checking location.")
        } else {
            Log.d("LocationSettings", "User cancelled or backed out of settings change.")
            Toast.makeText(this, "Location services may not be enabled.", Toast.LENGTH_SHORT).show()
            if (currentActualLocation == null) {
                currentFetchedLocationString = "My Current Location (Services Off)"
            }
        }
        if (binding.radioButtonCurrentLocation.isChecked) {
            currentFetchedLocationString = "My Current Location (Fetching...)"
            fetchOrRequestLocation()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        setupListeners()
        initializeLocationState()
    }

    override fun onPause() {
        super.onPause()
        stopLocationUpdates() // Important to stop updates when activity is not active
    }

    private fun setupListeners() {
        binding.radioGroupSource.setOnCheckedChangeListener { _, checkedId ->
            // ... (existing logic from previous step) ...
            if (checkedId == R.id.radioButtonCustomLocation) {
                binding.editTextSourceCustom.isEnabled = true
                binding.editTextSourceCustom.alpha = 1.0f
                binding.editTextSourceCustom.requestFocus()
                currentFetchedLocationString = null
                currentActualLocation = null
                stopLocationUpdates()
            } else if (checkedId == R.id.radioButtonCurrentLocation) {
                binding.editTextSourceCustom.isEnabled = false
                binding.editTextSourceCustom.alpha = 0.5f
                binding.editTextSourceCustom.text.clear()
                currentFetchedLocationString = "My Current Location (Fetching...)"
                fetchOrRequestLocation()
            }
        }

        binding.buttonNext.setOnClickListener {
            val destinationInput = binding.editTextDestination.text.toString().trim()
            var originType: String
            var originValue: String
            var sourceForIntentDisplay: String

            if (binding.radioButtonCurrentLocation.isChecked) {
                // ... (permission, service, and currentActualLocation null checks remain the same)
                if (currentActualLocation == null) {
                    Toast.makeText(this, "Current location details unavailable. Cannot plan trip.", Toast.LENGTH_LONG).show()
                    if (currentFetchedLocationString?.contains("Fetching") == true) fetchOrRequestLocation()
                    return@setOnClickListener
                }

                originType = "coord" // As per Swagger for coordinate input
                val lon = currentActualLocation!!.longitude
                val lat = currentActualLocation!!.latitude
                // --- CRITICAL CORRECTION based on Swagger ---
                originValue = "$lon:$lat:EPSG:4326"
                sourceForIntentDisplay = currentFetchedLocationString ?: "My Current Location"
                Log.d("APIRequest", "Origin (coord): $originValue")

            } else { // Custom location
                val customSourceInput = binding.editTextSourceCustom.text.toString().trim()
                if (customSourceInput.isEmpty()) {
                    Toast.makeText(this, "Please enter a source address", Toast.LENGTH_SHORT).show()
                    binding.editTextSourceCustom.error = "Source cannot be empty"
                    return@setOnClickListener
                }
                // --- MODIFICATION: Try type "any" for addresses ---
                originType = "any"
                originValue = customSourceInput // Pass raw string, Retrofit will URL-encode
                sourceForIntentDisplay = customSourceInput
                Log.d("APIRequest", "Origin (any - address input): $originValue")
            }

            if (destinationInput.isEmpty()) {
                Toast.makeText(this, "Please enter a destination address", Toast.LENGTH_SHORT).show()
                binding.editTextDestination.error = "Destination cannot be empty"
                return@setOnClickListener
            }
            // --- MODIFICATION: Try type "any" for addresses ---
            val destinationType = "any"
            val destinationValue = destinationInput // Pass raw string, Retrofit will URL-encode
            Log.d("APIRequest", "Destination (any - address input): $destinationValue")

            val calendar = Calendar.getInstance()
            val dateFormat = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
            val timeFormat = SimpleDateFormat("HHmm", Locale.getDefault())
            val currentDate = dateFormat.format(calendar.time)
            val currentTime = timeFormat.format(calendar.time)
            val authHeader = "apikey ${BuildConfig.TfNSW_API_KEY}"

            Log.d("APIRequest", "Auth: $authHeader, Date: $currentDate, Time: $currentTime")

            binding.buttonNext.isEnabled = false
            Toast.makeText(this, "Planning trip...", Toast.LENGTH_SHORT).show()

            val call = ApiClient.instance.planTrip(
                authorization = authHeader,
                date = currentDate,
                time = currentTime,
                originType = originType,
                originValue = originValue,
                destinationType = destinationType,
                destinationValue = destinationValue,
                tfNSWTR = "true" // Pass the TfNSWTR parameter
                // calcNumberOfTrips will use its default from the interface
            )

            call.enqueue(object : Callback<TripResponse> {
                override fun onResponse(call: Call<TripResponse>, response: Response<TripResponse>) {
                    binding.buttonNext.isEnabled = true
                    if (response.isSuccessful) {
                        val tripResponse = response.body()
                        if (tripResponse != null && tripResponse.journeys != null && !tripResponse.journeys.isEmpty()) {
                            // --- DATA TRANSFORMATION ---
                            val displayableTrips = transformApiJourneysToDisplayableOptions(tripResponse.journeys)
                            Log.d("APIResponse", "Success: ${displayableTrips.size} displayable journeys created.")
                            // You can remove the forEach log here if you've confirmed it's working
                            // displayableTrips.forEachIndexed { index, trip ->
                            //    Log.d("TransformedTrip[$index]", "Duration: ${trip.overallDurationInMinutes}min, Dep: ${trip.departureTimeFormatted} (${trip.departureStatus}), Arr: ${trip.arrivalTimeFormatted}, Modes: ${trip.transportModesSummary}, From: ${trip.effectiveOriginName}, To: ${trip.effectiveDestinationName}, Interchanges: ${trip.interchanges}")
                            // }
                            Toast.makeText(this@MainActivity, "Trip options processed! (${displayableTrips.size})", Toast.LENGTH_LONG).show()

                            // --- PASS PROCESSED DATA TO RouteOptionsActivity ---
                            val intent = Intent(this@MainActivity, RouteOptionsActivity::class.java).apply {
                                putExtra(EXTRA_SOURCE_LOCATION, sourceForIntentDisplay) // Keep for title
                                putExtra(EXTRA_DESTINATION_LOCATION, destinationInput) // Keep for title
                                // Pass the list of Parcelable trip options
                                putParcelableArrayListExtra(EXTRA_TRIP_OPTIONS_LIST, ArrayList(displayableTrips))
                            }
                            startActivity(intent)

                        } else if (tripResponse != null && tripResponse.systemMessages != null && !tripResponse.systemMessages.isEmpty()) {
                            // ... (existing systemMessages logging) ...
                            var errorMessages = "API Info: "
                            tripResponse.systemMessages.forEach { msg ->
                                errorMessages += "${msg.text}; "
                                Log.w("APIResponse", "System Message: Type=${msg.type}, Module=${msg.module}, Code=${msg.code}, Text=${msg.text}")
                            }
                            Toast.makeText(this@MainActivity, errorMessages, Toast.LENGTH_LONG).show()
                            if (tripResponse.journeys == null || tripResponse.journeys.isEmpty()){
                                Log.w("APIResponse", "Success (HTTP 200) but no journeys found (due to input issues reported in systemMessages).")
                            }
                        } else {
                            // ... (existing handling for null/malformed successful response) ...
                            Log.w("APIResponse", "Success (HTTP 200) but response body is null, malformed, or no journeys/messages.")
                            Toast.makeText(this@MainActivity, "Received an empty or unexpected valid response.", Toast.LENGTH_LONG).show()
                        }
                    } else {
                        // ... (existing API error handling) ...
                        val errorBody = response.errorBody()?.string()
                        Log.e("APIResponse", "API Error: ${response.code()} - ${response.message()}")
                        Log.e("APIResponse", "Error Body: $errorBody")
                        Toast.makeText(this@MainActivity, "API Error: ${response.code()} - Check logs.", Toast.LENGTH_LONG).show()
                    }
                }

                override fun onFailure(call: Call<TripResponse>, t: Throwable) {
                    binding.buttonNext.isEnabled = true
                    Log.e("APIResponse", "Network Failure: ${t.message}", t)
                    Toast.makeText(this@MainActivity, "Network Error: ${t.message}", Toast.LENGTH_LONG).show()
                }
            })
        }
    }

    private fun initializeLocationState() { /* ... as before ... */
        if (binding.radioButtonCurrentLocation.isChecked) {
            binding.editTextSourceCustom.isEnabled = false
            binding.editTextSourceCustom.alpha = 0.5f
            currentFetchedLocationString = "My Current Location (Fetching...)"
            fetchOrRequestLocation()
        } else if (binding.radioButtonCustomLocation.isChecked) {
            binding.editTextSourceCustom.isEnabled = true
            binding.editTextSourceCustom.alpha = 1.0f
        }
    }
    private fun isLocationPermissionGranted(): Boolean { /* ... as before ... */
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }
    private fun requestLocationPermission() { /* ... as before ... */
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
            LOCATION_PERMISSION_REQUEST_CODE
        )
    }
    private fun isLocationServicesEnabled(): Boolean { /* ... as before ... */
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }
    private fun promptToEnableLocationServices() { /* ... as before ... */
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10000)
            .setMinUpdateIntervalMillis(5000)
            .build()
        val builder = LocationSettingsRequest.Builder().addLocationRequest(locationRequest)
        val client: SettingsClient = LocationServices.getSettingsClient(this)
        val task: Task<LocationSettingsResponse> = client.checkLocationSettings(builder.build())
        task.addOnSuccessListener {
            Log.d("LocationSettings", "Location settings are satisfied (promptToEnableLocationServices).")
            if (binding.radioButtonCurrentLocation.isChecked) {
                fetchOrRequestLocation()
            }
        }
        task.addOnFailureListener { exception ->
            Log.w("LocationSettings", "Location settings check failed.", exception)
            if (exception is ResolvableApiException) {
                try {
                    val intentSenderRequest = IntentSenderRequest.Builder(exception.resolution.intentSender).build()
                    locationSettingsLauncher.launch(intentSenderRequest)
                } catch (sendEx: Exception) {
                    Log.e("LocationSettings", "Error starting settings resolution: ${sendEx.message}", sendEx)
                    Toast.makeText(this, "Error opening location settings. Please enable manually.", Toast.LENGTH_LONG).show()
                }
            } else {
                Toast.makeText(this, "Location settings are inadequate. Please enable manually.", Toast.LENGTH_LONG).show()
                currentFetchedLocationString = "My Current Location (Settings Issue)"
                currentActualLocation = null
            }
        }
    }
    private fun fetchOrRequestLocation() { /* ... as before ... */
        if (!isLocationPermissionGranted()) {
            currentFetchedLocationString = "My Current Location (Permission Needed)"
            currentActualLocation = null
            requestLocationPermission()
            return
        }
        if (!isLocationServicesEnabled()) {
            currentFetchedLocationString = "My Current Location (Services Off)"
            currentActualLocation = null
            promptToEnableLocationServices()
            return
        }
        Log.d("LocationFetch", "Permissions and services OK. Getting last known location.")
        getLastKnownLocation()
    }
    @SuppressLint("MissingPermission")
    private fun getLastKnownLocation() { /* ... as before ... */
        currentFetchedLocationString = "My Current Location (Fetching...)"
        fusedLocationClient.lastLocation
            .addOnSuccessListener { location ->
                if (location != null) {
                    Log.d("LocationFetch", "Last known location found.")
                    updateLocationDetails(location)
                } else {
                    Log.d("LocationFetch", "Last known location is null. Requesting new location update.")
                    startLocationUpdates()
                }
            }
            .addOnFailureListener { e ->
                Log.e("LocationFetch", "Failed to get last known location.", e)
                currentFetchedLocationString = "My Current Location (Error LastKnown)"
                currentActualLocation = null
                Toast.makeText(this, "Error getting last location. Trying fresh.", Toast.LENGTH_SHORT).show()
                startLocationUpdates()
            }
    }
    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() { /* ... as before ... */
        if (requestingLocationUpdates) {
            Log.d("LocationFetch", "Already requesting location updates.")
            return
        }
        if (!isLocationServicesEnabled()){
            Log.d("LocationFetch", "Location services disabled before starting updates.")
            currentFetchedLocationString = "My Current Location (Services Off)"
            currentActualLocation = null
            return
        }
        Log.d("LocationFetch", "Starting location updates.")
        currentFetchedLocationString = "My Current Location (Updating...)"
        requestingLocationUpdates = true
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10000)
            .setMinUpdateIntervalMillis(5000)
            .setMaxUpdates(1)
            .build()
        if (locationCallback == null) {
            locationCallback = object : LocationCallback() {
                override fun onLocationResult(locationResult: LocationResult) {
                    locationResult.lastLocation?.let { location ->
                        Log.d("LocationFetch", "New location received via update: Lat: ${location.latitude}, Lon: ${location.longitude}")
                        updateLocationDetails(location)
                    } ?: run {
                        Log.d("LocationFetch", "LocationResult.lastLocation is null in callback.")
                        if (currentActualLocation == null) {
                            currentFetchedLocationString = "My Current Location (Update Failed)"
                            Toast.makeText(this@MainActivity, "Failed to get current location update.", Toast.LENGTH_SHORT).show()
                        }
                    }
                    stopLocationUpdates()
                }
            }
        }
        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback!!,
                Looper.getMainLooper()
            )
        } catch (e: SecurityException) {
            Log.e("LocationFetch", "SecurityException during requestLocationUpdates.", e)
            currentFetchedLocationString = "My Current Location (Security Issue)"
            currentActualLocation = null
            requestingLocationUpdates = false
        }
    }
    private fun stopLocationUpdates() { /* ... as before ... */
        if (requestingLocationUpdates) {
            locationCallback?.let {
                Log.d("LocationFetch", "Attempting to stop location updates.")
                fusedLocationClient.removeLocationUpdates(it)
                    .addOnCompleteListener { task ->
                        requestingLocationUpdates = false
                        if (task.isSuccessful) {
                            Log.d("LocationFetch", "Successfully stopped location updates.")
                        } else {
                            Log.w("LocationFetch", "Failed to stop location updates.", task.exception)
                        }
                    }
            } ?: run {
                requestingLocationUpdates = false
            }
        }
    }
    private fun updateLocationDetails(location: Location) { /* ... as before ... */
        currentActualLocation = location
        val latStr = String.format("%.3f", location.latitude)
        val lonStr = String.format("%.3f", location.longitude)
        currentFetchedLocationString = "My Current Location (Lat: $latStr, Lon: $lonStr)"
        Toast.makeText(this, "Location updated: $currentFetchedLocationString", Toast.LENGTH_SHORT).show()
        Log.d("LocationFetch", "Location details updated: $currentFetchedLocationString")
    }
    override fun onRequestPermissionsResult( /* ... as before ... */
                                             requestCode: Int,
                                             permissions: Array<out String>,
                                             grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Location permission granted!", Toast.LENGTH_SHORT).show()
                if (binding.radioButtonCurrentLocation.isChecked) {
                    currentFetchedLocationString = "My Current Location (Fetching...)"
                    fetchOrRequestLocation()
                }
            } else {
                Toast.makeText(this, "Location permission denied.", Toast.LENGTH_SHORT).show()
                currentFetchedLocationString = "My Current Location (Permission Denied)"
                currentActualLocation = null
                if (binding.radioButtonCurrentLocation.isChecked) {
                    binding.radioButtonCustomLocation.isChecked = true
                    Toast.makeText(this, "Cannot use current location. Switched to custom source.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // --- Date/Time Helper Functions ---
    private fun parseIsoDateTime(dateTimeString: String?): ZonedDateTime? {
        if (dateTimeString.isNullOrBlank()) return null
        return try {
            // Handles formats like "2025-05-19T10:20:00Z" (UTC)
            // or "2025-05-19T10:21:42AEST" (if AEST is understood by default or system timezone matches)
            // For more robust parsing of various timezone offsets, more complex logic might be needed,
            // but TfNSW often returns 'Z' for UTC.
            // If the API returns zone explicitly (like AEST), ZonedDateTime should handle it.
            // If it's an offset like +10:00, ZonedDateTime also handles it.
            // Let's assume common ISO patterns.
            Instant.parse(dateTimeString).atZone(ZoneId.systemDefault()) // Convert to system's default timezone for display
        } catch (e: DateTimeParseException) {
            Log.e("DateTimeParse", "Failed to parse ISO DateTime: $dateTimeString", e)
            // Fallback for formats that might not be strictly Instant.parse compatible without a formatter
            try {
                // Try a common offset pattern if 'Z' is not present and no zone ID is there.
                // This is a common pattern, but you might need to adjust based on exact API output.
                val formatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME
                ZonedDateTime.parse(dateTimeString, formatter.withZone(ZoneId.systemDefault()))
            } catch (e2: DateTimeParseException) {
                Log.e("DateTimeParse", "Failed to parse ISO Offset DateTime: $dateTimeString", e2)
                null
            }
        }
    }

    private fun formatTimeForDisplay(zonedDateTime: ZonedDateTime?): String {
        if (zonedDateTime == null) return "--:--"
        val formatter = DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault()) // e.g., "10:44 AM"
        return zonedDateTime.format(formatter)
    }

    private fun calculateTimeDifferenceInMinutes(start: ZonedDateTime?, end: ZonedDateTime?): Long {
        if (start == null || end == null) return 0L
        return Duration.between(start, end).toMinutes()
    }


    // --- Main Transformation Function ---
    private fun transformApiJourneysToDisplayableOptions(apiJourneys: List<com.example.dadstripplanner3.Journey>): List<DisplayableTripOption> {
        val displayableOptions = mutableListOf<DisplayableTripOption>()

        for (apiJourney in apiJourneys) {
            if (apiJourney.legs.isNullOrEmpty()) continue

            val firstLeg = apiJourney.legs.first()
            val lastLeg = apiJourney.legs.last()

            val overallOrigin = firstLeg.origin
            val overallDestination = lastLeg.destination

            val departureTimePlannedZoned = parseIsoDateTime(overallOrigin?.departureTimePlanned)
            val departureTimeEstimatedZoned = parseIsoDateTime(overallOrigin?.departureTimeEstimated)
            val arrivalTimePlannedZoned = parseIsoDateTime(overallDestination?.arrivalTimePlanned)
            val arrivalTimeEstimatedZoned = parseIsoDateTime(overallDestination?.arrivalTimeEstimated)

            // Use estimated if available, otherwise planned, for display times
            val displayDepartureTime = departureTimeEstimatedZoned ?: departureTimePlannedZoned
            val displayArrivalTime = arrivalTimeEstimatedZoned ?: arrivalTimePlannedZoned

            // Overall duration: Sum of leg durations (API provides leg duration in seconds)
            val totalDurationSeconds = apiJourney.legs.sumOf { it.durationSeconds ?: 0L }
            val totalDurationMinutes = (totalDurationSeconds / 60).toInt()

            // Transport modes summary
            val modes = mutableListOf<String>()
            var interchanges = -1 // Start at -1 so first PT leg makes it 0 interchanges
            var previousLegWasPT = false

            apiJourney.legs.forEach { leg ->
                val modeName = when (leg.transportation?.product?.transportClass) {
                    100, 99 -> if (modes.isEmpty() || modes.last() != "Walk") "Walk" else null // Avoid consecutive "Walk"
                    5 -> "Bus ${leg.transportation.number ?: ""}".trim() // Bus
                    1 -> "Train ${leg.transportation.number ?: ""}".trim() // Train
                    4 -> "LR ${leg.transportation.number ?: ""}".trim() // Light Rail
                    9 -> "Ferry ${leg.transportation.number ?: ""}".trim() // Ferry
                    2 -> "Metro ${leg.transportation.number ?: ""}".trim() // Metro
                    7 -> "Coach ${leg.transportation.number ?: ""}".trim() // Coach
                    11 -> "School Bus ${leg.transportation.number ?: ""}".trim() // School Bus
                    else -> leg.transportation?.product?.name // Fallback
                }
                if (modeName != null) {
                    modes.add(modeName)
                    val currentLegIsPT = leg.transportation?.product?.transportClass !in listOf(100, 99)
                    if (currentLegIsPT && previousLegWasPT) {
                        interchanges++
                    }
                    if (currentLegIsPT) {
                        if (interchanges == -1) interchanges = 0 // First PT leg
                        previousLegWasPT = true
                    } else { // Walk leg might reset the PT sequence for interchange counting
                        previousLegWasPT = false
                    }
                }
            }
            if (interchanges < 0) interchanges = 0 // If only walk, 0 interchanges

            val modesSummary = modes.distinct().joinToString(" \u2022 ") // Use a bullet as separator, show distinct modes

            // Departure Status Logic (simplified, focuses on the first leg's departure)
            var departureStatusText = "Scheduled"
            var isLate = false
            var isRealTimeUnavailable = firstLeg.isRealtimeControlled == false

            if (firstLeg.isRealtimeControlled == true) {
                if (departureTimePlannedZoned != null && departureTimeEstimatedZoned != null) {
                    val delayMinutes = calculateTimeDifferenceInMinutes(departureTimePlannedZoned, departureTimeEstimatedZoned)
                    if (delayMinutes == 0L) {
                        departureStatusText = "On time"
                    } else if (delayMinutes > 0) {
                        departureStatusText = "$delayMinutes min late"
                        isLate = true
                    } else { // delayMinutes < 0
                        departureStatusText = "${-delayMinutes} min early"
                        // Consider if "early" should also set isLate or a different flag
                    }
                } else {
                    departureStatusText = "On time" // Assume on time if one of the times is missing but RT is controlled
                }
            } else if (firstLeg.origin?.departureTimePlanned != null) { // Not real-time controlled, but has a planned time
                departureStatusText = "Scheduled" // Or use "Data unavailable" if preferred
                isRealTimeUnavailable = true // Explicitly set if not real-time controlled
            } else {
                departureStatusText = "Data unavailable"
                isRealTimeUnavailable = true
            }
            // Refine based on leg.realtimeStatus if available and more descriptive
            firstLeg.realtimeStatus?.firstOrNull()?.let { rs ->
                if (rs.equals("MONITORED", ignoreCase = true) && departureStatusText == "Scheduled") {
                    // If monitored and still scheduled, could mean on time or just that it is being watched
                } else if (!rs.equals("MONITORED", ignoreCase = true)) {
                    // departureStatusText = rs // Could use the direct status if it's more user-friendly
                }
            }


            val option = DisplayableTripOption(
                overallDurationInMinutes = totalDurationMinutes,
                departureTimeFormatted = formatTimeForDisplay(displayDepartureTime),
                arrivalTimeFormatted = formatTimeForDisplay(displayArrivalTime),
                effectiveOriginName = overallOrigin?.name ?: "Unknown Origin",
                effectiveDestinationName = overallDestination?.name ?: "Unknown Destination",
                transportModesSummary = modesSummary.ifEmpty { "N/A" },
                departureStatus = departureStatusText,
                isLate = isLate,
                isRealTimeDataUnavailable = isRealTimeUnavailable,
                interchanges = interchanges
            )
            displayableOptions.add(option)
        }
        return displayableOptions
    }

} // End of MainActivity