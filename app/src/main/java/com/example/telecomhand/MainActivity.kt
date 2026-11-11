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
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.VolumeDown
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.*
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.telecomhand.ui.theme.*
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

internal val KeyShape = RoundedCornerShape(14.dp)
internal val CardShape = RoundedCornerShape(22.dp)

data class RemoteDevice(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val host: String,
    val port: Int = 45870,
    val token: String = "telecomhand",
    val os: DeviceOs = DeviceOs.WINDOWS
)

enum class DeviceOs { WINDOWS, LINUX }

internal data class AudioOutput(val id: String, val name: String, val active: Boolean)

enum class ConnectionState { OFFLINE, CONNECTING, CONNECTED }
internal enum class Screen { HOME, KEYBOARD, MORE, SHORTCUTS, PREVIEW, SETTINGS }
internal data class RemoteTile(val id: String, val name: String, val image: String = "")
internal data class MediaInfo(val available: Boolean = false, val title: String = "", val artist: String = "", val playing: Boolean = false, val position: Int = 0, val duration: Int = 0)
internal data class RemoteState(val raspyTv: Boolean = false, val apps: List<RemoteTile> = emptyList(), val games: List<RemoteTile> = emptyList(), val media: MediaInfo = MediaInfo())

@DrawableRes
internal fun tileAsset(id: String, name: String = ""): Int? = when {
    id == "jellyfin" && name.equals("Lumo", true) -> R.drawable.logo_lumo
    id == "browser" -> R.drawable.logo_browser
    id == "terminal" -> R.drawable.logo_terminal
    id == "explorer" -> R.drawable.logo_explorer
    id == "mail" -> R.drawable.logo_mail
    id == "notes" -> R.drawable.logo_notes
    id == "youtube" || id == "youtube-music" -> R.drawable.logo_youtube
    id == "spotify" -> R.drawable.logo_spotify
    id == "netflix" -> R.drawable.logo_netflix
    id == "twitch" -> R.drawable.logo_twitch
    id == "jellyfin" || id == "jellyfin-web" -> R.drawable.logo_jellyfin
    id == "immich" -> R.drawable.logo_immich
    id == "homelab" -> R.drawable.logo_dashy
    id == "lumo" -> R.drawable.logo_lumo
    id == "moonlight" -> R.drawable.logo_moonlight
    else -> null
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
    var screen by remember { mutableStateOf(if (devices.isEmpty()) Screen.SETTINGS else Screen.HOME) }
    val selected = devices.firstOrNull { it.id == selectedId } ?: devices.firstOrNull()
    val connection = remember { RemoteConnection() }
    var connectionState by remember { mutableStateOf(ConnectionState.OFFLINE) }
    var remoteState by remember { mutableStateOf(RemoteState()) }
    var tvState by remember { mutableStateOf(TvState()) }
    var tvRefresh by remember { mutableIntStateOf(0) }
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
            delay(if (screen == Screen.MORE) 2500 else 15000)
        }
    }
    // Détection de RaspyTV : seul l'agent Linux du Raspberry Pi répond à /tv-state.json.
    LaunchedEffect(selected?.id, connected, screen, tvRefresh) {
        if (selected == null || !connected || selected.os != DeviceOs.LINUX) {
            if (!connected && connectionState != ConnectionState.CONNECTING) tvState = TvState()
            if (selected?.os != DeviceOs.LINUX) tvState = TvState()
            return@LaunchedEffect
        }
        if (tvRefresh > 0) delay(500)
        while (isActive) {
            val next = withContext(Dispatchers.IO) { connection.fetchTvState(selected) }
            // Une requête perdue ne fait pas quitter le mode RaspyTV.
            if (next != null) tvState = next
            delay(when {
                !tvState.raspyTv -> 12000
                screen == Screen.HOME -> 2000
                else -> 6000
            })
        }
    }

    fun back() {
        screen = if (screen == Screen.SHORTCUTS || screen == Screen.PREVIEW) Screen.MORE else Screen.HOME
    }
    BackHandler(enabled = screen != Screen.HOME) { back() }

    Surface(Modifier.fillMaxSize(), color = Ink) {
        AnimatedContent(
            targetState = screen,
            transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
            label = "screen",
            modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()
        ) { current ->
            when (current) {
                Screen.HOME -> if (tvState.raspyTv && selected != null) {
                    RaspyTvHome(
                        device = selected, devices = devices, state = connectionState, connection = connection,
                        tv = tvState, games = remoteState.games,
                        onLocalChange = { tvState = it },
                        onRefresh = { tvRefresh++ },
                        onSelect = { selectedId = it.id; store.select(it.id) },
                        onNavigate = { screen = it }
                    )
                } else {
                    HomeScreen(
                        selected, devices, connectionState, connection,
                        onSelect = { selectedId = it.id; store.select(it.id) },
                        onNavigate = { screen = it }
                    )
                }
                Screen.KEYBOARD -> SubScreen("Clavier", "Ce que vous tapez s’écrit sur l’écran", ::back) { KeyboardScreen(connected, connection) }
                Screen.MORE -> SubScreen("Plus", selected?.name, ::back) {
                    MoreScreen(connection, connected, remoteState) { screen = it }
                }
                Screen.SHORTCUTS -> SubScreen("Raccourcis", selected?.name, ::back) { ShortcutsScreen(selected, connection, connected) }
                Screen.PREVIEW -> SubScreen("Écran", "Aperçu en direct", ::back) { ScreenPreview(selected, connection) }
                Screen.SETTINGS -> SubScreen("Appareils", "Les ordinateurs et TV que vous pilotez", ::back) {
                    DevicesScreen(devices, selectedId) { updated ->
                        devices = updated
                        if (selectedId !in updated.map { it.id }) selectedId = updated.firstOrNull()?.id
                        store.save(updated, selectedId)
                    }
                }
            }
        }
    }
}

