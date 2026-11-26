package com.example.ridesharingapp

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.ridesharingapp.databinding.ActivityEditProfileBinding
import com.example.ridesharingapp.network.dto.EmergencyContactDto
import com.example.ridesharingapp.network.dto.UpdateProfileRequestDto
import com.google.android.material.chip.Chip
import kotlinx.coroutines.launch
import java.util.Locale

/** Edit department/course/year, gender, blood group, vehicle and emergency contact. */
class EditProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEditProfileBinding
    private val bloodGroups = listOf("A+", "A-", "B+", "B-", "AB+", "AB-", "O+", "O-")
    private val phoneRegex = Regex("^\\+?[0-9 ()-]{7,20}$")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEditProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        bloodGroups.forEach { g ->
            binding.groupBlood.addView(
                Chip(this, null, com.google.android.material.R.attr.chipStyle).apply {
                    text = g
                    isCheckable = true
                    id = android.view.View.generateViewId()
                    tag = g
                },
            )
        }

        binding.btnBack.setOnClickListener { finish() }
        binding.btnSave.setOnClickListener { save() }
        prefill(RideRepository.currentUser)
    }

    private fun prefill(u: UserProfile) {
        binding.etName.setText(u.name)
        binding.etDepartment.setText(u.department.orEmpty())
        binding.etCourse.setText(u.course.orEmpty())
        binding.etYear.setText(u.year?.toString().orEmpty())
        binding.etVehicle.setText(u.vehicleNumber.orEmpty())
        binding.etContactName.setText(u.emergencyContactName.orEmpty())
        binding.etContactPhone.setText(u.emergencyContactPhone.orEmpty())
        when (u.gender.lowercase(Locale.US)) {
            "male" -> binding.chipMale.isChecked = true
            "female" -> binding.chipFemale.isChecked = true
            "other" -> binding.chipOther.isChecked = true
        }
        for (i in 0 until binding.groupBlood.childCount) {
            val chip = binding.groupBlood.getChildAt(i) as Chip
            chip.isChecked = chip.tag == u.bloodGroup
        }
    }

    private fun save() {
        val name = binding.etName.text?.toString()?.trim().orEmpty()
        if (name.length < 2) {
            binding.etName.error = "Enter your name"
            return
        }
        val yearText = binding.etYear.text?.toString()?.trim().orEmpty()
        val year = yearText.toIntOrNull()
        if (yearText.isNotEmpty() && (year == null || year !in 1..6)) {
            binding.etYear.error = "1–6"
            return
        }
        val contactName = binding.etContactName.text?.toString()?.trim().orEmpty()
        val contactPhone = binding.etContactPhone.text?.toString()?.trim().orEmpty()
        val relation = binding.etContactRelation.text?.toString()?.trim().orEmpty()
        if (contactPhone.isNotEmpty() && !phoneRegex.matches(contactPhone)) {
            binding.tilPhone.error = "Enter a valid phone number"
            return
        }
        binding.tilPhone.error = null
        if (contactPhone.isNotEmpty() && contactName.isEmpty()) {
            binding.etContactName.error = "Who is this?"
            return
        }

        val gender = when (binding.groupGender.checkedChipId) {
            binding.chipMale.id -> "Male"
            binding.chipFemale.id -> "Female"
            binding.chipOther.id -> "Other"
            else -> null
        }
        val blood = (0 until binding.groupBlood.childCount)
            .map { binding.groupBlood.getChildAt(it) as Chip }
            .firstOrNull { it.isChecked }?.tag as? String

        val body = UpdateProfileRequestDto(
            name = name,
            department = binding.etDepartment.text?.toString()?.trim()?.takeIf { it.isNotEmpty() },
            course = binding.etCourse.text?.toString()?.trim()?.takeIf { it.isNotEmpty() },
            year = year,
            gender = gender,
            bloodGroup = blood,
            vehicleNumber = binding.etVehicle.text?.toString()?.trim()?.uppercase(Locale.getDefault())?.takeIf { it.isNotEmpty() },
            emergencyContacts = if (contactPhone.isNotEmpty()) {
                EmergencyContactDto(contactName, contactPhone, relation.takeIf { it.isNotEmpty() })
            } else {
                null
            },
        )

        binding.btnSave.isEnabled = false
        binding.btnSave.text = "Saving…"
        lifecycleScope.launch {
            val result = RideSharingRepository.updateProfile(body)
            binding.btnSave.isEnabled = true
            binding.btnSave.text = "Save changes"
            result.fold(
                onSuccess = {
                    Toast.makeText(this@EditProfileActivity, "Profile updated", Toast.LENGTH_SHORT).show()
                    finish()
                },
                onFailure = { e -> Toast.makeText(this@EditProfileActivity, e.message ?: "Could not save", Toast.LENGTH_LONG).show() },
            )
        }
    }
}
