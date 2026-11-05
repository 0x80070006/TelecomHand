package com.example.telecomhand

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.telecomhand.ui.theme.ActionBlue
import com.example.telecomhand.ui.theme.ActionGreen
import com.example.telecomhand.ui.theme.ActionPink
import com.example.telecomhand.ui.theme.ActionRed
import com.example.telecomhand.ui.theme.Hairline
import com.example.telecomhand.ui.theme.TelecomHandTheme
import com.example.telecomhand.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
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
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sign

private val KeyShape = RoundedCornerShape(10.dp)
// Niveaux de gris éclaircis : un logo sombre (Netflix, Twitch) reste lisible sur fond noir.
private val GrayscaleFilter = ColorFilter.colorMatrix(ColorMatrix().apply {
    setToSaturation(0f)
    this[0, 4] = 95f
    this[1, 4] = 95f
    this[2, 4] = 95f
})

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
private enum class Screen { HOME, KEYBOARD, MORE, SHORTCUTS, PREVIEW, SETTINGS }
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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        setContent { TelecomHandTheme { TelecomHandApp() } }
    }
}

@Composable
private fun TelecomHandApp() {
    val context = LocalContext.current
    val store = remember { DeviceStore(context) }
    var devices by remember { mutableStateOf(store.load()) }
    var selectedId by remember { mutableStateOf(store.selectedId()) }
    var screen by remember { mutableStateOf(Screen.HOME) }
    val selected = devices.firstOrNull { it.id == selectedId } ?: devices.firstOrNull()
    val connection = remember { RemoteConnection() }
    var connectionState by remember { mutableStateOf(ConnectionState.OFFLINE) }
    var remoteState by remember { mutableStateOf(RemoteState()) }
    val connected = connectionState == ConnectionState.CONNECTED

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
    LaunchedEffect(selected, connectionState, screen) {
        if (selected == null || connectionState != ConnectionState.CONNECTED) {
            remoteState = RemoteState()
            return@LaunchedEffect
        }
        while (isActive) {
            remoteState = withContext(Dispatchers.IO) { connection.fetchRemoteState(selected) }
            delay(if (screen == Screen.MORE) 2500 else 8000)
        }
    }

    fun back() {
        screen = if (screen == Screen.SHORTCUTS || screen == Screen.PREVIEW) Screen.MORE else Screen.HOME
    }
    BackHandler(enabled = screen != Screen.HOME) { back() }

    Surface(Modifier.fillMaxSize(), color = Color.Black) {
        AnimatedContent(
            targetState = screen,
            transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(110)) },
            label = "screen",
            modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()
        ) { current ->
            when (current) {
                Screen.HOME -> HomeScreen(
                    selected, devices, connectionState, connection,
                    onSelect = { selectedId = it.id; store.select(it.id) },
                    onNavigate = { screen = it }
                )
                Screen.KEYBOARD -> SubScreen("Clavier", ::back) { KeyboardScreen(connected, connection) }
                Screen.MORE -> SubScreen("Plus", ::back) {
                    MoreScreen(connection, connected, remoteState) { screen = it }
                }
                Screen.SHORTCUTS -> SubScreen("Raccourcis", ::back) { ShortcutsScreen(selected, connection, connected, remoteState) }
                Screen.PREVIEW -> SubScreen("Écran", ::back) { ScreenPreview(selected, connection) }
                Screen.SETTINGS -> SubScreen("Appareils", ::back) {
                    DevicesScreen(devices) { updated ->
                        devices = updated
                        if (selectedId !in updated.map { it.id }) selectedId = updated.firstOrNull()?.id
                        store.save(updated, selectedId)
                    }
                }
            }
        }
    }
}

// Accueil

