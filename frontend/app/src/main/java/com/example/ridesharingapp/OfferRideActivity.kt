package com.example.ridesharingapp

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.graphics.Color
import android.location.Location
import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.ridesharingapp.databinding.ActivityOfferRideBinding
import com.example.ridesharingapp.network.ModelMapper
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

class OfferRideActivity : AppCompatActivity() {

    companion object {
        private val VEHICLES = listOf("Scooter", "Bike", "Auto", "Hatchback", "Sedan", "SUV", "CUV")

        /** Passenger seats a vehicle can realistically carry (driver excluded). */
        fun seatCapacity(vehicle: String) = when (vehicle) {
            "Scooter", "Bike" -> 1
            "Auto" -> 3
            "Hatchback", "Sedan" -> 4
            "SUV", "CUV" -> 6
            else -> 6
        }

        /** Approx. running cost per km (fuel + wear) used for fair cost sharing. */
        fun costPerKm(vehicle: String) = when (vehicle) {
            "Scooter", "Bike" -> 3.0
            "Auto" -> 6.0
            "Hatchback" -> 7.0
            "Sedan" -> 8.0
            "SUV", "CUV" -> 10.0
            else -> 7.0
        }

        private const val BASE_FARE = 10.0
        private const val MIN_FARE = 10
        private const val MIN_LEAD_MINUTES = 10
    }

    private lateinit var binding: ActivityOfferRideBinding
    private val viewModel: OfferRideViewModel by viewModels()

    private var selectedSeats = 1
    private var selectedVehicle = ""
    private var selectedDate = ""
    private var selectedTime = ""

    private var pickupLat: Double? = null
    private var pickupLng: Double? = null
    private var dropLat: Double? = null
    private var dropLng: Double? = null
    private var pickupAddress = ""
    private var dropAddress = ""
    private var tripKm = 0.0

    private var suggestedPrice = 0
    private var customPrice: Int? = null
    private val price get() = customPrice ?: suggestedPrice

    private lateinit var seatChips: List<TextView>
    private lateinit var vehicleChips: List<TextView>

