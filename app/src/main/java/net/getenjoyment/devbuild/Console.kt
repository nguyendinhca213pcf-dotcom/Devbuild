@file:OptIn(ExperimentalMaterial3Api::class)

package net.getenjoyment.devbuild

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream

fun readImageBase64(ctx: Context, uri: Uri): String? {
    try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (bounds.outWidth / sample > 1024 || bounds.outHeight / sample > 1024) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val bmp = ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return null
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, 88, out)
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    } catch (e: Exception) {
        return null
    }
}

@Composable
fun ConsoleScreen(onBack: () -> Unit, onRun: (Int) -> Unit) {
    val staff = Session.me?.optBoolean("is_staff") == true
    var open by remember { mutableStateOf(setOf<Int>()) }
    Scaffold(topBar = { BackBar(tr("Tất cả API", "API console"), onBack) }) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize()) {
            SECTIONS.forEachIndexed { si, names ->
                if (si < 6 || staff) {
                    item(key = "h$si") {
                        ListItem(
                            headlineContent = { Text(tr(names.first, names.second)) },
                            trailingContent = { Text(if (si in open) "▲" else "▼") },
                            modifier = Modifier.clickable { open = if (si in open) open - si else open + si }
                        )
                    }
                    if (si in open) {
                        ENDPOINTS.forEachIndexed { i, e ->
                            if (e.sec == si) item(key = "e$i") {
                                ListItem(
                                    overlineContent = { Text(e.method) },
                                    headlineContent = { Text(e.path, fontFamily = FontFamily.Monospace, fontSize = 13.sp) },
                                    modifier = Modifier.clickable { onRun(i) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RunnerScreen(index: Int, onBack: () -> Unit) {
    val ep = ENDPOINTS[index]
    val holders = remember(index) { Regex("\\{(\\w+)\\}").findAll(ep.path).map { it.groupValues[1] }.toList() }
    val pv = remember(index) { mutableStateMapOf<String, String>() }
    val vals = remember(index) { mutableStateMapOf<String, String>() }
    var result by remember(index) { mutableStateOf<String?>(null) }
    var err by remember(index) { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    var pickKey by remember { mutableStateOf("") }
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) readImageBase64(ctx, uri)?.let { vals[pickKey] = it }
    }

    fun run() {
        scope.launch {
            busy = true; err = null; result = null
            try {
                var p = ep.path
                for (h in holders) p = p.replace("{" + h + "}", Uri.encode(pv[h] ?: ""))
                val data = vals.filterValues { it.isNotBlank() }
                val d = if (ep.method == "GET") Api.request("GET", p, query = data) else Api.request(ep.method, p, body = data)
                result = d.toString(2)
            } catch (e: Exception) {
                err = errText(e)
            }
            busy = false
        }
    }

    Scaffold(topBar = { BackBar(ep.method + " " + ep.path, onBack) }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp).imePadding(),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (h in holders) {
                OutlinedTextField(pv[h] ?: "", { pv[h] = it }, Modifier.fillMaxWidth(), label = { Text("{$h}") }, singleLine = true)
            }
            for (k in ep.params) {
                if (k.endsWith("_base64")) {
                    OutlinedButton(onClick = { pickKey = k; picker.launch("image/*") }) {
                        Text((if (vals[k].isNullOrEmpty()) "" else "✔ ") + tr("Chọn ảnh", "Choose image") + " (" + k.removeSuffix("_base64") + ")")
                    }
                } else {
                    OutlinedTextField(vals[k] ?: "", { vals[k] = it }, Modifier.fillMaxWidth(), label = { Text(k) })
                }
            }
            Button(enabled = !busy, onClick = { if (ep.method == "DELETE") confirm = true else run() }) {
                if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text(tr("Chạy", "Run"))
            }
            err?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            result?.let {
                Box(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                    SelectionContainer { Text(it, fontFamily = FontFamily.Monospace, fontSize = 12.sp, softWrap = false) }
                }
            }
        }
    }
    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false },
        title = { Text(tr("Xác nhận xóa?", "Confirm delete?")) },
        text = { Text(ep.path) },
        confirmButton = { TextButton(onClick = { confirm = false; run() }) { Text(tr("Chạy", "Run")) } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text(tr("Hủy", "Cancel")) } }
    )
}
