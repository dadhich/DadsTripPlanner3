package com.example.dadstripplanner3

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.dadstripplanner3.databinding.ListItemTripLegBinding

class TripLegsAdapter(private val legs: List<DisplayableTripLeg>) :
    RecyclerView.Adapter<TripLegsAdapter.TripLegViewHolder>() {

    inner class TripLegViewHolder(val binding: ListItemTripLegBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(leg: DisplayableTripLeg) {
            binding.textViewLegModeEmoji.text = leg.modeEmoji
            binding.textViewLegDepartureTime.text = leg.originTimeFormatted
            binding.textViewLegOriginName.text = leg.originName

            var modeDetails = leg.modeName
            if (leg.durationMinutes > 0) {
                modeDetails += " (${leg.durationMinutes} min)"
            }
            if (!leg.lineDestination.isNullOrEmpty()) {
                modeDetails += " towards ${leg.lineDestination}"
            }
            binding.textViewLegModeNameAndDuration.text = modeDetails

            binding.textViewLegArrivalTime.text = leg.destinationTimeFormatted
            binding.textViewLegDestinationName.text = leg.destinationName

            if (!leg.realTimeStatus.isNullOrEmpty() && leg.realTimeStatus != "Scheduled" && leg.realTimeStatus != "On time" && !leg.realTimeStatus.contains("Scheduled", ignoreCase = true)) {
                binding.textViewLegRealTimeStatus.text = leg.realTimeStatus
                binding.textViewLegRealTimeStatus.visibility = View.VISIBLE
                if (leg.realTimeStatus.contains("late", ignoreCase = true)) {
                    binding.textViewLegRealTimeStatus.setTextColor(ContextCompat.getColor(itemView.context, R.color.trip_view_status_late))
                } else if (leg.realTimeStatus.contains("early", ignoreCase = true)) {
                    binding.textViewLegRealTimeStatus.setTextColor(ContextCompat.getColor(itemView.context, R.color.trip_view_status_on_time)) // Or a specific "early" color
                } else {
                    binding.textViewLegRealTimeStatus.setTextColor(ContextCompat.getColor(itemView.context, R.color.text_secondary_dark))
                }
            } else {
                binding.textViewLegRealTimeStatus.visibility = View.GONE
            }

            if (!leg.pathDescriptions.isNullOrEmpty()) {
                binding.textViewLegPathDescriptions.text = leg.pathDescriptions.joinToString("\n")
                binding.textViewLegPathDescriptions.visibility = View.VISIBLE
            } else {
                binding.textViewLegPathDescriptions.visibility = View.GONE
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TripLegViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ListItemTripLegBinding.inflate(inflater, parent, false)
        return TripLegViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TripLegViewHolder, position: Int) {
        holder.bind(legs[position])
    }

    override fun getItemCount(): Int = legs.size
}