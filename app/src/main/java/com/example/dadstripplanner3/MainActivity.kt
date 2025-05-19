package com.example.dadstripplanner3

import android.Manifest
import android.annotation.SuppressLint // Required for SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log // For logging
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.dadstripplanner3.databinding.ActivityMainBinding
import com.google.android.gms.location.FusedLocationProviderClient // Import
import com.google.android.gms.location.LocationServices // Import

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var fusedLocationClient: FusedLocationProviderClient // Declare FusedLocationProviderClient

    companion object {
        const val EXTRA_SOURCE_LOCATION = "com.example.dadstripplanner3.SOURCE_LOCATION"
        const val EXTRA_DESTINATION_LOCATION = "com.example.dadstripplanner3.DESTINATION_LOCATION"
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this) // Initialize

        binding.radioGroupSource.setOnCheckedChangeListener { _, checkedId ->
            if (checkedId == R.id.radioButtonCustomLocation) {
                binding.editTextSourceCustom.isEnabled = true
                binding.editTextSourceCustom.alpha = 1.0f
                binding.editTextSourceCustom.requestFocus()
            } else if (checkedId == R.id.radioButtonCurrentLocation) {
                binding.editTextSourceCustom.isEnabled = false
                binding.editTextSourceCustom.alpha = 0.5f
                binding.editTextSourceCustom.text.clear()

                if (isLocationPermissionGranted()) {
                    fetchLastKnownLocation() // Call fetch location
                } else {
                    requestLocationPermission()
                }
            }
        }

        // Initial state check (simplified for brevity, focus is on explicit selection for now)
        if (binding.radioButtonCurrentLocation.isChecked) {
            binding.editTextSourceCustom.isEnabled = false
            binding.editTextSourceCustom.alpha = 0.5f
            // If it's checked by default AND permission is granted, you might want to fetch location here too.
            // For now, explicit selection or "Next" button click will be the primary triggers after initial permission grant.
            // if(isLocationPermissionGranted()) fetchLastKnownLocation() // Example for initial fetch
        } else if (binding.radioButtonCustomLocation.isChecked) {
            binding.editTextSourceCustom.isEnabled = true
            binding.editTextSourceCustom.alpha = 1.0f
        }


        binding.buttonNext.setOnClickListener {
            val destination = binding.editTextDestination.text.toString().trim()
            var source: String

            if (binding.radioButtonCurrentLocation.isChecked) {
                if (!isLocationPermissionGranted()) {
                    Toast.makeText(
                        this,
                        "Location permission needed to use current location.",
                        Toast.LENGTH_LONG
                    ).show()
                    requestLocationPermission()
                    return@setOnClickListener
                }
                // The actual location string will be set by a class variable in Step 2.1.4
                // For now, just indicate it would use current location logic
                source = "My Current Location (To be fetched/processed)"
                // We might trigger fetchLastKnownLocation here too if it hasn't been fetched yet,
                // or rely on a stored value updated by the RadioButton listener or onRequestPermissionsResult.
                // For simplicity, let's assume fetchLastKnownLocation populates a variable we use.
            } else {
                source = binding.editTextSourceCustom.text.toString().trim()
                if (source.isEmpty()) {
                    Toast.makeText(this, "Please enter a source address", Toast.LENGTH_SHORT).show()
                    binding.editTextSourceCustom.error = "Source cannot be empty"
                    return@setOnClickListener
                }
            }

            if (destination.isEmpty()) {
                Toast.makeText(this, "Please enter a destination address", Toast.LENGTH_SHORT)
                    .show()
                binding.editTextDestination.error = "Destination cannot be empty"
                return@setOnClickListener
            }

            val intent = Intent(this, RouteOptionsActivity::class.java).apply {
                putExtra(EXTRA_SOURCE_LOCATION, source) // Will be refined in 2.1.4
                putExtra(EXTRA_DESTINATION_LOCATION, destination)
            }
            startActivity(intent)
        }
    } // End of onCreate

    private fun isLocationPermissionGranted(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestLocationPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
            LOCATION_PERMISSION_REQUEST_CODE
        )
    }

    // --- New: Function to Fetch Last Known Location ---
    @SuppressLint("MissingPermission") // We are checking permission before calling this
    private fun fetchLastKnownLocation() {
        if (!isLocationPermissionGranted()) { // Double check, though should be called after grant
            requestLocationPermission() // Request if somehow called without permission
            return
        }

        fusedLocationClient.lastLocation
            .addOnSuccessListener { location ->
                if (location != null) {
                    // Location found
                    val lat = location.latitude
                    val lon = location.longitude
                    Toast.makeText(this, "Lat: $lat, Lon: $lon", Toast.LENGTH_LONG).show()
                    Log.d("LocationFetch", "Lat: $lat, Lon: $lon")
                    // In Step 2.1.4, we will store this to use for the 'source' variable
                } else {
                    // Last known location is not available
                    Toast.makeText(
                        this,
                        "Last known location not available. Consider requesting current location.",
                        Toast.LENGTH_LONG
                    ).show()
                    Log.d("LocationFetch", "Last known location is null.")
                    // In a later step, we might trigger a request for current location updates here.
                }
            }
            .addOnFailureListener { e ->
                // Failed to get location
                Toast.makeText(
                    this,
                    "Failed to get last known location: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
                Log.e("LocationFetch", "Failed to get last known location", e)
            }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Location permission granted!", Toast.LENGTH_SHORT).show()
                if (binding.radioButtonCurrentLocation.isChecked) {
                    fetchLastKnownLocation() // Fetch location now that permission is granted
                }
            } else {
                Toast.makeText(this, "Location permission denied.", Toast.LENGTH_SHORT).show()
                if (binding.radioButtonCurrentLocation.isChecked) {
                    binding.radioButtonCustomLocation.isChecked = true
                    Toast.makeText(
                        this,
                        "Cannot use current location without permission.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }
}