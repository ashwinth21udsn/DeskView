@file:OptIn(ExperimentalFoundationApi::class)

package com.ambient.launcher

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDateTime

private val PanelShape = RoundedCornerShape(32.dp)

// ---------- Shared panel frame ----------

/** A large glass-look sheet over a dimmed scrim. Tapping the scrim or pressing back closes it. */
@Composable
fun PanelScaffold(title: String, onClose: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    BackHandler { onClose() }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                onClose()
            },
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier
                .systemBarsPadding()
                .padding(16.dp)
                .fillMaxHeight()
                .widthIn(max = 920.dp)
                .fillMaxWidth()
                .clip(PanelShape)
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF2B2E3A).copy(alpha = 0.97f), Color(0xFF15161D).copy(alpha = 0.98f))
                    )
                )
                .border(
                    1.dp,
                    Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = 0.30f), Color.White.copy(alpha = 0.04f))
                    ),
                    PanelShape
                )
                .blockTouches()
                .padding(24.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(title, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
                PillButton("Done", filled = true) { onClose() }
            }
            Spacer(Modifier.height(14.dp))
            content()
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 18.dp)) {
        Label(title)
        Spacer(Modifier.height(10.dp))
        content()
    }
}

@Composable
private fun Chip(text: String, selected: Boolean, accent: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) accent else Color.White.copy(alpha = 0.10f))
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 9.dp)
    ) {
        Text(
            text,
            color = if (selected) Color.Black else Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

// ---------- App drawer ----------

private fun launchApp(ctx: Context, pkg: String) {
    val intent = ctx.packageManager.getLaunchIntentForPackage(pkg)
    if (intent != null) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            ctx.startActivity(intent)
        } catch (e: Exception) {
            // The app may have been removed or disabled since the list was built.
        }
    }
}

private fun openAppDetails(ctx: Context, pkg: String) {
    try {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg"))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(intent)
    } catch (e: Exception) {
        // No settings screen available.
    }
}

@Composable
fun AppDrawer() {
    val st = LocalLauncher.current
    val ctx = LocalContext.current
    val accent = parseColor(st.prefs.accent, DefaultAccent)
    var query by remember { mutableStateOf("") }

    LaunchedEffect(Unit) { st.reloadApps() }

    val shown = remember(st.apps, query) {
        val q = query.trim()
        if (q.isEmpty()) st.apps else st.apps.filter { it.label.contains(q, ignoreCase = true) }
    }

    PanelScaffold("Apps", onClose = { st.overlay = Overlay.None }) {
        BasicTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            textStyle = TextStyle(color = Color.White, fontSize = 18.sp),
            cursorBrush = SolidColor(accent),
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { inner ->
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(50))
                        .background(Color.White.copy(alpha = 0.10f))
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (query.isEmpty()) {
                        Text("Search apps", color = Color.White.copy(alpha = 0.45f), fontSize = 18.sp)
                    }
                    inner()
                }
            }
        )
        Spacer(Modifier.height(12.dp))

        if (st.apps.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("Loading apps…", color = Color.White.copy(alpha = 0.6f), fontSize = 16.sp)
            }
        } else if (shown.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("No app matches “$query”", color = Color.White.copy(alpha = 0.6f), fontSize = 16.sp)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(112.dp),
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(shown, key = { it.pkg }) { app ->
                    Column(
                        Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .combinedClickable(
                                onClick = {
                                    launchApp(ctx, app.pkg)
                                    st.overlay = Overlay.None
                                },
                                onLongClick = { openAppDetails(ctx, app.pkg) }
                            )
                            .padding(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Image(
                            bitmap = app.icon,
                            contentDescription = app.label,
                            modifier = Modifier.size(60.dp)
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            app.label,
                            color = Color.White,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

// ---------- Settings ----------

private fun openHomeSettings(ctx: Context) {
    val actions = listOf(
        Settings.ACTION_HOME_SETTINGS,
        Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS,
        Settings.ACTION_SETTINGS
    )
    for (action in actions) {
        try {
            ctx.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        } catch (e: Exception) {
            // Try the next, more general screen.
        }
    }
}

@Composable
private fun WallpaperTile(
    label: String,
    colors: List<Color>,
    selected: Boolean,
    accent: Color,
    onClick: () -> Unit
) {
    val safe = if (colors.size >= 2) colors else listOf(colors[0], colors[0])
    val shape = RoundedCornerShape(16.dp)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .width(116.dp)
                .height(72.dp)
                .clip(shape)
                .background(Brush.linearGradient(safe))
                .border(if (selected) 3.dp else 1.dp, if (selected) accent else Color.White.copy(alpha = 0.2f), shape)
                .clickable { onClick() }
        )
        Spacer(Modifier.height(5.dp))
        Text(label, color = if (selected) accent else Color.White.copy(alpha = 0.75f), fontSize = 13.sp)
    }
}

@Composable
private fun PhotoWallpaperTile(selected: Boolean, accent: Color, onClick: () -> Unit) {
    val st = LocalLauncher.current
    val file = ImageStore.wallpaperFile(st.ctx)
    val rev = st.prefs.wallpaperRev
    val image = rememberImage(file, 400, rev)
    val shape = RoundedCornerShape(16.dp)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .width(116.dp)
                .height(72.dp)
                .clip(shape)
                .background(Color(0xFF222633))
                .border(if (selected) 3.dp else 1.dp, if (selected) accent else Color.White.copy(alpha = 0.2f), shape)
                .clickable { onClick() }
        ) {
            if (image != null) {
                Image(
                    bitmap = image,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        Spacer(Modifier.height(5.dp))
        Text("My photo", color = if (selected) accent else Color.White.copy(alpha = 0.75f), fontSize = 13.sp)
    }
}

@Composable
private fun SettingRow(label: String, checked: Boolean, accent: Color, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = Color.White, fontSize = 17.sp)
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = accent)
        )
    }
}

