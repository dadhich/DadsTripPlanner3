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
            // Map fields from DisplayableTripOption to the TextViews
            binding.textViewDuration.text = "${tripOption.overallDurationInMinutes} min" // Add "min" suffix
            binding.textViewDepartureTime.text = tripOption.departureTimeFormatted
            binding.textViewDepartureLocation.text = tripOption.effectiveOriginName
            binding.textViewStatus.text = tripOption.departureStatus
            binding.textViewArrivalTime.text = tripOption.arrivalTimeFormatted
            binding.textViewArrivalLocation.text = tripOption.effectiveDestinationName
            binding.textViewTransportModes.text = tripOption.transportModesSummary

            // Dynamically set status text color based on flags from DisplayableTripOption
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

            // Ensure duration text color is consistent (if it was set specifically before)
            binding.textViewDuration.setTextColor(
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