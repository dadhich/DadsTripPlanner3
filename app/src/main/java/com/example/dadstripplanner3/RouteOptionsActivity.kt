package com.example.dadstripplanner3 // Your package name

import android.os.Build // For SDK version check for getParcelableArrayListExtra
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.dadstripplanner3.databinding.ActivityRouteOptionsBinding

class RouteOptionsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRouteOptionsBinding
    private lateinit var tripOptionsAdapter: TripOptionsAdapter
    private var displayableTripOptions: List<DisplayableTripOption> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRouteOptionsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // --- Retrieve data from Intent ---
        val sourceLocation = intent.getStringExtra(MainActivity.EXTRA_SOURCE_LOCATION) ?: "Unknown Source"
        val destinationLocation = intent.getStringExtra(MainActivity.EXTRA_DESTINATION_LOCATION) ?: "Unknown Destination"

        // Retrieve the list of DisplayableTripOption objects
        // Note: getParcelableArrayListExtra is deprecated for API 33+, use specific type for API 33+
        displayableTripOptions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableArrayListExtra(MainActivity.EXTRA_TRIP_OPTIONS_LIST, DisplayableTripOption::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableArrayListExtra(MainActivity.EXTRA_TRIP_OPTIONS_LIST)
        } ?: emptyList()


        // --- Setup Toolbar ---
        binding.toolbarRouteOptions.setNavigationOnClickListener {
            finish()
        }
        binding.toolbarTitle.text = "$sourceLocation → $destinationLocation"


        // --- Setup RecyclerView ---
        if (displayableTripOptions.isNotEmpty()) {
            tripOptionsAdapter = TripOptionsAdapter(displayableTripOptions)
            binding.recyclerViewTripOptions.layoutManager = LinearLayoutManager(this)
            binding.recyclerViewTripOptions.adapter = tripOptionsAdapter
            binding.recyclerViewTripOptions.visibility = View.VISIBLE
            // Add a TextView for "No trips found" and hide/show it accordingly (optional)
            // binding.textViewNoTripsFound.visibility = View.GONE
        } else {
            // Handle case where no trip options were passed or an error occurred
            Log.w("RouteOptions", "No displayable trip options received.")
            Toast.makeText(this, "No trip options to display.", Toast.LENGTH_LONG).show()
            binding.recyclerViewTripOptions.visibility = View.GONE
            // binding.textViewNoTripsFound.visibility = View.VISIBLE
        }
    }

    // The createSampleTripData() function is no longer needed and can be removed.
    // private fun createSampleTripData(): List<DisplayableTripOption> { ... }
}