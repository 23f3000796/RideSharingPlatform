package com.example.ridesharingapp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.ridesharingapp.databinding.ActivityMainBinding
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Home dashboard: greeting, real reward/level progress, trip stats and entry points.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val locationPermission = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupClickListeners()
        bindUser(RideRepository.currentUser)
        askLocationOnce()
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            RideSharingRepository.refreshProfile().onSuccess { bindUser(it) }
        }
    }

    /** Location powers the map pickers and SOS; ask up-front once so SOS is never blocked. */
    private fun askLocationOnce() {
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val prefs = getSharedPreferences("ui_prefs", MODE_PRIVATE)
        if (!granted && !prefs.getBoolean("asked_location", false)) {
            prefs.edit().putBoolean("asked_location", true).apply()
            locationPermission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }
    }

    private fun bindUser(user: UserProfile) {
        binding.tvHeading.text = "${UiKit.greeting()}, ${UiKit.firstName(user.name)}"
        binding.tvAvatarInitials.text = UiKit.initials(user.name.ifBlank { "?" })
        binding.btnProfile.strokeColor = getColor(if (user.isVerified) R.color.success else android.R.color.transparent)

        binding.tvPoints.text = String.format(Locale.getDefault(), "%,d points", user.rewardPoints)
        UiKit.applyPill(binding.tvLevel, "LV ${user.levelNumber} · ${user.levelTitle.uppercase(Locale.getDefault())}", R.color.warning, R.color.warning_bg)

        val next = user.nextLevelAt
        if (next != null && next > 0) {
            val levelStart = next - (next / user.levelNumber.coerceAtLeast(1))
            val span = (next - levelStart).coerceAtLeast(1)
            val progress = ((user.rewardPoints - levelStart) * 100 / span).coerceIn(0, 100)
            binding.progressLevel.setProgressCompat(progress, true)
            binding.tvLevelHint.text = "${user.pointsToNextLevel} points to Level ${user.levelNumber + 1} · leaderboard ›"
        } else {
            binding.progressLevel.setProgressCompat(100, true)
            binding.tvLevelHint.text = "Max level reached · leaderboard ›"
        }

        binding.tvStatRides.text = user.ridesCompleted.toString()
        binding.tvStatRating.text = if (user.rating > 0f) "${UiKit.rating(user.rating)}★" else "New"
        val upcoming = user.upcomingAsDriver + user.upcomingAsPassenger
        binding.tvStatUpcoming.text = upcoming.toString()

        binding.tvMyRidesSub.text = when {
            user.upcomingAsDriver > 0 && user.upcomingAsPassenger > 0 ->
                "${user.upcomingAsPassenger} booked · ${user.upcomingAsDriver} you're driving"
            user.upcomingAsDriver > 0 -> "${user.upcomingAsDriver} upcoming ride(s) you're driving"
            user.upcomingAsPassenger > 0 -> "${user.upcomingAsPassenger} upcoming booking(s)"
            else -> "Bookings, requests and rides you offer"
        }
        binding.tvSosSub.text = if (user.emergencyContactPhone.isNullOrBlank()) {
            "Alert campus security · add an emergency contact in Profile"
        } else {
            "Alerts campus security and ${user.emergencyContactName ?: "your contact"}"
        }
    }

    private fun setupClickListeners() {
        binding.cardOfferRide.setOnClickListener { startActivity(Intent(this, OfferRideActivity::class.java)) }
        binding.cardFindRide.setOnClickListener { startActivity(Intent(this, SearchRideActivity::class.java)) }
        binding.btnProfile.setOnClickListener { startActivity(Intent(this, ProfileActivity::class.java)) }
        binding.cardRewards.setOnClickListener { startActivity(Intent(this, LeaderboardActivity::class.java)) }
        binding.cardLeaderboard.setOnClickListener { startActivity(Intent(this, LeaderboardActivity::class.java)) }
        binding.cardMyRides.setOnClickListener { startActivity(Intent(this, MyRidesActivity::class.java)) }
        binding.cardEmergency.setOnClickListener { SafetyManager.triggerEmergencySOS(this) }
    }
}
