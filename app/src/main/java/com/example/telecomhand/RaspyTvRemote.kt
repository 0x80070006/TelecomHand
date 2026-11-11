package com.example.telecomhand

import android.net.Uri
import android.os.SystemClock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import com.example.telecomhand.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.min

// Modèle : ce que l'agent du Pi renvoie sur /tv-state.json.

internal data class TvApp(val id: String, val name: String, val color: Color?, val icon: String)
internal data class TvMedia(
    val title: String, val artist: String, val app: String, val position: Float, val duration: Float,
    val playing: Boolean, val artwork: String
)
internal data class TvOutput(val id: String, val name: String, val kind: String, val headset: Boolean, val active: Boolean)
internal data class TvBluetooth(val id: String, val name: String)
internal data class TvController(val battery: Int, val charging: Boolean)
internal data class TvState(
    val raspyTv: Boolean = false,
    val activeId: String? = null,
    val inGame: Boolean = false,
    val volume: Int = 0,
    val muted: Boolean = false,
    val apps: List<TvApp> = emptyList(),
    val media: TvMedia? = null,
    val outputs: List<TvOutput> = emptyList(),
    val bluetooth: List<TvBluetooth> = emptyList(),
    val controller: TvController? = null,
    val receivedAt: Long = SystemClock.uptimeMillis()
)

private fun parseColor(value: String): Color? =
    runCatching { Color(android.graphics.Color.parseColor(value.trim())) }.getOrNull()

internal fun parseTvState(root: JSONObject, base: String, token: String): TvState {
    if (!root.optBoolean("raspyTv")) return TvState()
    val apps = root.optJSONArray("apps")?.let { array ->
        List(array.length()) { index -> array.getJSONObject(index) }.mapNotNull { item ->
            val id = item.optString("id").takeIf(String::isNotBlank) ?: return@mapNotNull null
            TvApp(
                id, item.optString("name", id), parseColor(item.optString("color")),
                if (item.optBoolean("hasIcon")) "$base/app-icon?token=$token&id=${Uri.encode(id)}" else ""
            )
        }
    } ?: emptyList()
    val media = root.optJSONObject("media")?.let { item ->
        val title = item.optString("title")
        // La jaquette change d'adresse avec le titre : Coil ne ressert pas l'ancienne image.
        val art = if (item.optBoolean("hasArtwork")) "$base/tv-art?token=$token&k=${Uri.encode(title + item.optString("artist")).take(120)}" else ""
        TvMedia(
            title, item.optString("artist"), item.optString("app"),
            item.optDouble("position", 0.0).toFloat().coerceAtLeast(0f),
            item.optDouble("duration", 0.0).toFloat().coerceAtLeast(0f),
            item.optBoolean("playing"), art
        )
    }
    val outputs = root.optJSONArray("outputs")?.let { array ->
        List(array.length()) { index -> array.getJSONObject(index) }.map {
            TvOutput(it.optString("id"), it.optString("name"), it.optString("kind"), it.optBoolean("headset"), it.optBoolean("active"))
        }.filter { it.id.isNotBlank() }
    } ?: emptyList()
    val bluetooth = root.optJSONArray("bluetooth")?.let { array ->
        List(array.length()) { index -> array.getJSONObject(index) }.map {
            val id = it.optString("id")
            TvBluetooth(if (id.startsWith("bt:")) id else "bt:$id", it.optString("name", "Casque Bluetooth"))
        }.filter { it.id.length > 3 }
    } ?: emptyList()
    val controller = root.optJSONObject("controller")?.takeIf { it.has("battery") }?.let {
        TvController(it.optInt("battery").coerceIn(0, 100), it.optString("status").equals("Charging", true) || it.optString("status").equals("Full", true))
    }
    return TvState(
        raspyTv = true,
        activeId = root.optString("activeId").takeIf { it.isNotBlank() && it != "null" },
        inGame = root.optBoolean("inGame"),
        volume = root.optInt("volume", 0).coerceIn(0, 100),
        muted = root.optBoolean("muted"),
        apps = apps, media = media, outputs = outputs, bluetooth = bluetooth, controller = controller
    )
}

// Écran principal en mode RaspyTV.

private enum class TvTab(val label: String, val icon: ImageVector) {
    REMOTE("Télécommande", Icons.Rounded.SettingsRemote),
    MOUSE("Souris", Icons.Rounded.Mouse),
    KEYBOARD("Clavier", Icons.Rounded.Keyboard)
}

