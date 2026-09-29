package com.example.telecomhand

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.annotation.DrawableRes
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.telecomhand.ui.theme.TelecomHandTheme
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs
import kotlin.math.roundToInt

private enum class AppVisualTheme(val label: String) {
    CLASSIC("Bleu classique"), CYBERPUNK("CP2077"), TRON("Tron"), MATRIX("Matrix"),
    BLACK("Noir"), WHITE("Blanc"), LAVENDER("Lavande pâle"), MINT("Menthe pâle"), CORAL("Corail")
}

private data class ThemePalette(
    val accent: Color, val secondary: Color, val ink: Color, val panel: Color,
    val muted: Color, val outline: Color, val top: Color, val padTop: Color,
    val primaryEnd: Color, val secondaryEnd: Color
)

private fun paletteFor(theme: AppVisualTheme) = when (theme) {
    AppVisualTheme.CLASSIC -> ThemePalette(Color(0xFF39B9FF), Color(0xFF6865F6), Color(0xFF091425), Color(0xFF1A3155), Color(0xFFB0C4E8), Color(0xFF416393), Color(0xFF183966), Color(0xFF203F70), Color(0xFF1664EB), Color(0xFF3730AC))
    AppVisualTheme.CYBERPUNK -> ThemePalette(Color(0xFFFFFF16), Color(0xFF00E6F3), Color(0xFF030B0D), Color(0xFF0A1D20), Color(0xFF9DF1F3), Color(0xFFE8EC17), Color(0xFF09212A), Color(0xFF0A2325), Color(0xFFD6CB00), Color(0xFF008C9B))
    AppVisualTheme.TRON -> ThemePalette(Color(0xFF36D8FF), Color(0xFF8AF0FF), Color(0xFF020A13), Color(0xFF0A2031), Color(0xFF9BD9F4), Color(0xFF2EADE5), Color(0xFF061A2F), Color(0xFF0B2434), Color(0xFF006AAF), Color(0xFF13618E))
    AppVisualTheme.MATRIX -> ThemePalette(Color(0xFF58FF8B), Color(0xFFB0FFD0), Color(0xFF010B07), Color(0xFF092016), Color(0xFFB1EAC3), Color(0xFF22B95B), Color(0xFF042511), Color(0xFF092318), Color(0xFF0E9340), Color(0xFF23794C))
    AppVisualTheme.BLACK -> ThemePalette(Color(0xFF4A90E2), Color(0xFF75B5F7), Color(0xFF050505), Color(0xFF111111), Color(0xFFBBBBBB), Color(0xFF343434), Color(0xFF0B0B0B), Color(0xFF111111), Color(0xFF2869B8), Color(0xFF3D7CC2))
    AppVisualTheme.WHITE -> ThemePalette(Color(0xFF1769C2), Color(0xFF4F91D4), Color(0xFFF5F5F5), Color(0xFFFFFFFF), Color(0xFF626262), Color(0xFFD1D1D1), Color(0xFFFFFFFF), Color(0xFFF7F7F7), Color(0xFF155FAE), Color(0xFF3E7FBA))
    AppVisualTheme.LAVENDER -> ThemePalette(Color(0xFFD6BDFF), Color(0xFFFFCBEB), Color(0xFF171628), Color(0xFF33304E), Color(0xFFE3D7F0), Color(0xFF9485B5), Color(0xFF393351), Color(0xFF403957), Color(0xFF9B7AD3), Color(0xFFB177B3))
    AppVisualTheme.MINT -> ThemePalette(Color(0xFFB5F4DE), Color(0xFFB9E2FF), Color(0xFF102120), Color(0xFF25453F), Color(0xFFD3ECE4), Color(0xFF729E91), Color(0xFF2E5650), Color(0xFF31534B), Color(0xFF7DB9A8), Color(0xFF79A4B5))
    AppVisualTheme.CORAL -> ThemePalette(Color(0xFFFF997E), Color(0xFFFFD37B), Color(0xFF25141C), Color(0xFF51313A), Color(0xFFF0CAD0), Color(0xFFA96D7D), Color(0xFF512B3B), Color(0xFF56343C), Color(0xFFD96B6F), Color(0xFFBB7753))
}

private var activeTheme by mutableStateOf(AppVisualTheme.CLASSIC)
private val currentPalette get() = paletteFor(activeTheme)
private val Accent get() = currentPalette.accent
private val Ink get() = currentPalette.ink
private val Panel get() = currentPalette.panel
private val Muted get() = currentPalette.muted
private val CardOutline get() = currentPalette.outline
private val ContentColor get() = if (activeTheme == AppVisualTheme.WHITE) Color(0xFF161616) else Color.White
private val MainShape get() = RoundedCornerShape(12.dp)

data class RemoteDevice(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val host: String,
    val port: Int = 45870,
    val token: String = "telecomhand",
    val os: DeviceOs = DeviceOs.WINDOWS
)

enum class DeviceOs { WINDOWS, LINUX }

private data class AudioOutput(val id: String, val name: String, val active: Boolean)

enum class ConnectionState { OFFLINE, CONNECTING, CONNECTED }
private enum class AppTab { HOME, KEYBOARD, SHORTCUTS, MORE }
private data class RemoteTile(val id: String, val name: String, val image: String = "")
private data class MediaInfo(val available: Boolean = false, val title: String = "", val artist: String = "", val playing: Boolean = false, val position: Int = 0, val duration: Int = 0)
private data class RemoteState(val raspyTv: Boolean = false, val apps: List<RemoteTile> = emptyList(), val games: List<RemoteTile> = emptyList(), val media: MediaInfo = MediaInfo())

@DrawableRes
private fun tileAsset(id: String, name: String = ""): Int = when {
    id == "jellyfin" && name.equals("Lumo", true) -> R.drawable.logo_lumo
    id == "browser" -> R.drawable.logo_browser
    id == "terminal" -> R.drawable.logo_terminal
    id == "explorer" -> R.drawable.logo_explorer
    id == "mail" -> R.drawable.logo_mail
    id == "notes" -> R.drawable.logo_notes
    id == "youtube" -> R.drawable.logo_youtube
    id == "spotify" -> R.drawable.logo_spotify
    id == "netflix" -> R.drawable.logo_netflix
    id == "twitch" -> R.drawable.logo_twitch
    id == "jellyfin" || id == "jellyfin-web" -> R.drawable.logo_jellyfin
    id == "immich" -> R.drawable.logo_immich
    id == "homelab" -> R.drawable.logo_dashy
    id == "lumo" -> R.drawable.logo_lumo
    else -> R.drawable.logo_moonlight
}

@Composable
private fun AssetLogo(@DrawableRes resource: Int, description: String?, modifier: Modifier = Modifier) {
    Image(painter = painterResource(resource), contentDescription = description, modifier = modifier, contentScale = androidx.compose.ui.layout.ContentScale.Fit)
}

