@file:OptIn(ExperimentalMaterial3Api::class)

package net.getenjoyment.devbuild

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

@Composable
fun Avatar(url: String?, name: String, size: Int = 36) {
    val m = Modifier.size(size.dp).clip(CircleShape)
    if (!url.isNullOrEmpty()) {
        AsyncImage(model = absUrl(url), contentDescription = name, modifier = m, contentScale = ContentScale.Crop)
    } else {
        Box(m.background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
            Text(name.take(1).uppercase(), color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}

@Composable
fun CenterLoading() {
    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

@Composable
fun ErrorBox(msg: String, onRetry: (() -> Unit)? = null) {
    Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(msg, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
        if (onRetry != null) OutlinedButton(onClick = onRetry) { Text("Thử lại") }
    }
}

@Composable
fun EmptyText(msg: String) {
    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { Text(msg) }
}

fun shortDate(s: String): String = s.take(16)
