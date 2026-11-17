package com.harroyuz.ddrchartviewer.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.harroyuz.ddrchartviewer.app.AppState
import com.harroyuz.ddrchartviewer.app.AppViewModel
import com.harroyuz.ddrchartviewer.domain.*
import kotlinx.coroutines.isActive
import java.util.Locale
import kotlin.math.*

@Composable fun ChartScreen(state: AppState, model: AppViewModel) {
    val ref = state.chartRef ?: return
    val song = state.catalog?.songs?.find { it.id == ref.songId }
    AppTopBar("${ref.mode} · ${ref.difficulty.shortName}", model::closeChart) {
        DifficultyNumber(ref.level, ref.constant, color = difficultyColor(ref.difficulty))
    }
    Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Jacket(ref.songId, song?.title.orEmpty(), model.repository.jackets, Modifier.size(48.dp))
        Spacer(Modifier.width(12.dp))
        Text(song?.title ?: state.chart?.title.orEmpty(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 2, modifier = Modifier.weight(1f))
    }
    Text("无音乐谱面播放", Modifier.padding(horizontal = 16.dp, vertical = 4.dp), color = Muted, style = MaterialTheme.typography.labelSmall)
    if (state.chartLoading) { EmptyState("正在读取谱面…"); return }
    if (state.chartError != null) {
        val uri = LocalUriHandler.current
        EmptyState("暂时无法播放", state.chartError) {
            Button({ model.openChart(ref) }) { Text("重试") }
            TextButton({ uri.openUri("https://3icecream.com/ddr/song_details/${ref.songId}?diff=${ref.slot}") }) { Text("查看原站谱面 / 视频") }
        }
        return
    }
    state.chart?.let { chart -> key(ref.key, chart) { Player(chart, model) } }
}

