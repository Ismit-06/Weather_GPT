package com.example.weathergpt.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

data class DeviceLocation(
    val latitude: Double,
    val longitude: Double
)

class DeviceLocationProvider(
    private val context: Context
) {

    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocation(): DeviceLocation? {

        val fineGranted =
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

        val coarseGranted =
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

        if (!fineGranted && !coarseGranted) {
            return null
        }

        val manager =
            context.getSystemService(
                Context.LOCATION_SERVICE
            ) as LocationManager

        val provider =
            when {
                manager.isProviderEnabled(LocationManager.GPS_PROVIDER) ->
                    LocationManager.GPS_PROVIDER

                manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) ->
                    LocationManager.NETWORK_PROVIDER

                manager.isProviderEnabled(LocationManager.PASSIVE_PROVIDER) ->
                    LocationManager.PASSIVE_PROVIDER

                else ->
                    return null
            }

        // Try getting last known location first as fast fallback
        val lastKnown = try {
            manager.getLastKnownLocation(provider)
        } catch (_: Exception) {
            null
        }

        return suspendCancellableCoroutine { continuation ->

            try {
                manager.getCurrentLocation(
                    provider,
                    null,
                    context.mainExecutor
                ) { location ->

                    if (location != null && continuation.isActive) {
                        continuation.resume(
                            DeviceLocation(
                                latitude = location.latitude,
                                longitude = location.longitude
                            )
                        )
                    } else if (continuation.isActive) {
                        if (lastKnown != null) {
                            continuation.resume(
                                DeviceLocation(
                                    latitude = lastKnown.latitude,
                                    longitude = lastKnown.longitude
                                )
                            )
                        } else {
                            continuation.resume(null)
                        }
                    }
                }
            } catch (e: Exception) {
                if (continuation.isActive) {
                    if (lastKnown != null) {
                        continuation.resume(
                            DeviceLocation(
                                latitude = lastKnown.latitude,
                                longitude = lastKnown.longitude
                            )
                        )
                    } else {
                        continuation.resume(null)
                    }
                }
            }
        }
    }
}
