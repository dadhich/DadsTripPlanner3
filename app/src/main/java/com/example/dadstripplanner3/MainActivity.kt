package com.example.dadstripplanner3

import android.Manifest
import android.annotation.SuppressLint
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
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
import androidx.activity.viewModels
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
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var sharedPreferences: SharedPreferences

    private var locationCallback: LocationCallback? = null
    private var requestingLocationUpdates = false

    private lateinit var destinationAdapter: ArrayAdapter<String>
    private lateinit var sourceAdapter: ArrayAdapter<String>

    private var isSettingDestinationTextProgrammatically = false
    private var isSettingSourceTextProgrammatically = false

    // Store user's explicitly selected date and time
    private val userSelectedCalendar = Calendar.getInstance()
    private var isUserDateTimeExplicitlySet = false // True if user picked a date/time

    companion object {
        const val EXTRA_SOURCE_LOCATION = "com.example.dadstripplanner3.SOURCE_LOCATION"
        const val EXTRA_DESTINATION_LOCATION = "com.example.dadstripplanner3.DESTINATION_LOCATION"
        const val EXTRA_TRIP_OPTIONS_LIST = "com.example.dadstripplanner3.TRIP_OPTIONS_LIST"
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1001

        private const val PREFS_NAME = "TripPlannerPrefs"
        private const val KEY_LAST_ORIGIN_DISPLAY = "lastOriginDisplay"
        private const val KEY_LAST_ORIGIN_TYPE = "lastOriginType"
        private const val KEY_LAST_ORIGIN_VALUE = "lastOriginValue"
        private const val KEY_LAST_DEST_DISPLAY = "lastDestDisplay"
        private const val KEY_LAST_DEST_TYPE = "lastDestType"
        private const val KEY_LAST_DEST_VALUE = "lastDestValue"
        private const val KEY_LAST_USE_CURRENT_LOCATION = "lastUseCurrentLocation"
        private const val KEY_LAST_SELECTED_DATE_MILLIS = "lastSelectedDateMillis" // Stores userSelectedCalendar.timeInMillis
        private const val KEY_IS_DATETIME_MANUALLY_SET = "isDateTimeManuallySet" // Tracks if user explicitly set it
        private const val KEY_LAST_SELECTED_TIME_TYPE = "lastSelectedTimeType" // "dep" or "arr"

        private const val DEFAULT_DESTINATION_ADDRESS = "Hornsby Station, Hornsby"
        private const val DEFAULT_DESTINATION_ID = "207720"
        private const val DEFAULT_DESTINATION_TYPE = "stop"
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
        sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        setupAutocompleteAdapters()
        setupUIObservers()
        setupListeners()
        loadLastSelectionsOrDefaults()
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

        // Observe ViewModel changes for selected date/time to update UI
        // This is primarily for initial load from ViewModel or if ViewModel logic changes it
        viewModel.selectedDateTimeCalendar.observe(this) { calendar ->
            userSelectedCalendar.timeInMillis = calendar.timeInMillis // Sync local calendar with ViewModel
            updateSelectedDateTimeDisplay()
        }
        viewModel.isUserDateTimeManuallySet.observe(this) { isSet ->
            isUserDateTimeExplicitlySet = isSet // Sync local flag with ViewModel
            updateSelectedDateTimeDisplay()
        }
        viewModel.tripTimeType.observe(this) { type ->
            if (type == "dep") {
                if (!binding.radioButtonDepartAt.isChecked) binding.radioButtonDepartAt.isChecked = true
            } else {
                if (!binding.radioButtonArriveBy.isChecked) binding.radioButtonArriveBy.isChecked = true
            }
            updateSelectedDateTimeDisplay()
        }
    }

    private fun setupListeners() {
        binding.radioGroupSource.setOnCheckedChangeListener { _, checkedId ->
            val isCurrentLocation = checkedId == R.id.radioButtonCurrentLocation
            viewModel.onOriginRadioButtonSelected(isCurrentLocation)
            if (isCurrentLocation) {
                binding.autoCompleteTextViewSourceCustom.isEnabled = false
                binding.autoCompleteTextViewSourceCustom.alpha = 0.5f
                isSettingSourceTextProgrammatically = true
                binding.autoCompleteTextViewSourceCustom.text.clear()
                isSettingSourceTextProgrammatically = false
                hideKeyboard(binding.autoCompleteTextViewSourceCustom)
                fetchOrRequestLocation()
            } else {
                binding.autoCompleteTextViewSourceCustom.isEnabled = true
                binding.autoCompleteTextViewSourceCustom.alpha = 1.0f
                binding.autoCompleteTextViewSourceCustom.requestFocus()
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
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
            viewModel.destinationSuggestionSelected(selectedSuggestionString)
            isSettingDestinationTextProgrammatically = true
            binding.autoCompleteTextViewDestination.setText(selectedSuggestionString, false)
            binding.autoCompleteTextViewDestination.setSelection(binding.autoCompleteTextViewDestination.text.length)
            isSettingDestinationTextProgrammatically = false
            binding.autoCompleteTextViewDestination.dismissDropDown()
            hideKeyboard(binding.autoCompleteTextViewDestination)
            binding.root.requestFocus()
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
                if (binding.radioButtonCustomLocation.isChecked) {
                    viewModel.handleSourceInputChanged(s.toString())
                }
            }
        })

        binding.autoCompleteTextViewSourceCustom.setOnItemClickListener { parent, _, position, _ ->
            val selectedSuggestionString = parent.adapter.getItem(position) as String
            viewModel.sourceSuggestionSelected(selectedSuggestionString)
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
            viewModel.updateTripTimeType(type) // Update ViewModel
            // If user interacts with these, we consider date/time manually set if a date was already picked.
            // If not, "Now" still applies until they pick a date/time.
            // The display update is handled by the ViewModel observer.
            if (isUserDateTimeExplicitlySet) { // Update display only if they had already set a custom time
                updateSelectedDateTimeDisplay()
            }
        }

        binding.buttonNext.setOnClickListener {
            if (binding.radioButtonCurrentLocation.isChecked) {
                if (!isLocationPermissionGranted()) {
                    Toast.makeText(this, "Location permission needed.", Toast.LENGTH_LONG).show()
                    requestLocationPermission(); return@setOnClickListener
                }
                if (!isLocationServicesEnabled()) {
                    Toast.makeText(this, "Please turn on Location Services.", Toast.LENGTH_LONG).show()
                    promptToEnableLocationServices(); return@setOnClickListener
                }
                if (viewModel.currentLocationData.value == null) {
                    Toast.makeText(this, "Current location still fetching or unavailable.", Toast.LENGTH_LONG).show()
                    fetchOrRequestLocation(); return@setOnClickListener
                }
            }

            val customOriginInputText = binding.autoCompleteTextViewSourceCustom.text.toString().trim()
            val destinationInputText = binding.autoCompleteTextViewDestination.text.toString().trim()

            val isCurrentLocOrigin = binding.radioButtonCurrentLocation.isChecked
            var originDisplayForSave: String
            var originTypeForSave: String
            var originValueForSave: String

            if (isCurrentLocOrigin) {
                originDisplayForSave = viewModel.currentLocationDisplayString.value ?: "My Current Location"
                viewModel.currentLocationData.value?.let { loc ->
                    originTypeForSave = "coord"
                    originValueForSave = "${loc.longitude}:${loc.latitude}:EPSG:4326"
                } ?: return@setOnClickListener
            } else {
                originDisplayForSave = customOriginInputText
                val selectedOrigin = viewModel.selectedOriginFromAutocomplete.value
                if (selectedOrigin != null && (selectedOrigin.name == originDisplayForSave || selectedOrigin.disassembledName == originDisplayForSave || viewModel.formatSuggestion(selectedOrigin) == originDisplayForSave) && selectedOrigin.id != null && selectedOrigin.type != null) {
                    originTypeForSave = selectedOrigin.type!!
                    originValueForSave = selectedOrigin.id!!
                } else {
                    originTypeForSave = "any"
                    originValueForSave = originDisplayForSave
                }
            }

            val destDisplayForSave = destinationInputText
            var destTypeForSave: String
            var destValueForSave: String
            val selectedDest = viewModel.selectedDestinationFromAutocomplete.value

            if (selectedDest != null && (selectedDest.name == destDisplayForSave || selectedDest.disassembledName == destDisplayForSave || viewModel.formatSuggestion(selectedDest) == destDisplayForSave) && selectedDest.id != null && selectedDest.type != null) {
                destTypeForSave = selectedDest.type!!
                destValueForSave = selectedDest.id!!
            } else {
                if (destDisplayForSave.equals(viewModel.defaultDestinationAddress, ignoreCase = true) && selectedDest == null) {
                    destTypeForSave = viewModel.defaultDestinationType
                    destValueForSave = viewModel.defaultDestinationId
                } else {
                    destTypeForSave = "any"
                    destValueForSave = destDisplayForSave
                }
            }

            if (originValueForSave.isEmpty() && !isCurrentLocOrigin) { Toast.makeText(this, "Please enter a source address.", Toast.LENGTH_SHORT).show(); return@setOnClickListener }
            if (destValueForSave.isEmpty()) { Toast.makeText(this, "Please enter a destination address.", Toast.LENGTH_SHORT).show(); return@setOnClickListener }

            val calendarToUse = if (isUserDateTimeExplicitlySet) userSelectedCalendar else Calendar.getInstance()
            val dateFormat = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
            val timeFormat = SimpleDateFormat("HHmm", Locale.getDefault())

            viewModel.onPlanTripClicked(
                isCurrentLocationOrigin = isCurrentLocOrigin,
                customOriginText = customOriginInputText,
                destinationText = destinationInputText,
                originDisplayForSave = originDisplayForSave,
                originTypeForSave = originTypeForSave,
                originValueForSave = originValueForSave,
                destDisplayForSave = destDisplayForSave,
                destTypeForSave = destTypeForSave,
                destValueForSave = destValueForSave,
                tripDate = dateFormat.format(calendarToUse.time),
                tripTime = timeFormat.format(calendarToUse.time),
                depArrMacro = if (binding.radioButtonDepartAt.isChecked) "dep" else "arr"
            )

            saveLastSelections(
                originDisplay = originDisplayForSave, originType = originTypeForSave, originValue = originValueForSave,
                destDisplay = destDisplayForSave, destType = destTypeForSave, destValue = destValueForSave,
                useCurrentLocation = isCurrentLocOrigin,
                // Save the explicitly set time, or -1 if "Now (Default)" was used
                selectedMillis = if (isUserDateTimeExplicitlySet) userSelectedCalendar.timeInMillis else -1L,
                selectedTimeType = if (binding.radioButtonDepartAt.isChecked) "dep" else "arr"
            )
        }
    }

    private fun loadLastSelectionsOrDefaults() {
        val lastUseCurrentLocation = sharedPreferences.getBoolean(KEY_LAST_USE_CURRENT_LOCATION, true)
        val lastOriginDisplay = sharedPreferences.getString(KEY_LAST_ORIGIN_DISPLAY, null)
        val lastOriginType = sharedPreferences.getString(KEY_LAST_ORIGIN_TYPE, null)
        val lastOriginValue = sharedPreferences.getString(KEY_LAST_ORIGIN_VALUE, null)

        val lastDestDisplay = sharedPreferences.getString(KEY_LAST_DEST_DISPLAY, viewModel.defaultDestinationAddress)
        val lastDestType = sharedPreferences.getString(KEY_LAST_DEST_TYPE, viewModel.defaultDestinationType)
        val lastDestValue = sharedPreferences.getString(KEY_LAST_DEST_VALUE, viewModel.defaultDestinationId)

        val lastSelectedTimeMillis = sharedPreferences.getLong(KEY_LAST_SELECTED_DATE_MILLIS, -1L)
        val lastSelectedTimeType = sharedPreferences.getString(KEY_LAST_SELECTED_TIME_TYPE, "dep") ?: "dep"

        isSettingDestinationTextProgrammatically = true
        binding.autoCompleteTextViewDestination.setText(lastDestDisplay)
        isSettingDestinationTextProgrammatically = false
        if (!lastDestDisplay.isNullOrEmpty() && !lastDestType.isNullOrEmpty() && !lastDestValue.isNullOrEmpty()) {
            viewModel.setSelectedDestination(StopFinderLocation(
                id = lastDestValue, name = lastDestDisplay, type = lastDestType,
                disassembledName = lastDestDisplay, coordinates = null, parent = null, modes = null, assignedStops = null, matchQuality = null, isBest = null, isGlobalId = null
            ))
        }

        if (lastUseCurrentLocation) {
            binding.radioButtonCurrentLocation.isChecked = true
        } else if (!lastOriginDisplay.isNullOrEmpty() && !lastOriginType.isNullOrEmpty() && !lastOriginValue.isNullOrEmpty()) {
            binding.radioButtonCustomLocation.isChecked = true
            isSettingSourceTextProgrammatically = true
            binding.autoCompleteTextViewSourceCustom.setText(lastOriginDisplay)
            isSettingSourceTextProgrammatically = false
            viewModel.setSelectedOrigin(StopFinderLocation(
                id = lastOriginValue, name = lastOriginDisplay, type = lastOriginType,
                disassembledName = lastOriginDisplay, coordinates = null, parent = null, modes = null, assignedStops = null, matchQuality = null, isBest = null, isGlobalId = null
            ))
        } else {
            binding.radioButtonCurrentLocation.isChecked = true
        }

        if (lastSelectedTimeMillis != -1L) {
            userSelectedCalendar.timeInMillis = lastSelectedTimeMillis
            isUserDateTimeExplicitlySet = true
            viewModel.updateSelectedDateTime(userSelectedCalendar) // Update ViewModel which will trigger observer
        } else {
            isUserDateTimeExplicitlySet = false // Default to "Now"
            viewModel.resetToDepartNow()      // Reset ViewModel to current time / "dep"
        }
        viewModel.updateTripTimeType(lastSelectedTimeType) // This will also trigger UI update via observer

        initializeUIFields() // Sets enabled states and calls updateSelectedDateTimeDisplay
    }

    private fun saveLastSelections(
        originDisplay: String, originType: String, originValue: String,
        destDisplay: String, destType: String, destValue: String,
        useCurrentLocation: Boolean,
        selectedMillis: Long, selectedTimeType: String
    ) {
        with(sharedPreferences.edit()) {
            putString(KEY_LAST_ORIGIN_DISPLAY, originDisplay)
            putString(KEY_LAST_ORIGIN_TYPE, originType)
            putString(KEY_LAST_ORIGIN_VALUE, originValue)
            putString(KEY_LAST_DEST_DISPLAY, destDisplay)
            putString(KEY_LAST_DEST_TYPE, destType)
            putString(KEY_LAST_DEST_VALUE, destValue)
            putBoolean(KEY_LAST_USE_CURRENT_LOCATION, useCurrentLocation)
            // Only save selectedMillis if it was manually set, otherwise save -1 to indicate "Now" was used
            putLong(KEY_LAST_SELECTED_DATE_MILLIS, if(isUserDateTimeExplicitlySet) selectedMillis else -1L)
            putString(KEY_LAST_SELECTED_TIME_TYPE, selectedTimeType)
            apply()
        }
        Log.d("Prefs", "Saved: Origin=$originDisplay ($originType:$originValue), Dest=$destDisplay ($destType:$destValue), UseCurrentLoc=$useCurrentLocation, TimeMillis=$selectedMillis, TimeType=$selectedTimeType, isManualTimeSet=$isUserDateTimeExplicitlySet")
    }

    private fun initializeUIFields() {
        if (binding.radioButtonCurrentLocation.isChecked) {
            binding.autoCompleteTextViewSourceCustom.isEnabled = false
            binding.autoCompleteTextViewSourceCustom.alpha = 0.5f
            if (viewModel.currentLocationDisplayString.value == null || viewModel.currentLocationDisplayString.value?.contains("Fetching") == false) {
                viewModel.updateCurrentLocationData(null, "My Current Location (Fetching...)")
            }
            fetchOrRequestLocation()
        } else {
            binding.autoCompleteTextViewSourceCustom.isEnabled = true
            binding.autoCompleteTextViewSourceCustom.alpha = 1.0f
        }
        updateSelectedDateTimeDisplay() // Crucial to update display after loading prefs or init
    }

    private fun showDatePickerDialog() {
        // Use userSelectedCalendar if already set, otherwise current time
        val calToShow = if (isUserDateTimeExplicitlySet) userSelectedCalendar else Calendar.getInstance()
        val year = calToShow.get(Calendar.YEAR)
        val month = calToShow.get(Calendar.MONTH)
        val day = calToShow.get(Calendar.DAY_OF_MONTH)

        val datePickerDialog = DatePickerDialog(this,
            { _, selectedYear, selectedMonth, selectedDayOfMonth ->
                userSelectedCalendar.set(Calendar.YEAR, selectedYear)
                userSelectedCalendar.set(Calendar.MONTH, selectedMonth)
                userSelectedCalendar.set(Calendar.DAY_OF_MONTH, selectedDayOfMonth)
                // Don't set isUserDateTimeExplicitlySet here, wait for time selection
                showTimePickerDialog()
            }, year, month, day
        )
        datePickerDialog.datePicker.minDate = System.currentTimeMillis() - 1000
        datePickerDialog.show()
    }

    private fun showTimePickerDialog() {
        // Use userSelectedCalendar (which now has the picked date) or current time for hour/minute defaults
        val calToShow = if (isUserDateTimeExplicitlySet) userSelectedCalendar else Calendar.getInstance()
        // If a date was just picked, userSelectedCalendar has that date, but time might be old/current.
        // If it's the first time picking time for a newly picked date, default to current hour/minute for that date.
        val hour: Int
        val minute: Int
        if (isUserDateTimeExplicitlySet &&
            (userSelectedCalendar.get(Calendar.YEAR) != Calendar.getInstance().get(Calendar.YEAR) ||
                    userSelectedCalendar.get(Calendar.MONTH) != Calendar.getInstance().get(Calendar.MONTH) ||
                    userSelectedCalendar.get(Calendar.DAY_OF_MONTH) != Calendar.getInstance().get(Calendar.DAY_OF_MONTH))) {
            // If date is not today OR if it's today but time hasn't been set yet with this date,
            // it's better to default time picker to something reasonable like current hour/minute
            // or a fixed time e.g. 9 AM. For now, let's use current hour/minute.
            val tempCal = Calendar.getInstance()
            hour = tempCal.get(Calendar.HOUR_OF_DAY)
            minute = tempCal.get(Calendar.MINUTE)
        } else {
            hour = calToShow.get(Calendar.HOUR_OF_DAY)
            minute = calToShow.get(Calendar.MINUTE)
        }


        val timePickerDialog = TimePickerDialog(this,
            { _, selectedHour, selectedMinute ->
                userSelectedCalendar.set(Calendar.HOUR_OF_DAY, selectedHour)
                userSelectedCalendar.set(Calendar.MINUTE, selectedMinute)
                isUserDateTimeExplicitlySet = true // Now it's definitely manually set
                viewModel.updateSelectedDateTime(userSelectedCalendar) // Update ViewModel
            }, hour, minute, false // false for 12-hour format with AM/PM
        )
        timePickerDialog.show()
    }

    private fun updateSelectedDateTimeDisplay() {
        val timeTypeString = if (binding.radioButtonDepartAt.isChecked) "Depart" else "Arrive"
        if (isUserDateTimeExplicitlySet) {
            val dateFormat = SimpleDateFormat("EEE, MMM d, h:mm a", Locale.getDefault())
            binding.textViewSelectedDateTime.text = "$timeTypeString: ${dateFormat.format(userSelectedCalendar.time)}"
        } else {
            binding.textViewSelectedDateTime.text = "$timeTypeString: Now (Default)"
        }
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
}