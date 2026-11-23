package com.fahrencel.app

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.Locale

/** A place to show weather for. "here" is the phone's own location. */
data class Place(
    val id: String,
    val name: String,
    val region: String?,
    val country: String?,
    val lat: Double,
    val lon: Double,
) {
    val isHere: Boolean get() = id == Places.HERE

    /** "Miami, Florida, United States" */
    val fullName: String get() = listOfNotNull(name, region?.takeIf { it != name }, country).joinToString(", ")

    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("name", name).put("region", region ?: JSONObject.NULL)
        .put("country", country ?: JSONObject.NULL).put("lat", lat).put("lon", lon)

    companion object {
        fun fromJson(o: JSONObject) = Place(
            id = o.getString("id"),
            name = o.getString("name"),
            region = if (o.isNull("region")) null else o.getString("region"),
            country = if (o.isNull("country")) null else o.getString("country"),
            lat = o.getDouble("lat"),
            lon = o.getDouble("lon"),
        )
    }
}

object Places {
    const val HERE = "here"

    /** Extra cities allowed without Pro. */
    const val FREE_CITY_LIMIT = 2

    fun canAddMore(ctx: Context): Boolean = Pro.isPro(ctx) || saved(ctx).size < FREE_CITY_LIMIT
    private const val PREFS = "places"

    /** Cities the user added (not including "My location"). */
    fun saved(ctx: Context): List<Place> {
        val raw = prefs(ctx).getString("list", null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { Place.fromJson(arr.getJSONObject(it)) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** "My location" first, then saved cities. */
    fun all(ctx: Context): List<Place> = listOf(here(ctx)) + saved(ctx)

    fun here(ctx: Context): Place {
        val (lat, lon) = WeatherStore.loadLocation(ctx) ?: (31.7853 to 35.2122)
        return Place(HERE, WeatherStore.hereLabel(ctx) ?: "My location", null, null, lat, lon)
    }

    fun byId(ctx: Context, id: String): Place? =
        if (id == HERE) here(ctx) else saved(ctx).firstOrNull { it.id == id }

    fun add(ctx: Context, p: Place) {
        val list = saved(ctx).filterNot { it.id == p.id } + p
        write(ctx, list)
    }

    fun remove(ctx: Context, id: String) {
        write(ctx, saved(ctx).filterNot { it.id == id })
        WeatherStore.delete(ctx, id)
    }

    private fun write(ctx: Context, list: List<Place>) {
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        prefs(ctx).edit().putString("list", arr.toString()).apply()
    }

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

/** City search using Open-Meteo's free geocoding service (blocking). */
object PlaceSearch {

    private class Hit(val place: Place, val countryCode: String?)

    private val usStates = mapOf(
        "AL" to "Alabama", "AK" to "Alaska", "AZ" to "Arizona", "AR" to "Arkansas",
        "CA" to "California", "CO" to "Colorado", "CT" to "Connecticut", "DE" to "Delaware",
        "FL" to "Florida", "GA" to "Georgia", "HI" to "Hawaii", "ID" to "Idaho",
        "IL" to "Illinois", "IN" to "Indiana", "IA" to "Iowa", "KS" to "Kansas",
        "KY" to "Kentucky", "LA" to "Louisiana", "ME" to "Maine", "MD" to "Maryland",
        "MA" to "Massachusetts", "MI" to "Michigan", "MN" to "Minnesota", "MS" to "Mississippi",
        "MO" to "Missouri", "MT" to "Montana", "NE" to "Nebraska", "NV" to "Nevada",
        "NH" to "New Hampshire", "NJ" to "New Jersey", "NM" to "New Mexico", "NY" to "New York",
        "NC" to "North Carolina", "ND" to "North Dakota", "OH" to "Ohio", "OK" to "Oklahoma",
        "OR" to "Oregon", "PA" to "Pennsylvania", "RI" to "Rhode Island", "SC" to "South Carolina",
        "SD" to "South Dakota", "TN" to "Tennessee", "TX" to "Texas", "UT" to "Utah",
        "VT" to "Vermont", "VA" to "Virginia", "WA" to "Washington", "WV" to "West Virginia",
        "WI" to "Wisconsin", "WY" to "Wyoming", "DC" to "District of Columbia",
    )

    /** "AZ" -> "Arizona", "USA" -> "United States", anything else unchanged. */
    private fun expand(q: String): String {
        val up = q.trim().uppercase()
        return when (up) {
            "US", "USA", "U.S.", "U.S.A." -> "United States"
            "UK", "U.K." -> "United Kingdom"
            "UAE" -> "United Arab Emirates"
            else -> usStates[up] ?: q.trim()
        }
    }

    private fun matches(h: Hit, qualifiers: List<String>): Boolean = qualifiers.all { q ->
        h.place.region?.contains(q, ignoreCase = true) == true ||
            h.place.country?.contains(q, ignoreCase = true) == true ||
            h.countryCode.equals(q, ignoreCase = true)
    }

    private fun fetch(name: String): List<Hit> {
        val q = URLEncoder.encode(name.trim(), "UTF-8")
        if (q.isEmpty()) return emptyList()
        val body = WeatherRepository.httpGet(
            "https://geocoding-api.open-meteo.com/v1/search?name=$q&count=20&language=en&format=json"
        )
        val arr = JSONObject(body).optJSONArray("results") ?: return emptyList()
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Hit(
                place = Place(
                    id = if (o.has("id")) "geo_" + o.getLong("id")
                    else "geo_%.3f_%.3f".format(Locale.US, o.getDouble("latitude"), o.getDouble("longitude")),
                    name = o.getString("name"),
                    region = if (o.isNull("admin1")) null else o.getString("admin1"),
                    country = if (o.isNull("country")) null else o.getString("country"),
                    lat = o.getDouble("latitude"),
                    lon = o.getDouble("longitude"),
                ),
                countryCode = if (o.isNull("country_code")) null else o.getString("country_code"),
            )
        }
    }

    /**
     * Understands "Tucson", "Tucson, Arizona", "Tucson, AZ" and "Tucson Arizona".
     * The weather service only searches by place name, so anything after the name
     * (state, country) is used to put the right matches first.
     */
    fun search(query: String): List<Place> {
        val raw = query.trim()
        if (raw.length < 2) return emptyList()
        val parts = raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        val name = parts.firstOrNull() ?: return emptyList()
        var qualifiers = parts.drop(1).map { expand(it) }
        var hits = fetch(name)

        // No comma, several words ("Tucson Arizona"): try the first words as the name
        // and the rest as the state or country.
        if (parts.size == 1) {
            val words = name.split(Regex("\\s+"))
            val exact = hits.any { it.place.name.equals(name, ignoreCase = true) }
            if (words.size > 1 && !exact) {
                for (cut in words.size - 1 downTo 1) {
                    val shortName = words.take(cut).joinToString(" ")
                    val rest = listOf(expand(words.drop(cut).joinToString(" ")))
                    val tryHits = fetch(shortName)
                    if (tryHits.any { matches(it, rest) }) {
                        hits = tryHits
                        qualifiers = rest
                        break
                    }
                }
            }
        }

        if (qualifiers.isEmpty()) return hits.map { it.place }
        val (good, others) = hits.partition { matches(it, qualifiers) }
        return (good + others).map { it.place }
    }
}

/** Which place each widget on the home screen shows. */
object WidgetPrefs {
    private const val PREFS = "widgets"

    fun placeFor(ctx: Context, appWidgetId: Int): String {
        val id = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString("place_$appWidgetId", null) ?: return Places.HERE
        // If the city was removed, fall back to "My location".
        return if (id == Places.HERE || Places.saved(ctx).any { it.id == id }) id else Places.HERE
    }

    fun setPlace(ctx: Context, appWidgetId: Int, placeId: String) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("place_$appWidgetId", placeId).apply()
    }

    /** Forgets a widget's settings once it is removed from the home screen. */
    fun forget(ctx: Context, appWidgetId: Int) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .remove("place_$appWidgetId").remove("emph_$appWidgetId").apply()
        clearPendingEdit(ctx, appWidgetId)
    }

    /** Remembers a widget that was just added from the app, so its editor can open next. */
    fun setPendingEdit(ctx: Context, appWidgetId: Int) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt("pending_edit", appWidgetId).apply()
    }

