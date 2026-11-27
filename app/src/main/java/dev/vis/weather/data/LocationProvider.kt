package dev.vis.weather.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.math.abs

/**
 * Location via the platform [LocationManager] only. No Google Play Services required,
 * so it works on de-Googled ROMs (GrapheneOS, LineageOS, /e/…) as well as stock Android.
 */
object LocationProvider {

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /**
     * Location while the app is closed (widgets). Implied by the foreground grant before
     * Android 10; from then on it is a separate "Allow all the time" grant.
     */
    fun hasBackground(context: Context): Boolean = hasPermission(context) && (Build.VERSION.SDK_INT < 29 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED)

    /**
     * The [Place] for the device's position, or null without a fix. Reuses [previous] (no
     * reverse geocoding) when we haven't moved more than ~1 km.
     */
    suspend fun currentPlace(context: Context, previous: Place?): Place? {
        val loc = getLocation(context) ?: return null
        if (previous != null && abs(previous.lat - loc.latitude) < 0.01 && abs(previous.lon - loc.longitude) < 0.01) return previous
        return WeatherApi.reverse(context, loc.latitude, loc.longitude)
    }

    private fun hasFine(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun isLocationEnabled(context: Context): Boolean {
        val lm = context.getSystemService(LocationManager::class.java) ?: return false
        return if (Build.VERSION.SDK_INT >= 28) lm.isLocationEnabled
        else lm.isProviderEnabled(LocationManager.GPS_PROVIDER) || lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    @SuppressLint("MissingPermission")
    suspend fun getLocation(context: Context): Location? {
        if (!hasPermission(context)) return null
        val lm = context.getSystemService(LocationManager::class.java) ?: return null

        val providers = buildList {
            if (Build.VERSION.SDK_INT >= 31) add(LocationManager.FUSED_PROVIDER)
            add(LocationManager.NETWORK_PROVIDER)
            if (hasFine(context)) add(LocationManager.GPS_PROVIDER)
        }.filter { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) }

        val lastKnown = (providers + LocationManager.PASSIVE_PROVIDER).mapNotNull {
            runCatching { lm.getLastKnownLocation(it) }.getOrNull()
        }.maxByOrNull { it.time }

        // A recent fix is good enough for weather.
        if (lastKnown != null && System.currentTimeMillis() - lastKnown.time < 30 * 60_000) return lastKnown

        for (provider in providers) {
            val fresh = withTimeoutOrNull(if (provider == LocationManager.GPS_PROVIDER) 15_000L else 6_000L) {
                currentLocation(lm, provider)
            }
            if (fresh != null) return fresh
        }
        return lastKnown
    }

    @SuppressLint("MissingPermission")
    private suspend fun currentLocation(lm: LocationManager, provider: String): Location? =
        suspendCancellableCoroutine { cont ->
            try {
                if (Build.VERSION.SDK_INT >= 30) {
                    val signal = CancellationSignal()
                    cont.invokeOnCancellation { signal.cancel() }
                    lm.getCurrentLocation(provider, signal, { it.run() }) { loc ->
                        if (cont.isActive) cont.resume(loc)
                    }
                } else {
                    val listener = object : LocationListener {
                        override fun onLocationChanged(location: Location) {
                            if (cont.isActive) cont.resume(location)
                        }

                        @Deprecated("Deprecated in Java")
                        override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {}
                        override fun onProviderEnabled(provider: String) {}
                        override fun onProviderDisabled(provider: String) {
                            if (cont.isActive) cont.resume(null)
                        }
                    }
                    cont.invokeOnCancellation { lm.removeUpdates(listener) }
                    @Suppress("DEPRECATION")
                    lm.requestSingleUpdate(provider, listener, Looper.getMainLooper())
                }
            } catch (e: Exception) {
                if (cont.isActive) cont.resume(null)
            }
        }
}
