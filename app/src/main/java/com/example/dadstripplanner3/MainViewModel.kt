package com.example.dadstripplanner3

import android.location.Location
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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

// Event Wrapper
open class Event<out T>(private val content: T) {
    @Suppress("MemberVisibilityCanBePrivate")
    var hasBeenHandled = false
        private set

    fun getContentIfNotHandled(): T? {
        return if (hasBeenHandled) {
            null
        } else {
            hasBeenHandled = true
            content
        }
    }
    fun peekContent(): T = content
}

// Data class for Navigation Event
data class NavigationParams(
    val sourceDisplay: String,
    val destinationDisplay: String,
    val trips: ArrayList<DisplayableTripOption>
)

class MainViewModel : ViewModel() {

    private val _currentLocationDisplayString = MutableLiveData<String?>()
    val currentLocationDisplayString: LiveData<String?> = _currentLocationDisplayString

    private val _currentLocationData = MutableLiveData<Location?>()
    val currentLocationData: LiveData<Location?> = _currentLocationData

    private val _destinationSuggestions = MutableLiveData<List<String>>()
    val destinationSuggestions: LiveData<List<String>> = _destinationSuggestions

    private val _sourceSuggestions = MutableLiveData<List<String>>()
    val sourceSuggestions: LiveData<List<String>> = _sourceSuggestions

    private val suggestionItemsMap = mutableMapOf<String, StopFinderLocation>()

    private var _selectedOriginFromAutocomplete = MutableLiveData<StopFinderLocation?>()
    val selectedOriginFromAutocomplete: LiveData<StopFinderLocation?> = _selectedOriginFromAutocomplete

    private var _selectedDestinationFromAutocomplete = MutableLiveData<StopFinderLocation?>()
    val selectedDestinationFromAutocomplete: LiveData<StopFinderLocation?> = _selectedDestinationFromAutocomplete

