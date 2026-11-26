package com.example.ridesharingapp

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.ridesharingapp.databinding.ItemRideResultBinding

/**
 * Ride card used by search results (with route-fit info) and by ride history.
 */
class RideAdapter(
    private val showMatch: Boolean = false,
    private val onBookClick: (RideResult) -> Unit,
) : ListAdapter<RideResult, RideAdapter.RideViewHolder>(RideDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RideViewHolder {
        val binding = ItemRideResultBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return RideViewHolder(binding)
    }

    override fun onBindViewHolder(holder: RideViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class RideViewHolder(private val binding: ItemRideResultBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(ride: RideResult) {
            val ctx = binding.root.context
            val isMine = ride.driverId.isNotBlank() && ride.driverId == RideRepository.currentUserId

            binding.tvAvatar.text = UiKit.initials(ride.driverName.ifBlank { "You" })
            binding.tvAvatar.background = UiKit.circle(ride.avatarColor)
            binding.tvDriverName.text = if (isMine) "You" else ride.driverName
            UiKit.setVisible(binding.imgVerified, ride.driverVerified)
            binding.tvDriverRating.text = UiKit.rating(ride.driverRating)
            binding.tvVehicleType.text = ride.vehicleType.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty()

            binding.tvPickup.text = ride.pickupAddress
            binding.tvDrop.text = ride.dropAddress
            binding.tvDateTime.text = "${ride.date} · ${ride.time}"

            val match = ride.match
            if (showMatch && match != null) {
                binding.tvPrice.text = "₹${ride.estimatedFare}"
                binding.tvPriceNote.text = if (ride.estimatedFare < ride.pricePerSeat) "your fare" else "per seat"
                UiKit.setVisible(binding.layoutMatch, true)
                if (match.isExact) {
                    UiKit.applyPill(binding.tvMatchBadge, "SAME ROUTE", R.color.success, R.color.success_bg)
                } else {
                    UiKit.applyPill(binding.tvMatchBadge, "ON YOUR WAY", R.color.info, R.color.info_bg)
                }
                binding.tvMatchInfo.text = buildString {
                    append("Pickup ${UiKit.km(match.pickupDistanceKm)}")
                    if (match.pickupDistanceKm >= 0.1) append(" away")
                    append(" · drop ${UiKit.km(match.dropDistanceKm)}")
                    if (match.dropDistanceKm >= 0.1) append(" away")
                    val eta = match.pickupEtaMin
                    if (eta != null && eta > 0 && ride.departureMillis > 0) {
                        val at = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault())
                            .format(java.util.Date(ride.departureMillis + eta * 60_000L))
                        append("\nReaches your pickup ~$at")
                    }
                }
            } else {
                binding.tvPrice.text = "₹${ride.pricePerSeat}"
                binding.tvPriceNote.text = ctx.getString(R.string.label_per_seat)
                if (!showMatch && ride.status != "Scheduled") {
                    UiKit.setVisible(binding.layoutMatch, true)
                    UiKit.applyStatusPill(binding.tvMatchBadge, ride.status)
                    binding.tvMatchInfo.text = ""
                } else {
                    UiKit.setVisible(binding.layoutMatch, false)
                }
            }

            binding.tvSeatsLeft.text = when {
                ride.seatsLeft <= 0 -> "Full"
                ride.seatsLeft == 1 -> "1 seat left"
                else -> "${ride.seatsLeft} seats left"
            }
            binding.tvSeatsLeft.setTextColor(
                ctx.getColor(if (ride.seatsLeft <= 1) R.color.warning else R.color.text_secondary),
            )

            binding.btnBook.text = when {
                isMine -> "Manage"
                ride.myBookingStatus == "Pending" -> "Requested"
                ride.myBookingStatus == "Accepted" -> "Booked ✓"
                else -> if (showMatch) "View" else "Details"
            }
            binding.btnBook.setOnClickListener { onBookClick(ride) }
            binding.root.setOnClickListener { onBookClick(ride) }
        }
    }

    class RideDiffCallback : DiffUtil.ItemCallback<RideResult>() {
        override fun areItemsTheSame(oldItem: RideResult, newItem: RideResult) = oldItem.rideId == newItem.rideId
        override fun areContentsTheSame(oldItem: RideResult, newItem: RideResult) = oldItem == newItem
    }
}
