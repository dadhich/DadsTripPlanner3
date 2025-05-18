package com.example.dadstripplanner3

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.dadstripplanner3.databinding.ActivityRouteOptionsBinding // View Binding class

class RouteOptionsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRouteOptionsBinding
    private lateinit var tripOptionsAdapter: TripOptionsAdapter // Declare adapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRouteOptionsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // --- 1. Setup Toolbar ---
        // Set the toolbar as the support action bar (optional, but can be useful)
        // setSupportActionBar(binding.toolbarRouteOptions) // If you need to handle options menu items from Activity

        // Handle navigation icon click (back arrow)
        binding.toolbarRouteOptions.setNavigationOnClickListener {
            finish() // Closes this activity and returns to the previous one
        }

        // Set a placeholder title (we'll make this dynamic in Step 4.1)
        binding.toolbarTitle.text = "Trips: Origin → Destination"


        // --- 2. Prepare Sample Data ---
        val sampleTripOptions = createSampleTripData()

        // --- 3. Setup RecyclerView ---
        // Initialize the adapter with the sample data
        tripOptionsAdapter = TripOptionsAdapter(sampleTripOptions)

        // Set the LayoutManager for the RecyclerView
        binding.recyclerViewTripOptions.layoutManager = LinearLayoutManager(this)

        // Set the Adapter for the RecyclerView
        binding.recyclerViewTripOptions.adapter = tripOptionsAdapter

        // Optional: Add item decoration for dividers (if desired, can be done later)
        // val dividerItemDecoration = DividerItemDecoration(this, LinearLayoutManager.VERTICAL)
        // binding.recyclerViewTripOptions.addItemDecoration(dividerItemDecoration)
    }

    // Helper function to create sample trip data (mimicking TripViewPlanner.jpg)
    private fun createSampleTripData(): List<TripOption> {
        val options = mutableListOf<TripOption>()

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
                duration = "1hr 24 mins", // Adjusted to match "84 mins" visually
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
        // Add more sample items if you like, following the pattern from TripViewPlanner.jpg

        return options
    }
}