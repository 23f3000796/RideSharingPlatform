package com.example.ridesharingapp

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.ridesharingapp.databinding.ActivityProfileBinding
import com.example.ridesharingapp.network.ModelMapper
import kotlinx.coroutines.launch
import java.util.Locale

class ProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProfileBinding
    private lateinit var historyAdapter: RideAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { finish() }
        binding.btnEdit.setOnClickListener { startActivity(Intent(this, EditProfileActivity::class.java)) }
        binding.cardEmergency.setOnClickListener { startActivity(Intent(this, EditProfileActivity::class.java)) }
        binding.tvLogout.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Sign out?")
                .setPositiveButton("Sign out") { _, _ ->
                    RideSharingRepository.logout()
                    startActivity(Intent(this, LoginActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK))
                    finish()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        historyAdapter = RideAdapter(showMatch = false) { ride ->
            startActivity(Intent(this, RideDetailsActivity::class.java).putExtra(SearchRideActivity.EXTRA_RIDE, ride))
        }
        binding.rvRideHistory.layoutManager = LinearLayoutManager(this)
        binding.rvRideHistory.adapter = historyAdapter

        bindUser(RideRepository.currentUser)
    }

    override fun onResume() {
        super.onResume()
        loadProfileAndHistory()
    }

    private fun bindUser(user: UserProfile) {
        binding.tvAvatar.text = UiKit.initials(user.name.ifBlank { "?" })
        binding.tvAvatar.background = UiKit.circle(ModelMapper.avatarColorFor(user.userId.ifBlank { user.name }))
        binding.tvUserName.text = user.name
        binding.tvUserLevel.text = user.level
        binding.tvPoints.text = String.format(Locale.getDefault(), "%,d", user.rewardPoints)
        binding.tvRidesCount.text = user.ridesCompleted.toString()
        binding.tvRidesLabel.text = "Trips · ${user.ridesOffered} driven"
        binding.tvRatingValue.text = if (user.rating > 0f) String.format(Locale.getDefault(), "%.1f★", user.rating) else "New"
        binding.tvRatingLabel.text = if (user.reviewsReceived > 0) "${user.reviewsReceived} review(s)" else "Rating"

        binding.tvRollNo.text = user.rollNumber.ifBlank { "—" }
        binding.tvEmail.text = user.collegeEmail.ifBlank { "—" }
        binding.tvCollegeID.text = user.collegeId
        binding.tvDepartment.text = user.department?.takeIf { it.isNotBlank() } ?: "Add in Edit profile"
        binding.tvCourse.text = listOfNotNull(user.course?.takeIf { it.isNotBlank() }, user.year?.let { "Year $it" })
            .joinToString(" · ").ifEmpty { "—" }
        binding.tvGender.text = user.gender
        binding.tvBlood.text = user.bloodGroup?.takeIf { it.isNotBlank() } ?: "—"
        binding.tvVehicle.text = user.vehicleNumber?.takeIf { it.isNotBlank() } ?: "No vehicle added"
        binding.layoutVerified.visibility = if (user.isVerified) View.VISIBLE else View.GONE

        if (user.emergencyContactPhone.isNullOrBlank()) {
            binding.tvEmergencyName.text = "Add an emergency contact"
            binding.tvEmergencyPhone.text = "They can be called or texted your location during an SOS"
        } else {
            binding.tvEmergencyName.text = user.emergencyContactName ?: "Emergency contact"
            binding.tvEmergencyPhone.text = user.emergencyContactPhone
        }
    }

    private fun loadProfileAndHistory() {
        lifecycleScope.launch {
            RideSharingRepository.refreshProfile().fold(
                onSuccess = { bindUser(it) },
                onFailure = { e -> Toast.makeText(this@ProfileActivity, e.message ?: "Profile error", Toast.LENGTH_LONG).show() },
            )
            RideSharingRepository.getMyRides(limit = 20).fold(
                onSuccess = { rides ->
                    historyAdapter.submitList(rides)
                    binding.tvHistoryEmpty.visibility = if (rides.isEmpty()) View.VISIBLE else View.GONE
                },
                onFailure = {
                    historyAdapter.submitList(emptyList())
                    binding.tvHistoryEmpty.visibility = View.VISIBLE
                },
            )
        }
    }
}
