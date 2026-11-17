package com.example.dualweather

import android.content.Context
import org.json.JSONObject

enum class LayoutPreset(val label: String) { MINIMAL("Minimal"), CLASSIC("Classic"), DETAILED("Detailed"), CUSTOM("Custom") }

/** Which unit comes first, and whether it's shown bigger. Applies to the app and widgets. */
enum class Emphasis(val label: String) {
    EQUAL("°F first"),
    C_FIRST("°C first"),
    F_BIG("°F big"),
    C_BIG("°C big");

    /** Celsius leads. */
    val cFirst: Boolean get() = this == C_FIRST || this == C_BIG

    /** The leading unit is drawn larger than the other. */
    val firstBigger: Boolean get() = this == F_BIG || this == C_BIG
}

/** System fonts available to home-screen widgets. */
enum class FontChoice(val label: String, val family: String, val premium: Boolean) {
    DEFAULT("Default", "sans-serif", false),
    LIGHT("Light", "sans-serif-light", false),
    CONDENSED("Condensed", "sans-serif-condensed", false),
    THIN("Thin", "sans-serif-thin", true),
    HEAVY("Heavy", "sans-serif-black", true),
    SERIF("Serif", "serif", true),
    MONO("Mono", "monospace", true),
    HANDWRITTEN("Handwritten", "cursive", true),
}

/** What the widget shows and how its text looks. Applies to all widgets. */
data class WidgetStyle(
    val layout: LayoutPreset = LayoutPreset.DETAILED,
    val showPlace: Boolean = true,
    val showFeels: Boolean = true,
    val showHiLo: Boolean = true,
    val showAlert: Boolean = true,
    val showHourly: Boolean = true,
    val showDaily: Boolean = true,
    val showUpdated: Boolean = true,
    val emphasis: Emphasis = Emphasis.EQUAL,
    val font: FontChoice = FontChoice.DEFAULT,
    val boldNumbers: Boolean = true,
    /** Color the temperatures from icy blue to hot red. */
    val tempColors: Boolean = false,
    val filledIcons: Boolean = false,
) {
    /** Apply one of the layout presets (sets the show/hide switches). */
    fun withPreset(p: LayoutPreset): WidgetStyle = when (p) {
        LayoutPreset.MINIMAL -> copy(
            layout = p, showPlace = true, showFeels = false, showHiLo = false,
            showAlert = false, showHourly = false, showDaily = false, showUpdated = false,
        )
        LayoutPreset.CLASSIC -> copy(
            layout = p, showPlace = true, showFeels = true, showHiLo = true,
            showAlert = true, showHourly = false, showDaily = true, showUpdated = false,
        )
        LayoutPreset.DETAILED -> copy(
            layout = p, showPlace = true, showFeels = true, showHiLo = true,
            showAlert = true, showHourly = true, showDaily = true, showUpdated = true,
        )
        LayoutPreset.CUSTOM -> copy(layout = p)
    }

    /** Fonts marked Pro fall back to the default without Pro. */
    fun gated(isPro: Boolean): WidgetStyle = if (font.premium && !isPro) copy(font = FontChoice.DEFAULT) else this

    fun toJson(): String = JSONObject().apply {
        put("layout", layout.name)
        put("place", showPlace)
        put("feels", showFeels)
        put("hilo", showHiLo)
        put("alert", showAlert)
        put("hourly", showHourly)
        put("daily", showDaily)
        put("updated", showUpdated)
        put("emphasis", emphasis.name)
        put("font", font.name)
        put("bold", boldNumbers)
        put("tempColors", tempColors)
        put("filled", filledIcons)
    }.toString()

    companion object {
        fun fromJson(s: String?): WidgetStyle {
            if (s == null) return WidgetStyle()
            return try {
                val o = JSONObject(s)
                WidgetStyle(
                    layout = runCatching { LayoutPreset.valueOf(o.optString("layout")) }.getOrDefault(LayoutPreset.DETAILED),
                    showPlace = o.optBoolean("place", true),
                    showFeels = o.optBoolean("feels", true),
                    showHiLo = o.optBoolean("hilo", true),
                    showAlert = o.optBoolean("alert", true),
                    showHourly = o.optBoolean("hourly", true),
                    showDaily = o.optBoolean("daily", true),
                    showUpdated = o.optBoolean("updated", true),
                    emphasis = runCatching { Emphasis.valueOf(o.optString("emphasis")) }.getOrDefault(Emphasis.EQUAL),
                    font = runCatching { FontChoice.valueOf(o.optString("font")) }.getOrDefault(FontChoice.DEFAULT),
                    boldNumbers = o.optBoolean("bold", true),
                    tempColors = o.optBoolean("tempColors", false),
                    filledIcons = o.optBoolean("filled", false),
                )
            } catch (e: Exception) {
                WidgetStyle()
            }
        }
    }
}

object StyleStore {
    private const val PREFS = "style"

    fun get(ctx: Context): WidgetStyle =
        WidgetStyle.fromJson(ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("style", null))

    fun set(ctx: Context, s: WidgetStyle) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("style", s.toJson()).apply()
    }
}
