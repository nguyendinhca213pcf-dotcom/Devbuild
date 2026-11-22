@file:OptIn(ExperimentalMaterial3Api::class)

package net.getenjoyment.devbuild

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier

sealed class Screen {
    object Tabs : Screen()
    data class Snippet(val id: Int) : Screen()
    data class Editor(val id: Int?) : Screen()
    data class News(val id: Int) : Screen()
    data class Page(val name: String) : Screen()
    data class Thread(val id: Int) : Screen()
    data class Run(val idx: Int) : Screen()
}

@Composable
fun MainScreen() {
    val stack = remember { mutableStateListOf<Screen>(Screen.Tabs) }
    var refreshKey by remember { mutableIntStateOf(0) }
    var tab by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        try { MetaStore.load() } catch (e: Exception) { }
        if (Session.me == null) {
            try { Session.me = Api.request("GET", "me").optJSONObject("user") } catch (e: Exception) { }
        }
    }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(35000)
            try { Api.request("POST", "quests/heartbeat") } catch (e: Exception) { }
        }
    }
    BackHandler(enabled = stack.size > 1) { stack.removeAt(stack.lastIndex) }

    fun back() { if (stack.size > 1) stack.removeAt(stack.lastIndex) }

    Box(Modifier.fillMaxSize()) {
        val titles = listOf("Code", tr("Tin tức", "News"), tr("Xếp hạng", "Ranking"), tr("Thông báo", "Alerts"), tr("Tôi", "Me"))
        Scaffold(
            topBar = { TopAppBar(title = { Text(titles[tab]) }) },
            bottomBar = {
                NavigationBar {
                    val icons = listOf(Icons.Default.Home, Icons.Default.Info, Icons.Default.Star, Icons.Default.Notifications, Icons.Default.Person)
                    titles.forEachIndexed { i, t ->
                        NavigationBarItem(selected = tab == i, onClick = { tab = i },
                            icon = { Icon(icons[i], contentDescription = t) }, label = { Text(t) })
                    }
                }
            },
            floatingActionButton = {
                if (tab == 0) FloatingActionButton(onClick = { stack.add(Screen.Editor(null)) }) {
                    Icon(Icons.Default.Add, contentDescription = tr("Đăng code", "Post code"))
                }
            }
        ) { pad ->
            Box(Modifier.padding(pad)) {
                when (tab) {
                    0 -> SnippetsTab(refreshKey) { stack.add(Screen.Snippet(it)) }
                    1 -> NewsTab { stack.add(Screen.News(it)) }
                    2 -> LeaderboardTab()
                    3 -> NotificationsTab { stack.add(Screen.Snippet(it)) }
                    else -> MeTab { stack.add(it) }
                }
            }
        }
        when (val top = stack.last()) {
            is Screen.Tabs -> {}
            is Screen.Snippet -> Surface(Modifier.fillMaxSize()) {
                SnippetDetail(top.id, onBack = { back() },
                    onEdit = { stack.add(Screen.Editor(top.id)) },
                    onDeleted = { refreshKey++; back() })
            }
            is Screen.Editor -> Surface(Modifier.fillMaxSize()) {
                EditorScreen(top.id, onBack = { back() }, onSaved = { id ->
                    refreshKey++
                    back()
                    if (top.id == null) stack.add(Screen.Snippet(id))
                })
            }
            is Screen.News -> Surface(Modifier.fillMaxSize()) { NewsDetail(top.id, onBack = { back() }) }
            is Screen.Page -> Surface(Modifier.fillMaxSize()) {
                when (top.name) {
                    "mail" -> MailScreen(onBack = { back() }, onOpen = { stack.add(Screen.Thread(it)) })
                    "quests" -> QuestsScreen { back() }
                    "shop" -> ShopScreen { back() }
                    "account" -> AccountScreen { back() }
                    else -> ConsoleScreen(onBack = { back() }, onRun = { stack.add(Screen.Run(it)) })
                }
            }
            is Screen.Thread -> Surface(Modifier.fillMaxSize()) { MailThreadScreen(top.id) { back() } }
            is Screen.Run -> Surface(Modifier.fillMaxSize()) { RunnerScreen(top.idx) { back() } }
        }
    }
}

@Composable
fun BackBar(title: String, onBack: () -> Unit, actions: @Composable RowScope.() -> Unit = {}) {
    TopAppBar(
        title = { Text(title, maxLines = 1) },
        navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = tr("Quay lại", "Back")) } },
        actions = actions
    )
}
