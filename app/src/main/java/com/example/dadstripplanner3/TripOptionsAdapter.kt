package com.example.dadstripplanner3 // Your package name

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.dadstripplanner3.databinding.ListItemTripOptionBinding // View Binding for the item layout

// Change the constructor to accept List<DisplayableTripOption>
class TripOptionsAdapter(private var tripOptions: List<DisplayableTripOption>) :
    RecyclerView.Adapter<TripOptionsAdapter.TripOptionViewHolder>() {

    // ViewHolder class: Holds references to the views for each item
    inner class TripOptionViewHolder(val binding: ListItemTripOptionBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(tripOption: DisplayableTripOption) {
            // Original overall duration (we might move this or combine it)
            // binding.textViewDuration.text = "${tripOption.overallDurationInMinutes} min"

            // --- NEW: Calculate and display "Departs in X min" ---
            val currentTimeMillis = System.currentTimeMillis()
            val departureTimeMillis = tripOption.firstLegDepartureEpochMillis
            var departsInText = "-- min" // Default

            if (departureTimeMillis > 0) { // Check if we have a valid departure time
                val diffMillis = departureTimeMillis - currentTimeMillis
                if (diffMillis <= 0) {
                    // If scheduled time has passed or is very close
                    // We rely on tripOption.departureStatus for "late", "on time" etc.
                    // "Now" might be confusing if it's "10 min late" but scheduled time passed 10 mins ago.
                    // Let's prioritize showing the actual status string if it's already past due.
                    // If it's truly "Now" and status is "On Time", we can show "Now".
                    if (tripOption.departureStatus.equals("On time", ignoreCase = true) && diffMillis > -60000) { // Within a minute past
                        departsInText = "Now"
                    } else if (tripOption.isLate && diffMillis <=0) { // It's late and scheduled time has passed
                        departsInText = tripOption.departureStatus // e.g., "5 min late"
                    } else if (diffMillis <= -60000 * 5) { // More than 5 mins past, and not explicitly "late" (edge case)
                        departsInText = "Departed" // Or use status
                    }
                    else {
                        departsInText = "Now" // Default for very near departure
                    }
                } else {
                    val diffMinutes = java.util.concurrent.TimeUnit.MILLISECONDS.toMinutes(diffMillis)
                    if (diffMinutes < 1) {
                        departsInText = "Now" // Or "< 1 min"
                    } else if (diffMinutes < 60) {
                        departsInText = "$diffMinutes min"
                    } else {
                        val diffHours = java.util.concurrent.TimeUnit.MILLISECONDS.toHours(diffMillis)
                        val remainingMinutes = diffMinutes % 60
                        departsInText = "${diffHours}h ${remainingMinutes}m"
                    }
                }
            } else { // firstLegDepartureEpochMillis was -1 or invalid
                departsInText = "N/A" // Or some other indicator
            }
            binding.textViewDuration.text = departsInText // This now shows "Departs in X min"

            // Other bindings
            binding.textViewDepartureTime.text = tripOption.departureTimeFormatted
            binding.textViewDepartureLocation.text = tripOption.effectiveOriginName

            // Combine departureStatus with overall trip duration perhaps?
            // For now, just the status as calculated.
            // Example of combining: "${tripOption.departureStatus} • ${tripOption.overallDurationInMinutes} min trip"
            binding.textViewStatus.text = tripOption.departureStatus
            // + " (${tripOption.overallDurationInMinutes} min total)" // Optionally add total duration here

            binding.textViewArrivalTime.text = tripOption.arrivalTimeFormatted
            binding.textViewArrivalLocation.text = tripOption.effectiveDestinationName
            binding.textViewTransportModes.text = tripOption.transportModesSummary

            // ... (status color logic remains the same)
            val context = itemView.context
            when {
                tripOption.isRealTimeDataUnavailable -> {
                    binding.textViewStatus.setTextColor(
                        ContextCompat.getColor(context, R.color.text_secondary_dark)
                    )
                }
                tripOption.isLate -> {
                    binding.textViewStatus.setTextColor(
                        ContextCompat.getColor(context, R.color.trip_view_status_late)
                    )
                }
                else -> { // On time or default
                    binding.textViewStatus.setTextColor(
                        ContextCompat.getColor(context, R.color.trip_view_status_on_time)
                    )
                }
            }
            binding.textViewDuration.setTextColor( // The "Departs in X min" text
                ContextCompat.getColor(context, R.color.trip_view_teal_highlight)
            )
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TripOptionViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ListItemTripOptionBinding.inflate(inflater, parent, false)
        return TripOptionViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TripOptionViewHolder, position: Int) {
        val tripOption = tripOptions[position]
        holder.bind(tripOption)
    }

    override fun getItemCount(): Int {
        return tripOptions.size
    }

    // Optional: Helper function to update data if you implement pull-to-refresh or new searches later
    fun updateData(newTripOptions: List<DisplayableTripOption>) {
        this.tripOptions = newTripOptions
        notifyDataSetChanged() // Consider using DiffUtil for better performance with large lists
    }
}