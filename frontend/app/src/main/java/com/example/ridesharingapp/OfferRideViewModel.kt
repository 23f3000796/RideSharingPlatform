package com.example.ridesharingapp

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ridesharingapp.network.ModelMapper
import com.example.ridesharingapp.network.dto.CreateRideRequestDto
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class OfferRideViewModel : ViewModel() {

    private val _offerSuccess = MutableSharedFlow<RideResult>(extraBufferCapacity = 1)
    val offerSuccess: SharedFlow<RideResult> = _offerSuccess.asSharedFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _isPublishing = MutableStateFlow(false)
    val isPublishing: StateFlow<Boolean> = _isPublishing.asStateFlow()

    fun publishRide(
        pickup: String,
        pickupLat: Double,
        pickupLng: Double,
        drop: String,
        dropLat: Double,
        dropLng: Double,
        date: String,
        time: String,
        seats: Int,
        price: Int,
        vehicle: String,
        vehicleNumber: String?,
    ) {
        if (_isPublishing.value) return // no double submits
        viewModelScope.launch {
            _error.value = null
            val departureIso = ModelMapper.combineLocalDateTimeToIso(date, time)
            if (departureIso == null) {
                _error.value = "Invalid date or time"
                return@launch
            }
            _isPublishing.value = true
            val result = RideSharingRepository.createRide(
                CreateRideRequestDto(
                    pickupAddress = pickup,
                    pickupLat = pickupLat,
                    pickupLng = pickupLng,
                    dropAddress = drop,
                    dropLat = dropLat,
                    dropLng = dropLng,
                    departureTime = departureIso,
                    totalSeats = seats,
                    pricePerSeat = price.coerceAtLeast(0),
                    vehicleType = vehicle,
                    vehicleNumber = vehicleNumber,
                ),
            )
            _isPublishing.value = false
            result.fold(
                onSuccess = { ride -> _offerSuccess.emit(ride) },
                onFailure = { e -> _error.value = e.message ?: "Could not publish ride" },
            )
        }
    }

    fun clearError() {
        _error.value = null
    }
}
