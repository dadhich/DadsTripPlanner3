package com.example.dadstripplanner3

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.dadstripplanner3.databinding.ListItemTripOptionBinding

class TripOptionsAdapter(private val tripOptions: List<TripOption>) :
    RecyclerView.Adapter<TripOptionsAdapter.TripOptionViewHolder>() {

    // ViewHolder class: Holds references to the views for each item
    inner class TripOptionViewHolder(val binding: ListItemTripOptionBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(tripOption: TripOption) {
            binding.textViewDuration.text = tripOption.duration
            binding.textViewDepartureTime.text = tripOption.departureTime
            binding.textViewDepartureLocation.text = tripOption.departureLocation
            binding.textViewStatus.text = tripOption.status
            binding.textViewArrivalTime.text = tripOption.arrivalTime
            binding.textViewArrivalLocation.text = tripOption.arrivalLocation
            binding.textViewTransportModes.text = tripOption.transportModes

            // Dynamically set status text color
            when {
                tripOption.isRealTimeDataUnavailable -> {
                    binding.textViewStatus.setTextColor(
                        ContextCompat.getColor(
                            itemView.context,
                            R.color.text_secondary_dark
                        ) // Or a specific grey
                    )
                }

                tripOption.isLate -> {
                    binding.textViewStatus.setTextColor(
                        ContextCompat.getColor(itemView.context, R.color.trip_view_status_late)
                    )
                }

                else -> { // On time
                    binding.textViewStatus.setTextColor(
                        ContextCompat.getColor(itemView.context, R.color.trip_view_status_on_time)
                    )
                }
            }
            // You might also want to change the duration color or other elements if needed
            binding.textViewDuration.setTextColor(
                ContextCompat.getColor(itemView.context, R.color.trip_view_teal_highlight)
            )
        }
    }

    // Called when RecyclerView needs a new ViewHolder of the given type to represent an item.
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TripOptionViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        // Inflate the item layout using View Binding
        val binding = ListItemTripOptionBinding.inflate(inflater, parent, false)
        return TripOptionViewHolder(binding)
    }

    // Called by RecyclerView to display the data at the specified position.
    override fun onBindViewHolder(holder: TripOptionViewHolder, position: Int) {
        val tripOption = tripOptions[position]
        holder.bind(tripOption)
    }

    // Returns the total number of items in the data set held by the adapter.
    override fun getItemCount(): Int {
        return tripOptions.size
    }
}