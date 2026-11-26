package com.example.ridesharingapp

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.ridesharingapp.databinding.ActivityRideSummaryBinding
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.PolylineOptions

class RideSummaryActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var binding: ActivityRideSummaryBinding
    private var ride: RideResult? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRideSummaryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ride = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(SearchRideActivity.EXTRA_RIDE, RideResult::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(SearchRideActivity.EXTRA_RIDE)
        }

        setupUI()

        (supportFragmentManager.findFragmentById(R.id.mapSummary) as? SupportMapFragment)?.getMapAsync(this)
    }

    private fun setupUI() {
        binding.toolbar.setNavigationOnClickListener { finish() }

        ride?.let {
            binding.tvSummaryRoute.text = "Ride published 🎉  ${it.date}, ${it.time}"
            binding.tvSummaryPickup.text = "From: ${it.pickupAddress}"
            binding.tvSummaryDrop.text = "To: ${it.dropAddress}"
            // pricePerSeat is an Int: the old "%.2f" format crashed this screen on every publish.
            binding.tvSummaryPrice.text = if (it.pricePerSeat == 0) "Free" else "₹${it.pricePerSeat} / seat"
            binding.tvSummarySeats.text = "${it.seatsLeft} seat(s) open · requests appear in My Rides"
        }
        binding.btnDone.text = "View my rides"

        binding.btnDone.setOnClickListener {
            startActivity(
                Intent(this, MyRidesActivity::class.java)
                    .putExtra(MyRidesActivity.EXTRA_TAB, MyRidesActivity.TAB_OFFERED),
            )
            finish()
        }
        binding.btnDone.setOnLongClickListener {
            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(intent)
            finish()
            true
        }
    }

    override fun onMapReady(map: GoogleMap) {
        map.uiSettings.isMapToolbarEnabled = false
        
        ride?.let { data ->
            val pickup = LatLng(data.pickupLat, data.pickupLng)
            val drop = LatLng(data.dropLat, data.dropLng)

            map.addMarker(MarkerOptions().position(pickup).title("Pickup"))
            map.addMarker(MarkerOptions().position(drop).title("Drop"))

            val road = Polyline.decode(data.routePolyline).takeIf { it.size >= 2 } ?: listOf(pickup, drop)
            map.addPolyline(PolylineOptions()
                .addAll(road)
                .width(12f)
                .color(getColor(R.color.accent_blue))
                .geodesic(true))

            val bounds = LatLngBounds.Builder().apply { road.forEach { include(it) } }.build()

            binding.root.post {
                try {
                    val padding = (resources.displayMetrics.density * 100).toInt()
                    map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, padding))
                } catch (e: Exception) {
                    map.moveCamera(CameraUpdateFactory.newLatLngZoom(pickup, 14f))
                }
            }
        }
    }
}
