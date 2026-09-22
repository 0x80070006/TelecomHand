package com.example.telecomhand

import android.content.Context
import android.graphics.BitmapFactory
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.telecomhand.ui.theme.TelecomHandTheme
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

private val Accent = Color(0xFF4CD8FF)
private val Violet = Color(0xFF7967FF)
private val Ink = Color(0xFF070B13)
private val Panel = Color(0xFF111824)
private val Muted = Color(0xFF91A1B7)

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

private data class TrailPixel(val position: Offset, val bornAt: Long, val size: Float)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
    var keyboardOpen by remember { mutableStateOf(false) }
    val selected = devices.firstOrNull { it.id == selectedId } ?: devices.firstOrNull()
    val connection = remember { RemoteConnection() }
    var connectionState by remember { mutableStateOf(ConnectionState.OFFLINE) }

    DisposableEffect(selected?.id) {
        if (selected == null) {
            connection.disconnect()
            connectionState = ConnectionState.OFFLINE
        } else {
            connectionState = ConnectionState.CONNECTING
            connection.connect(selected) { connectionState = it }
        }
        onDispose { connection.disconnect() }
    }
    LaunchedEffect(selected?.id) {
        while (isActive && selected != null) {
            delay(2000)
            connection.send("ping")
        }
    }

    Surface(Modifier.fillMaxSize(), color = Ink) {
        Box(
            Modifier.fillMaxSize().background(
                Brush.radialGradient(
                    listOf(Color(0xFF142139), Ink),
                    center = Offset(180f, 120f), radius = 850f
                )
            ).statusBarsPadding().navigationBarsPadding().padding(horizontal = 18.dp, vertical = 12.dp)
        ) {
            Column(Modifier.fillMaxSize()) {
                TopBar(
                    selected, connectionState, devices,
                    onSelect = { selectedId = it.id; store.select(it.id) },
                    onSettings = { settingsOpen = true }
                )
                Spacer(Modifier.height(18.dp))
                ModeSwitch(screenMode) { screenMode = it }
                Spacer(Modifier.height(14.dp))
                AnimatedContent(screenMode, label = "control-mode", modifier = Modifier.weight(1f)) { preview ->
                    if (preview) ScreenPreview(selected, connection) else TouchSurface(connection, connectionState)
                }
                Spacer(Modifier.height(14.dp))
                QuickControls(
                    enabled = connectionState == ConnectionState.CONNECTED,
                    keyboardOpen = keyboardOpen,
                    onLeft = { connection.send("click", "button" to "left") },
                    onKeyboard = { keyboardOpen = !keyboardOpen },
                    onRight = { connection.send("click", "button" to "right") }
                )
                Spacer(Modifier.height(10.dp))
                SystemControls(selected, connection, connectionState == ConnectionState.CONNECTED)
                AnimatedVisibility(keyboardOpen) {
                    QuickKeyboard(
                        enabled = connectionState == ConnectionState.CONNECTED,
                        onSend = { connection.send("text", "value" to it) },
                        onKey = { connection.send("key", "value" to it) }
                    )
                }
            }
        }
    }

    if (settingsOpen) {
        DeviceManager(devices, onDismiss = { settingsOpen = false }) { updated ->
            devices = updated
            if (selectedId !in updated.map { it.id }) selectedId = updated.firstOrNull()?.id
            store.save(updated, selectedId)
            settingsOpen = false
        }
    }
}

