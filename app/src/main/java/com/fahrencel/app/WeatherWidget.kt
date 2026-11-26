package com.fahrencel.app

import android.appwidget.AppWidgetManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.content.Context
import android.content.res.Configuration
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider as DayNightColorProvider
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.RowScope
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontFamily
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import kotlin.math.max
import kotlin.math.roundToInt

class WeatherWidget : GlanceAppWidget() {

    // Exact: we get the real size the launcher gives us and pick a layout to fit it.
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // Real widgets have positive IDs; the app's live preview uses a made-up one.
        val appWidgetId = try {
            GlanceAppWidgetManager(context).getAppWidgetId(id)
        } catch (e: Exception) {
            AppWidgetManager.INVALID_APPWIDGET_ID
        }
        provideContent {
            // Changes whenever weather, units, style or looks change (see WidgetUpdater).
            val version = currentState(WidgetUpdater.VERSION) ?: 0L
            val snap = remember(version) { Snapshot.load(context, appWidgetId) }
            CompositionLocalProvider(
                LocalColors provides snap.colors(),
                LocalStyle provides snap.style,
            ) {
                WidgetRoot(snap)
            }
        }
    }
}

// ================= What to draw =================

/** Everything one widget needs to draw, read once per update. */
private class Snapshot(
    val data: WeatherData?,
    val alert: Alert?,
    val kelvin: Boolean,
    val style: WidgetStyle,
    /** Looks with "Live sky" / "Temperature" already turned into real colors. */
    val day: WidgetTheme,
    val night: WidgetTheme,
    val isNight: Boolean,
    /** Shows "My location" but the phone's location has never been known. */
    val needsLocation: Boolean,
) {
    val current: WidgetTheme get() = if (isNight) night else day

    /**
     * Plain-color looks switch with the phone's dark mode instantly. Images (gradients,
     * sky, photos) switch at the next refresh instead.
     */
    val liveDayNight: Boolean
        get() = day != night &&
            day.bgMode == BgMode.COLORS && !day.isGradient &&
            night.bgMode == BgMode.COLORS && !night.isGradient

    fun colors(): GColors = when {
        current.bgMode == BgMode.WALLPAPER -> GColors.wallpaper(current.naturalIcons)
        liveDayNight -> GColors.of(day.textColors(), night.textColors())
        else -> current.textColors().let { GColors.of(it, it) }
    }

    companion object {
        fun load(ctx: Context, appWidgetId: Int): Snapshot {
            val preview = if (appWidgetId <= 0) PreviewOverride.current() else null
            val placeId = preview?.placeId ?: WidgetPrefs.placeFor(ctx, appWidgetId)
            val looks = preview?.let { Looks(it.theme, it.theme) } ?: ThemeStore.looks(ctx, appWidgetId)
            val data = WeatherStore.load(ctx, placeId)
            val pro = Pro.isPro(ctx)
            val baseStyle = StyleStore.get(ctx).gated(pro)
            // A widget's own unit order (Pro) wins over the app setting.
            val ownEmphasis = when {
                preview != null -> preview.emphasis
                pro && appWidgetId > 0 -> WidgetPrefs.emphasisFor(ctx, appWidgetId)
                else -> null
            }
            val style = ownEmphasis?.let { baseStyle.copy(emphasis = it) } ?: baseStyle
            val nowSec = System.currentTimeMillis() / 1000
            val night = (ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
            return Snapshot(
                data = data,
                alert = data?.let { Alerts.compute(it, style.emphasis.cFirst) },
                kelvin = AppPrefs.kelvin(ctx),
                style = style,
                day = ThemeRender.effective(looks.day, data, nowSec),
                night = ThemeRender.effective(looks.night, data, nowSec),
                isNight = night,
                needsLocation = placeId == Places.HERE && WeatherStore.loadLocation(ctx) == null,
            )
        }
    }
}

/** A color that can differ between light and dark mode. */
private fun dn(day: Color, night: Color): ColorProvider =
    if (day == night) ColorProvider(day) else DayNightColorProvider(day = day, night = night)

/** Widget colors, ready for light and dark mode. */
private class GColors(
    val text: ColorProvider,
    val dim: ColorProvider,
    val info: ColorProvider,
    val warn: ColorProvider,
    val danger: ColorProvider,
    val infoBg: ColorProvider,
    val warnBg: ColorProvider,
    val dangerBg: ColorProvider,
    /** Soft see-through panel behind forecast rows. */
    val panel: ColorProvider,
    private val iconFn: (Long) -> ColorProvider,
    private val dayOnLight: Boolean,
    private val nightOnLight: Boolean,
) {
    fun icon(tint: Long): ColorProvider = iconFn(tint)

    /** Temperature-colored text (icy blue to hot red). */
    fun temp(tempC: Double): ColorProvider =
        dn(TempScale.forText(tempC, dayOnLight), TempScale.forText(tempC, nightOnLight))

    companion object {
        fun of(day: ThemeColors, night: ThemeColors) = GColors(
            text = dn(day.text, night.text),
            dim = dn(day.dim, night.dim),
            info = dn(day.info, night.info),
            warn = dn(day.warn, night.warn),
            danger = dn(day.danger, night.danger),
            infoBg = dn(day.info.copy(alpha = 0.18f), night.info.copy(alpha = 0.18f)),
            warnBg = dn(day.warn.copy(alpha = 0.18f), night.warn.copy(alpha = 0.18f)),
            dangerBg = dn(day.danger.copy(alpha = 0.18f), night.danger.copy(alpha = 0.18f)),
            panel = dn(day.text.copy(alpha = 0.08f), night.text.copy(alpha = 0.08f)),
            iconFn = { t -> dn(day.icon(t), night.icon(t)) },
            dayOnLight = day.darkText,
            nightOnLight = night.darkText,
        )

        /** "Match my wallpaper": text colors come from the phone's own color palette. */
        fun wallpaper(natural: Boolean): GColors {
            val light = ThemeColors(Color.Black, Color.Black, darkText = true, naturalIcons = natural)
            val dark = ThemeColors(Color.White, Color.White, darkText = false, naturalIcons = natural)
            val text = ColorProvider(R.color.dw_wall_text)
            return GColors(
                text = text,
                dim = ColorProvider(R.color.dw_wall_dim),
                info = dn(light.info, dark.info),
                warn = dn(light.warn, dark.warn),
                danger = dn(light.danger, dark.danger),
                infoBg = dn(light.info.copy(alpha = 0.18f), dark.info.copy(alpha = 0.18f)),
                warnBg = dn(light.warn.copy(alpha = 0.18f), dark.warn.copy(alpha = 0.18f)),
                dangerBg = dn(light.danger.copy(alpha = 0.18f), dark.danger.copy(alpha = 0.18f)),
                panel = dn(Color.Black.copy(alpha = 0.06f), Color.White.copy(alpha = 0.08f)),
                iconFn = { t -> if (natural) dn(light.icon(t), dark.icon(t)) else text },
                dayOnLight = true,
                nightOnLight = false,
            )
        }
    }
}

private val LocalColors = staticCompositionLocalOf {
    Themes.MIDNIGHT.textColors().let { GColors.of(it, it) }
}
private val LocalStyle = staticCompositionLocalOf { WidgetStyle() }

/** Text color and background tint for a warning line. */
@Composable
private fun alertColors(level: AlertLevel): Pair<ColorProvider, ColorProvider> {
    val c = LocalColors.current
    return when (level) {
        AlertLevel.INFO -> c.info to c.infoBg
        AlertLevel.WARN -> c.warn to c.warnBg
        AlertLevel.DANGER -> c.danger to c.dangerBg
    }
}

private fun solid(t: WidgetTheme): Color = Color(t.colors[0]).copy(alpha = t.opacity)

// ================= Small building blocks =================

@Composable
private fun T(
    text: String,
    size: Int,
    bold: Boolean = false,
    color: ColorProvider = LocalColors.current.text,
    modifier: GlanceModifier = GlanceModifier,
    align: TextAlign = TextAlign.Start,
) {
    Text(
        text = text,
        modifier = modifier,
        style = TextStyle(
            color = color,
            fontSize = size.sp,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            textAlign = align,
            fontFamily = FontFamily(LocalStyle.current.font.family),
        ),
        maxLines = 1,
    )
}

/** A temperature number: bold and/or temperature-colored, per the style settings. */
@Composable
private fun TempT(text: String, tempC: Double, size: Int, secondary: Boolean = false) {
    val st = LocalStyle.current
    val c = LocalColors.current
    T(
        text, size,
        bold = st.boldNumbers && !secondary,
        color = when {
            st.tempColors -> c.temp(tempC)
            secondary -> c.dim
            else -> c.text
        },
    )
}

@Composable
private fun Icon(@DrawableRes res: Int, description: String?, size: Dp, color: ColorProvider) {
    Image(
        provider = ImageProvider(res),
        contentDescription = description,
        modifier = GlanceModifier.size(size),
        colorFilter = ColorFilter.tint(color),
    )
}

@Composable
private fun ConditionIcon(c: Condition, size: Dp) =
    Icon(c.iconFor(LocalStyle.current.filledIcons), c.label, size, LocalColors.current.icon(c.tint))

/**
 * The current temperature: the main unit big, the other unit smaller in the weather's color.
 * [stacked] puts the second unit under the first; otherwise it sits beside it.
 */
@Composable
private fun HeroTemps(d: WeatherData, kelvin: Boolean, big: Int, small: Int, stacked: Boolean, center: Boolean = false) {
    val st = LocalStyle.current
    val c = LocalColors.current
    val (first, second) = Units.pair(d.tempC, st.emphasis.cFirst)
    val secondSize = if (st.emphasis.firstBigger) small else (big * 0.85f).toInt()
    val firstColor = if (st.tempColors) c.temp(d.tempC) else c.text
    val secondColor = if (st.tempColors) c.temp(d.tempC) else c.icon(d.condition.tint)
    val kSize = (secondSize * 0.8f).toInt().coerceAtLeast(10)
    if (stacked) {
        Column(horizontalAlignment = if (center) Alignment.CenterHorizontally else Alignment.Start) {
            T(first, big, bold = st.boldNumbers, color = firstColor)
            T(second, secondSize, color = secondColor)
            if (kelvin) T(Units.k(d.tempC), kSize, color = c.dim)
        }
    } else {
        Row(verticalAlignment = Alignment.Bottom) {
            T(first, big, bold = st.boldNumbers, color = firstColor)
            Spacer(GlanceModifier.width(6.dp))
            T(second, secondSize, color = secondColor)
            if (kelvin) {
                Spacer(GlanceModifier.width(6.dp))
                T(Units.k(d.tempC), kSize, color = c.dim)
            }
        }
    }
}

/** The place name with a pin, if the style shows it. */
@Composable
private fun CityLine(d: WeatherData, size: Int, center: Boolean = false) {
    if (!LocalStyle.current.showPlace) return
    val name = d.city ?: d.area ?: return
    Row(
        modifier = if (center) GlanceModifier.fillMaxWidth() else GlanceModifier,
        horizontalAlignment = if (center) Alignment.CenterHorizontally else Alignment.Start,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(R.drawable.ic_location_on, null, (size + 1).dp, LocalColors.current.dim)
        Spacer(GlanceModifier.width(2.dp))
        T(name, size, bold = true, color = LocalColors.current.dim)
    }
}

/** A thin line between sections. */
@Composable
private fun Divider(top: Dp = 0.dp, bottom: Dp = 0.dp) {
    Spacer(GlanceModifier.height(top))
    Box(GlanceModifier.fillMaxWidth().height(1.dp).background(LocalColors.current.panel)) {}
    Spacer(GlanceModifier.height(bottom))
}

/** "66°F" -> "66°", for narrow spots that say the unit once. */
private fun String.noUnit(): String = removeSuffix("F").removeSuffix("C")

/** Whether Celsius leads, per the style settings. */
@Composable
private fun cf(): Boolean = LocalStyle.current.emphasis.cFirst

// ================= Root: choose a layout from the widget's real size =================

@Composable
private fun WidgetRoot(snap: Snapshot) {
    val ctx = LocalContext.current
    val data = snap.data
    val alert = snap.alert
    val kelvin = snap.kelvin
    val theme = snap.current
    val size = LocalSize.current
    val wDp = size.width.value
    val hDp = size.height.value
    val cols = WidgetGrid.cols(wDp)
    val rows = WidgetGrid.rows(hDp)
    val pad = if (cols == 1 || rows == 1) 8.dp else 12.dp

    val background = when {
        theme.bgMode == BgMode.WALLPAPER -> GlanceModifier.background(ColorProvider(R.color.dw_wall_bg))
        snap.liveDayNight -> GlanceModifier.background(dn(solid(snap.day), solid(snap.night)))
        theme.needsBaseBitmap -> GlanceModifier.background(
            ImageProvider(ThemeRender.baseBitmap(ctx, theme, wDp, hDp)),
            ContentScale.FillBounds,
        )
        else -> GlanceModifier.background(ColorProvider(solid(theme)))
    }
    val overlay = ThemeRender.overlayBitmap(theme, wDp, hDp)
    // Tall, narrow widgets glow from the top middle; the rest from the top left.
    val narrow = cols == 1 && rows > 1
    val glow = data?.let {
        glowBitmap(it.condition.tint, wDp, hDp, if (narrow) 0.5f else 0.18f, if (narrow) 0.36f / rows else 0.12f)
    }

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .then(background)
            .cornerRadius(theme.corner.dp)
            .clickable(actionRunCallback<RefreshAction>()),
    ) {
        if (overlay != null) {
            Image(
                provider = ImageProvider(overlay),
                contentDescription = null,
                modifier = GlanceModifier.fillMaxSize(),
                contentScale = ContentScale.FillBounds,
            )
        }
        if (glow != null) {
            Image(
                provider = ImageProvider(glow),
                contentDescription = null,
                modifier = GlanceModifier.fillMaxSize(),
                contentScale = ContentScale.FillBounds,
            )
        }
        Box(
            modifier = GlanceModifier.fillMaxSize().padding(pad),
            contentAlignment = Alignment.Center,
        ) {
            if (data == null) {
                T(
                    if (snap.needsLocation) "Allow location or pick a city" else "Tap to load weather",
                    12,
                    align = TextAlign.Center,
                )
            } else {
                when {
                    cols == 1 && rows == 1 -> Tiny(data, kelvin)
                    cols == 1 -> Tower(data, kelvin, rows)
                    rows == 1 && cols == 2 -> Strip2(data, kelvin)
                    rows == 1 && cols == 3 -> Strip3(data, kelvin)
                    rows == 1 -> Strip4(data, kelvin)
                    cols == 2 && rows >= 3 -> Square3(data, kelvin)
                    cols == 2 -> Square2(data, kelvin)
                    rows == 2 -> Medium(data, alert, kelvin, cols)
                    else -> Large(data, alert, kelvin, cols)
                }
            }
        }
    }
}