// Accueil classique (Windows, Linux sans RaspyTV)

@Composable
private fun HomeScreen(
    selected: RemoteDevice?, devices: List<RemoteDevice>, state: ConnectionState, connection: RemoteConnection,
    onSelect: (RemoteDevice) -> Unit, onNavigate: (Screen) -> Unit
) {
    val connected = state == ConnectionState.CONNECTED
    Column(Modifier.fillMaxSize()) {
        DeviceHeader(selected, devices, state, null, onSelect, onSettings = { onNavigate(Screen.SETTINGS) })
        MousePad(connection, selected, connected, Modifier.weight(1f))
        Row(
            Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 14.dp, bottom = 14.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            ActionButton("Clic gauche", ActionBlue, connected, onClick = { connection.send("click", "button" to "left") }) { MouseGlyph(left = true, ActionBlue) }
            ActionButton("Clic droit", ActionRed, connected, onClick = { connection.send("click", "button" to "right") }) { MouseGlyph(left = false, ActionRed) }
            ActionButton("Clavier", ActionGreen, true, onClick = { onNavigate(Screen.KEYBOARD) }) { ActionIcon(Icons.Rounded.Keyboard, ActionGreen) }
            ActionButton("Plus", ActionPink, true, onClick = { onNavigate(Screen.MORE) }) { ActionIcon(Icons.Rounded.Apps, ActionPink) }
        }
    }
}

/** Pavé tactile et rail de défilement, dans une carte, avec un rappel des gestes. */
@Composable
internal fun MousePad(connection: RemoteConnection, selected: RemoteDevice?, connected: Boolean, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        Row(
            Modifier.weight(1f).fillMaxWidth().clip(CardShape).background(Surface1).border(1.dp, Hairline, CardShape)
        ) {
            TouchSurface(connection, selected == null, Modifier.weight(1f).fillMaxHeight())
            ScrollRail(
                enabled = connected,
                os = selected?.os ?: DeviceOs.WINDOWS,
                onScroll = { connection.send("scroll", "dy" to it) },
                modifier = Modifier.width(44.dp).fillMaxHeight()
            )
        }
        Text(
            "Glisser : déplacer  ·  Toucher : cliquer  ·  Maintenir : saisir  ·  Deux doigts : défiler",
            color = TextMuted, fontSize = 11.sp, maxLines = 2,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp, start = 4.dp, end = 4.dp)
        )
    }
}

