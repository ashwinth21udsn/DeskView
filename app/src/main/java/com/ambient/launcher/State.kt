package com.ambient.launcher

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import java.io.File

enum class Overlay { None, Drawer, Settings, ClockEditor, Widgets }

enum class Period { Morning, Day, Evening, Night }

fun periodOf(hour: Int): Period {
    return when {
        hour in 5..10 -> Period.Morning
        hour in 11..16 -> Period.Day
        hour in 17..20 -> Period.Evening
        else -> Period.Night
    }
}

data class WidgetDef(val id: String, val name: String, val span: Int)

val WIDGETS = listOf(
    WidgetDef("greet", "Greeting", 2),
    WidgetDef("forecast", "Forecast", 2),
    WidgetDef("agenda", "Agenda", 2),
    WidgetDef("special", "Today’s special", 2),
    WidgetDef("news", "Headlines", 2),
    WidgetDef("photo", "Photos", 1),
    WidgetDef("sun", "Sun and moon", 1),
    WidgetDef("countdown", "Countdown", 1)
)

val CLOCK_STYLES = listOf(
    "light" to "Light",
    "bold" to "Bold",
    "glass" to "Glass",
    "serif" to "Serif",
    "mono" to "Mono",
    "stack" to "Stacked",
    "outline" to "Outline",
    "analog" to "Analog"
)

val CLOCK_COLORS = listOf(
    "auto", "#FFFFFF", "#FF9F0A", "#FF453A", "#30D158", "#64D2FF", "#BF5AF2", "#FFD60A"
)

val ACCENTS = listOf("#FF9F0A", "#FF375F", "#64D2FF", "#30D158", "#BF5AF2", "#FFD60A")

data class City(val name: String, val lat: Double, val lon: Double)

val CITIES = listOf(
    City("Colombo", 6.9271, 79.8612),
    City("Kandy", 7.2906, 80.6337),
    City("Galle", 6.0535, 80.2210),
    City("Jaffna", 9.6615, 80.0255),
    City("London", 51.5072, -0.1276),
    City("New York", 40.7128, -74.0060),
    City("Dubai", 25.2048, 55.2708),
    City("Singapore", 1.3521, 103.8198),
    City("Sydney", -33.8688, 151.2093)
)

/** Everything the screens share. One instance lives for the life of the activity. */
class LauncherState(val ctx: Context) {
    val prefs = Prefs(ctx)

    var weather by mutableStateOf<WeatherData?>(null)
    var headlines by mutableStateOf<List<String>>(emptyList())
    var apps by mutableStateOf<List<AppItem>>(emptyList())
    var events by mutableStateOf<List<CalEvent>>(emptyList())
    var calendarAllowed by mutableStateOf(hasCalendarPermission(ctx))
    var photos by mutableStateOf<List<File>>(ImageStore.listPhotos(ctx))
    var overlay by mutableStateOf(Overlay.None)
    var homeTick by mutableStateOf(0)

    // Set by the activity once the system pickers exist.
    var requestCalendar: () -> Unit = {}
    var pickWallpaper: () -> Unit = {}
    var pickPhotos: () -> Unit = {}

    fun goHome() {
        overlay = Overlay.None
        homeTick += 1
    }

    fun refreshPhotos() {
        photos = ImageStore.listPhotos(ctx)
    }

    suspend fun reloadApps() {
        apps = loadApps(ctx.packageManager, ctx.packageName)
    }

    fun enabledWidgets(): List<WidgetDef> {
        val ids = prefs.widgets.split(",").toSet()
        return WIDGETS.filter { it.id in ids }
    }

    fun setWidget(id: String, on: Boolean) {
        val ids = prefs.widgets.split(",").filter { it.isNotEmpty() }.toMutableSet()
        if (on) ids.add(id) else ids.remove(id)
        prefs.widgets = WIDGETS.filter { it.id in ids }.joinToString(",") { it.id }
    }
}

val LocalLauncher = staticCompositionLocalOf<LauncherState> {
    error("LauncherState was not provided")
}