// ---- 1x1 ----

@Composable
private fun Tiny(d: WeatherData, kelvin: Boolean) {
    Column(modifier = GlanceModifier.fillMaxSize()) {
        ConditionIcon(d.condition, 18.dp)
        Spacer(GlanceModifier.defaultWeight())
        HeroTemps(d, kelvin, if (kelvin) 18 else 21, 12, stacked = true)
        if (!kelvin) CityLine(d, 9)
    }
}

// ---- 1x2, 1x3, 1x4 ----

@Composable
private fun Tower(d: WeatherData, kelvin: Boolean, rows: Int) {
    val st = LocalStyle.current
    Column(modifier = GlanceModifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        CityLine(d, 9, center = true)
        Spacer(GlanceModifier.height(6.dp))
        ConditionIcon(d.condition, 30.dp)
        Spacer(GlanceModifier.height(4.dp))
        HeroTemps(d, kelvin, if (kelvin) 18 else 22, 12, stacked = true, center = true)
        if (rows == 2) {
            Spacer(GlanceModifier.defaultWeight())
            if (st.showHiLo) {
                Divider(bottom = 5.dp)
                HiLoLines(d)
            }
        } else {
            Spacer(GlanceModifier.height(6.dp))
            HiLoLines(d)
            Spacer(GlanceModifier.defaultWeight())
            if (st.showHourly) {
                Divider(bottom = 4.dp)
                SectionLabel("Next hours")
                HourRows(d, 4)
            }
            if (rows >= 4 && st.showDaily) {
                Divider(top = 4.dp, bottom = 4.dp)
                SectionLabel(if (cf()) "Days °C" else "Days °F")
                DayRows(d, 4)
            }
        }
    }
}

