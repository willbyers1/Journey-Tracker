package com.example.ui.screens

import android.Manifest
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocationSearching
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.Route
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.LocationSearchDialog
import com.example.ui.components.OsmMapView
import com.example.ui.viewmodel.AppNavigationTab
import com.example.ui.viewmodel.JourneyViewModel
import com.example.util.GpsFilterAndDistanceCalculator
import org.osmdroid.util.GeoPoint

@Composable
fun HomeScreen(
    viewModel: JourneyViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val departureName by viewModel.departureName.collectAsStateWithLifecycle()
    val departureLat by viewModel.departureLat.collectAsStateWithLifecycle()
    val departureLng by viewModel.departureLng.collectAsStateWithLifecycle()

    val destinationName by viewModel.destinationName.collectAsStateWithLifecycle()
    val destinationLat by viewModel.destinationLat.collectAsStateWithLifecycle()
    val destinationLng by viewModel.destinationLng.collectAsStateWithLifecycle()

    val arrivalRadius by viewModel.arrivalRadiusMeters.collectAsStateWithLifecycle()
    val currentDeviceLocation by viewModel.currentDeviceLocation.collectAsStateWithLifecycle()
    val completedJourneys by viewModel.completedJourneysFlowState()

    var showDepartureDialog by remember { mutableStateOf(false) }
    var showDestinationDialog by remember { mutableStateOf(false) }

    // Permission Request Handler
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        if (fineGranted) {
            viewModel.fetchDeviceLocation()
        }
    }

    LaunchedEffect(Unit) {
        permissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.POST_NOTIFICATIONS
            )
        )
    }

    val depPoint = if (departureLat != null && departureLng != null) {
        GeoPoint(departureLat!!, departureLng!!)
    } else null

    val destPoint = if (destinationLat != null && destinationLng != null) {
        GeoPoint(destinationLat!!, destinationLng!!)
    } else null

    val currentPoint = if (currentDeviceLocation != null) {
        GeoPoint(currentDeviceLocation!!.latitude, currentDeviceLocation!!.longitude)
    } else null

    val canStartJourney = departureLat != null && departureLng != null &&
            destinationLat != null && destinationLng != null

    Box(modifier = modifier.fillMaxSize()) {
        // 1. Fullscreen Interactive Map
        OsmMapView(
            departurePoint = depPoint,
            destinationPoint = destPoint,
            currentLocation = currentPoint,
            arrivalRadiusMeters = arrivalRadius,
            recordedPoints = emptyList(),
            onMapTapped = { tappedPoint ->
                // If destination not set, set as destination on map tap
                if (destPoint == null) {
                    viewModel.setDestination(
                        "Selected Destination (${String.format("%.4f, %.4f", tappedPoint.latitude, tappedPoint.longitude)})",
                        tappedPoint.latitude,
                        tappedPoint.longitude
                    )
                }
            },
            autoCenterOnLocation = true
        )

        // 2. Floating Location Selection & Control Panel (Overlaid on Bottom)
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Quick Center Floating Action Button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.End
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 6.dp,
                    shadowElevation = 4.dp
                ) {
                    IconButton(
                        onClick = { viewModel.fetchDeviceLocation() },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MyLocation,
                            contentDescription = "Find My Location",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // Main Journey Planning Card
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("journey_planner_card")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp)
                ) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Route,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "New Journey",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (completedJourneys.isNotEmpty()) {
                            Text(
                                text = "${completedJourneys.size} Recorded",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.clickable {
                                    viewModel.navigateTo(AppNavigationTab.HISTORY)
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Departure Selection Row
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showDepartureDialog = true }
                            .testTag("select_departure_button")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .background(Color(0xFF10B981).copy(alpha = 0.2f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.RadioButtonChecked,
                                    contentDescription = null,
                                    tint = Color(0xFF10B981),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Departure Point",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = departureName.ifBlank { "Select Location" },
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Icon(
                                Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Destination Selection Row
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showDestinationDialog = true }
                            .testTag("select_destination_button")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .background(Color(0xFFEF4444).copy(alpha = 0.2f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Place,
                                    contentDescription = null,
                                    tint = Color(0xFFEF4444),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Destination",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = destinationName.ifBlank { "Select Destination" },
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Icon(
                                Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Arrival Radius Settings Chips
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Arrival Radius:",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row {
                            listOf(30f to "30m", 50f to "50m", 100f to "100m").forEach { (rad, label) ->
                                FilterChip(
                                    selected = arrivalRadius == rad,
                                    onClick = { viewModel.arrivalRadiusMeters.value = rad },
                                    label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                                    modifier = Modifier.padding(start = 4.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Start Journey Button
                    Button(
                        onClick = { viewModel.startJourney(context) },
                        enabled = canStartJourney,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("start_journey_button"),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (canStartJourney) "Start Journey" else "Please Select Departure and Destination",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }

    // Departure Search Dialog
    if (showDepartureDialog) {
        LocationSearchDialog(
            title = "Select Departure Point",
            onDismissRequest = { showDepartureDialog = false },
            onLocationSelected = { name, lat, lng ->
                viewModel.setDeparture(name, lat, lng)
            },
            currentDeviceLocation = currentDeviceLocation
        )
    }

    // Destination Search Dialog
    if (showDestinationDialog) {
        LocationSearchDialog(
            title = "Select Destination",
            onDismissRequest = { showDestinationDialog = false },
            onLocationSelected = { name, lat, lng ->
                viewModel.setDestination(name, lat, lng)
            },
            currentDeviceLocation = currentDeviceLocation
        )
    }
}

@Composable
private fun JourneyViewModel.completedJourneysFlowState() =
    completedJourneys.collectAsStateWithLifecycle()
