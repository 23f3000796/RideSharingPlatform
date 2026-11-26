package com.example.ridesharingapp

import com.example.ridesharingapp.network.ApiException
import com.example.ridesharingapp.network.ModelMapper
import com.example.ridesharingapp.network.NetworkModule
import com.example.ridesharingapp.network.PaginationMeta
import com.example.ridesharingapp.network.dto.CreateReviewDto
import com.example.ridesharingapp.network.dto.CreateRideRequestDto
import com.example.ridesharingapp.network.dto.LoginRequestDto
import com.example.ridesharingapp.network.dto.RegisterRequestDto
import com.example.ridesharingapp.network.dto.RequestBookingBodyDto
import com.example.ridesharingapp.network.dto.SosRequestDto
import com.example.ridesharingapp.network.dto.UpdateProfileRequestDto
import com.example.ridesharingapp.network.requireData
import com.example.ridesharingapp.network.runApi
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Central API access for the app. Maps backend envelopes into UI models.
 * Every call returns a [Result]; failures carry a user-readable message.
 */
object RideSharingRepository {

    private val api get() = NetworkModule.api
    private val gson = Gson()

    enum class SearchSort(val apiValue: String) { BEST("best"), PRICE("price"), TIME("time") }

    // ---------------------------------------------------------------- auth

    suspend fun register(
        name: String,
        rollNumber: String,
        collegeEmail: String,
        password: String,
        confirmPassword: String?,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runApi {
            val data = api.register(
                RegisterRequestDto(
                    name = name,
                    rollNumber = rollNumber,
                    collegeEmail = collegeEmail,
                    password = password,
                    confirmPassword = confirmPassword,
                ),
            ).requireData()
            val user = data.user ?: throw ApiException("Invalid registration response")
            val token = data.accessToken ?: throw ApiException("No access token")
            SessionManager.saveSession(token, user.id ?: throw ApiException("No user id"))
            RideRepository.currentUser = ModelMapper.userDtoToProfile(user)
        }
    }

    suspend fun login(identifier: String, password: String): Result<Unit> = withContext(Dispatchers.IO) {
        runApi {
            val data = api.login(LoginRequestDto(identifier = identifier, password = password)).requireData()
            val user = data.user ?: throw ApiException("Invalid login response")
            val token = data.accessToken ?: throw ApiException("No access token")
            SessionManager.saveSession(token, user.id ?: throw ApiException("No user id"))
            RideRepository.currentUser = ModelMapper.userDtoToProfile(user)
        }
    }

    fun logout() {
        SessionManager.clear()
        RideRepository.reset()
    }

    // ------------------------------------------------------------- profile

    suspend fun refreshProfile(): Result<UserProfile> = withContext(Dispatchers.IO) {
        runApi {
            val profile = ModelMapper.userDtoToProfile(api.getProfile().requireData())
            RideRepository.currentUser = profile
            profile
        }
    }

    suspend fun updateProfile(body: UpdateProfileRequestDto): Result<UserProfile> = withContext(Dispatchers.IO) {
        runApi {
            val user = api.updateProfile(body).requireData().user
                ?: throw ApiException("Profile update missing user")
            // Keep stats from the last full profile load
            val old = RideRepository.currentUser
            val profile = ModelMapper.userDtoToProfile(user).copy(
                ridesOffered = old.ridesOffered,
                ridesShared = old.ridesShared,
                upcomingAsDriver = old.upcomingAsDriver,
                upcomingAsPassenger = old.upcomingAsPassenger,
                reviewsReceived = old.reviewsReceived,
            )
            RideRepository.currentUser = profile
            profile
        }
    }

    suspend fun getLeaderboard(page: Int = 1, limit: Int = 20): Result<Pair<List<LeaderboardUiRow>, PaginationMeta?>> =
        withContext(Dispatchers.IO) {
            runApi {
                val env = api.getLeaderboard(page, limit)
                val list = env.requireData()
                val meta = env.meta?.let { gson.fromJson(it, PaginationMeta::class.java) }
                val rows = list.mapIndexed { index, e -> ModelMapper.leaderboardRow(e, (page - 1) * limit + index + 1) }
                rows to meta
            }
        }

    // --------------------------------------------------------------- rides

    suspend fun createRide(body: CreateRideRequestDto): Result<RideResult> = withContext(Dispatchers.IO) {
        runApi {
            val ride = api.createRide(body).requireData().ride ?: throw ApiException("No ride in response")
            ModelMapper.rideDtoToResult(ride)
        }
    }