private data class TrailPixel(val position: Offset, val bornAt: Long, val size: Float)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        activeTheme = DeviceStore(this).loadTheme()
        enableEdgeToEdge()
        setContent { TelecomHandTheme { TelecomHandApp() } }
    }
}

@Composable
private fun TelecomHandApp() {
    val context = LocalContext.current
    val store = remember { DeviceStore(context) }
    var devices by remember { mutableStateOf(store.load()) }
    var selectedId by remember { mutableStateOf(store.selectedId()) }
    var settingsOpen by remember { mutableStateOf(false) }
    var screenMode by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableStateOf(AppTab.HOME) }
    var theme by remember { mutableStateOf(store.loadTheme()) }
    val selected = devices.firstOrNull { it.id == selectedId } ?: devices.firstOrNull()
    val connection = remember { RemoteConnection() }
    var connectionState by remember { mutableStateOf(ConnectionState.OFFLINE) }
    var remoteState by remember { mutableStateOf(RemoteState()) }

    DisposableEffect(selected) {
        if (selected == null) {
            connection.disconnect()
            connectionState = ConnectionState.OFFLINE
        } else {
            connectionState = ConnectionState.CONNECTING
            connection.connect(selected) { connectionState = it }
        }
        onDispose { connection.disconnect() }
    }
    LaunchedEffect(selected) {
        while (isActive && selected != null) {
            delay(2000)
            connection.send("ping")
        }
    }
    LaunchedEffect(selected, connectionState, selectedTab) {
        if (selected == null || connectionState != ConnectionState.CONNECTED) {
            remoteState = RemoteState()
            return@LaunchedEffect
        }
        while (isActive) {
            remoteState = withContext(Dispatchers.IO) { connection.fetchRemoteState(selected) }
            delay(if (selectedTab == AppTab.MORE) 2500 else 8000)
        }
    }

    Surface(Modifier.fillMaxSize(), color = Ink) {
        Box(Modifier.fillMaxSize().background(Ink)) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 18.dp, vertical = 10.dp)) {
                TopBar(
                    selected, connectionState, devices,
                    onSelect = { selectedId = it.id; store.select(it.id); selectedTab = AppTab.HOME },
                    onSettings = { settingsOpen = true }
                )
                Spacer(Modifier.height(12.dp))
                AnimatedContent(selectedTab, label = "main-tab", modifier = Modifier.weight(1f)) { tab ->
                    when (tab) {
                        AppTab.HOME -> HomeScreen(selected, connection, connectionState, screenMode, { screenMode = it }, { selectedTab = AppTab.KEYBOARD }, { selectedTab = AppTab.SHORTCUTS })
                        AppTab.KEYBOARD -> KeyboardScreen(connectionState == ConnectionState.CONNECTED, connection)
                        AppTab.SHORTCUTS -> ShortcutsScreen(selected, connection, connectionState == ConnectionState.CONNECTED, remoteState)
                        AppTab.MORE -> MoreScreen(selected, connection, connectionState == ConnectionState.CONNECTED, remoteState)
                    }
                }
                Spacer(Modifier.height(10.dp))
                BottomNavigation(
                    selectedTab,
                    onSelect = { selectedTab = it }
                )
            }
        }
    }

    if (settingsOpen) {
        DeviceManager(devices, theme, onThemeChange = {
            theme = it
            activeTheme = it
            store.saveTheme(it)
        }, onDismiss = { settingsOpen = false }) { updated ->
            devices = updated
            if (selectedId !in updated.map { it.id }) selectedId = updated.firstOrNull()?.id
            store.save(updated, selectedId)
            settingsOpen = false
        }
    }
}

@Composable
private fun HomeScreen(
    selected: RemoteDevice?, connection: RemoteConnection, state: ConnectionState,
    screenMode: Boolean, onModeChange: (Boolean) -> Unit, onKeyboard: () -> Unit, onShortcuts: () -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        ModeSwitch(screenMode, onModeChange)
        Spacer(Modifier.height(10.dp))
        AnimatedContent(screenMode, label = "control-mode", modifier = Modifier.weight(1f)) { preview ->
            if (preview) ScreenPreview(selected, connection) else TouchSurface(connection, state)
        }
        Spacer(Modifier.height(10.dp))
        QuickControls(
            enabled = state == ConnectionState.CONNECTED, keyboardOpen = false,
            onLeft = { connection.send("click", "button" to "left") }, onKeyboard = onKeyboard,
            onRight = { connection.send("click", "button" to "right") }
        )
        Spacer(Modifier.height(7.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            CompactButton("Vol −", "−", state == ConnectionState.CONNECTED, Modifier.weight(1f)) { connection.send("volume", "action" to "down") }
            CompactButton("Muet", "×", state == ConnectionState.CONNECTED, Modifier.weight(1f)) { connection.send("volume", "action" to "mute") }
            CompactButton("Vol +", "+", state == ConnectionState.CONNECTED, Modifier.weight(1f)) { connection.send("volume", "action" to "up") }
        }
        Spacer(Modifier.height(7.dp))
        FlatNavigationRow(
            if (selected?.os == DeviceOs.LINUX) R.drawable.logo_linux else R.drawable.logo_windows,
            if (selected?.os == DeviceOs.WINDOWS) "Raccourcis Windows" else "Raccourcis Linux", onShortcuts
        )
    }
}

@Composable
private fun BottomNavigation(
    selected: AppTab, onSelect: (AppTab) -> Unit
) {
    Row(
        Modifier.fillMaxWidth().clip(MainShape)
            .background(Ink)
            .border(.5.dp, CardOutline.copy(alpha = .75f), MainShape)
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        val tabs = listOf(
            Triple(Icons.Outlined.Home, "Accueil", AppTab.HOME),
            Triple(Icons.Outlined.Keyboard, "Clavier", AppTab.KEYBOARD),
            Triple(Icons.Outlined.GridView, "Raccourcis", AppTab.SHORTCUTS),
            Triple(Icons.Outlined.MoreHoriz, "Plus", AppTab.MORE)
        )
        tabs.forEach { tab ->
            val active = selected == tab.third
            Column(
                Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).clickable { onSelect(tab.third) }
                    .padding(vertical = 3.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(tab.first, null, tint = if (active) Accent else Muted, modifier = Modifier.size(18.dp))
                Text(tab.second, color = if (active) Accent else Muted, fontSize = 9.sp, maxLines = 1)
            }
        }
    }
}