@Composable
internal fun DeviceHeader(
    selected: RemoteDevice?, devices: List<RemoteDevice>, state: ConnectionState, badge: String?,
    onSelect: (RemoteDevice) -> Unit, onSettings: () -> Unit,
    trailing: @Composable RowScope.() -> Unit = {}
) {
    var expanded by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().height(64.dp).padding(start = 8.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) {
            Row(
                Modifier.clip(RoundedCornerShape(18.dp)).clickable { if (devices.isEmpty()) onSettings() else expanded = true }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                DeviceAvatar(selected, state)
                Spacer(Modifier.width(12.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            selected?.name ?: "Aucun appareil", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 170.dp)
                        )
                        if (devices.size > 1) Icon(Icons.Rounded.ExpandMore, "Changer d’appareil", tint = TextSecondary, modifier = Modifier.size(18.dp))
                    }
                    Text(
                        when {
                            selected == null -> "Touchez pour en ajouter un"
                            state == ConnectionState.CONNECTING -> "Connexion…"
                            state == ConnectionState.OFFLINE -> "Hors ligne"
                            badge != null -> badge
                            else -> "Connecté"
                        },
                        color = when {
                            state == ConnectionState.CONNECTED && badge != null -> Raspberry
                            state == ConnectionState.CONNECTED -> ActionGreen
                            else -> TextSecondary
                        },
                        fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1
                    )
                }
            }
            DropdownMenu(expanded, onDismissRequest = { expanded = false }, containerColor = Surface2, shape = KeyShape) {
                devices.forEach { device ->
                    DropdownMenuItem(
                        text = { Text(device.name, color = TextPrimary) },
                        leadingIcon = { Image(painterResource(osLogo(device.os)), null, Modifier.size(20.dp)) },
                        trailingIcon = { if (device.id == selected?.id) Icon(Icons.Rounded.Check, null, tint = Raspberry, modifier = Modifier.size(18.dp)) },
                        onClick = { expanded = false; onSelect(device) }
                    )
                }
            }
        }
        trailing()
        IconButton(onClick = onSettings) {
            Icon(Icons.Rounded.Settings, "Appareils", tint = TextSecondary, modifier = Modifier.size(22.dp))
        }
    }
}

@DrawableRes
private fun osLogo(os: DeviceOs) = if (os == DeviceOs.WINDOWS) R.drawable.logo_windows else R.drawable.logo_linux

/** Logo du système dans une pastille, avec un point d'état en bas à droite. */
@Composable
private fun DeviceAvatar(device: RemoteDevice?, state: ConnectionState) {
    val dot by animateColorAsState(
        when (state) {
            ConnectionState.CONNECTED -> ActionGreen
            ConnectionState.CONNECTING -> Amber
            ConnectionState.OFFLINE -> TextMuted
        }, label = "dot"
    )
    Box(Modifier.size(40.dp)) {
        Box(Modifier.fillMaxSize().clip(CircleShape).background(Surface2), contentAlignment = Alignment.Center) {
            if (device != null) Image(painterResource(osLogo(device.os)), null, Modifier.size(22.dp))
            else Icon(Icons.Rounded.Add, null, tint = TextSecondary, modifier = Modifier.size(20.dp))
        }
        if (device != null) Box(
            Modifier.align(Alignment.BottomEnd).size(13.dp).clip(CircleShape).background(Ink).padding(2.5.dp).clip(CircleShape).background(dot)
        )
    }
}

@Composable
internal fun ActionButton(label: String, color: Color, enabled: Boolean, onClick: () -> Unit, glyph: @Composable () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val alpha = if (enabled) 1f else .35f
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.graphicsLayer { this.alpha = alpha }) {
        Box(
            Modifier.size(60.dp).graphicsLayer { scaleX = if (pressed) .94f else 1f; scaleY = scaleX }
                .clip(RoundedCornerShape(20.dp))
                .background(color.copy(alpha = if (pressed) .30f else .15f))
                .clickable(source, null, enabled = enabled, role = Role.Button, onClickLabel = label, onClick = onClick),
            contentAlignment = Alignment.Center
        ) { glyph() }
        Spacer(Modifier.height(7.dp))
        Text(label, color = TextSecondary, fontSize = 11.sp, maxLines = 1)
    }
}

@Composable
private fun ActionIcon(icon: ImageVector, color: Color) {
    Icon(icon, null, tint = color, modifier = Modifier.size(26.dp))
}

