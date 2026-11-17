@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.example.dualweather

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.ui.res.painterResource
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * The widget editor. Opens when a widget is added (from the app or the phone's widget
 * list), from "Your widgets" in the app, and from the widget's long-press "Settings".
 */
class WidgetConfigActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val widgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        // The widget stays on the home screen even if this screen is closed with Back.
        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        // Already editing it, so the app doesn't need to open the editor again.
        WidgetPrefs.clearPendingEdit(this, widgetId)
        // Make sure background updates are running for a newly added widget.
        WeatherScheduler.schedulePeriodic(this)

        enableEdgeToEdge()
        setContent {
            AppTheme { WidgetEditorScreen(widgetId) { finish() } }
        }
    }
}

private enum class EditorSection(val label: String) { PLACE("Place"), LOOK("Look"), LAYOUT("Layout") }

@Composable
private fun WidgetEditorScreen(widgetId: Int, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val isPro = remember { Pro.isPro(ctx) }
    var section by rememberSaveable { mutableStateOf(EditorSection.PLACE) }
    var showSearch by remember { mutableStateOf(false) }
    var showPro by remember { mutableStateOf(false) }
    var toRemove by remember { mutableStateOf<Place?>(null) }
    val cells = remember { PlacedWidgets.cells(ctx, widgetId) }

    // ---- Place
    var placesTick by remember { mutableIntStateOf(0) }
    val places = remember(placesTick) { Places.all(ctx) }
    var placeId by remember { mutableStateOf(WidgetPrefs.placeFor(ctx, widgetId)) }

    // ---- Look: either this widget's own look (Pro) or the look shared by all widgets
    var own by remember { mutableStateOf(isPro && ThemeStore.forWidget(ctx, widgetId) != null) }
    var ownLook by remember { mutableStateOf(ThemeStore.forWidget(ctx, widgetId) ?: ThemeStore.global(ctx)) }
    var follow by remember { mutableStateOf(ThemeStore.followSystem(ctx)) }
    var editingDark by remember { mutableStateOf(false) }
    var lightLook by remember { mutableStateOf(ThemeStore.global(ctx)) }
    var darkLook by remember { mutableStateOf(ThemeStore.dark(ctx)) }
    var draft by remember { mutableStateOf<WidgetTheme?>(null) }

    // ---- Layout
    var style by remember { mutableStateOf(StyleStore.get(ctx)) }
    var ownEmphasis by remember { mutableStateOf(if (isPro) WidgetPrefs.emphasisFor(ctx, widgetId) else null) }

    val sharedSlot = if (follow && editingDark) darkLook else lightLook
    val currentLook = if (own) ownLook else sharedSlot
    val previewLook = draft ?: currentLook

    /** Redraws all widgets, since shared looks and layout affect every one. */
    fun redraw() {
        scope.launch { WidgetUpdater.refreshAll(ctx) }
    }

    fun saveLook(t: WidgetTheme) {
        when {
            own -> {
                ownLook = t
                ThemeStore.setForWidget(ctx, widgetId, t)
            }
            follow && editingDark -> {
                darkLook = t
                ThemeStore.setDark(ctx, t)
            }
            else -> {
                lightLook = t
                ThemeStore.setGlobal(ctx, t)
            }
        }
        redraw()
    }

    fun choosePlace(id: String) {
        placeId = id
        WidgetPrefs.setPlace(ctx, widgetId, id)
        redraw()
    }

    val pickPhoto = rememberPhotoPicker { name ->
        val d = draft
        if (d != null) draft = d.copy(bgMode = BgMode.PHOTO, photo = name) else saveLook(Themes.PHOTO.copy(photo = name))
    }
    val choosePhoto: () -> Unit = { if (isPro) pickPhoto() else showPro = true }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Edit widget") }) },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Button(
                    onClick = onDone,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                ) { Text("Done") }
            }
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            // Pinned live preview of this widget, plus the section switcher.
            Surface(tonalElevation = 2.dp) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Wallpaper {
                        LiveWidgetPreview(
                            cols = cells.first,
                            rows = cells.second,
                            theme = previewLook,
                            placeId = placeId,
                            refreshKey = style,
                            emphasis = ownEmphasis,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        EditorSection.values().forEach { s ->
                            FilterChip(
                                selected = section == s,
                                onClick = { section = s },
                                label = { Text(s.label) },
                            )
                        }
                    }
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when (section) {
                    EditorSection.PLACE -> {
                        SectionTitle("Which place this widget shows")
                        places.forEach { p ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { choosePlace(p.id) },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(selected = placeId == p.id, onClick = { choosePlace(p.id) })
                                Text(
                                    if (p.isHere) "My location (${p.name})" else p.fullName,
                                    modifier = Modifier.weight(1f),
                                )
                                if (!p.isHere) {
                                    IconButton(onClick = { toRemove = p }) {
                                        Icon(
                                            painterResource(R.drawable.ic_delete),
                                            contentDescription = "Remove ${p.name}",
                                        )
                                    }
                                }
                            }
                        }
                        TextButton(onClick = { if (Places.canAddMore(ctx)) showSearch = true else showPro = true }) {
                            Text("+ Add a city")
                        }
                    }

                    EditorSection.LOOK -> {
                        if (isPro) {
                            SwitchRow(
                                "Give this widget its own look",
                                "Off: it shares one look with your other widgets",
                                own,
                            ) { on ->
                                own = on
                                draft = null
                                ThemeStore.setForWidget(ctx, widgetId, if (on) ownLook else null)
                                redraw()
                            }
                        } else {
                            Card(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(12.dp)) {
                                    Text(
                                        "This look applies to all your widgets.",
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                    TextButton(onClick = { showPro = true }) {
                                        Text("Pro lets each widget have its own")
                                    }
                                }
                            }
                        }

                        if (!own) {
                            SwitchRow(
                                "Match phone's light/dark mode",
                                "One look in light mode, another in dark mode",
                                follow,
                            ) { on ->
                                ThemeStore.setFollowSystem(ctx, on)
                                follow = on
                                lightLook = ThemeStore.global(ctx)
                                darkLook = ThemeStore.dark(ctx)
                                editingDark = false
                                draft = null
                                redraw()
                            }
                            if (follow) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    FilterChip(
                                        selected = !editingDark,
                                        onClick = { editingDark = false; draft = null },
                                        label = { Text("Light-mode look") },
                                    )
                                    FilterChip(
                                        selected = editingDark,
                                        onClick = { editingDark = true; draft = null },
                                        label = { Text("Dark-mode look") },
                                    )
                                }
                            }
                        }

                        PresetGrid(
                            selected = previewLook,
                            isPro = isPro,
                            onPick = { t ->
                                if (t.premium && !isPro) {
                                    showPro = true
                                } else {
                                    draft = null
                                    saveLook(t)
                                }
                            },
                            onPhoto = choosePhoto,
                        )

                        val d = draft
                        if (d == null) {
                            OutlinedButton(onClick = { if (isPro) draft = currentLook.asCustom() else showPro = true }) {
                                Text(if (isPro) "Customize colors, shapes and border…" else "Customize… (Pro)")
                            }
                        } else {
                            EditorApplyCancel(onApply = { saveLook(d); draft = null }, onCancel = { draft = null })
                            ThemeEditor(d, onChange = { draft = it }, onPickPhoto = choosePhoto)
                            EditorApplyCancel(onApply = { saveLook(d); draft = null }, onCancel = { draft = null })
                        }
                    }

                    EditorSection.LAYOUT -> {
                        Text(
                            "Layout and text settings apply to all your widgets.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        StyleEditor(
                            s = style,
                            isPro = isPro,
                            onChange = {
                                style = it
                                StyleStore.set(ctx, it)
                                redraw()
                            },
                            onLocked = { showPro = true },
                        )

                        HorizontalDivider()
                        SectionTitle("This widget's unit order")
                        if (!isPro) {
                            Text(
                                "Uses the order above. Setting it for each widget is part of Pro.",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        } else {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(
                                    selected = ownEmphasis == null,
                                    onClick = {
                                        ownEmphasis = null
                                        WidgetPrefs.setEmphasis(ctx, widgetId, null)
                                        redraw()
                                    },
                                    label = { Text("Same as above") },
                                )
                                Emphasis.values().forEach { e ->
                                    FilterChip(
                                        selected = ownEmphasis == e,
                                        onClick = {
                                            ownEmphasis = e
                                            WidgetPrefs.setEmphasis(ctx, widgetId, e)
                                            redraw()
                                        },
                                        label = { Text(e.label) },
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    if (showSearch) {
        PlaceSearchDialog(
            onPicked = { p ->
                Places.add(ctx, p)
                showSearch = false
                placesTick++
                choosePlace(p.id)
                WeatherScheduler.refreshNow(ctx)
            },
            onDismiss = { showSearch = false },
        )
    }
    toRemove?.let { p ->
        ConfirmRemoveCityDialog(
            place = p,
            onConfirm = {
                Places.remove(ctx, p.id)
                toRemove = null
                placesTick++
                if (placeId == p.id) choosePlace(Places.HERE) else redraw()
            },
            onDismiss = { toRemove = null },
        )
    }
    if (showPro) ProDialog { showPro = false }
}

@Composable
private fun EditorApplyCancel(onApply: () -> Unit, onCancel: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = onApply) { Text("Apply") }
        TextButton(onClick = onCancel) { Text("Cancel") }
    }
}
