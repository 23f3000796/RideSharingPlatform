package com.example.ridesharingapp

/**
 * Backend-ready data classes for the RideSharing app.
 * Structured to match typical REST API JSON payloads.
 * Ready for Retrofit/Gson serialization — just add @SerializedName if needed.
 */

// ───────────────────────── Offer Ride ─────────────────────────

data class OfferRideRequest(
    val userId: String,
    val pickupAddress: String,
    val pickupLat: Double,
    val pickupLng: Double,
    val dropAddress: String,
    val dropLat: Double,
    val dropLng: Double,
    val date: String,            // "yyyy-MM-dd"
    val time: String,            // "HH:mm"
    val availableSeats: Int,
    val pricePerSeat: Double,
    val vehicleType: String,     // "Car", "SUV", "Van", "Auto"
    val notes: String = ""
)

// ───────────────────────── Search / Ride Results ─────────────────────────

data class RideResult(
    val rideId: String,
    val driverName: String,
    val vehicleType: String,
    val pickupAddress: String,
    val pickupLat: Double,
    val pickupLng: Double,
    val dropAddress: String,
    val dropLat: Double,
    val dropLng: Double,
    val date: String,            // "dd MMM" for display, or "yyyy-MM-dd" from API
    val time: String,            // "hh:mm a"
    val pricePerSeat: Int,
    val seatsLeft: Int,
    val avatarColor: Int         // Local-only — color int for driver avatar circle
)

data class SearchRideRequest(
    val fromAddress: String,
    val toAddress: String,
    val date: String,            // "yyyy-MM-dd"
    val seatsNeeded: Int
)

data class SearchRideResponse(
    val rides: List<RideResult>,
    val totalCount: Int
)

// ───────────────────────── Booking ─────────────────────────

data class BookingRequest(
    val userId: String,
    val rideId: String,
    val seatsRequested: Int
)

data class BookingResponse(
    val bookingId: String,
    val status: String,          // "confirmed", "pending", "rejected"
    val message: String
)