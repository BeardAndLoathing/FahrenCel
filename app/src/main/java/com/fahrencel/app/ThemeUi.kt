@file:OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class, ExperimentalGlanceApi::class)

package com.fahrencel.app

import android.widget.FrameLayout
import android.widget.RemoteViews
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Typography
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.datastore.preferences.core.emptyPreferences
import androidx.glance.ExperimentalGlanceApi
import androidx.glance.appwidget.compose
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

// ---- App color scheme: FahrenCel brand colors (warm °F orange, violet, cool °C blue) ----

object Brand {
    val warm = Color(0xFFFF6A2B)
    val warmSoft = Color(0xFFFFC12E)
    val violet = Color(0xFF7A45E6)
    val cool = Color(0xFF2C6CF0)
    val coolSoft = Color(0xFF12B5F5)

    /** The icon's sweep, for accents like the app bar title. */
    val sweep = listOf(warmSoft, warm, Color(0xFFE8336D), violet, cool)
}

private val LightScheme = lightColorScheme(
    primary = Color(0xFF6A3BD6),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEDE4FF),
    onPrimaryContainer = Color(0xFF22005D),
    secondary = Color(0xFFC2471A),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE3D6),
    onSecondaryContainer = Color(0xFF3A0B00),
    tertiary = Color(0xFF1F5BD8),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFDCE6FF),
    onTertiaryContainer = Color(0xFF001A43),
    background = Color(0xFFFAF8FC),
    onBackground = Color(0xFF1B1A20),
    surface = Color(0xFFFAF8FC),
    onSurface = Color(0xFF1B1A20),
    surfaceVariant = Color(0xFFE9E4F0),
    onSurfaceVariant = Color(0xFF4A4552),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF5F2F9),
    surfaceContainer = Color(0xFFF0ECF5),
    surfaceContainerHigh = Color(0xFFEAE6F0),
    surfaceContainerHighest = Color(0xFFE4E0EA),
    outline = Color(0xFF7B7584),
    outlineVariant = Color(0xFFCBC4D3),
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFFCDB8FF),
    onPrimary = Color(0xFF370096),
    primaryContainer = Color(0xFF4F25B8),
    onPrimaryContainer = Color(0xFFEDE4FF),
    secondary = Color(0xFFFFB592),
    onSecondary = Color(0xFF5B1A00),
    secondaryContainer = Color(0xFF7E2E0A),
    onSecondaryContainer = Color(0xFFFFE3D6),
    tertiary = Color(0xFFAFC6FF),
    onTertiary = Color(0xFF002E6A),
    tertiaryContainer = Color(0xFF0F45AE),
    onTertiaryContainer = Color(0xFFDCE6FF),
    background = Color(0xFF121117),
    onBackground = Color(0xFFE6E1EA),
    surface = Color(0xFF121117),
    onSurface = Color(0xFFE6E1EA),
    surfaceVariant = Color(0xFF494551),
    onSurfaceVariant = Color(0xFFCBC4D3),
    surfaceContainerLowest = Color(0xFF0D0C11),
    surfaceContainerLow = Color(0xFF1A1920),
    surfaceContainer = Color(0xFF1F1D25),
    surfaceContainerHigh = Color(0xFF292730),
    surfaceContainerHighest = Color(0xFF34323B),
    outline = Color(0xFF958E9E),
    outlineVariant = Color(0xFF494551),
)

private val AppTypography = Typography().let { t ->
    t.copy(
        displayLarge = t.displayLarge.copy(fontWeight = FontWeight.Light, letterSpacing = (-1.5).sp),
        displayMedium = t.displayMedium.copy(fontWeight = FontWeight.Light, letterSpacing = (-1).sp),
        headlineSmall = t.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = t.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    )
}

/** Shared card shape and color for the app screens. */
val CardShape = RoundedCornerShape(20.dp)

