@file:OptIn(ExperimentalMaterial3Api::class)

package net.getenjoyment.devbuild

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.json.JSONObject

@Composable
fun SnippetsTab(refreshKey: Int, onOpen: (Int) -> Unit) {
    var query by remember { mutableStateOf("") }
    var applied by remember { mutableStateOf("") }
    var sort by remember { mutableStateOf("new") }
    var filter by remember { mutableStateOf("all") }
    val items = remember { mutableStateListOf<JSONObject>() }
    var page by remember { mutableIntStateOf(1) }
    var pages by remember { mutableIntStateOf(1) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    suspend fun fetch(p: Int) {
        loading = true; error = null
        try {
            val d = Api.request("GET", "snippets", query = mapOf(
                "page" to p, "per_page" to 15, "q" to applied, "sort" to sort,
                "mine" to (if (filter == "mine") "1" else null),
                "starred" to (if (filter == "starred") "1" else null)))
            if (p == 1) items.clear()
            items.addAll(d.optJSONArray("snippets").objs())
            val meta = d.optJSONObject("pagination")
            page = meta?.optInt("page", p) ?: p
            pages = meta?.optInt("pages", 1) ?: 1
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            error = errText(e)
        }
        loading = false
    }

    LaunchedEffect(applied, sort, filter, refreshKey) { fetch(1) }

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query, onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            placeholder = { Text("Tìm theo tiêu đề, mô tả, tác giả…") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { applied = query.trim() })
        )
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("all" to "Tất cả", "mine" to "Của tôi", "starred" to "Đã gắn sao").forEach { (k, n) ->
                FilterChip(selected = filter == k, onClick = { filter = k }, label = { Text(n) })
            }
            listOf("new" to "Mới", "popular" to "Phổ biến", "views" to "Xem nhiều").forEach { (k, n) ->
                AssistChip(onClick = { sort = k }, label = { Text(if (sort == k) "● $n" else n) })
            }
        }
        LazyColumn(Modifier.fillMaxSize()) {
            items(items, key = { it.optInt("id") }) { s -> SnippetCard(s) { onOpen(s.optInt("id")) } }
            item {
                when {
                    loading -> CenterLoading()
                    error != null -> ErrorBox(error!!) { scope.launch { fetch(page) } }
                    items.isEmpty() -> EmptyText("Chưa có đoạn code nào.")
                    page < pages -> Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                        OutlinedButton(onClick = { scope.launch { fetch(page + 1) } }) { Text("Tải thêm") }
                    }
                }
            }
        }
    }
}

@Composable
fun SnippetCard(s: JSONObject, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(s.str("title"), style = MaterialTheme.typography.titleMedium, maxLines = 2,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                val g = s.strOrNull("grade")
                if (g != null) AssistChip(onClick = {}, label = { Text(g) })
                if (!s.optBoolean("is_public", true)) Icon(Icons.Default.Lock, contentDescription = "Riêng tư", modifier = Modifier.size(18.dp))
            }
            val desc = s.str("description")
            if (desc.isNotEmpty()) Text(desc, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
            val pv = s.str("preview")
            if (pv.isNotEmpty()) {
                Text(pv, maxLines = 4, overflow = TextOverflow.Ellipsis, fontFamily = FontFamily.Monospace, fontSize = 11.sp,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant).padding(8.dp))
            }
            val author = s.optJSONObject("author")?.str("username") ?: ""
            Text("${s.str("language_name")} · $author · ★ ${s.optInt("stars")} · 💬 ${s.optInt("comment_count")} · 👁 ${s.optInt("views")}",
                style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
fun SnippetDetail(id: Int, onBack: () -> Unit, onEdit: () -> Unit, onDeleted: () -> Unit) {
    val st = rememberLoad(id) { Api.request("GET", "snippets/$id").getJSONObject("snippet") }
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    var comment by remember { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf(false) }
    var actionError by remember { mutableStateOf<String?>(null) }
    val s = st.data

    Scaffold(topBar = {
        BackBar(s?.str("title") ?: "Đoạn code", onBack) {
            if (s != null) {
                IconButton(onClick = { clipboard.setText(AnnotatedString(s.str("code"))) }) {
                    Icon(Icons.Default.Share, contentDescription = "Sao chép code")
                }
                if (s.optJSONObject("permissions")?.optBoolean("can_edit") == true)
                    IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, contentDescription = "Sửa") }
                if (s.optJSONObject("permissions")?.optBoolean("can_delete") == true)
                    IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.Delete, contentDescription = "Xóa") }
            }
        }
    }) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            if (s == null) {
                if (st.loading) CenterLoading() else ErrorBox(st.error ?: "Không tải được.") { st.reload() }
            } else Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp).imePadding(),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val author = s.optJSONObject("author")
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Avatar(author?.strOrNull("avatar_url"), author?.str("username") ?: "?")
                    Column {
                        Text(author?.str("username") ?: "", style = MaterialTheme.typography.titleSmall)
                        Text("${s.str("language_name")} · ${shortDate(s.str("created_at"))}", style = MaterialTheme.typography.labelMedium)
                    }
                }
                Text(s.str("title"), style = MaterialTheme.typography.headlineSmall)
                if (s.str("description").isNotEmpty()) Text(s.str("description"))
                s.strOrNull("grade")?.let { g ->
                    Card { Column(Modifier.padding(12.dp)) {
                        Text("Điểm: $g (+${s.optInt("points")} điểm)", style = MaterialTheme.typography.titleSmall)
                        s.strOrNull("grade_note")?.let { Text(it) }
                    } }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    val starred = s.optBoolean("starred")
                    FilledTonalButton(onClick = {
                        scope.launch {
                            try { Api.request("POST", "snippets/$id/star"); st.reload() } catch (e: Exception) { actionError = errText(e) }
                        }
                    }) {
                        Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(18.dp),
                            tint = if (starred) androidx.compose.ui.graphics.Color(0xFFFFB300) else LocalContentColor.current)
                        Spacer(Modifier.width(6.dp))
                        Text("${s.optInt("stars")}")
                    }
                    Text("👁 ${s.optInt("views")}")
                }
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
                    .horizontalScroll(rememberScrollState()).padding(12.dp)) {
                    SelectionContainer {
                        Text(s.str("code"), fontFamily = FontFamily.Monospace, fontSize = 12.sp, softWrap = false)
                    }
                }
                val files = s.optJSONArray("files").objs()
                if (files.isNotEmpty()) {
                    Text("Tệp đính kèm", style = MaterialTheme.typography.titleSmall)
                    files.forEach { Text("📎 ${it.str("name")} (${it.optLong("size") / 1024} KB)") }
                }
                actionError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                HorizontalDivider()
                Text("Bình luận", style = MaterialTheme.typography.titleMedium)
                s.optJSONArray("comments").objs().forEach { c ->
                    val a = c.optJSONObject("author")
                    Row(Modifier.fillMaxWidth().padding(start = (minOf(c.optInt("depth"), 3) * 16).dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Avatar(a?.strOrNull("avatar_url"), a?.str("username") ?: "?", 28)
                        Column(Modifier.weight(1f)) {
                            Text("${a?.str("username")} · ${shortDate(c.str("created_at"))}", style = MaterialTheme.typography.labelMedium)
                            Text(c.str("body"))
                        }
                        if (c.optBoolean("can_delete")) IconButton(onClick = {
                            scope.launch {
                                try { Api.request("DELETE", "comments/${c.optInt("id")}"); st.reload() } catch (e: Exception) { actionError = errText(e) }
                            }
                        }) { Icon(Icons.Default.Delete, contentDescription = "Xóa bình luận", modifier = Modifier.size(18.dp)) }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(comment, { comment = it }, Modifier.weight(1f), placeholder = { Text("Viết bình luận…") })
                    IconButton(enabled = comment.isNotBlank(), onClick = {
                        scope.launch {
                            try {
                                Api.request("POST", "snippets/$id/comments", body = mapOf("body" to comment.trim()))
                                comment = ""; st.reload()
                            } catch (e: Exception) { actionError = errText(e) }
                        }
                    }) { Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Gửi") }
                }
            }
        }
    }

    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("Xóa đoạn code?") }, text = { Text("Hành động này không thể hoàn tác.") },
        confirmButton = {
            TextButton(onClick = {
                confirmDelete = false
                scope.launch {
                    try { Api.request("DELETE", "snippets/$id"); onDeleted() } catch (e: Exception) { actionError = errText(e) }
                }
            }) { Text("Xóa") }
        },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Hủy") } }
    )
}

