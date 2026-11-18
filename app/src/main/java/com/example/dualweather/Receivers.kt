package com.example.dualweather

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.state.updateAppWidgetState

/**
 * The FahrenCel widget. The main entry is 4x2; the sized entries below exist for home
 * screens that can't resize widgets (many Xiaomi, Huawei and older launchers), so people
 * can pick the size up front. All of them draw the same widget and can still be resized
 * where the home screen allows it.
 */
open class WeatherWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WeatherWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WeatherScheduler.schedulePeriodic(context)
        WeatherScheduler.refreshNow(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        // Only stop background updates when no FahrenCel widget of any size is left.
        if (PlacedWidgets.ids(context).isEmpty()) WeatherScheduler.cancel(context)
    }
}

class Widget1x1Receiver : WeatherWidgetReceiver()
class Widget2x1Receiver : WeatherWidgetReceiver()
class Widget4x1Receiver : WeatherWidgetReceiver()
class Widget2x2Receiver : WeatherWidgetReceiver()
class Widget4x3Receiver : WeatherWidgetReceiver()

/** The sizes offered in the app's "Add a widget" picker. */
enum class WidgetSize(val cols: Int, val rows: Int, val label: String, val receiver: Class<out WeatherWidgetReceiver>) {
    S1x1(1, 1, "Tiny", Widget1x1Receiver::class.java),
    S2x1(2, 1, "Slim", Widget2x1Receiver::class.java),
    S4x1(4, 1, "Wide bar", Widget4x1Receiver::class.java),
    S2x2(2, 2, "Square", Widget2x2Receiver::class.java),
    S4x2(4, 2, "Medium", WeatherWidgetReceiver::class.java),
    S4x3(4, 3, "Large", Widget4x3Receiver::class.java),
}

object PlacedWidgets {
    /** IDs of every FahrenCel widget currently on the home screen, any size. */
    fun ids(ctx: Context): List<Int> {
        val mgr = AppWidgetManager.getInstance(ctx)
        return WidgetSize.values().flatMap { mgr.getAppWidgetIds(ComponentName(ctx, it.receiver)).toList() }
    }

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