@Composable
internal fun RaspyTvHome(
    device: RemoteDevice, devices: List<RemoteDevice>, state: ConnectionState, connection: RemoteConnection,
    tv: TvState, games: List<RemoteTile>,
    onLocalChange: (TvState) -> Unit, onRefresh: () -> Unit,
    onSelect: (RemoteDevice) -> Unit, onNavigate: (Screen) -> Unit
) {
    val connected = state == ConnectionState.CONNECTED
    var tab by rememberSaveable { mutableStateOf(TvTab.REMOTE) }
    var showAudio by remember { mutableStateOf(false) }
    var showPower by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        DeviceHeader(device, devices, state, "RaspyTV", onSelect, onSettings = { onNavigate(Screen.SETTINGS) }) {
            tv.controller?.let { ControllerChip(it) }
            IconButton(onClick = { showPower = true }, enabled = connected) {
                Icon(Icons.Rounded.PowerSettingsNew, "Alimentation", tint = if (connected) TextSecondary else TextMuted, modifier = Modifier.size(22.dp))
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (tab) {
                TvTab.REMOTE -> RemoteTab(connection, connected, tv, games, onLocalChange, onRefresh, onAudio = { showAudio = true }, onNavigate = onNavigate)
                TvTab.MOUSE -> Column(Modifier.fillMaxSize()) {
                    MousePad(connection, device, connected, Modifier.weight(1f))
                    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                        ActionButton("Clic gauche", ActionBlue, connected, onClick = { connection.send("click", "button" to "left") }) { MouseGlyph(left = true, ActionBlue) }
                        ActionButton("Clic droit", ActionRed, connected, onClick = { connection.send("click", "button" to "right") }) { MouseGlyph(left = false, ActionRed) }
                        ActionButton("Retour", Raspberry, connected, onClick = { connection.send("tv", "action" to "back") }) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, null, tint = Raspberry, modifier = Modifier.size(24.dp))
                        }
                    }
                }
                TvTab.KEYBOARD -> Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 4.dp)) { KeyboardScreen(connected, connection) }
            }
        }
        TabBar(tab) { tab = it }
    }

    if (showAudio) AudioSheet(tv, connected, onDismiss = { showAudio = false }) { id ->
        connection.send("audio_output", "id" to id)
        if (!id.startsWith("bt:")) onLocalChange(tv.copy(outputs = tv.outputs.map { it.copy(active = it.id == id) }))
        onRefresh()
    }
    if (showPower) PowerDialog(onDismiss = { showPower = false }) { op ->
        showPower = false
        connection.send("system_power", "op" to op)
    }
}

