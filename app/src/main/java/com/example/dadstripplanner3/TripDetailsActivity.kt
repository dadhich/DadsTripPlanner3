package com.example.dadstripplanner3

import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.recyclerview.widget.DividerItemDecoration // Import DividerItemDecoration
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
        binding.toolbarTripDetails.title = "Trip Details"
        binding.toolbarTripDetails.setNavigationOnClickListener {
            finish()
        }

        if (selectedTripOption == null) {
            Log.e("TripDetailsActivity", "No TripOption data received.")
            Toast.makeText(this, "Error: Trip details not found.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        binding.textViewTripSummaryHeader.text =
            "From: ${selectedTripOption!!.overallJourneyOriginName}\nTo: ${selectedTripOption!!.overallJourneyDestinationName}"

        if (selectedTripOption!!.legs.isNotEmpty()) {
            tripLegsAdapter = TripLegsAdapter(selectedTripOption!!.legs)
            binding.recyclerViewTripLegs.layoutManager = LinearLayoutManager(this)
            binding.recyclerViewTripLegs.adapter = tripLegsAdapter

            // --- ADD DIVIDER ITEM DECORATION ---
            val dividerItemDecoration = DividerItemDecoration(
                binding.recyclerViewTripLegs.context,
                (binding.recyclerViewTripLegs.layoutManager as LinearLayoutManager).orientation
            )
            // Optionally, set a custom drawable for the divider:
            // ContextCompat.getDrawable(this, R.drawable.your_custom_divider)?.let {
            //    dividerItemDecoration.setDrawable(it)
            // }
            binding.recyclerViewTripLegs.addItemDecoration(dividerItemDecoration)
            // --- END ADD DIVIDER ---

            binding.recyclerViewTripLegs.visibility = View.VISIBLE
        } else {
            Log.w("TripDetailsActivity", "Selected trip option has no legs to display.")
            Toast.makeText(this, "No trip legs to display for this option.", Toast.LENGTH_LONG).show()
            binding.recyclerViewTripLegs.visibility = View.GONE
        }
    }
}