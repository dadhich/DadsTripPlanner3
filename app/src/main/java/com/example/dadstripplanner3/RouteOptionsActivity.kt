package com.example.dadstripplanner3

import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
// Import DividerItemDecoration if you plan to use it
// import androidx.recyclerview.widget.DividerItemDecoration
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
        val sourceLocation = intent.getStringExtra(MainActivity.EXTRA_SOURCE_LOCATION) ?: "Origin"
        val destinationLocation = intent.getStringExtra(MainActivity.EXTRA_DESTINATION_LOCATION) ?: "Destination"

        // Retrieve the list of DisplayableTripOption objects
        displayableTripOptions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableArrayListExtra(MainActivity.EXTRA_TRIP_OPTIONS_LIST, DisplayableTripOption::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableArrayListExtra(MainActivity.EXTRA_TRIP_OPTIONS_LIST)
        } ?: emptyList()


        // --- Setup Toolbar ---
        // If you are using a custom TextView for the title as per the latest activity_route_options.xml
        binding.toolbarTitle.text = "$sourceLocation → $destinationLocation"

        // Handle navigation icon click (back arrow)
        binding.toolbarRouteOptions.setNavigationOnClickListener {
            finish() // Closes this activity and returns to the previous one
        }

        // Handle menu item clicks (placeholders for now)
        binding.toolbarRouteOptions.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_filter_options -> {
                    Toast.makeText(this, "Filter options clicked (not implemented)", Toast.LENGTH_SHORT).show()
                    true
                }
                R.id.action_more_options -> {
                    Toast.makeText(this, "More options clicked (not implemented)", Toast.LENGTH_SHORT).show()
                    true
                }
                else -> false
            }
        }


        // --- Setup RecyclerView ---
        if (displayableTripOptions.isNotEmpty()) {
            tripOptionsAdapter = TripOptionsAdapter(displayableTripOptions)
            binding.recyclerViewTripOptions.layoutManager = LinearLayoutManager(this)
            binding.recyclerViewTripOptions.adapter = tripOptionsAdapter
            binding.recyclerViewTripOptions.visibility = View.VISIBLE
            // If you have a "no trips found" TextView, hide it here:
            // binding.textViewNoTripsFound.visibility = View.GONE
        } else {
            // Handle case where no trip options were passed or an error occurred
            Log.w("RouteOptions", "No displayable trip options received.")
            Toast.makeText(this, "No trip options to display.", Toast.LENGTH_LONG).show()
            binding.recyclerViewTripOptions.visibility = View.GONE
            // If you have a "no trips found" TextView, show it here:
            // binding.textViewNoTripsFound.visibility = View.VISIBLE
        }

        // Optional: Add item decoration for dividers
        // val dividerItemDecoration = DividerItemDecoration(this, LinearLayoutManager.VERTICAL)
        // binding.recyclerViewTripOptions.addItemDecoration(dividerItemDecoration)
    }
}