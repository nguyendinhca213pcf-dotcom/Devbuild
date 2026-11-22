@file:OptIn(ExperimentalMaterial3Api::class)

package net.getenjoyment.devbuild

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.json.JSONObject

private fun deviceName() = "Android " + Build.MODEL

@Composable
fun AuthScreen() {
    var mode by remember { mutableStateOf("login") }
    var identity by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var birth by remember { mutableStateOf("") }
    var accept by remember { mutableStateOf(false) }
    var code by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var info by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current

    fun finish(d: JSONObject) = Session.save(d.str("token"), d.optJSONObject("user"))

    fun submit() {
        scope.launch {
            busy = true; error = null; info = null
            try {
                when (mode) {
                    "login" -> finish(Api.request("POST", "auth/login", body = mapOf(
                        "identity" to identity.trim(), "password" to password, "device" to deviceName())))
                    "register" -> {
                        val d = Api.request("POST", "auth/register", body = mapOf(
                            "username" to username.trim(), "email" to email.trim(), "password" to password,
                            "birth_date" to birth.trim(), "accept_terms" to accept, "device" to deviceName()))
                        if (d.optBoolean("verification_required")) {
                            mode = "verify"
                            info = tr("Mã xác minh đã gửi tới ${d.str("email")}.", "Verification code sent to ${d.str("email")}.")
                        } else finish(d)
                    }
                    else -> finish(Api.request("POST", "auth/verify", body = mapOf(
                        "email" to email.trim(), "code" to code.trim(), "device" to deviceName())))
                }
            } catch (e: Exception) {
                error = errText(e)
            }
            busy = false
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp).imePadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { LanguageSwitcher() }
        Spacer(Modifier.height(16.dp))
        Text("DevBuild", style = MaterialTheme.typography.headlineLarge)
        Text(tr("Chia sẻ & chấm điểm code", "Share & grade code"), style = MaterialTheme.typography.bodyMedium)
        if (mode != "verify") {
            TabRow(selectedTabIndex = if (mode == "login") 0 else 1) {
                Tab(selected = mode == "login", onClick = { mode = "login"; error = null }, text = { Text(tr("Đăng nhập", "Log in")) })
                Tab(selected = mode == "register", onClick = { mode = "register"; error = null }, text = { Text(tr("Đăng ký", "Sign up")) })
            }
        }
        val full = Modifier.fillMaxWidth()
        when (mode) {
            "login" -> {
                OutlinedTextField(identity, { identity = it }, full, label = { Text(tr("Email hoặc tên đăng nhập", "Email or username")) }, singleLine = true)
                OutlinedTextField(password, { password = it }, full, label = { Text(tr("Mật khẩu", "Password")) }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation())
            }
            "register" -> {
                OutlinedTextField(username, { username = it }, full, label = { Text(tr("Tên đăng nhập (3-20 ký tự)", "Username (3-20 characters)")) }, singleLine = true)
                OutlinedTextField(email, { email = it }, full, label = { Text("Email") }, singleLine = true)
                OutlinedTextField(password, { password = it }, full, label = { Text(tr("Mật khẩu (≥ 8 ký tự)", "Password (8+ characters)")) }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation())
                OutlinedTextField(birth, { birth = it }, full, label = { Text(tr("Ngày sinh (YYYY-MM-DD)", "Date of birth (YYYY-MM-DD)")) }, singleLine = true)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(accept, { accept = it })
                    Text(tr("Tôi đồng ý Điều khoản dịch vụ và Chính sách bảo mật", "I agree to the Terms of Service and Privacy Policy"))
                }
            }
            else -> {
                Text(tr("Nhập mã xác minh gửi qua email.", "Enter the verification code sent to your email."))
                OutlinedTextField(code, { code = it }, full, label = { Text(tr("Mã xác minh", "Verification code")) }, singleLine = true)
            }
        }
        info?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(onClick = { submit() }, enabled = !busy, modifier = full) {
            if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            else Text(when (mode) { "login" -> tr("Đăng nhập", "Log in"); "register" -> tr("Tạo tài khoản", "Create account"); else -> tr("Xác minh", "Verify") })
        }
        if (mode == "login") {
            OutlinedButton(modifier = full, onClick = {
                val state = Session.beginWebLogin()
                val url = SITE_DIR + "api/v1/app_login.php?redirect=" + Uri.encode("devbuild://auth") +
                    "&device=" + Uri.encode(deviceName()) + "&state=" + state
                ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            }) { Text(tr("Đăng nhập bằng Google / GitHub / Discord (web)", "Continue with Google / GitHub / Discord (web)")) }
        }
        if (mode == "verify") {
            TextButton(onClick = {
                scope.launch {
                    try { Api.request("POST", "auth/resend", body = mapOf("email" to email.trim())); info = tr("Đã gửi lại mã.", "Code resent.") }
                    catch (e: Exception) { error = errText(e) }
                }
            }) { Text(tr("Gửi lại mã", "Resend code")) }
        }
    }
}