    private val _isLoading = MutableLiveData<Boolean>(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _userMessage = MutableLiveData<Event<String>>()
    val userMessage: LiveData<Event<String>> = _userMessage

    private val _navigateToRouteOptions = MutableLiveData<Event<NavigationParams>>()
    val navigateToRouteOptions: LiveData<Event<NavigationParams>> = _navigateToRouteOptions

    // --- NEW: LiveData for selected date/time and type ---
    private val _selectedDateTimeCalendar = MutableLiveData<Calendar>()
    val selectedDateTimeCalendar: LiveData<Calendar> = _selectedDateTimeCalendar

    private val _tripTimeType = MutableLiveData<String>() // "dep" or "arr"
    val tripTimeType: LiveData<String> = _tripTimeType

    private val _isUserDateTimeManuallySet = MutableLiveData<Boolean>(false)
    val isUserDateTimeManuallySet: LiveData<Boolean> = _isUserDateTimeManuallySet


    private var sourceSearchJob: Job? = null
    private var destinationSearchJob: Job? = null
    private val DEBOUNCE_DELAY_MS = 500L
    private val MIN_CHAR_THRESHOLD = 3

    val defaultDestinationAddress: String = "Hornsby Station, Hornsby"
    val defaultDestinationId: String = "207720"
    val defaultDestinationType: String = "stop"

    private val apiClient = ApiClient.instance
    private val tfnswApiKey = BuildConfig.TfNSW_API_KEY

    init {
        // Initialize with current time and default "depart at"
        _selectedDateTimeCalendar.value = Calendar.getInstance()
        _tripTimeType.value = "dep" // Default to "Depart At"
        _isUserDateTimeManuallySet.value = false
    }


    fun onOriginRadioButtonSelected(isCurrentLocation: Boolean) {
        if (isCurrentLocation) {
            _currentLocationDisplayString.value = "My Current Location (Fetching...)"
        } else {
            _currentLocationDisplayString.value = null
            _currentLocationData.value = null
            _selectedOriginFromAutocomplete.value = null
        }
    }

    fun updateCurrentLocationData(location: Location?, displayString: String?) {
        _currentLocationData.value = location
        _currentLocationDisplayString.value = displayString
    }

    fun handleSourceInputChanged(query: String) {
        sourceSearchJob?.cancel()
        if (query.length >= MIN_CHAR_THRESHOLD) {
            sourceSearchJob = viewModelScope.launch {
                delay(DEBOUNCE_DELAY_MS)
                fetchAutocompleteSuggestions(query, "source")
            }
        } else {
            _sourceSuggestions.value = emptyList()
        }
    }

    fun handleDestinationInputChanged(query: String) {
        destinationSearchJob?.cancel()
        if (query.length >= MIN_CHAR_THRESHOLD) {
            destinationSearchJob = viewModelScope.launch {
                delay(DEBOUNCE_DELAY_MS)
                fetchAutocompleteSuggestions(query, "destination")
            }
        } else {
            _destinationSuggestions.value = emptyList()
        }
    }

    fun sourceSuggestionSelected(suggestionString: String) {
        _selectedOriginFromAutocomplete.value = suggestionItemsMap[suggestionString]
    }

    fun destinationSuggestionSelected(suggestionString: String) {
        _selectedDestinationFromAutocomplete.value = suggestionItemsMap[suggestionString]
    }

    fun clearSelectedOriginAfterTextChange() {
        _selectedOriginFromAutocomplete.value = null
    }
    fun clearSelectedDestinationAfterTextChange() {
        _selectedDestinationFromAutocomplete.value = null
    }

    fun setSelectedOrigin(location: StopFinderLocation?) {
        _selectedOriginFromAutocomplete.value = location
    }

    fun setSelectedDestination(location: StopFinderLocation?) {
        _selectedDestinationFromAutocomplete.value = location
    }

    // --- NEW: Methods to update selected date/time from MainActivity ---
    fun updateSelectedDateTime(calendar: Calendar) {
        _selectedDateTimeCalendar.value = calendar
        _isUserDateTimeManuallySet.value = true
    }

    fun updateTripTimeType(type: String) { // type will be "dep" or "arr"
        _tripTimeType.value = type
        // No need to change isUserDateTimeManuallySet here unless a type change implies manual setting
    }

    fun resetToDepartNow() {
        _selectedDateTimeCalendar.value = Calendar.getInstance()
        _tripTimeType.value = "dep"
        _isUserDateTimeManuallySet.value = false // Reset this flag
    }


    private fun fetchAutocompleteSuggestions(query: String, fieldType: String) {
        Log.d("ViewModelAutocomplete", "Fetching suggestions for '$query' for $fieldType")
        val authHeader = "apikey $tfnswApiKey"

        apiClient.findLocations(
            authorization = authHeader,
            searchTerm = query
        ).enqueue(object : Callback<StopFinderResponse> {
            override fun onResponse(call: Call<StopFinderResponse>, response: Response<StopFinderResponse>) {
                if (response.isSuccessful) {
                    val stopFinderResponse = response.body()
                    val suggestionDisplayNames = mutableListOf<String>()
                    val newSuggestionItemsMap = mutableMapOf<String, StopFinderLocation>()

                    stopFinderResponse?.locations?.let { locations ->
                        locations.take(10).forEach { location ->
                            val displayName = formatSuggestion(location)
                            suggestionDisplayNames.add(displayName)
                            newSuggestionItemsMap[displayName] = location
                        }
                    }
                    suggestionItemsMap.clear()
                    suggestionItemsMap.putAll(newSuggestionItemsMap)

                    if (fieldType == "destination") {
                        _destinationSuggestions.value = suggestionDisplayNames
                    } else {
                        _sourceSuggestions.value = suggestionDisplayNames
                    }
                } else {
                    Log.e("ViewModelAutocomplete", "API Error for /stop_finder: ${response.code()} - ${response.message()}")
                    if (fieldType == "destination") _destinationSuggestions.value = emptyList() else _sourceSuggestions.value = emptyList()
                }
            }
            override fun onFailure(call: Call<StopFinderResponse>, t: Throwable) {
                Log.e("ViewModelAutocomplete", "Network Failure for /stop_finder: ${t.message}", t)
                if (fieldType == "destination") _destinationSuggestions.value = emptyList() else _sourceSuggestions.value = emptyList()
            }
        })
    }

    internal fun formatSuggestion(location: StopFinderLocation): String {
        if (location.name == null && location.disassembledName == null) return "Unknown Suggestion"
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
                if (suggestion == namePart && (typePart.isNullOrEmpty() || typePart.lowercase(Locale.getDefault()) == "unknown") ) {
                    suggestion += " - $parentName"
                } else if (!suggestion.contains(parentName)) {
                    suggestion += ", $parentName"
                }
            }
        }
        return suggestion.replace("  ", " ").trim()
    }

    // --- MODIFIED: onPlanTripClicked now accepts date/time params from Activity ---
    fun onPlanTripClicked(
        isCurrentLocationOrigin: Boolean,
        customOriginText: String,
        destinationText: String,
        // Resolved parameters for saving
        originDisplayForSave: String,
        originTypeForSave: String,
        originValueForSave: String,
        destDisplayForSave: String,
        destTypeForSave: String,
        destValueForSave: String,
        // New parameters for date/time
        tripDate: String, // YYYYMMDD
        tripTime: String, // HHMM
        depArrMacro: String // "dep" or "arr"
    ) {
        _isLoading.value = true

        val authHeader = "apikey $tfnswApiKey"

        Log.d("ViewModelAPIRequest", "Planning Trip: OriginType=$originTypeForSave, OriginValue=$originValueForSave, DestType=$destTypeForSave, DestValue=$destValueForSave, Date=$tripDate, Time=$tripTime, Mode=$depArrMacro")

        viewModelScope.launch {
            try {
                val call = apiClient.planTrip(
                    authorization = authHeader,
                    date = tripDate, // Use passed date
                    time = tripTime,   // Use passed time
                    depArrMacro = depArrMacro, // Use passed type
                    originType = originTypeForSave,
                    originValue = originValueForSave,
                    destinationType = destTypeForSave,
                    destinationValue = destValueForSave,
                    tfNSWTR = "true"
                )
                call.enqueue(object : Callback<TripResponse> {
                    override fun onResponse(call: Call<TripResponse>, response: Response<TripResponse>) {
                        _isLoading.value = false
                        if (response.isSuccessful) {
                            val tripResponseObject = response.body()
                            if (tripResponseObject != null && tripResponseObject.journeys != null && tripResponseObject.journeys.isNotEmpty()) {
                                val displayableTrips = transformApiJourneysToDisplayableOptions(tripResponseObject.journeys)
                                _navigateToRouteOptions.value = Event(NavigationParams(originDisplayForSave, destDisplayForSave, ArrayList(displayableTrips)))
                            } else if (tripResponseObject?.systemMessages != null && tripResponseObject.systemMessages.isNotEmpty()) {
                                val errorText = tripResponseObject.systemMessages.joinToString("; ") { msg -> msg.text ?: "Unknown system message" }
                                _userMessage.value = Event("API Info: $errorText")
                                Log.w("ViewModelAPIResponse", "Success but no journeys (SystemMessages: $errorText)")
                            } else {
                                _userMessage.value = Event("No trip options found for this request.")
                                Log.w("ViewModelAPIResponse", "No trip options or system messages in successful response.")
                            }
                        } else {
                            val errorBody = response.errorBody()?.string() ?: "No error body"
                            _userMessage.value = Event("API Error: ${response.code()} - ${response.message()}")
                            Log.e("ViewModelAPIResponse", "API Error: ${response.code()} - ${response.message()}. Body: $errorBody")
                        }
                    }
                    override fun onFailure(call: Call<TripResponse>, t: Throwable) {
                        _isLoading.value = false
                        _userMessage.value = Event("Network Error: ${t.message}")
                        Log.e("ViewModelAPIResponse", "Network Failure", t)
                    }
                })
            } catch (e: Exception) {
                _isLoading.value = false
                _userMessage.value = Event("Error planning trip: ${e.message}")
                Log.e("ViewModelAPIResponse", "Exception during trip planning", e)
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
            val displayableLegsForThisJourney = mutableListOf<DisplayableTripLeg>()

            apiJourney.legs.forEach { leg ->
                val legOrigin = leg.origin
                val legDestination = leg.destination
                val legDepartureTime = parseIsoDateTime(legOrigin?.departureTimeEstimated ?: legOrigin?.departureTimePlanned)
                val legArrivalTime = parseIsoDateTime(legDestination?.arrivalTimeEstimated ?: legDestination?.arrivalTimePlanned)
                var legModeName = "Unknown Mode"
                var legModeEmoji = "❓"
                var legLineDestination: String? = null
                var currentLegIsPT = false
                var ptInfoForThisLeg: String? = null
                var legRealTimeStatus: String? = "Scheduled"
                var legIsRealTime = leg.isRealtimeControlled == true
                var legIsLate = false

                when (leg.transportation?.product?.transportClass) {
                    100, 99 -> { legModeName = "Walk"; legModeEmoji = "🚶" }
                    5 -> { val rn = leg.transportation.number ?: ""; legModeName = "Bus $rn".trim(); ptInfoForThisLeg = legModeName; legModeEmoji = "🚌"; currentLegIsPT = true }
                    1 -> { val rn = leg.transportation.number ?: ""; legModeName = "Train $rn".trim(); ptInfoForThisLeg = legModeName; legModeEmoji = "🚆"; currentLegIsPT = true }
                    4 -> { val rn = leg.transportation.number ?: ""; legModeName = "LR $rn".trim(); ptInfoForThisLeg = legModeName; legModeEmoji = "🚈"; currentLegIsPT = true }
                    9 -> { val rn = leg.transportation.number ?: ""; legModeName = "Ferry $rn".trim(); ptInfoForThisLeg = legModeName; legModeEmoji = "⛴️"; currentLegIsPT = true }
                    2 -> { val rn = leg.transportation.number ?: ""; legModeName = "Metro $rn".trim(); ptInfoForThisLeg = legModeName; legModeEmoji = "🚇"; currentLegIsPT = true }
                    else -> { legModeName = leg.transportation?.product?.name?.takeIf { it.isNotBlank() } ?: "Service"; ptInfoForThisLeg = legModeName; currentLegIsPT = true }
                }

                if(legModeName == "Walk") {
                    if (modesSummaryList.isEmpty() || modesSummaryList.last() != "Walk") modesSummaryList.add("Walk")
                } else if (ptInfoForThisLeg != null) {
                    if (modesSummaryList.isEmpty() || modesSummaryList.last() != ptInfoForThisLeg) modesSummaryList.add(ptInfoForThisLeg)
                }

                if (currentLegIsPT) {
                    legLineDestination = leg.transportation?.lineDestination?.name
                    if (leg.isRealtimeControlled == true && legDepartureTime != null) {
                        val legPlannedDepZoned = parseIsoDateTime(legOrigin?.departureTimePlanned)
                        if (legPlannedDepZoned != null) {
                            val delayMinutes = calculateTimeDifferenceInMinutes(legPlannedDepZoned, legDepartureTime)
                            legRealTimeStatus = when {
                                delayMinutes == 0L -> "On time"
                                delayMinutes > 0 -> { legIsLate = true; "${formatTimeForDisplay(legPlannedDepZoned)}, $delayMinutes min late" }
                                else -> "${formatTimeForDisplay(legPlannedDepZoned)}, ${-delayMinutes} min early"
                            }
                        } else { legRealTimeStatus = "Real-time" }
                    } else if (legDepartureTime != null) {
                        legRealTimeStatus = "${formatTimeForDisplay(legDepartureTime)} Scheduled"
                    } else { legRealTimeStatus = "Data unavailable" }
                }

                if (currentLegIsPT) {
                    if (primaryPTInfoString == null && ptInfoForThisLeg != null) primaryPTInfoString = ptInfoForThisLeg
                    if (previousLegWasPT) interchanges++
                    if (interchanges == -1) interchanges = 0
                    previousLegWasPT = true
                } else { previousLegWasPT = false }

                displayableLegsForThisJourney.add(
                    DisplayableTripLeg(
                        modeEmoji = legModeEmoji, modeName = legModeName,
                        durationMinutes = (leg.durationSeconds?.toInt() ?: 0) / 60,
                        originName = legOrigin?.name ?: "Unknown point",
                        originTimeFormatted = formatTimeForDisplay(legDepartureTime),
                        destinationName = legDestination?.name ?: "Unknown point",
                        destinationTimeFormatted = formatTimeForDisplay(legArrivalTime),
                        lineDestination = legLineDestination,
                        stopSequenceCount = leg.stopSequence?.size ?: 0,
                        realTimeStatus = legRealTimeStatus, isRealTime = legIsRealTime,
                        distanceMeters = if (legModeName == "Walk") leg.distanceMetres else null,
                        pathDescriptions = if (legModeName == "Walk") leg.pathDescriptions?.mapNotNull { it.name } else null
                    )
                )
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

            val firstPublicTransportLeg = apiJourney.legs.find { it.transportation?.product?.transportClass !in listOf(100, 99) }

            if (firstPublicTransportLeg != null) {
                firstPTLegDepartureStopName = firstPublicTransportLeg.origin?.name
                val ptPlannedDepZoned = parseIsoDateTime(firstPublicTransportLeg.origin?.departureTimePlanned)
                val ptEstimatedDepZoned = parseIsoDateTime(firstPublicTransportLeg.origin?.departureTimeEstimated)
                firstPTLegDepartureEpochMillis = ptEstimatedDepZoned?.toInstant()?.toEpochMilli() ?: ptPlannedDepZoned?.toInstant()?.toEpochMilli() ?: -1L
                firstPTLegEstimatedDepartureTimeFormatted = formatTimeForDisplay(ptEstimatedDepZoned ?: ptPlannedDepZoned)
                val scheduledPtTimeFormattedForStatus = formatTimeForDisplay(ptPlannedDepZoned)
                firstPTLegScheduledDepartureTimeFormatted = scheduledPtTimeFormattedForStatus

                if (firstPublicTransportLeg.isRealtimeControlled == true && ptEstimatedDepZoned != null) {
                    isPTLegRealTimeDataUnavailable = false
                    if (ptPlannedDepZoned != null) {
                        val delayMinutes = calculateTimeDifferenceInMinutes(ptPlannedDepZoned, ptEstimatedDepZoned)
                        firstPTLegStatusMessage = when {
                            delayMinutes == 0L -> "On time"
                            delayMinutes > 0 -> { isPTLegLate = true; "$scheduledPtTimeFormattedForStatus, $delayMinutes min late" }
                            else -> "$scheduledPtTimeFormattedForStatus, ${-delayMinutes} min early"
                        }
                    } else { firstPTLegStatusMessage = "Real-time" }
                } else if (ptPlannedDepZoned != null) {
                    firstPTLegStatusMessage = "$scheduledPtTimeFormattedForStatus Scheduled"
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
                if (primaryPTInfoString == null && modesSummary.equals("Walk", ignoreCase = true)) primaryPTInfoString = "Walk"
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
                interchanges = interchanges,
                legs = displayableLegsForThisJourney
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