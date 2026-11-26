package com.example.ridesharingapp

import com.google.android.gms.maps.model.LatLng
import kotlin.math.cos
import kotlin.math.sqrt

/** Encoded-polyline helpers (precision 5, as returned by OSRM / Google). */
object Polyline {

    fun decode(encoded: String?): List<LatLng> {
        if (encoded.isNullOrEmpty()) return emptyList()
        val out = ArrayList<LatLng>()
        var index = 0
        var lat = 0
        var lng = 0
        while (index < encoded.length) {
            val dLat = next(encoded, index).also { index = it.second }.first ?: break
            lat += dLat
            val dLng = next(encoded, index).also { index = it.second }.first ?: break
            lng += dLng
            out += LatLng(lat / 1e5, lng / 1e5)
        }
        return out
    }

    private fun next(s: String, start: Int): Pair<Int?, Int> {
        var i = start
        var result = 0
        var shift = 0
        var b: Int
        do {
            if (i >= s.length) return null to i
            b = s[i++].code - 63
            result = result or ((b and 0x1f) shl shift)
            shift += 5
        } while (b >= 0x20)
        return (if (result and 1 != 0) (result shr 1).inv() else result shr 1) to i
    }

    private fun km(a: LatLng, b: LatLng): Double {
        val kmLat = 111.2
        val kmLng = 111.2 * cos(Math.toRadians((a.latitude + b.latitude) / 2))
        val dx = (b.longitude - a.longitude) * kmLng
        val dy = (b.latitude - a.latitude) * kmLat
        return sqrt(dx * dx + dy * dy)
    }

    /** Points of the route between two fractions of its length (0 = start, 1 = end). */
    fun slice(points: List<LatLng>, from: Double, to: Double): List<LatLng> {
        if (points.size < 2) return points
        val seg = points.zipWithNext { a, b -> km(a, b) }
        val total = seg.sum().takeIf { it > 0 } ?: return points
        val startKm = from.coerceIn(0.0, 1.0) * total
        val endKm = to.coerceIn(0.0, 1.0) * total
        val out = ArrayList<LatLng>()
        var walked = 0.0
        for (i in seg.indices) {
            val a = points[i]
            val b = points[i + 1]
            val segStart = walked
            val segEnd = walked + seg[i]
            fun at(km: Double): LatLng {
                val t = if (seg[i] > 0) (km - segStart) / seg[i] else 0.0
                return LatLng(a.latitude + (b.latitude - a.latitude) * t, a.longitude + (b.longitude - a.longitude) * t)
            }
            if (segEnd >= startKm && segStart <= endKm) {
                if (out.isEmpty()) out += at(maxOf(startKm, segStart))
                out += if (segEnd <= endKm) b else at(endKm)
            }
            walked = segEnd
        }
        return out
    }

    fun pointAt(points: List<LatLng>, fraction: Double): LatLng? = slice(points, fraction, fraction).firstOrNull()
}
