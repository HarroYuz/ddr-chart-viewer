package com.harroyuz.ddrchartviewer.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.harroyuz.ddrchartviewer.domain.Difficulty

val Mint = Color(0xFF61E6C8)
val Muted = Color(0xFF9AAAC0)
private val palette = darkColorScheme(
    primary = Mint, onPrimary = Color(0xFF06382E), secondary = Color(0xFFABB2FF),
    background = Color(0xFF0B1420), surface = Color(0xFF111E2E),
    surfaceVariant = Color(0xFF1C2C40), onSurface = Color(0xFFEDF4FF),
    onSurfaceVariant = Muted, outline = Color(0xFF35465C), error = Color(0xFFFF9EAF),
    secondaryContainer = Color(0xFF21443F), onSecondaryContainer = Mint,
)
@Composable fun DdrTheme(content: @Composable () -> Unit) { MaterialTheme(colorScheme = palette, content = content) }
fun difficultyColor(difficulty: Difficulty) = when (difficulty) {
    Difficulty.BEGINNER -> Color(0xFF7DE3EE)
    Difficulty.BASIC -> Color(0xFFFFC877)
    Difficulty.DIFFICULT -> Color(0xFFFF8EA7)
    Difficulty.EXPERT -> Color(0xFF8EE4A1)
    Difficulty.CHALLENGE -> Color(0xFFC0A1FF)
}
@Composable fun SimpleTopBar(title: String, back: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        TextButton(onClick = back) { Text("‹ 返回") }
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}