@Composable
private fun FlatNavigationRow(@DrawableRes icon: Int, label: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(48.dp).clip(MainShape).background(Panel)
            .border(.5.dp, CardOutline, MainShape).clickable(onClick = onClick).padding(horizontal = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AssetLogo(icon, null, Modifier.size(19.dp))
        Spacer(Modifier.width(10.dp))
        Text(label, color = ContentColor, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.weight(1f))
        Icon(Icons.Outlined.ChevronRight, null, tint = Muted, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun TopBar(
    selected: RemoteDevice?, state: ConnectionState, devices: List<RemoteDevice>,
    onSelect: (RemoteDevice) -> Unit, onSettings: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.weight(1f)) {
            Box(
                Modifier.fillMaxWidth().clip(MainShape).background(Panel)
                    .border(.5.dp, CardOutline, MainShape).clickable { expanded = true }
            ) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
                    AssetLogo(
                        if (selected?.os == DeviceOs.LINUX) R.drawable.logo_linux else R.drawable.logo_windows,
                        selected?.os?.name, Modifier.size(24.dp)
                    )
                    Spacer(Modifier.width(9.dp))
                    Column(Modifier.weight(1f)) {
                        Text(selected?.name ?: "Aucun appareil", color = ContentColor, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(7.dp).background(when (state) {
                                ConnectionState.CONNECTED -> Color(0xFF48E3AE)
                                ConnectionState.CONNECTING -> Color(0xFFFFC857)
                                ConnectionState.OFFLINE -> Color(0xFFFF667A)
                            }, CircleShape))
                            Spacer(Modifier.width(5.dp))
                            Text(when (state) {
                                ConnectionState.CONNECTED -> "Connecté"
                                ConnectionState.CONNECTING -> "Connexion…"
                                ConnectionState.OFFLINE -> "Hors ligne"
                            }, color = if (state == ConnectionState.CONNECTED) Color(0xFF58E8BA) else Muted, fontSize = 10.sp)
                        }
                    }
                    Icon(Icons.Outlined.ChevronRight, null, tint = Muted, modifier = Modifier.size(20.dp))
                }
            }
            DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
                devices.forEach { device -> DropdownMenuItem(
                    text = { Text(device.name) },
                    onClick = { expanded = false; onSelect(device) },
                    trailingIcon = { if (device.id == selected?.id) Text("✓", color = Accent) }
                ) }
                if (devices.isEmpty()) DropdownMenuItem(text = { Text("Ajoutez un appareil") }, onClick = onSettings)
            }
        }
        Column(Modifier.weight(1.16f), horizontalAlignment = Alignment.CenterHorizontally) {
            Row {
                Text("Telecom", color = ContentColor, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text("Hand", color = Accent, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            Text(if (selected?.os == DeviceOs.LINUX) "Contrôlez votre Linux" else "Contrôlez votre PC", color = Muted, fontSize = 10.sp, maxLines = 1)
        }
        Box(
            Modifier.size(42.dp).clip(CircleShape).background(Panel)
                .border(.5.dp, CardOutline, CircleShape).clickable(onClick = onSettings),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Outlined.Settings, "Paramètres", tint = Muted, modifier = Modifier.size(21.dp))
        }
    }
}

@Composable
private fun ModeSwitch(screenMode: Boolean, onChange: (Boolean) -> Unit) {
    Box(Modifier.fillMaxWidth().clip(MainShape).background(Panel).border(.5.dp, CardOutline, MainShape)) {
        Row(Modifier.padding(4.dp)) {
            ModeTab(Icons.Outlined.TouchApp, "Pavé tactile", !screenMode, Modifier.weight(1f)) { onChange(false) }
            ModeTab(Icons.Outlined.Monitor, "Aperçu écran", screenMode, Modifier.weight(1f)) { onChange(true) }
        }
    }
}

@Composable
private fun ModeTab(icon: ImageVector, label: String, active: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(modifier.clip(MainShape).background(if (active) Accent.copy(alpha = .12f) else Color.Transparent).clickable(onClick = onClick)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = if (active) Accent else Muted, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(label, color = if (active) Accent else Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun TouchSurface(connection: RemoteConnection, state: ConnectionState) {
    var gestureLabel by remember { mutableStateOf("Glissez pour déplacer") }
    var activePosition by remember { mutableStateOf<Offset?>(null) }
    var trail by remember { mutableStateOf<List<TrailPixel>>(emptyList()) }
    LaunchedEffect(Unit) {
        while (isActive) {
            val now = SystemClock.uptimeMillis()
            trail = trail.filter { now - it.bornAt < 560L }
            delay(16)
        }
    }
    Surface(
        modifier = Modifier.fillMaxSize().pointerInput(connection) {
            awaitEachGesture {
                val first = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Main)
                activePosition = first.position
                trail = trail + TrailPixel(first.position, SystemClock.uptimeMillis(), 9f)
                val startedAt = SystemClock.uptimeMillis()
                val longPressDelay = 380L
                val movementThreshold = viewConfiguration.touchSlop
                var last = first.position
                var travelled = 0f
                var moved = false
                var dragging = false
                var usedTwoFingers = false
                var lastScrollCenter: Float? = null
                var scrollRemainder = 0f
                try {
                    while (true) {
                    val remaining = longPressDelay - (SystemClock.uptimeMillis() - startedAt)
                    val event = if (!moved && !dragging && remaining > 0) {
                        withTimeoutOrNull(remaining) {
                            awaitPointerEvent(PointerEventPass.Main)
                        }
                    } else {
                        awaitPointerEvent(PointerEventPass.Main)
                    }
                    if (event == null) {
                        dragging = true
                        connection.send("button_down", "button" to "left")
                        gestureLabel = "Clic gauche maintenu"
                        continue
                    }
                    val pressed = event.changes.filter { it.pressed }
                    if (pressed.isEmpty()) {
                        if (dragging) {
                            connection.send("button_up", "button" to "left")
                            dragging = false
                        }
                        else if (!moved && !usedTwoFingers) connection.send("click", "button" to "left")
                        break
                    }
                    if (pressed.size >= 2) {
                        if (dragging) {
                            connection.send("button_up", "button" to "left")
                            dragging = false
                        }
                        usedTwoFingers = true
                        moved = true
                        val averageY = pressed.take(2).map { it.position.y }.average().toFloat()
                        val previousCenter = lastScrollCenter
                        val dy = if (previousCenter == null) 0f else averageY - previousCenter
                        val averageX = pressed.take(2).map { it.position.x }.average().toFloat()
                        activePosition = Offset(averageX, averageY)
                        trail = (trail + TrailPixel(Offset(averageX, averageY), SystemClock.uptimeMillis(), 7f)).takeLast(56)
                        scrollRemainder += -dy * .24f
                        val scrollSteps = scrollRemainder.toInt()
                        if (scrollSteps != 0) {
                            connection.send("scroll", "dy" to scrollSteps)
                            scrollRemainder -= scrollSteps
                            gestureLabel = "Défilement à deux doigts"
                        }
                        lastScrollCenter = averageY
                        last = pressed[0].position
                    } else {
                        val current = pressed[0].position
                        val delta = if (lastScrollCenter != null) Offset.Zero else current - last
                        activePosition = current
                        if (delta.getDistance() >= .35f) {
                            val now = SystemClock.uptimeMillis()
                            val steps = (delta.getDistance() / 10f).roundToInt().coerceIn(1, 6)
                            val additions = (1..steps).map { step ->
                                val fraction = step.toFloat() / steps
                                TrailPixel(last + delta * fraction, now - ((steps - step) * 12L), 5f + 5f * fraction)
                            }
                            trail = (trail + additions).takeLast(56)
                        }
                        lastScrollCenter = null
                        travelled += delta.getDistance()
                        if (travelled >= movementThreshold) moved = true
                        if (abs(delta.x) + abs(delta.y) >= .35f) {
                            val dx = (delta.x * 1.35f).roundToInt()
                            val dy = (delta.y * 1.35f).roundToInt()
                            if (dx != 0 || dy != 0) connection.send("move", "dx" to dx, "dy" to dy)
                        }
                        last = current
                    }
                    event.changes.forEach { it.consume() }
                    }
                } finally {
                    if (dragging) connection.send("button_up", "button" to "left")
                    activePosition = null
                    gestureLabel = "Glissez pour déplacer"
                }
            }
        },
        color = Panel, shape = MainShape,
        border = androidx.compose.foundation.BorderStroke(.5.dp, CardOutline)
    ) {
        Box(Modifier.fillMaxSize().background(Panel)) {
            Canvas(Modifier.fillMaxSize()) {
                val accent = Accent
                val now = SystemClock.uptimeMillis()
                trail.forEach { pixel ->
                    val life = (1f - (now - pixel.bornAt) / 560f).coerceIn(0f, 1f)
                    val side = (pixel.size * life.coerceAtLeast(.35f)).dp.toPx()
                    drawRect(
                        color = accent.copy(alpha = .72f * life),
                        topLeft = pixel.position - Offset(side / 2f, side / 2f),
                        size = androidx.compose.ui.geometry.Size(side, side)
                    )
                }
                val rawKnob = activePosition ?: Offset(size.width - 18.dp.toPx(), size.height - 18.dp.toPx())
                val safeRadius = 8.dp.toPx()
                val knob = Offset(
                    rawKnob.x.coerceIn(safeRadius, size.width - safeRadius),
                    rawKnob.y.coerceIn(safeRadius, size.height - safeRadius)
                )
                drawCircle(accent, if (activePosition == null) 2.5.dp.toPx() else 5.dp.toPx(), knob)
            }
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (state == ConnectionState.CONNECTED) gestureLabel else "Connectez un appareil", color = Muted, fontWeight = FontWeight.Medium, fontSize = 11.sp)
                Text("clic · appui long · défilement à 2 doigts", color = Muted.copy(alpha = .65f), fontSize = 8.sp)
            }
        }
    }
}

