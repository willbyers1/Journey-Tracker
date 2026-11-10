package com.example

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.example.data.db.JourneyDatabase
import com.example.data.geocoding.LocationSearchService
import com.example.data.repository.JourneyRepository
import org.osmdroid.config.Configuration

class JourneyApplication : Application() {

    lateinit var database: JourneyDatabase
        private set

    lateinit var repository: JourneyRepository
        private set

    lateinit var locationSearchService: LocationSearchService
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        // Initialize OSMDroid configuration
        Configuration.getInstance().userAgentValue = packageName
        val sharedPrefs = getSharedPreferences("osmdroid_prefs", Context.MODE_PRIVATE)
        Configuration.getInstance().load(this, sharedPrefs)

        // Initialize Database & Repository
        database = JourneyDatabase.getInstance(this)
        repository = JourneyRepository(database.journeyDao())
        locationSearchService = LocationSearchService(this)

        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // Tracking Channel (Persistent, Low Importance to prevent audio spam while tracking)
            val trackingChannel = NotificationChannel(
                CHANNEL_TRACKING_ID,
                "Active Journey Tracking",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Background GPS tracking notifications during journeys"
                setShowBadge(false)
            }

            // Completion Channel (High Importance for arrival alerts)
            val completionChannel = NotificationChannel(
                CHANNEL_COMPLETION_ID,
                "Journey Arrival Notification",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Automatic arrival and summary notifications when destination is reached"
                enableVibration(true)
                setShowBadge(true)
            }

            notificationManager.createNotificationChannel(trackingChannel)
            notificationManager.createNotificationChannel(completionChannel)
        }
    }

    companion object {
        const val CHANNEL_TRACKING_ID = "channel_journey_tracking"
        const val CHANNEL_COMPLETION_ID = "channel_journey_completion"

        lateinit var instance: JourneyApplication
            private set
    }
}
