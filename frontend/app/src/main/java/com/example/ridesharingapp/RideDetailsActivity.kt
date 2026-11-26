package com.example.ridesharingapp

import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.ridesharingapp.databinding.ActivityRideDetailsBinding
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.Dash
import com.google.android.gms.maps.model.Gap
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.PolylineOptions
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Ride details: driver, route, how the ride fits the passenger's own trip, seat picker
 * and the request / cancel action. Also reachable for drivers (read-only "hosting" view).
 */
class RideDetailsActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var binding: ActivityRideDetailsBinding
    private var ride: RideResult? = null
    private var trip: PassengerTrip? = null
    private var googleMap: GoogleMap? = null
    private var seats = 1
    private var busy = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRideDetailsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        binding.toolbar.setNavigationOnClickListener { finish() }

        ride = parcelable(SearchRideActivity.EXTRA_RIDE)
        trip = parcelable(SearchRideActivity.EXTRA_TRIP)

        val initial = ride
        if (initial == null) {
            Toast.makeText(this, "Ride details not found", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        seats = (trip?.seats ?: 1).coerceAtLeast(1)

        setupActions()
        bindRide(initial)

        (supportFragmentManager.findFragmentById(R.id.mapDetail) as? SupportMapFragment)?.getMapAsync(this)
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private inline fun <reified T : android.os.Parcelable> parcelable(key: String): T? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(key, T::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(key)
        }

    /** Pull fresh seats / status / my booking from the server, keeping the search match info. */
    private fun refresh() {
        val current = ride ?: return
        lifecycleScope.launch {
            RideSharingRepository.getRideDetails(current.rideId).onSuccess { fresh ->
                val merged = fresh.copy(
                    match = current.match,
                    estimatedFare = if (current.match != null) current.estimatedFare else fresh.pricePerSeat,
                    avatarColor = current.avatarColor,
                    driverGender = current.driverGender.takeIf { fresh.driverGender == "Any" } ?: fresh.driverGender,
                )
                ride = merged
                bindRide(merged)
            }
        }
    }

    private fun isMine(r: RideResult) = r.isDriver || (r.driverId.isNotBlank() && r.driverId == RideRepository.currentUserId)

    private fun setupActions() {
        binding.btnSeatMinus.setOnClickListener { changeSeats(-1) }
        binding.btnSeatPlus.setOnClickListener { changeSeats(+1) }
        binding.btnShare.setOnClickListener { ride?.let { UiKit.shareText(this, UiKit.shareRideText(it), "Share trip details") } }
        binding.btnSOS.setOnClickListener { SafetyManager.triggerEmergencySOS(this, ride?.rideId) }
        binding.btnRequestRide.setOnClickListener { onPrimaryAction() }
    }

    private fun changeSeats(delta: Int) {
        val max = (ride?.seatsLeft ?: 1).coerceIn(1, 8)
        seats = (seats + delta).coerceIn(1, max)
        binding.tvSeatCount.text = seats.toString()
        ride?.let { bindPrice(it) }
    }

    private fun bindPrice(r: RideResult) {
        val perSeat = if (r.match != null) r.estimatedFare else r.pricePerSeat
        binding.tvPrice.text = "₹${perSeat * seats}"
        binding.tvPriceNote.text = when {
            seats > 1 -> "₹$perSeat × $seats seats"
            r.match != null && r.estimatedFare < r.pricePerSeat -> "your fare (full route ₹${r.pricePerSeat})"
            else -> "per seat"
        }
    }

    private fun bindRide(r: RideResult) {
        val mine = isMine(r)

        binding.tvAvatar.text = UiKit.initials(r.driverName)
        binding.tvAvatar.background = UiKit.circle(r.avatarColor)
        binding.tvDriverName.text = if (mine) "${r.driverName} (you)" else r.driverName
        UiKit.setVisible(binding.imgVerified, r.driverVerified)
        binding.tvRating.text = "${UiKit.rating(r.driverRating)} · Level ${r.driverLevel}"
        binding.tvPickup.text = r.pickupAddress
        binding.tvDrop.text = r.dropAddress
        binding.tvVehicle.text = listOf(r.vehicleType, r.vehicleNumber).filter { it.isNotBlank() }.joinToString(" • ").ifEmpty { "Vehicle details not added" }
        binding.tvTime.text = "${r.date} • ${r.time}" + (r.routeKm?.let { String.format(Locale.getDefault(), "  ·  %.1f km trip", it) } ?: "")
        binding.tvSeatsLeft.text = when {
            r.seatsLeft <= 0 -> "Ride is full"
            else -> "${r.seatsLeft} of ${r.totalSeats} seats left"
        }
        if (r.passengerNames.isNotEmpty()) {
            binding.tvPassengers.text = "On board: ${r.passengerNames.joinToString(", ")}"
            UiKit.setVisible(binding.tvPassengers, true)
        } else {
            UiKit.setVisible(binding.tvPassengers, false)
        }

        bindMatch(r)
        if (seats > r.seatsLeft) seats = r.seatsLeft.coerceAtLeast(1)
        binding.tvSeatCount.text = seats.toString()
        bindPrice(r)
        bindStatusAndAction(r, mine)
    }

    private fun bindMatch(r: RideResult) {
        val m = r.match
        if (m == null) {
            UiKit.setVisible(binding.cardMatch, false)
            return
        }
        UiKit.setVisible(binding.cardMatch, true)
        binding.tvMatchTitle.text = if (m.isExact) "Same start and destination as you" else "This driver passes along your route"
        binding.tvMatchDetail.text = buildString {
            append(if (m.pickupDistanceKm < 0.1) "• Picks you up right on the route" else "• Meet the driver ${UiKit.km(m.pickupDistanceKm)} from your pickup")
            append('\n')
            append(if (m.dropDistanceKm < 0.1) "• Drops you right at your destination" else "• Dropped ${UiKit.km(m.dropDistanceKm)} from your destination")
            if (!m.isExact && m.routeKm > 0) {
                append('\n')
                append(String.format(Locale.getDefault(), "• You ride %.1f km of their %.1f km trip", m.passengerTripKm, m.routeKm))
            }
            if (m.detourKm >= 0.3) {
                append('\n')
                append("• Adds about ${UiKit.km(m.detourKm)} to the driver's trip")
            }
        }
    }

    private fun bindStatusAndAction(r: RideResult, mine: Boolean) {
        val btn = binding.btnRequestRide
        var canPickSeats = false
        btn.isEnabled = !busy

        when {
            mine -> {
                showStatus("YOU ARE HOSTING THIS RIDE · ${r.status.uppercase(Locale.getDefault())}", R.color.purple, R.color.purple_bg)
                btn.text = if (r.pendingRequests > 0) "Review ${r.pendingRequests} request(s)" else "Manage in My Rides"
                btn.setBackgroundColor(getColor(R.color.primary_dark_blue))
            }
            r.status != "Scheduled" -> {
                showStatus("RIDE ${UiKit.statusLabel(r.status).uppercase(Locale.getDefault())}", UiKit.statusColors(r.status).first, UiKit.statusColors(r.status).second)
                btn.text = if (r.myBookingStatus == "Accepted" || r.myBookingStatus == "Completed") "View in My Rides" else "Not accepting requests"
                btn.isEnabled = r.myBookingStatus == "Accepted" || r.myBookingStatus == "Completed"
                btn.setBackgroundColor(getColor(R.color.primary_dark_blue))
            }
            r.myBookingStatus == "Pending" -> {
                showStatus("REQUEST SENT · WAITING FOR DRIVER", R.color.warning, R.color.warning_bg)
                btn.text = "Cancel request"
                btn.setBackgroundColor(getColor(R.color.danger))
            }
            r.myBookingStatus == "Accepted" -> {
                showStatus("YOU'RE BOOKED · ${r.myBookingSeats.coerceAtLeast(1)} SEAT(S)", R.color.success, R.color.success_bg)
                btn.text = "Cancel booking"
                btn.setBackgroundColor(getColor(R.color.danger))
            }
            r.seatsLeft <= 0 -> {
                showStatus(if (r.myBookingStatus == "Rejected") "YOUR REQUEST WAS DECLINED" else "RIDE IS FULL", R.color.danger, R.color.danger_bg)
                btn.text = "Ride is full"
                btn.isEnabled = false
            }
            else -> {
                if (r.myBookingStatus == "Rejected") {
                    showStatus("YOUR LAST REQUEST WAS DECLINED", R.color.danger, R.color.danger_bg)
                } else {
                    UiKit.setVisible(binding.tvDriverStatus, false)
                }
                canPickSeats = true
                btn.text = if (seats > 1) "Request $seats seats" else "Request to join"
                btn.setBackgroundColor(getColor(R.color.accent_blue))
            }
        }
        UiKit.setVisible(binding.layoutSeatPicker, canPickSeats && r.seatsLeft > 1)
    }

    private fun showStatus(text: String, fg: Int, bg: Int) {
        UiKit.setVisible(binding.tvDriverStatus, true)
        UiKit.applyPill(binding.tvDriverStatus, text, fg, bg)
    }

    private fun onPrimaryAction() {
        val r = ride ?: return
        when {
            isMine(r) || r.status != "Scheduled" -> {
                startActivity(
                    Intent(this, MyRidesActivity::class.java)
                        .putExtra(MyRidesActivity.EXTRA_TAB, if (isMine(r)) MyRidesActivity.TAB_OFFERED else MyRidesActivity.TAB_BOOKED),
                )
            }
            r.myBookingStatus == "Pending" || r.myBookingStatus == "Accepted" -> confirmCancel(r)
            else -> confirmRequest(r)
        }
    }

    private fun confirmRequest(r: RideResult) {
        val perSeat = if (r.match != null) r.estimatedFare else r.pricePerSeat
        val pickup = trip?.pickupAddress?.takeIf { it.isNotBlank() } ?: r.pickupAddress
        val drop = trip?.dropAddress?.takeIf { it.isNotBlank() } ?: r.dropAddress
        AlertDialog.Builder(this)
            .setTitle("Request $seats seat(s)?")
            .setMessage(
                "Pickup: ${UiKit.shortPlace(pickup)}\nDrop: ${UiKit.shortPlace(drop)}\n" +
                    "Departure: ${r.date}, ${r.time}\nFare: ₹${perSeat * seats} (pay ${r.driverName.ifBlank { "the driver" }} directly)",
            )
            .setPositiveButton("Send request") { _, _ -> sendRequest(r, pickup, drop) }
            .setNegativeButton("Not now", null)
            .show()
    }

    private fun sendRequest(r: RideResult, pickup: String, drop: String) {
        setBusy(true)
        lifecycleScope.launch {
            val result = RideSharingRepository.requestBooking(r.rideId, pickup, drop, seats)
            setBusy(false)
            result.fold(
                onSuccess = {
                    AlertDialog.Builder(this@RideDetailsActivity)
                        .setTitle("Request sent ✅")
                        .setMessage("${r.driverName.ifBlank { "The driver" }} will review your request. You'll see the status in My Rides.")
                        .setPositiveButton("OK", null)
                        .show()
                    refresh()
                },
                onFailure = { e -> toast(e.message ?: "Booking failed") },
            )
        }
    }

    private fun confirmCancel(r: RideResult) {
        val bookingId = r.myBookingId ?: run {
            toast("Open My Rides to manage this booking")
            return
        }
        AlertDialog.Builder(this)
            .setTitle(if (r.myBookingStatus == "Accepted") "Cancel your booking?" else "Withdraw request?")
            .setMessage("Your seat will be released for other students.")
            .setPositiveButton("Yes, cancel") { _, _ ->
                setBusy(true)
                lifecycleScope.launch {
                    val res = RideSharingRepository.cancelBooking(bookingId)
                    setBusy(false)
                    res.fold(
                        onSuccess = { toast("Booking cancelled"); refresh() },
                        onFailure = { e -> toast(e.message ?: "Could not cancel") },
                    )
                }
            }
            .setNegativeButton("Keep it", null)
            .show()
    }

    private fun setBusy(value: Boolean) {
        busy = value
        binding.btnRequestRide.isEnabled = !value
        UiKit.setVisible(binding.progressDetails, value)
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        map.uiSettings.isMapToolbarEnabled = false
        val r = ride ?: return

        val start = LatLng(r.pickupLat, r.pickupLng)
        val end = LatLng(r.dropLat, r.dropLng)
        val bounds = LatLngBounds.Builder().include(start).include(end)

        map.addMarker(MarkerOptions().position(start).title("Driver starts").snippet(r.pickupAddress)
            .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)))
        map.addMarker(MarkerOptions().position(end).title("Driver ends").snippet(r.dropAddress)
            .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)))
        map.addPolyline(PolylineOptions().add(start, end).width(12f).color(getColor(R.color.accent_blue)).geodesic(true))

        // Passenger's own points + dashed "walk to the route" hints
        trip?.let { t ->
            val myPickup = LatLng(t.pickupLat, t.pickupLng)
            val myDrop = LatLng(t.dropLat, t.dropLng)
            bounds.include(myPickup).include(myDrop)
            map.addMarker(MarkerOptions().position(myPickup).title("Your pickup").snippet(t.pickupAddress)
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)))
            map.addMarker(MarkerOptions().position(myDrop).title("Your destination").snippet(t.dropAddress)
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_VIOLET)))
            r.match?.let { m ->
                val pOnRoute = pointAlong(start, end, m.pickupFraction)
                val dOnRoute = pointAlong(start, end, m.dropFraction)
                val dash = listOf(Dash(20f), Gap(14f))
                map.addPolyline(PolylineOptions().add(myPickup, pOnRoute).width(6f).color(Color.DKGRAY).pattern(dash))
                map.addPolyline(PolylineOptions().add(dOnRoute, myDrop).width(6f).color(Color.DKGRAY).pattern(dash))
                // Highlight the stretch the passenger rides
                map.addPolyline(PolylineOptions().add(pOnRoute, dOnRoute).width(16f).color(getColor(R.color.success)).zIndex(1f))
            }
        }

        binding.root.post {
            try {
                map.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds.build(), UiKit.dp(this, 56)))
            } catch (_: Exception) {
                map.moveCamera(CameraUpdateFactory.newLatLngZoom(start, 13f))
            }
        }
    }

    private fun pointAlong(a: LatLng, b: LatLng, f: Double): LatLng {
        val t = f.coerceIn(0.0, 1.0)
        return LatLng(a.latitude + (b.latitude - a.latitude) * t, a.longitude + (b.longitude - a.longitude) * t)
    }
}