@Composable
fun SettingsPanel() {
    val st = LocalLauncher.current
    val ctx = LocalContext.current
    val prefs = st.prefs
    val accent = parseColor(prefs.accent, DefaultAccent)
    val period = periodOf(LocalDateTime.now().hour)
    val hasWallpaperPhoto = remember(prefs.wallpaperRev) { ImageStore.wallpaperFile(ctx).exists() }

    PanelScaffold("Settings", onClose = { st.overlay = Overlay.None }) {
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {

            Section("Wallpaper") {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    WallpaperTile(
                        "Auto",
                        gradientColors("auto", period),
                        prefs.wallpaper == "auto",
                        accent
                    ) { prefs.wallpaper = "auto" }
                    if (hasWallpaperPhoto) {
                        PhotoWallpaperTile(prefs.wallpaper == "photo", accent) { prefs.wallpaper = "photo" }
                    }
                    for (g in GRADIENTS) {
                        WallpaperTile(g.name, g.colors, prefs.wallpaper == g.id, accent) {
                            prefs.wallpaper = g.id
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PillButton("Choose a photo", filled = false) { st.pickWallpaper() }
                }
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Dim", color = Color.White, fontSize = 17.sp, modifier = Modifier.width(64.dp))
                    Slider(
                        value = prefs.dim.coerceIn(0f, 0.8f),
                        onValueChange = { prefs.dim = it },
                        valueRange = 0f..0.8f,
                        modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(
                            thumbColor = accent,
                            activeTrackColor = accent,
                            inactiveTrackColor = Color.White.copy(alpha = 0.2f)
                        )
                    )
                }
            }

            Section("Accent colour") {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    for (c in ACCENTS) {
                        ColorDot(c, selected = c.equals(prefs.accent, ignoreCase = true)) { prefs.accent = c }
                    }
                }
            }

            Section("Weather") {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    for (i in CITIES.indices) {
                        Chip(CITIES[i].name, prefs.cityIndex == i, accent) { prefs.cityIndex = i }
                    }
                }
                Spacer(Modifier.height(8.dp))
                SettingRow("Use Fahrenheit", prefs.fahrenheit, accent) { prefs.fahrenheit = it }
            }

            Section("Photos") {
                Text(
                    if (st.photos.isEmpty()) "No photos yet. They play as a slideshow on the Photos screen and in the Photos widget."
                    else "${st.photos.size} photo(s) in the slideshow.",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 15.sp
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PillButton("Add photos", filled = false) { st.pickPhotos() }
                    if (st.photos.isNotEmpty()) {
                        PillButton("Clear all", filled = false) {
                            ImageStore.clearPhotos(ctx)
                            st.refreshPhotos()
                        }
                    }
                }
            }

            Section("Calendar") {
                if (st.calendarAllowed) {
                    Text("Today’s events are shown in the briefing.", color = Color.White.copy(alpha = 0.7f), fontSize = 15.sp)
                } else {
                    Text(
                        "Allow calendar access to see today’s agenda.",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 15.sp
                    )
                    Spacer(Modifier.height(10.dp))
                    PillButton("Allow calendar", filled = true) { st.requestCalendar() }
                }
            }

            Section("Display") {
                SettingRow("Keep screen on", prefs.keepAwake, accent) { prefs.keepAwake = it }
            }

            Section("Home app") {
                Text(
                    "Make Ambient Home your tablet’s home screen so the Home button brings you back here.",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 15.sp
                )
                Spacer(Modifier.height(10.dp))
                PillButton("Set as default home app", filled = true) { openHomeSettings(ctx) }
            }

            Spacer(Modifier.height(22.dp))
            Text(
                "Ambient Home 0.4.0",
                color = Color.White.copy(alpha = 0.35f),
                fontSize = 13.sp,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

// ---------- Widget picker ----------

private fun widgetBlurb(id: String): String {
    return when (id) {
        "greet" -> "A greeting for the time of day, with the weather in one line."
        "forecast" -> "The next hours at a glance."
        "agenda" -> "Today’s events from your calendar."
        "special" -> "Observances for today, like Father’s Day, with a small emoji."
        "news" -> "Top world headlines."
        "photo" -> "A slideshow of your photos."
        "sun" -> "Sunrise, sunset and the moon phase."
        "countdown" -> "Days until the next special day."
        else -> ""
    }
}

@Composable
fun WidgetPicker() {
    val st = LocalLauncher.current
    val accent = parseColor(st.prefs.accent, DefaultAccent)
    val enabledIds = st.enabledWidgets().map { it.id }.toSet()

    PanelScaffold("Briefing widgets", onClose = { st.overlay = Overlay.None }) {
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
            for (w in WIDGETS) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(Modifier.weight(1f).padding(end = 16.dp)) {
                        Text(w.name, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                        Text(widgetBlurb(w.id), color = Color.White.copy(alpha = 0.6f), fontSize = 14.sp)
                    }
                    Switch(
                        checked = w.id in enabledIds,
                        onCheckedChange = { st.setWidget(w.id, it) },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = accent)
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            PillButton("Reset to default", filled = false) {
                st.prefs.widgets = "greet,forecast,agenda,special,news,photo,sun"
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
