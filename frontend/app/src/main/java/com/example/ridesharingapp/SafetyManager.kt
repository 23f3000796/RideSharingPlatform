package com.example.ridesharingapp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.net.Uri
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Campus safety: SOS alert with live location.
 *
 * 1. Confirm (avoid accidental triggers)
 * 2. Grab the current location (best effort, 6 s max)
 * 3. POST /api/v1/sos so campus security sees it on the dashboard
 * 4. Offer immediate follow-ups: call 112, call / SMS the emergency contact, share location
 *
 * Steps 4 still work if the network or location is unavailable.
 */
object SafetyManager {

    private const val EMERGENCY_NUMBER = "112"

    fun triggerEmergencySOS(activity: AppCompatActivity, rideId: String? = null) {
        val user = RideRepository.currentUser
        AlertDialog.Builder(activity)
            .setTitle("🚨 Send emergency SOS?")
            .setMessage(
                "Your live location, name and roll number (${user.rollNumber.ifBlank { "—" }}) will be sent to campus security" +
                    (if (rideId != null) " together with this ride's driver and vehicle details." else ".") +
                    "\n\nUse only in a real emergency.",
            )
            .setPositiveButton("SEND SOS") { _, _ -> send(activity, rideId) }
            .setNegativeButton("Cancel", null)
            .setIcon(android.R.drawable.ic_dialog_alert)
            .show()
    }

    private fun send(activity: AppCompatActivity, rideId: String?) {
        Toast.makeText(activity, "Sending SOS…", Toast.LENGTH_SHORT).show()
        activity.lifecycleScope.launch {
            val location = currentLocation(activity)
            val result = RideSharingRepository.raiseSos(rideId, location?.latitude, location?.longitude)
            val status = result.fold(
                onSuccess = { "✅ Campus security has been alerted" + if (location == null) " (location unavailable)." else " with your location." },
                onFailure = { "⚠️ Could not reach the server: ${it.message}\nCall for help directly below." },
            )
            if (!activity.isFinishing && !activity.isDestroyed) showFollowUps(activity, status, location)
        }
    }

    private fun showFollowUps(activity: AppCompatActivity, status: String, location: Location?) {
        val user = RideRepository.currentUser
        val contactPhone = user.emergencyContactPhone?.takeIf { it.isNotBlank() }
        val contactName = user.emergencyContactName?.takeIf { it.isNotBlank() } ?: "emergency contact"
        val mapsLink = location?.let { "https://maps.google.com/?q=${it.latitude},${it.longitude}" }
        val message = "SOS! I need help. " + (mapsLink?.let { "My location: $it" } ?: "I couldn't get my location.") +
            " — ${user.name} (${user.rollNumber})"

        val options = mutableListOf<Pair<String, () -> Unit>>()
        options += "📞 Call $EMERGENCY_NUMBER (emergency)" to { dial(activity, EMERGENCY_NUMBER) }
        if (contactPhone != null) {
            options += "📞 Call $contactName" to { dial(activity, contactPhone) }
            options += "💬 Text $contactName my location" to { sms(activity, contactPhone, message) }
        }
        options += "📤 Share my location…" to { UiKit.shareText(activity, message, "Share SOS") }

        AlertDialog.Builder(activity)
            .setTitle(status)
            .setItems(options.map { it.first }.toTypedArray()) { _, which -> options[which].second() }
            .setNegativeButton(if (contactPhone == null) "Close (add a contact in Profile)" else "Close", null)
            .show()
    }

    private fun dial(activity: AppCompatActivity, number: String) {
        // ACTION_DIAL needs no permission and lets the user confirm the call.
        activity.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${Uri.encode(number)}")))
    }

    private fun sms(activity: AppCompatActivity, number: String, body: String) {
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${Uri.encode(number)}")).putExtra("sms_body", body)
        try {
            activity.startActivity(intent)
        } catch (_: Exception) {
            UiKit.shareText(activity, body, "Share SOS")
        }
    }

    private suspend fun currentLocation(activity: AppCompatActivity): Location? {
        val granted = ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) return null
        val client = LocationServices.getFusedLocationProviderClient(activity)
        return withTimeoutOrNull(6_000) {
            suspendCancellableCoroutine { cont ->
                val cts = CancellationTokenSource()
                try {
                    client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token)
                        .addOnSuccessListener { loc ->
                            if (loc != null) {
                                if (cont.isActive) cont.resume(loc)
                            } else {
                                client.lastLocation
                                    .addOnSuccessListener { last -> if (cont.isActive) cont.resume(last) }
                                    .addOnFailureListener { if (cont.isActive) cont.resume(null) }
                            }
                        }
                        .addOnFailureListener { if (cont.isActive) cont.resume(null) }
                } catch (_: SecurityException) {
                    if (cont.isActive) cont.resume(null)
                }
                cont.invokeOnCancellation { cts.cancel() }
            }
        }
    }
}
