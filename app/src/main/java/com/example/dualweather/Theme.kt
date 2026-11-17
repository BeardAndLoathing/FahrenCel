package com.example.dualweather

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.net.Uri
import androidx.compose.ui.graphics.Color
import androidx.core.graphics.ColorUtils
import androidx.exifinterface.media.ExifInterface
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.Random
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

enum class TextMode { AUTO, LIGHT, DARK }

/** Where the background comes from. */
enum class BgMode(val label: String) {
    COLORS("Colors"),
    SKY("Live sky"),
    TEMPERATURE("Temperature"),
    WALLPAPER("Match wallpaper"),
    PHOTO("Photo"),
}

/** Decorative shapes drawn over the background. */
enum class Decor(val label: String) {
    NONE("None"),
    SWOOSH("Swoosh"),
    DOUBLE_SWOOSH("Double swoosh"),
    WAVE("Waves"),
    GLOW("Glow"),
    MOUNTAINS("Mountains"),
    SKYLINE("City skyline"),
    CLOUDS("Clouds"),
    RAYS("Sun rays"),
    BOKEH("Soft dots"),
    STARS("Stars"),
    STRIPES("Stripes"),
    GRAIN("Grain"),
}

enum class BorderStyle(val label: String) {
    NONE("None"),
    THIN("Thin"),
    THICK("Thick"),
    GLOW("Glow"),
}

