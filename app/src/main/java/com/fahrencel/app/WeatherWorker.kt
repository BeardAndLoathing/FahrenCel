package com.fahrencel.app

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/** Refreshes weather for "My location" and every saved city, then redraws widgets. */
class WeatherWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val ctx = applicationContext
        var anyOk = false
        // Taps and app opens ask for fresh data; scheduled runs follow the normal timing.
        val force = inputData.getBoolean(KEY_FORCE, false)

        try {
            // No location yet: nothing to fetch for "My location", and that's not a failure.
            LocationHelper.resolve(ctx)?.let { (lat, lon) ->
                withContext(Dispatchers.IO) {
                    LocationHelper.lookUpPlace(ctx, lat, lon)
                    fetchAndSave(ctx, Places.HERE, lat, lon, force)
                }
            }
            anyOk = true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // try the cities anyway
        }

        for (p in Places.saved(ctx)) {
            try {
                withContext(Dispatchers.IO) { fetchAndSave(ctx, p.id, p.lat, p.lon, force) }
                anyOk = true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // skip this city this time
            }
        }

        WidgetUpdater.refreshAll(ctx)
        return if (anyOk) Result.success() else Result.retry()
    }

    /**
     * The forecast is fetched every time. The extras change more slowly, so they're fetched
     * less often, which keeps the number of calls to the weather service down:
     * air quality hourly, sea every 3 hours, river flow every 12 hours. Places with no sea
     * or river nearby are only re-checked once a week.
     */
    private fun fetchAndSave(ctx: Context, placeId: String, lat: Double, lon: Double, force: Boolean) {
        val forecast = WeatherRepository.fetchForecast(lat, lon)
        // Make sure the response is usable before replacing the cached one.
        WeatherRepository.parse(forecast, null, null, null, System.currentTimeMillis())
        WeatherStore.save(ctx, placeId, forecast)

        fun due(kind: String, everyMin: Long): Boolean {
            val age = WeatherStore.extraAgeMin(ctx, placeId, kind)
            val wait = if (WeatherStore.extraIsEmpty(ctx, placeId, kind)) 7 * 24 * 60L else everyMin
            return age >= wait || (force && kind == "air" && age >= 10)
        }
        if (due("air", 55)) WeatherStore.saveExtra(ctx, placeId, "air", WeatherRepository.fetchAir(lat, lon))
        // Sea and river conditions are Pro features, so free users don't use those calls at all.
        if (Pro.isPro(ctx)) {
            if (due("marine", 180)) WeatherStore.saveExtra(ctx, placeId, "marine", WeatherRepository.fetchMarine(lat, lon))
            if (due("flood", 720)) WeatherStore.saveExtra(ctx, placeId, "flood", WeatherRepository.fetchFlood(lat, lon))
        }
    }
}

private const val KEY_FORCE = "force"

object WeatherScheduler {
    private const val PERIODIC = "weather_periodic"
    private const val NOW = "weather_now"

    private val needsNetwork = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    fun schedulePeriodic(ctx: Context) {
        val req = PeriodicWorkRequestBuilder<WeatherWorker>(30, TimeUnit.MINUTES)
            .setConstraints(needsNetwork)
            .build()
        WorkManager.getInstance(ctx)
            .enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, req)
    }

    fun refreshNow(ctx: Context) {
        val req = OneTimeWorkRequestBuilder<WeatherWorker>()
            .setConstraints(needsNetwork)
            .setInputData(workDataOf(KEY_FORCE to true))
            .build()
        WorkManager.getInstance(ctx)
            .enqueueUniqueWork(NOW, ExistingWorkPolicy.REPLACE, req)
    }

    fun cancel(ctx: Context) {
        WorkManager.getInstance(ctx).cancelUniqueWork(PERIODIC)
    }
}