    /** Returns the waiting widget ID (once), or null. */
    fun takePendingEdit(ctx: Context): Int? {
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val id = p.getInt("pending_edit", 0)
        if (id == 0) return null
        p.edit().remove("pending_edit").apply()
        return id
    }

    fun clearPendingEdit(ctx: Context, appWidgetId: Int) {
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (p.getInt("pending_edit", 0) == appWidgetId) p.edit().remove("pending_edit").apply()
    }

    /** This widget's own unit order (Pro), or null to follow the app setting. */
    fun emphasisFor(ctx: Context, appWidgetId: Int): Emphasis? =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("emph_$appWidgetId", null)
            ?.let { runCatching { Emphasis.valueOf(it) }.getOrNull() }

    fun setEmphasis(ctx: Context, appWidgetId: Int, e: Emphasis?) {
        val edit = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        if (e == null) edit.remove("emph_$appWidgetId") else edit.putString("emph_$appWidgetId", e.name)
        edit.apply()
    }
}

/** Asks the launcher to drop a new widget on the home screen for a given place. */
object WidgetPin {
    const val EXTRA_PLACE = "place_id"

    fun supported(ctx: Context): Boolean =
        AppWidgetManager.getInstance(ctx).isRequestPinAppWidgetSupported

    fun request(ctx: Context, placeId: String, size: WidgetSize = WidgetSize.S4x2): Boolean {
        val mgr = AppWidgetManager.getInstance(ctx)
        if (!mgr.isRequestPinAppWidgetSupported) return false
        val intent = Intent(ctx, PinResultReceiver::class.java).putExtra(EXTRA_PLACE, placeId)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
        val callback = PendingIntent.getBroadcast(ctx, (placeId + size.name).hashCode(), intent, flags)
        return mgr.requestPinAppWidget(ComponentName(ctx, size.receiver), null, callback)
    }
}

/** Called after a widget is pinned: remember which place it should show. */
class PinResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        val placeId = intent.getStringExtra(WidgetPin.EXTRA_PLACE) ?: return
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return
        WidgetPrefs.setPlace(context, widgetId, placeId)
        // Open this widget's editor when the app is back on screen.
        WidgetPrefs.setPendingEdit(context, widgetId)
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                WidgetUpdater.refresh(context, widgetId)
            } finally {
                pending.finish()
            }
        }
    }
}
