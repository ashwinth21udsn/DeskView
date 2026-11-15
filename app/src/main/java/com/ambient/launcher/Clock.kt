package com.ambient.launcher

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.BackHandler
import androidx.compose.ui.platform.LocalDensity
import java.time.LocalDateTime
import kotlin.math.absoluteValue
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Resolves the saved clock color. "auto" follows the time of day. */
fun clockColorFor(saved: String, period: Period): Color {
    if (saved == "auto") {
        return if (period == Period.Night) Color(0xFFE0B98E) else Color.White
    }
    return parseColor(saved, Color.White)
}

private fun timeStyle(id: String, fsPx: Float, density: Density, color: Color): TextStyle {
    val fs = with(density) { fsPx.toSp() }
    val spacing = (fs.value * -0.03f).sp
    return when (id) {
        "bold" -> TextStyle(
            color = color, fontSize = fs, fontWeight = FontWeight.ExtraBold, letterSpacing = spacing
        )
        "serif" -> TextStyle(
            color = color, fontSize = fs, fontWeight = FontWeight.Normal,
            fontFamily = FontFamily.Serif, letterSpacing = spacing
        )
        "mono" -> TextStyle(
            color = color, fontSize = fs, fontWeight = FontWeight.Medium,
            fontFamily = FontFamily.Monospace, letterSpacing = spacing
        )
        "outline" -> TextStyle(
            color = color, fontSize = fs, fontWeight = FontWeight.Bold, letterSpacing = spacing,
            drawStyle = Stroke(width = fsPx * 0.012f + 2f, join = StrokeJoin.Round)
        )
        else -> TextStyle(
            color = color, fontSize = fs, fontWeight = FontWeight.ExtraLight, letterSpacing = spacing
        )
    }
}

/** Glass: a gradient-filled number with a thin bright edge and a soft shadow. No blur. */
@Composable
private fun GlassTime(text: String, fsPx: Float, density: Density, color: Color) {
    val fs = with(density) { fsPx.toSp() }
    val spacing = (fs.value * -0.03f).sp
    Box {
        BasicText(
            text,
            style = TextStyle(
                color = color.copy(alpha = 0.55f),
                fontSize = fs,
                fontWeight = FontWeight.Bold,
                letterSpacing = spacing,
                drawStyle = Stroke(width = fsPx * 0.008f + 1.5f, join = StrokeJoin.Round)
            ),
            maxLines = 1,
            softWrap = false
        )
        BasicText(
            text,
            style = TextStyle(
                brush = Brush.verticalGradient(
                    listOf(
                        color.copy(alpha = 0.95f),
                        color.copy(alpha = 0.22f),
                        color.copy(alpha = 0.55f)
                    )
                ),
                fontSize = fs,
                fontWeight = FontWeight.Bold,
                letterSpacing = spacing,
                shadow = Shadow(Color.Black.copy(alpha = 0.35f), Offset(0f, 10f), 24f)
            ),
            maxLines = 1,
            softWrap = false
        )
    }
}

@Composable
fun AnalogClock(now: LocalDateTime, color: Color, accent: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val c = center
        val r = size.minDimension / 2f
        for (i in 0 until 12) {
            val a = i * Math.PI / 6.0
            val major = i % 3 == 0
            val r1 = r * (if (major) 0.84f else 0.9f)
            val r2 = r * 0.96f
            drawLine(
                color.copy(alpha = 0.9f),
                Offset(c.x + (sin(a) * r1).toFloat(), c.y - (cos(a) * r1).toFloat()),
                Offset(c.x + (sin(a) * r2).toFloat(), c.y - (cos(a) * r2).toFloat()),
                strokeWidth = r * (if (major) 0.035f else 0.018f),
                cap = StrokeCap.Round
            )
        }
        val hourDeg = (now.hour % 12) * 30f + now.minute * 0.5f
        val minDeg = now.minute * 6f + now.second * 0.1f
        val secDeg = now.second * 6f

        fun hand(deg: Float, length: Float, width: Float, col: Color) {
            val a = Math.toRadians(deg.toDouble())
            drawLine(
                col,
                c,
                Offset(c.x + (sin(a) * length).toFloat(), c.y - (cos(a) * length).toFloat()),
                strokeWidth = width,
                cap = StrokeCap.Round
            )
        }
        hand(hourDeg, r * 0.5f, r * 0.05f, color)
        hand(minDeg, r * 0.76f, r * 0.035f, color)
        hand(secDeg, r * 0.84f, r * 0.012f, accent)
        drawCircle(accent, radius = r * 0.035f, center = c)
    }
}

