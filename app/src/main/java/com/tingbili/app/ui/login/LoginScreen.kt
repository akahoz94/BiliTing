package com.tingbili.app.ui.login

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import android.content.Intent
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    vm: LoginViewModel = androidx.lifecycle.viewmodel.compose.viewModel(factory = LoginViewModel.Factory)
) {
    val state by vm.state.collectAsState()
    var showWeb by remember { mutableStateOf(false) }

    // 官方登录页（内置 WebView）：登录成功直接 cookie 罐里取回来，不用手粘。
    // 这是给"手机装了 B 站 App 但不想扫码"的人的第二条路，走的是 B 站自己的页面，我们不碰账号密码。
    if (showWeb) {
        BiliLoginWebView(
            onClose = { showWeb = false },
            onCookie = { raw -> vm.adoptWebLogin(raw); showWeb = false }
        )
        return
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("扫码登录 B 站") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(280.dp)
                    .background(Color.White, RoundedCornerShape(16.dp))
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                val bmp = state.qrBitmap
                if (bmp != null && state.phase != LoginPhase.CONFIRMED) {
                    Image(bitmap = bmp.asImageBitmap(), contentDescription = "登录二维码", modifier = Modifier.fillMaxSize())
                } else {
                    Text(
                        text = when {
                            state.phase == LoginPhase.CONFIRMED -> "已登录"
                            state.phase == LoginPhase.LOADING -> "生成二维码…"
                            else -> state.message.ifBlank { "加载中…" }
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            val hint = when (state.phase) {
                LoginPhase.WAITING -> "打开 B 站 App 扫一扫登录"
                LoginPhase.SCANNED -> "已扫码，请在手机上确认"
                LoginPhase.CONFIRMED -> "登录成功，cookie 已生效"
                LoginPhase.ERROR -> state.message
                LoginPhase.LOADING -> "生成二维码…"
            }
            Text(
                text = hint,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 20.dp)
            )
            if (state.phase != LoginPhase.CONFIRMED) {
                Text(
                    text = "登录后请求走真实账号身份，风控拦截会大幅减少",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            if (state.phase == LoginPhase.ERROR) {
                Button(onClick = { vm.start() }, modifier = Modifier.padding(top = 16.dp)) {
                    Text("重新生成二维码")
                }
            }
            if (state.phase != LoginPhase.CONFIRMED) {
                TextButton(onClick = { showWeb = true }, modifier = Modifier.padding(top = 12.dp)) {
                    Text("不方便扫码？打开 B 站官方登录页")
                }
            }
        }
    }
}

@Composable
private fun BiliLoginWebView(onClose: () -> Unit, onCookie: (String) -> Unit) {
    val ctx = LocalContext.current
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onClose) { Text("关闭") }
            Spacer(Modifier.weight(1f))
            // 内置页在个别机型上会被 B 站当旧浏览器，留一条外部浏览器的路（登录后要自己粘 cookie）
            TextButton(onClick = {
                runCatching {
                    ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(BILI_H5_LOGIN)))
                }
            }) { Text("在浏览器打开") }
        }
        Text(
            "在这里登录 B 站，成功后自动取回 cookie · 我们不接触你的账号密码，页面是 B 站自己的",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
        )
        AndroidView(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            factory = { c ->
                WebView(c).apply {
                    webViewClient = WebViewClient()
                    settings.javaScriptEnabled = true
                    CookieManager.getInstance().setAcceptCookie(true)
                    loadUrl(BILI_H5_LOGIN)
                }
            }
        )
    }
    // cookie 是登录成功后由 B 站异步下发的，不去猜页面跳到哪了，直接每秒看 cookie 罐
    LaunchedEffect(Unit) {
        while (true) {
            delay(1500)
            val jar = runCatching {
                CookieManager.getInstance().getCookie("https://www.bilibili.com")
            }.getOrNull()
            if (jar != null && "SESSDATA=" in jar) {
                onCookie(jar)
                break
            }
        }
    }
}

/** B 站 H5 登录页（实测 2026-10-01：手机 UA 下 200；passport.bilibili.com/h5-app/passport-login 已 404） */
private const val BILI_H5_LOGIN = "https://account.bilibili.com/h5/account-h5/login"
