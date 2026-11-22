package net.getenjoyment.devbuild

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import androidx.compose.runtime.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

const val SITE_ROOT = "http://devbuild.getenjoyment.net"
const val SITE_DIR = "http://devbuild.getenjoyment.net/devbuild/"

fun absUrl(u: String): String = when {
    u.startsWith("http") -> u
    u.startsWith("/") -> SITE_ROOT + u
    else -> SITE_DIR + u
}

fun JSONObject.str(k: String): String = if (isNull(k)) "" else optString(k, "")
fun JSONObject.strOrNull(k: String): String? = if (isNull(k)) null else optString(k, "").ifEmpty { null }
fun JSONArray?.objs(): List<JSONObject> {
    if (this == null) return emptyList()
    val l = ArrayList<JSONObject>()
    for (i in 0 until length()) optJSONObject(i)?.let { l.add(it) }
    return l
}

class ApiException(
    val status: Int,
    val error: String,
    override val message: String,
    val fields: Map<String, String> = emptyMap()
) : Exception(message)

fun errText(e: Throwable): String = when (e) {
    is ApiException -> if (e.fields.isNotEmpty()) e.fields.values.joinToString("\n") else e.message
    is IOException -> tr("Không kết nối được máy chủ. Kiểm tra mạng rồi thử lại.", "Cannot reach the server. Check your connection and try again.")
    else -> e.message ?: tr("Đã xảy ra lỗi.", "Something went wrong.")
}

object Session {
    private var prefs: SharedPreferences? = null
    var token by mutableStateOf<String?>(null)
    var me by mutableStateOf<JSONObject?>(null)

    fun init(ctx: Context) {
        prefs = ctx.getSharedPreferences("devbuild", Context.MODE_PRIVATE)
        token = prefs?.getString("token", null)
    }

    fun save(t: String, user: JSONObject?) {
        prefs?.edit()?.putString("token", t)?.apply()
        me = user
        token = t
    }

    fun beginWebLogin(): String {
        val s = java.util.UUID.randomUUID().toString().replace("-", "")
        prefs?.edit()?.putString("state", s)?.apply()
        return s
    }

    fun checkState(s: String?): Boolean = s != null && s == prefs?.getString("state", null)

    fun clear() {
        prefs?.edit()?.remove("token")?.apply()
        me = null
        token = null
    }
}

object Api {
    private const val BASE = SITE_DIR + "api/v1/index.php"

    suspend fun request(
        method: String,
        path: String,
        query: Map<String, Any?> = emptyMap(),
        body: Map<String, Any?>? = null
    ): JSONObject = withContext(Dispatchers.IO) {
        val sb = StringBuilder(BASE).append("?r=").append(path)
        for ((k, v) in query) {
            if (v != null && v.toString().isNotEmpty()) {
                sb.append('&').append(Uri.encode(k)).append('=').append(Uri.encode(v.toString()))
            }
        }
        val conn = URL(sb.toString()).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 20000
            conn.readTimeout = 30000
            conn.setRequestProperty("Accept", "application/json")
            conn.setRequestProperty("User-Agent", "DevBuildApp/1.0 (Android)")
            Session.token?.let { conn.setRequestProperty("Authorization", "Bearer $it") }
            if (method == "GET") {
                conn.requestMethod = "GET"
            } else {
                conn.requestMethod = "POST"
                if (method != "POST") conn.setRequestProperty("X-Http-Method-Override", method)
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                val json = JSONObject()
                body?.forEach { (k, v) -> json.put(k, v ?: JSONObject.NULL) }
                conn.outputStream.use { it.write(json.toString().toByteArray(Charsets.UTF_8)) }
            }
            val code = conn.responseCode
            val stream = if (code >= 400) conn.errorStream else conn.inputStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            val json = try {
                JSONObject(text)
            } catch (e: Exception) {
                throw ApiException(code, "bad_response", tr("Máy chủ trả về dữ liệu không hợp lệ (HTTP $code).", "The server returned invalid data (HTTP $code)."))
            }
            if (!json.optBoolean("ok", false)) {
                val fields = HashMap<String, String>()
                json.optJSONObject("fields")?.let { f ->
                    for (k in f.keys()) fields[k] = f.optString(k)
                }
                val err = json.optString("error", "error")
                if (code == 401 && err == "invalid_token") Session.clear()
                throw ApiException(code, err, json.optString("message", tr("Lỗi ($code)", "Error ($code)")), fields)
            }
            json.optJSONObject("data") ?: JSONObject()
        } finally {
            conn.disconnect()
        }
    }
}

object MetaStore {
    var languages by mutableStateOf(listOf<Pair<String, String>>())
    var periods by mutableStateOf(listOf<Pair<String, String>>())

    suspend fun load() {
        val d = Api.request("GET", "meta")
        languages = d.optJSONArray("languages").objs().map { it.str("key") to it.str("name") }
        periods = d.optJSONArray("leaderboard_periods").objs().map { it.str("key") to it.str("name") }
    }
}

class LoadState<T> {
    var data by mutableStateOf<T?>(null)
    var error by mutableStateOf<String?>(null)
    var loading by mutableStateOf(true)
    var reload: () -> Unit = {}
}

@Composable
fun <T> rememberLoad(key: Any?, block: suspend () -> T): LoadState<T> {
    val st = remember(key) { LoadState<T>() }
    var tick by remember(key) { mutableIntStateOf(0) }
    st.reload = { tick++ }
    LaunchedEffect(key, tick) {
        st.loading = true
        st.error = null
        try {
            st.data = block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            st.error = errText(e)
        }
        st.loading = false
    }
    return st
}
