@file:OptIn(ExperimentalFoundationApi::class)

package com.ambient.launcher

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.io.File
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.sqrt
import kotlin.random.Random

private val White70 = Color.White.copy(alpha = 0.7f)
private val White55 = Color.White.copy(alpha = 0.55f)

// ---------- Small shared pieces ----------

@Composable
fun Label(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        modifier = modifier,
        color = Color.White.copy(alpha = 0.6f),
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.sp
    )
}

@Composable
private fun DateChip(text: String) {
    val accent = parseColor(LocalLauncher.current.prefs.accent, DefaultAccent)
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(accent)
            .padding(horizontal = 12.dp, vertical = 2.dp)
    ) {
        Text(text, color = Color.Black, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

private fun fmtMillis(ms: Long, use24: Boolean): String {
    val t = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalTime()
    return t.format(DateTimeFormatter.ofPattern(if (use24) "HH:mm" else "h:mm a", Locale.getDefault()))
}

private fun millisOf(time: LocalDateTime): Long {
    return time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
}

private fun greetingFor(period: Period): String {
    return when (period) {
        Period.Morning -> "Good morning"
        Period.Day -> "Good afternoon"
        Period.Evening -> "Good evening"
        Period.Night -> "Good night"
    }
}

// ---------- Clock face ----------

@Composable
fun ClockFace(nowState: State<LocalDateTime>, minute: LocalDateTime, period: Period) {
    val st = LocalLauncher.current
    val prefs = st.prefs
    val haptic = LocalHapticFeedback.current
    val date = minute.toLocalDate()
    val special = remember(date) { specialFor(date) }
    val accent = parseColor(prefs.accent, DefaultAccent)
    val color = clockColorFor(prefs.clockColor, period)
    val dateText = remember(date) {
        date.format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.getDefault()))
    }
    val emoji = if (special != null) "  " + special.emoji else ""

    Column(
        Modifier
            .fillMaxSize()
            .padding(top = 76.dp, bottom = 112.dp, start = 40.dp, end = 40.dp)
    ) {
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .combinedClickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                    onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        st.overlay = Overlay.ClockEditor
                    }
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            ClockView(
                prefs.clockStyle,
                nowState.value,
                prefs.clock24,
                color,
                accent,
                Modifier.fillMaxWidth().weight(1f)
            )
            if (prefs.clockDate) {
                Text(dateText + emoji, color = accent, fontSize = 28.sp, fontWeight = FontWeight.Medium)
            }
            Text(
                "Touch and hold the clock to customize",
                color = Color.White.copy(alpha = 0.4f),
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        Spacer(Modifier.height(16.dp))
        GlanceRow(minute)
    }
}