@Composable
private fun TabBar(selected: TvTab, onSelect: (TvTab) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).clip(RoundedCornerShape(24.dp)).background(Surface1).padding(5.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        TvTab.entries.forEach { tab ->
            val active = tab == selected
            val background by animateColorAsState(if (active) Raspberry.copy(alpha = .16f) else Color.Transparent, label = "tab")
            Row(
                Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(19.dp)).background(background)
                    .clickable(role = Role.Tab) { onSelect(tab) },
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(tab.icon, null, tint = if (active) Raspberry else TextSecondary, modifier = Modifier.size(20.dp))
                if (active) {
                    Spacer(Modifier.width(7.dp))
                    Text(tab.label, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun ControllerChip(controller: TvController) {
    val color = when {
        controller.charging -> ActionGreen
        controller.battery <= 15 -> Danger
        controller.battery <= 30 -> Amber
        else -> TextSecondary
    }
    Row(
        Modifier.clip(CircleShape).background(Surface1).padding(horizontal = 10.dp, vertical = 6.dp)
            .semantics { contentDescription = "Manette : ${controller.battery} %" + if (controller.charging) ", en charge" else "" },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Rounded.SportsEsports, null, tint = Amber, modifier = Modifier.size(17.dp))
        Spacer(Modifier.width(5.dp))
        Text("${controller.battery} %", color = color, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        if (controller.charging) Icon(Icons.Rounded.Bolt, null, tint = ActionGreen, modifier = Modifier.size(14.dp))
    }
}

@Composable
private fun RemoteTab(
    connection: RemoteConnection, connected: Boolean, tv: TvState, games: List<RemoteTile>,
    onLocalChange: (TvState) -> Unit, onRefresh: () -> Unit, onAudio: () -> Unit, onNavigate: (Screen) -> Unit
) {
    fun command(action: String) = connection.send("tv", "action" to action)
    val accent = tv.apps.firstOrNull { it.id == tv.activeId }?.color ?: Raspberry

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 12.dp)) {
        AnimatedVisibility(tv.media != null, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            tv.media?.let { media ->
                NowPlayingCard(
                    media, tv.apps.firstOrNull { it.name == media.app }?.color ?: accent, tv.receivedAt, connected,
                    onOp = { op, value -> connection.send("tv_media", "op" to op, "value" to value); onRefresh() },
                    onLocalToggle = { onLocalChange(tv.copy(media = media.copy(playing = !media.playing))) }
                )
            }
        }

        Box(Modifier.fillMaxWidth().height(286.dp).padding(horizontal = 20.dp)) {
            DPad(connected, Modifier.align(Alignment.Center).size(232.dp)) { key -> connection.send("key", "value" to key) }
            CornerButton("Retour", Icons.AutoMirrored.Rounded.ArrowBack, connected, Modifier.align(Alignment.TopStart)) { command("back") }
            CornerButton("Accueil", Icons.Rounded.Home, connected, Modifier.align(Alignment.TopEnd)) { command("home") }
            CornerButton("Barre", Icons.Rounded.ViewSidebar, connected, Modifier.align(Alignment.BottomStart)) { command("nav-peek") }
            CornerButton("Options", Icons.Rounded.ChangeHistory, connected, Modifier.align(Alignment.BottomEnd)) { command("context") }
        }

        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            VolumeRocker(
                tv.volume, tv.muted, connected, Modifier.weight(1f),
                onStep = { delta ->
                    connection.send("tv_volume", "delta" to delta)
                    onLocalChange(tv.copy(volume = (tv.volume + delta).coerceIn(0, 100), muted = false))
                },
                onSet = { value ->
                    connection.send("tv_volume", "value" to value)
                    onLocalChange(tv.copy(volume = value, muted = false))
                },
                onMute = {
                    connection.send("volume", "action" to "mute")
                    onLocalChange(tv.copy(muted = !tv.muted))
                    onRefresh()
                }
            )
            Spacer(Modifier.width(12.dp))
            val playing = tv.media?.playing == true
            RoundAction(if (playing) "Pause" else "Lecture", if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, Raspberry, connected, filled = true) {
                command("play-pause")
                tv.media?.let { onLocalChange(tv.copy(media = it.copy(playing = !it.playing))) }
                onRefresh()
            }
        }

        val output = tv.outputs.firstOrNull { it.active }
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            QuickAction("Panneau", Icons.Rounded.SpaceDashboard, Raspberry, connected, Modifier.weight(1f)) { command("panel") }
            QuickAction("Réglages", Icons.Rounded.Tune, ActionBlue, connected, Modifier.weight(1f)) { command("settings") }
            QuickAction(output?.let(::shortOutputName) ?: "Son", outputIcon(output), Lagoon, connected, Modifier.weight(1f), onClick = onAudio)
            QuickAction("Écran", Icons.Rounded.Monitor, Amber, connected, Modifier.weight(1f)) { onNavigate(Screen.PREVIEW) }
        }

        if (tv.apps.isNotEmpty()) {
            SectionLabel("Applications", Modifier.padding(start = 20.dp))
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(tv.apps, key = { it.id }) { app ->
                    AppTile(app, app.id == tv.activeId, connected) {
                        connection.send("launch_app", "id" to app.id)
                        onLocalChange(tv.copy(activeId = app.id))
                        onRefresh()
                    }
                }
            }
        }
        if (games.isNotEmpty()) {
            Row(Modifier.padding(start = 20.dp, end = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                SectionLabel("Jeux", Modifier.weight(1f))
                Text("Moonlight", color = Violet, fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 12.dp))
            }
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(games, key = { it.id }) { game ->
                    GameCard(game, connected) { connection.send("launch_game", "id" to game.id) }
                }
            }
        }
        if (!connection.supportsRaspyTv && connected) Text(
            "Mettez à jour l’agent du Raspberry Pi pour activer toutes les commandes RaspyTV.",
            color = Amber, fontSize = 12.sp, modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp)
        )
    }
}

// Croix directionnelle

private enum class PadZone(val key: String, val label: String) {
    UP("up", "Haut"), DOWN("down", "Bas"), LEFT("left", "Gauche"), RIGHT("right", "Droite"), OK("enter", "OK")
}

private fun padZone(position: Offset, size: Size): PadZone? {
    val center = Offset(size.width / 2f, size.height / 2f)
    val delta = position - center
    val radius = min(size.width, size.height) / 2f
    val distance = delta.getDistance()
    return when {
        distance > radius * 1.08f -> null
        distance < radius * OkRatio -> PadZone.OK
        abs(delta.x) > abs(delta.y) -> if (delta.x > 0) PadZone.RIGHT else PadZone.LEFT
        else -> if (delta.y > 0) PadZone.DOWN else PadZone.UP
    }
}