@Composable private fun Player(chart: ChartData, model: AppViewModel) {
    val timeline = chart.timeline
    var position by rememberSaveable { mutableDoubleStateOf(timeline.start) }
    var playing by remember { mutableStateOf(false) }
    var settings by remember { mutableStateOf(model.repository.store.playerSettings(chart.mode)) }
    var expanded by rememberSaveable { mutableStateOf(false) }
    var rotaryKind by remember { mutableStateOf<PlayerNumber?>(null) }
    var rotary by remember { mutableStateOf<RotaryAdjustment?>(null) }
    var rotaryCenter by remember { mutableStateOf(Offset.Zero) }
    var rotaryPointer by remember { mutableStateOf(Offset.Zero) }
    var rootPosition by remember { mutableStateOf(Offset.Zero) }
    val density = LocalDensity.current
    fun update(next: PlayerSettings) {
        settings = next.normalized()
        model.repository.store.savePlayerSettings(chart.mode, settings)
    }
    fun finishRotary() { rotaryKind = null; rotary = null }
    BackHandler(expanded || rotaryKind != null) {
        if (rotaryKind != null) finishRotary() else expanded = false
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) playing = false }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(playing, settings.rate) {
        if (playing) {
            var last = withFrameNanos { it }
            while (isActive) {
                val now = withFrameNanos { it }
                position = (position + (now - last) / 1_000_000_000.0 * settings.rate).coerceAtMost(timeline.duration)
                last = now
                if (position >= timeline.duration) { playing = false; break }
            }
        }
    }
    Box(Modifier.fillMaxSize().clipToBounds().onGloballyPositioned { rootPosition = it.positionInRoot() }) {
        Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("谱面播放器", color = Ink, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Text("BPM ${"%.1f".format(Locale.ROOT, timeline.bpmAt(position))} · 小节 ${(timeline.beatAt(position).coerceAtLeast(0.0) / 4).toInt() + 1}", color = Muted, fontSize = 11.sp)
                }
                TextButton({ playing = false; position = timeline.start }, contentPadding = PaddingValues(0.dp), modifier = Modifier.width(38.dp)) { Text("重置", fontSize = 11.sp) }
                TextButton({
                    playing = false
                    val beat = timeline.beatAt(position)
                    val start = floor(beat / 4) * 4
                    position = timeline.timeAt((if (beat <= start + .001) start - 4 else start).coerceAtLeast(0.0)).coerceIn(timeline.start, timeline.duration)
                }, contentPadding = PaddingValues(0.dp), modifier = Modifier.width(34.dp).semantics { contentDescription = "上一小节" }) { Text("|‹", fontSize = 18.sp) }
                TextButton({ if (position >= timeline.duration) position = timeline.start; playing = !playing }, contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.width(42.dp).semantics { contentDescription = if (playing) "暂停" else "播放" }) { Text(if (playing) "Ⅱ" else "▶", fontSize = 21.sp) }
                TextButton({ playing = false; position = timeline.timeAt(((floor(timeline.beatAt(position) / 4) + 1) * 4).coerceIn(0.0, chart.endBeat)).coerceIn(timeline.start, timeline.duration) },
                    contentPadding = PaddingValues(0.dp), modifier = Modifier.width(34.dp).semantics { contentDescription = "下一小节" }) { Text("›|", fontSize = 18.sp) }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${clock(position)} / ${clock(timeline.duration)}", color = Muted, fontSize = 10.sp)
                Text("${chart.notes.size} 音符 · 拖动谱面定位", color = Muted, fontSize = 10.sp)
            }
            Slider(value = position.toFloat().coerceIn(timeline.start.toFloat(), timeline.duration.toFloat()), onValueChange = { playing = false; position = it.toDouble() },
                valueRange = timeline.start.toFloat()..timeline.duration.toFloat(), modifier = Modifier.height(32.dp))
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                // Opening the panel must not change field geometry or FHS calibration.
                val available = (maxHeight - 64.dp).coerceAtLeast(1.dp)
                val tail = minOf(44.dp, available / 3)
                val field = tail + playerFieldHeight((available - tail).value, settings.fieldHeight).dp
                ChartCanvas(chart, position, settings, Modifier.height(field).fillMaxWidth()) { beatDelta ->
                    playing = false
                    position = timeline.timeAt((timeline.beatAt(position) + beatDelta).coerceIn(0.0, chart.endBeat)).coerceIn(timeline.start, timeline.duration)
                }
                PlayerConfigPanel(settings, chart.mode, expanded, { expanded = it }, ::update,
                    onRotaryStart = { kind, point ->
                        rotaryKind = kind; rotaryCenter = point; rotaryPointer = point
                        val floating = settings.speedMode == SpeedMode.FHS
                        rotary = when (kind) {
                            PlayerNumber.HEIGHT -> RotaryAdjustment(settings.fieldHeight.toFloat(), 200f, 1000f)
                            PlayerNumber.CONSTANT -> RotaryAdjustment(settings.constantMs.toFloat(), 100f, 10000f)
                            PlayerNumber.SPEED -> if (floating) RotaryAdjustment(settings.greenNumber.toFloat(), 10f, 9999f) else RotaryAdjustment(settings.speed, .25f, 100f)
                        }
                    },
                    onRotaryMove = { point ->
                        rotaryPointer = point
                        val delta = point - rotaryCenter
                        val floating = settings.speedMode == SpeedMode.FHS
                        val units = when (rotaryKind) { PlayerNumber.CONSTANT -> 2000f; PlayerNumber.HEIGHT -> 200f; else -> if (floating) 200f else 1f }
                        rotary?.move(delta.x, delta.y, with(density) { 12.dp.toPx() }, units)?.let { value ->
                            update(when (rotaryKind) {
                                PlayerNumber.HEIGHT -> settings.copy(fieldHeight = value.roundToInt())
                                PlayerNumber.CONSTANT -> settings.copy(constantMs = (value / 10).roundToInt() * 10)
                                else -> if (floating) settings.copy(greenNumber = value.roundToInt()) else settings.copy(speed = value)
                            })
                        }
                    }, onRotaryEnd = ::finishRotary,
                    modifier = Modifier.align(Alignment.BottomCenter).heightIn(max = maxHeight * .75f).alpha(if (rotaryKind == null) 1f else 0f))
            }
        }
        if (rotaryKind != null) {
            val label = when (rotaryKind) {
                PlayerNumber.HEIGHT -> "谱面区域高度 ${settings.fieldHeight}"
                PlayerNumber.CONSTANT -> "Constant ${settings.constantMs} ms"
                else -> settings.speedLabel()
            }
            PlayerRotaryOverlay(rotaryCenter - rootPosition, rotaryPointer - rootPosition, label)
        }
    }
}

