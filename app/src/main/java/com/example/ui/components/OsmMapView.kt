package com.example.ui.components

import android.graphics.Color
import android.graphics.Paint
import android.view.MotionEvent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.model.JourneyPointEntity
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.Polyline

@Composable
fun OsmMapView(
    modifier: Modifier = Modifier,
    departurePoint: GeoPoint? = null,
    destinationPoint: GeoPoint? = null,
    currentLocation: GeoPoint? = null,
    arrivalRadiusMeters: Float = 50f,
    recordedPoints: List<JourneyPointEntity> = emptyList(),
    onMapTapped: ((GeoPoint) -> Unit)? = null,
    autoCenterOnLocation: Boolean = true
) {
    val context = LocalContext.current
    val mapView = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            isTilesScaledToDpi = true
            controller.setZoom(15.0)
            controller.setCenter(GeoPoint(41.0082, 28.9784)) // Default Istanbul, will re-center
        }
    }

    DisposableEffect(mapView) {
        mapView.onResume()
        onDispose {
            mapView.onPause()
        }
    }

    AndroidView(
        factory = { mapView },
        modifier = modifier.fillMaxSize(),
        update = { map ->
            map.overlays.clear()

            // 1. Map Tap Overlay
            if (onMapTapped != null) {
                val eventsReceiver = object : MapEventsReceiver {
                    override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                        onMapTapped(p)
                        return true
                    }

                    override fun longPressHelper(p: GeoPoint): Boolean = false
                }
                map.overlays.add(MapEventsOverlay(eventsReceiver))
            }

            // 2. Traveled Route Polyline (Actual recorded GPS points)
            if (recordedPoints.isNotEmpty()) {
                val polyline = Polyline(map).apply {
                    outlinePaint.color = Color.parseColor("#0284C7") // Vivid Sky Blue
                    outlinePaint.strokeWidth = 14f
                    outlinePaint.strokeCap = Paint.Cap.ROUND
                    outlinePaint.strokeJoin = Paint.Join.ROUND
                    val geoPoints = recordedPoints.map { GeoPoint(it.latitude, it.longitude) }
                    setPoints(geoPoints)
                }
                map.overlays.add(polyline)
            }

            // 3. Destination Arrival Radius Area
            if (destinationPoint != null && arrivalRadiusMeters > 0) {
                val circlePolygon = Polygon(map).apply {
                    points = Polygon.pointsAsCircle(destinationPoint, arrivalRadiusMeters.toDouble())
                    fillPaint.color = Color.parseColor("#3310B981") // Semi-transparent emerald
                    outlinePaint.color = Color.parseColor("#10B981")
                    outlinePaint.strokeWidth = 4f
                }
                map.overlays.add(circlePolygon)
            }

            // 4. Departure Marker (Green)
            if (departurePoint != null) {
                val depMarker = Marker(map).apply {
                    position = departurePoint
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    title = "Departure Point"
                    icon = map.context.getDrawable(android.R.drawable.ic_menu_myplaces)?.mutate()?.apply {
                        setTint(Color.parseColor("#10B981"))
                    }
                }
                map.overlays.add(depMarker)
            }

            // 5. Destination Marker (Red)
            if (destinationPoint != null) {
                val destMarker = Marker(map).apply {
                    position = destinationPoint
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    title = "Destination"
                    icon = map.context.getDrawable(android.R.drawable.ic_menu_myplaces)?.mutate()?.apply {
                        setTint(Color.parseColor("#EF4444"))
                    }
                }
                map.overlays.add(destMarker)
            }

            // 6. User Current Position Marker (Blue / Direction)
            if (currentLocation != null) {
                val currentMarker = Marker(map).apply {
                    position = currentLocation
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    title = "Current Location"
                    icon = map.context.getDrawable(android.R.drawable.ic_menu_compass)?.mutate()?.apply {
                        setTint(Color.parseColor("#2563EB"))
                    }
                }
                map.overlays.add(currentMarker)

                if (autoCenterOnLocation && recordedPoints.size <= 2) {
                    map.controller.animateTo(currentLocation)
                }
            }

            // 7. Auto Zoom Bounds when showing full recorded journey
            if (recordedPoints.size > 2 && !autoCenterOnLocation) {
                val lats = recordedPoints.map { it.latitude }
                val lons = recordedPoints.map { it.longitude }
                val minLat = lats.minOrNull() ?: 0.0
                val maxLat = lats.maxOrNull() ?: 0.0
                val minLon = lons.minOrNull() ?: 0.0
                val maxLon = lons.maxOrNull() ?: 0.0

                if (minLat != maxLat || minLon != maxLon) {
                    val box = BoundingBox(maxLat + 0.002, maxLon + 0.002, minLat - 0.002, minLon - 0.002)
                    map.zoomToBoundingBox(box, true, 80)
                }
            }

            map.invalidate()
        }
    )
}