@Composable
private fun HiLoLines(d: WeatherData) {
    if (!LocalStyle.current.showHiLo) return
    val (a, b) = Units.hiLoPair(d.today.hiC, d.today.loC, cf())
    T(a, 10, align = TextAlign.Center)
    T(b, 9, color = LocalColors.current.dim, align = TextAlign.Center)
}

@Composable
private fun SectionLabel(text: String) {
    T(text.uppercase(), 8, color = LocalColors.current.dim)
    Spacer(GlanceModifier.height(3.dp))
}

/** Next hour, then every 3 hours (same as the hourly strip). */
private fun nextHours(d: WeatherData, n: Int): List<Hour> =
    d.hours.drop(1).filterIndexed { i, _ -> i % 3 == 0 }.take(n)

@Composable
private fun HourRows(d: WeatherData, n: Int) {
    nextHours(d, n).forEach { h ->
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            T(d.clock(h.time), 9, color = LocalColors.current.dim)
            Spacer(GlanceModifier.defaultWeight())
            ConditionIcon(Condition.fromCode(h.code, h.isDay), 14.dp)
            Spacer(GlanceModifier.width(4.dp))
            TempT(Units.pair(h.tempC, cf()).first.noUnit(), h.tempC, 10)
        }
        Spacer(GlanceModifier.height(3.dp))
    }
}

