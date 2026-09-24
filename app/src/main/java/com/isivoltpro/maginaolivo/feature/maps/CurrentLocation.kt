package com.isivoltpro.maginaolivo.feature.maps

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat

val LOCATION_PERMISSIONS = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)

fun hasLocationPermission(context: Context): Boolean = LOCATION_PERMISSIONS.any {
    ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
}

/**
 * One position for "Mi ubicación", only when the farmer taps it; nothing is tracked or stored.
 * Uses the platform LocationManager, so no Google services are needed. Null when the
 * permission is missing or location is switched off.
 */
@SuppressLint("MissingPermission")
fun requestCurrentLocation(context: Context, onResult: (GeoPoint?) -> Unit) {
    val manager = context.getSystemService(LocationManager::class.java)
    if (manager == null || !hasLocationPermission(context)) return onResult(null)
    val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
        .filter { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }
    if (providers.isEmpty()) return onResult(null)
    fun lastKnown(): GeoPoint? = providers
        .mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }
        .maxByOrNull(Location::getTime)
        ?.let { GeoPoint(it.latitude, it.longitude) }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        runCatching {
            manager.getCurrentLocation(providers.first(), null, context.mainExecutor) { location ->
                onResult(location?.let { GeoPoint(it.latitude, it.longitude) } ?: lastKnown())
            }
        }.onFailure { onResult(lastKnown()) }
    } else {
        onResult(lastKnown())
    }
}
