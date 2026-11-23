package com.fahrencel.app

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableIntStateOf
import androidx.lifecycle.lifecycleScope
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

        WeatherScheduler.schedulePeriodic(this)
        if (LocationHelper.hasPermission(this)) {
            refreshWithLocation()
        } else {
            askLocation.launch(
                arrayOf(
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                )
            )
        }
    }

    /** Takes a fresh location while the app is open, then refreshes the weather. */
    private fun refreshWithLocation() {
        lifecycleScope.launch {
            LocationHelper.updateFromDevice(this@MainActivity)
            WeatherScheduler.refreshNow(this@MainActivity)
            repeat(4) {
                delay(3_000)
                refreshTick.intValue++
            }
        }
    }
}