@Composable
fun cardColors() = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val scheme = if (isSystemInDarkTheme()) DarkScheme else LightScheme
    MaterialTheme(colorScheme = scheme, typography = AppTypography, content = content)
}

/** "FahrenCel" in the icon's warm-to-cool sweep, for the app bar. */
@Composable
fun BrandTitle() {
    Text(
        "FahrenCel",
        style = MaterialTheme.typography.titleLarge.copy(
            brush = Brush.linearGradient(Brand.sweep),
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.3).sp,
        ),
    )
}

@Composable
fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(top = 4.dp),
    )
}

@Composable
fun SwitchRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

// ---- The real widget, drawn inside the app ----

/** A pretend wallpaper behind previews, so see-through looks are visible. */
@Composable
fun Wallpaper(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.linearGradient(listOf(Color(0xFF8AA6CC), Color(0xFF3A4766), Color(0xFFC59A74)))
            )
            .padding(16.dp),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/**
 * Draws the actual home-screen widget at a size of [cols] x [rows] cells, using [theme]
 * and the weather for [placeId]. Updates live as those change.
 */
@Composable
fun LiveWidgetPreview(
    cols: Int,
    rows: Int,
    theme: WidgetTheme,
    placeId: String,
    refreshKey: Any? = null,
    modifier: Modifier = Modifier,
    emphasis: Emphasis? = null,
) {
    val ctx = LocalContext.current
    val w = (WidgetGrid.CELL_W * cols).dp
    val h = (WidgetGrid.CELL_H * rows).dp
    var views by remember { mutableStateOf<RemoteViews?>(null) }

    LaunchedEffect(theme, placeId, cols, rows, refreshKey, emphasis) {
        delay(60) // let quick slider drags settle
        views = PreviewOverride.lock.withLock {
            PreviewOverride.set(theme, placeId, emphasis)
            try {
                // Passing an empty state keeps previews from writing files to storage.
                WeatherWidget().compose(ctx, size = DpSize(w, h), state = emptyPreferences())
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
        }
    }

    Box(modifier.size(w, h)) {
        AndroidView(
            factory = { FrameLayout(it) },
            update = { frame ->
                frame.removeAllViews()
                views?.let { rv ->
                    try {
                        frame.addView(rv.apply(frame.context, frame))
                    } catch (e: Exception) {
                        // ignore; next update will try again
                    }
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/** Small picture of a look, used in the grids of looks. */
@Composable
fun ThemeThumb(t: WidgetTheme, selected: Boolean, locked: Boolean) {
    val ctx = LocalContext.current
    val bmp = remember(t) { ThemeRender.thumbnail(ctx, t, 72f, 46f).asImageBitmap() }
    val textColor = remember(t) {
        when (t.bgMode) {
            BgMode.WALLPAPER -> Color(ctx.getColor(R.color.dw_wall_text))
            BgMode.PHOTO -> Color.White
            else -> ThemeRender.effective(t, null).textColors().text
        }
    }
    Box(
        modifier = Modifier
            .width(72.dp)
            .height(46.dp)
            .clip(RoundedCornerShape(12.dp))
            .then(
                if (selected) {
                    Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            bitmap = bmp,
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = Modifier.fillMaxSize(),
        )
        if (t.bgMode == BgMode.PHOTO && t.photo == null) {
            Icon(painterResource(R.drawable.ic_image), contentDescription = null, tint = Color.White)
        } else {
            Text("72°", color = textColor, fontWeight = FontWeight.Bold)
        }
        if (locked) {
            Text(
                "PRO",
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(3.dp)
                    .background(Color(0xCC000000), RoundedCornerShape(4.dp))
                    .padding(horizontal = 4.dp, vertical = 1.dp),
            )
        }
    }
}

@Composable
private fun ThemeTile(t: WidgetTheme, selected: Boolean, locked: Boolean, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(78.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(3.dp),
    ) {
        ThemeThumb(t, selected = selected, locked = locked)
        Text(
            t.name,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/**
 * Smart backgrounds (wallpaper, live sky, temperature, photo) and color looks.
 * Picking "Photo" calls [onPhoto] so the caller can open the photo picker.
 */
@Composable
fun PresetGrid(selected: WidgetTheme, isPro: Boolean, onPick: (WidgetTheme) -> Unit, onPhoto: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Smart backgrounds", style = MaterialTheme.typography.labelLarge)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Themes.specials.forEach { t ->
                val isSel = selected.bgMode == t.bgMode && t.bgMode != BgMode.COLORS
                // Show the user's own photo on the Photo tile once they've picked one.
                val shownTile = if (t.bgMode == BgMode.PHOTO && selected.bgMode == BgMode.PHOTO) {
                    t.copy(photo = selected.photo, photoDim = selected.photoDim)
                } else {
                    t
                }
                ThemeTile(shownTile, isSel, t.premium && !isPro) {
                    if (t.bgMode == BgMode.PHOTO) onPhoto() else onPick(t)
                }
            }
        }
        Text("Colors", style = MaterialTheme.typography.labelLarge)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Themes.all.forEach { t ->
                ThemeTile(t, t.id == selected.id, t.premium && !isPro) { onPick(t) }
            }
        }
    }
}

/** Opens the phone's photo picker; the chosen photo is copied in and its name passed back. */
@Composable
fun rememberPhotoPicker(onPicked: (String) -> Unit): () -> Unit {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val callback by rememberUpdatedState(onPicked)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch {
                val name = withContext(Dispatchers.IO) { PhotoStore.import(ctx, uri) }
                if (name != null) callback(name)
            }
        }
    }
    return {
        launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }
}

// ---- Custom editor (Pro) ----

@Composable
private fun Label(text: String) = Text(text, style = MaterialTheme.typography.labelLarge)

@Composable
private fun Hint(text: String) = Text(text, style = MaterialTheme.typography.bodySmall)

@Composable
fun ThemeEditor(t: WidgetTheme, onChange: (WidgetTheme) -> Unit, onPickPhoto: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Label("Background")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BgMode.values().forEach { m ->
                FilterChip(
                    selected = t.bgMode == m,
                    onClick = {
                        if (m == BgMode.PHOTO && t.photo == null) onPickPhoto() else onChange(t.copy(bgMode = m))
                    },
                    label = { Text(m.label) },
                )
            }
        }
        when (t.bgMode) {
            BgMode.COLORS -> {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(1 to "Solid", 2 to "2-color gradient", 3 to "3-color").forEach { (n, label) ->
                        FilterChip(
                            selected = t.colors.size == n,
                            onClick = { onChange(t.withColorCount(n)) },
                            label = { Text(label) },
                        )
                    }
                }
                t.colors.forEachIndexed { i, color ->
                    Hint(if (t.colors.size == 1) "Background color" else "Color ${i + 1}")
                    SwatchGrid(selected = color) { onChange(t.withColor(i, it)) }
                }
                if (t.isGradient) {
                    Hint("Gradient direction: ${t.angle}°")
                    Slider(
                        value = t.angle.toFloat(),
                        onValueChange = { onChange(t.copy(angle = it.roundToInt())) },
                        valueRange = 0f..360f,
                    )
                }
            }
            BgMode.SKY -> Hint(
                "Colors follow the real sky: blue on sunny days, warm at sunrise and sunset, " +
                    "stars at night, gray when it rains. Pick a shape below to replace the automatic one."
            )
            BgMode.TEMPERATURE -> Hint("The background shifts from icy blue when it's cold to red when it's hot.")
            BgMode.WALLPAPER -> Hint(
                "Uses your phone's wallpaper colors (Android 12 and newer) and changes with your " +
                    "wallpaper and dark mode automatically."
            )
            BgMode.PHOTO -> {
                OutlinedButton(onClick = onPickPhoto) { Text(if (t.photo == null) "Choose photo…" else "Change photo…") }
                Hint("Darken photo: ${(t.photoDim * 100).roundToInt()}%")
                Slider(
                    value = t.photoDim,
                    onValueChange = { onChange(t.copy(photoDim = it)) },
                    valueRange = 0f..0.7f,
                )
            }
        }

        Label("Shape")
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Decor.values().forEach { d ->
                FilterChip(
                    selected = t.decor == d,
                    onClick = { onChange(t.copy(decor = d)) },
                    label = { Text(d.label) },
                )
            }
        }
        if (t.decor != Decor.NONE) {
            Hint("Shape strength: ${(t.decorStrength * 100).roundToInt()}%")
            Slider(
                value = t.decorStrength,
                onValueChange = { onChange(t.copy(decorStrength = it)) },
                valueRange = 0.05f..0.6f,
            )
            SwitchRow("Flip direction", null, t.decorFlip) { onChange(t.copy(decorFlip = it)) }
            Hint("Shape color")
            SwatchGrid(selected = t.decorColor) { onChange(t.copy(decorColor = it)) }
        }

        Label("Border")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BorderStyle.values().forEach { b ->
                FilterChip(
                    selected = t.border == b,
                    onClick = { onChange(t.copy(border = b)) },
                    label = { Text(b.label) },
                )
            }
        }
        if (t.border != BorderStyle.NONE) {
            Hint("Border color")
            SwatchGrid(selected = t.borderColor) { onChange(t.copy(borderColor = it)) }
        }

        if (t.bgMode != BgMode.WALLPAPER) {
            Hint("Opacity: ${(t.opacity * 100).roundToInt()}%")
            Slider(
                value = t.opacity,
                onValueChange = { onChange(t.copy(opacity = it)) },
                valueRange = 0.15f..1f,
            )
        }

        Hint("Corner roundness: ${t.corner} dp")
        Slider(
            value = t.corner.toFloat(),
            onValueChange = { onChange(t.copy(corner = it.roundToInt())) },
            valueRange = 0f..40f,
        )

        if (t.bgMode != BgMode.WALLPAPER) {
            Label("Text color")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(TextMode.AUTO to "Automatic", TextMode.LIGHT to "Light", TextMode.DARK to "Dark")
                    .forEach { (mode, label) ->
                        FilterChip(
                            selected = t.textMode == mode,
                            onClick = { onChange(t.copy(textMode = mode)) },
                            label = { Text(label) },
                        )
                    }
            }
        }
        SwitchRow("Colorful weather icons", "Off: icons match the text color", t.naturalIcons) {
            onChange(t.copy(naturalIcons = it))
        }
    }
}

