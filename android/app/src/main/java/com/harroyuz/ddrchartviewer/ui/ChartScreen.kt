package com.harroyuz.ddrchartviewer.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    SimpleTopBar("${ref.mode} · ${ref.difficulty.shortName} ${ref.level}", model::closeChart)
    Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Jacket(ref.songId, song?.title.orEmpty(), model.repository.jackets, Modifier.size(48.dp))
        Spacer(Modifier.width(12.dp))
        Text(song?.title ?: state.chart?.title.orEmpty(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 2)
    }
    Text("参考定数 ${ref.constant?.let { "%.2f".format(Locale.ROOT, it) } ?: "暂无"} · 无音乐谱面播放", Modifier.padding(horizontal = 16.dp, vertical = 4.dp), color = Muted, style = MaterialTheme.typography.labelSmall)
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
    var speed by rememberSaveable { mutableFloatStateOf(model.repository.store.speed(chart.mode)) }
    var rate by rememberSaveable { mutableFloatStateOf(1f) }
    var mirror by rememberSaveable { mutableStateOf(false) }
    var reverse by rememberSaveable { mutableStateOf(false) }
    var flip by rememberSaveable { mutableStateOf(false) }
    var fixed by rememberSaveable { mutableStateOf(false) }
    var settings by rememberSaveable { mutableStateOf(false) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) playing = false }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(playing, rate) {
        if (playing) {
            var last = withFrameNanos { it }
            while (isActive) {
                val now = withFrameNanos { it }
                position = (position + (now - last) / 1_000_000_000.0 * rate).coerceAtMost(timeline.duration)
                last = now
                if (position >= timeline.duration) { playing = false; break }
            }
        }
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("BPM ${"%.1f".format(Locale.ROOT, timeline.bpmAt(position))}", color = Mint, style = MaterialTheme.typography.labelMedium)
            Text("小节 ${(timeline.beatAt(position).coerceAtLeast(0.0) / 4).toInt() + 1} · ${chart.notes.size} 音符", color = Muted, style = MaterialTheme.typography.labelMedium)
        }
        ChartCanvas(chart, position, speed, mirror, flip, reverse, fixed, Modifier.weight(1f).fillMaxWidth()) { beatDelta ->
            playing = false
            position = timeline.timeAt((timeline.beatAt(position) + beatDelta).coerceIn(0.0, chart.endBeat)).coerceIn(timeline.start, timeline.duration)
        }
        Column(Modifier.padding(horizontal = 16.dp)) {
            Slider(value = position.toFloat().coerceIn(timeline.start.toFloat(), timeline.duration.toFloat()), onValueChange = { playing = false; position = it.toDouble() }, valueRange = timeline.start.toFloat()..timeline.duration.toFloat())
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${clock(position)} / ${clock(timeline.duration)}", color = Muted, style = MaterialTheme.typography.labelSmall)
                Text("拖动谱面定位", color = Muted, style = MaterialTheme.typography.labelSmall)
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceEvenly) {
                TextButton({ playing = false; position = timeline.start }) { Text("重置") }
                TextButton({ playing = false; position = timeline.timeAt((timeline.beatAt(position) - 4).coerceAtLeast(0.0)).coerceIn(timeline.start, timeline.duration) }) { Text("−小节") }
                Button({ if (position >= timeline.duration) position = timeline.start; playing = !playing }) { Text(if (playing) "暂停" else "播放") }
                TextButton({ playing = false; position = timeline.timeAt((timeline.beatAt(position) + 4).coerceAtMost(chart.endBeat)).coerceIn(timeline.start, timeline.duration) }) { Text("+小节") }
                TextButton({ settings = !settings }) { Text(if (settings) "收起" else "设置") }
            }
            if (settings) Column(Modifier.heightIn(max = 230.dp).verticalScroll(rememberScrollState())) {
                Text("Hi-Speed  ${"%.2f".format(Locale.ROOT, speed)}×", style = MaterialTheme.typography.labelMedium)
                Slider(speed, { speed = it }, onValueChangeFinished = { model.repository.store.saveSpeed(chart.mode, speed) }, valueRange = .25f..6f, steps = 22)
                Text("播放速率  ${"%.2f".format(Locale.ROOT, rate)}×", style = MaterialTheme.typography.labelMedium)
                Slider(rate, { rate = it }, valueRange = .25f..2f, steps = 6)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(mirror, { mirror = !mirror }, { Text("MIRROR") })
                    FilterChip(reverse, { reverse = !reverse }, { Text("REVERSE") })
                    if (chart.mode == PlayMode.DP) FilterChip(flip, { flip = !flip }, { Text("FLIP") })
                }
                FilterChip(fixed, { fixed = !fixed }, { Text("固定流速（基准 300 BPM）") })
            }
        }
    }
}

