package com.example.ridesharingapp

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.example.ridesharingapp.databinding.ActivityOfferRideBinding
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class OfferRideActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOfferRideBinding

    private var selectedSeats = 0
    private var selectedVehicle = ""
    private var selectedDate = ""
    private var selectedTime = ""

    // Map data
    private var pickupLat = 0.0
    private var pickupLng = 0.0
    private var dropLat = 0.0
    private var dropLng = 0.0
    private var pickupAddress = ""
    private var dropAddress = ""

    private lateinit var seatChips: List<TextView>
    private lateinit var vehicleChips: List<TextView>

    private lateinit var pickupMapLauncher: ActivityResultLauncher<Intent>
    private lateinit var dropMapLauncher: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOfferRideBinding.inflate(layoutInflater)
        setContentView(binding.root)

        seatChips = listOf(
            binding.chipSeat1, binding.chipSeat2, binding.chipSeat3,
            binding.chipSeat4, binding.chipSeat5, binding.chipSeat6
        )

        vehicleChips = listOf(
            binding.chipCar, binding.chipSuv, binding.chipVan, binding.chipAuto
        )

        setupMapLaunchers()
        setupBackButton()
        setupLocationPickers()
        setupDatePicker()
        setupTimePicker()
        setupSeatChips()
        setupVehicleChips()
        setupSubmitButton()
    }

    // ───────── MAP ─────────

    private fun setupMapLaunchers() {
        pickupMapLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == RESULT_OK && result.data != null) {
                val data = result.data!!
                pickupLat = data.getDoubleExtra(MapPickerActivity.RESULT_LATITUDE, 0.0)
                pickupLng = data.getDoubleExtra(MapPickerActivity.RESULT_LONGITUDE, 0.0)
                pickupAddress = data.getStringExtra(MapPickerActivity.RESULT_ADDRESS) ?: ""

                binding.tvPickup.text = pickupAddress
                binding.tvPickup.setTextColor(getColor(R.color.text_primary))
            }
        }

        dropMapLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == RESULT_OK && result.data != null) {
                val data = result.data!!
                dropLat = data.getDoubleExtra(MapPickerActivity.RESULT_LATITUDE, 0.0)
                dropLng = data.getDoubleExtra(MapPickerActivity.RESULT_LONGITUDE, 0.0)
                dropAddress = data.getStringExtra(MapPickerActivity.RESULT_ADDRESS) ?: ""

                binding.tvDrop.text = dropAddress
                binding.tvDrop.setTextColor(getColor(R.color.text_primary))
            }
        }
    }

    private fun setupLocationPickers() {
        binding.layoutPickup.setOnClickListener {
            val intent = Intent(this, MapPickerActivity::class.java)
            intent.putExtra(
                MapPickerActivity.EXTRA_TITLE,
                getString(R.string.map_picker_title_pickup)
            )
            pickupMapLauncher.launch(intent)
        }

        binding.layoutDrop.setOnClickListener {
            val intent = Intent(this, MapPickerActivity::class.java)
            intent.putExtra(
                MapPickerActivity.EXTRA_TITLE,
                getString(R.string.map_picker_title_drop)
            )
            dropMapLauncher.launch(intent)
        }
    }

    private fun setupBackButton() {
        binding.btnBack.setOnClickListener { finish() }
    }

    // ───────── DATE ─────────

    private fun setupDatePicker() {
        binding.layoutDate.setOnClickListener {
            val cal = Calendar.getInstance()

            DatePickerDialog(
                this,
                { _, year, month, day ->
                    cal.set(year, month, day)

                    selectedDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                        .format(cal.time)

                    val display = SimpleDateFormat("EEE, dd MMM", Locale.getDefault())
                    binding.tvDate.text = display.format(cal.time)
                    binding.tvDate.setTextColor(getColor(R.color.text_primary))
                },
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH)
            ).apply {
                datePicker.minDate = System.currentTimeMillis() - 1000
            }.show()
        }
    }

    // ───────── TIME ─────────

    private fun setupTimePicker() {
        binding.layoutTime.setOnClickListener {
            val cal = Calendar.getInstance()

            TimePickerDialog(
                this,
                { _, hour, minute ->
                    cal.set(Calendar.HOUR_OF_DAY, hour)
                    cal.set(Calendar.MINUTE, minute)

                    selectedTime = SimpleDateFormat("HH:mm", Locale.getDefault())
                        .format(cal.time)

                    val display = SimpleDateFormat("hh:mm a", Locale.getDefault())
                    binding.tvTime.text = display.format(cal.time)
                    binding.tvTime.setTextColor(getColor(R.color.text_primary))
                },
                cal.get(Calendar.HOUR_OF_DAY),
                cal.get(Calendar.MINUTE),
                false
            ).show()
        }
    }

    // ───────── CHIPS ─────────

    private fun setupSeatChips() {
        seatChips.forEachIndexed { index, chip ->
            chip.setOnClickListener {
                selectedSeats = index + 1
                updateChipSelection(seatChips, index)
            }
        }
    }

    private fun setupVehicleChips() {
        val vehicles = listOf("Car", "SUV", "Van", "Auto")

        vehicleChips.forEachIndexed { index, chip ->
            chip.setOnClickListener {
                selectedVehicle = vehicles[index]
                updateChipSelection(vehicleChips, index)
            }
        }
    }

    private fun updateChipSelection(chips: List<TextView>, selectedIndex: Int) {
        chips.forEachIndexed { i, chip ->
            val selected = i == selectedIndex
            chip.isSelected = selected
            chip.setTextColor(
                if (selected) Color.WHITE else getColor(R.color.text_primary)
            )
        }
    }

    // ───────── SUBMIT ─────────

    private fun setupSubmitButton() {
        binding.btnPublish.setOnClickListener {

            val price = binding.etPrice.text.toString().trim()
            val notes = binding.etNotes.text.toString().trim()

            val error = when {
                pickupAddress.isEmpty() -> R.string.validate_pickup
                dropAddress.isEmpty() -> R.string.validate_drop
                selectedDate.isEmpty() -> R.string.validate_date
                selectedTime.isEmpty() -> R.string.validate_time
                selectedSeats == 0 -> R.string.validate_seats
                price.isEmpty() -> R.string.validate_price
                selectedVehicle.isEmpty() -> R.string.validate_vehicle
                else -> null
            }

            if (error != null) {
                Toast.makeText(this, error, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val request = OfferRideRequest(
                userId = "",
                pickupAddress = pickupAddress,
                pickupLat = pickupLat,
                pickupLng = pickupLng,
                dropAddress = dropAddress,
                dropLat = dropLat,
                dropLng = dropLng,
                date = selectedDate,
                time = selectedTime,
                availableSeats = selectedSeats,
                pricePerSeat = price.toDoubleOrNull() ?: 0.0,
                vehicleType = selectedVehicle,
                notes = notes
            )

            // ✅ STABLE VERSION (no Part 7 screen)
            Toast.makeText(this, R.string.toast_offer_success, Toast.LENGTH_LONG).show()

            android.util.Log.d("OfferRide", "Request: $request")
        }
    }
}