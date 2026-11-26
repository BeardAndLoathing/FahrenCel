package com.fahrencel.app

import android.content.Context
import android.text.format.DateFormat
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** All temperatures are in Celsius; other units are derived for display (see Units). */
data class Hour(
    val time: Long,
    val tempC: Double,
    val precipProb: Int,
    val code: Int,
    val isDay: Boolean,
)

data class Day(
    val time: Long,
    val code: Int,
    val hiC: Double,
    val loC: Double,
    val precipMm: Double,
    val precipProb: Int,
    val gustKmh: Double,
    val uvMax: Double,
    /** Epoch seconds; 0 if unknown. */
    val sunrise: Long = 0L,
    val sunset: Long = 0L,
)

/** Air quality right now. Indices are null where not available. */
data class AirQuality(
    val usAqi: Int?,
    val euAqi: Int?,
    val pm25: Double,
    val pm10: Double,
    val ozone: Double,
    val no2: Double,
    val dust: Double,
    /** Pollen in grains/m³ by plant (Europe and nearby, including Israel). Only plants with data. */
    val pollen: Map<String, Double>,
)

/** Sea conditions, for places near the coast. */
data class Marine(
    val waveM: Double,
    val periodS: Double,
    val seaTempC: Double,
    val maxWaveTodayM: Double,
)

/** Flow of the nearest river (GloFAS model), next several days. */
data class River(
    val todayM3s: Double,
    val peakM3s: Double,
    val peakDay: String,
    val days: List<Pair<String, Double>>,
) {
    val rising: Boolean get() = peakM3s >= todayM3s * 1.5 && peakM3s > todayM3s + 5
}

data class WeatherData(
    val tempC: Double,
    val feelsC: Double,
    val code: Int,
    val isDay: Boolean,
    val windKmh: Double,
    val gustKmh: Double,
    val visibilityM: Double,
    val pm25: Double,
    val dust: Double,
    val condition: Condition,
    val today: Day,
    /** Days after today. */
    val days: List<Day>,
    /** Hourly points starting with the current hour. */
    val hours: List<Hour>,
    val utcOffsetSec: Int,
    val city: String?,
    val area: String?,
    val updatedAtMillis: Long,
    /** Height above sea level in meters (from the forecast; NaN if unknown). */
    val elevationM: Double = Double.NaN,
    val air: AirQuality? = null,
    val marine: Marine? = null,
    val river: River? = null,
) {
    private val zone: ZoneOffset get() = ZoneOffset.ofTotalSeconds(utcOffsetSec)

    fun clock(epochSec: Long): String =
        Instant.ofEpochSecond(epochSec).atOffset(zone).format(HHMM)

    fun dayName(epochSec: Long): String =
        Instant.ofEpochSecond(epochSec).atOffset(zone).dayOfWeek
            .getDisplayName(TextStyle.SHORT, Locale.getDefault())

    /**
     * Day and month in numbers, in the phone's own order: 11/25 in the US, 25/11 in Israel,
     * 25.11 in Germany. Worked out each time so a language change shows up right away.
     */
    fun shortDate(epochSec: Long): String {
        val locale = Locale.getDefault()
        val pattern = DateFormat.getBestDateTimePattern(locale, "Md")
        return Instant.ofEpochSecond(epochSec).atOffset(zone).format(DateTimeFormatter.ofPattern(pattern, locale))
    }

    fun updatedClock(): String = clock(updatedAtMillis / 1000)

    /** "Nachlaot, Jerusalem" when a neighborhood is known, else just the city. */
    val placeLabel: String?
        get() = if (area != null && city != null && area != city) "$area, $city" else city ?: area

    private companion object {
        val HHMM: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    }
}

object WeatherRepository {

    /** Raw forecast JSON from Open-Meteo (blocking). */
    fun fetchForecast(lat: Double, lon: Double): String = httpGet(
        "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
            "&current=temperature_2m,apparent_temperature,weather_code,is_day," +
            "wind_speed_10m,wind_gusts_10m" +
            "&hourly=temperature_2m,precipitation_probability,weather_code,is_day,visibility" +
            "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_sum," +
            "precipitation_probability_max,wind_gusts_10m_max,uv_index_max,sunrise,sunset" +
            "&timezone=auto&timeformat=unixtime&forecast_days=6"
    )

