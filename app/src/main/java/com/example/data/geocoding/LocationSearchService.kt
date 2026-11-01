package com.example.data.geocoding

import android.content.Context
import android.location.Address
import android.location.Geocoder
import com.example.data.model.LocationSearchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.TimeUnit

class LocationSearchService(private val context: Context) {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    @Suppress("DEPRECATION")
    suspend fun searchPlaces(query: String): List<LocationSearchResult> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()

        val results = mutableListOf<LocationSearchResult>()

        // 1. Try Android built-in Geocoder on background IO thread
        try {
            val geocoder = Geocoder(context, Locale.getDefault())
            val addresses: List<Address>? = geocoder.getFromLocationName(query, 5)
            if (!addresses.isNullOrEmpty()) {
                for (addr in addresses) {
                    val title = addr.featureName ?: addr.thoroughfare ?: addr.locality ?: query
                    val addressLines = mutableListOf<String>()
                    for (i in 0..addr.maxAddressLineIndex) {
                        addr.getAddressLine(i)?.let { addressLines.add(it) }
                    }
                    val fullAddress = addressLines.joinToString(", ")
                    results.add(
                        LocationSearchResult(
                            displayName = if (fullAddress.isNotBlank()) fullAddress else title,
                            shortName = title,
                            latitude = addr.latitude,
                            longitude = addr.longitude
                        )
                    )
                }
            }
        } catch (_: Exception) {
            // Geocoder service may not be available on some devices/emulators
        }

        // 2. If Geocoder didn't return results, query OpenStreetMap Nominatim as fallback
        if (results.isEmpty()) {
            try {
                val encoded = URLEncoder.encode(query, "UTF-8")
                val url = "https://nominatim.openstreetmap.org/search?q=$encoded&format=json&limit=5&addressdetails=1"
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "JourneyTrackerAndroid/1.0 (contact: student@app.local)")
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: ""
                        val jsonArray = JSONArray(body)
                        for (i in 0 until jsonArray.length()) {
                            val obj = jsonArray.getJSONObject(i)
                            val lat = obj.getDouble("lat")
                            val lon = obj.getDouble("lon")
                            val displayName = obj.optString("display_name", "")
                            val shortName = displayName.split(",").firstOrNull()?.trim() ?: displayName
                            results.add(
                                LocationSearchResult(
                                    displayName = displayName,
                                    shortName = shortName,
                                    latitude = lat,
                                    longitude = lon
                                )
                            )
                        }
                    }
                }
            } catch (_: Exception) {
                // Network error or rate limiting
            }
        }

        results
    }

    @Suppress("DEPRECATION")
    suspend fun reverseGeocode(latitude: Double, longitude: Double): String = withContext(Dispatchers.IO) {
        try {
            val geocoder = Geocoder(context, Locale.getDefault())
            val addresses: List<Address>? = geocoder.getFromLocation(latitude, longitude, 1)
            val addr = addresses?.firstOrNull()
            if (addr != null) {
                val addressLines = mutableListOf<String>()
                for (i in 0..addr.maxAddressLineIndex) {
                    addr.getAddressLine(i)?.let { addressLines.add(it) }
                }
                val full = addressLines.joinToString(", ")
                if (full.isNotBlank()) return@withContext full
            }
        } catch (_: Exception) {
            // fallback
        }
        String.format(Locale.US, "%.5f, %.5f", latitude, longitude)
    }
}
