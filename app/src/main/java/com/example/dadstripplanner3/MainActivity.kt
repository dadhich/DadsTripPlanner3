package com.example.dadstripplanner3

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
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
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.text.SimpleDateFormat
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Calendar
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private var currentFetchedLocationString: String? = null
    private var currentActualLocation: Location? = null

    private var locationCallback: LocationCallback? = null
    private var requestingLocationUpdates = false

    // For Autocomplete
    private lateinit var destinationAdapter: ArrayAdapter<String>
    private lateinit var sourceAdapter: ArrayAdapter<String>
    private val suggestionItemsMap = mutableMapOf<String, StopFinderLocation>()

    private val handler = Handler(Looper.getMainLooper())
    private var destinationRunnable: Runnable? = null
    private var sourceRunnable: Runnable? = null
    private val DEBOUNCE_DELAY_MS = 500L
    private val MIN_CHAR_THRESHOLD = 3

    private var selectedOriginFromAutocomplete: StopFinderLocation? = null
    private var selectedDestinationFromAutocomplete: StopFinderLocation? = null

    companion object {
        const val EXTRA_SOURCE_LOCATION = "com.example.dadstripplanner3.SOURCE_LOCATION"
        const val EXTRA_DESTINATION_LOCATION = "com.example.dadstripplanner3.DESTINATION_LOCATION"
        const val EXTRA_TRIP_OPTIONS_LIST = "com.example.dadstripplanner3.TRIP_OPTIONS_LIST"
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1001
        private const val DEFAULT_DESTINATION_ADDRESS = "Hornsby Station, Hornsby" // You mentioned you'd adjust "(stop)" part
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
            if (currentActualLocation == null && binding.radioButtonCurrentLocation.isChecked) {
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

        setupAutocompleteAdapters()
        setupListeners()
        initializeUIFields()
    }

    override fun onPause() {
        super.onPause()
        stopLocationUpdates()
    }

    private fun setupAutocompleteAdapters() {
        destinationAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, mutableListOf<String>())
        binding.autoCompleteTextViewDestination.setAdapter(destinationAdapter)

        sourceAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, mutableListOf<String>())
        binding.autoCompleteTextViewSourceCustom.setAdapter(sourceAdapter)
    }

    private fun setupListeners() {
        binding.radioGroupSource.setOnCheckedChangeListener { _, checkedId ->
            if (checkedId == R.id.radioButtonCustomLocation) {
                binding.autoCompleteTextViewSourceCustom.isEnabled = true
                binding.autoCompleteTextViewSourceCustom.alpha = 1.0f
                binding.autoCompleteTextViewSourceCustom.requestFocus()
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showSoftInput(binding.autoCompleteTextViewSourceCustom, InputMethodManager.SHOW_IMPLICIT)
                currentFetchedLocationString = null
                currentActualLocation = null
                selectedOriginFromAutocomplete = null
                stopLocationUpdates()
            } else if (checkedId == R.id.radioButtonCurrentLocation) {
                binding.autoCompleteTextViewSourceCustom.isEnabled = false
                binding.autoCompleteTextViewSourceCustom.alpha = 0.5f
                binding.autoCompleteTextViewSourceCustom.text.clear()
                selectedOriginFromAutocomplete = null
                hideKeyboard(binding.autoCompleteTextViewSourceCustom)
                currentFetchedLocationString = "My Current Location (Fetching...)"
                fetchOrRequestLocation()
            }
        }

        binding.autoCompleteTextViewDestination.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                destinationRunnable?.let { handler.removeCallbacks(it) }
                val currentText = binding.autoCompleteTextViewDestination.text.toString()
                if (selectedDestinationFromAutocomplete != null &&
                    currentText != selectedDestinationFromAutocomplete?.name &&
                    currentText != selectedDestinationFromAutocomplete?.disassembledName &&
                    currentText != formatSuggestion(selectedDestinationFromAutocomplete!!)) { // Check against formatted in case it was set like that
                    selectedDestinationFromAutocomplete = null
                }
            }
            override fun afterTextChanged(s: Editable?) {
                if (s != null && s.length >= MIN_CHAR_THRESHOLD) {
                    destinationRunnable = Runnable { fetchAutocompleteSuggestions(s.toString(), "destination") }
                    handler.postDelayed(destinationRunnable!!, DEBOUNCE_DELAY_MS)
                } else {
                    destinationAdapter.clear()
                    destinationAdapter.notifyDataSetChanged()
                }
            }
        })

        binding.autoCompleteTextViewDestination.setOnItemClickListener { parent, _, position, _ ->
            val selectedSuggestionString = parent.adapter.getItem(position) as String
            selectedDestinationFromAutocomplete = suggestionItemsMap[selectedSuggestionString]
            selectedDestinationFromAutocomplete?.let {
                binding.autoCompleteTextViewDestination.setText(it.name ?: it.disassembledName ?: "", false)
                binding.autoCompleteTextViewDestination.dismissDropDown()
                Log.d("Autocomplete", "Destination selected: ID=${it.id}, Name=${it.name}, Type=${it.type}")
            }
            hideKeyboard(binding.autoCompleteTextViewDestination)
        }

        binding.autoCompleteTextViewSourceCustom.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                sourceRunnable?.let { handler.removeCallbacks(it) }
                val currentText = binding.autoCompleteTextViewSourceCustom.text.toString()
                if (selectedOriginFromAutocomplete != null &&
                    currentText != selectedOriginFromAutocomplete?.name &&
                    currentText != selectedOriginFromAutocomplete?.disassembledName &&
                    currentText != formatSuggestion(selectedOriginFromAutocomplete!!)) {
                    selectedOriginFromAutocomplete = null
                }
            }
            override fun afterTextChanged(s: Editable?) {
                if (s != null && s.length >= MIN_CHAR_THRESHOLD && binding.radioButtonCustomLocation.isChecked) {
                    sourceRunnable = Runnable { fetchAutocompleteSuggestions(s.toString(), "source") }
                    handler.postDelayed(sourceRunnable!!, DEBOUNCE_DELAY_MS)
                } else {
                    sourceAdapter.clear()
                    sourceAdapter.notifyDataSetChanged()
                }
            }
        })

        binding.autoCompleteTextViewSourceCustom.setOnItemClickListener { parent, _, position, _ ->
            val selectedSuggestionString = parent.adapter.getItem(position) as String
            selectedOriginFromAutocomplete = suggestionItemsMap[selectedSuggestionString]
            selectedOriginFromAutocomplete?.let {
                binding.autoCompleteTextViewSourceCustom.setText(it.name ?: it.disassembledName ?: "", false)
                binding.autoCompleteTextViewSourceCustom.dismissDropDown()
                Log.d("Autocomplete", "Source selected: ID=${it.id}, Name=${it.name}, Type=${it.type}")
            }
            hideKeyboard(binding.autoCompleteTextViewSourceCustom)
        }

        binding.buttonNext.setOnClickListener {
            val destinationInputText = binding.autoCompleteTextViewDestination.text.toString().trim()
            var originTypeForAPI: String
            var originValueForAPI: String
            var sourceForIntentDisplay: String
            var destinationTypeForAPI: String
            var destinationValueForAPI: String

            if (binding.radioButtonCurrentLocation.isChecked) {
                if (!isLocationPermissionGranted() || !isLocationServicesEnabled() || currentActualLocation == null) {
                    Toast.makeText(this, "Current location not available or permissions not granted.", Toast.LENGTH_LONG).show()
                    if (currentFetchedLocationString?.contains("Fetching") == true || currentActualLocation == null) fetchOrRequestLocation()
                    else if (!isLocationServicesEnabled()) promptToEnableLocationServices()
                    else if (!isLocationPermissionGranted()) requestLocationPermission()
                    return@setOnClickListener
                }
                originTypeForAPI = "coord"
                val lon = currentActualLocation!!.longitude
                val lat = currentActualLocation!!.latitude
                originValueForAPI = "$lon:$lat:EPSG:4326"
                sourceForIntentDisplay = currentFetchedLocationString ?: "My Current Location"
            } else {
                val customSourceInputText = binding.autoCompleteTextViewSourceCustom.text.toString().trim()
                if (customSourceInputText.isEmpty()) {
                    Toast.makeText(this, "Please enter a source address", Toast.LENGTH_SHORT).show()
                    binding.autoCompleteTextViewSourceCustom.error = "Source cannot be empty"
                    return@setOnClickListener
                }
                sourceForIntentDisplay = customSourceInputText
                if (selectedOriginFromAutocomplete != null &&
                    (selectedOriginFromAutocomplete?.name == customSourceInputText ||
                            selectedOriginFromAutocomplete?.disassembledName == customSourceInputText ||
                            formatSuggestion(selectedOriginFromAutocomplete!!) == customSourceInputText) && // Check formatted string
                    selectedOriginFromAutocomplete?.id != null &&
                    selectedOriginFromAutocomplete?.type != null) {

                    originTypeForAPI = selectedOriginFromAutocomplete!!.type!!
                    originValueForAPI = selectedOriginFromAutocomplete!!.id!!
                    Log.d("APIRequest", "Using selected origin ID: ${originValueForAPI}, Type: ${originTypeForAPI}")
                } else {
                    originTypeForAPI = "any"
                    originValueForAPI = customSourceInputText
                    Log.d("APIRequest", "Using origin text with type 'any': $originValueForAPI")
                }
            }

            if (destinationInputText.isEmpty()) {
                Toast.makeText(this, "Please enter a destination address", Toast.LENGTH_SHORT).show()
                binding.autoCompleteTextViewDestination.error = "Destination cannot be empty"
                return@setOnClickListener
            }
            if (selectedDestinationFromAutocomplete != null &&
                (selectedDestinationFromAutocomplete?.name == destinationInputText ||
                        selectedDestinationFromAutocomplete?.disassembledName == destinationInputText ||
                        formatSuggestion(selectedDestinationFromAutocomplete!!) == destinationInputText) && // Check formatted string
                selectedDestinationFromAutocomplete?.id != null &&
                selectedDestinationFromAutocomplete?.type != null) {

                destinationTypeForAPI = selectedDestinationFromAutocomplete!!.type!!
                destinationValueForAPI = selectedDestinationFromAutocomplete!!.id!!
                Log.d("APIRequest", "Using selected destination ID: ${destinationValueForAPI}, Type: ${destinationTypeForAPI}")
            } else {
                destinationTypeForAPI = "any"
                destinationValueForAPI = destinationInputText
                Log.d("APIRequest", "Using destination text with type 'any': $destinationValueForAPI")
            }

            val calendar = Calendar.getInstance()
            val dateFormat = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
            val timeFormat = SimpleDateFormat("HHmm", Locale.getDefault())
            val currentDate = dateFormat.format(calendar.time)
            val currentTime = timeFormat.format(calendar.time)
            val authHeader = "apikey ${BuildConfig.TfNSW_API_KEY}"

            Log.d("APIRequest", "Planning Trip: OriginType=$originTypeForAPI, OriginValue=$originValueForAPI, DestType=$destinationTypeForAPI, DestValue=$destinationValueForAPI, Time=$currentTime")
            binding.buttonNext.isEnabled = false
            Toast.makeText(this, "Planning trip...", Toast.LENGTH_SHORT).show()

            val call = ApiClient.instance.planTrip(
                authorization = authHeader, date = currentDate, time = currentTime,
                originType = originTypeForAPI, originValue = originValueForAPI,
                destinationType = destinationTypeForAPI, destinationValue = destinationValueForAPI,
                tfNSWTR = "true"
            )

            call.enqueue(object : Callback<TripResponse> {
                override fun onResponse(call: Call<TripResponse>, response: Response<TripResponse>) {
                    binding.buttonNext.isEnabled = true
                    if (response.isSuccessful) {
                        val tripResponseObject = response.body()
                        if (tripResponseObject != null && tripResponseObject.journeys != null && tripResponseObject.journeys.isNotEmpty()) {
                            val displayableTrips = transformApiJourneysToDisplayableOptions(tripResponseObject.journeys)
                            Log.d("APIResponse", "Success: ${displayableTrips.size} displayable journeys created.")

                            val intent = Intent(this@MainActivity, RouteOptionsActivity::class.java).apply {
                                putExtra(EXTRA_SOURCE_LOCATION, sourceForIntentDisplay)
                                putExtra(EXTRA_DESTINATION_LOCATION, destinationInputText)
                                putParcelableArrayListExtra(EXTRA_TRIP_OPTIONS_LIST, ArrayList(displayableTrips))
                            }
                            startActivity(intent)
                        } else if (tripResponseObject != null && tripResponseObject.systemMessages != null && tripResponseObject.systemMessages.isNotEmpty()) {
                            var errorMessages = "API Info: "
                            tripResponseObject.systemMessages.forEach { msg -> errorMessages += "${msg.text}; " }
                            Toast.makeText(this@MainActivity, errorMessages, Toast.LENGTH_LONG).show()
                            Log.w("APIResponse", "Success (HTTP 200) but no journeys (SystemMessages: $errorMessages)")
                        } else {
                            Log.w("APIResponse", "Success (HTTP 200) but response body is null, malformed, or no journeys/messages.")
                            Toast.makeText(this@MainActivity, "No trip options found for this request.", Toast.LENGTH_LONG).show()
                        }
                    } else {
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

    private fun initializeUIFields() {
        binding.autoCompleteTextViewDestination.setText(DEFAULT_DESTINATION_ADDRESS)
        if (binding.radioButtonCurrentLocation.isChecked) {
            binding.autoCompleteTextViewSourceCustom.isEnabled = false
            binding.autoCompleteTextViewSourceCustom.alpha = 0.5f
            currentFetchedLocationString = "My Current Location (Fetching...)"
            fetchOrRequestLocation()
        } else { // Handles if "Specify start address" is default or becomes checked
            binding.autoCompleteTextViewSourceCustom.isEnabled = true
            binding.autoCompleteTextViewSourceCustom.alpha = 1.0f
        }
    }

    private fun fetchAutocompleteSuggestions(query: String, fieldType: String) {
        Log.d("Autocomplete", "Fetching suggestions for '$query' for $fieldType")
        val authHeader = "apikey ${BuildConfig.TfNSW_API_KEY}"

        ApiClient.instance.findLocations(
            authorization = authHeader,
            searchTerm = query
        ).enqueue(object : Callback<StopFinderResponse> {
            override fun onResponse(call: Call<StopFinderResponse>, response: Response<StopFinderResponse>) {
                if (response.isSuccessful) {
                    val stopFinderResponse = response.body()
                    suggestionItemsMap.clear()
                    val suggestionDisplayNames = mutableListOf<String>()

                    stopFinderResponse?.locations?.let { locations ->
                        locations.take(10).forEach { location ->
                            val displayName = formatSuggestion(location)
                            suggestionDisplayNames.add(displayName)
                            suggestionItemsMap[displayName] = location
                        }
                    }

                    val adapter = if (fieldType == "destination") destinationAdapter else sourceAdapter
                    val autoCompleteTextView = if (fieldType == "destination") binding.autoCompleteTextViewDestination else binding.autoCompleteTextViewSourceCustom

                    adapter.clear()
                    if (suggestionDisplayNames.isNotEmpty()) {
                        adapter.addAll(suggestionDisplayNames)
                    }
                    adapter.notifyDataSetChanged()

                    if (autoCompleteTextView.isFocused && suggestionDisplayNames.isNotEmpty()) {
                        autoCompleteTextView.showDropDown()
                    }
                } else {
                    Log.e("Autocomplete", "API Error for /stop_finder: ${response.code()} - ${response.message()}")
                    val adapter = if (fieldType == "destination") destinationAdapter else sourceAdapter
                    adapter.clear()
                    adapter.notifyDataSetChanged()
                }
            }

            override fun onFailure(call: Call<StopFinderResponse>, t: Throwable) {
                Log.e("Autocomplete", "Network Failure for /stop_finder: ${t.message}", t)
                val adapter = if (fieldType == "destination") destinationAdapter else sourceAdapter
                adapter.clear()
                adapter.notifyDataSetChanged()
            }
        })
    }

    private fun formatSuggestion(location: StopFinderLocation): String {
        val namePart = location.name ?: location.disassembledName ?: "Unknown Location"
        val typePart = location.type?.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
        var suggestion = namePart
        if (!typePart.isNullOrEmpty() && typePart.lowercase(Locale.getDefault()) != "unknown" && !namePart.contains(typePart, ignoreCase = true)) {
            if (typePart.lowercase(Locale.getDefault()) != "locality" && typePart.lowercase(Locale.getDefault()) != "suburb" ) {
                suggestion += " ($typePart)"
            }
        }
        location.parent?.name?.let { parentName ->
            if (parentName != namePart && !namePart.contains(parentName, ignoreCase = true) && parentName.lowercase(Locale.getDefault()) != typePart?.lowercase(Locale.getDefault())) {
                if (suggestion == namePart && (typePart.isNullOrEmpty() || typePart.lowercase(Locale.getDefault()) == "unknown") ) { // Only add parent if type wasn't already specific or added
                    suggestion += " - $parentName"
                } else if (!suggestion.contains(parentName)) {
                    suggestion += ", $parentName"
                }
            }
        }
        return suggestion.replace("  ", " ").trim()
    }


    private fun hideKeyboard(view: View) {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(view.windowToken, 0)
    }

    private fun isLocationPermissionGranted(): Boolean {
        return ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestLocationPermission() {
        ActivityCompat.requestPermissions(
            this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), LOCATION_PERMISSION_REQUEST_CODE
        )
    }

    private fun isLocationServicesEnabled(): Boolean {
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    private fun promptToEnableLocationServices() {
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10000)
            .setMinUpdateIntervalMillis(5000).build()
        val builder = LocationSettingsRequest.Builder().addLocationRequest(locationRequest)
        val client: SettingsClient = LocationServices.getSettingsClient(this)
        val task: Task<LocationSettingsResponse> = client.checkLocationSettings(builder.build())

        task.addOnSuccessListener {
            Log.d("LocationSettings", "Location settings satisfied (promptToEnableLocationServices).")
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
                }
            } else {
                currentFetchedLocationString = "My Current Location (Settings Issue)"
                currentActualLocation = null
            }
        }
    }

    private fun fetchOrRequestLocation() {
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
    private fun getLastKnownLocation() {
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
    private fun startLocationUpdates() {
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
            .setMinUpdateIntervalMillis(5000).setMaxUpdates(1).build()

        if (locationCallback == null) {
            locationCallback = object : LocationCallback() {
                override fun onLocationResult(locationResult: LocationResult) {
                    locationResult.lastLocation?.let { location ->
                        Log.d("LocationFetch", "New location received via update: Lat: ${location.latitude}, Lon: ${location.longitude}")
                        updateLocationDetails(location)
                    } ?: run {
                        Log.d("LocationFetch", "LocationResult.lastLocation is null in callback.")
                        if (currentActualLocation == null) { // If still no location after update attempt
                            currentFetchedLocationString = "My Current Location (Update Failed)"
                            Toast.makeText(this@MainActivity, "Failed to get current location update.", Toast.LENGTH_SHORT).show()
                        }
                    }
                    stopLocationUpdates() // Stop after receiving the update or if null
                }
            }
        }

        try {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback!!, Looper.getMainLooper())
        } catch (e: SecurityException) {
            Log.e("LocationFetch", "SecurityException during requestLocationUpdates: ${e.message}", e)
            currentFetchedLocationString = "My Current Location (Security Issue)"
            currentActualLocation = null
            requestingLocationUpdates = false
        }
    }

    private fun stopLocationUpdates() {
        if (requestingLocationUpdates) {
            locationCallback?.let {
                Log.d("LocationFetch", "Attempting to stop location updates.")
                fusedLocationClient.removeLocationUpdates(it).addOnCompleteListener { task ->
                    requestingLocationUpdates = false
                    if (task.isSuccessful) {
                        Log.d("LocationFetch", "Successfully stopped location updates.")
                    } else {
                        Log.w("LocationFetch", "Failed to stop location updates.", task.exception)
                    }
                }
            } ?: run { requestingLocationUpdates = false }
        }
    }

    private fun updateLocationDetails(location: Location) {
        currentActualLocation = location
        val latStr = String.format(Locale.US, "%.3f", location.latitude)
        val lonStr = String.format(Locale.US, "%.3f", location.longitude)
        currentFetchedLocationString = "My Current Location (Lat: $latStr, Lon: $lonStr)"
        // Toast.makeText(this, "Location updated: $currentFetchedLocationString", Toast.LENGTH_SHORT).show()
        Log.d("LocationFetch", "Location details updated: $currentFetchedLocationString")
    }

    override fun onRequestPermissionsResult(
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
                    binding.radioButtonCustomLocation.isChecked = true // Switch to custom
                    Toast.makeText(this, "Cannot use current location. Switched to custom source.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun transformApiJourneysToDisplayableOptions(apiJourneys: List<Journey>): List<DisplayableTripOption> {
        val displayableOptions = mutableListOf<DisplayableTripOption>()
        val timeFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())

        for (apiJourney in apiJourneys) {
            if (apiJourney.legs.isNullOrEmpty()) continue

            val firstLegOverall = apiJourney.legs.first()
            val lastLegOverall = apiJourney.legs.last()

            val overallJourneyDepartureZoned = parseIsoDateTime(firstLegOverall.origin?.departureTimeEstimated ?: firstLegOverall.origin?.departureTimePlanned)
            val overallJourneyArrivalZoned = parseIsoDateTime(lastLegOverall.destination?.arrivalTimeEstimated ?: lastLegOverall.destination?.arrivalTimePlanned)
            val overallJourneyOriginName = firstLegOverall.origin?.name ?: "Unknown Origin"
            val overallJourneyDestinationName = lastLegOverall.destination?.name ?: "Unknown Destination"

            val totalDurationSeconds = apiJourney.legs.sumOf { it.durationSeconds ?: 0L }
            val totalTripDurationInMinutes = (totalDurationSeconds / 60).toInt()

            val modesSummaryList = mutableListOf<String>()
            var interchanges = -1
            var previousLegWasPT = false
            var primaryPTInfoString: String? = null

            apiJourney.legs.forEach { leg ->
                val modeName: String?
                var currentLegIsPT = false
                var ptInfoForThisLeg: String? = null

                when (leg.transportation?.product?.transportClass) {
                    100, 99 -> {
                        modeName = if (modesSummaryList.isEmpty() || modesSummaryList.last() != "Walk") "Walk" else null
                    }
                    5 -> {
                        val routeNumber = leg.transportation.number ?: ""
                        modeName = "Bus ${routeNumber}".trim()
                        ptInfoForThisLeg = modeName
                        currentLegIsPT = true
                    }
                    1 -> {
                        val routeNumber = leg.transportation.number ?: ""
                        modeName = "Train ${routeNumber}".trim()
                        ptInfoForThisLeg = modeName
                        currentLegIsPT = true
                    }
                    4 -> {
                        val routeNumber = leg.transportation.number ?: ""
                        modeName = "LR ${routeNumber}".trim()
                        ptInfoForThisLeg = modeName
                        currentLegIsPT = true
                    }
                    9 -> {
                        val routeNumber = leg.transportation.number ?: ""
                        modeName = "Ferry ${routeNumber}".trim()
                        ptInfoForThisLeg = modeName
                        currentLegIsPT = true
                    }
                    2 -> {
                        val routeNumber = leg.transportation.number ?: ""
                        modeName = "Metro ${routeNumber}".trim()
                        ptInfoForThisLeg = modeName
                        currentLegIsPT = true
                    }
                    else -> {
                        modeName = leg.transportation?.product?.name?.takeIf { it.isNotBlank() }
                        if (modeName != null) currentLegIsPT = true
                    }
                }

                if (modeName != null) {
                    if (modesSummaryList.isEmpty() || modesSummaryList.last() != modeName || modeName == "Walk") {
                        modesSummaryList.add(modeName)
                    }
                }

                if (currentLegIsPT) {
                    if (primaryPTInfoString == null && ptInfoForThisLeg != null) {
                        primaryPTInfoString = ptInfoForThisLeg
                    }
                    if (previousLegWasPT) interchanges++
                    if (interchanges == -1) interchanges = 0
                    previousLegWasPT = true
                } else {
                    previousLegWasPT = false
                }
            }
            if (interchanges < 0) interchanges = 0
            val modesSummary = modesSummaryList.joinToString(" \u2022 ")

            var firstPTLegDepartureStopName: String? = null
            var firstPTLegEstimatedDepartureTimeFormatted: String? = "--:--"
            var firstPTLegScheduledDepartureTimeFormatted: String? = null
            var firstPTLegStatusMessage: String? = "Scheduled"
            var firstPTLegDepartureEpochMillis: Long = overallJourneyDepartureZoned?.toInstant()?.toEpochMilli() ?: -1L
            var isPTLegLate = false
            var isPTLegRealTimeDataUnavailable = true

            val firstPublicTransportLeg = apiJourney.legs.find {
                it.transportation?.product?.transportClass !in listOf(100, 99)
            }

            if (firstPublicTransportLeg != null) {
                firstPTLegDepartureStopName = firstPublicTransportLeg.origin?.name
                val ptPlannedDepZoned = parseIsoDateTime(firstPublicTransportLeg.origin?.departureTimePlanned)
                val ptEstimatedDepZoned = parseIsoDateTime(firstPublicTransportLeg.origin?.departureTimeEstimated)

                firstPTLegDepartureEpochMillis = ptEstimatedDepZoned?.toInstant()?.toEpochMilli()
                    ?: ptPlannedDepZoned?.toInstant()?.toEpochMilli()
                            ?: -1L

                firstPTLegEstimatedDepartureTimeFormatted = formatTimeForDisplay(ptEstimatedDepZoned ?: ptPlannedDepZoned)
                // firstPTLegScheduledDepartureTimeFormatted is used within firstPTLegStatusMessage if needed

                if (firstPublicTransportLeg.isRealtimeControlled == true && ptEstimatedDepZoned != null) {
                    isPTLegRealTimeDataUnavailable = false
                    if (ptPlannedDepZoned != null) {
                        val delayMinutes = calculateTimeDifferenceInMinutes(ptPlannedDepZoned, ptEstimatedDepZoned)
                        val scheduledTimeFormatted = formatTimeForDisplay(ptPlannedDepZoned)
                        firstPTLegScheduledDepartureTimeFormatted = scheduledTimeFormatted // Store for potential use
                        firstPTLegStatusMessage = when {
                            delayMinutes == 0L -> "On time"
                            delayMinutes > 0 -> {
                                isPTLegLate = true
                                "$scheduledTimeFormatted, $delayMinutes min late"
                            }
                            else -> "$scheduledTimeFormatted, ${-delayMinutes} min early"
                        }
                    } else {
                        firstPTLegStatusMessage = "Real-time"
                    }
                } else if (ptPlannedDepZoned != null) {
                    val scheduledTimeFormatted = formatTimeForDisplay(ptPlannedDepZoned)
                    firstPTLegScheduledDepartureTimeFormatted = scheduledTimeFormatted
                    firstPTLegStatusMessage = "$scheduledTimeFormatted Scheduled"
                    isPTLegRealTimeDataUnavailable = true
                } else {
                    firstPTLegStatusMessage = "Data unavailable"
                    isPTLegRealTimeDataUnavailable = true
                }
            } else {
                firstPTLegStatusMessage = if (apiJourney.legs.all { (it.transportation?.product?.transportClass ?: 0) in listOf(100, 99) }) "Walk only" else "Info unavailable"
                isPTLegRealTimeDataUnavailable = true
                firstPTLegDepartureEpochMillis = overallJourneyDepartureZoned?.toInstant()?.toEpochMilli() ?: -1L
                firstPTLegEstimatedDepartureTimeFormatted = formatTimeForDisplay(overallJourneyDepartureZoned)
                firstPTLegDepartureStopName = overallJourneyOriginName
                if (primaryPTInfoString == null && modesSummary.equals("Walk", ignoreCase = true)) {
                    primaryPTInfoString = "Walk"
                }
            }

            val option = DisplayableTripOption(
                overallJourneyDepartureTimeFormatted = formatTimeForDisplay(overallJourneyDepartureZoned),
                overallJourneyArrivalTimeFormatted = formatTimeForDisplay(overallJourneyArrivalZoned),
                overallJourneyOriginName = overallJourneyOriginName,
                overallJourneyDestinationName = overallJourneyDestinationName,
                totalTripDurationInMinutes = totalTripDurationInMinutes,

                firstPTLegDepartureStopName = firstPTLegDepartureStopName,
                firstPTLegEstimatedDepartureTimeFormatted = firstPTLegEstimatedDepartureTimeFormatted,
                firstPTLegScheduledDepartureTimeFormatted = firstPTLegScheduledDepartureTimeFormatted,
                firstPTLegStatusMessage = firstPTLegStatusMessage,
                firstPTLegDepartureEpochMillis = firstPTLegDepartureEpochMillis,
                isPTLegLate = isPTLegLate,
                isPTLegRealTimeDataUnavailable = isPTLegRealTimeDataUnavailable,

                transportModesSummary = modesSummary.ifEmpty { if (firstPTLegStatusMessage == "Walk only") "Walk" else "N/A" },
                primaryPublicTransportInfo = primaryPTInfoString,
                interchanges = interchanges
            )
            displayableOptions.add(option)
        }
        return displayableOptions
    }

    private fun parseIsoDateTime(dateTimeString: String?): ZonedDateTime? {
        if (dateTimeString.isNullOrBlank()) return null
        return try {
            Instant.parse(dateTimeString).atZone(ZoneId.systemDefault())
        } catch (e: DateTimeParseException) {
            Log.w("DateTimeParse", "Instant.parse failed for: $dateTimeString. Trying ISO_ZONED_DATE_TIME.", e)
            try {
                ZonedDateTime.parse(dateTimeString, DateTimeFormatter.ISO_ZONED_DATE_TIME.withZone(ZoneId.systemDefault()))
            } catch (e2: DateTimeParseException) {
                Log.w("DateTimeParse", "ISO_ZONED_DATE_TIME parse failed for: $dateTimeString. Trying ISO_OFFSET_DATE_TIME.", e2)
                try {
                    ZonedDateTime.parse(dateTimeString, DateTimeFormatter.ISO_OFFSET_DATE_TIME.withZone(ZoneId.systemDefault()))
                } catch (e3: DateTimeParseException) {
                    Log.e("DateTimeParse", "All parsing attempts failed for: $dateTimeString", e3)
                    null
                }
            }
        }
    }

    private fun calculateTimeDifferenceInMinutes(start: ZonedDateTime?, end: ZonedDateTime?): Long {
        if (start == null || end == null) return 0L
        return Duration.between(start, end).toMinutes()
    }

    private fun formatTimeForDisplay(zonedDateTime: ZonedDateTime?): String {
        if (zonedDateTime == null) return "--:--"
        val formatter = DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())
        return zonedDateTime.format(formatter)
    }
}