package com.example.dualweather

import androidx.annotation.DrawableRes

enum class AlertLevel { INFO, WARN, DANGER }

data class Alert(val text: String, val level: AlertLevel, @DrawableRes val icon: Int)

/**
 * Picks the single most important heads-up from the forecast. These are our own
 * forecast-based notes, not official government warnings.
 */
object Alerts {

    /** [cFirst]: list °C before °F, matching the unit order setting. */
    fun compute(d: WeatherData, cFirst: Boolean = false): Alert? {
        val next12 = d.hours.take(12)
        val next24 = d.hours.take(24)
        val tomorrow = d.days.firstOrNull()

        // 1. Thunderstorms
        if (d.code >= 95) {
            return Alert("Thunderstorms now", AlertLevel.DANGER, R.drawable.ic_thunderstorm)
        }
        next12.drop(1).firstOrNull { it.code >= 95 }?.let {
            return Alert(
                "Thunderstorms possible around ${d.clock(it.time)}",
                AlertLevel.DANGER, R.drawable.ic_thunderstorm,
            )
        }

        // 2. Heavy rain / flooding
        if (d.today.precipMm >= 30) {
            return Alert("Heavy rain today, flooding possible", AlertLevel.DANGER, R.drawable.ic_flood)
        }
        if (tomorrow != null && tomorrow.precipMm >= 30) {
            return Alert("Heavy rain tomorrow, flooding possible", AlertLevel.WARN, R.drawable.ic_flood)
        }

        // 2b. Rivers rising fast
        d.river?.takeIf { it.rising && it.peakM3s >= 20 }?.let {
            return Alert("River rising, highest flow ${it.peakDay}", AlertLevel.WARN, R.drawable.ic_flood)
        }

        // 3. Heat
        heat(d.today, "today", cFirst)?.let { return it }
        tomorrow?.let { heat(it, "tomorrow", cFirst) }?.let { return it }

        // 4. Air quality
        if (d.condition == Condition.DUST) {
            return Alert("Dusty air, limit time outdoors", AlertLevel.WARN, R.drawable.ic_blur_on)
        }
        d.air?.usAqi?.let { aqi ->
            when {
                aqi >= 201 -> return Alert("Very unhealthy air (AQI $aqi), stay indoors", AlertLevel.DANGER, R.drawable.ic_mist)
                aqi >= 151 -> return Alert("Unhealthy air (AQI $aqi)", AlertLevel.WARN, R.drawable.ic_mist)
                else -> Unit
            }
        }
        if (d.pm25 >= 55) {
            return Alert("Poor air quality (haze or smoke)", AlertLevel.WARN, R.drawable.ic_mist)
        }

        // 5. Snow
        next24.firstOrNull { Condition.isSnow(it.code) }?.let {
            val text = if (it == next24.first()) "Snow now" else "Snow expected around ${d.clock(it.time)}"
            return Alert(text, AlertLevel.WARN, R.drawable.ic_weather_snowy)
        }

        // 6. Freezing temperatures
        next24.firstOrNull { it.tempC <= 0.0 }?.let { first ->
            val low = next24.minOf { it.tempC }
            return Alert(
                "Freezing from ${d.clock(first.time)}, low ${Units.both(low, cFirst)}",
                AlertLevel.WARN, R.drawable.ic_severe_cold,
            )
        }

        // 7. Strong wind
        if (d.today.gustKmh >= 60) {
            return Alert(
                "Strong winds today, gusts ${Units.wind(d.today.gustKmh)}",
                AlertLevel.WARN, R.drawable.ic_air,
            )
        }
        if (tomorrow != null && tomorrow.gustKmh >= 60) {
            return Alert(
                "Strong winds tomorrow, gusts ${Units.wind(tomorrow.gustKmh)}",
                AlertLevel.INFO, R.drawable.ic_air,
            )
        }

        // 7b. Rough seas (coastal places)
        d.marine?.takeIf { it.maxWaveTodayM >= 3.0 }?.let {
            return Alert(
                "Rough seas today, waves up to ${Units.length(it.maxWaveTodayM, cFirst, decimals = true)}",
                AlertLevel.WARN, R.drawable.ic_flood,
            )
        }

        // 8. Rain starting or stopping
        if (!Condition.isRain(d.code)) {
            next12.drop(1).firstOrNull { it.precipProb >= 50 }?.let {
                return Alert("Rain likely from ${d.clock(it.time)}", AlertLevel.INFO, R.drawable.ic_umbrella)
            }
        } else {
            next12.drop(1).firstOrNull { it.precipProb < 30 && !Condition.isRain(it.code) }?.let {
                return Alert("Rain should ease by ${d.clock(it.time)}", AlertLevel.INFO, R.drawable.ic_umbrella)
            }
        }

        // 9. Big temperature swing tomorrow
        if (tomorrow != null) {
            val diff = tomorrow.hiC - d.today.hiC
            if (diff <= -6) {
                return Alert(
                    "Much colder tomorrow, high ${Units.both(tomorrow.hiC, cFirst)}",
                    AlertLevel.INFO, R.drawable.ic_severe_cold,
                )
            }
            if (diff >= 6) {
                return Alert(
                    "Much warmer tomorrow, high ${Units.both(tomorrow.hiC, cFirst)}",
                    AlertLevel.INFO, R.drawable.ic_sunny,
                )
            }
        }

        // 9b. Air that bothers sensitive people, high pollen
        d.air?.usAqi?.takeIf { it >= 101 }?.let {
            return Alert("Air may bother sensitive groups (AQI $it)", AlertLevel.INFO, R.drawable.ic_mist)
        }
        d.air?.pollen?.maxByOrNull { it.value }?.takeIf { it.value >= 50 }?.let {
            return Alert("High ${it.key} pollen today", AlertLevel.INFO, R.drawable.ic_blur_on)
        }

        // 10. UV
        if (d.isDay && d.today.uvMax >= 8) {
            return Alert(
                "Very high UV today (index ${Math.round(d.today.uvMax)})",
                AlertLevel.INFO, R.drawable.ic_sunny,
            )
        }
        return null
    }

    private fun heat(day: Day, whenText: String, cFirst: Boolean): Alert? = when {
        day.hiC >= 40 -> Alert(
            "Severe heat $whenText, high ${Units.both(day.hiC, cFirst)}",
            AlertLevel.DANGER, R.drawable.ic_emergency_heat,
        )
        day.hiC >= 35 -> Alert(
            "Extreme heat $whenText, high ${Units.both(day.hiC, cFirst)}",
            AlertLevel.WARN, R.drawable.ic_emergency_heat,
        )
        else -> null
    }
}