@Composable
private fun ScreenPreview(device: RemoteDevice?, connection: RemoteConnection) {
    var frame by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(device?.id) {
        while (isActive && device != null) {
            val result = withContext(Dispatchers.IO) { connection.fetchFrame(device) }
            if (result != null) { frame = result; error = null } else error = "Aperçu indisponible"
            delay(350)
        }
    }
    Surface(Modifier.fillMaxSize(), color = Color.Black, shape = RoundedCornerShape(26.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(.1f))) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (frame != null) Image(frame!!, "Écran distant", Modifier.fillMaxSize().padding(5.dp), contentScale = androidx.compose.ui.layout.ContentScale.Fit)
            else Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = Accent, strokeWidth = 2.dp); Spacer(Modifier.height(12.dp)); Text(error ?: "Chargement de l’écran…", color = Muted)
            }
        }
    }
}

@Composable
private fun QuickControls(enabled: Boolean, keyboardOpen: Boolean, onLeft: () -> Unit, onKeyboard: () -> Unit, onRight: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        ControlButton("Gauche", Icons.Outlined.Mouse, enabled, Modifier.weight(1f), onLeft)
        ControlButton(if (keyboardOpen) "Fermer" else "Clavier", Icons.Outlined.Keyboard, true, Modifier.weight(1f), onKeyboard)
        ControlButton("Droit", Icons.Outlined.Mouse, enabled, Modifier.weight(1f), onRight)
    }
}

@Composable
private fun ControlButton(label: String, icon: ImageVector, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(modifier.height(55.dp).clip(MainShape).background(Panel)
        .border(.5.dp, if (enabled) CardOutline else CardOutline.copy(.5f), MainShape)
        .clickable(enabled = enabled, onClick = onClick)) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, tint = if (enabled) Muted else Muted.copy(.45f), modifier = Modifier.size(16.dp))
            Text(label, color = if (enabled) ContentColor else Muted.copy(.5f), fontSize = 9.sp, maxLines = 1)
        }
    }
}

@Composable
private fun SystemControls(
    device: RemoteDevice?, connection: RemoteConnection, enabled: Boolean,
    shortcutsExpanded: Boolean, onShortcutsExpanded: (Boolean) -> Unit, audioMenuSignal: Int
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconCompactButton("Volume −", Icons.Outlined.VolumeDown, enabled, Modifier.weight(1f)) { connection.send("volume", "action" to "down") }
            IconCompactButton("Muet", Icons.Outlined.VolumeOff, enabled, Modifier.weight(1f)) { connection.send("volume", "action" to "mute") }
            IconCompactButton("Volume +", Icons.Outlined.VolumeUp, enabled, Modifier.weight(1f)) { connection.send("volume", "action" to "up") }
        }
        when (device?.os) {
            DeviceOs.WINDOWS -> WindowsShortcutPanel(connection, enabled, device.id, shortcutsExpanded, onShortcutsExpanded)
            DeviceOs.LINUX -> LinuxAudioOutputPicker(device, connection, enabled, audioMenuSignal)
            null -> Unit
        }
    }
}

@Composable
private fun IconCompactButton(label: String, icon: ImageVector, enabled: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        modifier = modifier.height(43.dp).clickable(enabled = enabled, onClick = onClick), shape = MainShape,
        color = if (enabled) Panel else Panel.copy(.45f), border = androidx.compose.foundation.BorderStroke(.5.dp, CardOutline)
    ) {
        Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            Icon(icon, null, tint = if (enabled) Accent else Muted.copy(.5f), modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(5.dp))
            Text(label, color = if (enabled) ContentColor else Muted.copy(.5f), fontSize = 10.sp, maxLines = 1)
        }
    }
}

@Composable
private fun CompactButton(
    label: String, symbol: String, enabled: Boolean, modifier: Modifier = Modifier,
    active: Boolean = false, onClick: () -> Unit
) {
    Surface(
        modifier = modifier.height(43.dp).clickable(enabled = enabled, onClick = onClick),
        shape = MainShape,
        color = when { active -> Accent.copy(.1f); enabled -> Panel; else -> Panel.copy(.45f) },
        border = androidx.compose.foundation.BorderStroke(.5.dp, if (active) Accent else CardOutline)
    ) {
        Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            if (symbol.isNotBlank()) {
                Text(symbol, color = if (active) ContentColor else Accent, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(5.dp))
            }
            Text(label, color = if (enabled) ContentColor else Muted.copy(.5f), fontSize = 10.sp, maxLines = 1)
        }
    }
}

