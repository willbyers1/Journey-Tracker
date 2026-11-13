package com.example.util

import android.location.Location
import java.util.Locale

object GpsFilterAndDistanceCalculator {

    // Maximum acceptable horizontal accuracy in meters (e.g., discard noisy indoor GPS drift)
    const val MAX_ACCEPTABLE_ACCURACY_METERS = 35.0f

    // Maximum physically reasonable speed for road travel (45 m/s ~= 162 km/h) to filter teleport jumps
    const val MAX_REALISTIC_SPEED_MPS = 45.0f

    // Minimum distance threshold to count as movement rather than stationary GPS jitter
    const val MIN_DISPLACEMENT_METERS = 2.0f

    /**
     * Evaluates whether a new GPS location reading is sufficiently accurate and physically plausible.
     */
    fun isLocationAcceptable(current: Location, previous: Location?): Boolean {
        // Discard if accuracy is unknown or too inaccurate
        if (!current.hasAccuracy() || current.accuracy > MAX_ACCEPTABLE_ACCURACY_METERS) {
            return false
        }

        // Must have valid geographic coordinates
        if (current.latitude !in -90.0..90.0 || current.longitude !in -180.0..180.0) {
            return false
        }

        // If this is the first point, accept it
        if (previous == null) {
            return true
        }

        val timeDeltaSeconds = (current.time - previous.time) / 1000.0
        if (timeDeltaSeconds <= 0.0) {
            // Discard out-of-order or duplicate timestamps
            return false
        }

        val distance = computeDistanceMeters(
            previous.latitude, previous.longitude,
            current.latitude, current.longitude
        )

        // Ignore sub-meter stationary jitter
        if (distance < MIN_DISPLACEMENT_METERS) {
            return false
        }

        // Calculate implied speed between consecutive fixes
        val calculatedSpeedMps = distance / timeDeltaSeconds
        if (calculatedSpeedMps > MAX_REALISTIC_SPEED_MPS) {
            // Unrealistic jump / GPS teleportation
            return false
        }

        return true
    }

    /**
     * Calculates distance in meters between two coordinates using the Haversine formula.
     */
    fun computeDistanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val results = FloatArray(1)
        Location.distanceBetween(lat1, lon1, lat2, lon2, results)
        return results[0].toDouble()
    }

    fun formatDuration(totalSeconds: Long): String {
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
        }
    }

    fun formatDistance(meters: Double): String {
        return if (meters < 1000.0) {
            String.format(Locale.getDefault(), "%.0f m", meters)
        } else {
            String.format(Locale.getDefault(), "%.2f km", meters / 1000.0)
        }
    }

    fun formatSpeed(speedMps: Float): String {
        val speedKmh = speedMps * 3.6f
        return String.format(Locale.getDefault(), "%.1f km/h", speedKmh)
    }
}
