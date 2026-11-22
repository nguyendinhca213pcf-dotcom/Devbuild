@file:OptIn(ExperimentalMaterial3Api::class)

package net.getenjoyment.devbuild

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import org.json.JSONObject

@Composable
fun NewsTab(onOpen: (Int) -> Unit) {
    val st = rememberLoad(Unit) { Api.request("GET", "news").optJSONArray("posts").objs() }
    val posts = st.data
    when {
        posts == null -> if (st.loading) CenterLoading() else ErrorBox(st.error ?: tr("Lỗi", "Error")) { st.reload() }
        posts.isEmpty() -> EmptyText(tr("Chưa có tin tức.", "No news yet."))
        else -> LazyColumn(Modifier.fillMaxSize()) {
            items(posts, key = { it.optInt("id") }) { p ->
                Card(onClick = { onOpen(p.optInt("id")) }, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
                    Column {
                        p.strOrNull("cover_url")?.let {
                            AsyncImage(model = absUrl(it), contentDescription = null, contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxWidth().height(160.dp))
                        }
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text((if (p.optBoolean("pinned")) "📌 " else "") + p.str("title"), style = MaterialTheme.typography.titleMedium)
                            if (p.str("summary").isNotEmpty()) Text(p.str("summary"), maxLines = 3, overflow = TextOverflow.Ellipsis)
                            Text("${shortDate(p.str("published_at"))} · 👁 ${p.optInt("views")}", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun NewsDetail(id: Int, onBack: () -> Unit) {
    val st = rememberLoad(id) { Api.request("GET", "news/$id").getJSONObject("post") }
    val ctx = LocalContext.current
    fun openLink(u: String) {
        try { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(absUrl(u)))) } catch (e: Exception) { }
    }
    val p = st.data
    Scaffold(topBar = { BackBar(p?.str("title") ?: tr("Tin tức", "News"), onBack) }) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            if (p == null) {
                if (st.loading) CenterLoading() else ErrorBox(st.error ?: tr("Lỗi", "Error")) { st.reload() }
            } else Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(p.str("title"), style = MaterialTheme.typography.headlineSmall)
                Text("${p.str("author")} · ${shortDate(p.str("published_at"))}", style = MaterialTheme.typography.labelMedium)
                p.optJSONArray("blocks").objs().forEach { b ->
                    when (b.str("type")) {
                        "text" -> Text(b.str("text"))
                        "image" -> Column {
                            AsyncImage(model = absUrl(b.str("url")), contentDescription = b.str("caption"),
                                modifier = Modifier.fillMaxWidth(), contentScale = ContentScale.FillWidth)
                            if (b.str("caption").isNotEmpty()) Text(b.str("caption"), style = MaterialTheme.typography.labelMedium)
                        }
                        "video" -> OutlinedButton(onClick = { openLink(b.str("url")) }) { Text(tr("▶ Xem video", "▶ Watch video")) }
                        "file" -> OutlinedButton(onClick = { openLink(b.str("download_url")) }) { Text("⬇ ${b.str("name")}") }
                    }
                }
            }
        }
    }
}

@Composable
fun LeaderboardTab() {
    var period by remember { mutableStateOf("all") }
    val st = rememberLoad(period) { Api.request("GET", "leaderboard", query = mapOf("period" to period, "per_page" to 50)) }
    Column(Modifier.fillMaxSize()) {
        if (MetaStore.periods.isNotEmpty()) {
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MetaStore.periods.forEach { (k, n) -> FilterChip(selected = period == k, onClick = { period = k }, label = { Text(n) }) }
            }
        }
        val d = st.data
        if (d == null) {
            if (st.loading) CenterLoading() else ErrorBox(st.error ?: tr("Lỗi", "Error")) { st.reload() }
        } else {
            val leaders = d.optJSONArray("leaders").objs()
            if (leaders.isEmpty()) EmptyText(tr("Chưa có ai trên bảng xếp hạng.", "Nobody on the leaderboard yet."))
            LazyColumn(Modifier.fillMaxSize()) {
                d.optJSONObject("me")?.let { me ->
                    item {
                        Text(tr("Hạng của bạn: #${me.optInt("rank")} · ${me.optInt("points")} điểm", "Your rank: #${me.optInt("rank")} · ${me.optInt("points")} points"),
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), fontWeight = FontWeight.Bold)
                    }
                }
                items(leaders) { r ->
                    val u = r.optJSONObject("user")
                    ListItem(
                        leadingContent = {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("#${r.optInt("rank")}", style = MaterialTheme.typography.titleSmall)
                                Avatar(u?.strOrNull("avatar_url"), u?.str("username") ?: "?")
                            }
                        },
                        headlineContent = { Text(u?.str("username") ?: "") },
                        supportingContent = { Text(tr("${r.optInt("graded")} bài được chấm", "${r.optInt("graded")} graded")) },
                        trailingContent = { Text(tr("${r.optInt("points")} điểm", "${r.optInt("points")} pts"), fontWeight = FontWeight.Bold) }
                    )
                }
            }
        }
    }
}