    /** Current air quality and pollen. Optional: null on any failure. */
    fun fetchAir(lat: Double, lon: Double): String? = try {
        httpGet(
            "https://air-quality-api.open-meteo.com/v1/air-quality?latitude=$lat&longitude=$lon" +
                "&current=us_aqi,european_aqi,pm2_5,pm10,ozone,nitrogen_dioxide,dust," +
                "alder_pollen,birch_pollen,grass_pollen,mugwort_pollen,olive_pollen,ragweed_pollen" +
                "&timezone=auto"
        )
    } catch (e: Exception) {
        null
    }

    /** Waves and sea temperature. Returns "" when the place isn't near the sea, null on failure. */
    fun fetchMarine(lat: Double, lon: Double): String? = try {
        httpGet(
            "https://marine-api.open-meteo.com/v1/marine?latitude=$lat&longitude=$lon" +
                "&current=wave_height,wave_period,sea_surface_temperature" +
                "&daily=wave_height_max&timezone=auto&forecast_days=1"
        )
    } catch (e: IOException) {
        // The service answers 400 for places with no sea data. Other errors (e.g. 429) are failures.
        if (e.message in NO_DATA) "" else null
    } catch (e: Exception) {
        null
    }

    /** Nearest river's flow for the next week. "" when there's no river data, null on failure. */
    fun fetchFlood(lat: Double, lon: Double): String? = try {
        httpGet(
            "https://flood-api.open-meteo.com/v1/flood?latitude=$lat&longitude=$lon" +
                "&daily=river_discharge&forecast_days=7"
        )
    } catch (e: IOException) {
        if (e.message in NO_DATA) "" else null
    } catch (e: Exception) {
        null
    }

    /** Replies that mean "no data for this place", as opposed to a failed request. */
    private val NO_DATA = setOf("HTTP 400", "HTTP 404")