@Composable
private fun TopBar(
    selected: RemoteDevice?, state: ConnectionState, devices: List<RemoteDevice>,
    onSelect: (RemoteDevice) -> Unit, onSettings: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box {
            Surface(
                modifier = Modifier.clickable { expanded = true }, color = Panel,
                shape = RoundedCornerShape(18.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(.08f))
            ) {
                Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(9.dp).background(when (state) {
                        ConnectionState.CONNECTED -> Color(0xFF50E3A4)
                        ConnectionState.CONNECTING -> Color(0xFFFFC857)
                        ConnectionState.OFFLINE -> Color(0xFFFF667A)
                    }, CircleShape))
                    Spacer(Modifier.width(9.dp))
                    Column {
                        Text(selected?.name ?: "Aucun appareil", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text(when (state) {
                            ConnectionState.CONNECTED -> "Connecté"
                            ConnectionState.CONNECTING -> "Connexion…"
                            ConnectionState.OFFLINE -> "Hors ligne"
                        }, color = Muted, fontSize = 11.sp)
                    }
                    Spacer(Modifier.width(12.dp)); Text("⌄", color = Muted, fontSize = 18.sp)
                }
            }
            DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
                devices.forEach { device -> DropdownMenuItem(
                    text = { Text(device.name) },
                    onClick = { expanded = false; onSelect(device) },
                    trailingIcon = { if (device.id == selected?.id) Text("✓", color = Violet) }
                ) }
                if (devices.isEmpty()) DropdownMenuItem(text = { Text("Ajoutez un appareil") }, onClick = onSettings)
            }
        }
        Spacer(Modifier.weight(1f))
        Text("TelecomHand", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.weight(1f))
        Surface(Modifier.size(46.dp).clickable(onClick = onSettings), shape = CircleShape, color = Panel) {
            Box(contentAlignment = Alignment.Center) { Text("⚙", color = Color.White, fontSize = 20.sp) }
        }
    }
}

@Composable
private fun ModeSwitch(screenMode: Boolean, onChange: (Boolean) -> Unit) {
    Surface(color = Panel, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(4.dp)) {
            ModeTab("Pavé tactile", !screenMode, Modifier.weight(1f)) { onChange(false) }
            ModeTab("Aperçu écran", screenMode, Modifier.weight(1f)) { onChange(true) }
        }
    }
}

@Composable
private fun ModeTab(label: String, active: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Surface(modifier.clickable(onClick = onClick), color = if (active) Color(0xFF263650) else Color.Transparent, shape = RoundedCornerShape(13.dp)) {
        Text(label, Modifier.padding(vertical = 10.dp), color = if (active) Color.White else Muted, textAlign = TextAlign.Center, fontSize = 13.sp, fontWeight = FontWeight.Medium)
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
                        usedTwoFingers = true
                        moved = true
                        val averageY = pressed.take(2).map { it.position.y }.average().toFloat()
                        val previousCenter = lastScrollCenter
                        val dy = if (previousCenter == null) 0f else averageY - previousCenter
                        val averageX = pressed.take(2).map { it.position.x }.average().toFloat()
                        activePosition = Offset(averageX, averageY)
                        trail = (trail + TrailPixel(Offset(averageX, averageY), SystemClock.uptimeMillis(), 7f)).takeLast(56)
                        if (abs(dy) >= 1f) {
                            connection.send("scroll", "dy" to (-dy * .24f).roundToInt())
                            gestureLabel = "Défilement à deux doigts"
                        }
                        lastScrollCenter = averageY
                    } else {
                        val current = pressed[0].position
                        val delta = current - last
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
        color = Color(0xFF0D1522), shape = RoundedCornerShape(30.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(.10f))
    ) {
        Box(Modifier.fillMaxSize()) {
            Canvas(Modifier.fillMaxSize()) {
                val step = 28.dp.toPx(); var x = step
                while (x < size.width) { var y = step; while (y < size.height) { drawCircle(Color.White.copy(.035f), 1.2.dp.toPx(), Offset(x, y)); y += step }; x += step }
                val now = SystemClock.uptimeMillis()
                trail.forEach { pixel ->
                    val life = (1f - (now - pixel.bornAt) / 560f).coerceIn(0f, 1f)
                    val side = (pixel.size * life.coerceAtLeast(.35f)).dp.toPx()
                    drawRect(
                        color = Accent.copy(alpha = .72f * life),
                        topLeft = pixel.position - Offset(side / 2f, side / 2f),
                        size = androidx.compose.ui.geometry.Size(side, side)
                    )
                }
                val rawKnob = activePosition ?: center
                val safeRadius = size.minDimension * .13f
                val knob = Offset(
                    rawKnob.x.coerceIn(safeRadius, size.width - safeRadius),
                    rawKnob.y.coerceIn(safeRadius, size.height - safeRadius)
                )
                drawCircle(Accent.copy(if (activePosition == null) .10f else .18f), size.minDimension * .13f, knob)
                drawCircle(Violet.copy(.65f), size.minDimension * .052f, knob, style = Stroke(2.dp.toPx()))
                drawCircle(Accent, if (activePosition == null) 4.dp.toPx() else 7.dp.toPx(), knob)
            }
            Column(Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (state == ConnectionState.CONNECTED) gestureLabel else "Connectez un appareil", color = Color.White.copy(.82f), fontWeight = FontWeight.Medium)
                Text("Appui bref : clic • Appui long : glisser • 2 doigts : défiler", color = Muted, fontSize = 11.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 5.dp, start = 18.dp, end = 18.dp))
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
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        ControlButton("Clic gauche", "◉", enabled, Modifier.weight(1f), onLeft)
        ControlButton(if (keyboardOpen) "Fermer" else "Clavier", "⌨", true, Modifier.weight(1f), onKeyboard)
        ControlButton("Clic droit", "◎", enabled, Modifier.weight(1f), onRight)
    }
}

