package com.example.ridesharingapp

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * UI models for the campus ride-sharing app (mapped from backend DTOs in ModelMapper).
 * Status strings are title-case: "Scheduled", "Ongoing", "Completed", "Cancelled" for rides
 * and "Pending", "Accepted", "Rejected", "Completed", "Cancelled" for bookings.
 */

@Parcelize
data class UserProfile(
    val userId: String,
    val name: String,
    val collegeId: String,
    val rollNumber: String,
    val collegeEmail: String,
    val isVerified: Boolean = false,
    val idCardUrl: String? = null,
    val rating: Float = 0f,
    val rewardPoints: Int = 0,
    val ridesCompleted: Int = 0,
    /** Display label, e.g. "Explorer • Level 2". */
    val level: String = "Newcomer",
    val levelNumber: Int = 1,
    val levelTitle: String = "Newcomer",
    val pointsToNextLevel: Int = 0,
    val nextLevelAt: Int? = null,
    val gender: String = "Not specified",
    val emergencyContact: String? = null,
    val emergencyContactName: String? = null,
    val emergencyContactPhone: String? = null,
    val vehicleNumber: String? = null,
    val department: String? = null,
    val course: String? = null,
    val year: Int? = null,
    val bloodGroup: String? = null,
    val ridesOffered: Int = 0,
    val ridesShared: Int = 0,
    val upcomingAsDriver: Int = 0,
    val upcomingAsPassenger: Int = 0,
    val reviewsReceived: Int = 0,
) : Parcelable

@Parcelize
data class RouteMatchInfo(
    /** "EXACT" (same start & end) or "ON_ROUTE" (joins/leaves part-way). */
    val matchType: String,
    val pickupDistanceKm: Double,
    val dropDistanceKm: Double,
    val detourKm: Double,
    val routeKm: Double,
    val passengerTripKm: Double,
    val pickupFraction: Double,
    val dropFraction: Double,
) : Parcelable {
    val isExact: Boolean get() = matchType.equals("EXACT", ignoreCase = true)
}

/** The passenger's own requested trip (carried from search to details/booking). */
@Parcelize
data class PassengerTrip(
    val pickupAddress: String,
    val pickupLat: Double,
    val pickupLng: Double,
    val dropAddress: String,
    val dropLat: Double,
    val dropLng: Double,
    val seats: Int = 1,
) : Parcelable

@Parcelize
data class RideResult(
    val rideId: String,
    val driverId: String = "",
    val driverName: String,
    val driverRating: Float,
    val driverGender: String,
    val driverVerified: Boolean = false,
    val driverLevel: Int = 1,
    val vehicleType: String,
    val vehicleNumber: String = "",
    val pickupAddress: String,
    val pickupLat: Double,
    val pickupLng: Double,
    val dropAddress: String,
    val dropLat: Double,
    val dropLng: Double,
    val date: String,
    val time: String,
    val departureIso: String? = null,
    val departureMillis: Long = 0L,
    val pricePerSeat: Int,
    /** Fare for the passenger's own (possibly partial) trip; equals pricePerSeat when not searched. */
    val estimatedFare: Int = pricePerSeat,
    val seatsLeft: Int,
    val totalSeats: Int = seatsLeft,
    val avatarColor: Int,
    var status: String = "Scheduled",
    val routeKm: Double? = null,
    val match: RouteMatchInfo? = null,
    /** Viewer's booking status on this ride ("Pending"/"Accepted"/...), if any. */
    val myBookingStatus: String? = null,
    val myBookingId: String? = null,
    val myBookingSeats: Int = 0,
    val pendingRequests: Int = 0,
    val isDriver: Boolean = false,
    val passengerNames: List<String> = emptyList(),
) : Parcelable

@Parcelize
data class BookingRequest(
    val bookingId: String,
    val rideId: String,
    val driverId: String,
    val passengerId: String,
    val passengerName: String,
    val passengerRollNo: String,
    val passengerRating: Float,
    val pickupAddress: String,
    val dropAddress: String,
    val rideDate: String,
    val rideTime: String,
    val seatsRequested: Int,
    var status: String = "Pending",
    val timestamp: Long = System.currentTimeMillis(),
    /** True when this row is shown on the driver tab (incoming requests for rides you host). */
    val viewAsDriver: Boolean = false,
    /** Preformatted line for the summary row (driver name vs passenger name). */
    val counterpartyDetail: String? = null,
    val rideSeatsLeft: Int? = null,
    val rideStatus: String = "Scheduled",
    val driverName: String = "",
    val vehicleLabel: String = "",
    val pricePerSeat: Int = 0,
    val reviewed: Boolean = false,
) : Parcelable {
    /** Who this user would rate for this booking. */
    val reviewTargetId: String get() = if (viewAsDriver) passengerId else driverId
    val reviewTargetName: String get() = if (viewAsDriver) passengerName else driverName
}

data class LeaderboardUiRow(
    val rank: Int,
    val userId: String,
    val name: String,
    val points: Int,
    val levelTitle: String,
    val level: Int,
    val ridesCompleted: Int,
    val rating: Float,
    val department: String?,
)