    fun httpGet(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 15_000
        conn.readTimeout = 15_000
        try {
            if (conn.responseCode != 200) throw IOException("HTTP ${conn.responseCode}")
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    fun parse(
        forecast: String,
        air: String?,
        city: String?,
        area: String?,
        updatedAtMillis: Long,
        nowSec: Long = System.currentTimeMillis() / 1000,
        marine: String? = null,
        flood: String? = null,
    ): WeatherData {
        val root = JSONObject(forecast)
        val offset = root.optInt("utc_offset_seconds", 0)
        val cur = root.getJSONObject("current")

        // Hourly
        val h = root.getJSONObject("hourly")
        val hTime = h.getJSONArray("time")
        val hTemp = h.getJSONArray("temperature_2m")
        val hProb = h.getJSONArray("precipitation_probability")
        val hCode = h.getJSONArray("weather_code")
        val hDay = h.getJSONArray("is_day")
        val hVis = h.optJSONArray("visibility")
        val firstHour = (0 until hTime.length()).firstOrNull { hTime.getLong(it) > nowSec - 3600 } ?: 0
        val hours = (firstHour until hTime.length()).take(25).map { i ->
            Hour(
                time = hTime.getLong(i),
                tempC = hTemp.optDouble(i, Double.NaN),
                precipProb = hProb.optInt(i, 0),
                code = hCode.optInt(i, 0),
                isDay = hDay.optInt(i, 1) == 1,
            )
        }
        val visibility = hVis?.optDouble(firstHour, Double.NaN) ?: Double.NaN

        // Daily
        val d = root.getJSONObject("daily")
        val dTime = d.getJSONArray("time")
        val allDays = (0 until dTime.length()).map { i ->
            Day(
                time = dTime.getLong(i),
                code = d.getJSONArray("weather_code").optInt(i, 0),
                hiC = d.getJSONArray("temperature_2m_max").optDouble(i, Double.NaN),
                loC = d.getJSONArray("temperature_2m_min").optDouble(i, Double.NaN),
                precipMm = d.getJSONArray("precipitation_sum").optDouble(i, 0.0),
                precipProb = d.getJSONArray("precipitation_probability_max").optInt(i, 0),
                gustKmh = d.getJSONArray("wind_gusts_10m_max").optDouble(i, 0.0),
                uvMax = d.getJSONArray("uv_index_max").optDouble(i, 0.0),
                sunrise = d.optJSONArray("sunrise")?.optLong(i, 0L) ?: 0L,
                sunset = d.optJSONArray("sunset")?.optLong(i, 0L) ?: 0L,
            )
        }

        // Daily times are local midnights; the last one already started is today, even in an old cached forecast.
        val todayIdx = allDays.indexOfLast { it.time <= nowSec }.coerceAtLeast(0)

        // Air quality (optional)
        val airQ = try {
            parseAir(air, nowSec)
        } catch (e: Exception) {
            null
        }
        val pm25 = airQ?.pm25 ?: Double.NaN
        val dust = airQ?.dust ?: Double.NaN

        val code = cur.optInt("weather_code", 0)
        val isDay = cur.optInt("is_day", 1) == 1
        val wind = cur.optDouble("wind_speed_10m", 0.0)
        val gust = cur.optDouble("wind_gusts_10m", 0.0)

        return WeatherData(
            tempC = cur.getDouble("temperature_2m"),
            feelsC = cur.optDouble("apparent_temperature", cur.getDouble("temperature_2m")),
            code = code,
            isDay = isDay,
            windKmh = wind,
            gustKmh = gust,
            visibilityM = visibility,
            pm25 = pm25,
            dust = dust,
            condition = Condition.current(code, isDay, wind, gust, visibility, pm25, dust),
            today = allDays[todayIdx],
            days = allDays.drop(todayIdx + 1),
            hours = hours,
            utcOffsetSec = offset,
            city = city,
            area = area,
            updatedAtMillis = updatedAtMillis,
            elevationM = root.optDouble("elevation", Double.NaN),
            air = airQ,
            marine = try { parseMarine(marine) } catch (e: Exception) { null },
            river = try { parseRiver(flood) } catch (e: Exception) { null },
        )
    }

    private fun JSONObject.num(key: String): Double = optDouble(key, Double.NaN)

    private fun parseAir(air: String?, nowSec: Long): AirQuality? {
        if (air.isNullOrEmpty()) return null
        val o = JSONObject(air)
        val c = o.optJSONObject("current")
        if (c != null) {
            val pollen = listOf("olive", "grass", "birch", "alder", "mugwort", "ragweed")
                .mapNotNull { k -> c.num("${k}_pollen").takeIf { !it.isNaN() }?.let { k to it } }
                .toMap()
            return AirQuality(
                usAqi = c.num("us_aqi").takeIf { !it.isNaN() }?.let { Math.round(it).toInt() },
                euAqi = c.num("european_aqi").takeIf { !it.isNaN() }?.let { Math.round(it).toInt() },
                pm25 = c.num("pm2_5"),
                pm10 = c.num("pm10"),
                ozone = c.num("ozone"),
                no2 = c.num("nitrogen_dioxide"),
                dust = c.num("dust"),
                pollen = pollen,
            )
        }
        // Older saved format (hourly pm2.5 and dust only).
        val a = o.optJSONObject("hourly") ?: return null
        val t = a.getJSONArray("time")
        val idx = (0 until t.length()).lastOrNull { t.getLong(it) <= nowSec } ?: 0
        return AirQuality(
            usAqi = null, euAqi = null,
            pm25 = a.getJSONArray("pm2_5").optDouble(idx, Double.NaN),
            pm10 = Double.NaN, ozone = Double.NaN, no2 = Double.NaN,
            dust = a.getJSONArray("dust").optDouble(idx, Double.NaN),
            pollen = emptyMap(),
        )
    }

    private fun parseMarine(body: String?): Marine? {
        if (body.isNullOrEmpty()) return null
        val o = JSONObject(body)
        val c = o.optJSONObject("current") ?: return null
        val wave = c.num("wave_height")
        val sst = c.num("sea_surface_temperature")
        if (wave.isNaN() && sst.isNaN()) return null // inland
        val maxToday = o.optJSONObject("daily")?.optJSONArray("wave_height_max")?.optDouble(0, Double.NaN) ?: Double.NaN
        return Marine(wave, c.num("wave_period"), sst, if (maxToday.isNaN()) wave else maxToday)
    }

    private fun parseRiver(body: String?): River? {
        if (body.isNullOrEmpty()) return null
        val d = JSONObject(body).optJSONObject("daily") ?: return null
        val times = d.getJSONArray("time")
        val flows = d.getJSONArray("river_discharge")
        val days = (0 until times.length()).mapNotNull { i ->
            val v = flows.optDouble(i, Double.NaN)
            if (v.isNaN()) null else {
                val name = LocalDate.parse(times.getString(i)).dayOfWeek
                    .getDisplayName(TextStyle.SHORT, Locale.getDefault())
                name to v
            }
        }
        if (days.isEmpty()) return null
        val peak = days.maxBy { it.second }
        // Ignore trickles (no real river nearby).
        if (peak.second < 1.0) return null
        return River(days.first().second, peak.second, peak.first, days)
    }
}

/** Stores the raw API responses per place so widgets can draw instantly and offline. */
object WeatherStore {
    private const val PREFS = "weather"

    // "My location" keeps the original keys so older saved data still loads.
    private fun key(base: String, placeId: String) = if (placeId == Places.HERE) base else "${base}_$placeId"

    fun save(ctx: Context, placeId: String, forecast: String) {
        prefs(ctx).edit()
            .putString(key("forecast", placeId), forecast)
            .putLong(key("updated", placeId), System.currentTimeMillis())
            .apply()
    }

    /** Saves air / marine / flood data. An empty body means "none here"; null means the fetch failed. */
    fun saveExtra(ctx: Context, placeId: String, kind: String, body: String?) {
        if (body == null) return
        prefs(ctx).edit()
            .putString(key(kind, placeId), body)
            .putLong(key("${kind}_t", placeId), System.currentTimeMillis())
            .apply()
    }

    /** Minutes since [kind] was last fetched, and whether that fetch found nothing. */
    fun extraAgeMin(ctx: Context, placeId: String, kind: String): Long {
        val t = prefs(ctx).getLong(key("${kind}_t", placeId), 0L)
        return if (t == 0L) Long.MAX_VALUE else (System.currentTimeMillis() - t) / 60_000
    }

    fun extraIsEmpty(ctx: Context, placeId: String, kind: String): Boolean =
        prefs(ctx).getString(key(kind, placeId), null)?.isEmpty() == true

    fun load(ctx: Context, placeId: String = Places.HERE): WeatherData? {
        val p = prefs(ctx)
        // Older versions saved Jerusalem's weather here when the location was unknown; hide it.
        if (placeId == Places.HERE && loadLocation(ctx) == null) return null
        val forecast = p.getString(key("forecast", placeId), null) ?: return null
        val (city, area) = if (placeId == Places.HERE) {
            p.getString("city", null) to p.getString("area", null)
        } else {
            (Places.saved(ctx).firstOrNull { it.id == placeId }?.name) to null
        }
        return try {
            WeatherRepository.parse(
                forecast = forecast,
                air = p.getString(key("air", placeId), null),
                city = city,
                area = area,
                updatedAtMillis = p.getLong(key("updated", placeId), 0L),
                // Sea and river data (and their warnings) only with Pro.
                marine = if (Pro.isPro(ctx)) p.getString(key("marine", placeId), null) else null,
                flood = if (Pro.isPro(ctx)) p.getString(key("flood", placeId), null) else null,
            )
        } catch (e: Exception) {
            null
        }
    }

    fun delete(ctx: Context, placeId: String) {
        if (placeId == Places.HERE) return
        prefs(ctx).edit()
            .remove(key("forecast", placeId))
            .remove(key("air", placeId))
            .remove(key("updated", placeId))
            .remove(key("marine", placeId))
            .remove(key("flood", placeId))
            .remove(key("air_t", placeId))
            .remove(key("marine_t", placeId))
            .remove(key("flood_t", placeId))
            .apply()
    }

    /** "Nachlaot, Jerusalem" for the phone's location, if known. */
    fun hereLabel(ctx: Context): String? {
        val p = prefs(ctx)
        val city = p.getString("city", null)
        val area = p.getString("area", null)
        return if (area != null && city != null && area != city) "$area, $city" else city ?: area
    }

    fun saveLocation(ctx: Context, lat: Double, lon: Double) {
        prefs(ctx).edit().putString("lat", lat.toString()).putString("lon", lon.toString()).apply()
    }

    fun loadLocation(ctx: Context): Pair<Double, Double>? {
        val p = prefs(ctx)
        val lat = p.getString("lat", null)?.toDoubleOrNull() ?: return null
        val lon = p.getString("lon", null)?.toDoubleOrNull() ?: return null
        return lat to lon
    }

    /** Place name cached per ~1 km grid square, so we don't geocode on every refresh. */
    fun cachedPlace(ctx: Context, key: String): Pair<String?, String?>? {
        val p = prefs(ctx)
        if (p.getString("placeKey", null) != key) return null
        return p.getString("city", null) to p.getString("area", null)
    }

    fun savePlace(ctx: Context, key: String, city: String?, area: String?) {
        prefs(ctx).edit()
            .putString("placeKey", key)
            .putString("city", city)
            .putString("area", area)
            .apply()
    }

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

/** User settings. */
object AppPrefs {
    private const val PREFS = "settings"

    fun kelvin(ctx: Context): Boolean = prefs(ctx).getBoolean("kelvin", false)

    fun setKelvin(ctx: Context, on: Boolean) {
        prefs(ctx).edit().putBoolean("kelvin", on).apply()
    }

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

object Units {
    fun cToF(c: Double): Double = c * 9.0 / 5.0 + 32.0

    fun f(c: Double) = "${r(cToF(c))}°F"
    fun c(c: Double) = "${r(c)}°C"
    fun k(c: Double) = "${r(c + 273.15)} K"

    /** "49°F · 10°C", or "10°C · 49°F" when Celsius comes first. */
    fun both(t: Double, cFirst: Boolean = false) =
        if (cFirst) "${c(t)} · ${f(t)}" else "${f(t)} · ${c(t)}"

    /** °F and °C always (in the chosen order), plus K if the user turned it on. */
    fun line(t: Double, kelvin: Boolean, sep: String = "  ", cFirst: Boolean = false): String {
        val units = if (cFirst) listOf(c(t), f(t)) else listOf(f(t), c(t))
        return (if (kelvin) units + k(t) else units).joinToString(sep)
    }

    /** The leading and the second temperature, e.g. ("49°F", "10°C"). */
    fun pair(t: Double, cFirst: Boolean): Pair<String, String> =
        if (cFirst) c(t) to f(t) else f(t) to c(t)

    /** High/low pair for a day, in the chosen order. */
    fun hiLoPair(hi: Double, lo: Double, cFirst: Boolean): Pair<String, String> =
        if (cFirst) hiLoC(hi, lo) to hiLoF(hi, lo) else hiLoF(hi, lo) to hiLoC(hi, lo)

    fun hiLoF(hi: Double, lo: Double) = "${r(cToF(hi))}/${r(cToF(lo))}°F"
    fun hiLoC(hi: Double, lo: Double) = "${r(hi)}/${r(lo)}°C"

    fun wind(kmh: Double) = "${r(kmh)} km/h · ${r(kmh * 0.621371)} mph"

    /** Meters and feet, metric first when °C comes first. */
    fun length(m: Double, metricFirst: Boolean, decimals: Boolean = false): String {
        val meters = if (decimals) String.format(Locale.getDefault(), "%.1f m", m) else "${r(m)} m"
        val feet = "${r(m * 3.28084)} ft"
        return if (metricFirst) "$meters · $feet" else "$feet · $meters"
    }

    /** River flow in cubic meters and cubic feet per second. */
    fun flow(m3s: Double, metricFirst: Boolean): String {
        val metric = "${r(m3s)} m³/s"
        val imperial = "${r(m3s * 35.3147)} ft³/s"
        return if (metricFirst) "$metric · $imperial" else "$imperial · $metric"
    }

    private fun r(v: Double): String = if (v.isNaN()) "–" else Math.round(v).toString()
}
