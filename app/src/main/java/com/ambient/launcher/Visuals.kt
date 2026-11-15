package com.ambient.launcher

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDateTime

// ---------- Time ----------

/** A clock that ticks once per second, aligned to the second boundary. */
@Composable
fun rememberNow(): State<LocalDateTime> {
    val state = remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            state.value = LocalDateTime.now()
            delay(1000L - (System.currentTimeMillis() % 1000L))
        }
    }
    return state
}

// ---------- Glass look (a gradient, not a blur) ----------

fun Modifier.glass(shape: Shape = RoundedCornerShape(26.dp)): Modifier {
    return this
        .clip(shape)
        .background(
            Brush.verticalGradient(
                listOf(Color.White.copy(alpha = 0.17f), Color.White.copy(alpha = 0.06f))
            )
        )
        .border(
            1.dp,
            Brush.verticalGradient(
                listOf(Color.White.copy(alpha = 0.34f), Color.White.copy(alpha = 0.05f))
            ),
            shape
        )
}

/** Stops touches from reaching whatever is drawn underneath an overlay. */
fun Modifier.blockTouches(): Modifier {
    return this.pointerInput(Unit) { detectTapGestures { } }
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    padding: Dp = 18.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier.glass().padding(padding), content = content)
}

// ---------- Wallpapers ----------

data class WallpaperGradient(val id: String, val name: String, val colors: List<Color>)

val GRADIENTS = listOf(
    WallpaperGradient("aurora", "Aurora", listOf(Color(0xFF0B3C49), Color(0xFF3A2D6B), Color(0xFF120F26))),
    WallpaperGradient("dusk", "Dusk", listOf(Color(0xFF5B2A4A), Color(0xFFB4533C), Color(0xFF1D1330))),
    WallpaperGradient("ocean", "Ocean", listOf(Color(0xFF0B2E4F), Color(0xFF16647A), Color(0xFF071A2B))),
    WallpaperGradient("forest", "Forest", listOf(Color(0xFF123524), Color(0xFF2D5B3F), Color(0xFF08150E))),
    WallpaperGradient("ember", "Ember", listOf(Color(0xFF3A0E0E), Color(0xFFB5421F), Color(0xFF1A0707))),
    WallpaperGradient("slate", "Slate", listOf(Color(0xFF1F2430), Color(0xFF3A4254), Color(0xFF0F1218))),
    WallpaperGradient("midnight", "Midnight", listOf(Color(0xFF05060C), Color(0xFF101540), Color(0xFF020208))),
    WallpaperGradient("blush", "Blush", listOf(Color(0xFF3B2233), Color(0xFFB8607A), Color(0xFF1C1020)))
)

fun gradientColors(id: String, period: Period): List<Color> {
    val picked = GRADIENTS.firstOrNull { it.id == id }
    if (picked != null) return picked.colors
    return when (period) {
        Period.Morning -> listOf(Color(0xFF3D2A2F), Color(0xFF1A1822))
        Period.Day -> listOf(Color(0xFF1F3B57), Color(0xFF0F1C2C))
        Period.Evening -> listOf(Color(0xFF47283A), Color(0xFF171327))
        Period.Night -> listOf(Color(0xFF050507), Color(0xFF020203))
    }
}

/** Loads a picture off the main thread. Pass [rev] to force a reload when the file is replaced. */
@Composable
fun rememberImage(file: File?, maxSide: Int, rev: Int = 0): ImageBitmap? {
    val state = produceState<ImageBitmap?>(null, file, rev) {
        value = if (file == null) {
            null
        } else {
            withContext(Dispatchers.IO) { ImageStore.loadBitmap(file, maxSide) }
        }
    }
    return state.value
}

