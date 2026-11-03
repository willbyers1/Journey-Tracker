package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.OsmMapView
import com.example.ui.components.StatCard
import com.example.ui.viewmodel.AppNavigationTab
import com.example.ui.viewmodel.JourneyViewModel
import com.example.util.GpsFilterAndDistanceCalculator
import org.osmdroid.util.GeoPoint

@Composable
fun ActiveJourneyScreen(
    viewModel: JourneyViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val liveState by viewModel.liveTrackingState.collectAsStateWithLifecycle()
    val recordedPoints by viewModel.activePoints.collectAsStateWithLifecycle()
    val activeJourney by viewModel.activeJourney.collectAsStateWithLifecycle()

    var showStopConfirmationDialog by remember { mutableStateOf(false) }

    // Handle back button: switch back to home view without interrupting background tracking!
    BackHandler {
        viewModel.navigateTo(AppNavigationTab.HOME)
    }

    val destLat = liveState.destinationLat.takeIf { it != 0.0 } ?: activeJourney?.destinationLatitude
    val destLng = liveState.destinationLng.takeIf { it != 0.0 } ?: activeJourney?.destinationLongitude

    val depLat = activeJourney?.departureLatitude
    val depLng = activeJourney?.departureLongitude

    val departurePoint = if (depLat != null && depLng != null) GeoPoint(depLat, depLng) else null
    val destinationPoint = if (destLat != null && destLng != null) GeoPoint(destLat, destLng) else null

    val currentPoint = liveState.currentLocation?.let {
        GeoPoint(it.latitude, it.longitude)
    } ?: recordedPoints.lastOrNull()?.let {
        GeoPoint(it.latitude, it.longitude)
    }

    Box(modifier = modifier.fillMaxSize()) {
        // 1. Live Map with Recorded Polyline and Destination
        OsmMapView(
            departurePoint = departurePoint,
            destinationPoint = destinationPoint,
            currentLocation = currentPoint,
            arrivalRadiusMeters = liveState.arrivalRadiusMeters,
            recordedPoints = recordedPoints,
            autoCenterOnLocation = true
        )

        // 2. Top App Bar & Background Execution Badge
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                tonalElevation = 6.dp,
                shadowElevation = 4.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { viewModel.navigateTo(AppNavigationTab.HOME) },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(
                                        if (liveState.hasArrived) Color(0xFF10B981) else Color(0xFF3B82F6),
                                        CircleShape
                                    )
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (liveState.hasArrived) "Arrival Completed" else "Live GPS Tracking Active",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (liveState.hasArrived) Color(0xFF10B981) else MaterialTheme.colorScheme.primary
                            )
                        }
                        Text(
                            text = "${liveState.departureName.ifBlank { "Start" }} ➔ ${liveState.destinationName.ifBlank { "Destination" }}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Proximity & Arrival Detection Indicator Banner
            if (liveState.consecutiveArrivalFixes > 0 && !liveState.hasArrived) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF10B981),
                    contentColor = Color.White,
                    shadowElevation = 4.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.NearMe, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Inside destination area! Verifying arrival (${liveState.consecutiveArrivalFixes}/3)",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            if (liveState.hasArrived) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF10B981),
                    contentColor = Color.White,
                    shadowElevation = 4.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Destination Reached! 🎯",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Your journey has been successfully recorded.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }

        // 3. Bottom Dashboard (Live Metrics & Controls)
        Card(
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .testTag("active_journey_dashboard")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Drag handle bar
                Box(
                    modifier = Modifier
                        .size(width = 36.dp, height = 4.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(2.dp))
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Prominent Live Elapsed Time Counter
                Text(
                    text = "ELAPSED TIME",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 1.sp
                )

                Text(
                    text = GpsFilterAndDistanceCalculator.formatDuration(liveState.elapsedSeconds),
                    style = MaterialTheme.typography.displayMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.testTag("live_elapsed_time")
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Metrics Grid
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    StatCard(
                        title = "Traveled",
                        value = GpsFilterAndDistanceCalculator.formatDistance(liveState.distanceTraveledMeters),
                        icon = Icons.Default.DirectionsCar,
                        iconTint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f).testTag("stat_distance_traveled")
                    )

                    StatCard(
                        title = "Current Speed",
                        value = GpsFilterAndDistanceCalculator.formatSpeed(liveState.currentSpeedMps),
                        icon = Icons.Default.Speed,
                        iconTint = Color(0xFFF59E0B),
                        modifier = Modifier.weight(1f).testTag("stat_current_speed")
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Distance to Destination
                val remainingDist = liveState.distanceToDestinationMeters
                if (remainingDist != null) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Place,
                                    contentDescription = null,
                                    tint = Color(0xFFEF4444),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Remaining to Destination:",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = GpsFilterAndDistanceCalculator.formatDistance(remainingDist),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Stop / Finish Journey Button or View Saved button
                if (liveState.hasArrived) {
                    Button(
                        onClick = { viewModel.navigateTo(AppNavigationTab.HISTORY) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("view_saved_journey_button"),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF10B981)
                        )
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "View Recorded Journeys",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    Button(
                        onClick = { showStopConfirmationDialog = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("stop_journey_button"),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Finish Journey",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }

    // Stop Confirmation Dialog
    if (showStopConfirmationDialog) {
        AlertDialog(
            onDismissRequest = { showStopConfirmationDialog = false },
            title = { Text("Finish Journey?") },
            text = {
                Text(
                    "You have not reached your destination yet. Ending the journey now will save the current duration and recorded route."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showStopConfirmationDialog = false
                        viewModel.stopJourney(context)
                        viewModel.navigateTo(AppNavigationTab.HISTORY)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Yes, Finish")
                }
            },
            dismissButton = {
                TextButton(onClick = { showStopConfirmationDialog = false }) {
                    Text("Continue Tracking")
                }
            }
        )
    }
}