@Composable private fun ChartCanvas(
    chart: ChartData, position: Double, settings: PlayerSettings,
    modifier: Modifier, seek: (Double) -> Unit,
) {
    val currentSeek by rememberUpdatedState(seek)
    val timeline = chart.timeline
    val times = remember(chart) { chart.notes.associateWith { timeline.timeAt(it.beat) to timeline.timeAt(it.endBeat) } }
    val reverse = settings.reverse
    Canvas(modifier.pointerInput(settings) { detectVerticalDragGestures { change, drag ->
        val receptorMargin = minOf(44.dp.toPx(), size.height / 3f)
        val pixels = playerPixelsPerBeat(settings, chart.bpms.first().value, size.height - receptorMargin, density)
        change.consume(); currentSeek((-drag / pixels * if (reverse) -1 else 1).toDouble())
    } }) {
        val lanes = chart.mode.lanes
        val layout = playerLaneLayout(size.width, density, chart.mode)
        val fieldWidth = layout.width
        val laneWidth = layout.laneWidth
        val left = layout.left
        val margin = minOf(44.dp.toPx(), size.height / 3f)
        val receptor = if (reverse) size.height - margin else margin
        val sign = if (reverse) -1f else 1f
        val pixels = playerPixelsPerBeat(settings, chart.bpms.first().value, size.height - margin, density)
        val currentBeat = timeline.beatAt(position)
        fun y(beat: Double): Float = receptor + sign * (beat - currentBeat).toFloat() * pixels
        fun alpha(time: Double) = if (settings.constant) constantAlpha(time - position, settings.constantMs) else 1f
        fun bodyBrush(color: Color): Brush {
            if (!settings.constant) return Brush.verticalGradient(listOf(color, color))
            val end = y(timeline.beatAt(position + settings.constantMs / 1000.0))
            val start = y(timeline.beatAt(position + settings.constantMs / 1000.0 - .1))
            return Brush.verticalGradient(if (reverse) listOf(Color.Transparent, color) else listOf(color, Color.Transparent),
                startY = min(start, end), endY = max(start, end).coerceAtLeast(min(start, end) + 1f))
        }
        fun mapped(lane: Int): Int {
            var value = if (settings.mirror) (lane / 4) * 4 + (3 - lane % 4) else lane
            if (settings.flip && lanes == 8) value = (value + 4) % 8
            return value
        }
        fun x(lane: Int): Float = layout.center(mapped(lane))
        drawRect(Color(0xFF080F19))
        for (lane in 0 until lanes) {
            val center = x(lane)
            drawRect(if (lane % 2 == 0) Color(0xFF111D2C) else Color(0xFF0E1825), Offset(center - laneWidth / 2, 0f), Size(laneWidth - 1, size.height))
        }
        clipRect {
            val visibleBeats = size.height / pixels + 4
            val first = (currentBeat - visibleBeats).toInt().coerceAtLeast(0)
            val last = min(chart.endBeat.toInt(), (currentBeat + visibleBeats).toInt() + 1)
            for (beat in first..last) {
                val lineY = y(beat.toDouble())
                if (lineY in 0f..size.height) drawLine(if (beat % 4 == 0) Color(0xFF425166) else Color(0xFF233144), Offset(left, lineY), Offset(left + fieldWidth, lineY), if (beat % 4 == 0) 2f else 1f)
            }
            for (note in chart.notes) {
                if (note.endBeat <= note.beat) continue
                val time = times.getValue(note)
                val head = y(note.beat); val tail = y(note.endBeat)
                if (max(head, tail) < 0 || min(head, tail) > size.height || notePhase(position, time.first, time.second) == NotePhase.PASSED) continue
                val activeHead = if (position >= time.first) receptor else head
                val color = if (note.kind == NoteKind.ROLL) Color(0xFFFFA754) else Mint
                drawLine(bodyBrush(color.copy(alpha = .45f)), Offset(x(note.lane), activeHead), Offset(x(note.lane), tail), laneWidth * .28f)
                drawCircle(color.copy(alpha = alpha(time.second)), laneWidth * .13f, Offset(x(note.lane), tail))
            }
            for (note in chart.notes) {
                val time = times.getValue(note)
                val phase = notePhase(position, time.first, time.second)
                // Remove the head at judgment time; sustained notes stay pinned until their tail ends.
                if (phase == NotePhase.PASSED) continue
                val noteY = if (phase == NotePhase.HOLDING) receptor else y(note.beat)
                if (noteY !in -laneWidth..(size.height + laneWidth)) continue
                val baseColor = when (note.kind) {
                    NoteKind.SHOCK -> Color(0xFFFF79C1)
                    NoteKind.HOLD -> Mint
                    NoteKind.ROLL -> Color(0xFFFFA754)
                    NoteKind.FAKE -> Color(0xFF9AAAC0).copy(alpha = .4f)
                    else -> beatColor(note.beat)
                }
                val color = baseColor.copy(alpha = baseColor.alpha * alpha(time.first))
                if (note.kind == NoteKind.SHOCK) {
                    val center = Offset(x(note.lane), noteY)
                    drawCircle(color, laneWidth * .23f, center, style = Stroke(3.dp.toPx()))
                    drawLine(color, center - Offset(laneWidth * .18f, laneWidth * .18f), center + Offset(laneWidth * .18f, laneWidth * .18f), 3.dp.toPx())
                } else arrow(Offset(x(note.lane), noteY), laneWidth * .75f, mapped(note.lane) % 4, color, false)
            }
            // Brief receptor feedback makes the disappearance read as a hit, including in reverse mode.
            for (note in chart.notes) {
                if (note.kind == NoteKind.FAKE) continue
                val elapsed = position - times.getValue(note).first
                if (elapsed >= 0 && elapsed < .12) {
                    val fade = (1 - elapsed / .12).toFloat()
                    arrow(Offset(x(note.lane), receptor), laneWidth * (.8f + .15f * (1 - fade)), mapped(note.lane) % 4,
                        Color.White.copy(alpha = fade * .8f), false)
                }
            }
            for (lane in 0 until lanes) arrow(Offset(x(lane), receptor), laneWidth * .8f, mapped(lane) % 4, Color.White.copy(alpha = .75f), true)
        }
    }
}