@Composable
fun WallpaperBackground(period: Period) {
    val st = LocalLauncher.current
    val prefs = st.prefs
    val wallFile = ImageStore.wallpaperFile(st.ctx)
    val usePhoto = prefs.wallpaper == "photo" && wallFile.exists()
    val photo = rememberImage(if (usePhoto) wallFile else null, 2200, prefs.wallpaperRev)

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (photo != null) {
            Image(
                bitmap = photo,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            val colors = gradientColors(prefs.wallpaper, period)
            val safe = if (colors.size >= 2) colors else listOf(colors[0], colors[0])
            Box(Modifier.fillMaxSize().background(Brush.linearGradient(safe)))
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = prefs.dim.coerceIn(0f, 0.8f)))
        )
    }
}

// ---------- Weather icons ----------

private val SunColor = Color(0xFFFFD60A)
private val CloudColor = Color(0xFFD7DBE3)
private val RainColor = Color(0xFF64D2FF)
private val MoonColor = Color(0xFFE8E0C8)

private fun DrawScope.sunShape(center: Offset, radius: Float) {
    drawCircle(SunColor, radius = radius, center = center)
    for (i in 0 until 8) {
        val a = i * Math.PI / 4.0
        val dx = Math.cos(a).toFloat()
        val dy = Math.sin(a).toFloat()
        drawLine(
            SunColor,
            Offset(center.x + dx * radius * 1.45f, center.y + dy * radius * 1.45f),
            Offset(center.x + dx * radius * 1.85f, center.y + dy * radius * 1.85f),
            strokeWidth = radius * 0.28f,
            cap = StrokeCap.Round
        )
    }
}

private fun DrawScope.moonShape(center: Offset, radius: Float) {
    val full = Path()
    full.addOval(Rect(center, radius))
    val bite = Path()
    bite.addOval(Rect(Offset(center.x + radius * 0.5f, center.y - radius * 0.3f), radius * 0.85f))
    val crescent = Path.combine(PathOperation.Difference, full, bite)
    drawPath(crescent, MoonColor)
}

private fun DrawScope.cloudShape(cx: Float, cy: Float, s: Float) {
    drawCircle(CloudColor, radius = s * 0.28f, center = Offset(cx - s * 0.22f, cy + s * 0.05f))
    drawCircle(CloudColor, radius = s * 0.36f, center = Offset(cx + s * 0.02f, cy - s * 0.08f))
    drawCircle(CloudColor, radius = s * 0.26f, center = Offset(cx + s * 0.30f, cy + s * 0.06f))
    drawRoundRect(
        CloudColor,
        topLeft = Offset(cx - s * 0.46f, cy + s * 0.05f),
        size = Size(s * 0.92f, s * 0.28f),
        cornerRadius = CornerRadius(s * 0.14f, s * 0.14f)
    )
}

