package com.harroyuz.ddrchartviewer.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.harroyuz.ddrchartviewer.domain.Difficulty

// Shared palette with IIDX Chart Viewer; the playfield keeps its dark colors.
val Ink = Color(0xFF202135)
val Muted = Color(0xFF6B6D80)
val Purple = Color(0xFF6750C8)
val Outline = Color(0xFFDEE1EC)
val Mint = Color(0xFF61E6C8)
private val palette = lightColorScheme(
    primary = Purple, onPrimary = Color.White,
    primaryContainer = Color(0xFFEEF0F8), onPrimaryContainer = Ink,
    secondary = Color(0xFF198D9B),
    background = Color(0xFFF7F8FC), onBackground = Ink,
    surface = Color.White, onSurface = Ink,
    surfaceVariant = Color(0xFFEEF0F8), onSurfaceVariant = Muted,
    surfaceContainer = Color(0xFFEEF0F8), surfaceContainerLow = Color(0xFFF7F8FC),
    surfaceContainerHigh = Color(0xFFEEF0F8), outline = Muted, outlineVariant = Outline,
)
@Composable fun DdrTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = palette, shapes = Shapes(
        extraSmall = RoundedCornerShape(8.dp), small = RoundedCornerShape(12.dp),
        medium = RoundedCornerShape(16.dp), large = RoundedCornerShape(20.dp), extraLarge = RoundedCornerShape(28.dp),
    ), content = content)
}
fun difficultyColor(difficulty: Difficulty) = when (difficulty) {
    Difficulty.BEGINNER -> Color(0xFF198D9B)
    Difficulty.BASIC -> Color(0xFF9A6700)
    Difficulty.DIFFICULT -> Color(0xFFD04C77)
    Difficulty.EXPERT -> Color(0xFF1B8657)
    Difficulty.CHALLENGE -> Color(0xFF5635B8)
}
@Composable fun SimpleTopBar(title: String, back: () -> Unit) = AppTopBar(title, back)

@Composable internal fun AppTopBar(
    title: String, navigate: () -> Unit, menu: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(navigate, Modifier.semantics { contentDescription = if (menu) "打开菜单" else "返回" }) {
            Canvas(Modifier.size(24.dp)) {
                val stroke = 2.dp.toPx()
                if (menu) listOf(.25f, .5f, .75f).forEach { y ->
                    drawLine(Ink, Offset(size.width * .15f, size.height * y), Offset(size.width * .85f, size.height * y), stroke, StrokeCap.Round)
                } else {
                    drawLine(Ink, Offset(size.width * .75f, size.height * .5f), Offset(size.width * .2f, size.height * .5f), stroke, StrokeCap.Round)
                    drawLine(Ink, Offset(size.width * .2f, size.height * .5f), Offset(size.width * .5f, size.height * .2f), stroke, StrokeCap.Round)
                    drawLine(Ink, Offset(size.width * .2f, size.height * .5f), Offset(size.width * .5f, size.height * .8f), stroke, StrokeCap.Round)
                }
            }
        }
        Text(title, color = Ink, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        actions()
    }
}