@Composable private fun ChartCanvas(
    chart: ChartData, position: Double, speed: Float, mirror: Boolean, flip: Boolean, reverse: Boolean, fixed: Boolean,
    modifier: Modifier, seek: (Double) -> Unit,
) {
    val currentSeek by rememberUpdatedState(seek)
    val timeline = chart.timeline
    val times = remember(chart) { chart.notes.associateWith { timeline.timeAt(it.beat) to timeline.timeAt(it.endBeat) } }
    Canvas(modifier.pointerInput(speed, reverse) { detectVerticalDragGestures { change, drag ->
        change.consume(); currentSeek((-drag / (32.dp.toPx() * speed) * if (reverse) -1 else 1).toDouble())
    } }) {
        val lanes = chart.mode.lanes
        val gutter = if (lanes == 8) 14.dp.toPx() else 0f
        val fieldWidth = min(size.width - 32.dp.toPx(), lanes * 68.dp.toPx())
        val laneWidth = (fieldWidth - gutter) / lanes
        val left = (size.width - fieldWidth) / 2
        val receptor = if (reverse) size.height - 44.dp.toPx() else 44.dp.toPx()
        val sign = if (reverse) -1f else 1f
        val pixels = 32.dp.toPx() * speed
        val currentBeat = timeline.beatAt(position)
        fun y(beat: Double, time: Double = timeline.timeAt(beat)): Float = receptor + sign * (if (fixed) (time - position) * 5 else beat - currentBeat).toFloat() * pixels
        fun mapped(lane: Int): Int {
            var value = if (mirror) (lane / 4) * 4 + (3 - lane % 4) else lane
            if (flip && lanes == 8) value = (value + 4) % 8
            return value
        }
        fun x(lane: Int): Float { val mapped = mapped(lane); return left + (mapped + .5f) * laneWidth + if (mapped >= 4) gutter else 0f }
        drawRect(Color(0xFF080F19))
        for (lane in 0 until lanes) {
            val center = x(lane)
            drawRect(if (lane % 2 == 0) Color(0xFF111D2C) else Color(0xFF0E1825), Offset(center - laneWidth / 2, 0f), Size(laneWidth - 1, size.height))
        }
        clipRect {
            val visibleBeats = size.height / pixels + 4
            val first = if (fixed) 0 else (currentBeat - visibleBeats).toInt().coerceAtLeast(0)
            val last = if (fixed) chart.endBeat.toInt() else min(chart.endBeat.toInt(), (currentBeat + visibleBeats).toInt() + 1)
            for (beat in first..last) {
                val lineY = y(beat.toDouble())
                if (lineY in 0f..size.height) drawLine(if (beat % 4 == 0) Color(0xFF425166) else Color(0xFF233144), Offset(left, lineY), Offset(left + fieldWidth, lineY), if (beat % 4 == 0) 2f else 1f)
            }
            for (note in chart.notes) {
                if (note.endBeat <= note.beat) continue
                val time = times.getValue(note)
                val head = y(note.beat, time.first); val tail = y(note.endBeat, time.second)
                if (max(head, tail) < 0 || min(head, tail) > size.height || position > time.second + .1) continue
                val activeHead = if (position >= time.first) receptor else head
                val color = if (note.kind == NoteKind.ROLL) Color(0xFFFFA754) else Mint
                drawLine(color.copy(alpha = .45f), Offset(x(note.lane), activeHead), Offset(x(note.lane), tail), laneWidth * .28f)
                drawCircle(color, laneWidth * .13f, Offset(x(note.lane), tail))
            }
            for (note in chart.notes) {
                val time = times.getValue(note)
                var noteY = y(note.beat, time.first)
                if (note.endBeat > note.beat && position in time.first..time.second) noteY = receptor
                else if (position > time.first + .09) continue
                if (noteY !in -laneWidth..(size.height + laneWidth)) continue
                val color = when (note.kind) {
                    NoteKind.SHOCK -> Color(0xFFFF79C1)
                    NoteKind.HOLD -> Mint
                    NoteKind.ROLL -> Color(0xFFFFA754)
                    NoteKind.FAKE -> Muted.copy(alpha = .4f)
                    else -> beatColor(note.beat)
                }
                if (note.kind == NoteKind.SHOCK) {
                    val center = Offset(x(note.lane), noteY)
                    drawCircle(color, laneWidth * .23f, center, style = Stroke(3.dp.toPx()))
                    drawLine(color, center - Offset(laneWidth * .18f, laneWidth * .18f), center + Offset(laneWidth * .18f, laneWidth * .18f), 3.dp.toPx())
                } else arrow(Offset(x(note.lane), noteY), laneWidth * .75f, mapped(note.lane) % 4, color, false)
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