@Composable
private fun HomeScreen(
    selected: RemoteDevice?, devices: List<RemoteDevice>, state: ConnectionState, connection: RemoteConnection,
    onSelect: (RemoteDevice) -> Unit, onNavigate: (Screen) -> Unit
) {
    val connected = state == ConnectionState.CONNECTED
    Column(Modifier.fillMaxSize()) {
        HomeHeader(selected, devices, state, onSelect) { onNavigate(Screen.SETTINGS) }
        Row(Modifier.weight(1f).fillMaxWidth()) {
            TouchSurface(connection, selected == null, Modifier.weight(1f).fillMaxHeight())
            ScrollRail(
                enabled = connected,
                os = selected?.os ?: DeviceOs.WINDOWS,
                onScroll = { connection.send("scroll", "dy" to it) },
                modifier = Modifier.width(44.dp).fillMaxHeight()
            )
        }
        Row(
            Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 14.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            ActionButton("Gauche", ActionBlue, connected, onClick = { connection.send("click", "button" to "left") }) { MouseGlyph(left = true, ActionBlue) }
            ActionButton("Droit", ActionRed, connected, onClick = { connection.send("click", "button" to "right") }) { MouseGlyph(left = false, ActionRed) }
            ActionButton("Clavier", ActionGreen, true, onClick = { onNavigate(Screen.KEYBOARD) }) { ActionIcon(Icons.Outlined.Keyboard, ActionGreen) }
            ActionButton("Plus", ActionPink, true, onClick = { onNavigate(Screen.MORE) }) { ActionIcon(Icons.Outlined.MoreHoriz, ActionPink) }
        }
    }
}

@Composable
private fun HomeHeader(
    selected: RemoteDevice?, devices: List<RemoteDevice>, state: ConnectionState,
    onSelect: (RemoteDevice) -> Unit, onSettings: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().height(56.dp).padding(start = 8.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) {
            Row(
                Modifier.clip(KeyShape).clickable { if (devices.isEmpty()) onSettings() else expanded = true }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatusDot(state)
                Spacer(Modifier.width(10.dp))
                Text(selected?.name ?: "Aucun appareil", color = Color.White, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 220.dp))
                if (state != ConnectionState.CONNECTED && selected != null) {
                    Text(
                        if (state == ConnectionState.CONNECTING) "  connexion" else "  hors ligne",
                        color = TextSecondary, fontSize = 13.sp, maxLines = 1
                    )
                }
            }
            DropdownMenu(expanded, onDismissRequest = { expanded = false }, containerColor = Color(0xFF141414), shape = KeyShape) {
                devices.forEach { device ->
                    DropdownMenuItem(
                        text = { Text(device.name, color = Color.White) },
                        trailingIcon = { if (device.id == selected?.id) Icon(Icons.Outlined.Check, null, tint = Color.White, modifier = Modifier.size(18.dp)) },
                        onClick = { expanded = false; onSelect(device) }
                    )
                }
            }
        }
        IconButton(onClick = onSettings) {
            Icon(Icons.Outlined.Settings, "Appareils", tint = Color.White, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
private fun StatusDot(state: ConnectionState) {
    val shape = CircleShape
    val modifier = Modifier.size(7.dp)
    when (state) {
        ConnectionState.CONNECTED -> Box(modifier.background(Color.White, shape))
        ConnectionState.CONNECTING -> Box(modifier.background(TextSecondary, shape))
        ConnectionState.OFFLINE -> Box(modifier.border(1.dp, TextSecondary, shape))
    }
}

@Composable
private fun ActionButton(label: String, color: Color, enabled: Boolean, onClick: () -> Unit, glyph: @Composable () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val alpha = if (enabled) 1f else .3f
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(58.dp).graphicsLayer { this.alpha = alpha }.clip(CircleShape)
                .background(if (pressed) color.copy(alpha = .22f) else Color.Black)
                .border(1.5.dp, color, CircleShape)
                .clickable(source, null, enabled = enabled, role = androidx.compose.ui.semantics.Role.Button, onClickLabel = label, onClick = onClick),
            contentAlignment = Alignment.Center
        ) { glyph() }
        Spacer(Modifier.height(7.dp))
        Text(label, color = TextSecondary, fontSize = 11.sp, maxLines = 1)
    }
}

@Composable
private fun ActionIcon(icon: ImageVector, color: Color) {
    Icon(icon, null, tint = color, modifier = Modifier.size(24.dp))
}