/** The big clock, drawn in any of the styles in [CLOCK_STYLES]. */
@Composable
fun ClockView(
    styleId: String,
    now: LocalDateTime,
    use24: Boolean,
    color: Color,
    accent: Color,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val density = LocalDensity.current
        val wPx = constraints.maxWidth.toFloat()
        val hPx = constraints.maxHeight.toFloat()
        val hour = now.hour
        val minute = now.minute
        val h12 = if (hour % 12 == 0) 12 else hour % 12
        val shownHour = if (use24) hour else h12

        if (styleId == "analog") {
            val side = with(density) { (min(wPx, hPx) * 0.92f).toDp() }
            AnalogClock(now, color, accent, Modifier.size(side))
        } else if (styleId == "stack") {
            val fsPx = min(hPx * 0.4f, wPx / 1.9f)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                BasicText(
                    String.format("%02d", shownHour),
                    style = timeStyle("bold", fsPx, density, color),
                    maxLines = 1,
                    softWrap = false
                )
                BasicText(
                    String.format("%02d", minute),
                    style = timeStyle("bold", fsPx, density, color.copy(alpha = 0.6f)),
                    maxLines = 1,
                    softWrap = false
                )
            }
        } else {
            val text = if (use24) {
                String.format("%02d:%02d", hour, minute)
            } else {
                String.format("%d:%02d", h12, minute)
            }
            val suffix = if (use24) "" else if (hour < 12) "AM" else "PM"
            val fsPx = min(hPx * 0.72f, wPx / (if (use24) 3.0f else 3.6f))
            Row(verticalAlignment = Alignment.Bottom) {
                if (styleId == "glass") {
                    GlassTime(text, fsPx, density, color)
                } else {
                    BasicText(
                        text,
                        style = timeStyle(styleId, fsPx, density, color),
                        maxLines = 1,
                        softWrap = false
                    )
                }
                if (suffix.isNotEmpty()) {
                    BasicText(
                        suffix,
                        style = TextStyle(
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = with(density) { (fsPx * 0.13f).toSp() },
                            fontWeight = FontWeight.Medium
                        ),
                        modifier = Modifier.padding(
                            start = 8.dp,
                            bottom = with(density) { (fsPx * 0.14f).toDp() }
                        )
                    )
                }
            }
        }
    }
}

/** Full-screen editor, opened by touching and holding the clock. Swipe to change style. */
@Composable
fun ClockEditor(nowState: State<LocalDateTime>, period: Period) {
    val st = LocalLauncher.current
    val prefs = st.prefs
    val accent = parseColor(prefs.accent, DefaultAccent)

    var color by remember { mutableStateOf(prefs.clockColor) }
    var use24 by remember { mutableStateOf(prefs.clock24) }
    var showDate by remember { mutableStateOf(prefs.clockDate) }
    val startPage = CLOCK_STYLES.indexOfFirst { it.first == prefs.clockStyle }.coerceAtLeast(0)
    val pager = rememberPagerState(initialPage = startPage, pageCount = { CLOCK_STYLES.size })

    val close = { st.overlay = Overlay.None }
    BackHandler { close() }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.82f))
            .blockTouches()
    ) {
        Column(Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 28.dp, vertical = 16.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                PillButton("Cancel", filled = false) { close() }
                Text("Customize clock", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                PillButton("Done", filled = true) {
                    prefs.clockStyle = CLOCK_STYLES[pager.currentPage].first
                    prefs.clockColor = color
                    prefs.clock24 = use24
                    prefs.clockDate = showDate
                    close()
                }
            }

            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                val sidePad = maxWidth * 0.14f
                HorizontalPager(
                    state = pager,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = sidePad)
                ) { page ->
                    val offset = ((pager.currentPage - page) + pager.currentPageOffsetFraction)
                        .absoluteValue.coerceIn(0f, 1f)
                    Box(
                        Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                val s = 1f - 0.35f * offset
                                scaleX = s
                                scaleY = s
                                alpha = 1f - 0.7f * offset
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        ClockView(
                            CLOCK_STYLES[page].first,
                            nowState.value,
                            use24,
                            clockColorFor(color, period),
                            accent,
                            Modifier.fillMaxSize().padding(8.dp)
                        )
                    }
                }
            }

            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    CLOCK_STYLES[pager.currentPage].second,
                    color = Color.White,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "${pager.currentPage + 1} of ${CLOCK_STYLES.size}",
                    color = Color.White.copy(alpha = 0.55f),
                    fontSize = 13.sp
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (i in CLOCK_STYLES.indices) {
                        Box(
                            Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(
                                    if (i == pager.currentPage) Color.White else Color.White.copy(alpha = 0.3f)
                                )
                        )
                    }
                }
                Spacer(Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    for (c in CLOCK_COLORS) {
                        ColorDot(c, selected = c == color) { color = c }
                    }
                }
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(28.dp), verticalAlignment = Alignment.CenterVertically) {
                    ToggleRow("24-hour", use24, accent) { use24 = it }
                    ToggleRow("Show date", showDate, accent) { showDate = it }
                }
            }
        }
    }
}

@Composable
fun ColorDot(value: String, selected: Boolean, onClick: () -> Unit) {
    val fill = if (value == "auto") Color(0xFF3A3F4A) else parseColor(value, Color.White)
    val ring = if (selected) Modifier.border(3.dp, Color.White, CircleShape) else Modifier
    Box(
        Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(fill)
            .then(ring)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        if (value == "auto") {
            Text("A", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun ToggleRow(label: String, checked: Boolean, accent: Color, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(label, color = Color.White, fontSize = 16.sp)
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = accent
            )
        )
    }
}

@Composable
fun PillButton(text: String, filled: Boolean, onClick: () -> Unit) {
    val accent = parseColor(LocalLauncher.current.prefs.accent, DefaultAccent)
    val bg = if (filled) accent else Color.White.copy(alpha = 0.12f)
    val fg = if (filled) Color.Black else Color.White
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .clickable { onClick() }
            .padding(horizontal = 20.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = fg, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}
