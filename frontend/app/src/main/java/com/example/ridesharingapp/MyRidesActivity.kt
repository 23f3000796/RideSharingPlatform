package com.example.ridesharingapp

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.ridesharingapp.databinding.ActivityMyRidesBinding
import com.example.ridesharingapp.databinding.ItemBookingBinding
import com.example.ridesharingapp.databinding.ItemOfferedRideBinding
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Three tabs:
 *  - Booked:   rides I requested as a passenger (cancel, SOS, rate driver)
 *  - Requests: requests other students sent for my rides (accept / decline, rate passenger)
 *  - Offered:  rides I publish (start → complete, or cancel)
 */
class MyRidesActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_TAB = "extra_tab"
        const val TAB_BOOKED = 0
        const val TAB_REQUESTS = 1
        const val TAB_OFFERED = 2
    }

    private lateinit var binding: ActivityMyRidesBinding
    private lateinit var bookingAdapter: BookingAdapter
    private lateinit var offeredAdapter: OfferedRideAdapter
    private var loadJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMyRidesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        bookingAdapter = BookingAdapter(
            onAccept = ::accept,
            onReject = ::reject,
            onCancel = ::cancel,
            onRate = ::rate,
            onSos = { SafetyManager.triggerEmergencySOS(this, it.rideId) },
            onOpen = ::openRide,
        )
        offeredAdapter = OfferedRideAdapter(
            onStart = ::startRide,
            onComplete = ::completeRide,
            onCancel = ::cancelRide,
            onRequests = { binding.tabLayout.getTabAt(TAB_REQUESTS)?.select() },
            onOpen = { ride -> startActivity(Intent(this, RideDetailsActivity::class.java).putExtra(SearchRideActivity.EXTRA_RIDE, ride)) },
        )
        binding.rvBookings.layoutManager = LinearLayoutManager(this)

        binding.btnBack.setOnClickListener { finish() }
        binding.btnRefresh.setOnClickListener { load() }
        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) = load()
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) = load()
        })

        val startTab = intent.getIntExtra(EXTRA_TAB, TAB_BOOKED).coerceIn(TAB_BOOKED, TAB_OFFERED)
        binding.tabLayout.getTabAt(startTab)?.select()
    }

    override fun onResume() {
        super.onResume()
        load()
    }

    private val currentTab get() = binding.tabLayout.selectedTabPosition

    private fun load() {
        val tab = currentTab
        loadJob?.cancel()
        binding.progress.visibility = View.VISIBLE
        loadJob = lifecycleScope.launch {
            if (tab == TAB_OFFERED) {
                if (binding.rvBookings.adapter !== offeredAdapter) binding.rvBookings.adapter = offeredAdapter
                RideSharingRepository.getMyRides().fold(
                    onSuccess = { rides ->
                        // Active rides first, then history
                        val sorted = rides.sortedWith(
                            compareBy<RideResult> { rank(it.status) }
                                .thenBy { if (rank(it.status) == 0) it.departureMillis else -it.departureMillis },
                        )
                        offeredAdapter.submitList(sorted)
                        showEmpty(sorted.isEmpty(), tab)
                    },
                    onFailure = { e -> offeredAdapter.submitList(emptyList()); showEmpty(true, tab); toast(e.message) },
                )
            } else {
                if (binding.rvBookings.adapter !== bookingAdapter) binding.rvBookings.adapter = bookingAdapter
                val result = if (tab == TAB_BOOKED) RideSharingRepository.getMyBookings() else RideSharingRepository.getDriverBookings()
                result.fold(
                    onSuccess = { list ->
                        bookingAdapter.submitList(list)
                        showEmpty(list.isEmpty(), tab)
                    },
                    onFailure = { e -> bookingAdapter.submitList(emptyList()); showEmpty(true, tab); toast(e.message) },
                )
            }
            binding.progress.visibility = View.GONE
        }
    }

    private fun rank(status: String) = when (status) {
        "Ongoing", "Scheduled" -> 0
        else -> 1
    }

    private fun showEmpty(empty: Boolean, tab: Int) {
        binding.layoutEmpty.visibility = if (empty) View.VISIBLE else View.GONE
        if (!empty) return
        when (tab) {
            TAB_BOOKED -> {
                binding.tvEmptyTitle.text = "No bookings yet"
                binding.tvEmptyBody.text = "Search for a ride going your way and request a seat."
                binding.btnEmptyAction.text = "Find a ride"
                binding.btnEmptyAction.setOnClickListener { startActivity(Intent(this, SearchRideActivity::class.java)) }
            }
            TAB_REQUESTS -> {
                binding.tvEmptyTitle.text = "No requests"
                binding.tvEmptyBody.text = "When students ask to join a ride you offer, they appear here."
                binding.btnEmptyAction.text = "Offer a ride"
                binding.btnEmptyAction.setOnClickListener { startActivity(Intent(this, OfferRideActivity::class.java)) }
            }
            else -> {
                binding.tvEmptyTitle.text = "You haven't offered rides"
                binding.tvEmptyBody.text = "Going to or from campus? Share your empty seats and earn points."
                binding.btnEmptyAction.text = "Offer a ride"
                binding.btnEmptyAction.setOnClickListener { startActivity(Intent(this, OfferRideActivity::class.java)) }
            }
        }
    }

    // ------------------------------------------------------------ actions

    private fun runAction(call: suspend () -> Result<String>, fallback: String) {
        binding.progress.visibility = View.VISIBLE
        lifecycleScope.launch {
            call().fold(
                onSuccess = { msg -> toast(msg.ifBlank { fallback }) },
                onFailure = { e -> toast(e.message ?: "Something went wrong") },
            )
            load()
        }
    }

    private fun confirm(title: String, message: String, positive: String, action: () -> Unit) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(positive) { _, _ -> action() }
            .setNegativeButton("Back", null)
            .show()
    }

    private fun accept(req: BookingRequest) =
        runAction({ RideSharingRepository.acceptBooking(req.bookingId) }, "Booking accepted")

    private fun reject(req: BookingRequest) =
        confirm("Decline request?", "${req.passengerName} will be told their request was declined.", "Decline") {
            runAction({ RideSharingRepository.rejectBooking(req.bookingId) }, "Request declined")
        }

    private fun cancel(req: BookingRequest) =
        confirm(
            if (req.status == "Accepted") "Cancel booking?" else "Withdraw request?",
            "Your seat will be released for other students.",
            "Yes, cancel",
        ) { runAction({ RideSharingRepository.cancelBooking(req.bookingId) }, "Booking cancelled") }

    private fun rate(req: BookingRequest) {
        if (req.reviewTargetId.isBlank()) {
            toast("Can't rate this ride right now")
            return
        }
        RatingDialog.show(this, req.rideId, req.reviewTargetId, req.reviewTargetName, req.viewAsDriver) { load() }
    }

    private fun openRide(req: BookingRequest) {
        binding.progress.visibility = View.VISIBLE
        lifecycleScope.launch {
            RideSharingRepository.getRideDetails(req.rideId).fold(
                onSuccess = { ride ->
                    startActivity(Intent(this@MyRidesActivity, RideDetailsActivity::class.java).putExtra(SearchRideActivity.EXTRA_RIDE, ride))
                },
                onFailure = { e -> toast(e.message) },
            )
            binding.progress.visibility = View.GONE
        }
    }

    private fun startRide(ride: RideResult) =
        confirm(
            "Start this ride?",
            "Pending requests will be declined automatically. Accepted passengers: ${ride.totalSeats - ride.seatsLeft} seat(s).",
            "Start ride",
        ) { runAction({ RideSharingRepository.startRide(ride.rideId) }, "Ride started") }

    private fun completeRide(ride: RideResult) =
        confirm("Complete ride?", "Mark the trip as finished. You and your passengers earn reward points.", "Complete") {
            runAction({ RideSharingRepository.completeRide(ride.rideId) }, "Ride completed")
        }

    private fun cancelRide(ride: RideResult) =
        confirm("Cancel this ride?", "All requests and bookings on this ride will be declined. This can't be undone.", "Cancel ride") {
            runAction({ RideSharingRepository.cancelRide(ride.rideId) }, "Ride cancelled")
        }

    private fun toast(msg: String?) = Toast.makeText(this, msg ?: "Could not load", Toast.LENGTH_LONG).show()

    // ------------------------------------------------------------ adapters

    class BookingAdapter(
        private val onAccept: (BookingRequest) -> Unit,
        private val onReject: (BookingRequest) -> Unit,
        private val onCancel: (BookingRequest) -> Unit,
        private val onRate: (BookingRequest) -> Unit,
        private val onSos: (BookingRequest) -> Unit,
        private val onOpen: (BookingRequest) -> Unit,
    ) : ListAdapter<BookingRequest, BookingAdapter.ViewHolder>(BookingDiffCallback()) {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            ViewHolder(ItemBookingBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(getItem(position))

        inner class ViewHolder(private val b: ItemBookingBinding) : RecyclerView.ViewHolder(b.root) {
            fun bind(r: BookingRequest) {
                val isDriver = r.viewAsDriver
                // A ride cancelled by the driver shows as "Cancelled" for the passenger, not "Declined".
                val shownStatus = when {
                    r.rideStatus == "Cancelled" && r.status != "Completed" -> "Cancelled"
                    r.status == "Accepted" && r.rideStatus == "Ongoing" -> "Ongoing"
                    else -> r.status
                }

                b.tvBookingId.text = "#${r.bookingId.takeLast(6).uppercase()}"
                UiKit.applyStatusPill(b.tvStatus, shownStatus)
                b.tvRideRoute.text = "${UiKit.shortPlace(r.pickupAddress)} → ${UiKit.shortPlace(r.dropAddress)}"
                b.tvRideTime.text = "${r.rideDate} • ${r.rideTime}"

                val name = if (isDriver) r.passengerName else r.driverName
                b.tvAvatar.text = UiKit.initials(name)
                b.tvAvatar.background = UiKit.circle(com.example.ridesharingapp.network.ModelMapper.avatarColorFor(if (isDriver) r.passengerId else r.driverId))
                b.tvDriverOrPassenger.text = (if (isDriver) "Passenger: " else "Driver: ") + r.counterpartyDetail.orEmpty()
                b.tvMeta.text = buildString {
                    append(if (r.seatsRequested == 1) "1 seat" else "${r.seatsRequested} seats")
                    if (r.pricePerSeat > 0) append(" · ₹${r.pricePerSeat * r.seatsRequested}")
                    if (isDriver && r.passengerRating > 0f) append(" · ★ ${UiKit.rating(r.passengerRating)}")
                    if (!isDriver && r.vehicleLabel.isNotBlank()) append(" · ${r.vehicleLabel}")
                }

                b.root.setOnClickListener { onOpen(r) }
                b.btnAccept.visibility = View.GONE
                b.btnReject.visibility = View.GONE
                b.layoutDriverControls.visibility = View.VISIBLE
                var hint: String? = null

                fun primary(text: String, colorRes: Int, action: () -> Unit) {
                    b.btnAccept.visibility = View.VISIBLE
                    b.btnAccept.text = text
                    b.btnAccept.setBackgroundColor(b.root.context.getColor(colorRes))
                    b.btnAccept.setOnClickListener { action() }
                }
                fun secondary(text: String, action: () -> Unit) {
                    b.btnReject.visibility = View.VISIBLE
                    b.btnReject.text = text
                    b.btnReject.setOnClickListener { action() }
                }

                when {
                    shownStatus == "Cancelled" -> {
                        hint = if (isDriver) "You cancelled this ride." else "The driver cancelled this ride. Search again for another one."
                        b.layoutDriverControls.visibility = View.GONE
                    }
                    isDriver && r.status == "Pending" -> {
                        hint = r.rideSeatsLeft?.let { "$it seat(s) still free on this ride" }
                        secondary("Decline") { onReject(r) }
                        primary("Accept", R.color.success) { onAccept(r) }
                    }
                    !isDriver && r.status == "Pending" -> {
                        hint = "Waiting for the driver to respond."
                        secondary("Withdraw") { onCancel(r) }
                    }
                    !isDriver && r.status == "Accepted" && r.rideStatus == "Ongoing" -> {
                        hint = "Ride in progress. Stay safe!"
                        primary("SOS", R.color.danger) { onSos(r) }
                    }
                    !isDriver && r.status == "Accepted" -> {
                        hint = "Be at the pickup point a few minutes early."
                        secondary("Cancel booking") { onCancel(r) }
                    }
                    isDriver && r.status == "Accepted" -> {
                        hint = if (r.rideStatus == "Ongoing") "On board now." else "Confirmed. Start the ride from the Offered tab."
                        b.layoutDriverControls.visibility = View.GONE
                    }
                    r.status == "Completed" && !r.reviewed -> {
                        hint = "Trip completed."
                        primary(if (isDriver) "Rate passenger" else "Rate driver", R.color.gold) { onRate(r) }
                    }
                    r.status == "Completed" -> {
                        hint = "Trip completed · thanks for rating!"
                        b.layoutDriverControls.visibility = View.GONE
                    }
                    else -> b.layoutDriverControls.visibility = View.GONE
                }

                b.tvHint.text = hint.orEmpty()
                b.tvHint.visibility = if (hint.isNullOrBlank()) View.GONE else View.VISIBLE
            }
        }
    }

    class BookingDiffCallback : DiffUtil.ItemCallback<BookingRequest>() {
        override fun areItemsTheSame(old: BookingRequest, new: BookingRequest) = old.bookingId == new.bookingId
        override fun areContentsTheSame(old: BookingRequest, new: BookingRequest) = old == new
    }

    class OfferedRideAdapter(
        private val onStart: (RideResult) -> Unit,
        private val onComplete: (RideResult) -> Unit,
        private val onCancel: (RideResult) -> Unit,
        private val onRequests: (RideResult) -> Unit,
        private val onOpen: (RideResult) -> Unit,
    ) : ListAdapter<RideResult, OfferedRideAdapter.ViewHolder>(RideAdapter.RideDiffCallback()) {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            ViewHolder(ItemOfferedRideBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(getItem(position))

        inner class ViewHolder(private val b: ItemOfferedRideBinding) : RecyclerView.ViewHolder(b.root) {
            fun bind(ride: RideResult) {
                val booked = (ride.totalSeats - ride.seatsLeft).coerceAtLeast(0)
                b.tvWhen.text = "${ride.date} • ${ride.time}"
                UiKit.applyStatusPill(b.tvStatus, ride.status)
                b.tvRoute.text = "${UiKit.shortPlace(ride.pickupAddress)} → ${UiKit.shortPlace(ride.dropAddress)}"
                b.tvDetails.text = listOf(
                    ride.vehicleType.takeIf { it.isNotBlank() },
                    "₹${ride.pricePerSeat}/seat",
                    "$booked of ${ride.totalSeats} seats booked",
                ).filterNotNull().joinToString(" · ")

                if (ride.pendingRequests > 0 && ride.status == "Scheduled") {
                    UiKit.setVisible(b.tvPending, true)
                    UiKit.applyPill(b.tvPending, "${ride.pendingRequests} NEW REQUEST${if (ride.pendingRequests > 1) "S" else ""} · TAP TO REVIEW", R.color.warning, R.color.warning_bg)
                    b.tvPending.setOnClickListener { onRequests(ride) }
                } else {
                    UiKit.setVisible(b.tvPending, false)
                }

                b.root.setOnClickListener { onOpen(ride) }
                val ctx = b.root.context
                when (ride.status) {
                    "Scheduled" -> {
                        UiKit.setVisible(b.layoutActions, true)
                        UiKit.setVisible(b.btnSecondary, true)
                        b.btnSecondary.text = "Cancel ride"
                        b.btnSecondary.setOnClickListener { onCancel(ride) }
                        b.btnPrimary.text = "Start ride"
                        b.btnPrimary.setBackgroundColor(ctx.getColor(R.color.accent_blue))
                        b.btnPrimary.setOnClickListener { onStart(ride) }
                    }
                    "Ongoing" -> {
                        UiKit.setVisible(b.layoutActions, true)
                        UiKit.setVisible(b.btnSecondary, false)
                        b.btnPrimary.text = "Complete ride"
                        b.btnPrimary.setBackgroundColor(ctx.getColor(R.color.success))
                        b.btnPrimary.setOnClickListener { onComplete(ride) }
                    }
                    else -> UiKit.setVisible(b.layoutActions, false)
                }
            }
        }
    }
}
