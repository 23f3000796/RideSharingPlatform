package com.example.ridesharingapp.network.dto

import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName

// ---------------------------------------------------------------- users

data class UserStatsDto(
    @SerializedName("ridesOffered") val ridesOffered: Int? = null,
    @SerializedName("ridesShared") val ridesShared: Int? = null,
    @SerializedName("upcomingAsDriver") val upcomingAsDriver: Int? = null,
    @SerializedName("upcomingAsPassenger") val upcomingAsPassenger: Int? = null,
    @SerializedName("reviewsReceived") val reviewsReceived: Int? = null,
)

data class UserDto(
    @SerializedName("id") val id: String? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("rollNumber") val rollNumber: String? = null,
    @SerializedName("collegeEmail") val collegeEmail: String? = null,
    @SerializedName("isVerified") val isVerified: Boolean? = null,
    @SerializedName("rating") val rating: Float? = null,
    @SerializedName("rewardPoints") val rewardPoints: Int? = null,
    @SerializedName("ridesCompleted") val ridesCompleted: Int? = null,
    @SerializedName("level") val level: Int? = null,
    @SerializedName("levelTitle") val levelTitle: String? = null,
    @SerializedName("pointsToNextLevel") val pointsToNextLevel: Int? = null,
    @SerializedName("nextLevelAt") val nextLevelAt: Int? = null,
    @SerializedName("department") val department: String? = null,
    @SerializedName("course") val course: String? = null,
    @SerializedName("year") val year: Int? = null,
    @SerializedName("profilePhotoUrl") val profilePhotoUrl: String? = null,
    @SerializedName("gender") val gender: String? = null,
    @SerializedName("bloodGroup") val bloodGroup: String? = null,
    @SerializedName("vehicleNumber") val vehicleNumber: String? = null,
    @SerializedName("emergencyContacts") val emergencyContacts: JsonElement? = null,
    @SerializedName("stats") val stats: UserStatsDto? = null,
)

data class AuthDataDto(
    @SerializedName("user") val user: UserDto? = null,
    @SerializedName("accessToken") val accessToken: String? = null,
)

data class RegisterRequestDto(
    @SerializedName("name") val name: String,
    @SerializedName("rollNumber") val rollNumber: String,
    @SerializedName("collegeEmail") val collegeEmail: String,
    @SerializedName("password") val password: String,
    @SerializedName("confirmPassword") val confirmPassword: String? = null,
)

data class LoginRequestDto(
    @SerializedName("identifier") val identifier: String,
    @SerializedName("password") val password: String,
)

data class EmergencyContactDto(
    @SerializedName("name") val name: String,
    @SerializedName("phone") val phone: String,
    @SerializedName("relation") val relation: String? = null,
)

/** Null fields are omitted by Gson, so only changed values are sent. */
data class UpdateProfileRequestDto(
    @SerializedName("name") val name: String? = null,
    @SerializedName("department") val department: String? = null,
    @SerializedName("course") val course: String? = null,
    @SerializedName("year") val year: Int? = null,
    @SerializedName("profilePhotoUrl") val profilePhotoUrl: String? = null,
    @SerializedName("gender") val gender: String? = null,
    @SerializedName("bloodGroup") val bloodGroup: String? = null,
    @SerializedName("vehicleNumber") val vehicleNumber: String? = null,
    @SerializedName("emergencyContacts") val emergencyContacts: EmergencyContactDto? = null,
)

data class LeaderboardEntryDto(
    @SerializedName("id") val id: String? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("rank") val rank: Int? = null,
    @SerializedName("rewardPoints") val rewardPoints: Int? = null,
    @SerializedName("level") val level: Int? = null,
    @SerializedName("levelTitle") val levelTitle: String? = null,
    @SerializedName("rating") val rating: Float? = null,
    @SerializedName("ridesCompleted") val ridesCompleted: Int? = null,
    @SerializedName("department") val department: String? = null,
    @SerializedName("profilePhotoUrl") val profilePhotoUrl: String? = null,
)

// ---------------------------------------------------------------- rides

data class RideDto(
    @SerializedName("id") val id: String? = null,
    @SerializedName("driverId") val driverId: String? = null,
    @SerializedName("vehicleType") val vehicleType: String? = null,
    @SerializedName("vehicleNumber") val vehicleNumber: String? = null,
    @SerializedName("pickupAddress") val pickupAddress: String? = null,
    @SerializedName("pickupLat") val pickupLat: Double? = null,
    @SerializedName("pickupLng") val pickupLng: Double? = null,
    @SerializedName("dropAddress") val dropAddress: String? = null,
    @SerializedName("dropLat") val dropLat: Double? = null,
    @SerializedName("dropLng") val dropLng: Double? = null,
    @SerializedName("departureTime") val departureTime: String? = null,
    @SerializedName("pricePerSeat") val pricePerSeat: Int? = null,
    @SerializedName("totalSeats") val totalSeats: Int? = null,
    @SerializedName("seatsLeft") val seatsLeft: Int? = null,
    @SerializedName("status") val status: String? = null,
    @SerializedName("driver") val driver: UserDto? = null,
    @SerializedName("routeKm") val routeKm: Double? = null,
    @SerializedName("isDriver") val isDriver: Boolean? = null,
    @SerializedName("myBooking") val myBooking: BookingRequestDto? = null,
    @SerializedName("passengers") val passengers: List<BookingRequestDto>? = null,
    @SerializedName("pendingRequests") val pendingRequests: Int? = null,
)

