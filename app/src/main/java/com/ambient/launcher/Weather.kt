package com.ambient.launcher

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

enum class WeatherKind { Clear, Partly, Cloud, Fog, Rain, Snow, Storm }

data class HourPoint(val label: String, val temp: Int, val kind: WeatherKind, val isDay: Boolean)

data class DayPoint(val label: String, val kind: WeatherKind, val hi: Int, val lo: Int)

data class WeatherData(
    val temp: Int,
    val kind: WeatherKind,
    val isDay: Boolean,
    val summary: String,
    val high: Int,
    val low: Int,
    val humidity: Int,
    val wind: Int,
    val sunrise: String,
    val sunset: String,
    val hours: List<HourPoint>,
    val days: List<DayPoint>,
    val rainSoon: Boolean
)

fun kindOf(code: Int): WeatherKind {
    return when (code) {
        0 -> WeatherKind.Clear
        1, 2 -> WeatherKind.Partly
        3 -> WeatherKind.Cloud
        45, 48 -> WeatherKind.Fog
        in 51..67 -> WeatherKind.Rain
        in 71..77 -> WeatherKind.Snow
        in 80..82 -> WeatherKind.Rain
        85, 86 -> WeatherKind.Snow
        in 95..99 -> WeatherKind.Storm
        else -> WeatherKind.Cloud
    }
}

fun summaryOf(kind: WeatherKind): String {
    return when (kind) {
        WeatherKind.Clear -> "Clear"
        WeatherKind.Partly -> "Partly cloudy"
        WeatherKind.Cloud -> "Cloudy"
        WeatherKind.Fog -> "Fog"
        WeatherKind.Rain -> "Rain"
        WeatherKind.Snow -> "Snow"
        WeatherKind.Storm -> "Thunderstorm"
    }
}

fun httpGet(url: String): String? {
    var conn: HttpURLConnection? = null
    return try {
        conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 10000
        conn.readTimeout = 15000
        conn.setRequestProperty("User-Agent", "AmbientHome/0.4")
        if (conn.responseCode in 200..299) {
            conn.inputStream.bufferedReader().use { it.readText() }
        } else {
            null
        }
    } catch (e: Exception) {
        null
    } finally {
        conn?.disconnect()
    }
}

suspend fun fetchWeather(city: City, fahrenheit: Boolean): WeatherData? = withContext(Dispatchers.IO) {
    try {
        val units = if (fahrenheit) "&temperature_unit=fahrenheit&wind_speed_unit=mph" else ""
        val url = "https://api.open-meteo.com/v1/forecast?latitude=${city.lat}&longitude=${city.lon}" +
            "&current=temperature_2m,relative_humidity_2m,weather_code,wind_speed_10m,is_day" +
            "&hourly=temperature_2m,weather_code,is_day" +
            "&daily=weather_code,temperature_2m_max,temperature_2m_min,sunrise,sunset" +
            "&timezone=auto&forecast_days=6" + units
        val body = httpGet(url) ?: return@withContext null
        val root = JSONObject(body)

        val cur = root.getJSONObject("current")
        val curTime = cur.getString("time")
        val kind = kindOf(cur.getInt("weather_code"))
        val isDay = cur.getInt("is_day") == 1

        val hourly = root.getJSONObject("hourly")
        val times = hourly.getJSONArray("time")
        val temps = hourly.getJSONArray("temperature_2m")
        val codes = hourly.getJSONArray("weather_code")
        val dayFlags = hourly.getJSONArray("is_day")

        var start = 0
        for (i in 0 until times.length()) {
            if (times.getString(i).take(13) >= curTime.take(13)) {
                start = i
                break
            }
        }
        val labelFmt = DateTimeFormatter.ofPattern("h a", Locale.getDefault())
        val hours = ArrayList<HourPoint>()
        val end = minOf(start + 8, times.length())
        for (i in start until end) {
            val label = if (i == start) "Now" else LocalDateTime.parse(times.getString(i)).format(labelFmt)
            hours.add(
                HourPoint(
                    label,
                    temps.getDouble(i).roundToInt(),
                    kindOf(codes.getInt(i)),
                    dayFlags.getInt(i) == 1
                )
            )
        }
        val rainSoon = hours.any { it.kind == WeatherKind.Rain || it.kind == WeatherKind.Storm }

        val daily = root.getJSONObject("daily")
        val dTimes = daily.getJSONArray("time")
        val dCodes = daily.getJSONArray("weather_code")
        val dMax = daily.getJSONArray("temperature_2m_max")
        val dMin = daily.getJSONArray("temperature_2m_min")
        val dRise = daily.getJSONArray("sunrise")
        val dSet = daily.getJSONArray("sunset")
        val days = ArrayList<DayPoint>()
        for (i in 0 until dTimes.length()) {
            val label = if (i == 0) {
                "Today"
            } else {
                LocalDate.parse(dTimes.getString(i)).dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())
            }
            days.add(
                DayPoint(
                    label,
                    kindOf(dCodes.getInt(i)),
                    dMax.getDouble(i).roundToInt(),
                    dMin.getDouble(i).roundToInt()
                )
            )
        }
        val timeFmt = DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())

        WeatherData(
            temp = cur.getDouble("temperature_2m").roundToInt(),
            kind = kind,
            isDay = isDay,
            summary = summaryOf(kind),
            high = days.first().hi,
            low = days.first().lo,
            humidity = cur.getInt("relative_humidity_2m"),
            wind = cur.getDouble("wind_speed_10m").roundToInt(),
            sunrise = LocalDateTime.parse(dRise.getString(0)).format(timeFmt),
            sunset = LocalDateTime.parse(dSet.getString(0)).format(timeFmt),
            hours = hours,
            days = days,
            rainSoon = rainSoon
        )
    } catch (e: Exception) {
        null
    }
}

/** Top world headlines from the BBC RSS feed. */
suspend fun fetchHeadlines(): List<String> = withContext(Dispatchers.IO) {
    try {
        val xml = httpGet("https://feeds.bbci.co.uk/news/world/rss.xml")
            ?: return@withContext emptyList<String>()
        val parser = android.util.Xml.newPullParser()
        parser.setInput(StringReader(xml))
        val out = ArrayList<String>()
        var inItem = false
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT && out.size < 6) {
            if (event == XmlPullParser.START_TAG) {
                if (parser.name == "item") {
                    inItem = true
                } else if (inItem && parser.name == "title") {
                    out.add(parser.nextText().trim())
                }
            } else if (event == XmlPullParser.END_TAG) {
                if (parser.name == "item") {
                    inItem = false
                }
            }
            event = parser.next()
        }
        out
    } catch (e: Exception) {
        emptyList()
    }
}