@Composable
private fun DayRows(d: WeatherData, n: Int) {
    d.days.take(n).forEach { day ->
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column {
                T(d.dayName(day.time), 9, bold = true)
                T(d.shortDate(day.time), 8, color = LocalColors.current.dim)
            }
            Spacer(GlanceModifier.defaultWeight())
            ConditionIcon(Condition.fromCode(day.code, isDay = true), 14.dp)
            Spacer(GlanceModifier.width(4.dp))
            T(Units.hiLoPair(day.hiC, day.loC, cf()).first.removeSuffix("°F").removeSuffix("°C"), 10)
        }
        Spacer(GlanceModifier.height(3.dp))
    }
}

// ---- 2x1 ----

@Composable
private fun Strip2(d: WeatherData, kelvin: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        ConditionIcon(d.condition, 38.dp)
        Spacer(GlanceModifier.width(10.dp))
        Column {
            HeroTemps(d, kelvin, if (kelvin) 20 else 26, 14, stacked = false)
            T(d.condition.label, 11, color = LocalColors.current.dim)
            CityLine(d, 10)
        }
    }
}

// ---- 3x1 ----

@Composable
private fun Strip3(d: WeatherData, kelvin: Boolean) {
    val st = LocalStyle.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        ConditionIcon(d.condition, 36.dp)
        Spacer(GlanceModifier.width(10.dp))
        Column {
            HeroTemps(d, kelvin, if (kelvin) 22 else 28, 15, stacked = false)
            if (st.showHiLo) {
                T("H ${Units.both(d.today.hiC, cf())}   L ${Units.both(d.today.loC, cf())}", 11, color = LocalColors.current.dim)
            }
            T(
                listOfNotNull(d.condition.label, if (st.showPlace) d.city else null).joinToString(" · "),
                11, color = LocalColors.current.dim,
            )
        }
    }
}