@Composable
private fun GlanceRow(minute: LocalDateTime) {
    val st = LocalLauncher.current
    val use24 = st.prefs.clock24
    val weather = st.weather
    val nowMs = millisOf(minute)
    val next = st.events.firstOrNull { !it.allDay && it.end > nowMs }
    val date = minute.toLocalDate()
    val special = specialFor(date)
    val upcoming = if (special == null) nextSpecial(date) else null

    Row(
        Modifier.fillMaxWidth().height(116.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        GlassCard(Modifier.weight(1f).fillMaxHeight()) {
            Label("Weather")
            Spacer(Modifier.weight(1f))
            if (weather == null) {
                Text("Loading…", color = White70, fontSize = 16.sp)
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    WeatherIcon(weather.kind, weather.isDay, 34.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("${weather.temp}°", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Medium)
                }
                Text(weather.summary, color = White70, fontSize = 13.sp)
            }
        }
        GlassCard(Modifier.weight(1f).fillMaxHeight()) {
            Label("Next")
            Spacer(Modifier.weight(1f))
            if (!st.calendarAllowed) {
                Text(
                    "Allow calendar",
                    color = parseColor(st.prefs.accent, DefaultAccent),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable { st.requestCalendar() }
                )
            } else if (next == null) {
                Text("Nothing else today", color = Color.White, fontSize = 16.sp)
            } else {
                Text(
                    next.title,
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(fmtMillis(next.begin, use24), color = White70, fontSize = 13.sp)
            }
        }
        GlassCard(Modifier.weight(1f).fillMaxHeight()) {
            Label(if (special != null) "Today’s special" else "Next special")
            Spacer(Modifier.weight(1f))
            if (special != null) {
                Text(
                    special.emoji + " " + special.name,
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            } else if (upcoming != null) {
                Text(
                    upcoming.special.emoji + " " + upcoming.special.name,
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text("in ${upcoming.days} days", color = White70, fontSize = 13.sp)
            }
        }
        GlassCard(Modifier.weight(1f).fillMaxHeight()) {
            Label("Headlines")
            Spacer(Modifier.weight(1f))
            val first = st.headlines.firstOrNull()
            Text(
                first ?: "Loading…",
                color = if (first == null) White70 else Color.White,
                fontSize = 14.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ---------- Briefing face with widgets ----------

@Composable
fun BriefingFace(minute: LocalDateTime, period: Period) {
    val st = LocalLauncher.current
    val list = st.enabledWidgets()

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .padding(top = 76.dp, bottom = 112.dp, start = 40.dp, end = 40.dp)
    ) {
        val cols = if (maxWidth > 900.dp) 4 else if (maxWidth > 560.dp) 3 else 2
        if (list.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "No widgets yet. Tap the pencil at the top to add some.",
                    color = White70,
                    fontSize = 18.sp
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(cols),
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(
                    list,
                    key = { it.id },
                    span = { item -> GridItemSpan(minOf(item.span, cols)) }
                ) { widget ->
                    WidgetHost(widget.id, minute, period, Modifier.height(188.dp))
                }
            }
        }
    }
}

@Composable
private fun WidgetHost(id: String, minute: LocalDateTime, period: Period, modifier: Modifier) {
    when (id) {
        "greet" -> GreetingWidget(minute, period, modifier)
        "forecast" -> ForecastWidget(modifier)
        "agenda" -> AgendaWidget(minute, modifier)
        "special" -> SpecialWidget(minute, modifier)
        "news" -> NewsWidget(modifier)
        "photo" -> Box(modifier.glass()) { PhotoSlideshow(Modifier.fillMaxSize()) }
        "sun" -> SunWidget(minute, modifier)
        "countdown" -> CountdownWidget(minute, modifier)
        else -> Box(modifier)
    }
}

@Composable
private fun GreetingWidget(minute: LocalDateTime, period: Period, modifier: Modifier) {
    val st = LocalLauncher.current
    val weather = st.weather
    val nowMs = millisOf(minute)
    val left = st.events.count { it.allDay || it.end > nowMs }
    val dateText = minute.format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.getDefault()))
    val weatherLine = if (weather == null) {
        "Loading the weather…"
    } else {
        "${weather.temp}° and ${weather.summary.lowercase()}."
    }
    val eventLine = if (!st.calendarAllowed) {
        ""
    } else if (left == 0) {
        " No more events today."
    } else {
        " $left " + (if (left == 1) "event" else "events") + " left today."
    }
    val umbrella = if (weather != null && weather.rainSoon) " Rain is likely soon. Take an umbrella." else ""

    GlassCard(modifier) {
        Label(dateText)
        Spacer(Modifier.height(6.dp))
        Text(greetingFor(period), color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text(
            weatherLine + eventLine + umbrella,
            color = White70,
            fontSize = 15.sp,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ForecastWidget(modifier: Modifier) {
    val st = LocalLauncher.current
    val weather = st.weather
    val city = CITIES[st.prefs.cityIndex.coerceIn(0, CITIES.size - 1)]
    GlassCard(modifier) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Label("Forecast")
            Text(city.name, color = White55, fontSize = 12.sp)
        }
        Spacer(Modifier.weight(1f))
        if (weather == null) {
            Text("Loading…", color = White70, fontSize = 16.sp)
        } else {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                WeatherIcon(weather.kind, weather.isDay, 52.dp)
                Spacer(Modifier.width(10.dp))
                Text("${weather.temp}°", color = Color.White, fontSize = 46.sp, fontWeight = FontWeight.Light)
                Spacer(Modifier.width(18.dp))
                for (h in weather.hours.take(5)) {
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(h.label, color = White55, fontSize = 12.sp)
                        WeatherIcon(h.kind, h.isDay, 26.dp)
                        Text("${h.temp}°", color = Color.White, fontSize = 14.sp)
                    }
                }
            }
        }
        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun AgendaWidget(minute: LocalDateTime, modifier: Modifier) {
    val st = LocalLauncher.current
    val use24 = st.prefs.clock24
    val nowMs = millisOf(minute)
    val nextIdx = st.events.indexOfFirst { it.end > nowMs }
    GlassCard(modifier) {
        Label("Today")
        Spacer(Modifier.height(8.dp))
        if (!st.calendarAllowed) {
            Text("See today’s events from your calendar here.", color = White70, fontSize = 14.sp)
            Spacer(Modifier.height(10.dp))
            PillButton("Allow calendar", filled = true) { st.requestCalendar() }
        } else if (st.events.isEmpty()) {
            Text("No events today.", color = White70, fontSize = 16.sp)
        } else {
            for ((i, e) in st.events.take(4).withIndex()) {
                val isNext = i == nextIdx
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (e.allDay) "All day" else fmtMillis(e.begin, use24),
                        color = if (isNext) Color.White else White55,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.width(72.dp)
                    )
                    Text(
                        e.title,
                        color = if (isNext) Color.White else White70,
                        fontSize = 15.sp,
                        fontWeight = if (isNext) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun SpecialWidget(minute: LocalDateTime, modifier: Modifier) {
    val date = minute.toLocalDate()
    val special = remember(date) { specialFor(date) }
    val word = remember(date) { wordOf(date) }
    GlassCard(modifier) {
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Text(if (special != null) special.emoji else "📖", fontSize = 60.sp)
            Spacer(Modifier.width(18.dp))
            Column {
                Label(if (special != null) "Today’s special" else "Word of the day")
                Spacer(Modifier.height(4.dp))
                Text(
                    if (special != null) special.name else word.first,
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(6.dp))
                DateChip(monthDay(date))
                Spacer(Modifier.height(6.dp))
                Text(
                    if (special != null) special.blurb else word.second,
                    color = White70,
                    fontSize = 14.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun NewsWidget(modifier: Modifier) {
    val st = LocalLauncher.current
    GlassCard(modifier) {
        Label("Headlines")
        Spacer(Modifier.height(8.dp))
        if (st.headlines.isEmpty()) {
            Text("Loading headlines…", color = White70, fontSize = 15.sp)
        } else {
            for (h in st.headlines.take(3)) {
                Text(
                    h,
                    color = Color.White,
                    fontSize = 15.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun SunWidget(minute: LocalDateTime, modifier: Modifier) {
    val st = LocalLauncher.current
    val weather = st.weather
    val moon = remember(minute.toLocalDate()) { moonInfo(minute.toLocalDate()) }
    GlassCard(modifier) {
        Label("Sun and moon")
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Sunrise", color = White70, fontSize = 14.sp)
            Text(weather?.sunrise ?: "—", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Sunset", color = White70, fontSize = 14.sp)
            Text(weather?.sunset ?: "—", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.weight(1f))
        Text(moon.name, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Text("${moon.lit}% lit", color = White55, fontSize = 13.sp)
    }
}

@Composable
private fun CountdownWidget(minute: LocalDateTime, modifier: Modifier) {
    val date = minute.toLocalDate()
    val upcoming = remember(date) { nextSpecial(date) }
    GlassCard(modifier) {
        Label("Next special")
        Spacer(Modifier.weight(1f))
        if (upcoming != null) {
            Text(
                "${upcoming.days}",
                color = Color.White,
                fontSize = 52.sp,
                fontWeight = FontWeight.Light
            )
            Text(
                (if (upcoming.days == 1) "day to " else "days to ") + upcoming.special.name,
                color = White70,
                fontSize = 14.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ---------- Weather face ----------

@Composable
fun WeatherFace() {
    val st = LocalLauncher.current
    val weather = st.weather
    val city = CITIES[st.prefs.cityIndex.coerceIn(0, CITIES.size - 1)]

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .padding(top = 76.dp, bottom = 112.dp, start = 40.dp, end = 40.dp)
    ) {
        val wide = maxWidth > 760.dp
        if (weather == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Loading the weather for ${city.name}…", color = White70, fontSize = 18.sp)
            }
        } else if (wide) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                WeatherNow(weather, city, Modifier.weight(0.9f).fillMaxHeight())
                Column(Modifier.weight(1.3f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    HourlyCard(weather)
                    DailyCard(weather)
                }
            }
        } else {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                WeatherNow(weather, city, Modifier.fillMaxWidth())
                HourlyCard(weather)
                DailyCard(weather)
            }
        }
    }
}

@Composable
private fun WeatherNow(w: WeatherData, city: City, modifier: Modifier) {
    Column(modifier) {
        Label(city.name)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${w.temp}°", color = Color.White, fontSize = 120.sp, fontWeight = FontWeight.ExtraLight)
            Spacer(Modifier.width(12.dp))
            WeatherIcon(w.kind, w.isDay, 72.dp)
        }
        Text(w.summary, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Medium)
        Text("High ${w.high}° · Low ${w.low}°", color = White70, fontSize = 17.sp)
        Spacer(Modifier.height(20.dp))
        Row(Modifier.fillMaxWidth()) {
            Detail("Humidity", "${w.humidity}%", Modifier.weight(1f))
            Detail("Wind", "${w.wind}", Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth()) {
            Detail("Sunrise", w.sunrise, Modifier.weight(1f))
            Detail("Sunset", w.sunset, Modifier.weight(1f))
        }
    }
}

@Composable
private fun Detail(label: String, value: String, modifier: Modifier) {
    Column(modifier) {
        Text(label, color = White55, fontSize = 13.sp)
        Text(value, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun HourlyCard(w: WeatherData) {
    GlassCard(Modifier.fillMaxWidth()) {
        Label("Next hours")
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth()) {
            for (h in w.hours) {
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(h.label, color = White55, fontSize = 12.sp)
                    Spacer(Modifier.height(4.dp))
                    WeatherIcon(h.kind, h.isDay, 30.dp)
                    Spacer(Modifier.height(4.dp))
                    Text("${h.temp}°", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
private fun DailyCard(w: WeatherData) {
    val gMin = w.days.minOf { it.lo }
    val gMax = w.days.maxOf { it.hi }
    val span = (gMax - gMin).coerceAtLeast(1)
    val bar = Color(0xFF64D2FF)
    GlassCard(Modifier.fillMaxWidth()) {
        Label("Forecast")
        Spacer(Modifier.height(6.dp))
        for (d in w.days) {
            Row(Modifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(d.label, color = Color.White, fontSize = 17.sp, modifier = Modifier.width(70.dp))
                WeatherIcon(d.kind, true, 28.dp)
                Spacer(Modifier.width(12.dp))
                Text("${d.lo}°", color = White55, fontSize = 15.sp, modifier = Modifier.width(40.dp))
                Canvas(Modifier.weight(1f).height(8.dp)) {
                    val w2 = size.width
                    val h2 = size.height
                    drawRoundRect(Color.White.copy(alpha = 0.15f), cornerRadius = CornerRadius(h2 / 2f, h2 / 2f))
                    val from = (d.lo - gMin).toFloat() / span.toFloat()
                    val to = (d.hi - gMin).toFloat() / span.toFloat()
                    drawRoundRect(
                        bar,
                        topLeft = Offset(w2 * from, 0f),
                        size = Size((w2 * (to - from)).coerceAtLeast(w2 * 0.05f), h2),
                        cornerRadius = CornerRadius(h2 / 2f, h2 / 2f)
                    )
                }
                Text(
                    "${d.hi}°",
                    color = Color.White,
                    fontSize = 15.sp,
                    modifier = Modifier.width(48.dp).padding(start = 8.dp)
                )
            }
        }
    }
}

// ---------- Photos face ----------

@Composable
fun PhotoSlideshow(modifier: Modifier = Modifier) {
    val st = LocalLauncher.current
    val files = st.photos
    val count = if (files.isEmpty()) SCENE_COUNT else files.size
    var index by remember { mutableStateOf(0) }
    LaunchedEffect(count) {
        while (true) {
            delay(8000L)
            index = (index + 1) % count
        }
    }
    Crossfade(
        targetState = index % count,
        modifier = modifier,
        animationSpec = tween(1200),
        label = "photos"
    ) { i ->
        if (files.isEmpty()) {
            SceneArt(i)
        } else {
            PhotoImage(files[i % files.size])
        }
    }
}

@Composable
private fun PhotoImage(file: File) {
    val bmp = rememberImage(file, 1800)
    if (bmp != null) {
        Image(
            bitmap = bmp,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
    } else {
        Box(Modifier.fillMaxSize().background(Color.Black))
    }
}

@Composable
fun PhotosFace(minute: LocalDateTime) {
    val st = LocalLauncher.current
    val use24 = st.prefs.clock24
    val timeText = minute.format(DateTimeFormatter.ofPattern(if (use24) "HH:mm" else "h:mm", Locale.getDefault()))
    val dateText = minute.format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.getDefault()))

    Box(Modifier.fillMaxSize()) {
        PhotoSlideshow(Modifier.fillMaxSize())
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(240.dp)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f))))
        )
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .navigationBarsPadding()
                .padding(start = 40.dp, bottom = 96.dp)
        ) {
            BasicText(
                timeText,
                style = TextStyle(color = Color.White, fontSize = 84.sp, fontWeight = FontWeight.Light)
            )
            Text(dateText, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Medium)
            if (st.photos.isEmpty()) {
                Text("Sample scenes. Add your own photos.", color = White70, fontSize = 14.sp)
            }
        }
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = 40.dp, bottom = 104.dp)
        ) {
            PillButton("Add photos", filled = false) { st.pickPhotos() }
        }
    }
}

// ---------- Flow face ----------

private class Blob(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    val radius: Float,
    val hue: Float
)

private fun stepBlobs(blobs: List<Blob>, dt: Float, width: Float, height: Float, touch: Offset?) {
    if (width <= 0f || height <= 0f) return
    for (b in blobs) {
        if (touch != null) {
            val dx = b.x - touch.x / width
            val dy = b.y - touch.y / height
            val d = sqrt(dx * dx + dy * dy) + 0.001f
            if (d < 0.3f) {
                b.vx += dx / d * 0.9f * dt
                b.vy += dy / d * 0.9f * dt
            }
        }
        b.vx *= 0.99f
        b.vy *= 0.99f
        val speed = sqrt(b.vx * b.vx + b.vy * b.vy)
        if (speed < 0.03f) {
            b.vx += (Random.nextFloat() - 0.5f) * 0.02f
            b.vy += (Random.nextFloat() - 0.5f) * 0.02f
        }
        b.x += b.vx * dt
        b.y += b.vy * dt
        if (b.x < 0f || b.x > 1f) b.vx = -b.vx
        if (b.y < 0f || b.y > 1f) b.vy = -b.vy
        b.x = b.x.coerceIn(0f, 1f)
        b.y = b.y.coerceIn(0f, 1f)
    }
}

@Composable
fun FlowFace(active: Boolean) {
    val st = LocalLauncher.current
    val accent = parseColor(st.prefs.accent, DefaultAccent)
    val baseHue = remember(accent) {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(accent.toArgb(), hsv)
        hsv[0]
    }
    val blobs = remember {
        List(9) { i ->
            Blob(
                x = Random.nextFloat(),
                y = Random.nextFloat(),
                vx = (Random.nextFloat() - 0.5f) * 0.12f,
                vy = (Random.nextFloat() - 0.5f) * 0.12f,
                radius = 0.28f + Random.nextFloat() * 0.2f,
                hue = i * 38f
            )
        }
    }
    var frame by remember { mutableStateOf(0L) }
    var width by remember { mutableStateOf(0f) }
    var height by remember { mutableStateOf(0f) }
    var touch by remember { mutableStateOf<Offset?>(null) }

    LaunchedEffect(active) {
        if (!active) return@LaunchedEffect
        var last = 0L
        while (true) {
            androidx.compose.runtime.withFrameNanos { t ->
                val dt = if (last == 0L) 0.016f else ((t - last) / 1_000_000_000f).coerceAtMost(0.05f)
                last = t
                stepBlobs(blobs, dt, width, height, touch)
                frame = t
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF07080C))
            .onSizeChanged {
                width = it.width.toFloat()
                height = it.height.toFloat()
            }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull()
                        touch = if (change != null && change.pressed) change.position else null
                    }
                }
            }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val seconds = frame / 1_000_000_000f
            for (b in blobs) {
                val c = Offset(b.x * size.width, b.y * size.height)
                val r = b.radius * size.minDimension
                val hue = (baseHue + b.hue + seconds * 6f) % 360f
                val col = Color.hsv(hue, 0.85f, 0.95f)
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(col.copy(alpha = 0.55f), Color.Transparent),
                        center = c,
                        radius = r
                    ),
                    radius = r,
                    center = c,
                    blendMode = BlendMode.Plus
                )
            }
        }
        Text(
            "Touch to stir the colors",
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 16.sp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 110.dp)
        )
    }
}
