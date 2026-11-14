package com.example

import android.content.Context
import android.location.Location
import androidx.test.core.app.ApplicationProvider
import com.example.util.GpsFilterAndDistanceCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun `read string from context matches Journey Tracker`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Journey Tracker", appName)
    }

    @Test
    fun `gps filtering rejects excessive inaccuracy`() {
        val loc = Location("gps").apply {
            latitude = 41.0082
            longitude = 28.9784
            accuracy = 60.0f // > 35m threshold
            time = System.currentTimeMillis()
        }
        val acceptable = GpsFilterAndDistanceCalculator.isLocationAcceptable(loc, null)
        assertFalse("Locations with poor accuracy should be rejected", acceptable)
    }

    @Test
    fun `gps filtering rejects impossible speed teleport jumps`() {
        val now = System.currentTimeMillis()
        val loc1 = Location("gps").apply {
            latitude = 41.0082
            longitude = 28.9784
            accuracy = 5.0f
            time = now
        }

        // Jump 500 meters in 1 second (500 m/s = 1800 km/h)
        val loc2 = Location("gps").apply {
            latitude = 41.0125
            longitude = 28.9784
            accuracy = 5.0f
            time = now + 1000L
        }

        val acceptable = GpsFilterAndDistanceCalculator.isLocationAcceptable(loc2, loc1)
        assertFalse("Impossible speed jumps should be filtered out", acceptable)
    }

    @Test
    fun `formatDuration and formatDistance work correctly`() {
        assertEquals("00:45", GpsFilterAndDistanceCalculator.formatDuration(45))
        assertEquals("05:30", GpsFilterAndDistanceCalculator.formatDuration(330))
        assertEquals("01:15:20", GpsFilterAndDistanceCalculator.formatDuration(4520))

        assertEquals("500 m", GpsFilterAndDistanceCalculator.formatDistance(500.0))
        assertEquals("3.45 km", GpsFilterAndDistanceCalculator.formatDistance(3450.0))
    }
}