/** Souris dont seul le bouton concerné est plein : gauche et droit se distinguent sans texte. */
@Composable
internal fun MouseGlyph(left: Boolean, color: Color) {
    Canvas(Modifier.size(width = 16.dp, height = 24.dp)) {
        val stroke = 1.6.dp.toPx()
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
 * Ruban lissé qui s'affine et s'éteint avec l'âge. Tracé du fond vers la couleur d'accent, en mode
 * Lighten : les segments se recouvrent sans former de perles aux jointures.
 */
private fun DrawScope.drawTrail(trail: FingerTrail, now: Long, background: Color, accent: Color) {
    val maxWidth = 9.dp.toPx()
    val step = 3.dp.toPx()
    val head = trail.head
    val points = if (head != null) trail.points + TrailPoint(head, now, trail.stroke) else trail.points

    fun shade(time: Float): Pair<Color, Float>? {
        val life = 1f - (now - time) / TrailLifetimeMs
        if (life <= 0f) return null
        val level = life.pow(1.8f)
        return androidx.compose.ui.graphics.lerp(background, accent, level) to maxWidth * (.12f + .88f * life.pow(1.3f))
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
        drawCircle(accent, maxWidth * .55f, head)
        if (trail.held) drawCircle(accent, 20.dp.toPx(), head, style = Stroke(1.5.dp.toPx()))
    }
}

private val TrailAccent = Color(0xFFDCE6FF)

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
            drawRect(Surface1)
            drawTrail(trail, SystemClock.uptimeMillis(), Surface1, TrailAccent)
        }
        if (noDevice) Text("Ajoutez un appareil", color = TextSecondary, fontSize = 13.sp)
        else if (trail.head == null && !trail.animating) Icon(Icons.Rounded.TouchApp, null, tint = Surface3, modifier = Modifier.size(56.dp))
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
        drawLine(Hairline, Offset(0f, 0f), Offset(0f, size.height), 1.dp.toPx())
        val x = size.width / 2f
        val middle = size.height / 2f
        val travel = size.height * RailTravelRatio
        val thumbY = middle + thumb.value * travel
        val thumbHalf = 22.dp.toPx()
        drawLine(Surface3, Offset(x, middle - travel - thumbHalf), Offset(x, middle + travel + thumbHalf), 2.dp.toPx(), StrokeCap.Round)
        if (abs(thumbY - middle) > 1f) drawLine(ActionBlue.copy(alpha = .6f), Offset(x, middle), Offset(x, thumbY), 2.dp.toPx(), StrokeCap.Round)
        val width = (if (held) 8.dp else 6.dp).toPx()
        drawRoundRect(
            if (!enabled) Surface3 else if (held) ActionBlue else TextSecondary,
            topLeft = Offset(x - width / 2f, thumbY - thumbHalf),
            size = Size(width, thumbHalf * 2f),
            cornerRadius = CornerRadius(width / 2f, width / 2f)
        )
    }
}

private const val RailTravelRatio = .42f

// Écrans secondaires