@Composable
private fun ControlButton(label: String, symbol: String, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Surface(modifier.height(64.dp).clickable(enabled = enabled, onClick = onClick), shape = RoundedCornerShape(18.dp), color = if (enabled) Panel else Panel.copy(.45f), border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(.08f))) {
        Row(Modifier.padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            Text(symbol, color = Accent, fontSize = 19.sp); Spacer(Modifier.width(6.dp)); Text(label, color = if (enabled) Color.White else Muted.copy(.5f), fontSize = 11.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun SystemControls(device: RemoteDevice?, connection: RemoteConnection, enabled: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CompactButton("Volume −", "−", enabled, Modifier.weight(1f)) { connection.send("volume", "action" to "down") }
            CompactButton("Muet", "◌", enabled, Modifier.weight(1f)) { connection.send("volume", "action" to "mute") }
            CompactButton("Volume +", "+", enabled, Modifier.weight(1f)) { connection.send("volume", "action" to "up") }
        }
        when (device?.os) {
            DeviceOs.WINDOWS -> WindowsShortcutPanel(connection, enabled, device.id)
            DeviceOs.LINUX -> LinuxAudioOutputPicker(device, connection, enabled)
            null -> Unit
        }
    }
}

@Composable
private fun CompactButton(
    label: String, symbol: String, enabled: Boolean, modifier: Modifier = Modifier,
    active: Boolean = false, onClick: () -> Unit
) {
    Surface(
        modifier = modifier.height(46.dp).clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = when { active -> Violet.copy(.38f); enabled -> Panel; else -> Panel.copy(.45f) },
        border = androidx.compose.foundation.BorderStroke(1.dp, if (active) Accent.copy(.55f) else Color.White.copy(.08f))
    ) {
        Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            Text(symbol, color = if (active) Color.White else Accent, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(5.dp))
            Text(label, color = if (enabled) Color.White else Muted.copy(.5f), fontSize = 10.sp, maxLines = 1)
        }
    }
}

