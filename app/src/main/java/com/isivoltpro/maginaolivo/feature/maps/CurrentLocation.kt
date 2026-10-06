package com.isivoltpro.maginaolivo.feature.maps

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat

val LOCATION_PERMISSIONS = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)

/** A one-shot location result. Approximate fixes may centre the map but must not drive Catastro automatically. */
data class CurrentLocationFix(
    val point: GeoPoint,
    val approximate: Boolean,
)

internal data class LocationQuality(
    val accepted: Boolean,
    val approximate: Boolean,
)

/**
 * #617 policy: a cached/current fix must be recent enough to represent where the farmer is now,
 * and useful enough to locate a parcel. A coarse-but-recent result can still centre the map, but
 * is marked approximate so the UI never starts a Catastro search silently from it.
 */
internal fun assessLocationQuality(ageMillis: Long, accuracyMeters: Float?): LocationQuality {
    if (ageMillis < 0L || ageMillis > MAX_LOCATION_AGE_MS) return LocationQuality(false, false)
    if (accuracyMeters != null && (!accuracyMeters.isFinite() || accuracyMeters <= 0f || accuracyMeters > MAX_USEFUL_ACCURACY_METERS)) {
        return LocationQuality(false, false)
    }
    return LocationQuality(
        accepted = true,
        approximate = accuracyMeters == null || accuracyMeters > PRECISE_ACCURACY_METERS,
    )
}

fun hasLocationPermission(context: Context): Boolean = LOCATION_PERMISSIONS.any {
    ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
}

/**
 * One position for "Mi ubicación", only when the farmer taps it; nothing is tracked or stored.
 * A stale cached position is never presented as current (#617).
 */
@SuppressLint("MissingPermission")
@Suppress("DEPRECATION")
fun requestCurrentLocation(context: Context, onResult: (CurrentLocationFix?) -> Unit) {
    val manager = context.getSystemService(LocationManager::class.java)
    if (manager == null || !hasLocationPermission(context)) return onResult(null)

    val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
        .filter { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }
    if (providers.isEmpty()) return onResult(null)

    fun accepted(location: Location?): CurrentLocationFix? {
        location ?: return null
        val age = locationAgeMillis(location)
        val accuracy = location.accuracy.takeIf { location.hasAccuracy() }
        val quality = assessLocationQuality(age, accuracy)
        if (!quality.accepted) return null
        return CurrentLocationFix(
            point = GeoPoint(location.latitude, location.longitude),
            approximate = quality.approximate,
        )
    }

    fun bestLastKnown(): CurrentLocationFix? = providers
        .mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }
        .sortedByDescending(Location::getTime)
        .firstNotNullOfOrNull(::accepted)

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        runCatching {
            manager.getCurrentLocation(providers.first(), null, context.mainExecutor) { location ->
                onResult(accepted(location) ?: bestLastKnown())
            }
        }.onFailure { onResult(bestLastKnown()) }
        return
    }

    // On older Android, a fresh usable cache is fine. If it is stale/poor, request one real update
    // instead of pretending yesterday's lastKnownLocation is the farmer's current position.
    bestLastKnown()?.let {
        onResult(it)
        return
    }

    val handler = Handler(Looper.getMainLooper())
    var finished = false
    lateinit var listener: LocationListener
    fun finish(fix: CurrentLocationFix?) {
        if (finished) return
        finished = true
        handler.removeCallbacksAndMessages(listener)
        runCatching { manager.removeUpdates(listener) }
        onResult(fix)
    }
    listener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            accepted(location)?.let(::finish)
        }

        override fun onProviderEnabled(provider: String) = Unit
        override fun onProviderDisabled(provider: String) = Unit
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
    }

    val requested = providers.any { provider ->
        runCatching {
            manager.requestSingleUpdate(provider, listener, Looper.getMainLooper())
            true
        }.getOrDefault(false)
    }
    if (!requested) return onResult(null)

    handler.postAtTime(
        { finish(bestLastKnown()) },
        listener,
        SystemClock.uptimeMillis() + LOCATION_REQUEST_TIMEOUT_MS,
    )
}

private fun locationAgeMillis(location: Location): Long {
    val elapsedNanos = location.elapsedRealtimeNanos
    if (elapsedNanos > 0L) {
        return ((SystemClock.elapsedRealtimeNanos() - elapsedNanos) / 1_000_000L).coerceAtLeast(0L)
    }
    return (System.currentTimeMillis() - location.time).coerceAtLeast(0L)
}

/** #361: whether any location provider is switched on, to tell «apagada» from «sin señal». */
fun isLocationEnabled(context: Context): Boolean {
    val manager = context.getSystemService(LocationManager::class.java) ?: return false
    return listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
        .any { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }
}

/**
 * #361: after a denial, whether Android will still show the permission prompt. It stops showing
 * it once the farmer denied it for good; then only the app settings can grant it.
 */
fun canAskLocationAgain(context: Context): Boolean {
    var current: Context? = context
    while (current is android.content.ContextWrapper && current !is android.app.Activity) current = current.baseContext
    val activity = current as? android.app.Activity ?: return false
    return LOCATION_PERMISSIONS.any { androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale(activity, it) }
}

internal const val MAX_LOCATION_AGE_MS = 5 * 60 * 1_000L
internal const val PRECISE_ACCURACY_METERS = 80f
internal const val MAX_USEFUL_ACCURACY_METERS = 250f
private const val LOCATION_REQUEST_TIMEOUT_MS = 8_000L