/** Souris dont seul le bouton concerné est plein : gauche et droit se distinguent sans texte. */
@Composable
private fun MouseGlyph(left: Boolean, color: Color) {
    Canvas(Modifier.size(width = 16.dp, height = 24.dp)) {
        val stroke = 1.5.dp.toPx()
        val radius = CornerRadius(size.width / 2f, size.width / 2f)
        val half = size.width / 2f
        val split = size.height * .42f
        clipRect(left = if (left) 0f else half, top = 0f, right = if (left) half else size.width, bottom = split) {
            drawRoundRect(color, cornerRadius = radius)
        }
        drawRoundRect(
            color, topLeft = Offset(stroke / 2f, stroke / 2f),
            size = Size(size.width - stroke, size.height - stroke),
            cornerRadius = radius, style = Stroke(stroke)
        )
        drawLine(color, Offset(0f, split), Offset(size.width, split), stroke)
        drawLine(color, Offset(half, 0f), Offset(half, split), stroke)
    }
}

// Traînée du doigt

private const val TrailLifetimeMs = 900f

private class TrailPoint(val position: Offset, val time: Long, val stroke: Int)

private class FingerTrail {
    val points = ArrayList<TrailPoint>()
    var stroke = 0
        private set
    var head by mutableStateOf<Offset?>(null)
        private set
    var held by mutableStateOf(false)
    var frame by mutableLongStateOf(0L)
    var animating by mutableStateOf(false)
        private set

    fun begin(position: Offset) {
        stroke++
        head = position
        push(position)
        animating = true
    }

    fun move(position: Offset) {
        head = position
        val last = points.lastOrNull()
        if (last == null || last.stroke != stroke || (last.position - position).getDistance() >= 1.5f) push(position)
    }

    fun end() {
        head = null
        held = false
    }

    private fun push(position: Offset) {
        points += TrailPoint(position, SystemClock.uptimeMillis(), stroke)
    }

    /** Retire les points éteints et indique s'il reste quelque chose à animer. */
    fun step(now: Long): Boolean {
        points.removeAll { now - it.time > TrailLifetimeMs }
        val alive = points.isNotEmpty() || head != null
        if (!alive) animating = false
        return alive
    }
}

private fun catmullRom(p0: Offset, p1: Offset, p2: Offset, p3: Offset, t: Float): Offset {
    val t2 = t * t
    val t3 = t2 * t
    return Offset(
        .5f * (2 * p1.x + (p2.x - p0.x) * t + (2 * p0.x - 5 * p1.x + 4 * p2.x - p3.x) * t2 + (3 * p1.x - p0.x - 3 * p2.x + p3.x) * t3),
        .5f * (2 * p1.y + (p2.y - p0.y) * t + (2 * p0.y - 5 * p1.y + 4 * p2.y - p3.y) * t2 + (3 * p1.y - p0.y - 3 * p2.y + p3.y) * t3)
    )
}

/**
 * Ruban lissé qui s'affine et s'éteint avec l'âge. Tracé en niveaux de gris opaques avec le mode
 * Lighten : les segments se recouvrent sans former de perles aux jointures.
 */
private fun DrawScope.drawTrail(trail: FingerTrail, now: Long) {
    val maxWidth = 9.dp.toPx()
    val step = 3.dp.toPx()
    val head = trail.head
    val points = if (head != null) trail.points + TrailPoint(head, now, trail.stroke) else trail.points

    fun shade(time: Float): Pair<Color, Float>? {
        val life = 1f - (now - time) / TrailLifetimeMs
        if (life <= 0f) return null
        val level = life.pow(1.8f)
        return Color(level, level, level) to maxWidth * (.12f + .88f * life.pow(1.3f))
    }

    points.forEachIndexed { index, point ->
        val next = points.getOrNull(index + 1)?.takeIf { it.stroke == point.stroke }
        if (next == null) {
            val previousSame = points.getOrNull(index - 1)?.stroke == point.stroke
            if (!previousSame) shade(point.time.toFloat())?.let { (color, width) ->
                drawCircle(color, width / 2f, point.position, blendMode = BlendMode.Lighten)
            }
            return@forEachIndexed
        }
        val before = points.getOrNull(index - 1)?.takeIf { it.stroke == point.stroke } ?: point
        val after = points.getOrNull(index + 2)?.takeIf { it.stroke == point.stroke } ?: next
        val divisions = ((next.position - point.position).getDistance() / step).toInt().coerceIn(1, 14)
        var from = point.position
        for (division in 1..divisions) {
            val t = division.toFloat() / divisions
            val to = catmullRom(before.position, point.position, next.position, after.position, t)
            shade(point.time + (next.time - point.time) * t)?.let { (color, width) ->
                drawLine(color, from, to, width, StrokeCap.Round, blendMode = BlendMode.Lighten)
            }
            from = to
        }
    }

    if (head != null) {
        drawCircle(Color.White, maxWidth * .55f, head)
        if (trail.held) drawCircle(Color.White, 20.dp.toPx(), head, style = Stroke(1.dp.toPx()))
    }
}

