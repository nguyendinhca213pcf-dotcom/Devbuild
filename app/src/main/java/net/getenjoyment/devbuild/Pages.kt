@file:OptIn(ExperimentalMaterial3Api::class)

package net.getenjoyment.devbuild

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import org.json.JSONObject

/* ---------------------------- Thư ---------------------------- */

@Composable
fun MailScreen(onBack: () -> Unit, onOpen: (Int) -> Unit) {
    val st = rememberLoad(Unit) { Api.request("GET", "mail") }
    var compose by remember { mutableStateOf(false) }
    Scaffold(
        topBar = { BackBar(tr("Thư", "Mail"), onBack) },
        floatingActionButton = {
            FloatingActionButton(onClick = { compose = true }) { Icon(Icons.Default.Edit, contentDescription = tr("Soạn thư", "Compose")) }
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            val d = st.data
            if (d == null) {
                if (st.loading) CenterLoading() else ErrorBox(st.error ?: tr("Lỗi", "Error")) { st.reload() }
            } else {
                val threads = d.optJSONArray("threads").objs()
                if (threads.isEmpty()) EmptyText(tr("Hộp thư trống.", "Your inbox is empty."))
                LazyColumn(Modifier.fillMaxSize()) {
                    items(threads) { t ->
                        ListItem(
                            modifier = Modifier.clickable { onOpen(t.optInt("thread_id")) },
                            overlineContent = { Text(t.str("with")) },
                            headlineContent = {
                                Text(t.str("subject"), fontWeight = if (t.optInt("unread") > 0) FontWeight.Bold else FontWeight.Normal)
                            },
                            supportingContent = { Text(t.optJSONObject("last")?.str("excerpt") ?: "", maxLines = 2, overflow = TextOverflow.Ellipsis) }
                        )
                    }
                }
            }
        }
    }
    if (compose) {
        var to by remember { mutableStateOf("") }
        var subject by remember { mutableStateOf("") }
        var body by remember { mutableStateOf("") }
        var err by remember { mutableStateOf<String?>(null) }
        val scope = rememberCoroutineScope()
        AlertDialog(
            onDismissRequest = { compose = false },
            title = { Text(tr("Thư mới", "New message")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(to, { to = it }, label = { Text(tr("Gửi tới (tên đăng nhập)", "To (username)")) }, singleLine = true)
                    OutlinedTextField(subject, { subject = it }, label = { Text(tr("Tiêu đề", "Subject")) }, singleLine = true)
                    OutlinedTextField(body, { body = it }, label = { Text(tr("Nội dung", "Message")) }, minLines = 3)
                    err?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        try {
                            Api.request("POST", "mail", body = mapOf("to" to to.trim(), "subject" to subject.trim(), "body" to body))
                            compose = false; st.reload()
                        } catch (e: Exception) { err = errText(e) }
                    }
                }) { Text(tr("Gửi", "Send")) }
            },
            dismissButton = { TextButton(onClick = { compose = false }) { Text(tr("Hủy", "Cancel")) } }
        )
    }
}

@Composable
fun MailThreadScreen(id: Int, onBack: () -> Unit) {
    val st = rememberLoad(id) { Api.request("GET", "mail/$id") }
    var text by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val d = st.data
    Scaffold(topBar = {
        BackBar(d?.str("subject") ?: tr("Thư", "Mail"), onBack) {
            IconButton(onClick = {
                scope.launch { try { Api.request("DELETE", "mail/$id"); onBack() } catch (e: Exception) { err = errText(e) } }
            }) { Icon(Icons.Default.Delete, contentDescription = tr("Xóa", "Delete")) }
        }
    }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().imePadding()) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (d == null) {
                    if (st.loading) CenterLoading() else ErrorBox(st.error ?: tr("Lỗi", "Error")) { st.reload() }
                } else LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(d.optJSONArray("messages").objs()) { m ->
                        val mine = m.optBoolean("from_me")
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
                            Card(colors = CardDefaults.cardColors(
                                containerColor = if (mine) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)) {
                                Column(Modifier.padding(10.dp)) {
                                    Text(m.optJSONObject("sender")?.str("username") ?: "", style = MaterialTheme.typography.labelSmall)
                                    Text(m.str("body"))
                                    Text(shortDate(m.str("created_at")), style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
            }
            err?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 12.dp)) }
            Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(text, { text = it }, Modifier.weight(1f), placeholder = { Text(tr("Trả lời…", "Reply…")) })
                IconButton(enabled = text.isNotBlank(), onClick = {
                    scope.launch {
                        try {
                            Api.request("POST", "mail/$id/reply", body = mapOf("body" to text))
                            text = ""; err = null; st.reload()
                        } catch (e: Exception) { err = errText(e) }
                    }
                }) { Icon(Icons.AutoMirrored.Filled.Send, contentDescription = tr("Gửi", "Send")) }
            }
        }
    }
}

