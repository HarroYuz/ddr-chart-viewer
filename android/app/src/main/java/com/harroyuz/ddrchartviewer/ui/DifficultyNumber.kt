package com.harroyuz.ddrchartviewer.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlin.math.roundToInt

internal fun difficultySuffix(level: Int?, constant: Double?): String? {
    if (level == null || constant == null || !constant.isFinite() || constant < level || constant >= level + 1) return null
    return ".%02d".format(Locale.ROOT, ((constant - level) * 100).roundToInt().coerceIn(0, 99))
}

@Composable internal fun DifficultyNumber(
    level: Int?, constant: Double?, modifier: Modifier = Modifier,
    color: Color = Ink, mainSize: TextUnit = 23.sp, fractionSize: TextUnit = 12.sp,
) {
    Text(buildAnnotatedString {
        withStyle(SpanStyle(fontSize = mainSize, fontWeight = FontWeight.Bold)) { append(level?.toString() ?: "?") }
        difficultySuffix(level, constant)?.let { suffix ->
            withStyle(SpanStyle(fontSize = fractionSize, fontWeight = FontWeight.SemiBold)) { append(suffix) }
        }
    }, modifier = modifier, color = color, maxLines = 1)
}