@Composable
private fun TouchSurface(connection: RemoteConnection, noDevice: Boolean, modifier: Modifier = Modifier) {
    val trail = remember { FingerTrail() }
    LaunchedEffect(trail.animating) {
        while (isActive && trail.animating) {
            withFrameMillis { }
            val now = SystemClock.uptimeMillis()
            trail.step(now)
            trail.frame = now
        }
    }
    Box(
        modifier.pointerInput(connection) {
            awaitEachGesture {
                val first = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Main)
                trail.begin(first.position)
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
                            withTimeoutOrNull(remaining) { awaitPointerEvent(PointerEventPass.Main) }
                        } else {
                            awaitPointerEvent(PointerEventPass.Main)
                        }
                        if (event == null) {
                            dragging = true
                            trail.held = true
                            connection.send("button_down", "button" to "left")
                            continue
                        }
                        val pressed = event.changes.filter { it.pressed }
                        if (pressed.isEmpty()) {
                            if (dragging) {
                                connection.send("button_up", "button" to "left")
                                dragging = false
                            } else if (!moved && !usedTwoFingers) connection.send("click", "button" to "left")
                            break
                        }
                        if (pressed.size >= 2) {
                            if (dragging) {
                                connection.send("button_up", "button" to "left")
                                dragging = false
                                trail.held = false
                            }
                            usedTwoFingers = true
                            moved = true
                            val averageY = pressed.take(2).map { it.position.y }.average().toFloat()
                            val averageX = pressed.take(2).map { it.position.x }.average().toFloat()
                            val previousCenter = lastScrollCenter
                            if (previousCenter == null) trail.begin(Offset(averageX, averageY)) else trail.move(Offset(averageX, averageY))
                            val dy = if (previousCenter == null) 0f else averageY - previousCenter
                            scrollRemainder += -dy * .24f
                            val scrollSteps = scrollRemainder.toInt()
                            if (scrollSteps != 0) {
                                connection.send("scroll", "dy" to scrollSteps)
                                scrollRemainder -= scrollSteps
                            }
                            lastScrollCenter = averageY
                            last = pressed[0].position
                        } else {
                            val current = pressed[0].position
                            val delta = if (lastScrollCenter != null) Offset.Zero else current - last
                            if (lastScrollCenter != null) trail.begin(current) else trail.move(current)
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
                    trail.end()
                }
            }
        },
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize().graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
            trail.frame // relit le dessin à chaque image de l'animation
            drawRect(Color.Black)
            drawTrail(trail, SystemClock.uptimeMillis())
        }
        if (noDevice) Text("Ajoutez un appareil", color = TextSecondary, fontSize = 13.sp)
    }
}

// Rail de défilement

private const val ScrollMinNotchesPerSecond = 1.2f
private const val ScrollMaxNotchesPerSecond = 30f
private const val ScrollDeadZone = .04f

/** Crans par seconde : très lent près du centre, rapide seulement en bout de course. */
private fun scrollRate(deflection: Float): Float {
    val amount = abs(deflection)
    if (amount < ScrollDeadZone) return 0f
    val normalized = (amount - ScrollDeadZone) / (1f - ScrollDeadZone)
    return ScrollMinNotchesPerSecond + (ScrollMaxNotchesPerSecond - ScrollMinNotchesPerSecond) * normalized.pow(2.6f)
}