// ---- 4x1 ----

@Composable
private fun Strip4(d: WeatherData, kelvin: Boolean) {
    val st = LocalStyle.current
    Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        ConditionIcon(d.condition, 40.dp)
        Spacer(GlanceModifier.width(10.dp))
        Column(modifier = GlanceModifier.defaultWeight()) {
            HeroTemps(d, kelvin, if (kelvin) 24 else 32, 16, stacked = false)
            if (st.showFeels) T("Feels ${Units.both(d.feelsC, cf())}", 11, color = LocalColors.current.dim)
            if (st.showHiLo) {
                T("H ${Units.both(d.today.hiC, cf())}   L ${Units.both(d.today.loC, cf())}", 11, color = LocalColors.current.dim)
            }
        }
        Spacer(GlanceModifier.width(6.dp))
        Column(horizontalAlignment = Alignment.End) {
            if (st.showPlace) d.city?.let { T(it, 12, bold = true, align = TextAlign.End) }
            T(d.condition.label, 11, color = LocalColors.current.dim, align = TextAlign.End)
        }
    }
}

// ---- 2x2 and 2x3 ----

@Composable
private fun SquareTop(d: WeatherData, kelvin: Boolean) {
    CityLine(d, 10)
    Spacer(GlanceModifier.height(6.dp))
    Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = GlanceModifier.defaultWeight()) {
            HeroTemps(d, kelvin, if (kelvin) 28 else 34, 16, stacked = true)
        }
        ConditionIcon(d.condition, 46.dp)
    }
    Spacer(GlanceModifier.height(3.dp))
    T(d.condition.label, 11, color = LocalColors.current.dim)
}

