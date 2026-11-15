package com.ambient.launcher

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import kotlin.reflect.KProperty

/** A String setting that is observable by Compose and saved to SharedPreferences. */
class StringPref(private val sp: SharedPreferences, private val key: String, default: String) {
    private var state by mutableStateOf(sp.getString(key, default) ?: default)

    operator fun getValue(thisRef: Any?, property: KProperty<*>): String = state

    operator fun setValue(thisRef: Any?, property: KProperty<*>, value: String) {
        state = value
        sp.edit().putString(key, value).apply()
    }
}

class BoolPref(private val sp: SharedPreferences, private val key: String, default: Boolean) {
    private var state by mutableStateOf(sp.getBoolean(key, default))

    operator fun getValue(thisRef: Any?, property: KProperty<*>): Boolean = state

    operator fun setValue(thisRef: Any?, property: KProperty<*>, value: Boolean) {
        state = value
        sp.edit().putBoolean(key, value).apply()
    }
}

class IntPref(private val sp: SharedPreferences, private val key: String, default: Int) {
    private var state by mutableStateOf(sp.getInt(key, default))

    operator fun getValue(thisRef: Any?, property: KProperty<*>): Int = state

    operator fun setValue(thisRef: Any?, property: KProperty<*>, value: Int) {
        state = value
        sp.edit().putInt(key, value).apply()
    }
}

class FloatPref(private val sp: SharedPreferences, private val key: String, default: Float) {
    private var state by mutableStateOf(sp.getFloat(key, default))

    operator fun getValue(thisRef: Any?, property: KProperty<*>): Float = state

    operator fun setValue(thisRef: Any?, property: KProperty<*>, value: Float) {
        state = value
        sp.edit().putFloat(key, value).apply()
    }
}

class Prefs(context: Context) {
    private val sp: SharedPreferences =
        context.getSharedPreferences("ambient_home", Context.MODE_PRIVATE)

    // Clock
    var clockStyle: String by StringPref(sp, "clockStyle", "light")
    var clockColor: String by StringPref(sp, "clockColor", "auto")
    var clock24: Boolean by BoolPref(sp, "clock24", false)
    var clockDate: Boolean by BoolPref(sp, "clockDate", true)

    // Look
    var wallpaper: String by StringPref(sp, "wallpaper", "auto")
    var wallpaperRev: Int by IntPref(sp, "wallpaperRev", 0)
    var dim: Float by FloatPref(sp, "dim", 0.18f)
    var accent: String by StringPref(sp, "accent", "#FF9F0A")

    // Data
    var cityIndex: Int by IntPref(sp, "cityIndex", 0)
    var fahrenheit: Boolean by BoolPref(sp, "fahrenheit", false)

    // Briefing widgets, as a comma separated list of ids
    var widgets: String by StringPref(sp, "widgets", "greet,forecast,agenda,special,news,photo,sun")

    // Display
    var keepAwake: Boolean by BoolPref(sp, "keepAwake", false)
}

val DefaultAccent = Color(0xFFFF9F0A)

fun parseColor(hex: String, fallback: Color): Color {
    return try {
        Color(android.graphics.Color.parseColor(hex))
    } catch (e: Exception) {
        fallback
    }
}