/** Layout and text options. These apply to all widgets and are mostly free. */
@Composable
fun StyleEditor(s: WidgetStyle, isPro: Boolean, onChange: (WidgetStyle) -> Unit, onLocked: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Label("Layout")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(LayoutPreset.MINIMAL, LayoutPreset.CLASSIC, LayoutPreset.DETAILED).forEach { p ->
                FilterChip(
                    selected = s.layout == p,
                    onClick = { onChange(s.withPreset(p)) },
                    label = { Text(p.label) },
                )
            }
        }
        Hint("Or pick exactly what to show:")
        fun toggle(update: WidgetStyle.() -> WidgetStyle) = onChange(s.update().copy(layout = LayoutPreset.CUSTOM))
        SwitchRow("Place name", null, s.showPlace) { v -> toggle { copy(showPlace = v) } }
        SwitchRow("Feels like", null, s.showFeels) { v -> toggle { copy(showFeels = v) } }
        SwitchRow("High and low", null, s.showHiLo) { v -> toggle { copy(showHiLo = v) } }
        SwitchRow("Weather warnings", null, s.showAlert) { v -> toggle { copy(showAlert = v) } }
        SwitchRow("Hourly forecast", "On big widgets", s.showHourly) { v -> toggle { copy(showHourly = v) } }
        SwitchRow("Daily forecast", null, s.showDaily) { v -> toggle { copy(showDaily = v) } }
        SwitchRow("Last updated time", null, s.showUpdated) { v -> toggle { copy(showUpdated = v) } }

        Label("Which unit comes first")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Emphasis.values().forEach { e ->
                FilterChip(
                    selected = s.emphasis == e,
                    onClick = { onChange(s.copy(emphasis = e)) },
                    label = { Text(e.label) },
                )
            }
        }

        Label("Font")
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FontChoice.values().forEach { f ->
                FilterChip(
                    selected = s.font == f,
                    onClick = { if (f.premium && !isPro) onLocked() else onChange(s.copy(font = f)) },
                    label = { Text(if (f.premium && !isPro) "${f.label} (Pro)" else f.label) },
                )
            }
        }
        SwitchRow("Bold numbers", null, s.boldNumbers) { onChange(s.copy(boldNumbers = it)) }
        SwitchRow("Temperature colors", "Numbers go from icy blue to hot red", s.tempColors) {
            onChange(s.copy(tempColors = it))
        }
        SwitchRow("Filled icons", "Off: outlined icons", s.filledIcons) { onChange(s.copy(filledIcons = it)) }
    }
}