@Composable
private fun WindowsShortcutPanel(connection: RemoteConnection, enabled: Boolean, deviceId: String) {
    var expanded by remember(deviceId) { mutableStateOf(false) }
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
    fun hotkey(vararg keys: String) = connection.send("hotkey", "keys" to JSONArray(keys.toList()))

    Surface(color = Panel, shape = RoundedCornerShape(16.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(.08f))) {
        Column {
            Row(
                Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(horizontal = 14.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("⊞", color = Accent, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(8.dp))
                Text("Raccourcis Windows", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                Text(if (expanded) "⌃" else "⌄", color = Muted)
            }
            AnimatedVisibility(expanded) {
                Column(Modifier.padding(start = 8.dp, end = 8.dp, bottom = 9.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        CompactButton("Démarrer", "⊞", enabled, Modifier.weight(1f)) { hotkey("win") }
                        CompactButton("Ctrl", "⌃", enabled, Modifier.weight(1f), ctrlHeld) {
                            val next = !ctrlHeld; ctrlHeld = next; connection.send(if (next) "key_down" else "key_up", "value" to "ctrl")
                        }
                        CompactButton("Alt", "⌥", enabled, Modifier.weight(1f), altHeld) {
                            val next = !altHeld; altHeld = next; connection.send(if (next) "key_down" else "key_up", "value" to "alt")
                        }
                        CompactButton("Verrouiller", "●", enabled, Modifier.weight(1f)) { hotkey("win", "l") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        CompactButton("Changer", "↹", enabled, Modifier.weight(1f)) { hotkey("alt", "tab") }
                        CompactButton("Bureau", "▣", enabled, Modifier.weight(1f)) { hotkey("win", "d") }
                        CompactButton("Fichiers", "▰", enabled, Modifier.weight(1f)) { hotkey("win", "e") }
                        CompactButton("Capture", "✂", enabled, Modifier.weight(1f)) { hotkey("win", "shift", "s") }
                    }
                }
            }
        }
    }
}

@Composable
private fun LinuxAudioOutputPicker(device: RemoteDevice, connection: RemoteConnection, enabled: Boolean) {
    var outputs by remember(device.id) { mutableStateOf<List<AudioOutput>>(emptyList()) }
    var expanded by remember { mutableStateOf(false) }
    var loading by remember(device.id) { mutableStateOf(true) }

    suspend fun refresh() {
        loading = true
        outputs = withContext(Dispatchers.IO) { connection.fetchAudioOutputs(device) }
        loading = false
    }
    LaunchedEffect(device.id, enabled) { if (enabled) refresh() }
    val active = outputs.firstOrNull { it.active }

    Box {
        Surface(
            Modifier.fillMaxWidth().clickable(enabled = enabled && !loading) { expanded = true },
            color = Panel, shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(.08f))
        ) {
            Row(Modifier.padding(horizontal = 14.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("◉", color = Accent, fontSize = 15.sp)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text("Sortie audio Linux", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    Text(if (loading) "Recherche…" else active?.name ?: "Choisir un périphérique", color = Muted, fontSize = 10.sp, maxLines = 1)
                }
                Text("⌄", color = Muted)
            }
        }
        DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
            outputs.forEach { output ->
                DropdownMenuItem(
                    text = { Text(output.name) },
                    leadingIcon = { Text(if (output.active) "●" else "○", color = if (output.active) Accent else Muted) },
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
                    Text("⌫", fontSize = 22.sp, color = Accent)
                }
            },
            supportingText = { Text("⌫ efface aussi le texte déjà présent sur l’appareil distant") },
            shape = RoundedCornerShape(16.dp)
        )
        Row(Modifier.padding(top = 7.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("enter" to "Entrée", "escape" to "Échap.", "backspace" to "⌫ Effacer distant", "delete" to "Suppr.").forEach { (key, label) -> AssistChip(onClick = { onKey(key) }, label = { Text(label) }, enabled = enabled) }
        }
    }
}

@Composable
private fun DeviceManager(devices: List<RemoteDevice>, onDismiss: () -> Unit, onSave: (List<RemoteDevice>) -> Unit) {
    var draft by remember { mutableStateOf(devices) }; var adding by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }; var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("45870") }; var token by remember { mutableStateOf("telecomhand") }
    var os by remember { mutableStateOf(DeviceOs.WINDOWS) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Mes appareils") }, text = {
        Column {
            LazyColumn(Modifier.heightIn(max = 210.dp)) { items(draft, key = { it.id }) { device ->
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text(device.name, fontWeight = FontWeight.SemiBold); Text("${device.host}:${device.port}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp) }
                    AssistChip(
                        onClick = { draft = draft.map { if (it.id == device.id) it.copy(os = if (it.os == DeviceOs.WINDOWS) DeviceOs.LINUX else DeviceOs.WINDOWS) else it } },
                        label = { Text(if (device.os == DeviceOs.WINDOWS) "Windows" else "Linux", fontSize = 10.sp) }
                    )
                    TextButton(onClick = { draft = draft.filterNot { it.id == device.id } }) { Text("Supprimer") }
                }
            } }
            if (adding) {
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                OutlinedTextField(name, { name = it }, label = { Text("Nom") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(7.dp)); OutlinedTextField(host, { host = it }, label = { Text("Adresse IP ou nom réseau") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(7.dp)); Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    OutlinedTextField(port, { port = it.filter(Char::isDigit) }, label = { Text("Port") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.weight(.7f))
                    OutlinedTextField(token, { token = it }, label = { Text("Code") }, singleLine = true, modifier = Modifier.weight(1.3f))
                }
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = os == DeviceOs.WINDOWS, onClick = { os = DeviceOs.WINDOWS }, label = { Text("⊞ Windows") }, modifier = Modifier.weight(1f))
                    FilterChip(selected = os == DeviceOs.LINUX, onClick = { os = DeviceOs.LINUX }, label = { Text("Linux") }, modifier = Modifier.weight(1f))
                }
                Button(onClick = { if (name.isNotBlank() && host.isNotBlank()) { draft = draft + RemoteDevice(name = name.trim(), host = host.trim(), port = port.toIntOrNull() ?: 45870, token = token, os = os); name = ""; host = ""; port = "45870"; os = DeviceOs.WINDOWS; adding = false } }, modifier = Modifier.padding(top = 10.dp).fillMaxWidth()) { Text("Ajouter cet appareil") }
            } else TextButton(onClick = { adding = true }, modifier = Modifier.fillMaxWidth()) { Text("＋ Ajouter un appareil") }
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
                } ?: if (storedName.contains("linux", true) || storedName.contains("rasp", true) || storedName.contains("pi", true)) DeviceOs.LINUX else DeviceOs.WINDOWS
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

    fun connect(target: RemoteDevice, onState: (ConnectionState) -> Unit) {
        val attempt = generation.incrementAndGet()
        closeSocket()
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
                    next.soTimeout = 0
                    if (generation.get() != attempt) {
                        next.close()
                        return@execute
                    }
                    socket = next
                    writer = nextWriter
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
        closeSocket()
    }

    @Synchronized
    private fun closeSocket() {
        runCatching { socket?.close() }
        socket = null
        writer = null
    }

    private fun publish(state: ConnectionState) {
        mainHandler.post { stateCallback?.invoke(state) }
    }

    fun send(type: String, vararg values: Pair<String, Any>) {
        val target = device ?: return
        val attempt = generation.get()
        val message = JSONObject().put("type", type).put("token", target.token)
        values.forEach { message.put(it.first, it.second) }
        sender.execute {
            val output = writer ?: return@execute
            try {
                synchronized(output) {
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
        connection.inputStream.use { BitmapFactory.decodeStream(it)?.asImageBitmap() }
    }.getOrNull()

    fun fetchAudioOutputs(target: RemoteDevice): List<AudioOutput> = runCatching {
        val url = URL("http://${target.host}:${target.port + 1}/audio-outputs.json?token=${java.net.URLEncoder.encode(target.token, "UTF-8")}")
        val connection = (url.openConnection() as HttpURLConnection).apply { connectTimeout = 1800; readTimeout = 3000; useCaches = false }
        val body = connection.inputStream.bufferedReader().use { it.readText() }
        Log.d("TelecomHand", "Réponse sorties audio: $body")
        val array = JSONObject(body).getJSONArray("outputs")
        List(array.length()) { index ->
            array.getJSONObject(index).let { AudioOutput(it.getString("id"), it.getString("name"), it.optBoolean("active")) }
        }.also { Log.i("TelecomHand", "${it.size} sortie(s) audio détectée(s)") }
    }.getOrElse { error ->
        Log.w("TelecomHand", "Sorties audio indisponibles: ${error.message}", error)
        emptyList()
    }
}