data class CreateRideRequestDto(
    @SerializedName("pickupAddress") val pickupAddress: String,
    @SerializedName("pickupLat") val pickupLat: Double,
    @SerializedName("pickupLng") val pickupLng: Double,
    @SerializedName("dropAddress") val dropAddress: String,
    @SerializedName("dropLat") val dropLat: Double,
    @SerializedName("dropLng") val dropLng: Double,
    @SerializedName("departureTime") val departureTime: String,
    @SerializedName("totalSeats") val totalSeats: Int,
    @SerializedName("pricePerSeat") val pricePerSeat: Int,
    @SerializedName("vehicleType") val vehicleType: String,
    @SerializedName("vehicleNumber") val vehicleNumber: String? = null,
)

data class RideEnvelopeDto(
    @SerializedName("ride") val ride: RideDto? = null,
    @SerializedName("pointsEarned") val pointsEarned: Int? = null,
)

data class ProfileUpdateEnvelopeDto(
    @SerializedName("user") val user: UserDto? = null,
)

/** How well a ride fits the passenger's trip (computed by the backend). */
data class RouteMatchDto(
    @SerializedName("matchType") val matchType: String? = null,
    @SerializedName("pickupDistanceKm") val pickupDistanceKm: Double? = null,
    @SerializedName("dropDistanceKm") val dropDistanceKm: Double? = null,
    @SerializedName("pickupFraction") val pickupFraction: Double? = null,
    @SerializedName("dropFraction") val dropFraction: Double? = null,
    @SerializedName("detourKm") val detourKm: Double? = null,
    @SerializedName("routeKm") val routeKm: Double? = null,
    @SerializedName("passengerTripKm") val passengerTripKm: Double? = null,
    @SerializedName("overlapRatio") val overlapRatio: Double? = null,
)

/** Row from the route-matching search (ride columns + driver info + match). */
data class SearchRideRowDto(
    @SerializedName("id") val id: String? = null,
    @SerializedName("driverId") val driverId: String? = null,
    @SerializedName("vehicleType") val vehicleType: String? = null,
    @SerializedName("vehicleNumber") val vehicleNumber: String? = null,
    @SerializedName("pickupAddress") val pickupAddress: String? = null,
    @SerializedName("pickupLat") val pickupLat: Double? = null,
    @SerializedName("pickupLng") val pickupLng: Double? = null,
    @SerializedName("dropAddress") val dropAddress: String? = null,
    @SerializedName("dropLat") val dropLat: Double? = null,
    @SerializedName("dropLng") val dropLng: Double? = null,
    @SerializedName("departureTime") val departureTime: String? = null,
    @SerializedName("pricePerSeat") val pricePerSeat: Int? = null,
    @SerializedName("totalSeats") val totalSeats: Int? = null,
    @SerializedName("seatsLeft") val seatsLeft: Int? = null,
    @SerializedName("status") val status: String? = null,
    @SerializedName("driverName") val driverName: String? = null,
    @SerializedName("driverGender") val driverGender: String? = null,
    @SerializedName("driverRating") val driverRating: Float? = null,
    @SerializedName("driverLevel") val driverLevel: Int? = null,
    @SerializedName("driverVerified") val driverVerified: Boolean? = null,
    @SerializedName("match") val match: RouteMatchDto? = null,
    @SerializedName("estimatedFare") val estimatedFare: Int? = null,
    @SerializedName("myBookingStatus") val myBookingStatus: String? = null,
)

// ------------------------------------------------------------- bookings

data class BookingRequestDto(
    @SerializedName("id") val id: String? = null,
    @SerializedName("rideId") val rideId: String? = null,
    @SerializedName("passengerId") val passengerId: String? = null,
    @SerializedName("pickupAddress") val pickupAddress: String? = null,
    @SerializedName("dropAddress") val dropAddress: String? = null,
    @SerializedName("seatsRequested") val seatsRequested: Int? = null,
    @SerializedName("status") val status: String? = null,
    @SerializedName("createdAt") val createdAt: String? = null,
    @SerializedName("reviewed") val reviewed: Boolean? = null,
    @SerializedName("ride") val ride: RideDto? = null,
    @SerializedName("passenger") val passenger: UserDto? = null,
)

data class BookingEnvelopeDto(
    @SerializedName("booking") val booking: BookingRequestDto? = null,
    @SerializedName("seatsLeft") val seatsLeft: Int? = null,
)

data class RequestBookingBodyDto(
    @SerializedName("rideId") val rideId: String,
    @SerializedName("pickupAddress") val pickupAddress: String,
    @SerializedName("dropAddress") val dropAddress: String,
    @SerializedName("seatsRequested") val seatsRequested: Int,
)

// ---------------------------------------------------------- reviews/sos

data class CreateReviewDto(
    @SerializedName("rideId") val rideId: String,
    @SerializedName("targetId") val targetId: String,
    @SerializedName("rating") val rating: Int,
    @SerializedName("comment") val comment: String? = null,
)

data class ReviewDto(
    @SerializedName("id") val id: String? = null,
    @SerializedName("rating") val rating: Float? = null,
    @SerializedName("comment") val comment: String? = null,
    @SerializedName("createdAt") val createdAt: String? = null,
    @SerializedName("author") val author: UserDto? = null,
)

data class SosRequestDto(
    @SerializedName("rideId") val rideId: String? = null,
    @SerializedName("lat") val lat: Double? = null,
    @SerializedName("lng") val lng: Double? = null,
)
