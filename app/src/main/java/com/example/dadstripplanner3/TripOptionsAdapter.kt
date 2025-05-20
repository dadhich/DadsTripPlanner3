package com.example.dadstripplanner3

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.dadstripplanner3.databinding.ListItemTripOptionBinding
import java.util.concurrent.TimeUnit

class TripOptionsAdapter(private var tripOptions: List<DisplayableTripOption>) :
    RecyclerView.Adapter<TripOptionsAdapter.TripOptionViewHolder>() {

    inner class TripOptionViewHolder(val binding: ListItemTripOptionBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(tripOption: DisplayableTripOption) {
            val context = itemView.context

            // Column 1: "Departs in" and Primary PT Info
            val currentTimeMillis = System.currentTimeMillis()
            val departureEpochToUseForDepartsIn = tripOption.firstPTLegDepartureEpochMillis
            var departsInText = "-- min"

            if (departureEpochToUseForDepartsIn > 0) {
                val diffMillis = departureEpochToUseForDepartsIn - currentTimeMillis
                if (diffMillis <= -120000 ) { // Departed more than 2 mins ago
                    departsInText = "Departed"
                } else if (diffMillis <= 60000) { // Departing now or very soon (within next minute or just passed)
                    if (tripOption.isPTLegLate && diffMillis <= 0 && tripOption.firstPTLegStatusMessage?.contains("late",ignoreCase = true) == true) {
                        departsInText = tripOption.firstPTLegStatusMessage.substringAfter(",").trim().substringBefore(" min") + " min" // Show "X min" from "Y, X min late"
                    } else if (tripOption.firstPTLegStatusMessage?.contains("early",ignoreCase = true) == true && diffMillis <=0){
                        departsInText = "Departed" // If it was early and scheduled time passed
                    }
                    else {
                        departsInText = "Now"
                    }
                } else {
                    val diffMinutes = TimeUnit.MILLISECONDS.toMinutes(diffMillis)
                    if (diffMinutes < 1) {
                        departsInText = "<1 min"
                    } else if (diffMinutes < 60) {
                        departsInText = "$diffMinutes min"
                    } else {
                        val diffHours = TimeUnit.MILLISECONDS.toHours(diffMillis)
                        val remainingMinutes = diffMinutes % 60
                        departsInText = if (remainingMinutes == 0L) "${diffHours}h" else "${diffHours}h ${remainingMinutes}m"
                    }
                }
            } else if (tripOption.firstPTLegStatusMessage == "Walk only") {
                // For walk-only trips, "Departs in" is from overall journey start (which is also in firstPTLegDepartureEpochMillis for this case)
                val overallJourneyDepartureMillis = tripOption.firstPTLegDepartureEpochMillis
                if(overallJourneyDepartureMillis > 0) {
                    val diffMillisOverall = overallJourneyDepartureMillis - currentTimeMillis
                    if (diffMillisOverall <= 60000) { // Within a minute or past
                        departsInText = "Now"
                    } else {
                        val diffMinutesOverall = TimeUnit.MILLISECONDS.toMinutes(diffMillisOverall)
                        if (diffMinutesOverall < 1) departsInText = "<1 min"
                        else if (diffMinutesOverall < 60) departsInText = "$diffMinutesOverall min"
                        else {
                            val diffHours = TimeUnit.MILLISECONDS.toHours(diffMillisOverall)
                            val remainingMinutes = diffMinutesOverall % 60
                            departsInText = if (remainingMinutes == 0L) "${diffHours}h" else "${diffHours}h ${remainingMinutes}m"
                        }
                    }
                } else {
                    departsInText = "N/A"
                }
            } else {
                departsInText = "N/A"
            }
            binding.textViewDuration.text = departsInText
            binding.textViewDuration.setTextColor(ContextCompat.getColor(context, R.color.trip_view_teal_highlight))

            if (!tripOption.primaryPublicTransportInfo.isNullOrEmpty()) {
                binding.textViewMainTransportInfo.text = tripOption.primaryPublicTransportInfo
                binding.textViewMainTransportInfo.setTextColor(ContextCompat.getColor(context, R.color.primary_transport_info_color))
                binding.textViewMainTransportInfo.visibility = View.VISIBLE
            } else {
                binding.textViewMainTransportInfo.visibility = View.GONE
            }

            // Column 2: PT Departure Stop, Estimated Time, and Status
            binding.textViewDepartureTime.text = tripOption.firstPTLegEstimatedDepartureTimeFormatted
            binding.textViewDepartureLocation.text = tripOption.firstPTLegDepartureStopName
            binding.textViewStatus.text = tripOption.firstPTLegStatusMessage

            when {
                tripOption.isPTLegRealTimeDataUnavailable -> {
                    binding.textViewStatus.setTextColor(ContextCompat.getColor(context, R.color.text_secondary_dark))
                }
                tripOption.isPTLegLate -> {
                    binding.textViewStatus.setTextColor(ContextCompat.getColor(context, R.color.trip_view_status_late))
                }
                // Add a condition for "early" if you want a different color, e.g., another shade of green or blue
                tripOption.firstPTLegStatusMessage?.contains("early", ignoreCase = true) == true -> {
                    binding.textViewStatus.setTextColor(ContextCompat.getColor(context, R.color.trip_view_status_on_time)) // Or a specific "early" color
                }
                else -> { // On time or default
                    binding.textViewStatus.setTextColor(ContextCompat.getColor(context, R.color.trip_view_status_on_time))
                }
            }

            // Column 3: Final Arrival and Modes Summary
            binding.textViewArrivalTime.text = tripOption.overallJourneyArrivalTimeFormatted
            binding.textViewArrivalLocation.text = tripOption.overallJourneyDestinationName
            binding.textViewTransportModes.text = tripOption.transportModesSummary
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TripOptionViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ListItemTripOptionBinding.inflate(inflater, parent, false)
        return TripOptionViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TripOptionViewHolder, position: Int) {
        holder.bind(tripOptions[position])
    }

    override fun getItemCount(): Int = tripOptions.size

    fun updateData(newTripOptions: List<DisplayableTripOption>) {
        this.tripOptions = newTripOptions
        notifyDataSetChanged() // For simplicity, consider DiffUtil for better performance later
    }
}