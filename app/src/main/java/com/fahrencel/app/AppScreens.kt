@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)

package com.fahrencel.app

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Brush
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.Image
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private data class Tab(val title: String, val icon: Int)

private val tabs = listOf(
    Tab("Widgets", R.drawable.ic_widgets),
    Tab("Weather", R.drawable.ic_partly_cloudy_day),
    Tab("Settings", R.drawable.ic_settings),
)

/** Opens the widget editor for one widget on the home screen. */
fun openWidgetEditor(ctx: Context, appWidgetId: Int) {
    ctx.startActivity(
        Intent(ctx, WidgetConfigActivity::class.java)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
    )
}

@Composable
fun DualWeatherApp(tick: Int, onRefresh: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var kelvin by remember { mutableStateOf(AppPrefs.kelvin(ctx)) }
    var isPro by remember { mutableStateOf(Pro.isPro(ctx)) }
    var showPro by remember { mutableStateOf(false) }

    // Re-read saved weather every few seconds so background refreshes show up.
    var poll by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(5_000)
            poll++
        }
    }

    // A widget was just added from inside the app: open its editor once we're back on screen.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(Unit) {
        while (true) {
            delay(700)
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                WidgetPrefs.takePendingEdit(ctx)?.let { openWidgetEditor(ctx, it) }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { if (tab == 2) Text("Settings") else BrandTitle() },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { i, t ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { tab = i },
                        icon = { Icon(painterResource(t.icon), contentDescription = null) },
                        label = { Text(t.title) },
                    )
                }
            }
        },
    ) { padding ->
        Box(
            Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            when (tab) {
                0 -> WidgetsTab(isPro, tick)
                1 -> WeatherTab(tick + poll, kelvin, isPro, onRefresh) { showPro = true }
                else -> SettingsTab(
                    kelvin = kelvin,
                    isPro = isPro,
                    onKelvin = {
                        kelvin = it
                        AppPrefs.setKelvin(ctx, it)
                        scope.launch { WidgetUpdater.refreshAll(ctx) }
                    },
                    onPro = {
                        isPro = it
                        Pro.setDevUnlock(ctx, it)
                        scope.launch { WidgetUpdater.refreshAll(ctx) }
                    },
                    onRefresh = onRefresh,
                )
            }
        }
    }

    if (showPro) ProDialog { showPro = false }
}

/** Weather icon color that reads well on the app's light or dark background. */
@Composable
private fun appTint(c: Condition): Color {
    val tint = Color(c.tint)
    return if (isSystemInDarkTheme()) tint else Color(tint.red * 0.7f, tint.green * 0.7f, tint.blue * 0.7f)
}

private fun pinWidget(ctx: Context, placeId: String, size: WidgetSize) {
    if (!WidgetPin.request(ctx, placeId, size)) {
        Toast.makeText(
            ctx,
            "Your home screen doesn't allow this. Long-press the home screen, tap Widgets, and add FahrenCel.",
            Toast.LENGTH_LONG,
        ).show()
    }
}

/** Phones whose home screens often can't resize widgets or quietly block "add to home screen". */
private val strictLaunchers = setOf("xiaomi", "redmi", "poco", "huawei", "honor", "oppo", "vivo", "realme")

private fun isStrictLauncher(): Boolean = Build.MANUFACTURER.lowercase() in strictLaunchers

/**
 * Returns a function that opens the "Choose a size" picker for a place. Picking a size asks
 * the home screen to add the widget at that size.
 */
@Composable
private fun rememberAddWidget(): (String) -> Unit {
    val ctx = LocalContext.current
    var forPlace by remember { mutableStateOf<String?>(null) }
    forPlace?.let { placeId ->
        SizePickerDialog(
            onPick = { size ->
                forPlace = null
                pinWidget(ctx, placeId, size)
            },
            onDismiss = { forPlace = null },
        )
    }
    return { forPlace = it }
}

