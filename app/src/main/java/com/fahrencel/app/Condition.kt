package com.fahrencel.app

import androidx.annotation.DrawableRes

/** Every weather state the widget can show, with its icon and icon color. */
enum class Condition(
    val label: String,
    @DrawableRes val icon: Int,
    val tint: Long,
    @DrawableRes val filledIcon: Int,
) {
    CLEAR("Sunny", R.drawable.ic_sunny, 0xFFFFC94D, R.drawable.ic_sunny_filled),
    CLEAR_NIGHT("Clear", R.drawable.ic_clear_night, 0xFFD6DCFF, R.drawable.ic_clear_night_filled),
    MOSTLY_CLEAR("Mostly sunny", R.drawable.ic_sunny, 0xFFFFC94D, R.drawable.ic_sunny_filled),
    MOSTLY_CLEAR_NIGHT("Mostly clear", R.drawable.ic_clear_night, 0xFFD6DCFF, R.drawable.ic_clear_night_filled),
    PARTLY_CLOUDY("Partly cloudy", R.drawable.ic_partly_cloudy_day, 0xFFFFD27A, R.drawable.ic_partly_cloudy_day_filled),
    PARTLY_CLOUDY_NIGHT("Partly cloudy", R.drawable.ic_partly_cloudy_night, 0xFFD6DCFF, R.drawable.ic_partly_cloudy_night_filled),
    CLOUDY("Cloudy", R.drawable.ic_cloud, 0xFFCFD8DC, R.drawable.ic_cloud_filled),
    FOG("Foggy", R.drawable.ic_foggy, 0xFFB0BEC5, R.drawable.ic_foggy_filled),
    HAZE("Hazy", R.drawable.ic_mist, 0xFFD7C4A8, R.drawable.ic_mist_filled),
    DUST("Dusty", R.drawable.ic_blur_on, 0xFFE0B771, R.drawable.ic_blur_on_filled),
    WINDY("Windy", R.drawable.ic_air, 0xFFB2EBF2, R.drawable.ic_air_filled),
    DRIZZLE("Drizzle", R.drawable.ic_rainy_light, 0xFF8EC5FF, R.drawable.ic_rainy_light_filled),
    SHOWERS("Showers", R.drawable.ic_rainy, 0xFF7FB8FF, R.drawable.ic_rainy_filled),
    RAIN("Rain", R.drawable.ic_rainy, 0xFF7FB8FF, R.drawable.ic_rainy_filled),
    HEAVY_RAIN("Heavy rain", R.drawable.ic_rainy_heavy, 0xFF5C9DFF, R.drawable.ic_rainy_heavy_filled),
    FREEZING_RAIN("Freezing rain", R.drawable.ic_weather_mix, 0xFFB3E5FC, R.drawable.ic_weather_mix_filled),
    SNOW("Snow", R.drawable.ic_weather_snowy, 0xFFE3F2FD, R.drawable.ic_weather_snowy_filled),
    HEAVY_SNOW("Heavy snow", R.drawable.ic_snowing_heavy, 0xFFE3F2FD, R.drawable.ic_snowing_heavy_filled),
    THUNDERSTORM("Thunderstorm", R.drawable.ic_thunderstorm, 0xFFC7A4FF, R.drawable.ic_thunderstorm_filled),
    HAIL("Storm with hail", R.drawable.ic_weather_hail, 0xFFC7A4FF, R.drawable.ic_weather_hail_filled);

    fun iconFor(filled: Boolean): Int = if (filled) filledIcon else icon

    companion object {
        /** Open-Meteo uses the WMO weather code table. */
        fun fromCode(code: Int, isDay: Boolean): Condition = when (code) {
            0 -> if (isDay) CLEAR else CLEAR_NIGHT
            1 -> if (isDay) MOSTLY_CLEAR else MOSTLY_CLEAR_NIGHT
            2 -> if (isDay) PARTLY_CLOUDY else PARTLY_CLOUDY_NIGHT
            3 -> CLOUDY
            45, 48 -> FOG
            51, 53, 55 -> DRIZZLE
            56, 57, 66, 67 -> FREEZING_RAIN
            61, 63 -> RAIN
            65, 82 -> HEAVY_RAIN
            80, 81 -> SHOWERS
            71, 73, 77, 85 -> SNOW
            75, 86 -> HEAVY_SNOW
            95 -> THUNDERSTORM
            96, 99 -> HAIL
            else -> CLOUDY
        }

        /**
         * Current conditions. The weather code has no dust, haze or wind states, so when
         * nothing is falling from the sky we check air quality, visibility and wind.
         */
        fun current(
            code: Int,
            isDay: Boolean,
            windKmh: Double,
            gustKmh: Double,
            visibilityM: Double,
            pm25: Double,
            dust: Double,
        ): Condition {
            val base = fromCode(code, isDay)
            if (code > 3) return base
            return when {
                dust >= 150 -> DUST
                pm25 >= 55 || visibilityM < 2000 -> HAZE
                gustKmh >= 55 || windKmh >= 40 -> WINDY
                else -> base
            }
        }

        fun isSnow(code: Int) = code in 71..77 || code == 85 || code == 86
        fun isRain(code: Int) = code in 51..67 || code in 80..82
    }
}
