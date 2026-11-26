package com.example.ridesharingapp

import android.app.DatePickerDialog
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.ridesharingapp.RideSharingRepository.SearchSort
import com.example.ridesharingapp.databinding.ActivitySearchRideBinding
import com.example.ridesharingapp.network.ModelMapper
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class SearchRideActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_RIDE = "EXTRA_RIDE"
        const val EXTRA_TRIP = "EXTRA_TRIP"
    }

    private lateinit var binding: ActivitySearchRideBinding
    private val viewModel: SearchViewModel by viewModels()
    private lateinit var adapter: RideAdapter

    private var selectedSeats = 1
    /** Null = "any upcoming date". */
    private var selectedDay: Triple<Int, Int, Int>? = null

    private var fromLat: Double? = null
    private var fromLng: Double? = null
    private var toLat: Double? = null
    private var toLng: Double? = null
    private var fromAddress = ""
    private var toAddress = ""

    private lateinit var seatChips: List<TextView>
    private lateinit var filterChips: List<TextView>
    private lateinit var genderChips: List<TextView>

    private lateinit var fromMapLauncher: ActivityResultLauncher<Intent>
    private lateinit var toMapLauncher: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySearchRideBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupMapLaunchers()
        setupRecyclerView()
        setupChips()
        setupUIListeners()
        observeViewModel()

        selectSeatChip(0)
        selectFilterChip(0)
        selectGenderChip(0)
        binding.tvSearchDate.text = "Any day"
        binding.tvSortLabel.text = sortLabel(viewModel.sort)
    }

    override fun onResume() {
        super.onResume()
        // Seats / booking status may have changed after visiting a ride.
        if (viewModel.state.value.hasSearched) viewModel.refresh()
    }

    private fun setupMapLaunchers() {
        fromMapLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val data = result.data ?: return@registerForActivityResult
            if (result.resultCode != RESULT_OK) return@registerForActivityResult
            fromLat = data.getDoubleExtra(MapPickerActivity.RESULT_LATITUDE, 0.0)
            fromLng = data.getDoubleExtra(MapPickerActivity.RESULT_LONGITUDE, 0.0)
            fromAddress = data.getStringExtra(MapPickerActivity.RESULT_ADDRESS).orEmpty()
            binding.etFrom.setText(fromAddress)
            if (toLat != null) performSearch()
        }

        toMapLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val data = result.data ?: return@registerForActivityResult
            if (result.resultCode != RESULT_OK) return@registerForActivityResult
            toLat = data.getDoubleExtra(MapPickerActivity.RESULT_LATITUDE, 0.0)
            toLng = data.getDoubleExtra(MapPickerActivity.RESULT_LONGITUDE, 0.0)
            toAddress = data.getStringExtra(MapPickerActivity.RESULT_ADDRESS).orEmpty()
            binding.etTo.setText(toAddress)
            if (fromLat != null) performSearch()
        }
    }

    private fun currentTrip(): PassengerTrip? {
        val fLat = fromLat ?: return null
        val fLng = fromLng ?: return null
        val tLat = toLat ?: return null
        val tLng = toLng ?: return null
        return PassengerTrip(fromAddress, fLat, fLng, toAddress, tLat, tLng, selectedSeats)
    }

    private fun setupRecyclerView() {
        adapter = RideAdapter(showMatch = true) { ride ->
            startActivity(
                Intent(this, RideDetailsActivity::class.java)
                    .putExtra(EXTRA_RIDE, ride)
                    .putExtra(EXTRA_TRIP, currentTrip()),
            )
        }
        binding.rvResults.layoutManager = LinearLayoutManager(this)
        binding.rvResults.adapter = adapter
    }

    private fun setupChips() {
        seatChips = listOf(
            binding.chipSearchSeat1, binding.chipSearchSeat2,
            binding.chipSearchSeat3, binding.chipSearchSeat4,
        )
        filterChips = listOf(
            binding.chipFilterAll, binding.chipFilterScooter,
            binding.chipFilterBike, binding.chipFilterAuto,
            binding.chipFilterHatchback, binding.chipFilterSedan,
            binding.chipFilterSuv, binding.chipFilterCuv,
        )
        genderChips = listOf(binding.chipGenderAny, binding.chipGenderMale, binding.chipGenderFemale)

        seatChips.forEachIndexed { index, chip ->
            chip.setOnClickListener {
                selectedSeats = index + 1
                selectSeatChip(index)
                if (currentTrip() != null) performSearch()
            }
        }

        val filterNames = listOf("All", "Scooter", "Bike", "Auto", "Hatchback", "Sedan", "SUV", "CUV")
        filterChips.forEachIndexed { index, chip ->
            chip.setOnClickListener {
                viewModel.updateFilter(filterNames[index])
                selectFilterChip(index)
            }
        }

        val genderOptions = listOf("Any", "Male", "Female")
        genderChips.forEachIndexed { index, chip ->
            chip.setOnClickListener {
                viewModel.updateGenderFilter(genderOptions[index])
                selectGenderChip(index)
            }
        }
    }

    private fun setupUIListeners() {
        binding.btnBack.setOnClickListener { finish() }

        binding.etFrom.setOnClickListener {
            fromMapLauncher.launch(
                Intent(this, MapPickerActivity::class.java)
                    .putExtra(MapPickerActivity.EXTRA_TITLE, "Where should they pick you up?")
                    .putExtra(MapPickerActivity.EXTRA_AUTO_FETCH, fromLat == null)
                    .apply { fromLat?.let { putExtra(MapPickerActivity.EXTRA_START_LAT, it); putExtra(MapPickerActivity.EXTRA_START_LNG, fromLng ?: 0.0) } },
            )
        }

        binding.etTo.setOnClickListener {
            toMapLauncher.launch(
                Intent(this, MapPickerActivity::class.java)
                    .putExtra(MapPickerActivity.EXTRA_TITLE, "Where are you going?")
                    .putExtra(MapPickerActivity.EXTRA_AUTO_FETCH, false)
                    .apply {
                        val lat = toLat ?: fromLat
                        val lng = toLng ?: fromLng
                        if (lat != null && lng != null) {
                            putExtra(MapPickerActivity.EXTRA_START_LAT, lat)
                            putExtra(MapPickerActivity.EXTRA_START_LNG, lng)
                        }
                    },
            )
        }

        binding.layoutSearchDate.setOnClickListener { showDatePicker() }
        binding.layoutSearchDate.setOnLongClickListener {
            selectedDay = null
            binding.tvSearchDate.text = "Any day"
            if (currentTrip() != null) performSearch()
            true
        }
        binding.btnSearch.setOnClickListener { performSearch() }

        binding.tvSortLabel.setOnClickListener {
            binding.tvSortLabel.text = sortLabel(viewModel.cycleSort())
        }

        binding.tvNoResults.setOnClickListener {
            if (viewModel.state.value.radiusKm < SearchViewModel.WIDE_RADIUS_KM) viewModel.widenSearch()
        }
    }

    private fun sortLabel(sort: SearchSort) = when (sort) {
        SearchSort.BEST -> "Sort: Best match"
        SearchSort.PRICE -> "Sort: Lowest fare"
        SearchSort.TIME -> "Sort: Earliest"
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    combine(viewModel.searchResults, viewModel.state, viewModel.isLoading) { rides, state, loading ->
                        Triple(rides, state, loading)
                    }.collect { (rides, state, loading) ->
                        adapter.submitList(rides)
                        val showEmpty = state.hasSearched && !loading && rides.isEmpty()
                        binding.rvResults.visibility = if (rides.isEmpty()) View.GONE else View.VISIBLE
                        binding.tvNoResults.visibility = if (showEmpty) View.VISIBLE else View.GONE
                        if (showEmpty) {
                            binding.tvNoResults.text = when {
                                state.totalFromServer > 0 ->
                                    "No rides match these filters. Try \"All\" vehicles or any gender."
                                state.radiusKm < SearchViewModel.WIDE_RADIUS_KM ->
                                    "No driver passes within ${state.radiusKm.toInt()} km of your pickup and drop.\nTap here to search within ${SearchViewModel.WIDE_RADIUS_KM.toInt()} km."
                                else ->
                                    "No rides found on this route yet. Try another day, or offer the ride yourself!"
                            }
                        }
                    }
                }
                launch {
                    viewModel.isLoading.collect { loading ->
                        binding.btnSearch.isEnabled = !loading
                        binding.btnSearch.text = if (loading) "Finding rides on your route…" else getString(R.string.btn_search)
                    }
                }
                launch {
                    viewModel.lastError.collect { msg ->
                        if (!msg.isNullOrBlank()) {
                            Toast.makeText(this@SearchRideActivity, msg, Toast.LENGTH_LONG).show()
                            viewModel.consumeError()
                        }
                    }
                }
            }
        }
    }

    private fun performSearch() {
        val trip = currentTrip()
        if (trip == null || fromAddress.isBlank() || toAddress.isBlank()) {
            Toast.makeText(this, R.string.toast_search_fill, Toast.LENGTH_SHORT).show()
            return
        }

        val day = selectedDay
        val fromIso: String
        val toIso: String?
        if (day == null) {
            fromIso = ModelMapper.nowIso()
            toIso = null
        } else {
            fromIso = ModelMapper.localMidnightIsoForDate(day.first, day.second, day.third)
            toIso = ModelMapper.localEndOfDayIsoForDate(day.first, day.second, day.third)
        }

        binding.layoutFilters.visibility = View.VISIBLE
        viewModel.searchRides(trip, fromIso, toIso, SearchViewModel.DEFAULT_RADIUS_KM)
    }

    private fun showDatePicker() {
        val cal = Calendar.getInstance()
        selectedDay?.let { cal.set(it.first, it.second, it.third) }
        DatePickerDialog(this, { _, year, month, day ->
            selectedDay = Triple(year, month, day)
            cal.set(year, month, day)
            binding.tvSearchDate.text = SimpleDateFormat("EEE, dd MMM", Locale.getDefault()).format(cal.time)
            binding.tvSearchDate.setTextColor(getColor(R.color.text_primary))
            if (currentTrip() != null) performSearch()
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).apply {
            datePicker.minDate = System.currentTimeMillis() - 1000
            setButton(DatePickerDialog.BUTTON_NEUTRAL, "Any day") { _, _ ->
                selectedDay = null
                binding.tvSearchDate.text = "Any day"
                if (currentTrip() != null) performSearch()
            }
        }.show()
    }

    private fun selectSeatChip(index: Int) = highlight(seatChips, index)
    private fun selectFilterChip(index: Int) = highlight(filterChips, index)
    private fun selectGenderChip(index: Int) = highlight(genderChips, index)

    private fun highlight(chips: List<TextView>, index: Int) {
        chips.forEachIndexed { i, chip ->
            val selected = i == index
            chip.isSelected = selected
            chip.setTextColor(if (selected) Color.WHITE else getColor(R.color.text_primary))
        }
    }
}