/** How a widget looks. */
data class WidgetTheme(
    val id: String,
    val name: String,
    /** For COLORS: one color = solid; two or three = gradient. */
    val colors: List<Int>,
    /** Gradient direction in degrees: 0 = left to right, 90 = top to bottom. */
    val angle: Int = 135,
    /** 0.15 (mostly see-through) to 1.0 (solid). */
    val opacity: Float = 0.92f,
    /** Corner roundness in dp. */
    val corner: Int = 22,
    val textMode: TextMode = TextMode.AUTO,
    /** Colorful weather icons, or icons that match the text color. */
    val naturalIcons: Boolean = true,
    val decor: Decor = Decor.NONE,
    /** How strongly the shape shows, 0.05 to 0.6. */
    val decorStrength: Float = 0.25f,
    val decorColor: Int = WHITE,
    /** Mirror the shape left-to-right. */
    val decorFlip: Boolean = false,
    val bgMode: BgMode = BgMode.COLORS,
    /** File name of the chosen photo (PHOTO mode). */
    val photo: String? = null,
    /** How much to darken the photo so text stays readable, 0 to 0.7. */
    val photoDim: Float = 0.3f,
    val border: BorderStyle = BorderStyle.NONE,
    val borderColor: Int = WHITE,
    val premium: Boolean = false,
) {
    val isGradient: Boolean get() = colors.size > 1

    /** Backgrounds drawn as an image (not a plain color). */
    val needsBaseBitmap: Boolean
        get() = when (bgMode) {
            BgMode.PHOTO, BgMode.SKY, BgMode.TEMPERATURE -> true
            BgMode.WALLPAPER -> false
            BgMode.COLORS -> isGradient
        }

    val hasOverlay: Boolean get() = decor != Decor.NONE || border != BorderStyle.NONE

    fun usesDarkText(): Boolean = when (textMode) {
        TextMode.LIGHT -> false
        TextMode.DARK -> true
        TextMode.AUTO -> when (bgMode) {
            BgMode.PHOTO -> false
            // When mostly see-through, the wallpaper decides; light text is the safer bet.
            else -> opacity >= 0.45f && colors.map { ColorUtils.calculateLuminance(it) }.average() > 0.5
        }
    }

    fun textColors(): ThemeColors {
        val dark = usesDarkText()
        return if (dark) {
            ThemeColors(Color(0xFF14171F), Color(0xFF14171F).copy(alpha = 0.72f), true, naturalIcons)
        } else {
            ThemeColors(Color.White, Color.White.copy(alpha = 0.76f), false, naturalIcons)
        }
    }

    fun asCustom() = copy(id = "custom", name = "Custom", premium = true)

    fun withColorCount(n: Int): WidgetTheme {
        val list = colors.toMutableList()
        val extras = listOf(0xFF3F5EFB.toInt(), 0xFFFC466B.toInt(), 0xFF38EF7D.toInt())
        while (list.size < n) list.add(extras[list.size % extras.size])
        return copy(colors = list.take(n))
    }

    fun withColor(index: Int, color: Int) =
        copy(colors = colors.toMutableList().also { it[index] = color })

    fun toJson(): String = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("colors", JSONArray(colors))
        put("angle", angle)
        put("opacity", opacity.toDouble())
        put("corner", corner)
        put("text", textMode.name)
        put("natural", naturalIcons)
        put("decor", decor.name)
        put("decorStrength", decorStrength.toDouble())
        put("decorColor", decorColor)
        put("decorFlip", decorFlip)
        put("bgMode", bgMode.name)
        put("photo", photo ?: JSONObject.NULL)
        put("photoDim", photoDim.toDouble())
        put("border", border.name)
        put("borderColor", borderColor)
        put("premium", premium)
    }.toString()

    companion object {
        const val WHITE: Int = -1 // 0xFFFFFFFF

        private inline fun <reified E : Enum<E>> enumOr(s: String?, fallback: E): E =
            try {
                if (s == null) fallback else enumValueOf<E>(s)
            } catch (e: Exception) {
                fallback
            }

        fun fromJson(s: String?): WidgetTheme? {
            if (s == null) return null
            return try {
                val o = JSONObject(s)
                val arr = o.getJSONArray("colors")
                val colors = (0 until arr.length()).map { arr.getInt(it) }
                if (colors.isEmpty()) return null
                WidgetTheme(
                    id = o.getString("id"),
                    name = o.getString("name"),
                    colors = colors,
                    angle = o.optInt("angle", 135),
                    opacity = o.optDouble("opacity", 0.92).toFloat(),
                    corner = o.optInt("corner", 22),
                    textMode = enumOr(o.optString("text", "AUTO"), TextMode.AUTO),
                    naturalIcons = o.optBoolean("natural", true),
                    decor = enumOr(o.optString("decor", "NONE"), Decor.NONE),
                    decorStrength = o.optDouble("decorStrength", 0.25).toFloat(),
                    decorColor = o.optInt("decorColor", WHITE),
                    decorFlip = o.optBoolean("decorFlip", false),
                    bgMode = enumOr(o.optString("bgMode", "COLORS"), BgMode.COLORS),
                    photo = if (o.isNull("photo")) null else o.optString("photo"),
                    photoDim = o.optDouble("photoDim", 0.3).toFloat(),
                    border = enumOr(o.optString("border", "NONE"), BorderStyle.NONE),
                    borderColor = o.optInt("borderColor", WHITE),
                    premium = o.optBoolean("premium", false),
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}

/** Text and icon colors that read well on a given look. */
data class ThemeColors(
    val text: Color,
    val dim: Color,
    val darkText: Boolean,
    val naturalIcons: Boolean,
) {
    fun icon(tint: Long): Color = when {
        !naturalIcons -> text
        darkText -> Color(tint).let { Color(it.red * 0.55f, it.green * 0.55f, it.blue * 0.55f) }
        else -> Color(tint)
    }

    val info: Color get() = if (darkText) Color(0xFF1565C0) else Color(0xFF90CAF9)
    val warn: Color get() = if (darkText) Color(0xFFB45F06) else Color(0xFFFFB74D)
    val danger: Color get() = if (darkText) Color(0xFFC62828) else Color(0xFFFF7A7A)
}

private fun c(v: Long): Int = v.toInt()

/** Colors for temperatures: icy blue when cold through green to red when hot. */
object TempScale {
    private val stops = listOf(
        -15.0 to c(0xFF5A4FCF), -5.0 to c(0xFF3D7DD8), 5.0 to c(0xFF2BA6C4),
        12.0 to c(0xFF2BB59A), 18.0 to c(0xFF6CC24A), 24.0 to c(0xFFF2C14E),
        30.0 to c(0xFFF08A3C), 36.0 to c(0xFFE5484D), 42.0 to c(0xFFA4161A),
    )

    fun color(tempC: Double): Int {
        if (tempC.isNaN()) return stops[4].second
        if (tempC <= stops.first().first) return stops.first().second
        if (tempC >= stops.last().first) return stops.last().second
        val i = stops.indexOfFirst { it.first >= tempC }
        val (t0, c0) = stops[i - 1]
        val (t1, c1) = stops[i]
        return ColorUtils.blendARGB(c0, c1, ((tempC - t0) / (t1 - t0)).toFloat())
    }

    /** Version that reads well as text on a light (darkText) or dark background. */
    fun forText(tempC: Double, onLight: Boolean): Color {
        val base = color(tempC)
        val adjusted = if (onLight) ColorUtils.blendARGB(base, c(0xFF000000), 0.35f)
        else ColorUtils.blendARGB(base, c(0xFFFFFFFF), 0.25f)
        return Color(adjusted)
    }
}

/** Background colors and a matching shape for the current weather and time of day. */
object Sky {
    fun look(d: WeatherData?, nowSec: Long): Pair<List<Int>, Decor> {
        fun l(vararg v: Long) = v.map { c(it) }
        if (d == null) return l(0xFF2F80D1, 0xFF7CC1F0) to Decor.RAYS
        val day = d.isDay
        val nearSun = listOf(d.today.sunrise, d.today.sunset).any { it > 0 && abs(nowSec - it) < 45 * 60 }
        return when (d.condition) {
            Condition.THUNDERSTORM, Condition.HAIL -> l(0xFF1F1C2C, 0xFF3B2F63, 0xFF4B4F6B) to Decor.CLOUDS
            Condition.RAIN, Condition.HEAVY_RAIN, Condition.SHOWERS, Condition.DRIZZLE, Condition.FREEZING_RAIN ->
                (if (day) l(0xFF4B5D73, 0xFF7A8CA3) else l(0xFF1C2533, 0xFF35465C)) to Decor.CLOUDS
            Condition.SNOW, Condition.HEAVY_SNOW ->
                (if (day) l(0xFF8FA6C4, 0xFFDDE7F3) else l(0xFF2E3B55, 0xFF56688A)) to Decor.BOKEH
            Condition.FOG, Condition.HAZE ->
                (if (day) l(0xFF8E979F, 0xFFC9CED3) else l(0xFF3A4048, 0xFF5A626B)) to Decor.NONE
            Condition.DUST -> l(0xFF9C6F3A, 0xFFD9B27A) to Decor.GRAIN
            Condition.CLOUDY ->
                (if (day) l(0xFF6F86A0, 0xFFAFBFCF) else l(0xFF232B38, 0xFF3D4A5C)) to Decor.CLOUDS
            Condition.WINDY ->
                (if (day) l(0xFF3E8BCB, 0xFF94C8EC) else l(0xFF0E1A33, 0xFF263B66)) to Decor.STRIPES
            else -> {
                val partly = d.condition == Condition.PARTLY_CLOUDY || d.condition == Condition.PARTLY_CLOUDY_NIGHT
                when {
                    nearSun -> l(0xFF2B3A67, 0xFFE8737A, 0xFFFDB97D) to (if (partly) Decor.CLOUDS else Decor.RAYS)
                    day -> l(0xFF2F80D1, 0xFF7CC1F0) to (if (partly) Decor.CLOUDS else Decor.RAYS)
                    else -> l(0xFF070B1F, 0xFF1B2550) to Decor.STARS
                }
            }
        }
    }
}

object Themes {
    // ---- Free ----
    val MIDNIGHT = WidgetTheme("midnight", "Midnight", listOf(c(0xFF1B1F2A)), opacity = 0.9f)
    val DAYLIGHT = WidgetTheme("daylight", "Daylight", listOf(c(0xFFF4F6FA)), opacity = 0.94f)
    private val OCEAN = WidgetTheme("ocean", "Ocean", listOf(c(0xFF15396B)))
    private val FOREST = WidgetTheme("forest", "Forest", listOf(c(0xFF1F4A3A)))

    // ---- Special backgrounds ----
    val WALLPAPER = WidgetTheme("wallpaper", "My wallpaper", listOf(c(0xFF3D5A80)), bgMode = BgMode.WALLPAPER)
    val SKY = WidgetTheme("sky", "Live sky", listOf(c(0xFF2F80D1)), bgMode = BgMode.SKY, angle = 90, premium = true)
    val TEMPERATURE = WidgetTheme(
        "temperature", "Temperature", listOf(c(0xFFF2C14E)), bgMode = BgMode.TEMPERATURE, premium = true,
    )
    val PHOTO = WidgetTheme("photo", "Photo", listOf(c(0xFF444444)), bgMode = BgMode.PHOTO, premium = true)

    // ---- Pro color looks ----
    private fun proTheme(
        id: String,
        name: String,
        vararg colors: Long,
        opacity: Float = 0.92f,
        angle: Int = 135,
        decor: Decor = Decor.NONE,
        strength: Float = 0.25f,
        flip: Boolean = false,
        border: BorderStyle = BorderStyle.NONE,
    ) = WidgetTheme(
        id, name, colors.map { c(it) }, angle = angle, opacity = opacity,
        decor = decor, decorStrength = strength, decorFlip = flip, border = border, premium = true,
    )

    val free = listOf(MIDNIGHT, DAYLIGHT, OCEAN, FOREST)
    val specials = listOf(WALLPAPER, SKY, TEMPERATURE, PHOTO)
    val pro = listOf(
        proTheme("glass", "Glass", 0xFF000000, opacity = 0.32f, border = BorderStyle.THIN),
        proTheme("nightswoosh", "Night Swoosh", 0xFF1B1F2A, 0xFF2A3350, decor = Decor.SWOOSH, strength = 0.14f),
        proTheme("sunset", "Sunset", 0xFFEE5A6F, 0xFFF29263, decor = Decor.SWOOSH, strength = 0.28f),
        proTheme("aurora", "Aurora", 0xFF0F2027, 0xFF203A43, 0xFF2C7A5B, decor = Decor.WAVE, strength = 0.2f),
        proTheme("lavender", "Lavender", 0xFF41295A, 0xFF7B4397, decor = Decor.SWOOSH, strength = 0.24f, flip = true),
        proTheme("deepsea", "Deep Sea", 0xFF000428, 0xFF004E92, angle = 90, decor = Decor.DOUBLE_SWOOSH, strength = 0.2f),
        proTheme("ember", "Ember", 0xFF3A1C1C, 0xFFB33939, 0xFFE17055, decor = Decor.WAVE, strength = 0.18f),
        proTheme("mint", "Mint", 0xFF11998E, 0xFF38EF7D, decor = Decor.SWOOSH, strength = 0.25f),
        proTheme("neon", "Neon", 0xFFFC466B, 0xFF3F5EFB, decor = Decor.GLOW, strength = 0.35f, border = BorderStyle.GLOW),
        proTheme("stone", "Jerusalem Stone", 0xFFEFE3C8, 0xFFC9A96E, decor = Decor.SKYLINE, strength = 0.22f),
        proTheme("rose", "Rose Gold", 0xFFE8B4B8, 0xFFB76E79, decor = Decor.DOUBLE_SWOOSH, strength = 0.22f),
        proTheme("alpine", "Alpine", 0xFF1E3C72, 0xFF2A5298, angle = 90, decor = Decor.MOUNTAINS, strength = 0.3f),
        proTheme("starry", "Starry Night", 0xFF070B1F, 0xFF1B2550, angle = 90, decor = Decor.STARS, strength = 0.5f),
        proTheme("cotton", "Cotton Clouds", 0xFF89A7D6, 0xFFC7D7F0, angle = 90, decor = Decor.CLOUDS, strength = 0.35f),
        proTheme("film", "Film Grain", 0xFF2D2A26, 0xFF4A4036, decor = Decor.GRAIN, strength = 0.25f),
    )
    val all = free + pro

    /** Colors offered in the custom editor. */
    val swatches: List<Int> = listOf(
        0xFF000000, 0xFF1B1F2A, 0xFF37474F, 0xFF78909C, 0xFFECEFF1, 0xFFFFFFFF,
        0xFFB71C1C, 0xFFE53935, 0xFFFF7043, 0xFFFFA726, 0xFFFFD54F, 0xFFFFF59D,
        0xFF1B5E20, 0xFF43A047, 0xFF9CCC65, 0xFF00897B, 0xFF4DD0E1,
        0xFF0D47A1, 0xFF1E88E5, 0xFF64B5F6, 0xFF283593, 0xFF5E35B1, 0xFF9575CD,
        0xFF8E24AA, 0xFFD81B60, 0xFFF48FB1, 0xFF6D4C41, 0xFFD7B98E,
    ).map { c(it) }
}

/** Pro status. Real purchases arrive in stage 3; for now a developer switch unlocks it. */
object Pro {
    private const val PREFS = "pro"

    fun isPro(ctx: Context): Boolean = prefs(ctx).getBoolean("dev_unlock", false)

    fun setDevUnlock(ctx: Context, on: Boolean) {
        prefs(ctx).edit().putBoolean("dev_unlock", on).apply()
    }

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

/** The look to use in light mode and in dark mode (the same when not following the phone). */
data class Looks(val day: WidgetTheme, val night: WidgetTheme)

object ThemeStore {
    private const val PREFS = "themes"

    /** The main look; when following the phone's dark mode, this is the light-mode look. */
    fun global(ctx: Context): WidgetTheme =
        WidgetTheme.fromJson(prefs(ctx).getString("global", null)) ?: Themes.MIDNIGHT

    fun setGlobal(ctx: Context, t: WidgetTheme) {
        prefs(ctx).edit().putString("global", t.toJson()).apply()
    }

    fun followSystem(ctx: Context): Boolean = prefs(ctx).getBoolean("follow_system", false)

    fun setFollowSystem(ctx: Context, on: Boolean) {
        // First time on: split into a light look and a dark look that make sense.
        if (on && prefs(ctx).getString("dark", null) == null) {
            val current = global(ctx)
            if (current.usesDarkText()) {
                setDark(ctx, Themes.MIDNIGHT)
            } else {
                setDark(ctx, current)
                setGlobal(ctx, Themes.DAYLIGHT)
            }
        }
        prefs(ctx).edit().putBoolean("follow_system", on).apply()
    }

    fun dark(ctx: Context): WidgetTheme =
        WidgetTheme.fromJson(prefs(ctx).getString("dark", null)) ?: Themes.MIDNIGHT

    fun setDark(ctx: Context, t: WidgetTheme) {
        prefs(ctx).edit().putString("dark", t.toJson()).apply()
    }

    fun forWidget(ctx: Context, appWidgetId: Int): WidgetTheme? =
        WidgetTheme.fromJson(prefs(ctx).getString("widget_$appWidgetId", null))

    fun setForWidget(ctx: Context, appWidgetId: Int, t: WidgetTheme?) {
        val e = prefs(ctx).edit()
        if (t == null) e.remove("widget_$appWidgetId") else e.putString("widget_$appWidgetId", t.toJson())
        e.apply()
    }

    /** What a particular widget should draw with. Pro looks fall back if Pro is off. */
    fun looks(ctx: Context, appWidgetId: Int): Looks {
        val pro = Pro.isPro(ctx)
        fun gate(t: WidgetTheme) = if (t.premium && !pro) Themes.MIDNIGHT else t
        val perWidget = if (pro && appWidgetId > 0) forWidget(ctx, appWidgetId) else null
        if (perWidget != null) return Looks(perWidget, perWidget)
        val main = gate(global(ctx))
        return if (followSystem(ctx)) Looks(main, gate(dark(ctx))) else Looks(main, main)
    }

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

/** Lets the app's live preview show a look and place that aren't saved yet. */
object PreviewOverride {
    data class Preview(val theme: WidgetTheme, val placeId: String, val emphasis: Emphasis?)

    @Volatile
    private var value: Preview? = null

    /** Previews take turns, so two on screen at once never mix up their settings. */
    val lock = kotlinx.coroutines.sync.Mutex()

    fun set(theme: WidgetTheme, placeId: String, emphasis: Emphasis? = null) {
        value = Preview(theme, placeId, emphasis)
    }

    fun current(): Preview? = value
}

/** Photos chosen as widget backgrounds, kept in the app's private storage. */
object PhotoStore {
    private fun dir(ctx: Context) = File(ctx.filesDir, "photos").apply { mkdirs() }

    fun file(ctx: Context, name: String) = File(dir(ctx), name)

    /** Copies a picked photo in, upright and shrunk to a sensible size. Returns its name. */
    fun import(ctx: Context, uri: Uri): String? = try {
        val cr = ctx.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 1600) sample *= 2
        val raw = cr.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        }
        if (raw == null) {
            null
        } else {
            val rotation = cr.openInputStream(uri)?.use {
                when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } ?: 0f
            val upright = if (rotation != 0f) {
                Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, Matrix().apply { postRotate(rotation) }, true)
            } else {
                raw
            }
            val name = "photo_${System.currentTimeMillis()}.jpg"
            FileOutputStream(file(ctx, name)).use { upright.compress(Bitmap.CompressFormat.JPEG, 88, it) }
            name
        }
    } catch (e: Exception) {
        null
    }
}

object ThemeRender {

    /**
     * Turns "Live sky" and "Temperature" into concrete colors for this moment's weather.
     * Other looks come back unchanged.
     */
    fun effective(t: WidgetTheme, d: WeatherData?, nowSec: Long = System.currentTimeMillis() / 1000): WidgetTheme =
        when (t.bgMode) {
            BgMode.SKY -> {
                val (colors, decor) = Sky.look(d, nowSec)
                val useDecor = if (t.decor == Decor.NONE) decor else t.decor
                t.copy(
                    colors = colors,
                    angle = 90,
                    decor = useDecor,
                    decorStrength = if (t.decor == Decor.NONE) 0.22f else t.decorStrength,
                )
            }
            BgMode.TEMPERATURE -> {
                val base = TempScale.color(d?.tempC ?: 20.0)
                t.copy(colors = listOf(base, ColorUtils.blendARGB(base, c(0xFF000000), 0.4f)))
            }
            else -> t
        }

    /** Start and end points (x0, y0, x1, y1) of a gradient at [angle] across a w x h box. */
    fun gradientPoints(angle: Int, w: Float, h: Float): FloatArray {
        val rad = Math.toRadians(angle.toDouble())
        val dx = cos(rad).toFloat()
        val dy = sin(rad).toFloat()
        val half = abs(w / 2 * dx) + abs(h / 2 * dy)
        val cx = w / 2
        val cy = h / 2
        return floatArrayOf(cx - dx * half, cy - dy * half, cx + dx * half, cy + dy * half)
    }

    /** Pixel size for a widget of the given dp size, capped so images stay small. Third value: px per dp. */
    private fun pixels(widthDp: Float, heightDp: Float, pxPerDp: Float = 2f): Triple<Int, Int, Float> {
        val wd = if (widthDp > 0f) widthDp else 100f
        val hd = if (heightDp > 0f) heightDp else 100f
        val scale = min(1f, 720f / (max(wd, hd) * pxPerDp))
        val ppd = pxPerDp * scale
        return Triple(max(1, (wd * ppd).roundToInt()), max(1, (hd * ppd).roundToInt()), ppd)
    }

    /** The background (gradient, sky, temperature or photo) as an image. */
    fun baseBitmap(ctx: Context, t: WidgetTheme, widthDp: Float, heightDp: Float): Bitmap {
        val (w, h, _) = pixels(widthDp, heightDp)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        drawBase(ctx, Canvas(bmp), t, w.toFloat(), h.toFloat())
        return bmp
    }

    /** Shapes and border, on a see-through image laid over the background. Null if none. */
    fun overlayBitmap(t: WidgetTheme, widthDp: Float, heightDp: Float): Bitmap? {
        if (!t.hasOverlay) return null
        val (w, h, ppd) = pixels(widthDp, heightDp)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        drawOverlay(Canvas(bmp), t, w.toFloat(), h.toFloat(), ppd)
        return bmp
    }

    /** Background plus shapes in one picture, for the app's look tiles. */
    fun thumbnail(ctx: Context, t: WidgetTheme, widthDp: Float, heightDp: Float): Bitmap {
        val eff = effective(t, null)
        val (w, h, ppd) = pixels(widthDp, heightDp)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        drawBase(ctx, canvas, eff, w.toFloat(), h.toFloat())
        drawOverlay(canvas, eff, w.toFloat(), h.toFloat(), ppd)
        return bmp
    }

    private fun drawBase(ctx: Context, canvas: Canvas, t: WidgetTheme, w: Float, h: Float) {
        if (t.bgMode == BgMode.PHOTO) {
            drawPhoto(ctx, canvas, t, w, h)
            return
        }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        when {
            t.bgMode == BgMode.WALLPAPER -> paint.color = ctx.getColor(R.color.dw_wall_bg)
            t.isGradient -> {
                val p = gradientPoints(t.angle, w, h)
                paint.shader = LinearGradient(p[0], p[1], p[2], p[3], t.colors.toIntArray(), null, Shader.TileMode.CLAMP)
            }
            else -> paint.color = t.colors[0]
        }
        if (t.bgMode != BgMode.WALLPAPER) {
            paint.alpha = (t.opacity * 255).roundToInt().coerceIn(0, 255)
        }
        canvas.drawRect(0f, 0f, w, h, paint)
    }

    private fun drawPhoto(ctx: Context, canvas: Canvas, t: WidgetTheme, w: Float, h: Float) {
        val src = t.photo?.let { name ->
            val f = PhotoStore.file(ctx, name)
            if (f.exists()) BitmapFactory.decodeFile(f.absolutePath) else null
        }
        if (src == null) {
            canvas.drawColor(c(0xFF444A55))
            return
        }
        // Center-crop to the widget's shape.
        val targetRatio = w / h
        val srcRatio = src.width.toFloat() / src.height
        val crop = if (srcRatio > targetRatio) {
            val cw = (src.height * targetRatio).roundToInt()
            Rect((src.width - cw) / 2, 0, (src.width + cw) / 2, src.height)
        } else {
            val ch = (src.width / targetRatio).roundToInt()
            Rect(0, (src.height - ch) / 2, src.width, (src.height + ch) / 2)
        }
        val paint = Paint(Paint.FILTER_BITMAP_FLAG).apply {
            alpha = (t.opacity * 255).roundToInt().coerceIn(0, 255)
        }
        canvas.drawBitmap(src, crop, RectF(0f, 0f, w, h), paint)
        if (t.photoDim > 0f) {
            canvas.drawColor(withAlpha(c(0xFF000000), t.photoDim.coerceIn(0f, 0.9f)))
        }
    }

    private fun withAlpha(color: Int, a: Float): Int =
        ColorUtils.setAlphaComponent(color, (a * 255).roundToInt().coerceIn(0, 255))

    private fun drawOverlay(c: Canvas, t: WidgetTheme, w: Float, h: Float, ppd: Float) {
        if (t.decor != Decor.NONE) drawDecor(c, t, w, h)
        if (t.border != BorderStyle.NONE) drawBorder(c, t, w, h, ppd)
    }

    private fun drawBorder(c: Canvas, t: WidgetTheme, w: Float, h: Float, ppd: Float) {
        val radius = t.corner * ppd
        fun stroke(widthDp: Float, alpha: Float, blurDp: Float = 0f) {
            val sw = widthDp * ppd
            val inset = sw / 2 + blurDp * ppd
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = sw
                color = withAlpha(t.borderColor, alpha)
                if (blurDp > 0f) maskFilter = BlurMaskFilter(blurDp * ppd, BlurMaskFilter.Blur.NORMAL)
            }
            val r = max(0f, radius - inset)
            c.drawRoundRect(RectF(inset, inset, w - inset, h - inset), r, r, paint)
        }
        when (t.border) {
            BorderStyle.THIN -> stroke(1.5f, 0.55f)
            BorderStyle.THICK -> stroke(3.5f, 0.7f)
            BorderStyle.GLOW -> {
                stroke(5f, 0.6f, blurDp = 5f)
                stroke(1.2f, 0.9f)
            }
            BorderStyle.NONE -> Unit
        }
    }

    private fun drawDecor(c: Canvas, t: WidgetTheme, w: Float, h: Float) {
        val a = t.decorStrength.coerceIn(0f, 1f)
        val col = t.decorColor
        c.save()
        if (t.decorFlip) c.scale(-1f, 1f, w / 2, h / 2)
        when (t.decor) {
            Decor.SWOOSH -> swoosh(c, w, h, col, a, y0 = 0.80f, peak = 0.12f, y1 = 0.40f, thick = 0.16f)
            Decor.DOUBLE_SWOOSH -> {
                swoosh(c, w, h, col, a, y0 = 0.68f, peak = 0.05f, y1 = 0.28f, thick = 0.12f)
                swoosh(c, w, h, col, a * 0.6f, y0 = 1.00f, peak = 0.42f, y1 = 0.68f, thick = 0.10f)
            }
            Decor.WAVE -> {
                wave(c, w, h, col, a * 0.7f, base = 0.58f, amp = 0.16f)
                wave(c, w, h, col, a, base = 0.76f, amp = 0.12f)
            }
            Decor.GLOW -> {
                glow(c, w, h, col, a, cx = 0.82f, cy = 0.12f, r = 0.75f)
                glow(c, w, h, col, a * 0.5f, cx = 0.10f, cy = 1.00f, r = 0.55f)
            }
            Decor.MOUNTAINS -> mountains(c, w, h, col, a)
            Decor.SKYLINE -> skyline(c, w, h, col, a)
            Decor.CLOUDS -> clouds(c, w, h, col, a)
            Decor.RAYS -> rays(c, w, h, col, a)
            Decor.BOKEH -> bokeh(c, w, h, col, a)
            Decor.STARS -> stars(c, w, h, col, a)
            Decor.STRIPES -> stripes(c, w, h, col, a)
            Decor.GRAIN -> grain(c, w, h, col, a)
            Decor.NONE -> Unit
        }
        c.restore()
    }

    private fun fill(color: Int, a: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = withAlpha(color, a) }

    /** A curved band sweeping across, brighter on the left, with a fine highlight edge. */
    private fun swoosh(
        c: Canvas, w: Float, h: Float, color: Int, a: Float,
        y0: Float, peak: Float, y1: Float, thick: Float,
    ) {
        val band = Path().apply {
            moveTo(0f, y0 * h)
            quadTo(w * 0.55f, peak * h, w, y1 * h)
            lineTo(w, (y1 + thick) * h)
            quadTo(w * 0.5f, (peak + thick * 1.6f) * h, 0f, (y0 + thick * 1.4f) * h)
            close()
        }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, w, 0f,
                intArrayOf(withAlpha(color, a), withAlpha(color, a * 0.3f)), null, Shader.TileMode.CLAMP,
            )
        }
        c.drawPath(band, paint)
        val edge = Path().apply {
            moveTo(0f, y0 * h)
            quadTo(w * 0.55f, peak * h, w, y1 * h)
        }
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = max(1f, h * 0.012f)
            shader = LinearGradient(
                0f, 0f, w, 0f,
                intArrayOf(withAlpha(color, min(1f, a * 1.8f)), withAlpha(color, 0f)), null, Shader.TileMode.CLAMP,
            )
        }
        c.drawPath(edge, stroke)
    }

    private fun wave(c: Canvas, w: Float, h: Float, color: Int, a: Float, base: Float, amp: Float) {
        val p = Path().apply {
            moveTo(0f, base * h)
            cubicTo(w * 0.3f, (base - amp) * h, w * 0.6f, (base + amp) * h, w, (base - amp * 0.3f) * h)
            lineTo(w, h)
            lineTo(0f, h)
            close()
        }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, (base - amp) * h, 0f, h,
                intArrayOf(withAlpha(color, a), withAlpha(color, a * 0.35f)), null, Shader.TileMode.CLAMP,
            )
        }
        c.drawPath(p, paint)
    }

    private fun glow(c: Canvas, w: Float, h: Float, color: Int, a: Float, cx: Float, cy: Float, r: Float) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                cx * w, cy * h, max(w, h) * r,
                intArrayOf(withAlpha(color, a), withAlpha(color, 0f)), null, Shader.TileMode.CLAMP,
            )
        }
        c.drawRect(0f, 0f, w, h, paint)
    }

    /** Two layers of peaks along the bottom. */
    private fun mountains(c: Canvas, w: Float, h: Float, color: Int, a: Float) {
        fun ridge(points: List<Pair<Float, Float>>, alpha: Float) {
            val p = Path().apply {
                moveTo(0f, h)
                points.forEach { (x, y) -> lineTo(x * w, y * h) }
                lineTo(w, h)
                close()
            }
            c.drawPath(p, fill(color, alpha))
        }
        ridge(listOf(0f to 0.70f, 0.18f to 0.48f, 0.32f to 0.62f, 0.52f to 0.38f, 0.70f to 0.58f, 0.86f to 0.44f, 1f to 0.60f), a * 0.55f)
        ridge(listOf(0f to 0.86f, 0.14f to 0.70f, 0.30f to 0.82f, 0.46f to 0.64f, 0.64f to 0.80f, 0.82f to 0.68f, 1f to 0.84f), a)
    }

    /** Simple buildings, a dome and a spire along the bottom. */
    private fun skyline(c: Canvas, w: Float, h: Float, color: Int, a: Float) {
        val paint = fill(color, a)
        val rnd = Random(7)
        var x = 0f
        while (x < w) {
            val bw = w * (0.05f + rnd.nextFloat() * 0.07f)
            val bh = h * (0.12f + rnd.nextFloat() * 0.22f)
            c.drawRect(x, h - bh, x + bw + 1f, h, paint)
            x += bw
        }
        // a dome
        val dx = w * 0.62f
        val dr = h * 0.12f
        c.drawRect(dx - dr, h - h * 0.30f, dx + dr, h, paint)
        c.drawArc(RectF(dx - dr, h - h * 0.30f - dr, dx + dr, h - h * 0.30f + dr), 180f, 180f, true, paint)
        // a spire
        val sx = w * 0.25f
        c.drawRect(sx - w * 0.012f, h - h * 0.55f, sx + w * 0.012f, h, paint)
    }

    private fun cloud(c: Canvas, cx: Float, cy: Float, s: Float, paint: Paint) {
        c.drawCircle(cx, cy, s, paint)
        c.drawCircle(cx - s * 0.9f, cy + s * 0.25f, s * 0.7f, paint)
        c.drawCircle(cx + s * 0.95f, cy + s * 0.2f, s * 0.75f, paint)
        c.drawRoundRect(RectF(cx - s * 1.6f, cy + s * 0.1f, cx + s * 1.7f, cy + s * 0.95f), s * 0.45f, s * 0.45f, paint)
    }

    private fun clouds(c: Canvas, w: Float, h: Float, color: Int, a: Float) {
        val s = min(w, h)
        cloud(c, w * 0.80f, h * 0.22f, s * 0.16f, fill(color, a))
        cloud(c, w * 0.18f, h * 0.78f, s * 0.20f, fill(color, a * 0.7f))
        cloud(c, w * 0.55f, h * 0.95f, s * 0.14f, fill(color, a * 0.5f))
    }

    /** Light beams fanning out from the top-right corner. */
    private fun rays(c: Canvas, w: Float, h: Float, color: Int, a: Float) {
        val ox = w * 0.95f
        val oy = -h * 0.1f
        val len = max(w, h) * 1.6f
        for (i in 0 until 7) {
            val ang = Math.toRadians((100 + i * 13).toDouble())
            val spread = Math.toRadians(4.0)
            val p = Path().apply {
                moveTo(ox, oy)
                lineTo(ox + (cos(ang - spread) * len).toFloat(), oy + (sin(ang - spread) * len).toFloat())
                lineTo(ox + (cos(ang + spread) * len).toFloat(), oy + (sin(ang + spread) * len).toFloat())
                close()
            }
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = RadialGradient(
                    ox, oy, len,
                    intArrayOf(withAlpha(color, a), withAlpha(color, 0f)), null, Shader.TileMode.CLAMP,
                )
            }
            c.drawPath(p, paint)
        }
    }

    private fun bokeh(c: Canvas, w: Float, h: Float, color: Int, a: Float) {
        val rnd = Random(11)
        val s = min(w, h)
        repeat(14) {
            val r = s * (0.04f + rnd.nextFloat() * 0.12f)
            c.drawCircle(rnd.nextFloat() * w, rnd.nextFloat() * h, r, fill(color, a * (0.3f + rnd.nextFloat() * 0.7f)))
        }
    }

    private fun stars(c: Canvas, w: Float, h: Float, color: Int, a: Float) {
        val rnd = Random(3)
        val s = min(w, h)
        repeat(70) {
            val r = s * (0.004f + rnd.nextFloat() * 0.008f)
            c.drawCircle(rnd.nextFloat() * w, rnd.nextFloat() * h, r, fill(color, min(1f, a * (0.4f + rnd.nextFloat() * 1.2f))))
        }
        // a few brighter ones with a soft halo
        repeat(4) {
            val x = rnd.nextFloat() * w
            val y = rnd.nextFloat() * h * 0.7f
            glowAt(c, x, y, s * 0.05f, color, min(1f, a * 1.2f))
        }
    }

    private fun glowAt(c: Canvas, x: Float, y: Float, r: Float, color: Int, a: Float) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(x, y, r, intArrayOf(withAlpha(color, a), withAlpha(color, 0f)), null, Shader.TileMode.CLAMP)
        }
        c.drawCircle(x, y, r, paint)
    }

    private fun stripes(c: Canvas, w: Float, h: Float, color: Int, a: Float) {
        val paint = fill(color, a * 0.6f)
        val step = max(w, h) * 0.09f
        var x = -h
        while (x < w + h) {
            val p = Path().apply {
                moveTo(x, h)
                lineTo(x + step * 0.45f, h)
                lineTo(x + step * 0.45f + h, 0f)
                lineTo(x + h, 0f)
                close()
            }
            c.drawPath(p, paint)
            x += step
        }
    }

    private fun grain(c: Canvas, w: Float, h: Float, color: Int, a: Float) {
        val rnd = Random(5)
        val count = (w * h / 30f).toInt().coerceAtMost(30_000)
        val pts = FloatArray(count * 2)
        for (i in 0 until count) {
            pts[i * 2] = rnd.nextFloat() * w
            pts[i * 2 + 1] = rnd.nextFloat() * h
        }
        val paint = Paint().apply {
            this.color = withAlpha(color, min(1f, a * 0.8f))
            strokeWidth = 1.2f
        }
        c.drawPoints(pts, paint)
    }
}
