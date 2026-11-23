package com.fahrencel.app

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import kotlin.coroutines.resume

object LocationHelper {
    // Used only if we have never obtained a real location: Jerusalem (Machane Yehuda).
    private const val DEFAULT_LAT = 31.7853
    private const val DEFAULT_LON = 35.2122

    fun hasPermission(ctx: Context): Boolean =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    /** Gets a fresh fix while the app is open and saves it. Returns true on success. */
    suspend fun updateFromDevice(ctx: Context): Boolean {
        if (!hasPermission(ctx)) return false
        val loc = try {
            currentLocation(ctx) ?: lastLocation(ctx)
        } catch (e: Exception) {
            null
        } ?: return false
        WeatherStore.saveLocation(ctx, loc.latitude, loc.longitude)
        return true
    }

    /** Used by background refresh: last known fix, else the saved one, else the default. */
    suspend fun resolve(ctx: Context): Pair<Double, Double> {
        if (hasPermission(ctx)) {
            val loc = try {
                lastLocation(ctx)
            } catch (e: Exception) {
                null
            }
            if (loc != null) {
                WeatherStore.saveLocation(ctx, loc.latitude, loc.longitude)
                return loc.latitude to loc.longitude
            }
        }
        return WeatherStore.loadLocation(ctx) ?: (DEFAULT_LAT to DEFAULT_LON)
    }

    /** Looks up the city and neighborhood name (blocking; call off the main thread). */
    @Suppress("DEPRECATION")
    fun lookUpPlace(ctx: Context, lat: Double, lon: Double) {
        val key = String.format(Locale.US, "%.2f,%.2f", lat, lon)
        if (WeatherStore.cachedPlace(ctx, key) != null) return
        if (!Geocoder.isPresent()) return
        try {
            val a = Geocoder(ctx, Locale.getDefault()).getFromLocation(lat, lon, 1)?.firstOrNull()
                ?: return
            val city = a.locality ?: a.subAdminArea ?: a.adminArea
            WeatherStore.savePlace(ctx, key, city, a.subLocality)
        } catch (e: Exception) {
            // No name this time; we'll try again next refresh.
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun lastLocation(ctx: Context): Location? =
        suspendCancellableCoroutine { cont ->
            LocationServices.getFusedLocationProviderClient(ctx).lastLocation
                .addOnSuccessListener { cont.resume(it) }
                .addOnFailureListener { cont.resume(null) }
                .addOnCanceledListener { cont.resume(null) }
        }

    @SuppressLint("MissingPermission")
    private suspend fun currentLocation(ctx: Context): Location? =
        suspendCancellableCoroutine { cont ->
            val cts = CancellationTokenSource()
            LocationServices.getFusedLocationProviderClient(ctx)
                .getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cts.token)
                .addOnSuccessListener { cont.resume(it) }
                .addOnFailureListener { cont.resume(null) }
                .addOnCanceledListener { cont.resume(null) }
            cont.invokeOnCancellation { cts.cancel() }
        }
}
