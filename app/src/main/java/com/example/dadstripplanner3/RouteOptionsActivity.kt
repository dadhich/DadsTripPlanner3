package com.example.dadstripplanner3 // Ensure this matches your package name

// import androidx.recyclerview.widget.DividerItemDecoration // Keep if you plan to use it
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.dadstripplanner3.databinding.ActivityRouteOptionsBinding

class RouteOptionsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRouteOptionsBinding
    private lateinit var tripOptionsAdapter: TripOptionsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRouteOptionsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // --- Retrieve data from Intent ---
        val sourceLocation =
            intent.getStringExtra(MainActivity.EXTRA_SOURCE_LOCATION) ?: "Unknown Source"
        val destinationLocation =
            intent.getStringExtra(MainActivity.EXTRA_DESTINATION_LOCATION) ?: "Unknown Destination"

        // --- Setup Toolbar ---
        binding.toolbarRouteOptions.setNavigationOnClickListener {
            finish()
        }
        // Update toolbar title dynamically
        binding.toolbarTitle.text = "$sourceLocation → $destinationLocation"


        val sampleTripOptions = createSampleTripData()
        tripOptionsAdapter = TripOptionsAdapter(sampleTripOptions)
        binding.recyclerViewTripOptions.layoutManager = LinearLayoutManager(this)
        binding.recyclerViewTripOptions.adapter = tripOptionsAdapter

        // Optional: Add item decoration for dividers
        // val dividerItemDecoration = DividerItemDecoration(this, LinearLayoutManager.VERTICAL)
        // binding.recyclerViewTripOptions.addItemDecoration(dividerItemDecoration)
    }

    private fun createSampleTripData(): List<TripOption> {
        val options = mutableListOf<TripOption>()
        // ... (createSampleTripData function remains the same as in Step 3.5) ...
        // (Ensure this function is still present in your RouteOptionsActivity.kt)
        options.add(
            TripOption(
                duration = "26 mins",
                departureTime = "10:44 am",
                departureLocation = "Lowe Rd opp James Park",
                status = "10:42 running 2 mins late",
                arrivalTime = "10:53 am",
                arrivalLocation = "Hornsby Station",
                transportModes = "Train T1, Bus 575",
                isLate = true
            )
        )
        options.add(
            TripOption(
                duration = "54 mins",
                departureTime = "11:12 am",
                departureLocation = "Lowe Rd opp James Park",
                status = "On time",
                arrivalTime = "11:21 am",
                arrivalLocation = "Hornsby Station",
                transportModes = "Bus 575"
            )
        )
        options.add(
            TripOption(
                duration = "1hr 24 mins",
                departureTime = "11:42 am",
                departureLocation = "Lowe Rd opp James Park",
                status = "On time",
                arrivalTime = "11:51 am",
                arrivalLocation = "Hornsby Station",
                transportModes = "Bus 575, Train T9"
            )
        )
        options.add(
            TripOption(
                duration = "2 hrs",
                departureTime = "12:12 pm",
                departureLocation = "Lowe Rd opp James Park",
                status = "Real-time data unavailable",
                arrivalTime = "12:21 pm",
                arrivalLocation = "Hornsby Station",
                transportModes = "Bus 575",
                isRealTimeDataUnavailable = true
            )
        )
        options.add(
            TripOption(
                duration = "2 hrs",
                departureTime = "12:42 pm",
                departureLocation = "Lowe Rd opp James Park",
                status = "Real-time data unavailable",
                arrivalTime = "12:51 pm",
                arrivalLocation = "Hornsby Station",
                transportModes = "Train T1",
                isRealTimeDataUnavailable = true
            )
        )
        return options
    }
}