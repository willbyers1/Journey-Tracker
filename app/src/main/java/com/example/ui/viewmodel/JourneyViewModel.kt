package com.example.ui.viewmodel

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.JourneyApplication
import com.example.data.model.JourneyEntity
import com.example.data.model.JourneyPointEntity
import com.example.data.model.JourneyStatus
import com.example.data.repository.JourneyRepository
import com.example.service.JourneyTrackingService
import com.example.service.LiveTrackingState
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class AppNavigationTab {
    HOME,
    ACTIVE,
    HISTORY,
    DETAIL
}

class JourneyViewModel(
    private val repository: JourneyRepository = JourneyApplication.instance.repository
) : ViewModel() {

    private val fusedLocationClient =
        LocationServices.getFusedLocationProviderClient(JourneyApplication.instance)

    // Current navigation tab
    private val _currentTab = MutableStateFlow(AppNavigationTab.HOME)
    val currentTab: StateFlow<AppNavigationTab> = _currentTab.asStateFlow()

    // Live Tracking State from Service
    val liveTrackingState: StateFlow<LiveTrackingState> = JourneyTrackingService.trackingState

    // Active journey from database
    val activeJourney: StateFlow<JourneyEntity?> = repository.activeJourneyFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Completed journeys history from database
    val completedJourneys: StateFlow<List<JourneyEntity>> = repository.completedJourneysFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Active points for live tracking route
    private val _activePoints = MutableStateFlow<List<JourneyPointEntity>>(emptyList())
    val activePoints: StateFlow<List<JourneyPointEntity>> = _activePoints.asStateFlow()

    // Selected journey for detail screen
    private val _selectedJourney = MutableStateFlow<JourneyEntity?>(null)
    val selectedJourney: StateFlow<JourneyEntity?> = _selectedJourney.asStateFlow()

    private val _selectedJourneyPoints = MutableStateFlow<List<JourneyPointEntity>>(emptyList())
    val selectedJourneyPoints: StateFlow<List<JourneyPointEntity>> = _selectedJourneyPoints.asStateFlow()

    // Home Screen Planning Fields
    var departureName = MutableStateFlow("")
    var departureLat = MutableStateFlow<Double?>(null)
    var departureLng = MutableStateFlow<Double?>(null)

    var destinationName = MutableStateFlow("")
    var destinationLat = MutableStateFlow<Double?>(null)
    var destinationLng = MutableStateFlow<Double?>(null)

    var arrivalRadiusMeters = MutableStateFlow(50f)

    // Device current location
    private val _currentDeviceLocation = MutableStateFlow<Location?>(null)
    val currentDeviceLocation: StateFlow<Location?> = _currentDeviceLocation.asStateFlow()

    init {
        fetchDeviceLocation()

        // Observe active journey points dynamically
        viewModelScope.launch {
            repository.activeJourneyFlow.collect { active ->
                if (active != null) {
                    repository.getPointsForJourneyFlow(active.id).collect { points ->
                        _activePoints.value = points
                    }
                } else {
                    _activePoints.value = emptyList()
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun fetchDeviceLocation() {
        try {
            fusedLocationClient.lastLocation.addOnSuccessListener { loc: Location? ->
                if (loc != null) {
                    _currentDeviceLocation.value = loc
                    // Auto-fill departure if empty
                    if (departureLat.value == null) {
                        viewModelScope.launch {
                            val address = JourneyApplication.instance.locationSearchService.reverseGeocode(loc.latitude, loc.longitude)
                            departureName.value = "Current Location ($address)"
                            departureLat.value = loc.latitude
                            departureLng.value = loc.longitude
                        }
                    }
                }
            }
        } catch (_: SecurityException) {
            // Permission not yet granted
        }
    }

    fun setDeparture(name: String, lat: Double, lng: Double) {
        departureName.value = name
        departureLat.value = lat
        departureLng.value = lng
    }

    fun setDestination(name: String, lat: Double, lng: Double) {
        destinationName.value = name
        destinationLat.value = lat
        destinationLng.value = lng
    }

    fun startJourney(context: Context) {
        val depName = departureName.value.ifBlank { "Departure Point" }
        val depLat = departureLat.value ?: return
        val depLng = departureLng.value ?: return

        val destName = destinationName.value.ifBlank { "Destination" }
        val destLat = destinationLat.value ?: return
        val destLng = destinationLng.value ?: return

        viewModelScope.launch {
            val journey = JourneyEntity(
                departureName = depName,
                departureLatitude = depLat,
                departureLongitude = depLng,
                destinationName = destName,
                destinationLatitude = destLat,
                destinationLongitude = destLng,
                arrivalRadiusMeters = arrivalRadiusMeters.value,
                startTimeMillis = System.currentTimeMillis(),
                status = JourneyStatus.ACTIVE
            )

            val journeyId = repository.startJourney(journey)
            JourneyTrackingService.start(context, journeyId)
            _currentTab.value = AppNavigationTab.ACTIVE
        }
    }

    fun stopJourney(context: Context) {
        JourneyTrackingService.stop(context)
    }

    fun navigateTo(tab: AppNavigationTab) {
        _currentTab.value = tab
    }

    fun selectJourneyForDetail(journey: JourneyEntity) {
        _selectedJourney.value = journey
        viewModelScope.launch {
            val points = repository.getPointsForJourney(journey.id)
            _selectedJourneyPoints.value = points
            _currentTab.value = AppNavigationTab.DETAIL
        }
    }

    fun deleteJourney(journey: JourneyEntity) {
        viewModelScope.launch {
            repository.deleteJourneyById(journey.id)
            if (_selectedJourney.value?.id == journey.id) {
                _selectedJourney.value = null
                _selectedJourneyPoints.value = emptyList()
                _currentTab.value = AppNavigationTab.HISTORY
            }
        }
    }
}
