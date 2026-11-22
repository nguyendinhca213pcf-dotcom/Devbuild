package net.getenjoyment.devbuild

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Ngôn ngữ giao diện. Mặc định là tiếng Anh. */
object Lang {
    private var prefs: SharedPreferences? = null
    var vi by mutableStateOf(false)

    fun init(ctx: Context) {
        prefs = ctx.getSharedPreferences("devbuild", Context.MODE_PRIVATE)
        vi = prefs?.getString("lang", "en") == "vi"
    }

    fun set(useVietnamese: Boolean) {
        vi = useVietnamese
        prefs?.edit()?.putString("lang", if (useVietnamese) "vi" else "en")?.apply()
    }
}

fun tr(vi: String, en: String): String = if (Lang.vi) vi else en
