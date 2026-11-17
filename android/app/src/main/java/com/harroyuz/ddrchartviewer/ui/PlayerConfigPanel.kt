package com.harroyuz.ddrchartviewer.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.harroyuz.ddrchartviewer.domain.*
import java.util.Locale

internal fun PlayerSettings.speedLabel(): String = if (speedMode == SpeedMode.FHS) "FHS $greenNumber" else "Hi-Speed ${String.format(Locale.ROOT, "%.2f", speed)}×"
internal fun PlayerSettings.summary(mode: PlayMode): String = buildList {
    add(speedLabel()); add("高度 $fieldHeight")
    if (constant) add("Constant ${constantMs}ms")
    if (mirror) add("MIRROR")
    if (reverse) add("REVERSE")
    if (flip && mode == PlayMode.DP) add("FLIP")
    if (rate != 1f) add("播放 ${String.format(Locale.ROOT, "%.2f", rate)}×")
}.joinToString(" · ")

@Composable
internal fun PlayerConfigPanel(
    settings: PlayerSettings, mode: PlayMode, expanded: Boolean, onExpanded: (Boolean) -> Unit,
    onChange: (PlayerSettings) -> Unit, onRotaryStart: (PlayerNumber, Offset) -> Unit,
    onRotaryMove: (Offset) -> Unit, onRotaryEnd: () -> Unit, modifier: Modifier = Modifier,
) {
    val floating = settings.speedMode == SpeedMode.FHS
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
        .background(MaterialTheme.colorScheme.surface).border(1.dp, Outline, RoundedCornerShape(10.dp))) {
        Row(Modifier.fillMaxWidth().clickable { onExpanded(!expanded) }.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (expanded) "播放器配置" else settings.summary(mode), color = if (expanded) Ink else Muted,
                fontSize = 11.sp, modifier = Modifier.weight(1f))
            Text(if (expanded) "收起 ▼" else "设置 ▲", color = Muted, fontSize = 11.sp, modifier = Modifier.padding(start = 8.dp))
        }
        AnimatedVisibility(expanded) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 10.dp, vertical = 4.dp)) {
                Text("点击数值输入，长按数值并旋转调节", color = Muted, fontSize = 10.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Choice("FHS", floating) { onChange(settings.copy(speedMode = SpeedMode.FHS)) }
                    Spacer(Modifier.width(4.dp))
                    Choice("Hi-Speed", !floating) { onChange(settings.copy(speedMode = SpeedMode.HI_SPEED)) }
                    Spacer(Modifier.weight(1f))
                    PlayerNumberControl(
                        label = if (floating) "开局绿字（10～9999，步长 1）" else "Hi-Speed（0.25～100.00，步长 0.01×）",
                        value = if (floating) settings.greenNumber.toString() else String.format(Locale.ROOT, "%.2f", settings.speed),
                        decimal = !floating,
                        onValue = { text -> if (floating) text.toIntOrNull()?.takeIf { it in 10..9999 }?.let { onChange(settings.copy(greenNumber = it)); true } ?: false
                            else text.toFloatOrNull()?.takeIf { it in .25f..100f }?.let { onChange(settings.copy(speed = it)); true } ?: false },
                        onDecrease = { onChange(if (floating) settings.copy(greenNumber = settings.greenNumber - 1) else settings.copy(speed = settings.speed - .01f)) },
                        onIncrease = { onChange(if (floating) settings.copy(greenNumber = settings.greenNumber + 1) else settings.copy(speed = settings.speed + .01f)) },
                        onRotaryStart = { onRotaryStart(PlayerNumber.SPEED, it) }, onRotaryMove = onRotaryMove, onRotaryEnd = onRotaryEnd,
                    )
                }
                Text(if (floating) "按开局 BPM 固定绿字，途中随 BPM 变速" else "按倍率滚动，途中随 BPM 变速", color = Muted, fontSize = 10.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("谱面区域高度", color = Muted, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    PlayerNumberControl("谱面区域高度（200～1000，步长 1）", settings.fieldHeight.toString(), false,
                        onValue = { text -> text.toIntOrNull()?.takeIf { it in 200..1000 }?.let { onChange(settings.copy(fieldHeight = it)); true } ?: false },
                        onDecrease = { onChange(settings.copy(fieldHeight = settings.fieldHeight - 1)) },
                        onIncrease = { onChange(settings.copy(fieldHeight = settings.fieldHeight + 1)) },
                        onRotaryStart = { onRotaryStart(PlayerNumber.HEIGHT, it) }, onRotaryMove = onRotaryMove, onRotaryEnd = onRotaryEnd)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Choice("Constant", settings.constant) { onChange(settings.copy(constant = !settings.constant)) }
                    Spacer(Modifier.weight(1f))
                    PlayerNumberControl("Constant 提前淡入（100～10000 ms，步长 10 ms）", settings.constantMs.toString(), false,
                        onValue = { text -> text.toIntOrNull()?.takeIf { it in 100..10000 && it % 10 == 0 }?.let { onChange(settings.copy(constantMs = it)); true } ?: false },
                        onDecrease = { onChange(settings.copy(constantMs = settings.constantMs - 10)) },
                        onIncrease = { onChange(settings.copy(constantMs = settings.constantMs + 10)) },
                        onRotaryStart = { onRotaryStart(PlayerNumber.CONSTANT, it) }, onRotaryMove = onRotaryMove, onRotaryEnd = onRotaryEnd)
                }
                Text("Constant：判定前指定毫秒开始淡入，淡入持续 100 ms", color = Muted, fontSize = 10.sp)
                Row(Modifier.padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Choice("MIRROR", settings.mirror) { onChange(settings.copy(mirror = !settings.mirror)) }
                    Choice("REVERSE", settings.reverse) { onChange(settings.copy(reverse = !settings.reverse)) }
                    if (mode == PlayMode.DP) Choice("FLIP", settings.flip) { onChange(settings.copy(flip = !settings.flip)) }
                }
                Text("播放速率 ${String.format(Locale.ROOT, "%.2f", settings.rate)}×", color = Muted, fontSize = 12.sp)
                Slider(settings.rate, { onChange(settings.copy(rate = it)) }, valueRange = .25f..2f, steps = 6)
            }
        }
    }
}

@Composable private fun Choice(label: String, selected: Boolean, action: () -> Unit) {
    FilterChip(selected, action, { Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold) })
}