private const val OkRatio = .40f
private const val RepeatDelayMs = 420L
private const val RepeatIntervalMs = 115L

/**
 * Anneau directionnel et bouton OK au centre. Une direction maintenue se répète, comme une
 * touche de clavier : on parcourt une longue liste sans tapoter.
 */
@Composable
private fun DPad(enabled: Boolean, modifier: Modifier, onKey: (String) -> Unit) {
    var pressed by remember { mutableStateOf<PadZone?>(null) }
    val haptic = LocalHapticFeedback.current
    val latestOnKey by rememberUpdatedState(onKey)
    val okScale by animateFloatAsState(if (pressed == PadZone.OK) .92f else 1f, spring(dampingRatio = .55f, stiffness = 900f), label = "ok")
    val ringAlpha by animateFloatAsState(if (pressed != null && pressed != PadZone.OK) 1f else 0f, spring(stiffness = 1400f), label = "ring")
    var lastZone by remember { mutableStateOf(PadZone.UP) }
    if (pressed != null && pressed != PadZone.OK) lastZone = pressed!!

    Box(
        modifier
            .graphicsLayer { alpha = if (enabled) 1f else .45f }
            .semantics { contentDescription = "Croix directionnelle" }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val zone = padZone(down.position, Size(size.width.toFloat(), size.height.toFloat())) ?: return@awaitEachGesture
                    down.consume()
                    pressed = zone
                    haptic.performHapticFeedback(if (zone == PadZone.OK) HapticFeedbackType.LongPress else HapticFeedbackType.TextHandleMove)
                    latestOnKey(zone.key)
                    var nextRepeat = SystemClock.uptimeMillis() + RepeatDelayMs
                    try {
                        while (true) {
                            val wait = (nextRepeat - SystemClock.uptimeMillis()).coerceAtLeast(1L)
                            val event = withTimeoutOrNull(wait) { awaitPointerEvent() }
                            if (event == null) {
                                if (zone != PadZone.OK) {
                                    latestOnKey(zone.key)
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                                nextRepeat = SystemClock.uptimeMillis() + RepeatIntervalMs
                                continue
                            }
                            event.changes.forEach { it.consume() }
                            if (event.changes.none { it.pressed }) break
                        }
                    } finally {
                        pressed = null
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val radius = size.minDimension / 2f
            val inner = radius * OkRatio
            drawCircle(Brush.verticalGradient(listOf(Surface2, Surface1)), radius)
            drawCircle(Hairline, radius - .5.dp.toPx(), style = Stroke(1.dp.toPx()))
            // Repères discrets entre les quatre directions.
            for (angle in listOf(45f, 135f, 225f, 315f)) {
                val radians = Math.toRadians(angle.toDouble())
                val from = Offset(center.x + (inner + 14.dp.toPx()) * kotlin.math.cos(radians).toFloat(), center.y + (inner + 14.dp.toPx()) * kotlin.math.sin(radians).toFloat())
                val to = Offset(center.x + (radius - 14.dp.toPx()) * kotlin.math.cos(radians).toFloat(), center.y + (radius - 14.dp.toPx()) * kotlin.math.sin(radians).toFloat())
                drawLine(Hairline, from, to, 1.dp.toPx(), StrokeCap.Round)
            }
            if (ringAlpha > 0f) {
                val start = when (lastZone) { PadZone.RIGHT -> -45f; PadZone.DOWN -> 45f; PadZone.LEFT -> 135f; else -> -135f }
                val middle = (radius + inner) / 2f
                val width = radius - inner - 8.dp.toPx()
                drawArc(
                    Raspberry.copy(alpha = .22f * ringAlpha), start + 4f, 82f, useCenter = false,
                    topLeft = Offset(center.x - middle, center.y - middle), size = Size(middle * 2, middle * 2),
                    style = Stroke(width, cap = StrokeCap.Round)
                )
            }
        }
        val arrow = 34.dp
        PadArrow(Icons.Rounded.KeyboardArrowUp, pressed == PadZone.UP, Modifier.align(Alignment.TopCenter).padding(top = 14.dp).size(arrow))
        PadArrow(Icons.Rounded.KeyboardArrowDown, pressed == PadZone.DOWN, Modifier.align(Alignment.BottomCenter).padding(bottom = 14.dp).size(arrow))
        PadArrow(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, pressed == PadZone.LEFT, Modifier.align(Alignment.CenterStart).padding(start = 14.dp).size(arrow))
        PadArrow(Icons.AutoMirrored.Rounded.KeyboardArrowRight, pressed == PadZone.RIGHT, Modifier.align(Alignment.CenterEnd).padding(end = 14.dp).size(arrow))
        Box(
            Modifier.fillMaxSize(OkRatio * 2f - .04f).graphicsLayer { scaleX = okScale; scaleY = okScale }
                .clip(CircleShape)
                .background(Brush.verticalGradient(listOf(Color(0xFFFF5C86), RaspberryDeep)))
                .border(1.dp, Color.White.copy(alpha = .14f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text("OK", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        }
    }
}

@Composable
private fun PadArrow(icon: ImageVector, active: Boolean, modifier: Modifier) {
    val tint by animateColorAsState(if (active) Raspberry else TextSecondary, label = "arrow")
    Icon(icon, null, tint = tint, modifier = modifier)
}

/** Bouton rond secondaire, posé dans un coin de la croix. */
@Composable
private fun CornerButton(label: String, icon: ImageVector, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val haptic = LocalHapticFeedback.current
    Column(modifier.graphicsLayer { alpha = if (enabled) 1f else .45f }, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(54.dp).graphicsLayer { scaleX = if (pressed) .92f else 1f; scaleY = scaleX }
                .clip(CircleShape).background(if (pressed) Surface3 else Surface2)
                .clickable(source, null, enabled = enabled, role = Role.Button, onClickLabel = label) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onClick()
                },
            contentAlignment = Alignment.Center
        ) { Icon(icon, label, tint = TextPrimary, modifier = Modifier.size(23.dp)) }
        Spacer(Modifier.height(5.dp))
        Text(label, color = TextSecondary, fontSize = 11.sp)
    }
}

@Composable
private fun RoundAction(label: String, icon: ImageVector, color: Color, enabled: Boolean, filled: Boolean, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val haptic = LocalHapticFeedback.current
    Box(
        Modifier.size(60.dp).graphicsLayer { alpha = if (enabled) 1f else .45f; scaleX = if (pressed) .93f else 1f; scaleY = scaleX }
            .clip(CircleShape).background(if (filled) color else color.copy(alpha = .16f))
            .clickable(source, null, enabled = enabled, role = Role.Button, onClickLabel = label) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            },
        contentAlignment = Alignment.Center
    ) { Icon(icon, label, tint = if (filled) Color.White else color, modifier = Modifier.size(30.dp)) }
}

@Composable
private fun QuickAction(label: String, icon: ImageVector, color: Color, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    Column(
        modifier.graphicsLayer { alpha = if (enabled) 1f else .45f }.clip(RoundedCornerShape(18.dp))
            .background(if (pressed) Surface2 else Surface1)
            .clickable(source, null, enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(color.copy(alpha = .16f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.height(7.dp))
        Text(label, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// Volume

/** − et + d'un cran ; la jauge centrale se glisse pour régler directement, un toucher coupe le son. */
@Composable
private fun VolumeRocker(
    volume: Int, muted: Boolean, enabled: Boolean, modifier: Modifier,
    onStep: (Int) -> Unit, onSet: (Int) -> Unit, onMute: () -> Unit
) {
    var dragging by remember { mutableStateOf<Float?>(null) }
    val shown = dragging?.let { (it * 100).toInt() } ?: volume
    val fill by animateFloatAsState(if (muted && dragging == null) 0f else shown / 100f, spring(stiffness = 700f), label = "volume")
    val latestSet by rememberUpdatedState(onSet)
    val latestMute by rememberUpdatedState(onMute)
    val haptic = LocalHapticFeedback.current

    Row(
        modifier.height(60.dp).graphicsLayer { alpha = if (enabled) 1f else .45f }.clip(RoundedCornerShape(30.dp)).background(Surface1),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StepButton(Icons.Rounded.Remove, "Baisser le volume", enabled) { onStep(-5) }
        Box(
            Modifier.weight(1f).fillMaxHeight().padding(vertical = 10.dp).clip(RoundedCornerShape(14.dp)).background(Surface2)
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        val slop = viewConfiguration.touchSlop
                        var moved = false
                        var value = (down.position.x / size.width).coerceIn(0f, 1f)
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            if (!moved && abs(change.position.x - down.position.x) > slop) moved = true
                            if (moved) {
                                value = (change.position.x / size.width).coerceIn(0f, 1f)
                                dragging = value
                                change.consume()
                            }
                        }
                        if (moved) {
                            latestSet((value * 100).toInt())
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        } else latestMute()
                        dragging = null
                    }
                }
                .semantics { contentDescription = if (muted) "Son coupé" else "Volume $volume %" },
            contentAlignment = Alignment.CenterStart
        ) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(fill).background(Brush.horizontalGradient(listOf(Lagoon.copy(alpha = .30f), Lagoon.copy(alpha = .55f)))))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Icon(if (muted && dragging == null) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp, null, tint = TextPrimary, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (muted && dragging == null) "Muet" else "$shown %", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        StepButton(Icons.Rounded.Add, "Monter le volume", enabled) { onStep(5) }
    }
}

@Composable
private fun StepButton(icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    Box(
        Modifier.size(52.dp).clip(CircleShape).clickable(enabled = enabled, role = Role.Button, onClickLabel = label) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        },
        contentAlignment = Alignment.Center
    ) { Icon(icon, label, tint = TextPrimary, modifier = Modifier.size(22.dp)) }
}

// Lecture en cours

private fun formatTime(seconds: Float): String {
    val total = seconds.toInt().coerceAtLeast(0)
    val hours = total / 3600
    return if (hours > 0) "%d:%02d:%02d".format(hours, total / 60 % 60, total % 60) else "%d:%02d".format(total / 60, total % 60)
}

@Composable
private fun NowPlayingCard(
    media: TvMedia, accent: Color, receivedAt: Long, enabled: Boolean,
    onOp: (String, Float) -> Unit, onLocalToggle: () -> Unit
) {
    // La position avance localement entre deux relevés de l'état.
    var now by remember { mutableLongStateOf(SystemClock.uptimeMillis()) }
    LaunchedEffect(media.playing, receivedAt) {
        while (isActive && media.playing) {
            now = SystemClock.uptimeMillis()
            delay(500)
        }
    }
    val elapsed = if (media.playing) (now - receivedAt).coerceAtLeast(0) / 1000f else 0f
    val position = if (media.duration > 0) (media.position + elapsed).coerceAtMost(media.duration) else media.position
    var seeking by remember { mutableStateOf<Float?>(null) }

    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).clip(CardShape)
            .background(Brush.verticalGradient(listOf(accent.copy(alpha = .28f), Surface1)))
            .border(1.dp, accent.copy(alpha = .22f), CardShape)
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SubcomposeAsyncImage(
                model = media.artwork.ifBlank { null }, contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.size(64.dp).clip(RoundedCornerShape(14.dp)).background(accent.copy(alpha = .25f)),
                error = { ArtworkFallback(accent) }, loading = { ArtworkFallback(accent) }
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                if (media.app.isNotBlank()) Text(media.app.uppercase(), color = accent, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = .8.sp, maxLines = 1)
                Text(media.title.ifBlank { "Lecture en cours" }, color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (media.artist.isNotBlank()) Text(media.artist, color = TextSecondary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (media.duration > 0) {
            Spacer(Modifier.height(10.dp))
            SeekBar(
                fraction = seeking ?: (position / media.duration), accent = accent, enabled = enabled,
                onDrag = { seeking = it },
                onCommit = { fraction -> seeking = null; onOp("seek", fraction * media.duration) }
            )
            Row(Modifier.fillMaxWidth()) {
                Text(formatTime(seeking?.let { it * media.duration } ?: position), color = TextSecondary, fontSize = 11.sp, modifier = Modifier.weight(1f))
                Text(formatTime(media.duration), color = TextSecondary, fontSize = 11.sp)
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            MediaButton(Icons.Rounded.SkipPrevious, "Précédent", enabled) { onOp("previous", 0f) }
            Spacer(Modifier.width(22.dp))
            Box(
                Modifier.size(52.dp).clip(CircleShape).background(Color.White)
                    .clickable(enabled = enabled, role = Role.Button, onClickLabel = if (media.playing) "Pause" else "Lecture") {
                        onOp("toggle", 0f); onLocalToggle()
                    },
                contentAlignment = Alignment.Center
            ) { Icon(if (media.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (media.playing) "Pause" else "Lecture", tint = Ink, modifier = Modifier.size(28.dp)) }
            Spacer(Modifier.width(22.dp))
            MediaButton(Icons.Rounded.SkipNext, "Suivant", enabled) { onOp("next", 0f) }
        }
    }
}

@Composable
private fun ArtworkFallback(accent: Color) {
    Box(Modifier.fillMaxSize().background(accent.copy(alpha = .25f)), contentAlignment = Alignment.Center) {
        Icon(Icons.Rounded.MusicNote, null, tint = accent, modifier = Modifier.size(28.dp))
    }
}

@Composable
private fun MediaButton(icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(46.dp).clip(CircleShape).clickable(enabled = enabled, role = Role.Button, onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Icon(icon, label, tint = TextPrimary, modifier = Modifier.size(30.dp)) }
}

/** Barre de lecture : un toucher ou un glissé positionne la lecture au relâchement. */
@Composable
private fun SeekBar(fraction: Float, accent: Color, enabled: Boolean, onDrag: (Float) -> Unit, onCommit: (Float) -> Unit) {
    val latestDrag by rememberUpdatedState(onDrag)
    val latestCommit by rememberUpdatedState(onCommit)
    var active by remember { mutableStateOf(false) }
    Canvas(
        Modifier.fillMaxWidth().height(26.dp).pointerInput(enabled) {
            if (!enabled) return@pointerInput
            awaitEachGesture {
                val down = awaitFirstDown()
                active = true
                var value = (down.position.x / size.width).coerceIn(0f, 1f)
                latestDrag(value)
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) break
                    value = (change.position.x / size.width).coerceIn(0f, 1f)
                    latestDrag(value)
                    change.consume()
                }
                active = false
                latestCommit(value)
            }
        }
    ) {
        val y = size.height / 2f
        val track = (if (active) 6.dp else 4.dp).toPx()
        val x = size.width * fraction.coerceIn(0f, 1f)
        drawLine(Color.White.copy(alpha = .14f), Offset(0f, y), Offset(size.width, y), track, StrokeCap.Round)
        drawLine(accent, Offset(0f, y), Offset(x, y), track, StrokeCap.Round)
        drawCircle(Color.White, (if (active) 8.dp else 6.dp).toPx(), Offset(x, y))
    }
}

// Applications et jeux

@Composable
private fun AppTile(app: TvApp, active: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val color = app.color ?: Raspberry
    Column(
        Modifier.width(82.dp).graphicsLayer { alpha = if (enabled) 1f else .45f }.clip(RoundedCornerShape(18.dp))
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = "Ouvrir ${app.name}", onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.size(70.dp).clip(RoundedCornerShape(20.dp))
                .background(Brush.linearGradient(listOf(color.copy(alpha = .34f), color.copy(alpha = .12f))))
                .border(if (active) 2.dp else 1.dp, if (active) color else color.copy(alpha = .28f), RoundedCornerShape(20.dp)),
            contentAlignment = Alignment.Center
        ) {
            val asset = tileAsset(app.id, app.name)
            when {
                app.icon.isNotBlank() -> SubcomposeAsyncImage(
                    model = app.icon, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.size(40.dp),
                    error = { if (asset != null) Image(painterResource(asset), null, Modifier.fillMaxSize()) else LetterBadge(app.name, color, 40.dp) }
                )
                asset != null -> Image(painterResource(asset), null, Modifier.size(38.dp), contentScale = ContentScale.Fit)
                else -> LetterBadge(app.name, color, 40.dp)
            }
        }
        Spacer(Modifier.height(7.dp))
        Text(
            app.name, color = if (active) TextPrimary else TextSecondary, fontSize = 12.sp,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun GameCard(game: RemoteTile, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.width(112.dp).height(150.dp).graphicsLayer { alpha = if (enabled) 1f else .45f }.clip(RoundedCornerShape(18.dp))
            .background(Brush.verticalGradient(listOf(Violet.copy(alpha = .35f), Surface2)))
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = "Lancer ${game.name}", onClick = onClick)
    ) {
        if (game.image.isNotBlank()) SubcomposeAsyncImage(
            model = game.image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
            error = { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.SportsEsports, null, tint = Violet, modifier = Modifier.size(34.dp)) } }
        ) else Icon(Icons.Rounded.SportsEsports, null, tint = Violet, modifier = Modifier.size(34.dp).align(Alignment.Center))
        Box(
            Modifier.fillMaxWidth().align(Alignment.BottomCenter)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .85f))))
                .padding(start = 9.dp, end = 9.dp, top = 22.dp, bottom = 8.dp)
        ) {
            Text(game.name, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 15.sp)
        }
    }
}

