package com.example.dadstripplanner3

import android.Manifest
import android.annotation.SuppressLint
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
// SharedPreferences is no longer directly managed here for loading/saving trip query display details
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
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
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels {
        MainViewModelFactory(application, TripRepository(applicationContext, ApiClient.instance))
    }

    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private var locationCallback: LocationCallback? = null
    private var requestingLocationUpdates = false

    private lateinit var destinationAdapter: ArrayAdapter<String>
    private lateinit var sourceAdapter: ArrayAdapter<String>

    private var isSettingDestinationTextProgrammatically = false
    private var isSettingSourceTextProgrammatically = false

    // This Calendar instance in MainActivity tracks UI selection for the current session for Date/Time pickers
    private val userSelectedDateTimeCalendar = Calendar.getInstance()
    // This flag is driven by UI interaction with pickers and synced with ViewModel's state
    private var isUserDateTimeExplicitlySet = false

    companion object {
        const val EXTRA_SOURCE_LOCATION = "com.example.dadstripplanner3.SOURCE_LOCATION"
        const val EXTRA_DESTINATION_LOCATION = "com.example.dadstripplanner3.DESTINATION_LOCATION"
        const val EXTRA_TRIP_OPTIONS_LIST = "com.example.dadstripplanner3.TRIP_OPTIONS_LIST"
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1001
        // SharedPreferences Keys are now fully encapsulated in TripRepository.companion object
    }

    private val locationSettingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        Log.d("LocationSettings", "Returned from location settings dialog with result code: ${result.resultCode}")
        if (binding.radioButtonCurrentLocation.isChecked) {
            viewModel.updateCurrentLocationData(null, "My Current Location (Fetching...)")
            fetchOrRequestLocation()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.root.isFocusable = true
        binding.root.isFocusableInTouchMode = true

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        setupAutocompleteAdapters()
        setupUIObservers()
        setupListeners()
        viewModel.loadLastTripPreferences() // ViewModel loads origin/dest prefs and resets its time state
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

    private fun setupUIObservers() {
        viewModel.lastQueryPreferences.observe(this) { prefs ->
            // This block now only cares about origin/destination from prefs.
            // Date/time state is reset by ViewModel's loadLastTripPreferences -> resetToDepartNowInternal.
            // The LiveData observers for selectedDateTimeCalendar, tripTimeType, and isUserDateTimeManuallySet
            // will then update the UI to "Depart: Now (Default)".

            prefs?.let {
                isSettingDestinationTextProgrammatically = true
                binding.autoCompleteTextViewDestination.setText(it.destDisplay ?: viewModel.defaultDestinationAddress)
                isSettingDestinationTextProgrammatically = false
                if (!it.destDisplay.isNullOrEmpty() && !it.destType.isNullOrEmpty() && !it.destValue.isNullOrEmpty()) {
                    viewModel.setSelectedDestination(StopFinderLocation(
                        id = it.destValue, name = it.destDisplay, type = it.destType,
                        disassembledName = it.destDisplay,
                        isGlobalId = null,
                        coordinates = null,
                        parent = null,
                        modes = null,
                        assignedStops = null,
                        matchQuality = null,
                        isBest = null
                    ))
                } else { // Fallback to hardcoded default if something is amiss with loaded dest
                    viewModel.setSelectedDestination(StopFinderLocation(
                        id = viewModel.defaultDestinationId, name = viewModel.defaultDestinationAddress, type = viewModel.defaultDestinationType,
                        disassembledName = viewModel.defaultDestinationAddress,
                        isGlobalId = null,
                        coordinates = null,
                        parent = null,
                        modes = null,
                        assignedStops = null,
                        matchQuality = null,
                        isBest = null
                    ))
                }

                if (it.useCurrentLocation) {
                    binding.radioButtonCurrentLocation.isChecked = true
                } else if (!it.originDisplay.isNullOrEmpty() && !it.originType.isNullOrEmpty() && !it.originValue.isNullOrEmpty()) {
                    binding.radioButtonCustomLocation.isChecked = true
                    isSettingSourceTextProgrammatically = true
                    binding.autoCompleteTextViewSourceCustom.setText(it.originDisplay)
                    isSettingSourceTextProgrammatically = false
                    viewModel.setSelectedOrigin(StopFinderLocation(
                        id = it.originValue, name = it.originDisplay, type = it.originType,
                        disassembledName = it.originDisplay,
                        isGlobalId = null,
                        coordinates = null,
                        parent = null,
                        modes = null,
                        assignedStops = null,
                        matchQuality = null,
                        isBest = null
                    ))
                } else { // Default to current location if no valid custom origin saved
                    binding.radioButtonCurrentLocation.isChecked = true
                }
            } ?: run { // No prefs found (e.g., first launch), set defaults for origin/dest
                isSettingDestinationTextProgrammatically = true
                binding.autoCompleteTextViewDestination.setText(viewModel.defaultDestinationAddress)
                isSettingDestinationTextProgrammatically = false
                viewModel.setSelectedDestination(StopFinderLocation(
                    id = viewModel.defaultDestinationId, name = viewModel.defaultDestinationAddress, type = viewModel.defaultDestinationType,
                    disassembledName = viewModel.defaultDestinationAddress,
                    isGlobalId = null,
                    coordinates = null,
                    parent = null,
                    modes = null,
                    assignedStops = null,
                    matchQuality = null,
                    isBest = null
                ))
                binding.radioButtonCurrentLocation.isChecked = true // Default origin
            }
            // ViewModel's init and loadLastTripPreferences handles resetting date/time LiveData.
            // initializeUIFields will then sync the Activity's current UI state correctly.
            initializeUIFields()
        }

        viewModel.destinationSuggestions.observe(this) { suggestions ->
            destinationAdapter.clear()
            if (suggestions.isNotEmpty()) { destinationAdapter.addAll(suggestions) }
            if (binding.autoCompleteTextViewDestination.isFocused && suggestions.isNotEmpty() && !binding.autoCompleteTextViewDestination.isPerformingCompletion) {
                binding.autoCompleteTextViewDestination.showDropDown()
            } else if (suggestions.isEmpty() && binding.autoCompleteTextViewDestination.isFocused) {
                binding.autoCompleteTextViewDestination.dismissDropDown()
            }
        }

        viewModel.sourceSuggestions.observe(this) { suggestions ->
            sourceAdapter.clear()
            if (suggestions.isNotEmpty()) { sourceAdapter.addAll(suggestions) }
            if (binding.autoCompleteTextViewSourceCustom.isFocused && suggestions.isNotEmpty() && !binding.autoCompleteTextViewSourceCustom.isPerformingCompletion) {
                binding.autoCompleteTextViewSourceCustom.showDropDown()
            } else if (suggestions.isEmpty() && binding.autoCompleteTextViewSourceCustom.isFocused) {
                binding.autoCompleteTextViewSourceCustom.dismissDropDown()
            }
        }

        viewModel.isLoading.observe(this) { isLoading ->
            binding.buttonNext.isEnabled = !isLoading
            if (isLoading) { Toast.makeText(this, "Processing...", Toast.LENGTH_SHORT).show() }
        }

        viewModel.userMessage.observe(this) { event ->
            event.getContentIfNotHandled()?.let { message ->
                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            }
        }

        viewModel.navigateToRouteOptions.observe(this) { event ->
            event.getContentIfNotHandled()?.let { params ->
                val intent = Intent(this, RouteOptionsActivity::class.java).apply {
                    putExtra(EXTRA_SOURCE_LOCATION, params.sourceDisplay)
                    putExtra(EXTRA_DESTINATION_LOCATION, params.destinationDisplay)
                    putParcelableArrayListExtra(EXTRA_TRIP_OPTIONS_LIST, params.trips)
                }
                startActivity(intent)
            }
        }

        viewModel.selectedDateTimeCalendar.observe(this) { calendar ->
            userSelectedDateTimeCalendar.timeInMillis = calendar.timeInMillis // Sync local UI calendar from VM
            updateSelectedDateTimeDisplay()
        }
        viewModel.isUserDateTimeManuallySet.observe(this) { isSet ->
            isUserDateTimeExplicitlySet = isSet // Sync local UI flag from VM
            updateSelectedDateTimeDisplay()
        }
        viewModel.tripTimeType.observe(this) { type ->
            if (type == "dep") {
                if (!binding.radioButtonDepartAt.isChecked) binding.radioButtonDepartAt.isChecked = true
            } else {
                if (!binding.radioButtonArriveBy.isChecked) binding.radioButtonArriveBy.isChecked = true
            }
            updateSelectedDateTimeDisplay() // Update text to reflect "Depart" or "Arrive"
        }
        viewModel.favoritesList.observe(this) { favorites ->
            Log.d("MainActivity", "Favorites list updated, size: ${favorites.size}")
            // The View Favorites dialog will use this LiveData when it's shown
        }
    }

    private fun setupListeners() {
        binding.radioGroupSource.setOnCheckedChangeListener { _, checkedId ->
            val isCurrentLocation = checkedId == R.id.radioButtonCurrentLocation
            viewModel.onOriginRadioButtonSelected(isCurrentLocation) // Update ViewModel state

            if (isCurrentLocation) {
                binding.autoCompleteTextViewSourceCustom.isEnabled = false
                binding.autoCompleteTextViewSourceCustom.alpha = 0.5f
                isSettingSourceTextProgrammatically = true
                binding.autoCompleteTextViewSourceCustom.text.clear()
                isSettingSourceTextProgrammatically = false
                hideKeyboard(binding.autoCompleteTextViewSourceCustom)
                fetchOrRequestLocation() // Activity handles initiating location fetch
            } else { // Custom Location
                binding.autoCompleteTextViewSourceCustom.isEnabled = true
                binding.autoCompleteTextViewSourceCustom.alpha = 1.0f
                binding.autoCompleteTextViewSourceCustom.requestFocus()
                val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showSoftInput(binding.autoCompleteTextViewSourceCustom, InputMethodManager.SHOW_IMPLICIT)
                stopLocationUpdates()
            }
        }

        binding.autoCompleteTextViewDestination.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (!isSettingDestinationTextProgrammatically) {
                    viewModel.clearSelectedDestinationAfterTextChange()
                }
            }
            override fun afterTextChanged(s: Editable?) {
                if (isSettingDestinationTextProgrammatically) return
                viewModel.handleDestinationInputChanged(s.toString())
            }
        })

        binding.autoCompleteTextViewDestination.setOnItemClickListener { parent, _, position, _ ->
            val selectedSuggestionString = parent.adapter.getItem(position) as String
            viewModel.destinationSuggestionSelected(selectedSuggestionString) // Notifies ViewModel

            isSettingDestinationTextProgrammatically = true
            binding.autoCompleteTextViewDestination.setText(selectedSuggestionString, false) // Update text without filtering
            binding.autoCompleteTextViewDestination.setSelection(binding.autoCompleteTextViewDestination.text.length) // Move cursor to end
            isSettingDestinationTextProgrammatically = false

            binding.autoCompleteTextViewDestination.dismissDropDown()
            hideKeyboard(binding.autoCompleteTextViewDestination)
            binding.root.requestFocus() // Clear focus from EditText
        }

        binding.autoCompleteTextViewSourceCustom.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (!isSettingSourceTextProgrammatically) {
                    viewModel.clearSelectedOriginAfterTextChange()
                }
            }
            override fun afterTextChanged(s: Editable?) {
                if (isSettingSourceTextProgrammatically) return
                if (binding.radioButtonCustomLocation.isChecked) { // Only fetch if custom source is active
                    viewModel.handleSourceInputChanged(s.toString())
                }
            }
        })

        binding.autoCompleteTextViewSourceCustom.setOnItemClickListener { parent, _, position, _ ->
            val selectedSuggestionString = parent.adapter.getItem(position) as String
            viewModel.sourceSuggestionSelected(selectedSuggestionString) // Notifies ViewModel

            isSettingSourceTextProgrammatically = true
            binding.autoCompleteTextViewSourceCustom.setText(selectedSuggestionString, false)
            binding.autoCompleteTextViewSourceCustom.setSelection(binding.autoCompleteTextViewSourceCustom.text.length)
            isSettingSourceTextProgrammatically = false

            binding.autoCompleteTextViewSourceCustom.dismissDropDown()
            hideKeyboard(binding.autoCompleteTextViewSourceCustom)
            binding.root.requestFocus()
        }

        binding.buttonChangeDateTime.setOnClickListener {
            showDatePickerDialog()
        }

        binding.radioGroupTripTimeType.setOnCheckedChangeListener { _, checkedId ->
            val type = if (checkedId == R.id.radioButtonDepartAt) "dep" else "arr"
            viewModel.updateTripTimeType(type) // Update ViewModel state
            // If user had explicitly picked a date/time, this flag (isUserDateTimeExplicitlySet) remains true
            // If it was "Now", it remains "Now" but for the new "Depart"/"Arrive" type.
            // The ViewModel's updateSelectedDateTime will be called from date/time pickers if user changes.
            // The display will update via observers.
        }

        binding.buttonAddToFavorites.setOnClickListener {
            promptForFavoriteNameAndAdd()
        }

        binding.buttonViewFavorites.setOnClickListener {
            showFavoritesDialog()
        }

        binding.buttonNext.setOnClickListener {
            if (binding.radioButtonCurrentLocation.isChecked) {
                if (!isLocationPermissionGranted()) { Toast.makeText(this, "Location permission needed.", Toast.LENGTH_LONG).show(); requestLocationPermission(); return@setOnClickListener }
                if (!isLocationServicesEnabled()) { Toast.makeText(this, "Please turn on Location Services.", Toast.LENGTH_LONG).show(); promptToEnableLocationServices(); return@setOnClickListener }
                if (viewModel.currentLocationData.value == null) { Toast.makeText(this, "Current location still fetching or unavailable.", Toast.LENGTH_LONG).show(); fetchOrRequestLocation(); return@setOnClickListener }
            }

            val customOriginInputText = binding.autoCompleteTextViewSourceCustom.text.toString().trim()
            val destinationInputText = binding.autoCompleteTextViewDestination.text.toString().trim()
            val isCurrentLocOrigin = binding.radioButtonCurrentLocation.isChecked

            // These are the final resolved values to be used for API call and for display in next screen
            var originDisplayForAPI: String
            var originTypeForAPI: String
            var originValueForAPI: String

            if (isCurrentLocOrigin) {
                originDisplayForAPI = viewModel.currentLocationDisplayString.value ?: "My Current Location"
                viewModel.currentLocationData.value?.let { loc ->
                    originTypeForAPI = "coord"
                    originValueForAPI = "${loc.longitude}:${loc.latitude}:EPSG:4326"
                } ?: return@setOnClickListener // Should be caught by earlier checks
            } else {
                originDisplayForAPI = customOriginInputText
                val selectedOrigin = viewModel.selectedOriginFromAutocomplete.value
                if (selectedOrigin != null && (selectedOrigin.name == originDisplayForAPI || selectedOrigin.disassembledName == originDisplayForAPI || viewModel.formatSuggestion(selectedOrigin) == originDisplayForAPI) && selectedOrigin.id != null && selectedOrigin.type != null) {
                    originTypeForAPI = selectedOrigin.type!!
                    originValueForAPI = selectedOrigin.id!!
                } else {
                    originTypeForAPI = "any"
                    originValueForAPI = originDisplayForAPI
                }
            }

            val destDisplayForAPI = destinationInputText
            var destTypeForAPI: String
            var destValueForAPI: String
            val selectedDest = viewModel.selectedDestinationFromAutocomplete.value

            if (selectedDest != null && (selectedDest.name == destDisplayForAPI || selectedDest.disassembledName == destDisplayForAPI || viewModel.formatSuggestion(selectedDest) == destDisplayForAPI) && selectedDest.id != null && selectedDest.type != null) {
                destTypeForAPI = selectedDest.type!!
                destValueForAPI = selectedDest.id!!
            } else {
                if (destDisplayForAPI.equals(viewModel.defaultDestinationAddress, ignoreCase = true) && selectedDest == null) {
                    destTypeForAPI = viewModel.defaultDestinationType
                    destValueForAPI = viewModel.defaultDestinationId
                } else {
                    destTypeForAPI = "any"
                    destValueForAPI = destDisplayForAPI
                }
            }

            if (originValueForAPI.isEmpty() && !isCurrentLocOrigin) { Toast.makeText(this, "Please enter a source address.", Toast.LENGTH_SHORT).show(); binding.autoCompleteTextViewSourceCustom.error = "Source cannot be empty"; return@setOnClickListener }
            if (destValueForAPI.isEmpty()) { Toast.makeText(this, "Please enter a destination address.", Toast.LENGTH_SHORT).show(); binding.autoCompleteTextViewDestination.error = "Destination cannot be empty"; return@setOnClickListener }

            // Use the Activity's current userSelectedDateTimeCalendar and isUserDateTimeExplicitlySet
            // These are kept in sync with ViewModel's LiveData.
            val calendarToUse = if (isUserDateTimeExplicitlySet) userSelectedDateTimeCalendar else Calendar.getInstance()
            val dateFormat = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
            val timeFormat = SimpleDateFormat("HHmm", Locale.getDefault())
            val depArrMacro = if (binding.radioButtonDepartAt.isChecked) "dep" else "arr"

            viewModel.onPlanTripClicked(
                originDisplayForSave = originDisplayForAPI, // What user sees/saved as origin name
                originTypeForSave = originTypeForAPI,
                originValueForSave = originValueForAPI,
                destDisplayForSave = destDisplayForAPI,   // What user sees/saved as dest name
                destTypeForSave = destTypeForAPI,
                destValueForSave = destValueForAPI,
                useCurrentLocationForSave = isCurrentLocOrigin,
                tripDate = dateFormat.format(calendarToUse.time),
                tripTime = timeFormat.format(calendarToUse.time),
                depArrMacro = depArrMacro
            )
        }
    }

    // This function is called from setupUIObservers after ViewModel loads preferences
    private fun initializeUIFields() {
        if (binding.radioButtonCurrentLocation.isChecked) {
            binding.autoCompleteTextViewSourceCustom.isEnabled = false
            binding.autoCompleteTextViewSourceCustom.alpha = 0.5f
            if (viewModel.currentLocationData.value == null && viewModel.currentLocationDisplayString.value?.contains("Fetching") != true) {
                viewModel.updateCurrentLocationData(null, "My Current Location (Fetching...)")
                fetchOrRequestLocation()
            } else if (viewModel.currentLocationData.value == null && viewModel.currentLocationDisplayString.value?.contains("Fetching") == true) {
                // If already "Fetching..." but no data, re-initiate fetch in case it failed silently
                fetchOrRequestLocation()
            }
        } else { // Custom location is selected
            binding.autoCompleteTextViewSourceCustom.isEnabled = true
            binding.autoCompleteTextViewSourceCustom.alpha = 1.0f
        }
        // Date/time display is updated via observers on ViewModel's LiveData.
        // Call updateSelectedDateTimeDisplay here to ensure it reflects the (now defaulted by VM) state.
        updateSelectedDateTimeDisplay()
    }

    private fun showDatePickerDialog() {
        val calToShow = if (isUserDateTimeExplicitlySet) userSelectedDateTimeCalendar else Calendar.getInstance()
        val year = calToShow.get(Calendar.YEAR)
        val month = calToShow.get(Calendar.MONTH)
        val day = calToShow.get(Calendar.DAY_OF_MONTH)

        val datePickerDialog = DatePickerDialog(this,
            { _, selectedYear, selectedMonth, selectedDayOfMonth ->
                // Set date part on the activity's calendar
                userSelectedDateTimeCalendar.set(Calendar.YEAR, selectedYear)
                userSelectedDateTimeCalendar.set(Calendar.MONTH, selectedMonth)
                userSelectedDateTimeCalendar.set(Calendar.DAY_OF_MONTH, selectedDayOfMonth)
                // Time part will be set by TimePickerDialog, default to current time of day for the newly selected date
                showTimePickerDialog()
            }, year, month, day
        )
        datePickerDialog.datePicker.minDate = System.currentTimeMillis() - 1000 // Allow today
        datePickerDialog.show()
    }

    private fun showTimePickerDialog() {
        // userSelectedDateTimeCalendar now has the date picked by DatePickerDialog.
        // For time, default to current time of day for the selected date.
        val calToUseForDefaults = Calendar.getInstance() // Always suggest current time of day as default in picker
        val hour = calToUseForDefaults.get(Calendar.HOUR_OF_DAY)
        val minute = calToUseForDefaults.get(Calendar.MINUTE)

        val timePickerDialog = TimePickerDialog(this,
            { _, selectedHour, selectedMinute ->
                userSelectedDateTimeCalendar.set(Calendar.HOUR_OF_DAY, selectedHour)
                userSelectedDateTimeCalendar.set(Calendar.MINUTE, selectedMinute)
                // isUserDateTimeExplicitlySet = true; // ViewModel sets its flag when updateSelectedDateTime is called
                viewModel.updateSelectedDateTime(userSelectedDateTimeCalendar) // Update ViewModel
            }, hour, minute, false // false for 12-hour AM/PM format
        )
        timePickerDialog.show()
    }

    private fun updateSelectedDateTimeDisplay() {
        val timeTypeString = if (binding.radioButtonDepartAt.isChecked) "Depart" else "Arrive"
        // isUserDateTimeExplicitlySet is the Activity's local flag, kept in sync with ViewModel's LiveData
        if (isUserDateTimeExplicitlySet) {
            val dateFormat = SimpleDateFormat("EEE, MMM d, h:mm a", Locale.getDefault())
            binding.textViewSelectedDateTime.text = "$timeTypeString: ${dateFormat.format(userSelectedDateTimeCalendar.time)}"
        } else {
            binding.textViewSelectedDateTime.text = "$timeTypeString: Now (Default)"
        }
    }

    private fun hideKeyboard(view: View) {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
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
        val locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
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
            Log.d("LocationSettings", "Location settings satisfied.")
            if (binding.radioButtonCurrentLocation.isChecked) fetchOrRequestLocation()
        }
        task.addOnFailureListener { exception ->
            if (exception is ResolvableApiException) {
                try {
                    locationSettingsLauncher.launch(IntentSenderRequest.Builder(exception.resolution.intentSender).build())
                } catch (sendEx: Exception) { Log.e("LocationSettings", "Error starting settings resolution", sendEx) }
            } else { viewModel.updateCurrentLocationData(null, "My Current Location (Settings Issue)") }
        }
    }
    private fun fetchOrRequestLocation() {
        if (!isLocationPermissionGranted()) {
            viewModel.updateCurrentLocationData(null, "My Current Location (Permission Needed)")
            requestLocationPermission(); return
        }
        if (!isLocationServicesEnabled()) {
            viewModel.updateCurrentLocationData(null, "My Current Location (Services Off)")
            promptToEnableLocationServices(); return
        }
        getLastKnownLocation()
    }
    @SuppressLint("MissingPermission")
    private fun getLastKnownLocation() {
        viewModel.updateCurrentLocationData(null, "My Current Location (Fetching...)")
        fusedLocationClient.lastLocation
            .addOnSuccessListener { location ->
                if (location != null) updateLocationDetails(location)
                else startLocationUpdates()
            }
            .addOnFailureListener { _ ->
                viewModel.updateCurrentLocationData(null, "My Current Location (Error LastKnown)")
                startLocationUpdates()
            }
    }
    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        if (requestingLocationUpdates || !isLocationServicesEnabled()) {
            if (!isLocationServicesEnabled()) viewModel.updateCurrentLocationData(null, "My Current Location (Services Off)")
            return
        }
        viewModel.updateCurrentLocationData(null, "My Current Location (Updating...)")
        requestingLocationUpdates = true
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10000).setMinUpdateIntervalMillis(5000).setMaxUpdates(1).build()
        if (locationCallback == null) {
            locationCallback = object : LocationCallback() {
                override fun onLocationResult(locationResult: LocationResult) {
                    locationResult.lastLocation?.let { updateLocationDetails(it) } ?: run {
                        if (viewModel.currentLocationData.value == null) viewModel.updateCurrentLocationData(null, "My Current Location (Update Failed)")
                    }
                    stopLocationUpdates()
                }
            }
        }
        try {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback!!, Looper.getMainLooper())
        } catch (e: SecurityException) {
            viewModel.updateCurrentLocationData(null, "My Current Location (Security Issue)")
            requestingLocationUpdates = false
        }
    }
    private fun stopLocationUpdates() {
        if (requestingLocationUpdates) {
            locationCallback?.let { fusedLocationClient.removeLocationUpdates(it).addOnCompleteListener { requestingLocationUpdates = false } }
                ?: run { requestingLocationUpdates = false }
        }
    }
    private fun updateLocationDetails(location: Location) {
        val latStr = String.format(Locale.US, "%.3f", location.latitude)
        val lonStr = String.format(Locale.US, "%.3f", location.longitude)
        viewModel.updateCurrentLocationData(location, "My Current Location (Lat: $latStr, Lon: $lonStr)")
    }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (binding.radioButtonCurrentLocation.isChecked) {
                    viewModel.updateCurrentLocationData(null, "My Current Location (Fetching...)")
                    fetchOrRequestLocation()
                }
            } else {
                viewModel.updateCurrentLocationData(null, "My Current Location (Permission Denied)")
                if (binding.radioButtonCurrentLocation.isChecked) binding.radioButtonCustomLocation.isChecked = true
            }
        }
    }

    // --- Favorites UI Methods ---
    private fun promptForFavoriteNameAndAdd() {
        val isCurrentLocOrigin = binding.radioButtonCurrentLocation.isChecked
        var originDisplay: String
        var originType: String
        var originValue: String

        if (isCurrentLocOrigin) {
            originDisplay = viewModel.currentLocationDisplayString.value ?: "My Current Location"
            viewModel.currentLocationData.value?.let {
                originType = "coord"
                originValue = "${it.longitude}:${it.latitude}:EPSG:4326"
            } ?: run {
                Toast.makeText(this, "Current location data needed to save as favorite origin.", Toast.LENGTH_SHORT).show()
                return
            }
        } else {
            originDisplay = binding.autoCompleteTextViewSourceCustom.text.toString().trim()
            if (originDisplay.isEmpty()) {
                Toast.makeText(this, "Custom origin is empty.", Toast.LENGTH_SHORT).show()
                return
            }
            val selectedOrigin = viewModel.selectedOriginFromAutocomplete.value
            if (selectedOrigin != null && (selectedOrigin.name == originDisplay || selectedOrigin.disassembledName == originDisplay || viewModel.formatSuggestion(selectedOrigin) == originDisplay) && selectedOrigin.id != null && selectedOrigin.type != null) {
                originType = selectedOrigin.type!!
                originValue = selectedOrigin.id!!
            } else {
                originType = "any"
                originValue = originDisplay
            }
        }

        val destDisplay = binding.autoCompleteTextViewDestination.text.toString().trim()
        if (destDisplay.isEmpty()) {
            Toast.makeText(this, "Destination is empty.", Toast.LENGTH_SHORT).show()
            return
        }
        var destType: String
        var destValue: String
        val selectedDest = viewModel.selectedDestinationFromAutocomplete.value
        if (selectedDest != null && (selectedDest.name == destDisplay || selectedDest.disassembledName == destDisplay || viewModel.formatSuggestion(selectedDest) == destDisplay) && selectedDest.id != null && selectedDest.type != null) {
            destType = selectedDest.type!!
            destValue = selectedDest.id!!
        } else {
            if (destDisplay.equals(viewModel.defaultDestinationAddress, ignoreCase = true) && selectedDest == null) {
                destType = viewModel.defaultDestinationType
                destValue = viewModel.defaultDestinationId
            } else {
                destType = "any"
                destValue = destDisplay
            }
        }

        val editText = EditText(this).apply { inputType = InputType.TYPE_CLASS_TEXT }
        AlertDialog.Builder(this)
            .setTitle("Save Favorite Trip")
            .setMessage("Enter a name for this favorite (e.g., Home to Work):")
            .setView(editText)
            .setPositiveButton("Save") { _, _ ->
                val favoriteName = editText.text.toString().trim()
                if (favoriteName.isNotEmpty()) {
                    val queryData = MainViewModel.FavoriteTripQueryData(
                        originDisplay, originType, originValue,
                        destDisplay, destType, destValue,
                        isCurrentLocOrigin
                    )
                    viewModel.addFavorite(favoriteName, queryData)
                } else {
                    Toast.makeText(this, "Favorite name cannot be empty.", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showFavoritesDialog() {
        val favorites = viewModel.favoritesList.value ?: emptyList()
        if (favorites.isEmpty()) {
            Toast.makeText(this, "No favorites saved yet.", Toast.LENGTH_SHORT).show()
            return
        }
        val favoriteNames = favorites.map { it.favoriteName }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Select a Favorite Trip")
            .setItems(favoriteNames) { _, which ->
                val selectedFav = favorites[which]
                applyFavoriteToFields(selectedFav)
            }
            .setNeutralButton("Manage") { _, _ ->
                showManageFavoritesDialog(favorites)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showManageFavoritesDialog(currentFavorites: List<FavoriteTripQuery>) {
        if (currentFavorites.isEmpty()) {
            Toast.makeText(this, "No favorites to manage.", Toast.LENGTH_SHORT).show()
            return
        }
        val favoriteNames = currentFavorites.map { it.favoriteName }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Manage Favorites (Tap to Delete)")
            .setItems(favoriteNames) { _, which ->
                val favNameToDelete = favoriteNames[which]
                AlertDialog.Builder(this)
                    .setTitle("Delete Favorite")
                    .setMessage("Are you sure you want to delete '$favNameToDelete'?")
                    .setPositiveButton("Delete") { _, _ ->
                        viewModel.removeFavorite(favNameToDelete)
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
            .setPositiveButton("Done", null)
            .show()
    }

    private fun applyFavoriteToFields(favorite: FavoriteTripQuery) {
        isSettingDestinationTextProgrammatically = true
        binding.autoCompleteTextViewDestination.setText(favorite.destinationDisplayName)
        viewModel.setSelectedDestination(StopFinderLocation( // Create a placeholder StopFinderLocation
            id = favorite.destinationValue,
            name = favorite.destinationDisplayName,
            type = favorite.destinationType,
            disassembledName = favorite.destinationDisplayName,
            isGlobalId = null,
            coordinates = null,
            parent = null,
            modes = null,
            assignedStops = null,
            matchQuality = null,
            isBest = null // Use display name if disassembled not explicitly saved
            // other fields can be null as they are for display/internal use by stop_finder mostly
        ))
        isSettingDestinationTextProgrammatically = false

        if (favorite.isOriginCurrentLocation) {
            binding.radioButtonCurrentLocation.isChecked = true // This will trigger listener which calls fetchOrRequestLocation
        } else {
            binding.radioButtonCustomLocation.isChecked = true
            isSettingSourceTextProgrammatically = true
            binding.autoCompleteTextViewSourceCustom.setText(favorite.originDisplayName)
            viewModel.setSelectedOrigin(StopFinderLocation(
                id = favorite.originValue,
                name = favorite.originDisplayName,
                type = favorite.originType,
                disassembledName = favorite.originDisplayName,
                isGlobalId = null,
                coordinates = null,
                parent = null,
                modes = null,
                assignedStops = null,
                matchQuality = null,
                isBest = null
            ))
            isSettingSourceTextProgrammatically = false
        }
        // Always reset date/time options when loading a favorite to "Now (Default)"
        isUserDateTimeExplicitlySet = false // Reset Activity's local flag
        userSelectedDateTimeCalendar.timeInMillis = System.currentTimeMillis() // Reset Activity's calendar
        viewModel.resetToDepartNow() // Tell ViewModel to reset its date/time state

        Toast.makeText(this, "'${favorite.favoriteName}' loaded.", Toast.LENGTH_SHORT).show()
    }
}