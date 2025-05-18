package com.example.dadstripplanner3 // Make sure this matches your package name

import android.content.Intent // Required for navigation
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import android.widget.RadioButton
import com.example.dadstripplanner3.databinding.ActivityMainBinding // Import View Binding class

class MainActivity : AppCompatActivity() {

    // Declare a variable for the View Binding class
    // The name 'ActivityMainBinding' is generated from your layout file 'activity_main.xml'
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Inflate the layout using View Binding and set the content view
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // --- Setup UI Listeners and Logic ---

        // 1. RadioGroup listener to enable/disable custom source EditText
        binding.radioGroupSource.setOnCheckedChangeListener { group, checkedId ->
            if (checkedId == R.id.radioButtonCustomLocation) {
                binding.editTextSourceCustom.isEnabled = true
                binding.editTextSourceCustom.alpha = 1.0f // Fully opaque
                binding.editTextSourceCustom.requestFocus() // Optional: move cursor here
            } else { // radioButtonCurrentLocation is checked
                binding.editTextSourceCustom.isEnabled = false
                binding.editTextSourceCustom.alpha = 0.5f // Visually indicate disabled
                binding.editTextSourceCustom.text.clear() // Optional: clear text
            }
        }

        // 2. Initialize the state of editTextSourceCustom based on the default checked RadioButton
        // Since "My current location" is checked by default in XML, custom source should be disabled.
        if (binding.radioButtonCurrentLocation.isChecked) {
            binding.editTextSourceCustom.isEnabled = false
            binding.editTextSourceCustom.alpha = 0.5f
        } else if (binding.radioButtonCustomLocation.isChecked) { // Should not happen with current XML default
            binding.editTextSourceCustom.isEnabled = true
            binding.editTextSourceCustom.alpha = 1.0f
        }


        // 3. "Next" button click listener
        binding.buttonNext.setOnClickListener {
            // For now, we'll just navigate to a placeholder (RouteOptionsActivity)
            // We'll create RouteOptionsActivity in Phase 3
            // val intent = Intent(this, RouteOptionsActivity::class.java)
            // startActivity(intent)

            // Placeholder action until RouteOptionsActivity is created:
            // You can show a Toast message for now
            android.widget.Toast.makeText(this, "Next button clicked!", android.widget.Toast.LENGTH_SHORT).show()
            // TODO: Implement navigation to RouteOptionsActivity in Step 4.1
        }
    }
}