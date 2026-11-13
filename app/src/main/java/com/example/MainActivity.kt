package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.screens.ActiveJourneyScreen
import com.example.ui.screens.HistoryScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.JourneyDetailScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.AppNavigationTab
import com.example.ui.viewmodel.JourneyViewModel
import com.example.util.GpsFilterAndDistanceCalculator

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                val viewModel: JourneyViewModel = viewModel()
                MainAppScreen(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun MainAppScreen(viewModel: JourneyViewModel) {
    val currentTab by viewModel.currentTab.collectAsStateWithLifecycle()
    val liveTrackingState by viewModel.liveTrackingState.collectAsStateWithLifecycle()
    val activeJourney by viewModel.activeJourney.collectAsStateWithLifecycle()

    // If an active journey exists in DB and tracking service is running, prompt user or stay in active view
    LaunchedEffect(activeJourney) {
        if (activeJourney != null && currentTab == AppNavigationTab.HOME && liveTrackingState.isTracking) {
            viewModel.navigateTo(AppNavigationTab.ACTIVE)
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.statusBars,
        bottomBar = {
            if (currentTab != AppNavigationTab.ACTIVE && currentTab != AppNavigationTab.DETAIL) {
                Column {
                    // Floating Mini Tracking Bar if tracking is active in background while browsing
                    AnimatedVisibility(
                        visible = liveTrackingState.isTracking,
                        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
                    ) {
                        Surface(
                            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            shadowElevation = 8.dp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.navigateTo(AppNavigationTab.ACTIVE) }
                                .testTag("mini_tracking_banner")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .background(Color(0xFF10B981), CircleShape)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = "Tracking Journey...",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "${GpsFilterAndDistanceCalculator.formatDuration(liveTrackingState.elapsedSeconds)} • ${GpsFilterAndDistanceCalculator.formatDistance(liveTrackingState.distanceTraveledMeters)}",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }
                                Icon(Icons.Default.ChevronRight, contentDescription = null)
                            }
                        }
                    }

                    // Bottom Navigation Bar
                    NavigationBar(
                        modifier = Modifier.testTag("bottom_nav_bar"),
                        windowInsets = WindowInsets.navigationBars
                    ) {
                        NavigationBarItem(
                            selected = currentTab == AppNavigationTab.HOME,
                            onClick = { viewModel.navigateTo(AppNavigationTab.HOME) },
                            icon = { Icon(Icons.Default.Map, contentDescription = "Map") },
                            label = { Text("Map") },
                            modifier = Modifier.testTag("nav_item_home")
                        )

                        if (liveTrackingState.isTracking) {
                            NavigationBarItem(
                                selected = currentTab == AppNavigationTab.ACTIVE,
                                onClick = { viewModel.navigateTo(AppNavigationTab.ACTIVE) },
                                icon = {
                                    Box {
                                        Icon(Icons.Default.Navigation, contentDescription = "Live Tracking")
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .background(Color(0xFF10B981), CircleShape)
                                                .align(Alignment.TopEnd)
                                        )
                                    }
                                },
                                label = { Text("Live Tracking") },
                                modifier = Modifier.testTag("nav_item_active")
                            )
                        }

                        NavigationBarItem(
                            selected = currentTab == AppNavigationTab.HISTORY,
                            onClick = { viewModel.navigateTo(AppNavigationTab.HISTORY) },
                            icon = { Icon(Icons.Default.History, contentDescription = "Recorded Journeys") },
                            label = { Text("History") },
                            modifier = Modifier.testTag("nav_item_history")
                        )
                    }
                }
            }
        },
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentTab) {
                AppNavigationTab.HOME -> HomeScreen(viewModel = viewModel)
                AppNavigationTab.ACTIVE -> ActiveJourneyScreen(viewModel = viewModel)
                AppNavigationTab.HISTORY -> HistoryScreen(viewModel = viewModel)
                AppNavigationTab.DETAIL -> JourneyDetailScreen(viewModel = viewModel)
            }
        }
    }
}
