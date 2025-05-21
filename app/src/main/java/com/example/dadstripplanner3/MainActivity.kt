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
    // SharedPreferences interaction will be via ViewModel -> Repository

    private var locationCallback: LocationCallback? = null
    private var requestingLocationUpdates = false

    private lateinit var destinationAdapter: ArrayAdapter<String>
    private lateinit var sourceAdapter: ArrayAdapter<String>

    private var isSettingDestinationTextProgrammatically = false
    private var isSettingSourceTextProgrammatically = false

    private val userSelectedDateTimeCalendar = Calendar.getInstance()
    private var isUserDateTimeExplicitlySet = false

    companion object {
        const val EXTRA_SOURCE_LOCATION = "com.example.dadstripplanner3.SOURCE_LOCATION"
        const val EXTRA_DESTINATION_LOCATION = "com.example.dadstripplanner3.DESTINATION_LOCATION"
        const val EXTRA_TRIP_OPTIONS_LIST = "com.example.dadstripplanner3.TRIP_OPTIONS_LIST"
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1001
        // SharedPreferences Keys moved to TripRepository
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
        // SharedPreferences initialization is handled by Repository

        setupAutocompleteAdapters()
        setupUIObservers()
        setupListeners()
        viewModel.loadLastTripPreferences() // Ask ViewModel to load
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
            prefs?.let {
                isSettingDestinationTextProgrammatically = true
                binding.autoCompleteTextViewDestination.setText(it.destDisplay ?: viewModel.defaultDestinationAddress)
                isSettingDestinationTextProgrammatically = false
                if (!it.destDisplay.isNullOrEmpty() && !it.destType.isNullOrEmpty() && !it.destValue.isNullOrEmpty()) {
                    viewModel.setSelectedDestination(StopFinderLocation(
                        id = it.destValue, name = it.destDisplay, type = it.destType,
                        disassembledName = it.destDisplay, coordinates = null, parent = null, modes = null, assignedStops = null, matchQuality = null, isBest = null, isGlobalId = null
                    ))
                } else {
                    viewModel.setSelectedDestination(StopFinderLocation(
                        id = viewModel.defaultDestinationId, name = viewModel.defaultDestinationAddress, type = viewModel.defaultDestinationType,
                        disassembledName = viewModel.defaultDestinationAddress, coordinates = null, parent = null, modes = null, assignedStops = null, matchQuality = null, isBest = null, isGlobalId = null
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
                        disassembledName = it.originDisplay, coordinates = null, parent = null, modes = null, assignedStops = null, matchQuality = null, isBest = null, isGlobalId = null
                    ))
                } else {
                    binding.radioButtonCurrentLocation.isChecked = true
                }

                isUserDateTimeExplicitlySet = it.isDateTimeManuallySet
                if (isUserDateTimeExplicitlySet && it.selectedTimeMillis != -1L) {
                    userSelectedDateTimeCalendar.timeInMillis = it.selectedTimeMillis
                } else {
                    userSelectedDateTimeCalendar.timeInMillis = System.currentTimeMillis()
                    isUserDateTimeExplicitlySet = false
                }
                viewModel.updateSelectedDateTime(userSelectedDateTimeCalendar)
                viewModel.updateTripTimeType(it.selectedTimeType)

                initializeUIFields()
            }
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
            userSelectedDateTimeCalendar.timeInMillis = calendar.timeInMillis
            updateSelectedDateTimeDisplay()
        }
        viewModel.isUserDateTimeManuallySet.observe(this) { isSet ->
            isUserDateTimeExplicitlySet = isSet
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
            viewModel.updateTripTimeType(type)
            // If user changes Depart/Arrive and a custom date/time was ALREADY set by picker, make it stick
            if (isUserDateTimeExplicitlySet) {
                viewModel.updateSelectedDateTime(userSelectedDateTimeCalendar) // This re-affirms manual set
            }
            // Display update will be handled by ViewModel observer
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

            // Determine final resolved parameters for the API call and for saving
            var originDisplay: String
            var originType: String
            var originValue: String

            if (isCurrentLocOrigin) {
                originDisplay = viewModel.currentLocationDisplayString.value ?: "My Current Location"
                viewModel.currentLocationData.value?.let { loc ->
                    originType = "coord"
                    originValue = "${loc.longitude}:${loc.latitude}:EPSG:4326"
                } ?: return@setOnClickListener
            } else {
                originDisplay = customOriginInputText
                val selectedOrigin = viewModel.selectedOriginFromAutocomplete.value
                if (selectedOrigin != null && (selectedOrigin.name == originDisplay || selectedOrigin.disassembledName == originDisplay || viewModel.formatSuggestion(selectedOrigin) == originDisplay) && selectedOrigin.id != null && selectedOrigin.type != null) {
                    originType = selectedOrigin.type!!
                    originValue = selectedOrigin.id!!
                } else {
                    originType = "any"
                    originValue = originDisplay
                }
            }

            var destDisplay: String = destinationInputText
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

            if (originValue.isEmpty() && !isCurrentLocOrigin) { Toast.makeText(this, "Please enter a source address.", Toast.LENGTH_SHORT).show(); binding.autoCompleteTextViewSourceCustom.error = "Source cannot be empty"; return@setOnClickListener }
            if (destValue.isEmpty()) { Toast.makeText(this, "Please enter a destination address.", Toast.LENGTH_SHORT).show(); binding.autoCompleteTextViewDestination.error = "Destination cannot be empty"; return@setOnClickListener }

            val calendarToUse = if (isUserDateTimeExplicitlySet) userSelectedDateTimeCalendar else Calendar.getInstance()
            val dateFormat = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
            val timeFormat = SimpleDateFormat("HHmm", Locale.getDefault())
            val depArrMacro = if (binding.radioButtonDepartAt.isChecked) "dep" else "arr"

            viewModel.onPlanTripClicked(
                originDisplayForSave = originDisplay,
                originTypeForSave = originType,
                originValueForSave = originValue,
                destDisplayForSave = destDisplay,
                destTypeForSave = destType,
                destValueForSave = destValue,
                useCurrentLocationForSave = isCurrentLocOrigin,
                tripDate = dateFormat.format(calendarToUse.time),
                tripTime = timeFormat.format(calendarToUse.time),
                depArrMacro = depArrMacro
            )
        }
    }


    private fun loadLastSelectionsOrDefaults() {
        viewModel.loadLastTripPreferences()
    }

    // saveLastSelections is now fully handled by ViewModel -> Repository
    // This function can be removed from MainActivity.
    // private fun saveLastSelections(...) { ... }


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
        updateSelectedDateTimeDisplay()
    }

    private fun showDatePickerDialog() {
        val calToShow = if (isUserDateTimeExplicitlySet) userSelectedDateTimeCalendar else Calendar.getInstance()
        val year = calToShow.get(Calendar.YEAR)
        val month = calToShow.get(Calendar.MONTH)
        val day = calToShow.get(Calendar.DAY_OF_MONTH)

        val datePickerDialog = DatePickerDialog(this,
            { _, selectedYear, selectedMonth, selectedDayOfMonth ->
                userSelectedDateTimeCalendar.set(Calendar.YEAR, selectedYear)
                userSelectedDateTimeCalendar.set(Calendar.MONTH, selectedMonth)
                userSelectedDateTimeCalendar.set(Calendar.DAY_OF_MONTH, selectedDayOfMonth)
                showTimePickerDialog()
            }, year, month, day
        )
        datePickerDialog.datePicker.minDate = System.currentTimeMillis() - 1000
        datePickerDialog.show()
    }

    private fun showTimePickerDialog() {
        val calToUseForDefaults = Calendar.getInstance()
        val hour = calToUseForDefaults.get(Calendar.HOUR_OF_DAY)
        val minute = calToUseForDefaults.get(Calendar.MINUTE)

        val timePickerDialog = TimePickerDialog(this,
            { _, selectedHour, selectedMinute ->
                userSelectedDateTimeCalendar.set(Calendar.HOUR_OF_DAY, selectedHour)
                userSelectedDateTimeCalendar.set(Calendar.MINUTE, selectedMinute)
                isUserDateTimeExplicitlySet = true // User has now explicitly set date and time
                viewModel.updateSelectedDateTime(userSelectedDateTimeCalendar) // Update ViewModel
            }, hour, minute, false
        )
        timePickerDialog.show()
    }

    private fun updateSelectedDateTimeDisplay() {
        val timeTypeString = if (binding.radioButtonDepartAt.isChecked) "Depart" else "Arrive"
        if (isUserDateTimeExplicitlySet) {
            val dateFormat = SimpleDateFormat("EEE, MMM d, h:mm a", Locale.getDefault())
            binding.textViewSelectedDateTime.text = "$timeTypeString: ${dateFormat.format(userSelectedDateTimeCalendar.time)}"
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