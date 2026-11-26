package com.example.ridesharingapp.network

import android.graphics.Color
import com.example.ridesharingapp.BookingRequest
import com.example.ridesharingapp.LeaderboardUiRow
import com.example.ridesharingapp.RideResult
import com.example.ridesharingapp.RouteMatchInfo
import com.example.ridesharingapp.UserProfile
import com.example.ridesharingapp.network.dto.BookingRequestDto
import com.example.ridesharingapp.network.dto.LeaderboardEntryDto
import com.example.ridesharingapp.network.dto.RideDto
import com.example.ridesharingapp.network.dto.RouteMatchDto
import com.example.ridesharingapp.network.dto.SearchRideRowDto
import com.example.ridesharingapp.network.dto.UserDto
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs

object ModelMapper {

    // SimpleDateFormat is NOT thread-safe and mapping runs on IO threads in parallel,
    // so every call gets its own formatter.
    private fun isoFormatter() = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
    private fun isoNoMillis() = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
    private fun isoOffset() = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US)

    fun parseIso(iso: String?): Date? {
        if (iso.isNullOrBlank()) return null
        for (f in listOf(isoFormatter(), isoNoMillis(), isoOffset())) {
            try {
                return f.parse(iso) ?: continue
            } catch (_: Exception) {
            }
        }
        return null
    }

    fun formatIso(date: Date): String = isoFormatter().format(date)

    // ------------------------------------------------------------ users

    fun userDtoToProfile(dto: UserDto): UserProfile {
        val levelNumber = dto.level ?: 1
        val title = dto.levelTitle ?: "Newcomer"
        val contact = dto.emergencyContacts?.takeIf { it.isJsonObject }?.asJsonObject
        val contactName = contact?.get("name")?.takeIf { it.isJsonPrimitive }?.asString
        val contactPhone = contact?.get("phone")?.takeIf { it.isJsonPrimitive }?.asString
            ?: contact?.get("primary")?.takeIf { it.isJsonPrimitive }?.asString
        val stats = dto.stats

        return UserProfile(
            userId = dto.id.orEmpty(),
            name = dto.name.orEmpty(),
            collegeId = dto.rollNumber?.let { "ID-${it.takeLast(6)}" } ?: "—",
            rollNumber = dto.rollNumber.orEmpty(),
            collegeEmail = dto.collegeEmail.orEmpty(),
            isVerified = dto.isVerified == true,
            rating = dto.rating ?: 0f,
            rewardPoints = dto.rewardPoints ?: 0,
            ridesCompleted = dto.ridesCompleted ?: 0,
            level = "$title • Level $levelNumber",
            levelNumber = levelNumber,
            levelTitle = title,
            pointsToNextLevel = dto.pointsToNextLevel ?: 0,
            nextLevelAt = dto.nextLevelAt,
            gender = dto.gender?.takeIf { it.isNotBlank() } ?: "Not specified",
            emergencyContact = contactPhone,
            emergencyContactName = contactName,
            emergencyContactPhone = contactPhone,
            vehicleNumber = dto.vehicleNumber,
            department = dto.department,
            course = dto.course,
            year = dto.year,
            bloodGroup = dto.bloodGroup,
            ridesOffered = stats?.ridesOffered ?: 0,
            ridesShared = stats?.ridesShared ?: 0,
            upcomingAsDriver = stats?.upcomingAsDriver ?: 0,
            upcomingAsPassenger = stats?.upcomingAsPassenger ?: 0,
            reviewsReceived = stats?.reviewsReceived ?: 0,
        )
    }

    fun leaderboardRow(dto: LeaderboardEntryDto, fallbackRank: Int) = LeaderboardUiRow(
        rank = dto.rank ?: fallbackRank,
        userId = dto.id.orEmpty(),
        name = dto.name.orEmpty(),
        points = dto.rewardPoints ?: 0,
        levelTitle = dto.levelTitle ?: "Rider",
        level = dto.level ?: 1,
        ridesCompleted = dto.ridesCompleted ?: 0,
        rating = dto.rating ?: 0f,
        department = dto.department,
    )

    // ------------------------------------------------------------ rides

    fun parseDepartureToDisplay(iso: String?): Pair<String, String> {
        val date = parseIso(iso) ?: return "—" to "—"
        return displayDate(date) to SimpleDateFormat("hh:mm a", Locale.getDefault()).format(date)
    }

    /** "Today", "Tomorrow" or "Mon, 12 May". */
    private fun displayDate(date: Date): String {
        val today = Calendar.getInstance()
        val target = Calendar.getInstance().apply { time = date }
        val sameYear = today.get(Calendar.YEAR) == target.get(Calendar.YEAR)
        val dayDiff = target.get(Calendar.DAY_OF_YEAR) - today.get(Calendar.DAY_OF_YEAR)
        return when {
            sameYear && dayDiff == 0 -> "Today"
            sameYear && dayDiff == 1 -> "Tomorrow"
            sameYear && dayDiff == -1 -> "Yesterday"
            else -> SimpleDateFormat("EEE, dd MMM", Locale.getDefault()).format(date)
        }
    }

    fun avatarColorFor(seed: String): Int {
        val hue = (abs(seed.hashCode()) % 360).toFloat()
        return Color.HSVToColor(floatArrayOf(hue, 0.45f, 0.85f))
    }

    private fun RouteMatchDto.toUi() = RouteMatchInfo(
        matchType = matchType ?: "ON_ROUTE",
        pickupDistanceKm = pickupDistanceKm ?: 0.0,
        dropDistanceKm = dropDistanceKm ?: 0.0,
        detourKm = detourKm ?: 0.0,
        routeKm = routeKm ?: 0.0,
        passengerTripKm = passengerTripKm ?: 0.0,
        pickupFraction = pickupFraction ?: 0.0,
        dropFraction = dropFraction ?: 1.0,
        roadRoute = roadRoute == true,
        pickupEtaMin = pickupEtaMin,
    )

    fun rideDtoToResult(dto: RideDto, avatarSeed: String = dto.driverId.orEmpty()): RideResult {
        val (d, t) = parseDepartureToDisplay(dto.departureTime)
        val driver = dto.driver
        val price = dto.pricePerSeat ?: 0
        val myBooking = dto.myBooking?.takeIf { it.id != null }
        return RideResult(
            rideId = dto.id.orEmpty(),
            driverId = dto.driverId.orEmpty(),
            driverName = driver?.name.orEmpty(),
            driverRating = driver?.rating ?: 0f,
            driverGender = driver?.gender?.takeIf { it.isNotBlank() } ?: "Any",
            driverVerified = driver?.isVerified == true,
            driverLevel = driver?.level ?: 1,
            vehicleType = dto.vehicleType.orEmpty(),
            vehicleNumber = dto.vehicleNumber.orEmpty(),
            pickupAddress = dto.pickupAddress.orEmpty(),
            pickupLat = dto.pickupLat ?: 0.0,
            pickupLng = dto.pickupLng ?: 0.0,
            dropAddress = dto.dropAddress.orEmpty(),
            dropLat = dto.dropLat ?: 0.0,
            dropLng = dto.dropLng ?: 0.0,
            date = d,
            time = t,
            departureIso = dto.departureTime,
            departureMillis = parseIso(dto.departureTime)?.time ?: 0L,
            pricePerSeat = price,
            estimatedFare = price,
            seatsLeft = dto.seatsLeft ?: 0,
            totalSeats = dto.totalSeats ?: (dto.seatsLeft ?: 0),
            avatarColor = avatarColorFor(avatarSeed),
            status = dto.status?.toRideUiStatus() ?: "Scheduled",
            routeKm = dto.routeKm ?: dto.routeDistanceKm,
            routePolyline = dto.routePolyline,
            routeDurationMin = dto.routeDurationMin,
            myBookingStatus = myBooking?.status?.toBookingUiStatus(),
            myBookingId = myBooking?.id,
            myBookingSeats = myBooking?.seatsRequested ?: 0,
            pendingRequests = dto.pendingRequests ?: 0,
            isDriver = dto.isDriver == true,
            passengerNames = dto.passengers.orEmpty().mapNotNull { it.passenger?.name },
        )
    }

    fun searchRowToResult(row: SearchRideRowDto): RideResult {
        val (d, t) = parseDepartureToDisplay(row.departureTime)
        val price = row.pricePerSeat ?: 0
        return RideResult(
            rideId = row.id.orEmpty(),
            driverId = row.driverId.orEmpty(),
            driverName = row.driverName.orEmpty(),
            driverRating = row.driverRating ?: 0f,
            driverGender = row.driverGender?.takeIf { it.isNotBlank() } ?: "Any",
            driverVerified = row.driverVerified == true,
            driverLevel = row.driverLevel ?: 1,
            vehicleType = row.vehicleType.orEmpty(),
            vehicleNumber = row.vehicleNumber.orEmpty(),
            pickupAddress = row.pickupAddress.orEmpty(),
            pickupLat = row.pickupLat ?: 0.0,
            pickupLng = row.pickupLng ?: 0.0,
            dropAddress = row.dropAddress.orEmpty(),
            dropLat = row.dropLat ?: 0.0,
            dropLng = row.dropLng ?: 0.0,
            date = d,
            time = t,
            departureIso = row.departureTime,
            departureMillis = parseIso(row.departureTime)?.time ?: 0L,
            pricePerSeat = price,
            estimatedFare = row.estimatedFare ?: price,
            seatsLeft = row.seatsLeft ?: 0,
            totalSeats = row.totalSeats ?: (row.seatsLeft ?: 0),
            avatarColor = avatarColorFor(row.driverId.orEmpty()),
            status = row.status?.toRideUiStatus() ?: "Scheduled",
            routeKm = row.routeDistanceKm ?: row.match?.routeKm,
            routeDurationMin = row.routeDurationMin,
            match = row.match?.toUi(),
            myBookingStatus = row.myBookingStatus?.toBookingUiStatus(),
        )
    }

    // --------------------------------------------------------- bookings

    fun bookingDtoToUi(dto: BookingRequestDto, viewAsDriver: Boolean): BookingRequest? {
        val id = dto.id ?: return null
        val ride = dto.ride
        val (rideDate, rideTime) = parseDepartureToDisplay(ride?.departureTime)
        val passenger = dto.passenger
        val driverName = ride?.driver?.name.orEmpty()

        val counterparty = if (viewAsDriver) {
            val roll = passenger?.rollNumber?.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty()
            "${passenger?.name.orEmpty()}$roll"
        } else {
            driverName
        }
        val vehicle = listOfNotNull(
            ride?.vehicleType?.takeIf { it.isNotBlank() },
            ride?.vehicleNumber?.takeIf { it.isNotBlank() },
        ).joinToString(" · ")

        return BookingRequest(
            bookingId = id,
            rideId = dto.rideId.orEmpty(),
            driverId = ride?.driverId.orEmpty(),
            passengerId = dto.passengerId.orEmpty(),
            passengerName = passenger?.name.orEmpty(),
            passengerRollNo = passenger?.rollNumber.orEmpty(),
            passengerRating = passenger?.rating ?: 0f,
            pickupAddress = dto.pickupAddress ?: ride?.pickupAddress.orEmpty(),
            dropAddress = dto.dropAddress ?: ride?.dropAddress.orEmpty(),
            rideDate = rideDate,
            rideTime = rideTime,
            seatsRequested = dto.seatsRequested ?: 1,
            status = dto.status?.toBookingUiStatus() ?: "Pending",
            timestamp = parseIso(dto.createdAt)?.time ?: System.currentTimeMillis(),
            viewAsDriver = viewAsDriver,
            counterpartyDetail = counterparty,
            rideSeatsLeft = ride?.seatsLeft,
            rideStatus = ride?.status?.toRideUiStatus() ?: "Scheduled",
            driverName = driverName,
            vehicleLabel = vehicle,
            pricePerSeat = ride?.pricePerSeat ?: 0,
            reviewed = dto.reviewed == true,
        )
    }

    // ------------------------------------------------------ time helpers

    /** Local calendar date at 00:00 as an ISO instant (search lower bound). */
    fun localMidnightIsoForDate(year: Int, month: Int, dayOfMonth: Int): String {
        val cal = Calendar.getInstance()
        cal.set(year, month, dayOfMonth, 0, 0, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return formatIso(cal.time)
    }

    /** Local calendar date at 23:59:59 as an ISO instant (search upper bound). */
    fun localEndOfDayIsoForDate(year: Int, month: Int, dayOfMonth: Int): String {
        val cal = Calendar.getInstance()
        cal.set(year, month, dayOfMonth, 23, 59, 59)
        cal.set(Calendar.MILLISECOND, 999)
        return formatIso(cal.time)
    }

    fun nowIso(): String = formatIso(Date())

    fun localTodayMidnightIso(): String {
        val cal = Calendar.getInstance()
        return localMidnightIsoForDate(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH))
    }

    /** Combine local "yyyy-MM-dd" + "HH:mm" into a UTC ISO instant, or null when unparsable. */
    fun combineLocalDateTimeToIso(dateYmd: String, timeHm: String): String? = try {
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).parse("$dateYmd $timeHm")?.let { formatIso(it) }
    } catch (_: Exception) {
        null
    }

    fun combineLocalDateTimeToMillis(dateYmd: String, timeHm: String): Long? = try {
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).parse("$dateYmd $timeHm")?.time
    } catch (_: Exception) {
        null
    }

    fun String.toRideUiStatus(): String = when (uppercase(Locale.US)) {
        "SCHEDULED" -> "Scheduled"
        "ONGOING" -> "Ongoing"
        "COMPLETED" -> "Completed"
        "CANCELLED" -> "Cancelled"
        else -> this
    }

    fun String.toBookingUiStatus(): String = when (uppercase(Locale.US)) {
        "PENDING" -> "Pending"
        "ACCEPTED" -> "Accepted"
        "REJECTED" -> "Rejected"
        "COMPLETED" -> "Completed"
        else -> this
    }
}
