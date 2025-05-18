package com.example.dadstripplanner3

// Remove 'import android.view.View' if it's unused
// Remove 'import android.widget.RadioButton' if it's unused directly here after View Binding
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.dadstripplanner3.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    // Companion object to define keys for Intent extras
    companion object {
        const val EXTRA_SOURCE_LOCATION = "com.example.dadstripplanner3.SOURCE_LOCATION"
        const val EXTRA_DESTINATION_LOCATION = "com.example.dadstripplanner3.DESTINATION_LOCATION"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.radioGroupSource.setOnCheckedChangeListener { _, checkedId ->
            if (checkedId == R.id.radioButtonCustomLocation) {
                binding.editTextSourceCustom.isEnabled = true
                binding.editTextSourceCustom.alpha = 1.0f
                binding.editTextSourceCustom.requestFocus()
            } else { // radioButtonCurrentLocation is checked
                binding.editTextSourceCustom.isEnabled = false
                binding.editTextSourceCustom.alpha = 0.5f
                binding.editTextSourceCustom.text.clear()
            }
        }

        if (binding.radioButtonCurrentLocation.isChecked) {
            binding.editTextSourceCustom.isEnabled = false
            binding.editTextSourceCustom.alpha = 0.5f
        } else if (binding.radioButtonCustomLocation.isChecked) {
            binding.editTextSourceCustom.isEnabled = true
            binding.editTextSourceCustom.alpha = 1.0f
        }

        binding.buttonNext.setOnClickListener {
            val destination = binding.editTextDestination.text.toString().trim()
            var source: String

            if (binding.radioButtonCurrentLocation.isChecked) {
                source = "My Current Location" // Placeholder text
                // In a future iteration, we'll get the actual current location here.
            } else {
                source = binding.editTextSourceCustom.text.toString().trim()
                if (source.isEmpty()) {
                    Toast.makeText(this, "Please enter a source address", Toast.LENGTH_SHORT).show()
                    binding.editTextSourceCustom.error =
                        "Source cannot be empty" // Optional: show error on EditText
                    return@setOnClickListener // Stop further execution
                }
            }

            if (destination.isEmpty()) {
                Toast.makeText(this, "Please enter a destination address", Toast.LENGTH_SHORT)
                    .show()
                binding.editTextDestination.error = "Destination cannot be empty" // Optional
                return@setOnClickListener // Stop further execution
            }

            // Proceed to RouteOptionsActivity
            val intent = Intent(this, RouteOptionsActivity::class.java).apply {
                putExtra(EXTRA_SOURCE_LOCATION, source)
                putExtra(EXTRA_DESTINATION_LOCATION, destination)
            }
            startActivity(intent)
        }
    }
}