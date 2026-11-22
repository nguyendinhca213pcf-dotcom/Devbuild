package net.getenjoyment.devbuild

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable

class MainActivity : ComponentActivity() {
    private fun handleLogin(i: Intent?) {
        val d = i?.data ?: return
        if (d.scheme != "devbuild") return
        val token = d.getQueryParameter("token")
        if (!token.isNullOrEmpty() && Session.checkState(d.getQueryParameter("state"))) Session.save(token, null)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Session.init(applicationContext)
        Lang.init(applicationContext)
        handleLogin(intent)
        addOnNewIntentListener { handleLogin(it) }
        setContent {
            MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
                Surface(color = MaterialTheme.colorScheme.background) { Root() }
            }
        }
    }
}

@Composable
fun Root() {
    if (Session.token == null) AuthScreen() else MainScreen()
}
