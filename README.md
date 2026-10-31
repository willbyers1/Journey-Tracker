# 📍 GPS Journey Tracker

![Journey Tracker Banner](docs/images/app_banner.png)

[![Android](https://img.shields.io/badge/Platform-Android-3DDC84.svg?style=flat&logo=android)](https://www.android.com/)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.2.10-7F52FF.svg?style=flat&logo=kotlin)](https://kotlinlang.org/)
[![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4.svg?style=flat&logo=jetpackcompose)](https://developer.android.com/jetpack/compose)
[![Material 3](https://img.shields.io/badge/Design-Material%203-blue.svg?style=flat)](https://m3.material.io/)
[![Room Database](https://img.shields.io/badge/Storage-Room%20(SQLite)-FFA000.svg?style=flat)](https://developer.android.com/training/data-storage/room)
[![OpenStreetMap](https://img.shields.io/badge/Map-osmdroid-7EBC6F.svg?style=flat&logo=openstreetmap)](https://github.com/osmdroid/osmdroid)
[![Min SDK](https://img.shields.io/badge/Min%20SDK-24-brightgreen.svg?style=flat)](https://developer.android.com/)
[![Target SDK](https://img.shields.io/badge/Target%20SDK-36-brightgreen.svg?style=flat)](https://developer.android.com/)

A modern, production-grade native Android application designed to measure **real-world journey duration** and record the **actual GPS route traveled** by the user.

> **Central Philosophy:**
> *Start your journey. Put your phone in your pocket. Reach your destination. The application automatically records how long it took and saves the actual route you traveled.*

---

## 📸 Application Screenshots

<p align="center">
  <img src="docs/images/screenshot_home.png" width="23%" alt="Home Screen" />
  &nbsp;&nbsp;
  <img src="docs/images/screenshot_active.png" width="23%" alt="Active Journey Screen" />
  &nbsp;&nbsp;
  <img src="docs/images/screenshot_detail.png" width="23%" alt="Journey Detail Screen" />
  &nbsp;&nbsp;
  <img src="docs/images/screenshot_history.png" width="23%" alt="History Screen" />
</p>

| 1. Home & Route Planner | 2. Live Background Tracking | 3. Full Route Analytics | 4. Recorded Journey History |
| :---: | :---: | :---: | :---: |
| Select departure & destination, set arrival radius (30m/50m/100m), interactive map view. | Live timer, current speed, traveled distance, proximity countdown, and stop action. | Full polyline road route, average speed, waypoint count, and exact timestamps. | Chronological list of saved journeys with distance and time summaries. |

---

## 🌟 Key Features

### 1. Reliable Background GPS Tracking
- **Foreground Service:** Runs with `ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION`, ensuring Android does not kill the process during screen-off operation or when switching apps.
- **Screen-Off Execution:** Employs a managed `PARTIAL_WAKE_LOCK` to keep the GPS engine active while the device is in the user's pocket or locked.
- **Process Recreation Resilience:** Uses `START_STICKY` and continuous Room SQLite point persistence; active journeys survive process recycling without losing recorded segments.
- **Persistent Notification:** Real-time elapsed duration and distance counter updated directly in the status bar notification with a one-tap "Stop" action.

### 2. Multi-Fix Automatic Destination Arrival Detection
- Compares incoming high-accuracy coordinates with the destination.
- **Configurable Radius:** Supports 30m, 50m, or 100m arrival zones.
- **Anti-False-Positive Filtering:** Requires **at least 3 consecutive high-accuracy fixes** within the arrival perimeter (~10 seconds). Brief passes (e.g. overpasses or highway flyovers) will **not** trigger accidental completion.
- **Automatic Finalization:** Stops GPS updates, calculates final duration & distance, commits the journey to Room, and fires a high-priority Heads-up Notification ("Destination Reached! 🎯").

### 3. Smart GPS Noise & Teleport Jump Filtering
- **Accuracy Gate:** Rejects noisy indoor/drift readings (`accuracy > 35m`).
- **Speed Anomaly Rejection:** Analyzes $\Delta \text{distance} / \Delta t$ between consecutive fixes; rejects physically impossible speed jumps ($> 160\text{ km/h}$ or $45\text{ m/s}$).
- **Actual Road Trajectory:** Computes distance cumulatively along verified GPS waypoints rather than drawing a straight theoretical line between endpoints.

### 4. Interactive OpenStreetMap Integration
- Powered by **osmdroid** (no proprietary API keys or paid billing requirements).
- Renders departure pin (green), destination pin (red), arrival radius circle, live user marker, and the full road-following traveled polyline (sky blue).
- Auto-frames bounding box to fit the entire route upon inspection.

### 5. Local-First Room Database Architecture
- **Offline & Private:** All journeys and GPS points are stored locally on the device with zero cloud dependency or mandatory user logins.
- Relational schema linking `journeys` to `journey_points` with cascading deletion.
- Reactive UI updates driven by Kotlin Coroutines and Room `Flow`.

---

## 🏛️ System Architecture

The application is structured following Clean Architecture and MVVM patterns:

```
com.example/
├── data/
│   ├── db/
│   │   ├── JourneyDao.kt           # Room Data Access Object with reactive Flow queries
│   │   └── JourneyDatabase.kt      # SQLite Room database singleton
│   ├── geocoding/
│   │   └── LocationSearchService.kt # Android Geocoder + OpenStreetMap Nominatim fallback
│   ├── model/
│   │   ├── JourneyEntity.kt        # Primary journey record table
│   │   ├── JourneyPointEntity.kt   # Individual recorded GPS coordinates
│   │   └── LocationSearchResult.kt # Autocomplete search model
│   └── repository/
│       └── JourneyRepository.kt    # Data source abstraction for UI and Service
├── receiver/
│   └── BootCompletedReceiver.kt    # Device reboot state integrity handler
├── service/
│   └── JourneyTrackingService.kt   # Location Foreground Service & Arrival Engine
├── ui/
│   ├── components/
│   │   ├── LocationSearchDialog.kt # Search dialog with current location shortcut
│   │   ├── OsmMapView.kt           # Compose wrapper around osmdroid MapView
│   │   └── StatCard.kt             # Material 3 metric presentation cards
│   ├── screens/
│   │   ├── ActiveJourneyScreen.kt  # Live tracking dashboard & arrival indicator
│   │   ├── HistoryScreen.kt        # Saved journeys list & deletion
│   │   ├── HomeScreen.kt           # Route planner & interactive map
│   │   └── JourneyDetailScreen.kt  # Full route inspection & analytics
│   ├── theme/
│   │   ├── Color.kt                # Modern navigation palette (Sky Blue, Emerald)
│   │   ├── Theme.kt                # Dynamic color M3 theme configuration
│   │   └── Type.kt                 # Material typography definitions
│   └── viewmodel/
│       └── JourneyViewModel.kt     # State holder for planning, tracking, and history
├── util/
│   └── GpsFilterAndDistanceCalculator.kt # Haversine math, GPS noise filter, unit formatting
├── JourneyApplication.kt           # Notification channels & database initialization
└── MainActivity.kt                 # Edge-to-edge root activity & navigation
```

---

## 🧪 Testing & Verification

The project includes JVM Robolectric and unit tests covering core business logic:

```bash
# Run unit tests and Robolectric verification
gradle :app:testDebugUnitTest
```

Key tested components:
- `GpsFilterAndDistanceCalculatorTest`: Verifies accuracy threshold rejection, teleportation jump filtering, and formatting.
- `ExampleRobolectricTest`: Verifies application context string integrity and location filtering math.

---

## 🚀 Getting Started

### Prerequisites
- Android Studio Ladybug / Meerkat (or newer)
- JDK 17 / 21
- Android SDK 36 (Minimum supported: Android 7.0 / API 24)

### Building the APK
```bash
# Clone the repository
git clone https://github.com/mmerttbatu/gps-journey-tracker.git
cd gps-journey-tracker

# Build debug APK
gradle :app:assembleDebug
```

The APK will be generated at:
`app/build/outputs/apk/debug/app-debug.apk`

---

## 📋 Required Android Permissions

- `ACCESS_FINE_LOCATION` & `ACCESS_COARSE_LOCATION`: High-precision GPS tracking.
- `FOREGROUND_SERVICE` & `FOREGROUND_SERVICE_LOCATION`: Continuous background service execution.
- `POST_NOTIFICATIONS`: Android 13+ live status bar notifications.
- `WAKE_LOCK`: Keeps CPU awake during screen-off travel.
- `INTERNET` & `ACCESS_NETWORK_STATE`: OpenStreetMap tile rendering and place autocomplete.
- `RECEIVE_BOOT_COMPLETED`: Detects device restarts during active journeys.

---

<div align="center">

**Created by Mert Batu Bülbül**
💻 Computer Engineering Student (2nd Year) 💻

</div>