private fun DrawScope.arrow(center: Offset, width: Float, lane: Int, color: Color, outline: Boolean) {
    val r = width / 2
    val path = Path().apply {
        moveTo(center.x, center.y - r)
        lineTo(center.x + r, center.y)
        lineTo(center.x + r * .38f, center.y)
        lineTo(center.x + r * .38f, center.y + r)
        lineTo(center.x - r * .38f, center.y + r)
        lineTo(center.x - r * .38f, center.y)
        lineTo(center.x - r, center.y); close()
    }
    rotate(listOf(-90f, 180f, 0f, 90f)[lane], center) {
        if (outline) drawPath(path, color, style = Stroke(2.dp.toPx())) else drawPath(path, color)
    }
}
private fun beatColor(beat: Double): Color = when {
    abs(beat - round(beat)) < .001 -> Color(0xFFFF7E98)
    abs(beat * 2 - round(beat * 2)) < .001 -> Color(0xFF77B8FF)
    abs(beat * 4 - round(beat * 4)) < .001 -> Color(0xFFFFDA7D)
    else -> Color(0xFFC0A1FF)
}
private fun clock(seconds: Double): String { val n = seconds.toInt().coerceAtLeast(0); return "%d:%02d".format(Locale.ROOT, n / 60, n % 60) }
