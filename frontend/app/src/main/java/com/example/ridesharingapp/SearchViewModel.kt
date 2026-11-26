package com.example.ridesharingapp

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ridesharingapp.RideSharingRepository.SearchSort
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SearchViewModel : ViewModel() {

    data class SearchState(
        val hasSearched: Boolean = false,
        val totalFromServer: Int = 0,
        val radiusKm: Double = DEFAULT_RADIUS_KM,
    )

    companion object {
        const val DEFAULT_RADIUS_KM = 2.0
        const val WIDE_RADIUS_KM = 5.0
    }

    private val _searchResults = MutableStateFlow<List<RideResult>>(emptyList())
    val searchResults: StateFlow<List<RideResult>> = _searchResults.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    private val _state = MutableStateFlow(SearchState())
    val state: StateFlow<SearchState> = _state.asStateFlow()

    private var currentFilter = "All"
    private var currentGenderFilter = "Any"
    var sort: SearchSort = SearchSort.BEST
        private set

    private var lastTrip: PassengerTrip? = null
    private var lastFromIso: String? = null
    private var lastToIso: String? = null
    private var lastApiResults: List<RideResult> = emptyList()
    private var searchJob: Job? = null

    fun searchRides(trip: PassengerTrip, fromIso: String, toIso: String?, radiusKm: Double = _state.value.radiusKm) {
        lastTrip = trip
        lastFromIso = fromIso
        lastToIso = toIso
        searchJob?.cancel() // only the latest search wins
        searchJob = viewModelScope.launch {
            _isLoading.value = true
            _lastError.value = null
            RideSharingRepository.searchRides(trip, fromIso, toIso, radiusKm, sort).fold(
                onSuccess = { list ->
                    lastApiResults = list
                    _state.value = SearchState(hasSearched = true, totalFromServer = list.size, radiusKm = radiusKm)
                    applyFilters()
                },
                onFailure = { e ->
                    _lastError.value = e.message ?: "Search failed"
                    lastApiResults = emptyList()
                    _state.value = _state.value.copy(hasSearched = true, totalFromServer = 0)
                    _searchResults.value = emptyList()
                },
            )
            _isLoading.value = false
        }
    }

    /** Re-run the last search with a larger pickup/drop radius. */
    fun widenSearch() {
        val trip = lastTrip ?: return
        val from = lastFromIso ?: return
        searchRides(trip, from, lastToIso, WIDE_RADIUS_KM)
    }

    fun refresh() {
        val trip = lastTrip ?: return
        val from = lastFromIso ?: return
        searchRides(trip, from, lastToIso)
    }

    fun consumeError() {
        _lastError.value = null
    }

    fun updateFilter(vehicleType: String) {
        currentFilter = vehicleType
        applyFilters()
    }

    fun updateGenderFilter(gender: String) {
        currentGenderFilter = gender
        applyFilters()
    }

    /** Cycles Best match → Lowest fare → Earliest departure. */
    fun cycleSort(): SearchSort {
        sort = when (sort) {
            SearchSort.BEST -> SearchSort.PRICE
            SearchSort.PRICE -> SearchSort.TIME
            SearchSort.TIME -> SearchSort.BEST
        }
        applyFilters()
        return sort
    }

    private fun applyFilters() {
        var filtered = lastApiResults

        if (currentFilter != "All") {
            filtered = filtered.filter { it.vehicleType.equals(currentFilter, ignoreCase = true) }
        }
        if (currentGenderFilter != "Any") {
            filtered = filtered.filter { it.driverGender.equals(currentGenderFilter, ignoreCase = true) }
        }

        // The server already ranks by best match; re-sort locally so switching sort is instant.
        filtered = when (sort) {
            SearchSort.BEST -> filtered
            SearchSort.PRICE -> filtered.sortedWith(compareBy({ it.estimatedFare }, { it.departureMillis }))
            SearchSort.TIME -> filtered.sortedBy { it.departureMillis }
        }
        _searchResults.value = filtered
    }
}
