package com.example.ridesharingapp.network

import com.example.ridesharingapp.network.dto.AuthDataDto
import com.example.ridesharingapp.network.dto.BookingEnvelopeDto
import com.example.ridesharingapp.network.dto.BookingRequestDto
import com.example.ridesharingapp.network.dto.CreateReviewDto
import com.example.ridesharingapp.network.dto.CreateRideRequestDto
import com.example.ridesharingapp.network.dto.LeaderboardEntryDto
import com.example.ridesharingapp.network.dto.LoginRequestDto
import com.example.ridesharingapp.network.dto.ProfileUpdateEnvelopeDto
import com.example.ridesharingapp.network.dto.RegisterRequestDto
import com.example.ridesharingapp.network.dto.RequestBookingBodyDto
import com.example.ridesharingapp.network.dto.ReviewDto
import com.example.ridesharingapp.network.dto.RideDto
import com.example.ridesharingapp.network.dto.RideEnvelopeDto
import com.example.ridesharingapp.network.dto.SearchRideRowDto
import com.example.ridesharingapp.network.dto.SosRequestDto
import com.example.ridesharingapp.network.dto.UpdateProfileRequestDto
import com.example.ridesharingapp.network.dto.UserDto
import com.google.gson.JsonElement
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface RideSharingApiService {

    // ---- auth
    @POST("api/v1/auth/register")
    suspend fun register(@Body body: RegisterRequestDto): ApiEnvelope<AuthDataDto>

    @POST("api/v1/auth/login")
    suspend fun login(@Body body: LoginRequestDto): ApiEnvelope<AuthDataDto>

    // ---- users
    @GET("api/v1/users/profile")
    suspend fun getProfile(): ApiEnvelope<UserDto>

    @PUT("api/v1/users/profile")
    suspend fun updateProfile(@Body body: UpdateProfileRequestDto): ApiEnvelope<ProfileUpdateEnvelopeDto>

    @GET("api/v1/users/leaderboard")
    suspend fun getLeaderboard(
        @Query("page") page: Int,
        @Query("limit") limit: Int,
    ): ApiEnvelope<List<LeaderboardEntryDto>>

    // ---- rides
    @POST("api/v1/rides")
    suspend fun createRide(@Body body: CreateRideRequestDto): ApiEnvelope<RideEnvelopeDto>

    @GET("api/v1/rides/search")
    suspend fun searchRides(
        @Query("pickupLat") pickupLat: Double,
        @Query("pickupLng") pickupLng: Double,
        @Query("dropLat") dropLat: Double,
        @Query("dropLng") dropLng: Double,
        @Query("radius") radius: Double,
        @Query("departureTime") departureTime: String,
        @Query("departureTimeTo") departureTimeTo: String?,
        @Query("seatsRequired") seatsRequired: Int,
        @Query("sort") sort: String,
        @Query("page") page: Int,
        @Query("limit") limit: Int,
    ): ApiEnvelope<List<SearchRideRowDto>>

    @GET("api/v1/rides/my-rides")
    suspend fun getMyRides(
        @Query("page") page: Int,
        @Query("limit") limit: Int,
    ): ApiEnvelope<List<RideDto>>

    @GET("api/v1/rides/{id}")
    suspend fun getRideDetails(@Path("id") id: String): ApiEnvelope<RideDto>

    @PATCH("api/v1/rides/{id}/start")
    suspend fun startRide(@Path("id") id: String): ApiEnvelope<RideEnvelopeDto>

    @PATCH("api/v1/rides/{id}/complete")
    suspend fun completeRide(@Path("id") id: String): ApiEnvelope<RideEnvelopeDto>

    @PATCH("api/v1/rides/{id}/cancel")
    suspend fun cancelRide(@Path("id") id: String): ApiEnvelope<RideEnvelopeDto>

    // ---- bookings
    @POST("api/v1/bookings/request")
    suspend fun requestBooking(@Body body: RequestBookingBodyDto): ApiEnvelope<BookingEnvelopeDto>

    @GET("api/v1/bookings/my-bookings")
    suspend fun getMyBookings(
        @Query("page") page: Int,
        @Query("limit") limit: Int,
    ): ApiEnvelope<List<BookingRequestDto>>

    @GET("api/v1/bookings/incoming")
    suspend fun getIncomingRequests(
        @Query("page") page: Int,
        @Query("limit") limit: Int,
    ): ApiEnvelope<List<BookingRequestDto>>

    @PATCH("api/v1/bookings/{id}/accept")
    suspend fun acceptBooking(@Path("id") bookingId: String): ApiEnvelope<BookingEnvelopeDto>

    @PATCH("api/v1/bookings/{id}/reject")
    suspend fun rejectBooking(@Path("id") bookingId: String): ApiEnvelope<BookingEnvelopeDto>

    @PATCH("api/v1/bookings/{id}/cancel")
    suspend fun cancelBooking(@Path("id") bookingId: String): ApiEnvelope<BookingEnvelopeDto>

    // ---- reviews
    @POST("api/v1/reviews")
    suspend fun createReview(@Body body: CreateReviewDto): ApiEnvelope<JsonElement>

    @GET("api/v1/reviews/user/{id}")
    suspend fun getReviewsForUser(@Path("id") userId: String): ApiEnvelope<List<ReviewDto>>

    // ---- safety
    @POST("api/v1/sos")
    suspend fun raiseSos(@Body body: SosRequestDto): ApiEnvelope<JsonElement>
}