/* ------------------------- Nhiệm vụ ------------------------- */

@Composable
fun QuestsScreen(onBack: () -> Unit) {
    val st = rememberLoad(Unit) { Api.request("GET", "quests") }
    val scope = rememberCoroutineScope()
    var msg by remember { mutableStateOf<String?>(null) }
    fun act(path: String) {
        scope.launch {
            try { msg = Api.request("POST", path).str("message"); st.reload() } catch (e: Exception) { msg = errText(e) }
        }
    }
    Scaffold(topBar = { BackBar(tr("Nhiệm vụ", "Quests"), onBack) }) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            val d = st.data
            if (d == null) {
                if (st.loading) CenterLoading() else ErrorBox(st.error ?: tr("Lỗi", "Error")) { st.reload() }
            } else LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    Text("Orbs: ${d.opt("orbs")}", style = MaterialTheme.typography.titleMedium)
                    msg?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
                }
                items(d.optJSONArray("quests").objs()) { q ->
                    val pct = q.optInt("percent")
                    val state = q.str("state")
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("${q.str("icon")} ${q.str("title")}", style = MaterialTheme.typography.titleMedium)
                            if (q.str("description").isNotEmpty()) Text(q.str("description"))
                            LinearProgressIndicator(progress = pct / 100f, modifier = Modifier.fillMaxWidth())
                            Text("${q.optInt("progress")}/${q.optInt("target")} ${q.str("unit")} · $state", style = MaterialTheme.typography.labelMedium)
                            val r = q.optJSONObject("reward")
                            val parts = ArrayList<String>()
                            if ((r?.optInt("orbs") ?: 0) > 0) parts.add("${r?.optInt("orbs")} orbs")
                            if ((r?.optInt("pro_days") ?: 0) > 0) parts.add("${r?.optInt("pro_days")} Pro")
                            r?.optJSONObject("frame")?.let { parts.add(tr("Khung ", "Frame ") + it.str("name")) }
                            if (parts.isNotEmpty()) Text(tr("Thưởng: ", "Reward: ") + parts.joinToString(", "), style = MaterialTheme.typography.labelMedium)
                            val id = q.optInt("id")
                            if (state != "claimed") {
                                if (pct >= 100) Button(onClick = { act("quests/$id/claim") }) { Text(tr("Nhận thưởng", "Claim")) }
                                else if (state != "accepted") OutlinedButton(onClick = { act("quests/$id/accept") }) { Text(tr("Nhận nhiệm vụ", "Accept")) }
                            }
                        }
                    }
                }
            }
        }
    }
}

/* --------------------------- Shop --------------------------- */

