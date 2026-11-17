package com.harroyuz.ddrchartviewer.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.harroyuz.ddrchartviewer.data.JacketRepository

@Composable fun Jacket(id: String, title: String, repository: JacketRepository, modifier: Modifier) {
    val bitmap by produceState<Bitmap?>(null, id) { value = repository.load(id) }
    Box(modifier.clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
        val loaded = bitmap
        if (loaded == null) Text("♫", color = Muted, style = MaterialTheme.typography.headlineMedium)
        else Image(loaded.asImageBitmap(), contentDescription = "$title 曲绘", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
    }
}
