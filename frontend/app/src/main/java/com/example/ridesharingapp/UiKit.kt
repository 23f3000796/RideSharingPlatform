package com.example.ridesharingapp

import android.content.Context
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.TextView
import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat
import java.util.Locale

/** Small shared UI helpers so every screen renders statuses, chips and numbers the same way. */
object UiKit {

    fun dp(context: Context, value: Int): Int = (value * context.resources.displayMetrics.density).toInt()

    /** Colours (text, background) for a ride or booking status. */
    fun statusColors(status: String): Pair<Int, Int> = when (status.lowercase(Locale.US)) {
        "accepted", "ongoing" -> R.color.success to R.color.success_bg
        "completed" -> R.color.purple to R.color.purple_bg
        "rejected", "cancelled" -> R.color.danger to R.color.danger_bg
        "pending" -> R.color.warning to R.color.warning_bg
        else -> R.color.info to R.color.info_bg // scheduled
    }

    fun statusLabel(status: String): String = when (status.lowercase(Locale.US)) {
        "rejected" -> "Declined"
        "ongoing" -> "On the way"
        else -> status
    }

    /** Render a rounded status pill on a TextView. */
    fun applyStatusPill(view: TextView, status: String) {
        val (fg, bg) = statusColors(status)
        applyPill(view, UiKit.statusLabel(status), fg, bg)
    }

    fun applyPill(view: TextView, text: String, @ColorRes fg: Int, @ColorRes bg: Int) {
        val ctx = view.context
        view.text = text
        view.setTextColor(ContextCompat.getColor(ctx, fg))
        view.background = GradientDrawable().apply {
            cornerRadius = dp(ctx, 20).toFloat()
            setColor(ContextCompat.getColor(ctx, bg))
        }
        val h = dp(ctx, 10)
        val v = dp(ctx, 4)
        view.setPadding(h, v, h, v)
    }

    fun circle(color: Int) = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(color)
    }

    fun initials(name: String): String =
        name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.take(2)
            .joinToString("") { it.first().uppercase() }.ifEmpty { "?" }

    fun firstName(name: String): String = name.trim().split(" ").firstOrNull().orEmpty().ifEmpty { "there" }

    fun shortPlace(address: String): String = address.split(",").firstOrNull()?.trim()?.ifEmpty { address } ?: address

    fun km(value: Double): String = when {
        value < 0.1 -> "on route"
        value < 1.0 -> "${(value * 1000).toInt()} m"
        else -> String.format(Locale.getDefault(), "%.1f km", value)
    }

    fun rating(value: Float): String = if (value <= 0f) "New" else String.format(Locale.getDefault(), "%.1f", value)

    fun greeting(): String {
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        return when (hour) {
            in 5..11 -> "Good morning"
            in 12..16 -> "Good afternoon"
            else -> "Good evening"
        }
    }

    fun setVisible(view: View, visible: Boolean) {
        view.visibility = if (visible) View.VISIBLE else View.GONE
    }

    /** Plain-text ride summary for sharing with friends / family (safety). */
    fun shareRideText(ride: RideResult): String = buildString {
        appendLine("I'm travelling with Campus RidePool")
        appendLine("Driver: ${ride.driverName}")
        if (ride.vehicleType.isNotBlank() || ride.vehicleNumber.isNotBlank()) {
            appendLine("Vehicle: ${listOf(ride.vehicleType, ride.vehicleNumber).filter { it.isNotBlank() }.joinToString(" · ")}")
        }
        appendLine("From: ${ride.pickupAddress}")
        appendLine("To: ${ride.dropAddress}")
        appendLine("Departure: ${ride.date}, ${ride.time}")
        append("Route: https://www.google.com/maps/dir/?api=1&origin=${ride.pickupLat},${ride.pickupLng}&destination=${ride.dropLat},${ride.dropLng}")
    }

    fun shareText(context: Context, text: String, title: String = "Share via") {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(intent, title))
    }
}
