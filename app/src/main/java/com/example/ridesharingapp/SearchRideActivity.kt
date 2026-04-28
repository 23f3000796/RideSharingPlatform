package com.example.ridesharingapp

import android.app.DatePickerDialog
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.ridesharingapp.databinding.ActivitySearchRideBinding
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class SearchRideActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySearchRideBinding

    private var selectedSeats = 1
    private var selectedDate = ""
    private var activeVehicleFilter = "All"
    private var sortAscending = true

    private lateinit var seatChips: List<TextView>
    private lateinit var filterChips: List<TextView>

    // ─────────────────── Dummy Data (structured like API response) ───────────────────

    private val allRides = listOf(
        RideResult("r001", "Arjun Mehta", "Car", "Koramangala, Bangalore", 12.9352, 77.6245, "Whitefield, Bangalore", 12.9698, 77.7500, "25 Apr", "08:30 AM", 120, 3, Color.parseColor("#2563EB")),
        RideResult("r002", "Priya Sharma", "SUV", "Indiranagar, Bangalore", 12.9784, 77.6408, "Electronic City, Bangalore", 12.8390, 77.6780, "25 Apr", "09:00 AM", 180, 2, Color.parseColor("#7C3AED")),
        RideResult("r003", "Rahul Verma", "Auto", "MG Road, Bangalore", 12.9758, 77.6068, "Marathahalli, Bangalore", 12.9591, 77.6974, "25 Apr", "09:15 AM", 60, 1, Color.parseColor("#10B981")),
        RideResult("r004", "Sneha Patil", "Car", "HSR Layout, Bangalore", 12.9116, 77.6474, "Manyata Tech Park, Bangalore", 13.0474, 77.6219, "25 Apr", "10:00 AM", 150, 4, Color.parseColor("#F59E0B")),
        RideResult("r005", "Vikram Singh", "Van", "Jayanagar, Bangalore", 12.9299, 77.5838, "ITPL, Bangalore", 12.9857, 77.7315, "26 Apr", "07:45 AM", 100, 6, Color.parseColor("#EF4444")),
        RideResult("r006", "Anita Desai", "SUV", "BTM Layout, Bangalore", 12.9166, 77.6101, "Hebbal, Bangalore", 13.0358, 77.5970, "26 Apr", "08:00 AM", 200, 3, Color.parseColor("#EC4899")),
        RideResult("r007", "Karthik Nair", "Car", "Bellandur, Bangalore", 12.9260, 77.6762, "KR Puram, Bangalore", 13.0098, 77.6960, "26 Apr", "11:30 AM", 90, 2, Color.parseColor("#0EA5E9"))
    )

    // ─────────────────── Lifecycle ───────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySearchRideBinding.inflate(layoutInflater)
        setContentView(binding.root)

        seatChips = listOf(
            binding.chipSearchSeat1, binding.chipSearchSeat2,
            binding.chipSearchSeat3, binding.chipSearchSeat4
        )

        filterChips = listOf(
            binding.chipFilterAll, binding.chipFilterCar,
            binding.chipFilterSuv, binding.chipFilterVan, binding.chipFilterAuto
        )

        setupBackButton()
        setupDatePicker()
        setupSeatChips()
        setupSearchButton()
        setupFilterChips()
        setupSortToggle()

        // Default seat = 1
        selectSeatChip(0)
        // Default filter = All
        selectFilterChip(0)
    }

    // ─────────────────── Back ───────────────────

    private fun setupBackButton() {
        binding.btnBack.setOnClickListener { finish() }
    }

    // ─────────────────── Date Picker ───────────────────

    private fun setupDatePicker() {
        binding.layoutSearchDate.setOnClickListener {
            val cal = Calendar.getInstance()
            val picker = DatePickerDialog(
                this,
                { _, year, month, day ->
                    cal.set(year, month, day)
                    val display = SimpleDateFormat("EEE, dd MMM", Locale.getDefault())
                    selectedDate = SimpleDateFormat("dd MMM", Locale.getDefault()).format(cal.time)
                    binding.tvSearchDate.text = display.format(cal.time)
                    binding.tvSearchDate.setTextColor(getColor(R.color.text_primary))
                },
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH)
            )
            picker.datePicker.minDate = System.currentTimeMillis() - 1000
            picker.show()
        }
    }

    // ─────────────────── Seat Chips ───────────────────

    private fun setupSeatChips() {
        seatChips.forEachIndexed { index, chip ->
            chip.setOnClickListener {
                selectedSeats = index + 1
                selectSeatChip(index)
            }
        }
    }

    private fun selectSeatChip(index: Int) {
        seatChips.forEachIndexed { i, chip ->
            val selected = i == index
            chip.isSelected = selected
            chip.setTextColor(if (selected) Color.WHITE else getColor(R.color.text_primary))
        }
    }

    // ─────────────────── Search ───────────────────

    private fun setupSearchButton() {
        binding.btnSearch.setOnClickListener {
            val from = binding.etFrom.text.toString().trim()
            val to = binding.etTo.text.toString().trim()

            if (from.isEmpty() || to.isEmpty()) {
                Toast.makeText(this, R.string.toast_search_fill, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Show filters + results
            binding.layoutFilters.visibility = View.VISIBLE
            binding.layoutResults.visibility = View.VISIBLE

            // Reset filter to All
            activeVehicleFilter = "All"
            selectFilterChip(0)

            renderResults()
        }
    }

    // ─────────────────── Filter Chips ───────────────────

    private fun setupFilterChips() {
        val filterNames = listOf("All", "Car", "SUV", "Van", "Auto")
        filterChips.forEachIndexed { index, chip ->
            chip.setOnClickListener {
                activeVehicleFilter = filterNames[index]
                selectFilterChip(index)
                renderResults()
            }
        }
    }

    private fun selectFilterChip(index: Int) {
        filterChips.forEachIndexed { i, chip ->
            val selected = i == index
            chip.isSelected = selected
            chip.setTextColor(if (selected) Color.WHITE else getColor(R.color.text_primary))
        }
    }

    // ─────────────────── Sort Toggle ───────────────────

    private fun setupSortToggle() {
        binding.tvSortLabel.setOnClickListener {
            sortAscending = !sortAscending
            binding.tvSortLabel.text = if (sortAscending)
                getString(R.string.sort_price_low) else getString(R.string.sort_price_high)
            renderResults()
        }
    }

    // ─────────────────── Render Results ───────────────────

    private fun renderResults() {
        val container = binding.layoutResults
        container.removeAllViews()

        // Filter
        var filtered = if (activeVehicleFilter == "All") {
            allRides
        } else {
            allRides.filter { it.vehicleType == activeVehicleFilter }
        }

        // Filter by seat availability
        filtered = filtered.filter { it.seatsLeft >= selectedSeats }

        // Sort
        filtered = if (sortAscending) {
            filtered.sortedBy { it.pricePerSeat }
        } else {
            filtered.sortedByDescending { it.pricePerSeat }
        }

        if (filtered.isEmpty()) {
            binding.tvNoResults.visibility = View.VISIBLE
            container.visibility = View.GONE
            return
        }

        binding.tvNoResults.visibility = View.GONE
        container.visibility = View.VISIBLE

        val inflater = LayoutInflater.from(this)

        for (ride in filtered) {
            val card = inflater.inflate(R.layout.item_ride_result, container, false)

            card.findViewById<TextView>(R.id.tvDriverName).text = ride.driverName
            card.findViewById<TextView>(R.id.tvVehicleType).text = ride.vehicleType
            card.findViewById<TextView>(R.id.tvPickup).text = ride.pickupAddress
            card.findViewById<TextView>(R.id.tvDrop).text = ride.dropAddress
            card.findViewById<TextView>(R.id.tvDateTime).text = "${ride.date} · ${ride.time}"
            card.findViewById<TextView>(R.id.tvPrice).text = "₹${ride.pricePerSeat}"
            card.findViewById<TextView>(R.id.tvSeatsLeft).text = "${ride.seatsLeft} ${getString(R.string.label_seats_left)}"

            // Driver avatar circle with color
            val avatar = card.findViewById<View>(R.id.viewAvatar)
            val circle = GradientDrawable()
            circle.shape = GradientDrawable.OVAL
            circle.setColor(ride.avatarColor)
            avatar.background = circle

            // Book button
            card.findViewById<Button>(R.id.btnBook).setOnClickListener {
                Toast.makeText(
                    this,
                    R.string.toast_booking_requested,
                    Toast.LENGTH_SHORT
                ).show()
            }

            container.addView(card)
        }
    }
}