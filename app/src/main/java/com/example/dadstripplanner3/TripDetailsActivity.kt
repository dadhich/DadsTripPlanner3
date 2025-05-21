package com.example.dadstripplanner3

import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.dadstripplanner3.databinding.ActivityTripDetailsBinding

class TripDetailsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTripDetailsBinding
    private lateinit var tripLegsAdapter: TripLegsAdapter
    private var selectedTripOption: DisplayableTripOption? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTripDetailsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Retrieve the DisplayableTripOption from the Intent
        selectedTripOption = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(RouteOptionsActivity.EXTRA_SELECTED_TRIP_OPTION, DisplayableTripOption::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(RouteOptionsActivity.EXTRA_SELECTED_TRIP_OPTION)
        }

        // Setup Toolbar
        // If using the MaterialToolbar directly for title:
        binding.toolbarTripDetails.title = "Trip Details" // You can make this more dynamic if needed
        binding.toolbarTripDetails.setNavigationOnClickListener {
            finish() // Handles back press
        }
        // If you added a custom TextView for title in activity_trip_details.xml, set its text here.

        if (selectedTripOption == null) {
            Log.e("TripDetailsActivity", "No TripOption data received.")
            Toast.makeText(this, "Error: Trip details not found.", Toast.LENGTH_LONG).show()
            finish() // Close activity if no data
            return
        }

        // Set the trip summary header
        binding.textViewTripSummaryHeader.text =
            "From: ${selectedTripOption!!.overallJourneyOriginName}\nTo: ${selectedTripOption!!.overallJourneyDestinationName}"


        // Setup RecyclerView with the legs from the selectedTripOption
        if (selectedTripOption!!.legs.isNotEmpty()) {
            tripLegsAdapter = TripLegsAdapter(selectedTripOption!!.legs)
            binding.recyclerViewTripLegs.layoutManager = LinearLayoutManager(this)
            binding.recyclerViewTripLegs.adapter = tripLegsAdapter
            binding.recyclerViewTripLegs.visibility = View.VISIBLE
        } else {
            Log.w("TripDetailsActivity", "Selected trip option has no legs to display.")
            Toast.makeText(this, "No trip legs to display for this option.", Toast.LENGTH_LONG).show()
            binding.recyclerViewTripLegs.visibility = View.GONE
            // Optionally show a "No legs available" message in the UI
        }
    }

    // If you are using setSupportActionBar(binding.toolbarTripDetails) and want to handle
    // the Up button from the ActionBar in a standard way:
    // override fun onOptionsItemSelected(item: MenuItem): Boolean {
    //     when (item.itemId) {
    //         android.R.id.home -> {
    //             onBackPressedDispatcher.onBackPressed()
    //             return true
    //         }
    //     }
    //     return super.onOptionsItemSelected(item)
    // }
}