@Composable
fun NotificationsTab(onOpenSnippet: (Int) -> Unit) {
    val st = rememberLoad(Unit) { Api.request("GET", "notifications", query = mapOf("per_page" to 50)) }
    val scope = rememberCoroutineScope()
    val d = st.data
    if (d == null) {
        if (st.loading) CenterLoading() else ErrorBox(st.error ?: tr("Lỗi", "Error")) { st.reload() }
        return
    }
    val list = d.optJSONArray("notifications").objs()
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            Text(tr("${d.optInt("unread")} chưa đọc", "${d.optInt("unread")} unread"))
            TextButton(onClick = {
                scope.launch { try { Api.request("POST", "notifications/read"); st.reload() } catch (e: Exception) { } }
            }) { Text(tr("Đọc hết", "Mark all read")) }
        }
        if (list.isEmpty()) EmptyText(tr("Không có thông báo.", "No notifications."))
        LazyColumn(Modifier.fillMaxSize()) {
            items(list, key = { it.optInt("id") }) { n ->
                val unread = !n.optBoolean("read")
                ListItem(
                    modifier = Modifier.clickable {
                        scope.launch {
                            try { Api.request("POST", "notifications/${n.optInt("id")}/read") } catch (e: Exception) { }
                            st.reload()
                        }
                        val t = n.optJSONObject("target")
                        if (t != null && t.str("type") == "snippet") onOpenSnippet(t.optInt("snippet_id"))
                    },
                    headlineContent = { Text(n.str("title"), fontWeight = if (unread) FontWeight.Bold else FontWeight.Normal) },
                    supportingContent = { Text(n.str("body"), maxLines = 3, overflow = TextOverflow.Ellipsis) },
                    overlineContent = { Text(shortDate(n.str("created_at"))) }
                )
            }
        }
    }
}

@Composable
fun ProfileTab() {
    val st = rememberLoad(Unit) { Api.request("GET", "me").getJSONObject("user") }
    val scope = rememberCoroutineScope()
    val u: JSONObject? = st.data ?: Session.me
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        if (u == null) {
            if (st.loading) CenterLoading() else ErrorBox(st.error ?: tr("Lỗi", "Error")) { st.reload() }
        } else {
            Avatar(u.strOrNull("avatar_url"), u.str("username"), 88)
            Text(u.str("username"), style = MaterialTheme.typography.headlineSmall)
            Text(u.str("role_label"), style = MaterialTheme.typography.labelLarge)
            Text(u.str("email"))
            if (u.has("points")) Text(tr("${u.optInt("points")} điểm · ${u.optInt("graded_snippets")} bài được chấm", "${u.optInt("points")} points · ${u.optInt("graded_snippets")} graded"))
            if (u.optJSONObject("pro")?.optBoolean("active") == true)
                Text(tr("⭐ Pro đến ${u.optJSONObject("pro")?.str("until")}", "⭐ Pro until ${u.optJSONObject("pro")?.str("until")}"))
        }
        Spacer(Modifier.height(8.dp))
        LanguageSwitcher()
        Spacer(Modifier.height(16.dp))
        Button(onClick = {
            scope.launch {
                try { Api.request("POST", "auth/logout") } catch (e: Exception) { }
                Session.clear()
            }
        }) { Text(tr("Đăng xuất", "Log out")) }
    }
}