@Composable
private fun ScrollRail(enabled: Boolean, os: DeviceOs, onScroll: (Int) -> Unit, modifier: Modifier = Modifier) {
    val thumb = remember { Animatable(0f) }
    var held by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val latestOnScroll by rememberUpdatedState(onScroll)
    // Windows compte 120 unités par cran et accepte les fractions ; l'agent Linux compte en crans entiers.
    val unitsPerNotch = if (os == DeviceOs.WINDOWS) 120 else 1
    val quantum = if (os == DeviceOs.WINDOWS) 30 else 1

    LaunchedEffect(held, enabled, os) {
        if (!held || !enabled) return@LaunchedEffect
        var previous = withFrameNanos { it }
        var remainder = 0f
        while (isActive) {
            val now = withFrameNanos { it }
            val seconds = (now - previous) / 1_000_000_000f
            previous = now
            val deflection = thumb.value
            // Tirer vers le haut remonte la page : valeur positive côté agent.
            remainder += -sign(deflection) * scrollRate(deflection) * unitsPerNotch * seconds
            val quanta = (remainder / quantum).toInt()
            if (quanta != 0) {
                latestOnScroll(quanta * quantum)
                remainder -= quanta * quantum
            }
        }
    }

    Canvas(
        modifier.pointerInput(enabled) {
            if (!enabled) return@pointerInput
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                down.consume()
                val travel = size.height * RailTravelRatio
                held = true
                try {
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        val deflection = ((change.position.y - down.position.y) / travel).coerceIn(-1f, 1f)
                        scope.launch { thumb.snapTo(deflection) }
                        change.consume()
                    }
                } finally {
                    held = false
                    scope.launch { thumb.animateTo(0f, spring(dampingRatio = .72f, stiffness = 500f)) }
                }
            }
        }
    ) {
        val x = size.width / 2f
        val middle = size.height / 2f
        val travel = size.height * RailTravelRatio
        val thumbY = middle + thumb.value * travel
        val thumbHalf = 22.dp.toPx()
        drawLine(Hairline, Offset(x, middle - travel - thumbHalf), Offset(x, middle + travel + thumbHalf), 1.dp.toPx(), StrokeCap.Round)
        if (abs(thumbY - middle) > 1f) drawLine(Color.White.copy(alpha = .55f), Offset(x, middle), Offset(x, thumbY), 1.dp.toPx())
        val width = (if (held) 6.dp else 4.dp).toPx()
        drawRoundRect(
            if (enabled) Color.White else Color(0xFF4A4A4A),
            topLeft = Offset(x - width / 2f, thumbY - thumbHalf),
            size = Size(width, thumbHalf * 2f),
            cornerRadius = CornerRadius(width / 2f, width / 2f)
        )
    }
}

private const val RailTravelRatio = .42f

// Écrans secondaires

@Composable
private fun SubScreen(title: String, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().height(56.dp).padding(start = 6.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Retour", tint = Color.White, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(4.dp))
            Text(title, color = Color.White, fontSize = 17.sp)
        }
        Column(Modifier.fillMaxWidth().weight(1f).padding(horizontal = 16.dp), content = content)
    }
}

@Composable
private fun SectionLabel(label: String) {
    Text(label, color = TextSecondary, fontSize = 12.sp, modifier = Modifier.padding(top = 22.dp, bottom = 9.dp))
}

/** Touche sobre : contour fin, s'inverse en blanc à l'appui ou lorsqu'elle est active. */
@Composable
private fun Key(label: String, enabled: Boolean, modifier: Modifier = Modifier, active: Boolean = false, icon: ImageVector? = null, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val inverted = active || pressed
    val content = when {
        inverted -> Color.Black
        enabled -> Color.White
        else -> Color(0xFF555555)
    }
    Box(
        modifier.height(46.dp).clip(KeyShape)
            .background(if (inverted) Color.White else Color.Black)
            .border(1.dp, if (inverted) Color.White else Hairline, KeyShape)
            .clickable(source, null, enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (icon != null) Icon(icon, label, tint = content, modifier = Modifier.size(20.dp))
        else Text(label, color = content, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 4.dp))
    }
}

@Composable
private fun NavigationRow(label: String, icon: ImageVector, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(52.dp).clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(14.dp))
        Text(label, color = Color.White, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = TextSecondary, modifier = Modifier.size(20.dp))
    }
    HorizontalDivider(color = Hairline, thickness = 1.dp)
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
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val image = frame
        if (image != null) Image(image, "Écran distant", Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        else Text(error ?: "Chargement de l’écran…", color = TextSecondary, fontSize = 13.sp)
    }
}

