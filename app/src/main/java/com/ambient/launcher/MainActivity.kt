package com.ambient.launcher

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private const val MAX_PHOTOS = 30

class MainActivity : ComponentActivity() {

    private lateinit var state: LauncherState

    private val calendarPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            state.calendarAllowed = granted
            if (granted) {
                lifecycleScope.launch {
                    state.events = withContext(Dispatchers.IO) { loadTodayEvents(this@MainActivity) }
                }
            }
        }

    private val wallpaperPicker =
        registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) importWallpaper(uri)
        }

    private val photoPicker =
        registerForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
            if (uris.isNotEmpty()) importPhotos(uris)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )

        state = LauncherState(this)
        state.requestCalendar = { calendarPermission.launch(Manifest.permission.READ_CALENDAR) }
        state.pickWallpaper = {
            wallpaperPicker.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        }
        state.pickPhotos = {
            photoPicker.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        }

        setContent {
            LauncherApp(state)
        }
    }

    override fun onResume() {
        super.onResume()
        state.calendarAllowed = hasCalendarPermission(this)
        state.refreshPhotos()
    }

    // Pressing Home while we are already the home app brings us back to the first screen.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == Intent.ACTION_MAIN) {
            state.goHome()
        }
    }

    private fun importWallpaper(uri: Uri) {
        lifecycleScope.launch {
            val ok = withContext(Dispatchers.IO) {
                ImageStore.importImage(this@MainActivity, uri, ImageStore.wallpaperFile(this@MainActivity), 2200)
            }
            if (ok) {
                state.prefs.wallpaperRev = state.prefs.wallpaperRev + 1
                state.prefs.wallpaper = "photo"
            } else {
                Toast.makeText(this@MainActivity, "Could not use that picture", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun importPhotos(uris: List<Uri>) {
        lifecycleScope.launch {
            val dir = ImageStore.photosDir(this@MainActivity)
            val room = (MAX_PHOTOS - ImageStore.listPhotos(this@MainActivity).size).coerceAtLeast(0)
            if (room == 0) {
                Toast.makeText(this@MainActivity, "Photo limit reached ($MAX_PHOTOS)", Toast.LENGTH_SHORT).show()
                return@launch
            }
            withContext(Dispatchers.IO) {
                val stamp = System.currentTimeMillis()
                uris.take(room).forEachIndexed { i, uri ->
                    val dest = File(dir, "p${stamp}_${i.toString().padStart(2, '0')}.jpg")
                    ImageStore.importImage(this@MainActivity, uri, dest, 1600)
                }
            }
            state.refreshPhotos()
        }
    }
}

private val PAGE_NAMES = listOf("Clock", "Briefing", "Weather", "Photos", "Flow")

@Composable
fun LauncherApp(st: LauncherState) {
    CompositionLocalProvider(LocalLauncher provides st) {
        val activity = androidx.compose.ui.platform.LocalContext.current as ComponentActivity
        val scope = rememberCoroutineScope()
        val nowState = rememberNow()
        val minuteState = remember {
            derivedStateOf { nowState.value.withSecond(0).withNano(0) }
        }
        val minute = minuteState.value
        val period = periodOf(minute.hour)
        val pager = rememberPagerState(initialPage = 0, pageCount = { PAGE_NAMES.size })

        // Keep the screen awake when asked.
        val keepAwake = st.prefs.keepAwake
        LaunchedEffect(keepAwake) {
            if (keepAwake) {
                activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }

        // Pressing Home returns to the clock.
        LaunchedEffect(st.homeTick) {
            if (st.homeTick > 0) pager.animateScrollToPage(0)
        }

        // Weather, refreshed every 20 minutes and whenever the city or units change.
        val cityIndex = st.prefs.cityIndex.coerceIn(0, CITIES.size - 1)
        val fahrenheit = st.prefs.fahrenheit
        LaunchedEffect(cityIndex, fahrenheit) {
            while (true) {
                val result = fetchWeather(CITIES[cityIndex], fahrenheit)
                if (result != null) st.weather = result
                delay(20L * 60L * 1000L)
            }
        }

        // Headlines, refreshed every 30 minutes.
        LaunchedEffect(Unit) {
            while (true) {
                val result = fetchHeadlines()
                if (result.isNotEmpty()) st.headlines = result
                delay(30L * 60L * 1000L)
            }
        }

        // Calendar, refreshed every 5 minutes and when permission changes.
        LaunchedEffect(st.calendarAllowed) {
            while (true) {
                st.events = withContext(Dispatchers.IO) { loadTodayEvents(st.ctx) }
                delay(5L * 60L * 1000L)
            }
        }

        // Installed apps.
        LaunchedEffect(Unit) { st.reloadApps() }

        BackHandler(enabled = st.overlay != Overlay.None) { st.overlay = Overlay.None }

        Box(Modifier.fillMaxSize().background(Color.Black)) {
            WallpaperBackground(period)

            HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
                when (page) {
                    0 -> ClockFace(nowState, minute, period)
                    1 -> BriefingFace(minute, period)
                    2 -> WeatherFace()
                    3 -> PhotosFace(minute)
                    else -> FlowFace(active = pager.currentPage == 4 && st.overlay == Overlay.None)
                }
            }

            TopBar(
                title = PAGE_NAMES[pager.currentPage],
                showWidgets = pager.currentPage == 1,
                modifier = Modifier.align(Alignment.TopCenter)
            )

            BottomBar(
                current = pager.currentPage,
                count = PAGE_NAMES.size,
                onDot = { i -> scope.launch { pager.animateScrollToPage(i) } },
                modifier = Modifier.align(Alignment.BottomCenter)
            )

            Crossfade(
                targetState = st.overlay,
                animationSpec = tween(220),
                label = "overlay"
            ) { overlay ->
                when (overlay) {
                    Overlay.None -> Unit
                    Overlay.Drawer -> AppDrawer()
                    Overlay.Settings -> SettingsPanel()
                    Overlay.ClockEditor -> ClockEditor(nowState, period)
                    Overlay.Widgets -> WidgetPicker()
                }
            }
        }
    }
}

@Composable
private fun GlassPill(text: String, onClick: () -> Unit) {
    Box(
        Modifier
            .glass(RoundedCornerShape(50))
            .clickable { onClick() }
            .padding(horizontal = 20.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun TopBar(title: String, showWidgets: Boolean, modifier: Modifier = Modifier) {
    val st = LocalLauncher.current
    Row(
        modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 32.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Label(title)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (showWidgets) {
                GlassPill("Edit widgets") { st.overlay = Overlay.Widgets }
            }
            GlassPill("Settings") { st.overlay = Overlay.Settings }
        }
    }
}

@Composable
private fun BottomBar(current: Int, count: Int, onDot: (Int) -> Unit, modifier: Modifier = Modifier) {
    val st = LocalLauncher.current
    val accent = parseColor(st.prefs.accent, DefaultAccent)
    Column(
        modifier
            .navigationBarsPadding()
            .padding(bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            for (i in 0 until count) {
                // The dot sits inside a larger tap target.
                Box(
                    Modifier
                        .size(24.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onDot(i) },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        Modifier
                            .size(if (i == current) 9.dp else 7.dp)
                            .clip(CircleShape)
                            .background(if (i == current) accent else Color.White.copy(alpha = 0.35f))
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        GlassPill("Apps") { st.overlay = Overlay.Drawer }
    }
}