@Composable
fun ShopScreen(onBack: () -> Unit) {
    val st = rememberLoad(Unit) { Api.request("GET", "shop") }
    val scope = rememberCoroutineScope()
    var msg by remember { mutableStateOf<String?>(null) }
    fun act(path: String, body: Map<String, Any?>? = null) {
        scope.launch {
            try { msg = Api.request("POST", path, body = body).str("message"); st.reload() } catch (e: Exception) { msg = errText(e) }
        }
    }
    Scaffold(topBar = { BackBar(tr("Cửa hàng khung", "Frame shop"), onBack) }) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            val d = st.data
            if (d == null) {
                if (st.loading) CenterLoading() else ErrorBox(st.error ?: tr("Lỗi", "Error")) { st.reload() }
            } else LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    Text("Orbs: ${d.opt("orbs")}", style = MaterialTheme.typography.titleMedium)
                    msg?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
                }
                for (kind in listOf("avatar", "snippet")) {
                    item { Text(if (kind == "avatar") tr("Khung avatar", "Avatar frames") else tr("Khung bài code", "Snippet frames"),
                        style = MaterialTheme.typography.titleSmall) }
                    items(d.optJSONArray(kind).objs()) { f ->
                        Card(Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                val img = f.strOrNull("image_url")
                                if (img != null) AsyncImage(model = absUrl(img), contentDescription = f.str("name"), modifier = Modifier.size(56.dp),
                                    contentScale = ContentScale.Fit)
                                else Text(f.strOrNull("preset") ?: "🖼", modifier = Modifier.width(56.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(f.str("name"), style = MaterialTheme.typography.titleSmall)
                                    if (f.str("description").isNotEmpty()) Text(f.str("description"), maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    Text("${f.optInt("price")} orbs", style = MaterialTheme.typography.labelMedium)
                                }
                                val fid = f.optInt("id")
                                when {
                                    f.optBoolean("equipped") -> OutlinedButton(onClick = { act("me/frames/unequip", mapOf("kind" to kind)) }) { Text(tr("Bỏ", "Unequip")) }
                                    f.optBoolean("owned") -> Button(onClick = { act("me/frames/$fid/equip") }) { Text(tr("Dùng", "Equip")) }
                                    else -> Button(onClick = { act("shop/frames/$fid/buy") }) { Text(tr("Mua", "Buy")) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/* ------------------------- Tài khoản ------------------------- */

@Composable
fun AccountScreen(onBack: () -> Unit) {
    val sessions = rememberLoad(Unit) { Api.request("GET", "auth/sessions").optJSONArray("sessions").objs() }
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    var curPw by remember { mutableStateOf("") }
    var newPw by remember { mutableStateOf("") }
    var newName by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) scope.launch {
            val b64 = readImageBase64(ctx, uri)
            if (b64 == null) { msg = tr("Không đọc được ảnh.", "Could not read the image."); return@launch }
            try {
                Api.request("POST", "me/avatar", body = mapOf("avatar_base64" to b64))
                Session.me = Api.request("GET", "me").optJSONObject("user")
                msg = tr("Đã cập nhật avatar.", "Avatar updated.")
            } catch (e: Exception) { msg = errText(e) }
        }
    }
    fun call(method: String, path: String, body: Map<String, Any?>? = null, ok: String, after: () -> Unit = {}) {
        scope.launch {
            try { Api.request(method, path, body = body); msg = ok; after() } catch (e: Exception) { msg = errText(e) }
        }
    }
    Scaffold(topBar = { BackBar(tr("Tài khoản & bảo mật", "Account & security"), onBack) }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp).imePadding(),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            msg?.let { Text(it, color = MaterialTheme.colorScheme.primary) }

            Text(tr("Avatar", "Avatar"), style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { picker.launch("image/*") }) { Text(tr("Chọn ảnh", "Choose image")) }
                OutlinedButton(onClick = {
                    call("DELETE", "me/avatar", ok = tr("Đã xóa avatar.", "Avatar removed.")) {
                        scope.launch { try { Session.me = Api.request("GET", "me").optJSONObject("user") } catch (e: Exception) { } }
                    }
                }) { Text(tr("Xóa avatar", "Remove")) }
            }

            HorizontalDivider()
            Text(tr("Đổi tên đăng nhập", "Change username"), style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(newName, { newName = it }, Modifier.fillMaxWidth(), label = { Text(tr("Tên mới", "New username")) }, singleLine = true)
            Button(enabled = newName.isNotBlank(), onClick = {
                call("POST", "me/username", mapOf("username" to newName.trim()), tr("Đã đổi tên.", "Username changed.")) {
                    scope.launch { try { Session.me = Api.request("GET", "me").optJSONObject("user") } catch (e: Exception) { } }
                }
            }) { Text(tr("Đổi tên", "Change")) }

            HorizontalDivider()
            Text(tr("Đổi mật khẩu", "Change password"), style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(curPw, { curPw = it }, Modifier.fillMaxWidth(), label = { Text(tr("Mật khẩu hiện tại", "Current password")) },
                singleLine = true, visualTransformation = PasswordVisualTransformation())
            OutlinedTextField(newPw, { newPw = it }, Modifier.fillMaxWidth(), label = { Text(tr("Mật khẩu mới", "New password")) },
                singleLine = true, visualTransformation = PasswordVisualTransformation())
            Button(enabled = curPw.isNotEmpty() && newPw.isNotEmpty(), onClick = {
                call("POST", "me/password", mapOf("current" to curPw, "new" to newPw), tr("Đã đổi mật khẩu.", "Password changed.")) {
                    curPw = ""; newPw = ""
                }
            }) { Text(tr("Đổi mật khẩu", "Change password")) }

            HorizontalDivider()
            Text(tr("Phiên đăng nhập", "Sessions"), style = MaterialTheme.typography.titleMedium)
            val list = sessions.data
            if (list == null) { if (sessions.loading) CenterLoading() else ErrorBox(sessions.error ?: tr("Lỗi", "Error")) { sessions.reload() } }
            else {
                list.forEach { s ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(s.str("device") + if (s.optBoolean("current")) tr(" (máy này)", " (this device)") else "")
                            Text(tr("Lần cuối: ", "Last used: ") + shortDate(s.str("last_used_at")), style = MaterialTheme.typography.labelMedium)
                        }
                        if (!s.optBoolean("current")) IconButton(onClick = {
                            call("DELETE", "auth/sessions/${s.optInt("id")}", ok = tr("Đã thu hồi.", "Revoked.")) { sessions.reload() }
                        }) { Icon(Icons.Default.Delete, contentDescription = tr("Thu hồi", "Revoke")) }
                    }
                }
                OutlinedButton(onClick = {
                    call("DELETE", "auth/sessions", ok = tr("Đã đăng xuất các thiết bị khác.", "Other devices signed out.")) { sessions.reload() }
                }) { Text(tr("Đăng xuất thiết bị khác", "Sign out other devices")) }
            }
        }
    }
}

class Entry(val title: String, val icon: ImageVector, val click: () -> Unit)

/* ------------------------ Tab "Tôi" ------------------------ */

@Composable
fun MeTab(onNav: (Screen) -> Unit) {
    val st = rememberLoad(Unit) { Api.request("GET", "me").getJSONObject("user") }
    LaunchedEffect(st.data) { st.data?.let { Session.me = it } }
    val scope = rememberCoroutineScope()
    val u: JSONObject? = st.data ?: Session.me
    fun go(method: String, path: String) { val i = epIndex(method, path); if (i >= 0) onNav(Screen.Run(i)) }
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (u == null) {
                    if (st.loading) CenterLoading() else ErrorBox(st.error ?: tr("Lỗi", "Error")) { st.reload() }
                } else {
                    Avatar(u.strOrNull("avatar_url"), u.str("username"), 88)
                    Text(u.str("username"), style = MaterialTheme.typography.headlineSmall)
                    Text(u.str("role_label"), style = MaterialTheme.typography.labelLarge)
                    Text(u.str("email"))
                    Text(tr("${u.optInt("points")} điểm · ${u.optInt("graded_snippets")} bài được chấm · Orbs ${u.opt("orbs")}",
                        "${u.optInt("points")} points · ${u.optInt("graded_snippets")} graded · Orbs ${u.opt("orbs")}"))
                    if (u.optJSONObject("pro")?.optBoolean("active") == true)
                        Text(tr("⭐ Pro đến ${u.optJSONObject("pro")?.str("until")}", "⭐ Pro until ${u.optJSONObject("pro")?.str("until")}"))
                }
                LanguageSwitcher()
            }
        }
        val entries = listOf(
            Entry(tr("Thư", "Mail"), Icons.Default.Email) { onNav(Screen.Page("mail")) },
            Entry(tr("Nhiệm vụ", "Quests"), Icons.Default.CheckCircle) { onNav(Screen.Page("quests")) },
            Entry(tr("Cửa hàng khung", "Frame shop"), Icons.Default.ShoppingCart) { onNav(Screen.Page("shop")) },
            Entry(tr("Tài khoản & bảo mật", "Account & security"), Icons.Default.Settings) { onNav(Screen.Page("account")) },
            Entry(tr("Nhập mã (Redeem)", "Redeem a code"), Icons.Default.Favorite) { go("POST", "redeem") },
            Entry(tr("Gói Pro", "Pro plans"), Icons.Default.Star) { go("GET", "pro") },
            Entry(tr("Thông báo chung", "Announcements"), Icons.Default.Info) { go("GET", "announcements") },
            Entry(tr("Trợ giúp / Báo cáo", "Help / Reports"), Icons.Default.Call) { go("GET", "help") },
            Entry(tr("Tất cả API" + if (u?.optBoolean("is_staff") == true) " + Admin" else "", "API console" + if (u?.optBoolean("is_staff") == true) " + Admin" else ""),
                Icons.Default.Build) { onNav(Screen.Page("console")) }
        )
        items(entries) { e ->
            ListItem(modifier = Modifier.clickable { e.click() }, headlineContent = { Text(e.title) },
                leadingContent = { Icon(e.icon, contentDescription = null) })
        }
        item {
            Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                Button(onClick = {
                    scope.launch { try { Api.request("POST", "auth/logout") } catch (e: Exception) { }; Session.clear() }
                }) { Text(tr("Đăng xuất", "Log out")) }
            }
        }
    }
}