@Composable
private fun SubScreen(title: String, subtitle: String?, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 10.dp, end = 16.dp, top = 8.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(42.dp).clip(CircleShape).background(Surface2).clickable(onClickLabel = "Retour", onClick = onBack),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Retour", tint = TextPrimary, modifier = Modifier.size(21.dp)) }
            Spacer(Modifier.width(14.dp))
            Column {
                Text(title, color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                if (!subtitle.isNullOrBlank()) Text(subtitle, color = TextSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Column(Modifier.fillMaxWidth().weight(1f).padding(horizontal = 16.dp), content = content)
    }
}

@Composable
internal fun SectionLabel(label: String, modifier: Modifier = Modifier) {
    Text(label, color = TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = modifier.padding(top = 22.dp, bottom = 10.dp))
}

/** Touche pleine aux coins arrondis ; prend la couleur d'accent lorsqu'elle est active. */
@Composable
internal fun Key(
    label: String, enabled: Boolean, modifier: Modifier = Modifier, active: Boolean = false, icon: ImageVector? = null,
    accent: Color = Raspberry, onClick: () -> Unit
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val background by animateColorAsState(
        when {
            active -> accent.copy(alpha = .22f)
            pressed -> Surface3
            else -> Surface2
        }, tween(120), label = "key"
    )
    val content = when {
        active -> accent
        enabled -> TextPrimary
        else -> TextMuted
    }
    Box(
        modifier.height(48.dp).clip(KeyShape).background(background)
            .then(if (active) Modifier.border(1.dp, accent.copy(alpha = .5f), KeyShape) else Modifier)
            .clickable(source, null, enabled = enabled, role = Role.Button, onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (icon != null) Icon(icon, label, tint = content, modifier = Modifier.size(22.dp))
        else Text(label, color = content, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 6.dp))
    }
}

@Composable
private fun NavigationRow(label: String, detail: String, icon: ImageVector, color: Color, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(CardShape).background(Surface1).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(13.dp)).background(color.copy(alpha = .16f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = color, modifier = Modifier.size(21.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(label, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(detail, color = TextSecondary, fontSize = 12.sp)
        }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = TextMuted, modifier = Modifier.size(22.dp))
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
    Box(Modifier.fillMaxSize().padding(bottom = 16.dp).clip(CardShape).background(Surface1), contentAlignment = Alignment.Center) {
        val image = frame
        if (image != null) Image(image, "Écran distant", Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        else Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Rounded.Monitor, null, tint = TextMuted, modifier = Modifier.size(40.dp))
            Spacer(Modifier.height(10.dp))
            Text(error ?: "Chargement de l’écran…", color = TextSecondary, fontSize = 13.sp)
        }
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
        NavigationRow("Sortie audio", if (loading) "Recherche…" else active?.name ?: "Choisir un périphérique", Icons.Rounded.Speaker, Lagoon) {
            if (enabled && !loading) expanded = true
        }
        DropdownMenu(expanded, onDismissRequest = { expanded = false }, containerColor = Surface2, shape = KeyShape) {
            outputs.forEach { output ->
                DropdownMenuItem(
                    text = { Text(output.name, color = TextPrimary) },
                    trailingIcon = { if (output.active) Icon(Icons.Rounded.Check, null, tint = Lagoon, modifier = Modifier.size(18.dp)) },
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
}

@Composable
internal fun ColumnScope.KeyboardScreen(enabled: Boolean, connection: RemoteConnection) {
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
        placeholder = { Text("Écrire…", color = TextMuted) },
        leadingIcon = { Icon(Icons.Rounded.Keyboard, null, tint = TextSecondary) },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
        keyboardActions = KeyboardActions(onSend = { connection.send("key", "value" to "enter") }),
        trailingIcon = {
            IconButton(onClick = { connection.send("key", "value" to "backspace") }, enabled = enabled) {
                Icon(Icons.AutoMirrored.Rounded.Backspace, "Effacer", tint = TextPrimary, modifier = Modifier.size(20.dp))
            }
        },
        shape = KeyShape,
        colors = fieldColors()
    )
    Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
        SectionLabel("Navigation")
        KeyRows(enabled, connection, listOf(
            listOf("home" to "Origine", "end" to "Fin", "pageup" to "Pg préc", "pagedown" to "Pg suiv"),
            listOf("left" to "←", "up" to "↑", "down" to "↓", "right" to "→")
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
internal fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Raspberry,
    unfocusedBorderColor = Hairline,
    disabledBorderColor = Hairline,
    focusedContainerColor = Surface1,
    unfocusedContainerColor = Surface1,
    disabledContainerColor = Surface1,
    cursorColor = Raspberry,
    focusedLabelColor = Raspberry,
    unfocusedLabelColor = TextSecondary,
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary,
    focusedLeadingIconColor = Raspberry
)

@Composable
private fun KeyRows(enabled: Boolean, connection: RemoteConnection, rows: List<List<Pair<String, String>>>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Key("Démarrer", enabled, Modifier.weight(1f)) { hotkey("win") }
            Key("Ctrl", enabled, Modifier.weight(1f), active = ctrlHeld, accent = ActionBlue) {
                val next = !ctrlHeld; ctrlHeld = next; connection.send(if (next) "key_down" else "key_up", "value" to "ctrl")
            }
            Key("Alt", enabled, Modifier.weight(1f), active = altHeld, accent = ActionBlue) {
                val next = !altHeld; altHeld = next; connection.send(if (next) "key_down" else "key_up", "value" to "alt")
            }
            Key("Verrouiller", enabled && connection.supportsScreenLock, Modifier.weight(1f), icon = Icons.Rounded.Lock) { releaseModifiers(); connection.send("lock_screen") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
private fun ColumnScope.ShortcutsScreen(device: RemoteDevice?, connection: RemoteConnection, enabled: Boolean) {
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
            DeviceOs.LINUX -> {
                Spacer(Modifier.height(12.dp))
                LinuxAudioOutputPicker(device, connection, enabled)
            }
            null -> Text("Ajoutez un appareil.", color = TextSecondary, fontSize = 13.sp)
        }
    }
}

@Composable
private fun ShortcutRows(connection: RemoteConnection, enabled: Boolean, rows: List<List<Pair<List<String>, String>>>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Key("Baisser", enabled, Modifier.weight(1f), icon = Icons.AutoMirrored.Rounded.VolumeDown) { connection.send("volume", "action" to "down") }
            Key("Muet", enabled, Modifier.weight(1f), icon = Icons.AutoMirrored.Rounded.VolumeOff) { connection.send("volume", "action" to "mute") }
            Key("Monter", enabled, Modifier.weight(1f), icon = Icons.AutoMirrored.Rounded.VolumeUp) { connection.send("volume", "action" to "up") }
        }
        SectionLabel("Lecture")
        MediaControls(remote.media, enabled) { connection.send("media_control", "action" to it) }
        Spacer(Modifier.height(20.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            NavigationRow("Écran", "Voir l’écran distant en direct", Icons.Rounded.Monitor, ActionBlue) { onNavigate(Screen.PREVIEW) }
            NavigationRow("Raccourcis", "Combinaisons de touches, sortie audio", Icons.Rounded.GridView, ActionGreen) { onNavigate(Screen.SHORTCUTS) }
        }
        val hasGames = remote.raspyTv && remote.games.isNotEmpty()
        val tiles = if (showGames && hasGames) remote.games else remote.apps
        if (hasGames) {
            Spacer(Modifier.height(22.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Key("Applications", true, Modifier.weight(1f), active = !showGames) { showGames = false }
                Key("Jeux", true, Modifier.weight(1f), active = showGames, accent = Violet) { showGames = true }
            }
            Spacer(Modifier.height(10.dp))
        } else if (tiles.isNotEmpty()) SectionLabel("Applications")
        TileGrid(tiles, enabled) { tile -> connection.send(if (showGames && hasGames) "launch_game" else "launch_app", "id" to tile.id) }
    }
}

@Composable
private fun MediaControls(media: MediaInfo, enabled: Boolean, onControl: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().clip(CardShape).background(Surface1).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(ActionPink.copy(alpha = .16f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.MusicNote, null, tint = ActionPink, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(if (media.available) media.title.ifBlank { "Lecture en cours" } else "Aucune lecture", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (media.available && media.artist.isNotBlank()) Text(media.artist, color = TextSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (media.duration > 0) LinearProgressIndicator(
            progress = { (media.position.toFloat() / media.duration).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().padding(top = 14.dp).height(4.dp).clip(CircleShape), color = ActionPink, trackColor = Surface3, drawStopIndicator = {}, gapSize = 0.dp
        )
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Key("Précédent", enabled, Modifier.weight(1f), icon = Icons.Rounded.SkipPrevious) { onControl("previous") }
            Key(if (media.playing) "Pause" else "Lecture", enabled, Modifier.weight(1f), icon = if (media.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow) { onControl("play_pause") }
            Key("Suivant", enabled, Modifier.weight(1f), icon = Icons.Rounded.SkipNext) { onControl("next") }
        }
    }
}

@Composable
private fun TileGrid(tiles: List<RemoteTile>, enabled: Boolean, onClick: (RemoteTile) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        tiles.chunked(4).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { tile ->
                    Column(
                        Modifier.weight(1f).clip(KeyShape).background(Surface1).clickable(enabled = enabled) { onClick(tile) }.padding(vertical = 12.dp, horizontal = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        val asset = tileAsset(tile.id, tile.name)
                        when {
                            tile.image.isNotBlank() -> AsyncImage(model = tile.image, contentDescription = tile.name, modifier = Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)), contentScale = ContentScale.Crop)
                            asset != null -> Image(painterResource(asset), tile.name, Modifier.size(32.dp), contentScale = ContentScale.Fit)
                            else -> LetterBadge(tile.name, Violet, 32.dp)
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(tile.name, color = if (enabled) TextPrimary else TextSecondary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** Initiale sur fond coloré, pour une application sans logo connu. */
@Composable
internal fun LetterBadge(name: String, color: Color, size: androidx.compose.ui.unit.Dp) {
    Box(Modifier.size(size).clip(RoundedCornerShape(size * .3f)).background(color.copy(alpha = .9f)), contentAlignment = Alignment.Center) {
        Text(name.trim().take(1).uppercase(), color = Color.White, fontSize = (size.value * .48f).sp, fontWeight = FontWeight.Bold)
    }
}

// Appareils

@Composable
private fun ColumnScope.DevicesScreen(devices: List<RemoteDevice>, selectedId: String?, onChange: (List<RemoteDevice>) -> Unit) {
    var editing by remember { mutableStateOf<RemoteDevice?>(null) }
    var adding by remember { mutableStateOf(devices.isEmpty()) }
    var confirmDelete by remember { mutableStateOf<RemoteDevice?>(null) }
    Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(top = 8.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (devices.isEmpty()) {
            Column(Modifier.fillMaxWidth().clip(CardShape).background(Surface1).padding(18.dp)) {
                Text("Bienvenue", color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Installez l’agent TelecomHand sur votre PC ou votre Raspberry Pi : il affiche son adresse IP et son code d’accès. Saisissez-les ci-dessous.",
                    color = TextSecondary, fontSize = 13.sp, lineHeight = 19.sp
                )
            }
        }
        devices.forEach { device ->
            if (editing?.id == device.id) {
                DeviceForm(device, "Enregistrer", onCancel = { editing = null }) { updated ->
                    onChange(devices.map { if (it.id == device.id) updated else it })
                    editing = null
                }
            } else {
                Row(
                    Modifier.fillMaxWidth().clip(CardShape).background(Surface1)
                        .then(if (device.id == selectedId) Modifier.border(1.dp, Raspberry.copy(alpha = .45f), CardShape) else Modifier)
                        .clickable { editing = device; adding = false }.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(Surface2), contentAlignment = Alignment.Center) {
                        Image(painterResource(osLogo(device.os)), null, Modifier.size(24.dp))
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(device.name, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${if (device.os == DeviceOs.WINDOWS) "Windows" else "Linux · Raspberry Pi"} · ${device.host}", color = TextSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Icon(Icons.Rounded.Edit, "Modifier ${device.name}", tint = TextMuted, modifier = Modifier.size(18.dp))
                    IconButton(onClick = { confirmDelete = device }) {
                        Icon(Icons.Rounded.DeleteOutline, "Supprimer ${device.name}", tint = TextSecondary, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
        if (adding) {
            DeviceForm(null, "Ajouter", onCancel = { adding = false }.takeIf { devices.isNotEmpty() }) { created ->
                onChange(devices + created)
                adding = false
            }
        } else if (editing == null) {
            Button(
                onClick = { adding = true }, modifier = Modifier.fillMaxWidth().height(52.dp), shape = KeyShape,
                colors = ButtonDefaults.buttonColors(containerColor = Raspberry, contentColor = Color.White)
            ) {
                Icon(Icons.Rounded.Add, null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Ajouter un appareil", fontWeight = FontWeight.SemiBold)
            }
        }
    }
    confirmDelete?.let { device ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            containerColor = Surface1,
            icon = { Icon(Icons.Rounded.DeleteOutline, null, tint = Danger) },
            title = { Text("Supprimer « ${device.name} » ?", color = TextPrimary) },
            text = { Text("L’appareil et son code d’accès seront retirés de cette application.", color = TextSecondary) },
            confirmButton = {
                TextButton(onClick = { onChange(devices.filterNot { it.id == device.id }); confirmDelete = null }) { Text("Supprimer", color = Danger) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Annuler", color = TextPrimary) } }
        )
    }
}

@Composable
private fun DeviceForm(initial: RemoteDevice?, submitLabel: String, onCancel: (() -> Unit)?, onSubmit: (RemoteDevice) -> Unit) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var host by remember { mutableStateOf(initial?.host ?: "") }
    var port by remember { mutableStateOf((initial?.port ?: 45870).toString()) }
    var token by remember { mutableStateOf(initial?.token ?: "") }
    var os by remember { mutableStateOf(initial?.os ?: DeviceOs.LINUX) }
    var showToken by remember { mutableStateOf(false) }
    val colors = fieldColors()
    val valid = name.isNotBlank() && host.isNotBlank() && token.isNotBlank()
    Column(
        Modifier.fillMaxWidth().clip(CardShape).background(Surface1).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(if (initial == null) "Nouvel appareil" else "Modifier l’appareil", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OsChoice("Raspberry Pi / Linux", R.drawable.logo_linux, os == DeviceOs.LINUX, Modifier.weight(1f)) { os = DeviceOs.LINUX }
            OsChoice("Windows", R.drawable.logo_windows, os == DeviceOs.WINDOWS, Modifier.weight(1f)) { os = DeviceOs.WINDOWS }
        }
        OutlinedTextField(name, { name = it }, label = { Text("Nom") }, placeholder = { Text("Salon", color = TextMuted) }, singleLine = true, shape = KeyShape, colors = colors, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(host, { host = it.trim() }, label = { Text("Adresse IP") }, placeholder = { Text("192.168.1.20", color = TextMuted) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), singleLine = true, shape = KeyShape, colors = colors, modifier = Modifier.weight(1f))
            OutlinedTextField(port, { port = it.filter(Char::isDigit).take(5) }, label = { Text("Port") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, shape = KeyShape, colors = colors, modifier = Modifier.width(96.dp))
        }
        OutlinedTextField(
            token, { token = it.trim() }, label = { Text("Code d’accès") }, singleLine = true, shape = KeyShape, colors = colors,
            supportingText = { Text("Dans ~/.local/share/telecomhand/config.json (ou config.json de l’agent Windows)", color = TextMuted) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            visualTransformation = if (showToken) androidx.compose.ui.text.input.VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { showToken = !showToken }) {
                    Icon(if (showToken) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, if (showToken) "Masquer" else "Afficher", tint = TextSecondary)
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (onCancel != null) Key("Annuler", true, Modifier.weight(1f), onClick = onCancel)
            Button(
                onClick = {
                    val base = initial ?: RemoteDevice(name = name, host = host)
                    onSubmit(base.copy(name = name.trim(), host = host.trim(), port = port.toIntOrNull() ?: 45870, token = token, os = os))
                },
                enabled = valid, modifier = Modifier.weight(1f).height(48.dp), shape = KeyShape,
                colors = ButtonDefaults.buttonColors(containerColor = Raspberry, contentColor = Color.White, disabledContainerColor = Surface2, disabledContentColor = TextMuted)
            ) { Text(submitLabel, fontWeight = FontWeight.SemiBold) }
        }
    }
}

@Composable
private fun OsChoice(label: String, @DrawableRes logo: Int, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier.height(48.dp).clip(KeyShape).background(if (selected) Raspberry.copy(alpha = .16f) else Surface2)
            .then(if (selected) Modifier.border(1.dp, Raspberry.copy(alpha = .55f), KeyShape) else Modifier)
            .clickable(onClick = onClick).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center
    ) {
        Image(painterResource(logo), null, Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, color = if (selected) TextPrimary else TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
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

internal class RemoteConnection {
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
    /** Commandes tv, tv_media, tv_volume et system_power : agent du Raspberry Pi en protocole 5. */
    val supportsRaspyTv get() = protocolVersion >= 5

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
                    Log.i("TelecomHand", "Client distant authentifié (protocole $negotiatedVersion)")
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

    private fun httpBase(target: RemoteDevice) = "http://${target.host}:${target.port + 1}"
    private fun encodedToken(target: RemoteDevice) = java.net.URLEncoder.encode(target.token, "UTF-8")

    private fun getText(url: String, readTimeoutMs: Int): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply { connectTimeout = 1800; readTimeout = readTimeoutMs; useCaches = false }
        return try { connection.inputStream.bufferedReader().use { it.readText() } } finally { connection.disconnect() }
    }

    fun fetchFrame(target: RemoteDevice): androidx.compose.ui.graphics.ImageBitmap? = runCatching {
        val url = URL("${httpBase(target)}/screen.jpg?token=${encodedToken(target)}")
        val connection = (url.openConnection() as HttpURLConnection).apply { connectTimeout = 1400; readTimeout = 2500; useCaches = false }
        try { connection.inputStream.use { BitmapFactory.decodeStream(it)?.asImageBitmap() } }
        finally { connection.disconnect() }
    }.getOrNull()

    fun fetchAudioOutputs(target: RemoteDevice): List<AudioOutput> = runCatching {
        val array = JSONObject(getText("${httpBase(target)}/audio-outputs.json?token=${encodedToken(target)}", 3000)).getJSONArray("outputs")
        List(array.length()) { index ->
            array.getJSONObject(index).let { AudioOutput(it.getString("id"), it.getString("name"), it.optBoolean("active")) }
        }.also { Log.i("TelecomHand", "${it.size} sortie(s) audio détectée(s)") }
    }.getOrElse { error ->
        Log.w("TelecomHand", "Sorties audio indisponibles: ${error.message}", error)
        emptyList()
    }

    fun fetchRemoteState(target: RemoteDevice): RemoteState = runCatching {
        val token = encodedToken(target)
        val root = JSONObject(getText("${httpBase(target)}/remote-state.json?token=$token", 4500))
        fun tiles(name: String): List<RemoteTile> {
            val array = root.optJSONArray(name) ?: return emptyList()
            return List(array.length()) { index -> array.getJSONObject(index).let { item ->
                val id = item.optString("id")
                val image = if (item.optBoolean("hasImage")) "${httpBase(target)}/game-image?token=$token&id=${Uri.encode(id)}" else ""
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

    /** État RaspyTV, ou null si la requête échoue (le dernier état connu reste affiché). */
    fun fetchTvState(target: RemoteDevice): TvState? = runCatching {
        val token = encodedToken(target)
        val root = JSONObject(getText("${httpBase(target)}/tv-state.json?token=$token", 4500))
        parseTvState(root, httpBase(target), token)
    }.getOrElse { error ->
        Log.d("TelecomHand", "État RaspyTV indisponible: ${error.message}")
        // 404 : agent antérieur au protocole 5, sans mode RaspyTV.
        if (error is java.io.FileNotFoundException) TvState() else null
    }
}