@Composable
private fun LinuxAudioOutputPicker(device: RemoteDevice, connection: RemoteConnection, enabled: Boolean) {
    var outputs by remember(device.id) { mutableStateOf<List<AudioOutput>>(emptyList()) }
    var expanded by remember { mutableStateOf(false) }
    var loading by remember(device.id) { mutableStateOf(true) }

    LaunchedEffect(device.id, enabled) {
        if (!enabled) return@LaunchedEffect
        loading = true
        outputs = withContext(Dispatchers.IO) { connection.fetchAudioOutputs(device) }
        loading = false
    }
    val active = outputs.firstOrNull { it.active }

    Box {
        Row(
            Modifier.fillMaxWidth().height(58.dp).clickable(enabled = enabled && !loading) { expanded = true },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Outlined.Speaker, null, tint = Color.White, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("Sortie audio", color = Color.White, fontSize = 14.sp)
                Text(if (loading) "Recherche…" else active?.name ?: "Choisir un périphérique", color = TextSecondary, fontSize = 12.sp, maxLines = 1)
            }
            Icon(Icons.Outlined.ExpandMore, null, tint = TextSecondary, modifier = Modifier.size(20.dp))
        }
        DropdownMenu(expanded, onDismissRequest = { expanded = false }, containerColor = Color(0xFF141414), shape = KeyShape) {
            outputs.forEach { output ->
                DropdownMenuItem(
                    text = { Text(output.name, color = Color.White) },
                    trailingIcon = { if (output.active) Icon(Icons.Outlined.Check, null, tint = Color.White, modifier = Modifier.size(18.dp)) },
                    onClick = {
                        expanded = false
                        outputs = outputs.map { it.copy(active = it.id == output.id) }
                        connection.send("audio_output", "id" to output.id)
                    }
                )
            }
            if (outputs.isEmpty() && !loading) DropdownMenuItem(text = { Text("Aucune sortie détectée", color = TextSecondary) }, onClick = {})
        }
    }
    HorizontalDivider(color = Hairline, thickness = 1.dp)
}

@Composable
private fun ColumnScope.KeyboardScreen(enabled: Boolean, connection: RemoteConnection) {
    var text by remember { mutableStateOf("") }
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
        placeholder = { Text("Écrire…", color = TextSecondary) },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
        keyboardActions = KeyboardActions(onSend = { connection.send("key", "value" to "enter") }),
        trailingIcon = {
            IconButton(onClick = { connection.send("key", "value" to "backspace") }, enabled = enabled) {
                Icon(Icons.AutoMirrored.Outlined.Backspace, "Effacer", tint = Color.White, modifier = Modifier.size(20.dp))
            }
        },
        shape = KeyShape,
        colors = monochromeFieldColors()
    )
    Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
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

@Composable
private fun monochromeFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Color.White,
    unfocusedBorderColor = Hairline,
    disabledBorderColor = Hairline,
    cursorColor = Color.White,
    focusedLabelColor = Color.White,
    unfocusedLabelColor = TextSecondary,
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White
)

@Composable
private fun KeyRows(enabled: Boolean, connection: RemoteConnection, rows: List<List<Pair<String, String>>>) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { (key, label) ->
                    Key(label, enabled, Modifier.weight(1f)) { connection.send("key", "value" to key) }
                }
                repeat((4 - row.size).coerceAtLeast(0)) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun WindowsShortcuts(connection: RemoteConnection, enabled: Boolean, deviceId: String) {
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
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Key("Démarrer", enabled, Modifier.weight(1f)) { hotkey("win") }
            Key("Ctrl", enabled, Modifier.weight(1f), active = ctrlHeld) {
                val next = !ctrlHeld; ctrlHeld = next; connection.send(if (next) "key_down" else "key_up", "value" to "ctrl")
            }
            Key("Alt", enabled, Modifier.weight(1f), active = altHeld) {
                val next = !altHeld; altHeld = next; connection.send(if (next) "key_down" else "key_up", "value" to "alt")
            }
            Key("Verrouiller", enabled && connection.supportsScreenLock, Modifier.weight(1f)) { releaseModifiers(); connection.send("lock_screen") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Key("Changer", enabled, Modifier.weight(1f)) { hotkey("alt", "tab") }
            Key("Bureau", enabled, Modifier.weight(1f)) { hotkey("win", "d") }
            Key("Fichiers", enabled, Modifier.weight(1f)) { hotkey("win", "e") }
            Key("Capture", enabled, Modifier.weight(1f)) { hotkey("win", "shift", "s") }
        }
        if (enabled && !connection.supportsScreenLock) Text(
            "Mettez à jour l’agent Windows pour activer le verrouillage.",
            color = TextSecondary, fontSize = 12.sp
        )
    }
}