@Composable
private fun Square2(d: WeatherData, kelvin: Boolean) {
    Column(modifier = GlanceModifier.fillMaxSize()) {
        SquareTop(d, kelvin)
        Spacer(GlanceModifier.defaultWeight())
        StatPanel(d)
    }
}

@Composable
private fun Square3(d: WeatherData, kelvin: Boolean) {
    val st = LocalStyle.current
    Column(modifier = GlanceModifier.fillMaxSize()) {
        SquareTop(d, kelvin)
        Spacer(GlanceModifier.height(8.dp))
        StatPanel(d)
        Spacer(GlanceModifier.defaultWeight())
        when {
            st.showHourly -> HourlyStrip(d, 4)
            st.showDaily -> DailyStrip(d, d.days.take(3))
        }
    }
}

/** High/low, feels-like and chance of rain, side by side. */
@Composable
private fun StatPanel(d: WeatherData) {
    val st = LocalStyle.current
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .background(LocalColors.current.panel)
            .cornerRadius(14.dp)
            .padding(vertical = 5.dp),
    ) {
        if (st.showHiLo) {
            val (a, b) = Units.hiLoPair(d.today.hiC, d.today.loC, cf())
            Stat("H / L", a.noUnit(), b)
        }
        if (st.showFeels) {
            val (a, b) = Units.pair(d.feelsC, cf())
            Stat("Feels", a, b)
        }
        Stat("Rain", "${d.today.precipProb}%", "chance")
    }
}

