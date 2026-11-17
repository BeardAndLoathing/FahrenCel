package com.example.dualweather

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.state.updateAppWidgetState

/** The one FahrenCel widget. It can be resized from 1x1 to 4x3 (and beyond). */
class WeatherWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WeatherWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WeatherScheduler.schedulePeriodic(context)
        WeatherScheduler.refreshNow(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        WeatherScheduler.cancel(context)
    }
}

object PlacedWidgets {
    /** IDs of every FahrenCel widget currently on the home screen. */
    fun ids(ctx: Context): List<Int> =
        AppWidgetManager.getInstance(ctx)
            .getAppWidgetIds(ComponentName(ctx, WeatherWidgetReceiver::class.java))
            .toList()

    /** Approximate size in home-screen cells, e.g. 4 x 2. */
    fun cells(ctx: Context, appWidgetId: Int): Pair<Int, Int> {
        val o = AppWidgetManager.getInstance(ctx).getAppWidgetOptions(appWidgetId)
        val w = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)
        val h = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0)
        if (w == 0 || h == 0) return 4 to 2
        return WidgetGrid.cols(w.toFloat()) to WidgetGrid.rows(h.toFloat())
    }
}

/** Which layout a given size gets. Shared by the widget and the app's previews. */
object WidgetGrid {
    // Approximate home-screen cells (Samsung 4x5 grid is about 90 x 95 dp per cell).
    const val CELL_W = 84f
    const val CELL_H = 92f

    fun cols(widthDp: Float): Int = when {
        widthDp < 130f -> 1
        widthDp < 220f -> 2
        widthDp < 310f -> 3
        else -> 4
    }

    fun rows(heightDp: Float): Int = when {
        heightDp < 140f -> 1
        heightDp < 240f -> 2
        else -> 3
    }
}

/**
 * Redraws widgets. Bumping a small version number in each widget's state makes sure
 * the widget re-reads weather, units and looks even if it is already running.
 */
object WidgetUpdater {
    val VERSION = longPreferencesKey("dw_version")

    suspend fun refreshAll(ctx: Context) {
        val ids = GlanceAppWidgetManager(ctx).getGlanceIds(WeatherWidget::class.java)
        ids.forEach { id ->
            updateAppWidgetState(ctx, id) { it[VERSION] = System.currentTimeMillis() }
            WeatherWidget().update(ctx, id)
        }
    }

    suspend fun refresh(ctx: Context, appWidgetId: Int) {
        val id = try {
            GlanceAppWidgetManager(ctx).getGlanceIdBy(appWidgetId)
        } catch (e: Exception) {
            return
        }
        updateAppWidgetState(ctx, id) { it[VERSION] = System.currentTimeMillis() }
        WeatherWidget().update(ctx, id)
    }
}