// Sortie audio

private fun outputIcon(output: TvOutput?): ImageVector = when {
    output == null -> Icons.Rounded.Speaker
    output.kind == "bluetooth" -> Icons.Rounded.Headphones
    output.kind == "hdmi" -> Icons.Rounded.Tv
    output.headset -> Icons.Rounded.HeadsetMic
    else -> Icons.Rounded.Speaker
}

private fun outputKind(output: TvOutput) = when (output.kind) {
    "bluetooth" -> "Bluetooth"
    "hdmi" -> "Téléviseur (HDMI)"
    "usb" -> if (output.headset) "Casque USB" else "USB"
    "internal" -> "Prise jack du Pi"
    else -> "Sortie audio"
}

/** Nom court pour le bouton : « TV », « WH-1000XM4 »… */
private fun shortOutputName(output: TvOutput) = when (output.kind) {
    "hdmi" -> "TV"
    "internal" -> "Jack"
    else -> output.name.substringBefore(" Analog").substringBefore(" Wireless").take(14)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AudioSheet(tv: TvState, enabled: Boolean, onDismiss: () -> Unit, onChoose: (String) -> Unit) {
    var connecting by remember { mutableStateOf<String?>(null) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Surface1, contentColor = TextPrimary) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
            Text("Sortie audio", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Text("Le son de la TV passe par l’appareil choisi.", color = TextSecondary, fontSize = 13.sp)
            Spacer(Modifier.height(14.dp))
            tv.outputs.forEach { output ->
                OutputRow(outputIcon(output), output.name, outputKind(output), output.active, enabled) {
                    if (!output.active) onChoose(output.id)
                    onDismiss()
                }
            }
            if (tv.outputs.isEmpty()) Text("Aucune sortie détectée.", color = TextSecondary, fontSize = 13.sp)
            if (tv.bluetooth.isNotEmpty()) {
                SectionLabel("Casques Bluetooth jumelés")
                tv.bluetooth.forEach { headset ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconBadge(Icons.Rounded.Bluetooth, ActionBlue, false)
                        Spacer(Modifier.width(14.dp))
                        Text(headset.name, fontSize = 15.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        FilledTonalButton(
                            onClick = { connecting = headset.id; onChoose(headset.id) }, enabled = enabled && connecting == null,
                            colors = ButtonDefaults.filledTonalButtonColors(containerColor = ActionBlue.copy(alpha = .18f), contentColor = ActionBlue)
                        ) { Text(if (connecting == headset.id) "Connexion…" else "Se connecter", fontWeight = FontWeight.SemiBold) }
                    }
                }
                Text("Allumez le casque avant de le connecter.", color = TextMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

@Composable
private fun OutputRow(icon: ImageVector, name: String, detail: String, active: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(if (active) Lagoon.copy(alpha = .12f) else Color.Transparent)
            .clickable(enabled = enabled, onClick = onClick).padding(horizontal = 8.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconBadge(icon, Lagoon, active)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(name, fontSize = 15.sp, fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(detail, color = TextSecondary, fontSize = 12.sp)
        }
        if (active) Icon(Icons.Rounded.CheckCircle, "Sortie active", tint = Lagoon, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun IconBadge(icon: ImageVector, color: Color, strong: Boolean, size: Dp = 40.dp) {
    Box(Modifier.size(size).clip(RoundedCornerShape(13.dp)).background(color.copy(alpha = if (strong) .28f else .14f)), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = color, modifier = Modifier.size(21.dp))
    }
}

// Alimentation

/** Deux temps : on choisit d'abord, puis on confirme. Un toucher égaré n'éteint rien. */
@Composable
private fun PowerDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var choice by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface1,
        icon = { Icon(Icons.Rounded.PowerSettingsNew, null, tint = Raspberry) },
        title = { Text("Alimentation", color = TextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Que faire du Raspberry Pi ?", color = TextSecondary, fontSize = 14.sp)
                PowerOption("Redémarrer", "La télécommande se reconnecte toute seule", Icons.Rounded.RestartAlt, Amber, choice == "reboot") { choice = "reboot" }
                PowerOption("Éteindre", "Il faudra le rallumer à la main", Icons.Rounded.PowerSettingsNew, Danger, choice == "poweroff") { choice = "poweroff" }
            }
        },
        confirmButton = {
            Button(
                onClick = { choice?.let(onConfirm) }, enabled = choice != null,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (choice == "poweroff") Danger else Amber, contentColor = Ink,
                    disabledContainerColor = Surface2, disabledContentColor = TextMuted
                )
            ) { Text(when (choice) { "reboot" -> "Redémarrer"; "poweroff" -> "Éteindre"; else -> "Choisir" }, fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler", color = TextPrimary) } }
    )
}

@Composable
private fun PowerOption(label: String, detail: String, icon: ImageVector, color: Color, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .background(if (selected) color.copy(alpha = .14f) else Surface2)
            .border(1.dp, if (selected) color.copy(alpha = .6f) else Color.Transparent, RoundedCornerShape(16.dp))
            .clickable(role = Role.RadioButton, onClick = onClick).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconBadge(icon, color, selected, 38.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(label, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(detail, color = TextSecondary, fontSize = 12.sp)
        }
    }
}