@Composable
private fun SizePickerDialog(onPick: (WidgetSize) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        title = { Text("Choose a size") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    WidgetSize.values().forEach { size ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .width(78.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { onPick(size) }
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                .padding(vertical = 10.dp),
                        ) {
                            SizeDiagram(size.cols, size.rows)
                            Spacer(Modifier.height(6.dp))
                            Text("${size.cols}x${size.rows}", style = MaterialTheme.typography.titleSmall)
                            Text(
                                size.label,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                Text(
                    "If your home screen lets you resize widgets, you can stretch any of these later. " +
                        "If it doesn't, remove the widget and add it again in another size.",
                    style = MaterialTheme.typography.bodySmall,
                )
                if (isStrictLauncher()) {
                    Text(
                        "Nothing appears on your home screen? Open Settings > Apps > FahrenCel > " +
                            "Other permissions and allow \"Home screen shortcuts\", then try again.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
            }
        },
    )
}

/** A 4x3 grid of cells with the chosen size filled in. */
@Composable
private fun SizeDiagram(cols: Int, rows: Int) {
    val empty = MaterialTheme.colorScheme.outlineVariant
    val full = MaterialTheme.colorScheme.primary
    Canvas(Modifier.size(width = 52.dp, height = 39.dp)) {
        val gap = 2.dp.toPx()
        val cw = (size.width - gap * 3) / 4
        val ch = (size.height - gap * 2) / 3
        for (c in 0 until 4) for (r in 0 until 3) {
            val on = c < cols && r < rows
            drawRoundRect(
                color = if (on) full else empty.copy(alpha = 0.5f),
                topLeft = Offset(c * (cw + gap), r * (ch + gap)),
                size = Size(cw, ch),
                cornerRadius = CornerRadius(3.dp.toPx()),
            )
        }
    }
}

// ================= Weather =================

@Composable
private fun WeatherTab(tick: Int, kelvin: Boolean, isPro: Boolean, onRefresh: () -> Unit, onLocked: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var placesTick by remember { mutableIntStateOf(0) }
    val places = remember(placesTick, tick) { Places.all(ctx) }
    var showSearch by remember { mutableStateOf(false) }
    var scrollToEnd by remember { mutableStateOf(false) }
    val pager = rememberPagerState(pageCount = { places.size })

    LaunchedEffect(places.size) {
        if (scrollToEnd) {
            pager.animateScrollToPage(places.size - 1)
            scrollToEnd = false
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                places.forEachIndexed { i, _ ->
                    val selected = i == pager.currentPage
                    Box(
                        Modifier
                            .padding(3.dp)
                            .size(if (selected) 9.dp else 7.dp)
                            .clip(CircleShape)
                            .background(
                                if (selected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            )
                    )
                }
            }
            TextButton(onClick = { if (Places.canAddMore(ctx)) showSearch = true else onLocked() }) {
                Icon(painterResource(R.drawable.ic_add), contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Add city")
            }
        }
        HorizontalPager(state = pager, modifier = Modifier.weight(1f)) { page ->
            val place = places[page]
            PlacePage(
                place = place,
                tick = tick,
                kelvin = kelvin,
                isPro = isPro,
                onRefresh = onRefresh,
                onLocked = onLocked,
                onRemove = {
                    Places.remove(ctx, place.id)
                    placesTick++
                    scope.launch { WidgetUpdater.refreshAll(ctx) }
                },
            )
        }
    }

    if (showSearch) {
        PlaceSearchDialog(
            onPicked = { p ->
                Places.add(ctx, p)
                showSearch = false
                scrollToEnd = true
                placesTick++
                WeatherScheduler.refreshNow(ctx)
            },
            onDismiss = { showSearch = false },
        )
    }
}

@Composable
private fun PlacePage(
    place: Place,
    tick: Int,
    kelvin: Boolean,
    isPro: Boolean,
    onRefresh: () -> Unit,
    onRemove: () -> Unit,
    onLocked: () -> Unit,
) {
    val ctx = LocalContext.current
    val addWidget = rememberAddWidget()
    val data = remember(place.id, tick) { WeatherStore.load(ctx, place.id) }
    val emphasis = remember(tick) { StyleStore.get(ctx).emphasis }
    val cFirst = emphasis.cFirst
    val alert = remember(data, cFirst) { data?.let { Alerts.compute(it, cFirst) } }
    var confirmRemove by remember { mutableStateOf(false) }
    if (confirmRemove) {
        ConfirmRemoveCityDialog(
            place = place,
            onConfirm = {
                confirmRemove = false
                onRemove()
            },
            onDismiss = { confirmRemove = false },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (data == null) {
            Card(Modifier.fillMaxWidth(), shape = CardShape, colors = cardColors()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(place.name, style = MaterialTheme.typography.headlineSmall)
                    Text("Loading weather…", style = MaterialTheme.typography.bodyLarge)
                    TextButton(onClick = onRefresh) { Text("Refresh") }
                }
            }
        } else {
            WeatherHero(
                place = place,
                data = data,
                kelvin = kelvin,
                emphasis = emphasis,
                onRemove = { confirmRemove = true },
            )

            alert?.let { AlertPill(it) }

            // Hourly
            SectionTitle("Next 24 hours")
            Card(Modifier.fillMaxWidth(), shape = CardShape, colors = cardColors()) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(data.hours.take(25)) { h ->
                        val isNow = h == data.hours.first()
                        HourChip(
                            label = if (isNow) "Now" else data.clock(h.time),
                            cond = Condition.fromCode(h.code, h.isDay),
                            tempC = h.tempC,
                            rain = h.precipProb,
                            cFirst = cFirst,
                            highlight = isNow,
                        )
                    }
                }
            }

            // Daily
            SectionTitle("Next days")
            Card(Modifier.fillMaxWidth(), shape = CardShape, colors = cardColors()) {
                val week = listOf(data.today) + data.days
                val weekMin = week.minOf { it.loC }
                val weekMax = week.maxOf { it.hiC }
                Column(Modifier.padding(vertical = 6.dp)) {
                    week.forEachIndexed { i, d ->
                        if (i > 0) {
                            HorizontalDivider(
                                Modifier.padding(horizontal = 16.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                            )
                        }
                        DayRow(
                            name = if (i == 0) "Today" else data.dayName(d.time),
                            day = d,
                            weekMin = weekMin,
                            weekMax = weekMax,
                            cFirst = cFirst,
                        )
                    }
                }
            }

            data.air?.let { AirCard(it) }
            if (isPro) {
                data.marine?.let { SeaCard(it, cFirst) }
                data.river?.let { RiverCard(it, cFirst) }
            } else {
                InfoCard("Sea and rivers") {
                    Text(
                        "Wave height, water temperature and river levels for your places, " +
                            "with warnings for rough seas and rising rivers.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    TextButton(onClick = onLocked) { Text("Part of Pro") }
                }
            }
        }

        FilledTonalButton(
            onClick = { addWidget(place.id) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(painterResource(R.drawable.ic_add_to_home_screen), contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Add widget for this place")
        }
        if (data != null) {
            Text(
                "Updated ${data.updatedClock()} · Weather data by Open-Meteo.com",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
        Spacer(Modifier.height(16.dp))
    }
}

/** The big card at the top: the real sky behind the current temperature. */
@Composable
private fun WeatherHero(
    place: Place,
    data: WeatherData,
    kelvin: Boolean,
    emphasis: Emphasis,
    onRemove: () -> Unit,
) {
    val ctx = LocalContext.current
    val cFirst = emphasis.cFirst
    val sky = remember(data) { ThemeRender.effective(Themes.SKY.copy(opacity = 1f), data) }
    val bg = remember(sky) {
        ThemeRender.baseBitmap(ctx, sky, 360f, 260f).asImageBitmap()
    }
    val decor = remember(sky) {
        ThemeRender.overlayBitmap(sky, 360f, 260f)?.asImageBitmap()
    }
    val onSky = sky.textColors()
    val text = onSky.text
    val dim = onSky.dim
    val (first, second) = Units.pair(data.tempC, cFirst)

    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
    ) {
        Image(bg, null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
        decor?.let { Image(it, null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize()) }
        Column(Modifier.padding(start = 20.dp, end = 8.dp, top = 14.dp, bottom = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painterResource(if (place.isHere) R.drawable.ic_my_location else R.drawable.ic_location_on),
                    contentDescription = null,
                    tint = text,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        data.placeLabel ?: place.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = text,
                        maxLines = 1,
                    )
                    val elevation = data.elevationM.takeIf { !it.isNaN() }
                    Text(
                        listOfNotNull(
                            if (place.isHere) "My location" else place.country,
                            elevation?.let { "${Units.length(it, cFirst)} up" },
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = dim,
                        maxLines = 1,
                    )
                }
                if (!place.isHere) {
                    IconButton(onClick = onRemove) {
                        Icon(painterResource(R.drawable.ic_delete), contentDescription = "Remove ${place.name}", tint = dim)
                    }
                } else {
                    Spacer(Modifier.height(48.dp))
                }
            }

            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    // Both scales, big and side by side: the whole point of the app.
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            first,
                            fontSize = if (emphasis.firstBigger) 64.sp else 52.sp,
                            fontWeight = FontWeight.Light,
                            letterSpacing = (-2).sp,
                            color = text,
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            second,
                            fontSize = if (emphasis.firstBigger) 26.sp else 52.sp,
                            fontWeight = FontWeight.Light,
                            letterSpacing = if (emphasis.firstBigger) 0.sp else (-2).sp,
                            color = if (emphasis.firstBigger) dim else text,
                            modifier = Modifier.padding(bottom = if (emphasis.firstBigger) 10.dp else 0.dp),
                        )
                    }
                    if (kelvin) Text(Units.k(data.tempC), style = MaterialTheme.typography.titleMedium, color = dim)
                }
                Icon(
                    painterResource(data.condition.filledIcon),
                    contentDescription = data.condition.label,
                    tint = onSky.icon(data.condition.tint),
                    modifier = Modifier
                        .padding(end = 12.dp)
                        .size(72.dp),
                )
            }
            Text(data.condition.label, style = MaterialTheme.typography.titleLarge, color = text)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HeroChip("Feels ${Units.both(data.feelsC, cFirst)}", text)
            }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HeroChip("H ${Units.both(data.today.hiC, cFirst)}", text)
                HeroChip("L ${Units.both(data.today.loC, cFirst)}", text)
            }
        }
    }
}

@Composable
private fun HeroChip(label: String, color: Color) {
    Text(
        label,
        style = MaterialTheme.typography.labelLarge,
        color = color,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

@Composable
private fun AlertPill(a: Alert) {
    val (fg, bg) = when (a.level) {
        AlertLevel.INFO -> MaterialTheme.colorScheme.onTertiaryContainer to MaterialTheme.colorScheme.tertiaryContainer
        AlertLevel.WARN -> MaterialTheme.colorScheme.onSecondaryContainer to MaterialTheme.colorScheme.secondaryContainer
        AlertLevel.DANGER -> MaterialTheme.colorScheme.onErrorContainer to MaterialTheme.colorScheme.errorContainer
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(a.icon), contentDescription = null, tint = fg, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Text(a.text, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, color = fg)
    }
}

private val RainBlue = Color(0xFF3D8BEA)

@Composable
private fun HourChip(label: String, cond: Condition, tempC: Double, rain: Int, cFirst: Boolean, highlight: Boolean) {
    val (first, second) = Units.pair(tempC, cFirst)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (highlight) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
            .padding(horizontal = 10.dp, vertical = 10.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = if (highlight) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        Icon(
            painterResource(cond.filledIcon), contentDescription = cond.label,
            tint = appTint(cond), modifier = Modifier.size(28.dp),
        )
        Spacer(Modifier.height(6.dp))
        Text(first, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Text(second, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            if (rain >= 20) "$rain%" else " ",
            style = MaterialTheme.typography.labelSmall,
            color = RainBlue,
        )
    }
}

/** One day: name, icon, rain chance, and the day's low-to-high range on a bar for the week. */
@Composable
private fun DayRow(name: String, day: Day, weekMin: Double, weekMax: Double, cFirst: Boolean) {
    val cond = Condition.fromCode(day.code, true)
    val (loA, loB) = Units.pair(day.loC, cFirst)
    val (hiA, hiB) = Units.pair(day.hiC, cFirst)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.width(58.dp))
        Box(Modifier.width(44.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    painterResource(cond.filledIcon), contentDescription = cond.label,
                    tint = appTint(cond), modifier = Modifier.size(26.dp),
                )
                if (day.precipProb >= 20) {
                    Text("${day.precipProb}%", style = MaterialTheme.typography.labelSmall, color = RainBlue)
                }
            }
        }
        Spacer(Modifier.width(8.dp))
        TempPair(loA, loB, Alignment.End)
        RangeBar(day.loC, day.hiC, weekMin, weekMax, Modifier.weight(1f).padding(horizontal = 10.dp))
        TempPair(hiA, hiB, Alignment.Start)
    }
}

@Composable
private fun TempPair(a: String, b: String, align: Alignment.Horizontal) {
    Column(horizontalAlignment = align, modifier = Modifier.width(44.dp)) {
        Text(a, style = MaterialTheme.typography.titleSmall)
        Text(b, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** A pill-shaped track with the day's range filled in temperature colors. */
@Composable
private fun RangeBar(lo: Double, hi: Double, min: Double, max: Double, modifier: Modifier) {
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val span = (max - min).takeIf { it > 0.1 } ?: 1.0
    val start = ((lo - min) / span).toFloat().coerceIn(0f, 1f)
    val end = ((hi - min) / span).toFloat().coerceIn(start, 1f)
    val loColor = Color(TempScale.color(lo))
    val hiColor = Color(TempScale.color(hi))
    Canvas(modifier.height(6.dp)) {
        val r = CornerRadius(size.height / 2)
        drawRoundRect(track, cornerRadius = r)
        val x0 = size.width * start
        val x1 = maxOf(size.width * end, x0 + size.height)
        drawRoundRect(
            brush = Brush.horizontalGradient(listOf(loColor, hiColor), startX = x0, endX = x1),
            topLeft = Offset(x0, 0f),
            size = Size(x1 - x0, size.height),
            cornerRadius = r,
        )
    }
}

// ---- Extra cards: air, sea, river ----

@Composable
private fun InfoCard(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = CardShape, colors = cardColors()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

/** US AQI bands: name and color. */
private fun aqiBand(aqi: Int): Pair<String, Color> = when {
    aqi <= 50 -> "Good" to Color(0xFF43A047)
    aqi <= 100 -> "Moderate" to Color(0xFFF9A825)
    aqi <= 150 -> "Unhealthy for sensitive groups" to Color(0xFFFB8C00)
    aqi <= 200 -> "Unhealthy" to Color(0xFFE53935)
    aqi <= 300 -> "Very unhealthy" to Color(0xFF8E24AA)
    else -> "Hazardous" to Color(0xFF7B1F2E)
}

private fun pollenLevel(v: Double): String = when {
    v < 10 -> "low"
    v < 50 -> "moderate"
    v < 200 -> "high"
    else -> "very high"
}

private fun ug(v: Double) = if (v.isNaN()) "–" else "${Math.round(v)} µg/m³"

/** Green-to-purple scale with a dot marking today's AQI. */
@Composable
private fun AqiMeter(aqi: Int) {
    val ring = MaterialTheme.colorScheme.surfaceContainerLow
    val stops = listOf(
        Color(0xFF43A047), Color(0xFFF9A825), Color(0xFFFB8C00),
        Color(0xFFE53935), Color(0xFF8E24AA), Color(0xFF7B1F2E),
    )
    val pos = (aqi / 300f).coerceIn(0f, 1f)
    Canvas(Modifier.fillMaxWidth().height(14.dp)) {
        val barH = 6.dp.toPx()
        val top = (size.height - barH) / 2
        drawRoundRect(
            brush = Brush.horizontalGradient(stops),
            topLeft = Offset(0f, top),
            size = Size(size.width, barH),
            cornerRadius = CornerRadius(barH / 2),
        )
        val x = (size.width * pos).coerceIn(size.height / 2, size.width - size.height / 2)
        drawCircle(ring, radius = size.height / 2, center = Offset(x, size.height / 2))
        drawCircle(Color.White, radius = size.height / 2 - 2.dp.toPx(), center = Offset(x, size.height / 2))
    }
}

@Composable
private fun AirCard(a: AirQuality) {
    InfoCard("Air quality") {
        a.usAqi?.let { aqi ->
            val (name, color) = aqiBand(aqi)
            Row(verticalAlignment = Alignment.Bottom) {
                Text("$aqi", fontSize = 40.sp, fontWeight = FontWeight.Light, color = color)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.padding(bottom = 6.dp)) {
                    Text(name, style = MaterialTheme.typography.titleSmall)
                    Text("US air quality index", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            AqiMeter(aqi)
            Spacer(Modifier.height(6.dp))
        }
        a.euAqi?.let { InfoRow("European AQI", "$it") }
        InfoRow("Fine particles (PM2.5)", ug(a.pm25))
        InfoRow("Particles (PM10)", ug(a.pm10))
        InfoRow("Ozone", ug(a.ozone))
        InfoRow("Nitrogen dioxide", ug(a.no2))
        if (!a.dust.isNaN() && a.dust >= 1) InfoRow("Dust", ug(a.dust))
        val pollen = a.pollen.filterValues { it >= 1 }
        if (pollen.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text("Pollen", style = MaterialTheme.typography.labelLarge)
            pollen.entries.sortedByDescending { it.value }.forEach { (plant, v) ->
                InfoRow(plant.replaceFirstChar { it.uppercase() }, "${pollenLevel(v)} (${Math.round(v)} grains/m³)")
            }
        }
    }
}

@Composable
private fun SeaCard(m: Marine, metricFirst: Boolean) {
    InfoCard("Sea") {
        if (!m.waveM.isNaN()) InfoRow("Waves now", Units.length(m.waveM, metricFirst, decimals = true))
        if (!m.maxWaveTodayM.isNaN()) InfoRow("Highest today", Units.length(m.maxWaveTodayM, metricFirst, decimals = true))
        if (!m.periodS.isNaN()) InfoRow("Wave period", "${Math.round(m.periodS)} seconds")
        if (!m.seaTempC.isNaN()) InfoRow("Water temperature", Units.both(m.seaTempC, metricFirst))
    }
}

@Composable
private fun RiverCard(r: River, metricFirst: Boolean) {
    InfoCard("Nearest river") {
        InfoRow("Flow today", Units.flow(r.todayM3s, metricFirst))
        InfoRow("Highest this week", "${Units.flow(r.peakM3s, metricFirst)} (${r.peakDay})")
        Text(
            when {
                r.rising -> "Rising: flow is expected to climb this week."
                r.days.last().second < r.todayM3s * 0.7 -> "Falling over the coming days."
                else -> "Steady this week."
            },
            style = MaterialTheme.typography.bodySmall,
        )
        Text("From the GloFAS river model.", style = MaterialTheme.typography.bodySmall)
    }
}

/** The big current temperature on the Weather tab, in the chosen order and sizes. */
@Composable
private fun AppMainTemps(tempC: Double, kelvin: Boolean, emphasis: Emphasis) {
    val (first, second) = Units.pair(tempC, emphasis.cFirst)
    val big = if (kelvin && !emphasis.firstBigger) 26.sp else 32.sp
    if (emphasis.firstBigger) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(first, fontSize = 40.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(10.dp))
            Column {
                Text(second, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                if (kelvin) Text(Units.k(tempC), style = MaterialTheme.typography.bodySmall)
            }
        }
    } else {
        Text(
            if (kelvin) "$first  $second  ${Units.k(tempC)}" else "$first  $second",
            fontSize = big,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** Chips for choosing which unit comes first (and bigger). Shared by Settings and Look. */
@Composable
fun UnitOrderChips(current: Emphasis, onChange: (Emphasis) -> Unit) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Emphasis.values().forEach { e ->
            FilterChip(
                selected = current == e,
                onClick = { onChange(e) },
                label = { Text(e.label) },
            )
        }
    }
}

// ================= Widgets =================

@Composable
private fun WidgetsTab(isPro: Boolean, tick: Int) {
    val ctx = LocalContext.current
    var resumeTick by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        resumeTick++
        onPauseOrDispose { }
    }
    val addWidget = rememberAddWidget()
    val ids = remember(resumeTick, tick) { PlacedWidgets.ids(ctx) }
    val sharedLook = remember(resumeTick) { ThemeStore.looks(ctx, 0).day }
    val style = remember(resumeTick) { StyleStore.get(ctx) }

    // Grow the sample widget from 1x1 to 4x3, over and over.
    val steps = listOf(1 to 1, 2 to 1, 2 to 2, 4 to 2, 4 to 3)
    var step by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_800)
            step = (step + 1) % steps.size
        }
    }
    val (cols, rows) = steps[step]

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionTitle("Your widgets")
        if (ids.isEmpty()) {
            Card(Modifier.fillMaxWidth(), shape = CardShape, colors = cardColors()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Add your first widget", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "It'll show up here, and you can change its place, colors and layout any time.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
        ids.forEach { id ->
            val (c, r) = PlacedWidgets.cells(ctx, id)
            val place = Places.byId(ctx, WidgetPrefs.placeFor(ctx, id))
            val ownLook = if (isPro) ThemeStore.forWidget(ctx, id) else null
            val look = ownLook ?: sharedLook
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(CardShape)
                    .clickable { openWidgetEditor(ctx, id) },
                shape = CardShape,
                colors = cardColors(),
            ) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    ThemeThumb(look, selected = false, locked = false)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "${place?.name ?: "My location"} · ${c}x$r",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            if (ownLook != null) "Its own look: ${ownLook.name}" else "Shared look: ${look.name}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Text(
                        "Edit",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                    )
                }
            }
        }
        Button(onClick = { addWidget(Places.HERE) }, modifier = Modifier.fillMaxWidth()) {
            Icon(painterResource(R.drawable.ic_add), contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Add a widget")
        }
        Text(
            "Pick a size, and the widget lands on your home screen. Then its editor opens so you " +
                "can choose the place and look.",
            style = MaterialTheme.typography.bodySmall,
        )

        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        SectionTitle("One widget, any size")
        Text(
            "Stretch a widget and it rearranges itself to fit, from a tiny 1x1 up to a big 4x3.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Wallpaper(Modifier.fillMaxWidth()) {
            Box(Modifier.size((WidgetGrid.CELL_W * 4).dp, (WidgetGrid.CELL_H * 3).dp)) {
                CellGrid(Modifier.fillMaxSize())
                LiveWidgetPreview(cols, rows, sharedLook, Places.HERE, refreshKey = tick to style)
            }
        }
        Text(
            "$cols x $rows",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        SectionTitle("Tips")
        listOf(
            "Resize: long-press a widget on your home screen and drag the dots on its edges. " +
                "Some phones (many Xiaomi and Huawei) can't resize, so add the size you want instead.",
            "Edit: tap a widget in Your widgets, or long-press it on your home screen and tap Settings.",
            "You can also add FahrenCel from your phone's own widget list, where each size is listed.",
        ).forEach { Text("•  $it", style = MaterialTheme.typography.bodyMedium) }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
}

/** Faint outline of home-screen cells behind the growing sample widget. */
@Composable
private fun CellGrid(modifier: Modifier) {
    Canvas(modifier) {
        val cw = size.width / 4
        val ch = size.height / 3
        for (c in 0 until 4) {
            for (r in 0 until 3) {
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.22f),
                    topLeft = Offset(c * cw + 4f, r * ch + 4f),
                    size = Size(cw - 8f, ch - 8f),
                    cornerRadius = CornerRadius(16f),
                    style = Stroke(width = 2f),
                )
            }
        }
    }
}

// ================= Settings =================

@Composable
private fun SettingsTab(
    kelvin: Boolean,
    isPro: Boolean,
    onKelvin: (Boolean) -> Unit,
    onPro: (Boolean) -> Unit,
    onRefresh: () -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var style by remember { mutableStateOf(StyleStore.get(ctx)) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SectionTitle("Units")
        Text("°F and °C are always shown together.", style = MaterialTheme.typography.bodyMedium)
        Text("Which comes first", style = MaterialTheme.typography.labelLarge)
        UnitOrderChips(style.emphasis) { e ->
            style = style.copy(emphasis = e)
            StyleStore.set(ctx, style)
            scope.launch { WidgetUpdater.refreshAll(ctx) }
        }
        Text(
            "\"First\" puts that unit on top or on the left everywhere. \"Big\" also makes it larger.",
            style = MaterialTheme.typography.bodySmall,
        )
        // Kelvin: a playful opt-in, with the checkbox right next to the question.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable { onKelvin(!kelvin) },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = kelvin, onCheckedChange = onKelvin)
            Column {
                Text("Feeling extra nerdy?", style = MaterialTheme.typography.bodyLarge)
                Text("Add Kelvin (K) next to °F and °C", style = MaterialTheme.typography.bodySmall)
            }
        }

        SettingsDivider()
        SectionTitle("Location")
        Text(
            if (LocationHelper.hasPermission(ctx)) {
                "\"My location\" follows your phone. It updates whenever you open the app."
            } else {
                "Location permission is off, so \"My location\" uses the last known place."
            },
            style = MaterialTheme.typography.bodyMedium,
        )
        OutlinedButton(onClick = onRefresh) {
            Icon(painterResource(R.drawable.ic_refresh), contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Refresh now")
        }

        SettingsDivider()
        SectionTitle("Developer")
        SwitchRow(
            "Unlock Pro (testing only)",
            "Remove before publishing. Real purchases come in stage 3.",
            isPro,
            onPro,
        )

        SettingsDivider()
        SectionTitle("About")
        Text(
            "Weather data by Open-Meteo.com (CC BY 4.0). Weather icons from Google's Material Symbols.",
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(16.dp))
    }
}