@Composable
fun EditorScreen(id: Int?, onBack: () -> Unit, onSaved: (Int) -> Unit) {
    var title by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var lang by remember { mutableStateOf("") }
    var isPublic by remember { mutableStateOf(true) }
    var loaded by remember { mutableStateOf(id == null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var menu by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(id) {
        if (id != null) {
            try {
                val s = Api.request("GET", "snippets/$id").getJSONObject("snippet")
                title = s.str("title"); desc = s.str("description"); code = s.str("code")
                lang = s.str("language"); isPublic = s.optBoolean("is_public", true)
                loaded = true
            } catch (e: Exception) { error = errText(e) }
        }
    }
    LaunchedEffect(MetaStore.languages) {
        if (lang.isEmpty()) lang = MetaStore.languages.firstOrNull()?.first ?: ""
    }

    Scaffold(topBar = { BackBar(if (id == null) "Đăng code mới" else "Sửa code", onBack) }) { pad ->
        if (!loaded) {
            Box(Modifier.padding(pad)) { if (error != null) ErrorBox(error!!) else CenterLoading() }
        } else Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp).imePadding(),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(title, { title = it }, Modifier.fillMaxWidth(), label = { Text("Tiêu đề") }, singleLine = true)
            OutlinedTextField(desc, { desc = it }, Modifier.fillMaxWidth(), label = { Text("Mô tả (tùy chọn)") }, minLines = 2)
            Box {
                OutlinedButton(onClick = { menu = true }) {
                    Text("Ngôn ngữ: " + (MetaStore.languages.firstOrNull { it.first == lang }?.second ?: lang.ifEmpty { "chọn" }))
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    MetaStore.languages.forEach { (k, n) ->
                        DropdownMenuItem(text = { Text(n) }, onClick = { lang = k; menu = false })
                    }
                }
            }
            OutlinedTextField(code, { code = it }, Modifier.fillMaxWidth(), label = { Text("Code") }, minLines = 10,
                textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(isPublic, { isPublic = it }); Spacer(Modifier.width(8.dp)); Text(if (isPublic) "Công khai" else "Riêng tư")
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(enabled = !busy, modifier = Modifier.fillMaxWidth(), onClick = {
                scope.launch {
                    busy = true; error = null
                    try {
                        val body = mapOf("title" to title.trim(), "description" to desc.trim(), "language" to lang,
                            "code" to code, "is_public" to isPublic)
                        val d = if (id == null) Api.request("POST", "snippets", body = body)
                                else Api.request("PUT", "snippets/$id", body = body)
                        onSaved(d.getJSONObject("snippet").optInt("id"))
                    } catch (e: Exception) { error = errText(e) }
                    busy = false
                }
            }) { Text(if (id == null) "Đăng" else "Lưu thay đổi") }
        }
    }
}