@Composable
private fun SwatchGrid(selected: Int, onPick: (Int) -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Themes.swatches.forEach { sw ->
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(Color(sw))
                    .border(
                        width = if (sw == selected) 3.dp else 1.dp,
                        color = if (sw == selected) MaterialTheme.colorScheme.primary else Color(0x33000000),
                        shape = CircleShape,
                    )
                    .clickable { onPick(sw) },
            )
        }
    }
}

@Composable
fun ProDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
        title = { Text("FahrenCel Pro") },
        text = {
            Text(
                "Live sky, temperature and photo backgrounds, gradients, shapes, borders, " +
                    "transparency, custom colors, extra fonts, sea and river conditions, a different look for " +
                    "each widget, and more than ${Places.FREE_CITY_LIMIT} extra cities are part of Pro.\n\nBuying Pro isn't set up yet; that's the next " +
                    "stage. For testing, use the Developer switch in Settings."
            )
        },
    )
}

// ---- Places ----

/** "Are you sure?" before removing a saved city. */
@Composable
fun ConfirmRemoveCityDialog(place: Place, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Remove ${place.name}?") },
        text = {
            Text(
                "${place.fullName} will be removed from your places. Any widget showing it " +
                    "will switch to My location."
            )
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Remove") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Search box for adding a city. Suggestions appear as you type. */
@Composable
fun PlaceSearchDialog(onPicked: (Place) -> Unit, onDismiss: () -> Unit) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Place>>(emptyList()) }
    var status by remember { mutableStateOf<String?>(null) }

    // Search shortly after typing stops; a new keystroke cancels the previous search.
    LaunchedEffect(query) {
        if (query.trim().length < 2) {
            results = emptyList()
            status = null
            return@LaunchedEffect
        }
        delay(350)
        status = "Searching…"
        val found = try {
            withContext(Dispatchers.IO) { PlaceSearch.search(query) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
        results = found ?: emptyList()
        status = when {
            found == null -> "Couldn't search. Check your connection."
            found.isEmpty() -> "No places found. Try just the city name."
            else -> null
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        title = { Text("Add a city") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("City, e.g. Tucson, AZ") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    modifier = Modifier.fillMaxWidth(),
                )
                status?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                LazyColumn(Modifier.heightIn(max = 320.dp)) {
                    items(results) { p ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPicked(p) }
                                .padding(vertical = 10.dp),
                        ) {
                            Text(p.name, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                listOfNotNull(p.region, p.country).joinToString(", "),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        },
    )
}