    suspend fun searchRides(
        trip: PassengerTrip,
        departureFromIso: String,
        departureToIso: String?,
        radiusKm: Double,
        sort: SearchSort,
        page: Int = 1,
        limit: Int = 30,
    ): Result<List<RideResult>> = withContext(Dispatchers.IO) {
        runApi {
            api.searchRides(
                pickupLat = trip.pickupLat,
                pickupLng = trip.pickupLng,
                dropLat = trip.dropLat,
                dropLng = trip.dropLng,
                radius = radiusKm,
                departureTime = departureFromIso,
                departureTimeTo = departureToIso,
                seatsRequired = trip.seats,
                sort = sort.apiValue,
                page = page,
                limit = limit,
            ).requireData().map { ModelMapper.searchRowToResult(it) }
        }
    }

    suspend fun getMyRides(page: Int = 1, limit: Int = 50): Result<List<RideResult>> = withContext(Dispatchers.IO) {
        runApi {
            api.getMyRides(page, limit).requireData().map { ModelMapper.rideDtoToResult(it) }
        }
    }

    suspend fun getRideDetails(id: String): Result<RideResult> = withContext(Dispatchers.IO) {
        runApi { ModelMapper.rideDtoToResult(api.getRideDetails(id).requireData()) }
    }

    /** Returns the server's message (e.g. points earned). */
    suspend fun startRide(id: String): Result<String> = rideAction { api.startRide(id) }
    suspend fun completeRide(id: String): Result<String> = rideAction { api.completeRide(id) }
    suspend fun cancelRide(id: String): Result<String> = rideAction { api.cancelRide(id) }

    private suspend fun rideAction(call: suspend () -> com.example.ridesharingapp.network.ApiEnvelope<*>): Result<String> =
        withContext(Dispatchers.IO) {
            runApi {
                val env = call()
                env.requireData()
                env.message
            }
        }

    // ------------------------------------------------------------ bookings

    suspend fun requestBooking(
        rideId: String,
        pickupAddress: String,
        dropAddress: String,
        seatsRequested: Int,
    ): Result<String> = withContext(Dispatchers.IO) {
        runApi {
            val env = api.requestBooking(
                RequestBookingBodyDto(
                    rideId = rideId,
                    pickupAddress = pickupAddress,
                    dropAddress = dropAddress,
                    seatsRequested = seatsRequested,
                ),
            )
            env.requireData()
            env.message
        }
    }

    suspend fun getMyBookings(page: Int = 1, limit: Int = 50): Result<List<BookingRequest>> =
        withContext(Dispatchers.IO) {
            runApi {
                api.getMyBookings(page, limit).requireData()
                    .mapNotNull { ModelMapper.bookingDtoToUi(it, viewAsDriver = false) }
            }
        }

    /** Every request on rides I drive, in a single call. */
    suspend fun getDriverBookings(page: Int = 1, limit: Int = 50): Result<List<BookingRequest>> =
        withContext(Dispatchers.IO) {
            runApi {
                api.getIncomingRequests(page, limit).requireData()
                    .mapNotNull { ModelMapper.bookingDtoToUi(it, viewAsDriver = true) }
            }
        }

    suspend fun acceptBooking(bookingId: String): Result<String> = withContext(Dispatchers.IO) {
        runApi { api.acceptBooking(bookingId).let { it.requireData(); it.message } }
    }

    suspend fun rejectBooking(bookingId: String): Result<String> = withContext(Dispatchers.IO) {
        runApi { api.rejectBooking(bookingId).let { it.requireData(); it.message } }
    }

    suspend fun cancelBooking(bookingId: String): Result<String> = withContext(Dispatchers.IO) {
        runApi { api.cancelBooking(bookingId).let { it.requireData(); it.message } }
    }

    // ------------------------------------------------------ reviews / SOS

    suspend fun submitReview(rideId: String, targetId: String, rating: Int, comment: String?): Result<String> =
        withContext(Dispatchers.IO) {
            runApi {
                val env = api.createReview(
                    CreateReviewDto(
                        rideId = rideId,
                        targetId = targetId,
                        rating = rating,
                        comment = comment?.trim()?.takeIf { it.isNotEmpty() },
                    ),
                )
                env.requireData()
                env.message
            }
        }

    suspend fun raiseSos(rideId: String?, lat: Double?, lng: Double?): Result<String> = withContext(Dispatchers.IO) {
        runApi {
            val env = api.raiseSos(SosRequestDto(rideId = rideId, lat = lat, lng = lng))
            env.requireData()
            env.message
        }
    }
}