@Composable
private fun ColumnScope.ShortcutsScreen(device: RemoteDevice?, connection: RemoteConnection, enabled: Boolean, remote: RemoteState) {
    Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
        when (device?.os) {
            DeviceOs.WINDOWS -> {
                SectionLabel("Windows")
                WindowsShortcuts(connection, enabled, device.id)
                SectionLabel("Édition")
                ShortcutRows(connection, enabled, listOf(
                    listOf(listOf("ctrl", "c") to "Copier", listOf("ctrl", "v") to "Coller", listOf("ctrl", "x") to "Couper"),
                    listOf(listOf("ctrl", "z") to "Annuler", listOf("ctrl", "a") to "Tout", listOf("ctrl", "shift", "escape") to "Tâches")
                ))
            }
            DeviceOs.LINUX -> LinuxAudioOutputPicker(device, connection, enabled)
            null -> Text("Ajoutez un appareil.", color = TextSecondary, fontSize = 13.sp)
        }
    }
}

@Composable
private fun ShortcutRows(connection: RemoteConnection, enabled: Boolean, rows: List<List<Pair<List<String>, String>>>) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { (keys, label) ->
                    Key(label, enabled, Modifier.weight(1f)) { connection.send("hotkey", "keys" to JSONArray(keys)) }
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.MoreScreen(connection: RemoteConnection, enabled: Boolean, remote: RemoteState, onNavigate: (Screen) -> Unit) {
    var showGames by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
        SectionLabel("Volume")
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Key("Baisser", enabled, Modifier.weight(1f), icon = Icons.Outlined.VolumeDown) { connection.send("volume", "action" to "down") }
            Key("Muet", enabled, Modifier.weight(1f), icon = Icons.Outlined.VolumeOff) { connection.send("volume", "action" to "mute") }
            Key("Monter", enabled, Modifier.weight(1f), icon = Icons.Outlined.VolumeUp) { connection.send("volume", "action" to "up") }
        }
        SectionLabel("Lecture")
        MediaControls(remote.media, enabled) { connection.send("media_control", "action" to it) }
        Spacer(Modifier.height(12.dp))
        HorizontalDivider(color = Hairline, thickness = 1.dp)
        NavigationRow("Écran", Icons.Outlined.Monitor) { onNavigate(Screen.PREVIEW) }
        NavigationRow("Raccourcis", Icons.Outlined.GridView) { onNavigate(Screen.SHORTCUTS) }
        val hasGames = remote.raspyTv && remote.games.isNotEmpty()
        val tiles = if (showGames && hasGames) remote.games else remote.apps
        if (hasGames) {
            Spacer(Modifier.height(22.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Key("Applications", true, Modifier.weight(1f), active = !showGames) { showGames = false }
                Key("Jeux", true, Modifier.weight(1f), active = showGames) { showGames = true }
            }
            Spacer(Modifier.height(10.dp))
        } else if (tiles.isNotEmpty()) SectionLabel("Applications")
        TileGrid(tiles, enabled) { tile -> connection.send(if (showGames && hasGames) "launch_game" else "launch_app", "id" to tile.id) }
    }
}

