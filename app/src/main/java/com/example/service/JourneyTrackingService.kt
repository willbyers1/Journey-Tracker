package com.example.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import com.example.JourneyApplication
import com.example.MainActivity
import com.example.data.model.JourneyEntity
import com.example.data.model.JourneyPointEntity
import com.example.data.model.JourneyStatus
import com.example.util.GpsFilterAndDistanceCalculator
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class LiveTrackingState(
    val isTracking: Boolean = false,
    val journeyId: Long? = null,
    val departureName: String = "",
    val destinationName: String = "",
    val destinationLat: Double = 0.0,
    val destinationLng: Double = 0.0,
    val arrivalRadiusMeters: Float = 50f,
    val startTimeMillis: Long = 0L,
    val elapsedSeconds: Long = 0L,
    val distanceTraveledMeters: Double = 0.0,
    val currentSpeedMps: Float = 0f,
    val distanceToDestinationMeters: Double? = null,
    val currentLocation: Location? = null,
    val consecutiveArrivalFixes: Int = 0,
    val hasArrived: Boolean = false
)

class JourneyTrackingService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private var timerJob: Job? = null

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var locationCallback: LocationCallback? = null
    private var wakeLock: PowerManager.WakeLock? = null

    private var activeJourneyId: Long = 0L
    private var activeJourney: JourneyEntity? = null
    private var lastRecordedLocation: Location? = null
    private var cumulativeDistanceMeters: Double = 0.0
    private var journeyStartTimestamp: Long = 0L

    // Counter for consecutive location fixes inside arrival radius
    private var arrivalConfirmationCount = 0
    private val requiredConsecutiveArrivalFixes = 3

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "JourneyTracker::ServiceWakeLock"
        ).apply {
            setReferenceCounted(false)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START_JOURNEY

        when (action) {
            ACTION_STOP_JOURNEY -> {
                stopTracking(isArrival = false)
            }
            ACTION_START_JOURNEY -> {
                val journeyId = intent?.getLongExtra(EXTRA_JOURNEY_ID, -1L) ?: -1L
                if (journeyId > 0) {
                    startTracking(journeyId)
                } else {
                    stopSelf()
                }
            }
            else -> {
                // If started with no explicit action, inspect if an active journey exists in DB
                serviceScope.launch {
                    val currentActive = JourneyApplication.instance.repository.getActiveJourney()
                    if (currentActive != null) {
                        startTracking(currentActive.id)
                    } else {
                        stopSelf()
                    }
                }
            }
        }

        return START_STICKY
    }

    private fun startTracking(journeyId: Long) {
        activeJourneyId = journeyId

        serviceScope.launch {
            val journey = JourneyApplication.instance.repository.getJourneyById(journeyId)
            if (journey == null || journey.status != JourneyStatus.ACTIVE) {
                stopSelf()
                return@launch
            }

            activeJourney = journey
            journeyStartTimestamp = journey.startTimeMillis
            cumulativeDistanceMeters = journey.totalDistanceMeters

            // Acquire partial wake lock for screen-off tracking
            wakeLock?.let {
                if (!it.isHeld) {
                    it.acquire(12 * 60 * 60 * 1000L) // Safety max 12 hours
                }
            }

            // Start foreground service with initial notification
            val initialNotification = buildTrackingNotification(
                elapsedSeconds = (System.currentTimeMillis() - journeyStartTimestamp) / 1000,
                distanceMeters = cumulativeDistanceMeters
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceCompat.startForeground(
                    this@JourneyTrackingService,
                    NOTIFICATION_ID_TRACKING,
                    initialNotification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                )
            } else {
                startForeground(NOTIFICATION_ID_TRACKING, initialNotification)
            }

            // Start Location Updates
            requestGpsLocationUpdates()

            // Start elapsed time ticker
            startTimerTicker()

            // Publish initial LiveTrackingState
            _trackingState.value = LiveTrackingState(
                isTracking = true,
                journeyId = journey.id,
                departureName = journey.departureName,
                destinationName = journey.destinationName,
                destinationLat = journey.destinationLatitude,
                destinationLng = journey.destinationLongitude,
                arrivalRadiusMeters = journey.arrivalRadiusMeters,
                startTimeMillis = journeyStartTimestamp,
                elapsedSeconds = (System.currentTimeMillis() - journeyStartTimestamp) / 1000,
                distanceTraveledMeters = cumulativeDistanceMeters
            )
        }
    }

    @SuppressLint("MissingPermission")
    private fun requestGpsLocationUpdates() {
        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            3000L // 3 seconds interval
        ).apply {
            setMinUpdateIntervalMillis(1500L) // Fastest interval
            setMinUpdateDistanceMeters(2.0f)   // Minimum displacement in meters
            setWaitForAccurateLocation(false)
        }.build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val location = result.lastLocation ?: return
                handleNewLocation(location)
            }
        }

        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback as LocationCallback,
                Looper.getMainLooper()
            )
        } catch (_: SecurityException) {
            // Permission revoked
            stopSelf()
        }
    }

    private fun handleNewLocation(location: Location) {
        val journey = activeJourney ?: return

        // 1. Filter inaccurate GPS noise and impossible jumps
        val isAcceptable = GpsFilterAndDistanceCalculator.isLocationAcceptable(
            current = location,
            previous = lastRecordedLocation
        )

        val distanceToDest = GpsFilterAndDistanceCalculator.computeDistanceMeters(
            location.latitude, location.longitude,
            journey.destinationLatitude, journey.destinationLongitude
        )

        // 2. Arrival Verification Logic
        // Requirements: User is inside arrival radius AND reading has good accuracy AND consecutive fixes confirmed
        if (location.hasAccuracy() && location.accuracy <= journey.arrivalRadiusMeters) {
            if (distanceToDest <= journey.arrivalRadiusMeters) {
                arrivalConfirmationCount++
                if (arrivalConfirmationCount >= requiredConsecutiveArrivalFixes) {
                    // Confirmed Arrival!
                    onArrivalDetected(location)
                    return
                }
            } else {
                // Stepped outside radius, reset consecutive counter
                arrivalConfirmationCount = 0
            }
        }

        if (!isAcceptable) {
            // Update state with current raw location for UI marker, but don't add to traveled polyline
            _trackingState.value = _trackingState.value.copy(
                currentLocation = location,
                distanceToDestinationMeters = distanceToDest,
                consecutiveArrivalFixes = arrivalConfirmationCount
            )
            return
        }

        // 3. Compute traveled segment distance
        val prev = lastRecordedLocation
        if (prev != null) {
            val segmentDistance = GpsFilterAndDistanceCalculator.computeDistanceMeters(
                prev.latitude, prev.longitude,
                location.latitude, location.longitude
            )
            cumulativeDistanceMeters += segmentDistance
        }
        lastRecordedLocation = location

        // 4. Persist GPS point to Room database
        serviceScope.launch {
            JourneyApplication.instance.repository.insertPoint(
                JourneyPointEntity(
                    journeyId = activeJourneyId,
                    latitude = location.latitude,
                    longitude = location.longitude,
                    altitude = if (location.hasAltitude()) location.altitude else 0.0,
                    accuracy = if (location.hasAccuracy()) location.accuracy else 0f,
                    speed = if (location.hasSpeed()) location.speed else 0f,
                    timestampMillis = location.time
                )
            )

            // Update journey record with latest distance
            activeJourney = journey.copy(
                totalDistanceMeters = cumulativeDistanceMeters
            )
            JourneyApplication.instance.repository.updateJourney(activeJourney!!)
        }

        // 5. Update Live State
        val elapsed = (System.currentTimeMillis() - journeyStartTimestamp) / 1000
        _trackingState.value = _trackingState.value.copy(
            distanceTraveledMeters = cumulativeDistanceMeters,
            currentSpeedMps = if (location.hasSpeed()) location.speed else 0f,
            distanceToDestinationMeters = distanceToDest,
            currentLocation = location,
            consecutiveArrivalFixes = arrivalConfirmationCount
        )

        // 6. Update Notification text periodically
        updateNotification(elapsed, cumulativeDistanceMeters)
    }

    private fun startTimerTicker() {
        timerJob?.cancel()
        timerJob = serviceScope.launch {
            while (isActive) {
                delay(1000L)
                val elapsed = (System.currentTimeMillis() - journeyStartTimestamp) / 1000
                _trackingState.value = _trackingState.value.copy(
                    elapsedSeconds = elapsed
                )
            }
        }
    }

    private fun onArrivalDetected(finalLocation: Location) {
        val journey = activeJourney ?: return
        val endTimestamp = System.currentTimeMillis()
        val durationSeconds = (endTimestamp - journeyStartTimestamp) / 1000

        serviceScope.launch {
            // Insert final destination arrival point
            JourneyApplication.instance.repository.insertPoint(
                JourneyPointEntity(
                    journeyId = activeJourneyId,
                    latitude = finalLocation.latitude,
                    longitude = finalLocation.longitude,
                    altitude = if (finalLocation.hasAltitude()) finalLocation.altitude else 0.0,
                    accuracy = if (finalLocation.hasAccuracy()) finalLocation.accuracy else 0f,
                    speed = 0f,
                    timestampMillis = endTimestamp
                )
            )

            // Mark journey COMPLETED in database
            JourneyApplication.instance.repository.completeJourney(
                journeyId = activeJourneyId,
                endTimeMillis = endTimestamp,
                durationSeconds = durationSeconds,
                totalDistanceMeters = cumulativeDistanceMeters
            )

            // Post High Importance Arrival Notification
            sendArrivalNotification(
                journey = journey,
                durationSeconds = durationSeconds,
                distanceMeters = cumulativeDistanceMeters
            )

            _trackingState.value = _trackingState.value.copy(
                isTracking = false,
                hasArrived = true,
                elapsedSeconds = durationSeconds,
                distanceTraveledMeters = cumulativeDistanceMeters
            )

            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun stopTracking(isArrival: Boolean) {
        val journey = activeJourney
        if (journey != null && !isArrival) {
            val endTimestamp = System.currentTimeMillis()
            val durationSeconds = (endTimestamp - journeyStartTimestamp) / 1000
            serviceScope.launch {
                JourneyApplication.instance.repository.completeJourney(
                    journeyId = activeJourneyId,
                    endTimeMillis = endTimestamp,
                    durationSeconds = durationSeconds,
                    totalDistanceMeters = cumulativeDistanceMeters
                )
            }
        }

        _trackingState.value = LiveTrackingState(isTracking = false)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun buildTrackingNotification(elapsedSeconds: Long, distanceMeters: Double): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, JourneyTrackingService::class.java).apply {
            action = ACTION_STOP_JOURNEY
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val destName = activeJourney?.destinationName ?: "Destination"
        val timeStr = GpsFilterAndDistanceCalculator.formatDuration(elapsedSeconds)
        val distStr = GpsFilterAndDistanceCalculator.formatDistance(distanceMeters)

        return NotificationCompat.Builder(this, JourneyApplication.CHANNEL_TRACKING_ID)
            .setContentTitle("Tracking Journey: $destName")
            .setContentText("Time: $timeStr • Traveled: $distStr")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentIntent)
            .addAction(
                android.R.drawable.ic_media_pause,
                "Stop",
                stopPendingIntent
            )
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun updateNotification(elapsedSeconds: Long, distanceMeters: Double) {
        val notification = buildTrackingNotification(elapsedSeconds, distanceMeters)
        val manager = NotificationManagerCompat.from(this)
        try {
            manager.notify(NOTIFICATION_ID_TRACKING, notification)
        } catch (_: SecurityException) {
            // Notification permission missing
        }
    }

    @SuppressLint("MissingPermission")
    private fun sendArrivalNotification(
        journey: JourneyEntity,
        durationSeconds: Long,
        distanceMeters: Double
    ) {
        val contentIntent = PendingIntent.getActivity(
            this,
            2,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val timeStr = GpsFilterAndDistanceCalculator.formatDuration(durationSeconds)
        val distStr = GpsFilterAndDistanceCalculator.formatDistance(distanceMeters)

        val arrivalNotification = NotificationCompat.Builder(this, JourneyApplication.CHANNEL_COMPLETION_ID)
            .setContentTitle("Destination Reached! 🎯")
            .setContentText("You arrived at ${journey.destinationName}.\nDuration: $timeStr • Distance: $distStr")
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "Congratulations! You arrived at ${journey.destinationName} from ${journey.departureName}.\n" +
                            "Total Duration: $timeStr\n" +
                            "Total Distance Traveled: $distStr"
                )
            )
            .setSmallIcon(android.R.drawable.ic_dialog_map)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .build()

        val manager = NotificationManagerCompat.from(this)
        try {
            manager.notify(NOTIFICATION_ID_ARRIVAL, arrivalNotification)
        } catch (_: SecurityException) {
            // Notification permission missing
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        locationCallback?.let { fusedLocationClient.removeLocationUpdates(it) }
        timerJob?.cancel()
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_START_JOURNEY = "com.example.service.ACTION_START_JOURNEY"
        const val ACTION_STOP_JOURNEY = "com.example.service.ACTION_STOP_JOURNEY"
        const val EXTRA_JOURNEY_ID = "extra_journey_id"

        private const val NOTIFICATION_ID_TRACKING = 1001
        private const val NOTIFICATION_ID_ARRIVAL = 1002

        private val _trackingState = MutableStateFlow(LiveTrackingState())
        val trackingState: StateFlow<LiveTrackingState> = _trackingState.asStateFlow()

        fun start(context: Context, journeyId: Long) {
            val intent = Intent(context, JourneyTrackingService::class.java).apply {
                action = ACTION_START_JOURNEY
                putExtra(EXTRA_JOURNEY_ID, journeyId)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, JourneyTrackingService::class.java).apply {
                action = ACTION_STOP_JOURNEY
            }
            context.startService(intent)
        }
    }
}