@Composable
private fun RowScope.Stat(label: String, value: String, sub: String) {
    Column(modifier = GlanceModifier.defaultWeight(), horizontalAlignment = Alignment.CenterHorizontally) {
        T(label.uppercase(), 9, color = LocalColors.current.dim)
        T(value, 11, bold = true)
        T(sub, 9, color = LocalColors.current.dim)
    }
}

// ---- 3x2 and 4x2 ----

@Composable
private fun Medium(d: WeatherData, alert: Alert?, kelvin: Boolean, cols: Int) {
    val st = LocalStyle.current
    val showAlert = st.showAlert && alert != null
    val hasBottom = showAlert || st.showDaily
    val centered = !hasBottom && !st.showFeels && !st.showHiLo
    Column(
        modifier = GlanceModifier.fillMaxSize(),
        verticalAlignment = if (hasBottom) Alignment.Top else Alignment.CenterVertically,
        horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start,
    ) {
        Header(d, centered)
        Spacer(GlanceModifier.height(4.dp))
        MainBlock(
            d, kelvin,
            iconSize = 44.dp,
            tempSize = if (cols >= 4) (if (kelvin) 28 else 36) else (if (kelvin) 24 else 32),
        )
        if (hasBottom) {
            Spacer(GlanceModifier.defaultWeight())
            if (showAlert && alert != null) {
                AlertLine(alert)
            } else {
                DailyStrip(d, d.days.take(if (cols >= 4) 4 else 3))
            }
        }
    }
}

// ---- 3x3, 4x3 and bigger ----

@Composable
private fun Large(d: WeatherData, alert: Alert?, kelvin: Boolean, cols: Int) {
    val st = LocalStyle.current
    val showAlert = st.showAlert && alert != null
    val hasBottom = st.showHourly || st.showDaily || st.showUpdated
    val centered = !hasBottom && !showAlert && !st.showFeels && !st.showHiLo
    Column(
        modifier = GlanceModifier.fillMaxSize(),
        verticalAlignment = if (hasBottom) Alignment.Top else Alignment.CenterVertically,
        horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start,
    ) {
        Header(d, centered)
        Spacer(GlanceModifier.height(6.dp))
        MainBlock(
            d, kelvin,
            iconSize = 52.dp,
            tempSize = if (cols >= 4) (if (kelvin) 30 else 40) else (if (kelvin) 26 else 34),
        )
        if (showAlert && alert != null) {
            Spacer(GlanceModifier.height(8.dp))
            AlertLine(alert)
        }
        if (hasBottom) {
            Spacer(GlanceModifier.defaultWeight())
            if (st.showHourly) HourlyStrip(d, if (cols >= 4) 5 else 4)
            if (st.showHourly && st.showDaily) Spacer(GlanceModifier.height(8.dp))
            if (st.showDaily) DailyStrip(d, d.days.take(if (cols >= 4) 4 else 3))
            if (st.showUpdated) {
                T(
                    "Updated ${d.updatedClock()}", 9, color = LocalColors.current.dim,
                    modifier = GlanceModifier.fillMaxWidth().padding(top = 4.dp), align = TextAlign.Center,
                )
            }
        }
    }
}

// ---- Shared pieces for the bigger layouts ----

@Composable
private fun Header(d: WeatherData, centered: Boolean) {
    val st = LocalStyle.current
    if (centered) {
        if (st.showPlace) T(d.placeLabel ?: "Locating…", 11, bold = true, color = LocalColors.current.dim)
        return
    }
    Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (st.showPlace) {
            Icon(R.drawable.ic_location_on, null, 12.dp, LocalColors.current.dim)
            Spacer(GlanceModifier.width(3.dp))
            T(
                d.placeLabel ?: "Locating…", 11, bold = true, color = LocalColors.current.dim,
                modifier = GlanceModifier.defaultWeight(),
            )
        } else {
            Spacer(GlanceModifier.defaultWeight())
        }
        T(d.condition.label, 11, color = LocalColors.current.dim, align = TextAlign.End)
    }
}