@Composable
fun WeatherIcon(kind: WeatherKind, isDay: Boolean, iconSize: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(iconSize)) {
        val w = size.width
        val h = size.height
        when (kind) {
            WeatherKind.Clear -> {
                if (isDay) sunShape(Offset(w * 0.5f, h * 0.5f), w * 0.2f)
                else moonShape(Offset(w * 0.5f, h * 0.5f), w * 0.3f)
            }
            WeatherKind.Partly -> {
                if (isDay) sunShape(Offset(w * 0.36f, h * 0.36f), w * 0.15f)
                else moonShape(Offset(w * 0.36f, h * 0.36f), w * 0.2f)
                cloudShape(w * 0.58f, h * 0.6f, w * 0.7f)
            }
            WeatherKind.Cloud -> cloudShape(w * 0.5f, h * 0.5f, w * 0.9f)
            WeatherKind.Fog -> {
                cloudShape(w * 0.5f, h * 0.38f, w * 0.8f)
                for (i in 0 until 3) {
                    val y = h * (0.68f + i * 0.1f)
                    drawLine(
                        CloudColor,
                        Offset(w * 0.2f, y),
                        Offset(w * 0.8f, y),
                        strokeWidth = w * 0.05f,
                        cap = StrokeCap.Round
                    )
                }
            }
            WeatherKind.Rain -> {
                cloudShape(w * 0.5f, h * 0.38f, w * 0.8f)
                for (i in 0 until 3) {
                    val x = w * (0.32f + i * 0.18f)
                    drawLine(
                        RainColor,
                        Offset(x, h * 0.72f),
                        Offset(x - w * 0.05f, h * 0.92f),
                        strokeWidth = w * 0.06f,
                        cap = StrokeCap.Round
                    )
                }
            }
            WeatherKind.Snow -> {
                cloudShape(w * 0.5f, h * 0.38f, w * 0.8f)
                for (i in 0 until 3) {
                    drawCircle(
                        Color.White,
                        radius = w * 0.05f,
                        center = Offset(w * (0.32f + i * 0.18f), h * (0.78f + (i % 2) * 0.08f))
                    )
                }
            }
            WeatherKind.Storm -> {
                cloudShape(w * 0.5f, h * 0.36f, w * 0.8f)
                val bolt = Path()
                bolt.moveTo(w * 0.54f, h * 0.62f)
                bolt.lineTo(w * 0.42f, h * 0.82f)
                bolt.lineTo(w * 0.52f, h * 0.82f)
                bolt.lineTo(w * 0.46f, h * 0.98f)
                bolt.lineTo(w * 0.64f, h * 0.74f)
                bolt.lineTo(w * 0.54f, h * 0.74f)
                bolt.close()
                drawPath(bolt, SunColor)
            }
        }
    }
}

// ---------- Placeholder scenes for the Photos face ----------

private class Scene(
    val top: Color,
    val bottom: Color,
    val sun: Color,
    val sx: Float,
    val sy: Float,
    val h1: Color,
    val h2: Color,
    val h3: Color
)

private val SCENES = listOf(
    Scene(Color(0xFF3A2A5A), Color(0xFFF08A5D), Color(0xFFFFD9A0), 0.70f, 0.50f, Color(0xFF4A2F55), Color(0xFF2F2142), Color(0xFF1B1330)),
    Scene(Color(0xFF0F4C5C), Color(0xFF7FD1C7), Color(0xFFFFF3C4), 0.23f, 0.38f, Color(0xFF1F6F78), Color(0xFF16525E), Color(0xFF0D3844)),
    Scene(Color(0xFF7FB6A8), Color(0xFFE8F0C8), Color(0xFFFFFFFF), 0.78f, 0.31f, Color(0xFF3F7A4A), Color(0xFF2D6038), Color(0xFF1C4528)),
    Scene(Color(0xFF0B1026), Color(0xFF2A3566), Color(0xFFF2EFE0), 0.50f, 0.28f, Color(0xFF151B3A), Color(0xFF0F1530), Color(0xFF090D22))
)

val SCENE_COUNT: Int = SCENES.size

private fun DrawScope.hill(color: Color, baseY: Float, amp: Float) {
    val w = size.width
    val h = size.height
    val p = Path()
    p.moveTo(0f, h * baseY)
    p.cubicTo(w * 0.25f, h * (baseY - amp), w * 0.55f, h * (baseY + amp * 0.6f), w * 0.8f, h * (baseY - amp * 0.4f))
    p.cubicTo(w * 0.9f, h * (baseY - amp * 0.7f), w * 0.97f, h * baseY, w, h * (baseY + amp * 0.2f))
    p.lineTo(w, h)
    p.lineTo(0f, h)
    p.close()
    drawPath(p, color)
}

@Composable
fun SceneArt(index: Int, modifier: Modifier = Modifier.fillMaxSize()) {
    val s = SCENES[index % SCENES.size]
    Canvas(modifier) {
        drawRect(Brush.verticalGradient(listOf(s.top, s.bottom)))
        drawCircle(s.sun, radius = size.minDimension * 0.09f, center = Offset(size.width * s.sx, size.height * s.sy))
        hill(s.h1, 0.70f, 0.10f)
        hill(s.h2, 0.80f, 0.07f)
        hill(s.h3, 0.90f, 0.05f)
    }
}
