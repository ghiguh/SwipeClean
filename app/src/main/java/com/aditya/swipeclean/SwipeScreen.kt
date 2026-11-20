package com.aditya.swipeclean

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.min
import kotlin.math.sign

data class SwipeCmd(val keep: Boolean, val nonce: Int)

val KeepGreen = Color(0xFF2ECC71)
val TrashRed = Color(0xFFFF4D4F)

/** Rubber-band: finger travel -> card travel, with growing resistance. */
fun resist(raw: Float, max: Float): Float = sign(raw) * max * (1f - exp(-abs(raw) / max))
fun unresist(x: Float, max: Float): Float {
    val r = (abs(x) / max).coerceIn(0f, 0.98f)
    return sign(x) * (-max * ln(1f - r))
}
fun fmtDuration(ms: Long) = "%d:%02d".format(ms / 60000, (ms / 1000) % 60)

@Composable
fun SwipeScreen(vm: MainViewModel, onTrash: () -> Unit, onSettings: () -> Unit) {
    var cmd by remember { mutableStateOf<SwipeCmd?>(null) }
    var nonce by remember { mutableIntStateOf(0) }
    var progress by remember { mutableFloatStateOf(0f) }
    var editing by remember { mutableStateOf<MediaItem?>(null) }
    val queue = vm.queue
    val bg = lerp(MaterialTheme.colorScheme.background, if (progress > 0) KeepGreen else TrashRed, abs(progress) * 0.22f)

    Column(Modifier.fillMaxSize().background(bg).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("SwipeClean", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("${queue.size} to go", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onTrash) {
                BadgedBox(badge = { if (vm.trashed.isNotEmpty()) Badge { Text("${vm.trashed.size}") } }) {
                    Icon(Icons.Rounded.Delete, "Trash")
                }
            }
            IconButton(onClick = onSettings) { Icon(Icons.Rounded.Settings, "Settings") }
        }

        Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
            when {
                vm.loading -> CircularProgressIndicator()
                queue.isEmpty() -> EmptyState(vm, onTrash)
                else -> {
                    val visible = queue.take(3)
                    for (i in visible.indices.reversed()) {
                        val item = visible[i]
                        key(item.id) {
                            SwipeCard(
                                item = item, depth = i, vm = vm, cmd = cmd,
                                onCmdConsumed = { cmd = null },
                                onProgress = { progress = it },
                                onSwiped = { keep -> vm.swipe(item, keep) },
                                onEdit = { editing = item }
                            )
                        }
                    }
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(bottom = 20.dp, top = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically
        ) {
            BouncyButton(72, TrashRed, enabled = queue.isNotEmpty(), onClick = { cmd = SwipeCmd(false, ++nonce) }) {
                Icon(Icons.Rounded.Close, "Trash", tint = Color.White, modifier = Modifier.size(34.dp))
            }
            BouncyButton(52, MaterialTheme.colorScheme.secondaryContainer, enabled = vm.canUndo, onClick = { vm.undo() }) {
                Icon(Icons.AutoMirrored.Rounded.Undo, "Undo", tint = MaterialTheme.colorScheme.onSecondaryContainer)
            }
            BouncyButton(72, KeepGreen, enabled = queue.isNotEmpty(), onClick = { cmd = SwipeCmd(true, ++nonce) }) {
                Icon(Icons.Rounded.Check, "Keep", tint = Color.White, modifier = Modifier.size(34.dp))
            }
        }
    }
    editing?.let { EditSheet(it, vm) { editing = null } }
}

@Composable
fun BouncyButton(size: Int, container: Color, enabled: Boolean = true, onClick: () -> Unit, content: @Composable BoxScope.() -> Unit) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val s by animateFloatAsState(
        if (pressed) 0.8f else 1f, spring<Float>(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium), label = "press"
    )
    Box(
        Modifier.size(size.dp).graphicsLayer { scaleX = s; scaleY = s; alpha = if (enabled) 1f else 0.35f }
            .shadow(if (enabled) 6.dp else 0.dp, CircleShape).background(container, CircleShape).clip(CircleShape)
            .clickable(interactionSource = src, indication = null, enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center, content = content
    )
}

@Composable
fun EmptyState(vm: MainViewModel, onTrash: () -> Unit) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val s by animateFloatAsState(if (shown) 1f else 0f, spring<Float>(0.35f, Spring.StiffnessLow), label = "empty")
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.graphicsLayer { scaleX = s; scaleY = s }) {
        Icon(Icons.Rounded.DoneAll, null, Modifier.size(96.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        Text("All caught up", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("${vm.trashed.size} waiting in trash", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onTrash) { Text("Review trash") }
    }
}

@Composable
fun SwipeCard(
    item: MediaItem, depth: Int, vm: MainViewModel, cmd: SwipeCmd?,
    onCmdConsumed: () -> Unit, onProgress: (Float) -> Unit,
    onSwiped: (Boolean) -> Unit, onEdit: () -> Unit
) {
    val isTop = depth == 0
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val widthPx = with(density) { LocalConfiguration.current.screenWidthDp.dp.toPx() }
    val trigger = widthPx * 0.30f
    val maxStretch = widthPx * 0.55f
    val dispT = resist(trigger, maxStretch)
    val tickStep = with(density) { 22.dp.toPx() }
    val offX = remember { Animatable(0f) }
    val offY = remember { Animatable(0f) }
    var busy by remember { mutableStateOf(false) }

    fun commit(keep: Boolean) {
        if (busy) return
        busy = true
        vm.fxCommit(keep)
        scope.launch {
            offX.animateTo((if (keep) 1f else -1f) * widthPx * 1.5f, tween(300, easing = FastOutLinearInEasing))
            onSwiped(keep)
        }
    }
    fun bounce() {
        vm.fxSnap()
        scope.launch { offY.animateTo(0f, spring<Float>(0.4f, 300f)) }
        scope.launch { offX.animateTo(0f, spring<Float>(0.4f, 300f)) }
    }

    LaunchedEffect(cmd, isTop) {
        if (isTop && cmd != null) { onCmdConsumed(); vm.fxTick(1f); commit(cmd.keep) }
    }

    val signed by remember { derivedStateOf { (offX.value / dispT).coerceIn(-1f, 1f) } }
    if (isTop) {
        LaunchedEffect(Unit) { snapshotFlow { signed }.collect { onProgress(it) } }
        DisposableEffect(Unit) { onDispose { onProgress(0f) } }
    }

    val gesture = if (isTop) Modifier.pointerInput(item.id) {
        val tracker = VelocityTracker()
        var raw = 0f; var rawY = 0f; var crossed = false; var lastTick = 0f
        detectDragGestures(
            onDragStart = {
                if (!busy) {
                    raw = unresist(offX.value, maxStretch); rawY = offY.value / 0.35f
                    lastTick = raw; crossed = abs(raw) >= trigger
                    scope.launch { offX.stop(); offY.stop() }
                }
            },
            onDrag = { change, amount ->
                if (!busy) {
                    change.consume()
                    tracker.addPosition(change.uptimeMillis, change.position)
                    raw += amount.x; rawY += amount.y
                    val x = resist(raw, maxStretch)
                    val y = (rawY * 0.35f).coerceIn(-widthPx * 0.25f, widthPx * 0.25f)
                    scope.launch { offX.snapTo(x); offY.snapTo(y) }
                    if (abs(raw - lastTick) >= tickStep) { lastTick = raw; vm.fxTick(min(1f, abs(raw) / trigger)) }
                    val now = abs(raw) >= trigger
                    if (now != crossed) { crossed = now; if (now) vm.fxThreshold() else vm.fxTick(0.3f) }
                }
            },
            onDragEnd = {
                val v = tracker.calculateVelocity().x
                tracker.resetTracking()
                if (!busy) {
                    val flick = abs(v) > 2000f && abs(raw) > widthPx * 0.08f
                    if (abs(raw) >= trigger || flick) commit(if (abs(raw) >= trigger) raw > 0 else v > 0) else bounce()
                }
                raw = 0f; rawY = 0f; crossed = false; lastTick = 0f
            },
            onDragCancel = { if (!busy) bounce(); raw = 0f; rawY = 0f; crossed = false }
        )
    } else Modifier

    val scale by animateFloatAsState(1f - depth * 0.06f, spring<Float>(0.5f, Spring.StiffnessLow), label = "scale")
    val lift by animateDpAsState((depth * 18).dp, spring<androidx.compose.ui.unit.Dp>(0.5f, Spring.StiffnessLow), label = "lift")

    val meta by produceState<Meta?>(null, item.id, vm.metaVersion, isTop) { if (isTop) value = vm.readMeta(item) }
    val fmt = remember { SimpleDateFormat("d MMM yyyy · HH:mm", Locale.getDefault()) }
    val missing = meta?.let { it.dateTaken == null || (!item.isVideo && !it.hasLocation) } ?: false
    val summary = meta?.let { m ->
        (m.dateTaken?.let { fmt.format(Date(it)) } ?: "No date") +
            (if (item.isVideo) "" else if (m.hasLocation) " · Located" else " · No location")
    } ?: ""

    Box(
        Modifier.fillMaxSize().then(gesture).graphicsLayer {
            translationX = offX.value
            translationY = offY.value + lift.toPx()
            rotationZ = offX.value / widthPx * 14f
            scaleX = scale; scaleY = scale
        }
    ) {
        Card(
            Modifier.fillMaxSize(), shape = RoundedCornerShape(32.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
            elevation = CardDefaults.cardElevation(8.dp)
        ) {
            Box(Modifier.fillMaxSize()) {
                AsyncImage(item.uri, item.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)

                if (item.isVideo) {
                    Surface(
                        Modifier.align(Alignment.TopCenter).padding(16.dp), shape = CircleShape,
                        color = Color.Black.copy(alpha = 0.5f)
                    ) {
                        Row(Modifier.padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.PlayArrow, null, tint = Color.White, modifier = Modifier.size(18.dp))
                            Text(fmtDuration(item.durationMs), color = Color.White, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }

                // gentle green / red wash that grows as you pull
                val a = abs(signed)
                if (a > 0.001f) {
                    val c = if (signed > 0) KeepGreen else TrashRed
                    Box(
                        Modifier.matchParentSize().background(
                            Brush.horizontalGradient(
                                if (signed > 0) listOf(c.copy(alpha = a * 0.20f), c.copy(alpha = a * 0.70f))
                                else listOf(c.copy(alpha = a * 0.70f), c.copy(alpha = a * 0.20f))
                            )
                        )
                    )
                    val ic by animateFloatAsState(
                        if (a >= 1f) 1.25f else 0.55f + 0.6f * a, spring<Float>(0.4f, Spring.StiffnessMedium), label = "icon"
                    )
                    Box(
                        Modifier.align(if (signed > 0) Alignment.TopStart else Alignment.TopEnd).padding(28.dp)
                            .graphicsLayer { scaleX = ic; scaleY = ic; alpha = min(1f, a * 1.4f) }
                            .size(64.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.92f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            if (signed > 0) Icons.Rounded.Check else Icons.Rounded.Delete, null,
                            tint = c, modifier = Modifier.size(34.dp)
                        )
                    }
                }

                // subtle info handle (tap to edit metadata)
                Column(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f))))
                        .then(if (isTop) Modifier.clickable(onClick = onEdit) else Modifier)
                        .padding(top = 36.dp, bottom = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (missing) {
                            Box(Modifier.size(6.dp).clip(CircleShape).background(Color(0xFFFFC857)))
                            Spacer(Modifier.width(6.dp))
                        }
                        Text(summary, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.75f))
                    }
                    Spacer(Modifier.height(8.dp))
                    Box(Modifier.width(40.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = 0.6f)))
                }
            }
        }
    }
}