@Composable
private fun MainBlock(d: WeatherData, kelvin: Boolean, iconSize: Dp, tempSize: Int) {
    val st = LocalStyle.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        ConditionIcon(d.condition, iconSize)
        Spacer(GlanceModifier.width(12.dp))
        Column {
            HeroTemps(d, kelvin, tempSize, (tempSize * 0.47f).toInt().coerceAtLeast(13), stacked = false)
            if (st.showFeels) T("Feels ${Units.both(d.feelsC, cf())}", 11, color = LocalColors.current.dim)
            if (st.showHiLo) {
                T("H ${Units.both(d.today.hiC, cf())}   L ${Units.both(d.today.loC, cf())}", 11, color = LocalColors.current.dim)
            }
        }
    }
}

@Composable
private fun AlertLine(a: Alert) {
    val (fg, bg) = alertColors(a.level)
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .background(bg)
            .cornerRadius(10.dp)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(a.icon, null, 14.dp, fg)
        Spacer(GlanceModifier.width(6.dp))
        T(a.text, 11, color = fg)
    }
}

@Composable
private fun HourlyStrip(d: WeatherData, count: Int) {
    // Next hour, then every 3 hours.
    val points = d.hours.drop(1).filterIndexed { i, _ -> i % 3 == 0 }.take(count)
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .background(LocalColors.current.panel)
            .cornerRadius(14.dp)
            .padding(vertical = 4.dp),
    ) {
        points.forEach { h ->
            Column(
                modifier = GlanceModifier.defaultWeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                T(d.clock(h.time), 10, color = LocalColors.current.dim)
                ConditionIcon(Condition.fromCode(h.code, h.isDay), 20.dp)
                val (a, b) = Units.pair(h.tempC, cf())
                TempT(a, h.tempC, 11)
                TempT(b, h.tempC, 10, secondary = true)
            }
        }
    }
}

@Composable
private fun DailyStrip(d: WeatherData, days: List<Day>) {
    // "Wed 11/25" on one line where columns are wide enough; otherwise the date goes underneath.
    val oneLine = (LocalSize.current.width.value - 24f) / days.size.coerceAtLeast(1) >= 60f
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .background(LocalColors.current.panel)
            .cornerRadius(14.dp)
            .padding(vertical = 4.dp),
    ) {
        days.forEach { day ->
            Column(
                modifier = GlanceModifier.defaultWeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (oneLine) {
                    T("${d.dayName(day.time)} ${d.shortDate(day.time)}", 10, bold = true)
                } else {
                    T(d.dayName(day.time), 11, bold = true)
                    T(d.shortDate(day.time), 9, color = LocalColors.current.dim)
                }
                ConditionIcon(Condition.fromCode(day.code, isDay = true), 20.dp)
                val (a, b) = Units.hiLoPair(day.hiC, day.loC, cf())
                TempT(a, day.hiC, 11)
                TempT(b, day.hiC, 10, secondary = true)
            }
        }
    }
}

// ================= Actions =================

/** Tapping the widget refreshes the weather. */
class RefreshAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        WeatherScheduler.refreshNow(context)
    }
}

/** A soft light in the weather's color behind the content, fading out from one corner. */
private fun glowBitmap(tint: Long, wDp: Float, hDp: Float, cx: Float, cy: Float): Bitmap {
    // One pixel per dp is plenty for a blur this soft.
    val w = wDp.roundToInt().coerceIn(1, 800)
    val h = hDp.roundToInt().coerceIn(1, 800)
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val rgb = (tint and 0xFFFFFF).toInt()
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = RadialGradient(
            w * cx, h * cy, max(w, h) * 0.9f,
            intArrayOf(rgb or (0x38 shl 24), rgb), floatArrayOf(0f, 0.7f), Shader.TileMode.CLAMP,
        )
    }
    Canvas(bmp).drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
    return bmp
}
