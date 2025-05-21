package com.example.dadstripplanner3

import android.content.Intent // Import Intent
import android.os.Build
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

    companion object {
        const val EXTRA_SELECTED_TRIP_OPTION = "com.example.dadstripplanner3.SELECTED_TRIP_OPTION"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRouteOptionsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val sourceLocation = intent.getStringExtra(MainActivity.EXTRA_SOURCE_LOCATION) ?: "Origin"
        val destinationLocation = intent.getStringExtra(MainActivity.EXTRA_DESTINATION_LOCATION) ?: "Destination"

        displayableTripOptions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableArrayListExtra(MainActivity.EXTRA_TRIP_OPTIONS_LIST, DisplayableTripOption::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableArrayListExtra(MainActivity.EXTRA_TRIP_OPTIONS_LIST)
        } ?: emptyList()

        binding.toolbarTitle.text = "$sourceLocation → $destinationLocation"
        binding.toolbarRouteOptions.setNavigationOnClickListener {
            finish()
        }
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

        if (displayableTripOptions.isNotEmpty()) {
            // Instantiate adapter with the click listener
            tripOptionsAdapter = TripOptionsAdapter(displayableTripOptions) { selectedTrip ->
                // Handle item click: Navigate to TripDetailsActivity
                val intent = Intent(this, TripDetailsActivity::class.java)
                intent.putExtra(EXTRA_SELECTED_TRIP_OPTION, selectedTrip) // Pass the whole DisplayableTripOption
                startActivity(intent)
            }

            binding.recyclerViewTripOptions.layoutManager = LinearLayoutManager(this)
            binding.recyclerViewTripOptions.adapter = tripOptionsAdapter
            binding.recyclerViewTripOptions.visibility = View.VISIBLE
        } else {
            Log.w("RouteOptions", "No displayable trip options received.")
            Toast.makeText(this, "No trip options to display.", Toast.LENGTH_LONG).show()
            binding.recyclerViewTripOptions.visibility = View.GONE
        }
    }
}