@Composable
private fun MediaControls(media: MediaInfo, enabled: Boolean, onControl: (String) -> Unit) {
    Text(if (media.available) media.title else "Aucune lecture", color = Color.White, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    if (media.available && media.artist.isNotBlank()) Text(media.artist, color = TextSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    if (media.duration > 0) LinearProgressIndicator(
        progress = { (media.position.toFloat() / media.duration).coerceIn(0f, 1f) },
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp).height(1.dp), color = Color.White, trackColor = Hairline, drawStopIndicator = {}, gapSize = 0.dp
    )
    Spacer(Modifier.height(10.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Key("Précédent", enabled, Modifier.weight(1f), icon = Icons.Outlined.SkipPrevious) { onControl("previous") }
        Key(if (media.playing) "Pause" else "Lecture", enabled, Modifier.weight(1f), icon = if (media.playing) Icons.Outlined.Pause else Icons.Outlined.PlayArrow) { onControl("play_pause") }
        Key("Suivant", enabled, Modifier.weight(1f), icon = Icons.Outlined.SkipNext) { onControl("next") }
    }
}

@Composable
private fun TileGrid(tiles: List<RemoteTile>, enabled: Boolean, onClick: (RemoteTile) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        tiles.chunked(4).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { tile ->
                    Column(
                        Modifier.weight(1f).clip(KeyShape).clickable(enabled = enabled) { onClick(tile) }.padding(vertical = 12.dp, horizontal = 2.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (tile.image.isBlank()) {
                            Image(painterResource(tileAsset(tile.id, tile.name)), tile.name, Modifier.size(30.dp), contentScale = ContentScale.Fit, colorFilter = GrayscaleFilter)
                        } else {
                            AsyncImage(model = tile.image, contentDescription = tile.name, modifier = Modifier.size(44.dp), contentScale = ContentScale.Fit)
                        }
                        Spacer(Modifier.height(7.dp))
                        Text(tile.name, color = if (enabled) Color.White else TextSecondary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

// Appareils

@Composable
private fun ColumnScope.DevicesScreen(devices: List<RemoteDevice>, onChange: (List<RemoteDevice>) -> Unit) {
    var editing by remember { mutableStateOf<RemoteDevice?>(null) }
    var adding by remember { mutableStateOf(devices.isEmpty()) }
    Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
        devices.forEach { device ->
            if (editing?.id == device.id) {
                DeviceForm(device, "Enregistrer", onCancel = { editing = null }) { updated ->
                    onChange(devices.map { if (it.id == device.id) updated else it })
                    editing = null
                }
            } else {
                Row(Modifier.fillMaxWidth().clickable { editing = device; adding = false }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(device.name, color = Color.White, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${if (device.os == DeviceOs.WINDOWS) "Windows" else "Linux"} · ${device.host}:${device.port}", color = TextSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    IconButton(onClick = { onChange(devices.filterNot { it.id == device.id }) }) {
                        Icon(Icons.Outlined.Close, "Supprimer ${device.name}", tint = TextSecondary, modifier = Modifier.size(18.dp))
                    }
                }
            }
            HorizontalDivider(color = Hairline, thickness = 1.dp)
        }
        Spacer(Modifier.height(14.dp))
        if (adding) {
            DeviceForm(null, "Ajouter", onCancel = { adding = false }.takeIf { devices.isNotEmpty() }) { created ->
                onChange(devices + created)
                adding = false
            }
        } else if (editing == null) {
            Key("Ajouter un appareil", true, Modifier.fillMaxWidth()) { adding = true }
        }
    }
}

@Composable
private fun DeviceForm(initial: RemoteDevice?, submitLabel: String, onCancel: (() -> Unit)?, onSubmit: (RemoteDevice) -> Unit) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var host by remember { mutableStateOf(initial?.host ?: "") }
    var port by remember { mutableStateOf((initial?.port ?: 45870).toString()) }
    var token by remember { mutableStateOf(initial?.token ?: "") }
    var os by remember { mutableStateOf(initial?.os ?: DeviceOs.WINDOWS) }
    val colors = monochromeFieldColors()
    val valid = name.isNotBlank() && host.isNotBlank() && token.isNotBlank()
    Column(Modifier.padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(name, { name = it }, label = { Text("Nom") }, singleLine = true, shape = KeyShape, colors = colors, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(host, { host = it }, label = { Text("Adresse") }, singleLine = true, shape = KeyShape, colors = colors, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(port, { port = it.filter(Char::isDigit) }, label = { Text("Port") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, shape = KeyShape, colors = colors, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(
            token, { token = it.trim() }, label = { Text("Code d’accès") }, singleLine = true, shape = KeyShape, colors = colors,
            supportingText = { Text("Dans le config.json de l’agent", color = TextSecondary) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth()
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Key("Windows", true, Modifier.weight(1f), active = os == DeviceOs.WINDOWS) { os = DeviceOs.WINDOWS }
            Key("Linux", true, Modifier.weight(1f), active = os == DeviceOs.LINUX) { os = DeviceOs.LINUX }
        }
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (onCancel != null) Key("Annuler", true, Modifier.weight(1f), onClick = onCancel)
            Key(submitLabel, valid, Modifier.weight(1f)) {
                val base = initial ?: RemoteDevice(name = name, host = host)
                onSubmit(base.copy(name = name.trim(), host = host.trim(), port = port.toIntOrNull() ?: 45870, token = token, os = os))
            }
        }
    }
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