    private lateinit var pickupMapLauncher: ActivityResultLauncher<Intent>
    private lateinit var dropMapLauncher: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOfferRideBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupMapLaunchers()
        setupUI()
        observeViewModel()
        selectSeatChip(0)
        binding.etVehicleNumber.setText(RideRepository.currentUser.vehicleNumber.orEmpty())
    }

    private fun setupUI() {
        seatChips = listOf(binding.chipSeat1, binding.chipSeat2, binding.chipSeat3, binding.chipSeat4, binding.chipSeat5, binding.chipSeat6)
        vehicleChips = listOf(binding.chipScooter, binding.chipBike, binding.chipAuto, binding.chipHatchback, binding.chipSedan, binding.chipSuv, binding.chipCuv)

        binding.btnBack.setOnClickListener { finish() }
        setupLocationPickers()
        binding.layoutDate.setOnClickListener { showDatePicker() }
        binding.layoutTime.setOnClickListener { showTimePicker() }
        seatChips.forEachIndexed { index, chip -> chip.setOnClickListener { selectSeatChip(index) } }
        vehicleChips.forEachIndexed { index, chip ->
            chip.setOnClickListener {
                selectedVehicle = VEHICLES[index]
                highlight(vehicleChips, index)
                applySeatCapacity()
                recalcFare()
            }
        }
        binding.cardPrice.setOnClickListener { editPrice() }
        binding.btnPublish.setOnClickListener { publish() }
    }

    private fun setupMapLaunchers() {
        pickupMapLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val data = result.data ?: return@registerForActivityResult
            if (result.resultCode != RESULT_OK) return@registerForActivityResult
            pickupLat = data.getDoubleExtra(MapPickerActivity.RESULT_LATITUDE, 0.0)
            pickupLng = data.getDoubleExtra(MapPickerActivity.RESULT_LONGITUDE, 0.0)
            pickupAddress = data.getStringExtra(MapPickerActivity.RESULT_ADDRESS).orEmpty()
            binding.tvPickup.text = pickupAddress
            binding.tvPickup.setTextColor(getColor(R.color.text_primary))
            recalcFare()
        }
        dropMapLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val data = result.data ?: return@registerForActivityResult
            if (result.resultCode != RESULT_OK) return@registerForActivityResult
            dropLat = data.getDoubleExtra(MapPickerActivity.RESULT_LATITUDE, 0.0)
            dropLng = data.getDoubleExtra(MapPickerActivity.RESULT_LONGITUDE, 0.0)
            dropAddress = data.getStringExtra(MapPickerActivity.RESULT_ADDRESS).orEmpty()
            binding.tvDrop.text = dropAddress
            binding.tvDrop.setTextColor(getColor(R.color.text_primary))
            recalcFare()
        }
    }

    private fun setupLocationPickers() {
        binding.layoutPickup.setOnClickListener {
            pickupMapLauncher.launch(
                Intent(this, MapPickerActivity::class.java)
                    .putExtra(MapPickerActivity.EXTRA_TITLE, "Where does your trip start?")
                    .putExtra(MapPickerActivity.EXTRA_AUTO_FETCH, pickupLat == null)
                    .apply { pickupLat?.let { putExtra(MapPickerActivity.EXTRA_START_LAT, it); putExtra(MapPickerActivity.EXTRA_START_LNG, pickupLng ?: 0.0) } },
            )
        }
        binding.layoutDrop.setOnClickListener {
            dropMapLauncher.launch(
                Intent(this, MapPickerActivity::class.java)
                    .putExtra(MapPickerActivity.EXTRA_TITLE, "Where are you heading?")
                    .putExtra(MapPickerActivity.EXTRA_AUTO_FETCH, false)
                    .apply {
                        val lat = dropLat ?: pickupLat
                        val lng = dropLng ?: pickupLng
                        if (lat != null && lng != null) {
                            putExtra(MapPickerActivity.EXTRA_START_LAT, lat)
                            putExtra(MapPickerActivity.EXTRA_START_LNG, lng)
                        }
                    },
            )
        }
    }

    // ---------------------------------------------------------------- seats

    private fun selectSeatChip(index: Int) {
        val cap = if (selectedVehicle.isEmpty()) 6 else seatCapacity(selectedVehicle)
        if (index + 1 > cap) {
            Toast.makeText(this, "A ${selectedVehicle.lowercase()} can carry at most $cap passenger(s)", Toast.LENGTH_SHORT).show()
            return
        }
        selectedSeats = index + 1
        highlight(seatChips, index)
        recalcFare()
    }

    private fun applySeatCapacity() {
        val cap = seatCapacity(selectedVehicle)
        seatChips.forEachIndexed { i, chip -> chip.alpha = if (i + 1 <= cap) 1f else 0.35f }
        if (selectedSeats > cap) selectSeatChip(cap - 1)
    }

    // ----------------------------------------------------------------- fare

    /**
     * Fair cost-sharing: the trip's running cost is split between the driver and
     * every passenger seat, so a fuller vehicle is cheaper for everyone.
     */
    private fun recalcFare() {
        val pLat = pickupLat; val pLng = pickupLng; val dLat = dropLat; val dLng = dropLng
        if (pLat == null || pLng == null || dLat == null || dLng == null || selectedVehicle.isEmpty()) {
            renderPrice()
            return
        }
        val dist = FloatArray(1)
        Location.distanceBetween(pLat, pLng, dLat, dLng, dist)
        // Straight-line distance × 1.3 ≈ road distance in a city
        tripKm = dist[0] / 1000.0 * 1.3
        val tripCost = BASE_FARE + tripKm * costPerKm(selectedVehicle)
        suggestedPrice = max(MIN_FARE, (tripCost / (selectedSeats + 1)).roundToInt())
        renderPrice()
    }

    private fun renderPrice() {
        if (suggestedPrice == 0 && customPrice == null) {
            binding.tvCalculatedPrice.text = "₹—"
            binding.tvPriceBreakdown.text = "Pick route and vehicle to estimate a fair cost-sharing price"
            return
        }
        binding.tvCalculatedPrice.text = if (price == 0) "Free" else "₹$price"
        binding.tvPriceBreakdown.text = if (customPrice != null) {
            "Custom price · suggested ₹$suggestedPrice"
        } else {
            String.format(Locale.getDefault(), "≈ %.1f km · cost split between you and %d passenger(s)", tripKm, selectedSeats)
        }
    }

    private fun editPrice() {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            hint = if (suggestedPrice > 0) "Suggested ₹$suggestedPrice" else "Price in ₹"
            setText(customPrice?.toString().orEmpty())
            setPadding(UiKit.dp(context, 20), UiKit.dp(context, 16), UiKit.dp(context, 20), UiKit.dp(context, 16))
        }
        AlertDialog.Builder(this)
            .setTitle("Price per seat")
            .setMessage("Charge only to share costs. Enter 0 to offer a free ride.")
            .setView(input)
            .setPositiveButton("Set") { _, _ ->
                val v = input.text.toString().trim().toIntOrNull()
                when {
                    v == null -> Toast.makeText(this, "Enter a number", Toast.LENGTH_SHORT).show()
                    v > 2000 -> Toast.makeText(this, "That's too high for a campus ride", Toast.LENGTH_SHORT).show()
                    else -> { customPrice = v; renderPrice() }
                }
            }
            .setNeutralButton("Use suggested") { _, _ -> customPrice = null; renderPrice() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ----------------------------------------------------------- date/time

    private fun showDatePicker() {
        val cal = Calendar.getInstance()
        DatePickerDialog(this, { _, year, month, day ->
            cal.set(year, month, day)
            selectedDate = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)
            binding.tvDate.text = SimpleDateFormat("EEE, dd MMM", Locale.getDefault()).format(cal.time)
            binding.tvDate.setTextColor(getColor(R.color.text_primary))
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).apply {
            datePicker.minDate = System.currentTimeMillis() - 1000
            datePicker.maxDate = System.currentTimeMillis() + 30L * 24 * 3600 * 1000
        }.show()
    }

    private fun showTimePicker() {
        val cal = Calendar.getInstance().apply { add(Calendar.MINUTE, 30) }
        TimePickerDialog(this, { _, h, m ->
            cal.set(Calendar.HOUR_OF_DAY, h)
            cal.set(Calendar.MINUTE, m)
            selectedTime = String.format(Locale.US, "%02d:%02d", h, m)
            binding.tvTime.text = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(cal.time)
            binding.tvTime.setTextColor(getColor(R.color.text_primary))
        }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), false).show()
    }

    // -------------------------------------------------------------- submit

    private fun publish() {
        val pLat = pickupLat; val pLng = pickupLng; val dLat = dropLat; val dLng = dropLng
        val problem = when {
            pLat == null || pLng == null || pickupAddress.isBlank() -> getString(R.string.validate_pickup)
            dLat == null || dLng == null || dropAddress.isBlank() -> getString(R.string.validate_drop)
            selectedDate.isEmpty() -> getString(R.string.validate_date)
            selectedTime.isEmpty() -> getString(R.string.validate_time)
            selectedVehicle.isEmpty() -> getString(R.string.validate_vehicle)
            else -> null
        }
        if (problem != null) {
            Toast.makeText(this, problem, Toast.LENGTH_SHORT).show()
            return
        }
        val departure = ModelMapper.combineLocalDateTimeToMillis(selectedDate, selectedTime)
        if (departure == null || departure < System.currentTimeMillis() + MIN_LEAD_MINUTES * 60_000L) {
            Toast.makeText(this, "Departure must be at least $MIN_LEAD_MINUTES minutes from now", Toast.LENGTH_LONG).show()
            return
        }
        val results = FloatArray(1)
        Location.distanceBetween(pLat!!, pLng!!, dLat!!, dLng!!, results)
        if (results[0] < 300f) {
            Toast.makeText(this, "Pickup and drop are too close together", Toast.LENGTH_SHORT).show()
            return
        }

        val vehicleNumber = binding.etVehicleNumber.text.toString().trim().uppercase(Locale.getDefault())
        val summary = "${UiKit.shortPlace(pickupAddress)} → ${UiKit.shortPlace(dropAddress)}\n" +
            "${binding.tvDate.text}, ${binding.tvTime.text}\n" +
            "$selectedSeats seat(s) · $selectedVehicle · ${if (price == 0) "Free" else "₹$price/seat"}"
        AlertDialog.Builder(this)
            .setTitle("Publish this ride?")
            .setMessage(summary)
            .setPositiveButton("Publish") { _, _ ->
                viewModel.publishRide(
                    pickupAddress, pLat, pLng,
                    dropAddress, dLat, dLng,
                    selectedDate, selectedTime,
                    selectedSeats, price,
                    selectedVehicle, vehicleNumber.ifBlank { null },
                )
            }
            .setNegativeButton("Edit", null)
            .show()
    }

    private fun highlight(chips: List<TextView>, selectedIndex: Int) {
        chips.forEachIndexed { i, chip ->
            val selected = i == selectedIndex
            chip.isSelected = selected
            if (selected) {
                chip.setBackgroundResource(R.drawable.bg_search_button)
                chip.setTextColor(Color.WHITE)
            } else {
                chip.setBackgroundResource(R.drawable.bg_seat_chip)
                chip.setTextColor(getColor(R.color.text_primary))
            }
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.offerSuccess.collect { ride ->
                        startActivity(Intent(this@OfferRideActivity, RideSummaryActivity::class.java).putExtra(SearchRideActivity.EXTRA_RIDE, ride))
                        finish()
                    }
                }
                launch {
                    viewModel.error.collect { msg ->
                        if (!msg.isNullOrBlank()) {
                            Toast.makeText(this@OfferRideActivity, msg, Toast.LENGTH_LONG).show()
                            viewModel.clearError()
                        }
                    }
                }
                launch {
                    viewModel.isPublishing.collect { busy ->
                        binding.btnPublish.isEnabled = !busy
                        binding.btnPublish.text = if (busy) "Publishing…" else getString(R.string.btn_offer_ride)
                    }
                }
            }
        }
    }
}