@Composable
private fun WindowsShortcutPanel(connection: RemoteConnection, enabled: Boolean, deviceId: String, expanded: Boolean, onExpanded: (Boolean) -> Unit) {
    var ctrlHeld by remember(deviceId) { mutableStateOf(false) }
    var altHeld by remember(deviceId) { mutableStateOf(false) }
    val latestCtrl by rememberUpdatedState(ctrlHeld)
    val latestAlt by rememberUpdatedState(altHeld)
    DisposableEffect(deviceId) {
        onDispose {
            if (latestCtrl) connection.send("key_up", "value" to "ctrl")
            if (latestAlt) connection.send("key_up", "value" to "alt")
        }
    }
    fun releaseModifiers() {
        if (ctrlHeld) { ctrlHeld = false; connection.send("key_up", "value" to "ctrl") }
        if (altHeld) { altHeld = false; connection.send("key_up", "value" to "alt") }
    }
    fun hotkey(vararg keys: String) {
        releaseModifiers()
        connection.send("hotkey", "keys" to JSONArray(keys.toList()))
    }

    Surface(color = Panel, shape = MainShape, border = androidx.compose.foundation.BorderStroke(1.dp, CardOutline)) {
        Column {
            Row(
                Modifier.fillMaxWidth().clickable { onExpanded(!expanded) }.padding(horizontal = 15.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AssetLogo(R.drawable.logo_windows, "Windows", Modifier.size(24.dp))
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Raccourcis Windows", color = ContentColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text("Accès rapide aux actions courantes", color = Muted, fontSize = 10.sp)
                }
                Spacer(Modifier.weight(1f))
                Icon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ChevronRight, null, tint = Muted)
            }
            AnimatedVisibility(expanded) {
                Column(Modifier.padding(start = 8.dp, end = 8.dp, bottom = 9.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        CompactButton("Démarrer", "", enabled, Modifier.weight(1f)) { hotkey("win") }
                        CompactButton("Ctrl", "", enabled, Modifier.weight(1f), ctrlHeld) {
                            val next = !ctrlHeld; ctrlHeld = next; connection.send(if (next) "key_down" else "key_up", "value" to "ctrl")
                        }
                        CompactButton("Alt", "", enabled, Modifier.weight(1f), altHeld) {
                            val next = !altHeld; altHeld = next; connection.send(if (next) "key_down" else "key_up", "value" to "alt")
                        }
                        CompactButton("Verrouiller", "", enabled && connection.supportsScreenLock, Modifier.weight(1f)) { releaseModifiers(); connection.send("lock_screen") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        CompactButton("Changer", "", enabled, Modifier.weight(1f)) { hotkey("alt", "tab") }
                        CompactButton("Bureau", "", enabled, Modifier.weight(1f)) { hotkey("win", "d") }
                        CompactButton("Fichiers", "", enabled, Modifier.weight(1f)) { hotkey("win", "e") }
                        CompactButton("Capture", "", enabled, Modifier.weight(1f)) { hotkey("win", "shift", "s") }
                    }
                    if (enabled && !connection.supportsScreenLock) Text(
                        "Mettez à jour l’agent Windows pour activer le verrouillage.",
                        color = Muted, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 6.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun LinuxAudioOutputPicker(device: RemoteDevice, connection: RemoteConnection, enabled: Boolean, audioMenuSignal: Int) {
    var outputs by remember(device.id) { mutableStateOf<List<AudioOutput>>(emptyList()) }
    var expanded by remember { mutableStateOf(false) }
    var loading by remember(device.id) { mutableStateOf(true) }

    suspend fun refresh() {
        loading = true
        outputs = withContext(Dispatchers.IO) { connection.fetchAudioOutputs(device) }
        loading = false
    }
    LaunchedEffect(device.id, enabled) { if (enabled) refresh() }
    LaunchedEffect(audioMenuSignal) { if (audioMenuSignal > 0 && enabled) expanded = true }
    val active = outputs.firstOrNull { it.active }

    Box {
        Surface(
            Modifier.fillMaxWidth().clickable(enabled = enabled && !loading) { expanded = true },
            color = Panel, shape = RoundedCornerShape(18.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, CardOutline)
        ) {
            Row(Modifier.padding(horizontal = 14.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Speaker, null, tint = Accent, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text("Sortie audio Linux", color = ContentColor, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Text(if (loading) "Recherche…" else active?.name ?: "Choisir un périphérique", color = Muted, fontSize = 10.sp, maxLines = 1)
                }
                Icon(Icons.Outlined.ExpandMore, null, tint = Muted, modifier = Modifier.size(20.dp))
            }
        }
        DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
            outputs.forEach { output ->
                DropdownMenuItem(
                    text = { Text(output.name) },
                    leadingIcon = { Icon(if (output.active) Icons.Outlined.RadioButtonChecked else Icons.Outlined.RadioButtonUnchecked, null, tint = if (output.active) Accent else Muted) },
                    onClick = {
                        expanded = false
                        outputs = outputs.map { it.copy(active = it.id == output.id) }
                        connection.send("audio_output", "id" to output.id)
                    }
                )
            }
            if (outputs.isEmpty() && !loading) DropdownMenuItem(text = { Text("Aucune sortie détectée") }, onClick = {})
        }
    }
}

@Composable
private fun ScreenHeading(title: String, subtitle: String) {
    Column(Modifier.padding(bottom = 10.dp)) {
        Text(title, color = ContentColor, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(subtitle, color = Muted, fontSize = 10.sp)
    }
}

@Composable
private fun SectionLabel(label: String) {
    Text(label, color = Muted, fontSize = 10.sp, modifier = Modifier.padding(top = 12.dp, bottom = 5.dp))
}

@Composable
private fun KeyboardScreen(enabled: Boolean, connection: RemoteConnection) {
    var text by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize()) {
        ScreenHeading("Clavier", "Texte direct et touches spéciales")
        OutlinedTextField(
            value = text,
            onValueChange = { updated ->
                if (enabled) {
                    var common = 0
                    val limit = minOf(text.length, updated.length)
                    while (common < limit && text[common] == updated[common]) common++
                    repeat(text.length - common) { connection.send("key", "value" to "backspace") }
                    if (common < updated.length) connection.send("text", "value" to updated.substring(common))
                }
                text = updated
            },
            modifier = Modifier.fillMaxWidth(), enabled = enabled, singleLine = true,
            placeholder = { Text("Écrire du texte…") },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { connection.send("key", "value" to "enter") }),
            trailingIcon = { IconButton(onClick = { connection.send("key", "value" to "backspace") }, enabled = enabled) { Icon(Icons.Outlined.Backspace, "Effacer", tint = Accent) } },
            shape = MainShape,
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Accent, unfocusedBorderColor = CardOutline)
        )
        Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(bottom = 12.dp)) {
            SectionLabel("Navigation")
            KeyRows(enabled, connection, listOf(
                listOf("home" to "Origine", "end" to "Fin", "pageup" to "Pg préc", "pagedown" to "Pg suiv"),
                listOf("left" to "Gauche", "up" to "Haut", "down" to "Bas", "right" to "Droite")
            ))
            SectionLabel("Système")
            KeyRows(enabled, connection, listOf(
                listOf("escape" to "Échap", "tab" to "Tab", "capslock" to "Verr Maj"),
                listOf("enter" to "Entrée", "space" to "Espace", "delete" to "Suppr")
            ))
            SectionLabel("Fonction")
            KeyRows(enabled, connection, (1..12).chunked(4).map { row -> row.map { "f$it" to "F$it" } })
        }
    }
}

@Composable
private fun KeyRows(enabled: Boolean, connection: RemoteConnection, rows: List<List<Pair<String, String>>>) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { (key, label) ->
                    CompactButton(label, "", enabled, Modifier.weight(1f)) { connection.send("key", "value" to key) }
                }
                repeat((4 - row.size).coerceAtLeast(0)) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun ShortcutsScreen(device: RemoteDevice?, connection: RemoteConnection, enabled: Boolean, remote: RemoteState) {
    Column(Modifier.fillMaxSize()) {
        ScreenHeading("Raccourcis", if (device?.os == DeviceOs.WINDOWS) "Actions Windows" else if (remote.raspyTv) "Linux · RaspyTV détecté" else "Actions Linux")
        Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())) {
            when (device?.os) {
                DeviceOs.WINDOWS -> {
                    WindowsShortcutPanel(connection, enabled, device.id, true) {}
                    SectionLabel("Édition")
                    ShortcutRows(connection, enabled, listOf(
                        listOf(listOf("ctrl", "c") to "Copier", listOf("ctrl", "v") to "Coller", listOf("ctrl", "x") to "Couper"),
                        listOf(listOf("ctrl", "z") to "Annuler", listOf("ctrl", "a") to "Tout sélectionner", listOf("ctrl", "shift", "escape") to "Gestionnaire")
                    ))
                }
                DeviceOs.LINUX -> {
                    LinuxAudioOutputPicker(device, connection, enabled, 0)
                    if (remote.raspyTv) {
                        SectionLabel("RaspyTV")
                        RemoteTileGrid(remote.apps, enabled) { connection.send("launch_app", "id" to it.id) }
                        SectionLabel("Jeux Moonlight")
                        RemoteTileGrid(remote.games, enabled) { connection.send("launch_game", "id" to it.id) }
                    }
                }
                null -> Text("Ajoutez un appareil dans les paramètres.", color = Muted)
            }
        }
    }
}

@Composable
private fun ShortcutRows(connection: RemoteConnection, enabled: Boolean, rows: List<List<Pair<List<String>, String>>>) {
    rows.forEach { row ->
        Row(Modifier.padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            row.forEach { (keys, label) ->
                CompactButton(label, "", enabled, Modifier.weight(1f)) { connection.send("hotkey", "keys" to JSONArray(keys)) }
            }
        }
    }
}

@Composable
private fun MoreScreen(device: RemoteDevice?, connection: RemoteConnection, enabled: Boolean, remote: RemoteState) {
    var showGames by remember(device?.id) { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        ScreenHeading("Plus", "Média et lanceur rapide")
        Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())) {
            MediaCard(remote.media, enabled) { connection.send("media_control", "action" to it) }
            SectionLabel(if (remote.raspyTv) "RaspyTV" else "Lancer une application")
            if (remote.raspyTv && remote.games.isNotEmpty()) {
                Row(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                    FilterChip(!showGames, { showGames = false }, { Text("Applications") }, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(7.dp))
                    FilterChip(showGames, { showGames = true }, { Text("Jeux") }, modifier = Modifier.weight(1f))
                }
            }
            val tiles = if (showGames && remote.raspyTv) remote.games else remote.apps
            RemoteTileGrid(tiles, enabled) { tile -> connection.send(if (showGames) "launch_game" else "launch_app", "id" to tile.id) }
            if (tiles.isEmpty()) Text(if (enabled) "Mettez l’agent de cet appareil à jour." else "Appareil hors ligne.", color = Muted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun MediaCard(media: MediaInfo, enabled: Boolean, onControl: (String) -> Unit) {
    Surface(color = Panel, shape = MainShape, border = androidx.compose.foundation.BorderStroke(.5.dp, CardOutline)) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Text(if (media.available) media.title else "Aucune lecture active", color = ContentColor, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(if (media.available) media.artist.ifBlank { "Appareil distant" } else "Les contrôles restent disponibles", color = Muted, fontSize = 10.sp, maxLines = 1)
            if (media.duration > 0) LinearProgressIndicator(
                progress = { (media.position.toFloat() / media.duration).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().padding(vertical = 9.dp).height(2.dp), color = Accent, trackColor = CardOutline
            ) else Spacer(Modifier.height(9.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onControl("previous") }, enabled = enabled) { Icon(Icons.Outlined.SkipPrevious, "Précédent", tint = Muted) }
                IconButton(onClick = { onControl("play_pause") }, enabled = enabled) { Icon(if (media.playing) Icons.Outlined.Pause else Icons.Outlined.PlayArrow, if (media.playing) "Pause" else "Lecture", tint = Accent, modifier = Modifier.size(26.dp)) }
                IconButton(onClick = { onControl("next") }, enabled = enabled) { Icon(Icons.Outlined.SkipNext, "Suivant", tint = Muted) }
            }
        }
    }
}

@Composable
private fun RemoteTileGrid(tiles: List<RemoteTile>, enabled: Boolean, onClick: (RemoteTile) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        tiles.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { tile ->
                    Surface(
                        Modifier.weight(1f).height(82.dp).clickable(enabled = enabled) { onClick(tile) },
                        color = Panel, shape = MainShape, border = androidx.compose.foundation.BorderStroke(.5.dp, CardOutline)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            if (tile.image.isBlank()) {
                                AssetLogo(tileAsset(tile.id, tile.name), tile.name, Modifier.size(30.dp))
                            } else {
                                AsyncImage(model = tile.image, contentDescription = tile.name, modifier = Modifier.size(44.dp), contentScale = androidx.compose.ui.layout.ContentScale.Fit)
                            }
                            Spacer(Modifier.height(3.dp))
                            Text(tile.name, color = if (enabled) ContentColor else Muted, fontSize = 9.sp, maxLines = 1)
                        }
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun QuickKeyboard(enabled: Boolean, onSend: (String) -> Unit, onKey: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    Column(Modifier.padding(top = 10.dp)) {
        OutlinedTextField(
            value = text,
            onValueChange = { updated ->
                if (enabled) {
                    var common = 0
                    val limit = minOf(text.length, updated.length)
                    while (common < limit && text[common] == updated[common]) common++
                    repeat(text.length - common) { onKey("backspace") }
                    if (common < updated.length) onSend(updated.substring(common))
                }
                text = updated
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
            singleLine = true,
            label = { Text("Saisie directe") },
            placeholder = { Text("Chaque caractère est envoyé au PC") },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { if (enabled) onKey("enter") }),
            trailingIcon = {
                TextButton(onClick = { if (enabled) onKey("backspace") }, enabled = enabled) {
                    Icon(Icons.Outlined.Backspace, "Effacer", tint = Accent)
                }
            },
            supportingText = { Text("Effacer supprime aussi le texte déjà présent sur l’appareil distant") },
            shape = RoundedCornerShape(16.dp)
        )
        Row(Modifier.padding(top = 7.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("enter" to "Entrée", "escape" to "Échap.", "backspace" to "Effacer distant", "delete" to "Suppr.").forEach { (key, label) -> AssistChip(onClick = { onKey(key) }, label = { Text(label) }, enabled = enabled) }
        }
    }
}

@Composable
private fun DeviceManager(
    devices: List<RemoteDevice>, theme: AppVisualTheme, onThemeChange: (AppVisualTheme) -> Unit,
    onDismiss: () -> Unit, onSave: (List<RemoteDevice>) -> Unit
) {
    var draft by remember { mutableStateOf(devices) }; var adding by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }; var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("45870") }; var token by remember { mutableStateOf("telecomhand") }
    var os by remember { mutableStateOf(DeviceOs.WINDOWS) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Mes appareils") }, text = {
        Column(Modifier.heightIn(max = 530.dp).verticalScroll(rememberScrollState())) {
            draft.forEach { device ->
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text(device.name, fontWeight = FontWeight.SemiBold); Text("${device.host}:${device.port}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp) }
                    AssistChip(
                        onClick = { draft = draft.map { if (it.id == device.id) it.copy(os = if (it.os == DeviceOs.WINDOWS) DeviceOs.LINUX else DeviceOs.WINDOWS) else it } },
                        label = { Text(if (device.os == DeviceOs.WINDOWS) "Windows" else "Linux", fontSize = 10.sp) }
                    )
                    TextButton(onClick = { draft = draft.filterNot { it.id == device.id } }) { Text("Supprimer") }
                }
            }
            if (adding) {
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                OutlinedTextField(name, { name = it }, label = { Text("Nom") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(7.dp)); OutlinedTextField(host, { host = it }, label = { Text("Adresse IP ou nom réseau") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(7.dp)); Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    OutlinedTextField(port, { port = it.filter(Char::isDigit) }, label = { Text("Port") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.weight(.7f))
                    OutlinedTextField(token, { token = it }, label = { Text("Code") }, singleLine = true, modifier = Modifier.weight(1.3f))
                }
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = os == DeviceOs.WINDOWS, onClick = { os = DeviceOs.WINDOWS }, label = { Text("Windows") }, leadingIcon = { AssetLogo(R.drawable.logo_windows, "Windows", Modifier.size(16.dp)) }, modifier = Modifier.weight(1f))
                    FilterChip(selected = os == DeviceOs.LINUX, onClick = { os = DeviceOs.LINUX }, label = { Text("Linux") }, leadingIcon = { AssetLogo(R.drawable.logo_linux, "Linux", Modifier.size(16.dp)) }, modifier = Modifier.weight(1f))
                }
                Button(onClick = { if (name.isNotBlank() && host.isNotBlank()) { draft = draft + RemoteDevice(name = name.trim(), host = host.trim(), port = port.toIntOrNull() ?: 45870, token = token, os = os); name = ""; host = ""; port = "45870"; os = DeviceOs.WINDOWS; adding = false } }, modifier = Modifier.padding(top = 10.dp).fillMaxWidth()) { Text("Ajouter cet appareil") }
            } else TextButton(onClick = { adding = true }, modifier = Modifier.fillMaxWidth()) { Text("＋ Ajouter un appareil") }
            HorizontalDivider(Modifier.padding(vertical = 10.dp))
            Text("Thème de l’interface", fontWeight = FontWeight.Bold)
            Text("Choisissez un style sans modifier les commandes.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            AppVisualTheme.entries.chunked(2).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEach { option ->
                        FilterChip(
                            selected = theme == option,
                            onClick = { onThemeChange(option) },
                            label = { Text(option.label, fontSize = 11.sp, maxLines = 1) },
                            leadingIcon = { Box(Modifier.size(10.dp).background(paletteFor(option).accent, CircleShape)) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }, confirmButton = { Button(onClick = { onSave(draft) }) { Text("Enregistrer") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } })
}

private class DeviceStore(context: Context) {
    private val prefs = context.getSharedPreferences("telecomhand_devices", Context.MODE_PRIVATE)
    fun load(): List<RemoteDevice> = runCatching {
        val array = JSONArray(prefs.getString("devices", "[]"))
        List(array.length()) { i ->
            array.getJSONObject(i).let {
                val storedPort = it.optInt("port", 45870)
                val storedName = it.getString("name")
                val storedOs = it.optString("os").takeIf(String::isNotBlank)?.let { value ->
                    runCatching { DeviceOs.valueOf(value) }.getOrNull()
                } ?: if (storedName.contains("linux", true) || storedName.contains("rasp", true) || storedName.trim().equals("pi", true)) DeviceOs.LINUX else DeviceOs.WINDOWS
                RemoteDevice(
                    id = it.getString("id"), name = storedName, host = it.getString("host"),
                    port = if (storedPort == 47990) 45870 else storedPort,
                    token = it.optString("token", "telecomhand"), os = storedOs
                )
            }
        }
    }.getOrElse { emptyList() }
    fun selectedId(): String? = prefs.getString("selected", null)
    fun loadTheme(): AppVisualTheme = runCatching {
        AppVisualTheme.valueOf(prefs.getString("theme", AppVisualTheme.CLASSIC.name) ?: AppVisualTheme.CLASSIC.name)
    }.getOrDefault(AppVisualTheme.CLASSIC)
    fun saveTheme(theme: AppVisualTheme) { prefs.edit().putString("theme", theme.name).apply() }
    fun select(id: String) = prefs.edit().putString("selected", id).apply()
    fun save(devices: List<RemoteDevice>, selected: String?) {
        val array = JSONArray(); devices.forEach { array.put(JSONObject().put("id", it.id).put("name", it.name).put("host", it.host).put("port", it.port).put("token", it.token).put("os", it.os.name)) }
        prefs.edit().putString("devices", array.toString()).putString("selected", selected).apply()
    }
}

private class RemoteConnection {
    private val connector = Executors.newSingleThreadExecutor()
    private val sender = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val generation = AtomicInteger(0)
    @Volatile private var socket: Socket? = null
    @Volatile private var writer: BufferedWriter? = null
    @Volatile private var device: RemoteDevice? = null
    @Volatile private var stateCallback: ((ConnectionState) -> Unit)? = null
    @Volatile private var protocolVersion: Int = 0
    val supportsScreenLock get() = protocolVersion >= 3

    fun connect(target: RemoteDevice, onState: (ConnectionState) -> Unit) {
        val attempt = generation.incrementAndGet()
        closeSocket()
        protocolVersion = 0
        device = target
        stateCallback = onState
        publish(ConnectionState.CONNECTING)
        startConnectionLoop(target, attempt)
    }

    private fun startConnectionLoop(target: RemoteDevice, attempt: Int) {
        connector.execute {
            while (generation.get() == attempt && socket == null) {
                var next: Socket? = null
                try {
                    Log.d("TelecomHand", "Connexion à ${target.host}:${target.port}")
                    next = Socket().apply {
                        connect(InetSocketAddress(target.host, target.port), 2500)
                        tcpNoDelay = true
                        soTimeout = 2500
                    }
                    val nextWriter = BufferedWriter(OutputStreamWriter(next.getOutputStream()))
                    val reader = BufferedReader(InputStreamReader(next.getInputStream()))
                    val hello = JSONObject().put("type", "hello").put("token", target.token)
                    nextWriter.write(hello.toString())
                    nextWriter.newLine()
                    nextWriter.flush()
                    val acknowledgement = reader.readLine()?.let(::JSONObject)
                    if (acknowledgement?.optString("status") != "ok") error("Handshake refusé")
                    val negotiatedVersion = acknowledgement.optInt("version", 0)
                    next.soTimeout = 0
                    if (generation.get() != attempt) {
                        next.close()
                        return@execute
                    }
                    socket = next
                    writer = nextWriter
                    protocolVersion = negotiatedVersion
                    Log.i("TelecomHand", "Client distant authentifié")
                    publish(ConnectionState.CONNECTED)
                    return@execute
                } catch (error: Exception) {
                    Log.w("TelecomHand", "Connexion impossible: ${error.message}")
                    runCatching { next?.close() }
                    if (generation.get() == attempt) {
                        publish(ConnectionState.OFFLINE)
                        Thread.sleep(1800)
                        if (generation.get() == attempt) publish(ConnectionState.CONNECTING)
                    }
                }
            }
        }
    }

    fun disconnect() {
        generation.incrementAndGet()
        device = null
        stateCallback = null
        protocolVersion = 0
        closeSocket()
    }

    @Synchronized
    private fun closeSocket() {
        runCatching { socket?.close() }
        socket = null
        writer = null
        protocolVersion = 0
    }

    private fun publish(state: ConnectionState) {
        val attempt = generation.get()
        mainHandler.post { if (generation.get() == attempt) stateCallback?.invoke(state) }
    }

    fun send(type: String, vararg values: Pair<String, Any>) {
        val target = device ?: return
        val attempt = generation.get()
        val message = JSONObject().put("type", type).put("token", target.token)
        values.forEach { message.put(it.first, it.second) }
        sender.execute {
            if (generation.get() != attempt || device?.id != target.id) return@execute
            val output = writer ?: return@execute
            try {
                synchronized(output) {
                    if (generation.get() != attempt || writer !== output) return@synchronized
                    output.write(message.toString())
                    output.newLine()
                    output.flush()
                }
            } catch (error: Exception) {
                Log.w("TelecomHand", "Commande $type non envoyée: ${error.message}")
                if (generation.get() == attempt) {
                    closeSocket()
                    publish(ConnectionState.OFFLINE)
                    startConnectionLoop(target, attempt)
                }
            }
        }
    }
    fun fetchFrame(target: RemoteDevice): androidx.compose.ui.graphics.ImageBitmap? = runCatching {
        val url = URL("http://${target.host}:${target.port + 1}/screen.jpg?token=${java.net.URLEncoder.encode(target.token, "UTF-8")}")
        val connection = (url.openConnection() as HttpURLConnection).apply { connectTimeout = 1400; readTimeout = 2500; useCaches = false }
        try { connection.inputStream.use { BitmapFactory.decodeStream(it)?.asImageBitmap() } }
        finally { connection.disconnect() }
    }.getOrNull()

    fun fetchAudioOutputs(target: RemoteDevice): List<AudioOutput> = runCatching {
        val url = URL("http://${target.host}:${target.port + 1}/audio-outputs.json?token=${java.net.URLEncoder.encode(target.token, "UTF-8")}")
        val connection = (url.openConnection() as HttpURLConnection).apply { connectTimeout = 1800; readTimeout = 3000; useCaches = false }
        val body = try { connection.inputStream.bufferedReader().use { it.readText() } }
        finally { connection.disconnect() }
        val array = JSONObject(body).getJSONArray("outputs")
        List(array.length()) { index ->
            array.getJSONObject(index).let { AudioOutput(it.getString("id"), it.getString("name"), it.optBoolean("active")) }
        }.also { Log.i("TelecomHand", "${it.size} sortie(s) audio détectée(s)") }
    }.getOrElse { error ->
        Log.w("TelecomHand", "Sorties audio indisponibles: ${error.message}", error)
        emptyList()
    }

    fun fetchRemoteState(target: RemoteDevice): RemoteState = runCatching {
        val encodedToken = java.net.URLEncoder.encode(target.token, "UTF-8")
        val connection = (URL("http://${target.host}:${target.port + 1}/remote-state.json?token=$encodedToken").openConnection() as HttpURLConnection).apply {
            connectTimeout = 1800; readTimeout = 4500; useCaches = false
        }
        val body = try { connection.inputStream.bufferedReader().use { it.readText() } }
        finally { connection.disconnect() }
        val root = JSONObject(body)
        fun tiles(name: String): List<RemoteTile> {
            val array = root.optJSONArray(name) ?: return emptyList()
            return List(array.length()) { index -> array.getJSONObject(index).let { item ->
                val id = item.optString("id")
                val image = if (item.optBoolean("hasImage")) {
                    "http://${target.host}:${target.port + 1}/game-image?token=$encodedToken&id=${Uri.encode(id)}"
                } else ""
                RemoteTile(id, item.optString("name"), image)
            } }
                .filter { it.id.isNotBlank() && it.name.isNotBlank() }
        }
        val media = root.optJSONObject("media") ?: JSONObject()
        RemoteState(
            raspyTv = root.optBoolean("raspyTv"), apps = tiles("apps"), games = tiles("games"),
            media = MediaInfo(media.optBoolean("available"), media.optString("title"), media.optString("artist"), media.optBoolean("playing"), media.optInt("position"), media.optInt("duration"))
        )
    }.getOrElse { error ->
        Log.d("TelecomHand", "État distant indisponible: ${error.message}")
        RemoteState()
    }
}
