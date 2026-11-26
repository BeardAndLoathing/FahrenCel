package com.fahrencel.app

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableIntStateOf
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    /** Bumped after a refresh so screens re-read the saved weather. */
    private val refreshTick = mutableIntStateOf(0)

    private val askLocation = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { refreshWithLocation() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppTheme {
                DualWeatherApp(tick = refreshTick.intValue, onRefresh = ::refreshWithLocation)
            }
        }

        // Ask once per fresh start, not again when the screen rotates.
        if (savedInstanceState == null && !LocationHelper.hasPermission(this)) {
            askLocation.launch(
                arrayOf(
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                )
            )
        }
    }

    /**
     * Runs on every open: fresh start, coming back to the app, and after Android closed it in
     * the background. Rotating the screen also lands here, so skip it if we just refreshed.
     */
    override fun onStart() {
        super.onStart()
        WeatherScheduler.schedulePeriodic(this)
        lifecycleScope.launch(Dispatchers.IO) { PhotoStore.deleteUnused(applicationContext) }
        if (System.currentTimeMillis() - lastRefreshMillis > 10 * 60_000L) refreshWithLocation()
    }

    /** Takes a fresh location while the app is open, then refreshes the weather. */
    private fun refreshWithLocation() {
        lastRefreshMillis = System.currentTimeMillis()
        lifecycleScope.launch {
            LocationHelper.updateFromDevice(this@MainActivity)
            WeatherScheduler.refreshNow(this@MainActivity)
            repeat(4) {
                delay(3_000)
                refreshTick.intValue++
            }
        }
    }

    private companion object {
        // Lives as long as the app's process, so it survives rotation but resets after Android closes the app.
        var lastRefreshMillis = 0L
    }
}
