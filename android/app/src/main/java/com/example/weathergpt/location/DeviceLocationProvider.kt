package com.example.weathergpt.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

data class DeviceLocation(
    val latitude: Double,
    val longitude: Double
)

class DeviceLocationProvider(
    private val context: Context
) {
    private val tag = "DeviceLocationProvider"

    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocation(): DeviceLocation? = withContext(Dispatchers.IO) {
        val fineGranted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val coarseGranted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!fineGranted && !coarseGranted) {
            Log.w(tag, "Location permission not granted")
            return@withContext null
        }

        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return@withContext null

        // 1. Identify enabled providers
        val candidateProviders = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                if (manager.isProviderEnabled(LocationManager.FUSED_PROVIDER)) {
                    candidateProviders.add(LocationManager.FUSED_PROVIDER)
                }
            } catch (_: Exception) {}
        }

        try {
            if (manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                candidateProviders.add(LocationManager.NETWORK_PROVIDER)
            }
        } catch (_: Exception) {}

        try {
            if (manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                candidateProviders.add(LocationManager.GPS_PROVIDER)
            }
        } catch (_: Exception) {}

        try {
            if (manager.isProviderEnabled(LocationManager.PASSIVE_PROVIDER)) {
                candidateProviders.add(LocationManager.PASSIVE_PROVIDER)
            }
        } catch (_: Exception) {}

        Log.d(tag, "Enabled location providers: $candidateProviders")

        // 2. Gather last known location across all providers and find best candidate
        var bestLastKnown: Location? = null
        for (providerName in listOf(
            LocationManager.NETWORK_PROVIDER,
            LocationManager.GPS_PROVIDER,
            LocationManager.PASSIVE_PROVIDER
        )) {
            try {
                val loc = manager.getLastKnownLocation(providerName)
                if (loc != null) {
                    if (bestLastKnown == null || isBetterLocation(loc, bestLastKnown)) {
                        bestLastKnown = loc
                    }
                }
            } catch (e: Exception) {
                Log.d(tag, "getLastKnownLocation error for $providerName: ${e.message}")
            }
        }

        if (bestLastKnown != null) {
            Log.d(tag, "Found last known location: (${bestLastKnown.latitude}, ${bestLastKnown.longitude}) accuracy=${bestLastKnown.accuracy}m")
        }

        // 3. Try to fetch a fresh current location with a 3.5-second timeout
        val liveProvider = when {
            candidateProviders.contains(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && candidateProviders.contains(LocationManager.FUSED_PROVIDER) -> LocationManager.FUSED_PROVIDER
            candidateProviders.contains(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            candidateProviders.isNotEmpty() -> candidateProviders.first()
            else -> null
        }

        var liveLocation: Location? = null
        if (liveProvider != null) {
            liveLocation = try {
                withTimeoutOrNull(3500L) {
                    fetchLiveLocation(manager, liveProvider)
                }
            } catch (e: Exception) {
                Log.w(tag, "fetchLiveLocation timed out or failed: ${e.message}")
                null
            }
        }

        val finalLocation = liveLocation ?: bestLastKnown
        if (finalLocation != null) {
            Log.d(tag, "Returning location: (${finalLocation.latitude}, ${finalLocation.longitude})")
            DeviceLocation(
                latitude = finalLocation.latitude,
                longitude = finalLocation.longitude
            )
        } else {
            Log.w(tag, "Could not obtain any location fix")
            null
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun fetchLiveLocation(
        manager: LocationManager,
        provider: String
    ): Location? = suspendCancellableCoroutine { continuation ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val signal = CancellationSignal()
            continuation.invokeOnCancellation {
                try { signal.cancel() } catch (_: Exception) {}
            }
            try {
                manager.getCurrentLocation(
                    provider,
                    signal,
                    context.mainExecutor
                ) { loc ->
                    if (continuation.isActive) {
                        continuation.resume(loc)
                    }
                }
            } catch (e: Exception) {
                if (continuation.isActive) {
                    continuation.resume(null)
                }
            }
        } else {
            try {
                val last = manager.getLastKnownLocation(provider)
                continuation.resume(last)
            } catch (_: Exception) {
                continuation.resume(null)
            }
        }
    }

    private fun isBetterLocation(location: Location, currentBestLocation: Location?): Boolean {
        if (currentBestLocation == null) return true
        val timeDelta = location.time - currentBestLocation.time
        val isSignificantlyNewer = timeDelta > 120_000
        val isSignificantlyOlder = timeDelta < -120_000
        val isNewer = timeDelta > 0

        if (isSignificantlyNewer) return true
        if (isSignificantlyOlder) return false

        val accuracyDelta = (location.accuracy - currentBestLocation.accuracy).toInt()
        val isMoreAccurate = accuracyDelta < 0
        return isMoreAccurate || (isNewer && accuracyDelta <= 0)
    }
}
