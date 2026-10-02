package com.tingbili.app.ui.login

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.tingbili.app.BiliTingApplication
import com.tingbili.app.data.api.LoginApi
import com.tingbili.app.data.api.QrPollCode
import com.tingbili.app.data.local.CookieStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class LoginPhase { LOADING, WAITING, SCANNED, CONFIRMED, ERROR }

data class LoginUiState(
    val phase: LoginPhase = LoginPhase.LOADING,
    val qrBitmap: Bitmap? = null,
    val message: String = "",
    /** 登录成功后写进 CookieStore 的身份展示用 */
    val dedeUserId: String = ""
)

class LoginViewModel(
    private val api: LoginApi,
    private val cookieStore: CookieStore
) : ViewModel() {

    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state

    private var pollJob: Job? = null

    init { start() }

    /** 生成二维码并开始轮询；过期自动重新生成 */
    fun start() {
        pollJob?.cancel()
        _state.value = LoginUiState(phase = LoginPhase.LOADING)
        viewModelScope.launch {
            try {
                val qr = withContext(Dispatchers.IO) { api.generate() }
                _state.value = _state.value.copy(
                    phase = LoginPhase.WAITING,
                    qrBitmap = renderQr(qr.url)
                )
                pollLoop(qr.qrcode_key)
            } catch (t: Throwable) {
                _state.value = LoginUiState(phase = LoginPhase.ERROR, message = t.message ?: "网络异常")
            }
        }
    }

    private fun pollLoop(key: String) {
        pollJob = viewModelScope.launch {
            while (true) {
                delay(2000)
                try {
                    val (poll, cookieFromHeaders) = withContext(Dispatchers.IO) { api.pollOnce(key) }
                    when (poll.code) {
                        QrPollCode.SUCCESS -> {
                            // 新版端点把登录态放在 set-cookie 头里，data.url 可能为空；两种来源都试
                            val loginCookies = LoginApi.loginFieldsOnly(cookieFromHeaders)
                                .ifBlank { LoginApi.cookiesFromUrl(poll.url).orEmpty() }
                            if (loginCookies.isBlank()) {
                                _state.value = _state.value.copy(
                                    phase = LoginPhase.ERROR,
                                    message = "B站已确认登录，但响应里没带 cookie（头尾都没有）· 请把这条反馈给我"
                                )
                                return@launch
                            }
                            // 重要：扫码登录是第二种登录方式，用户可能已粘贴过完整 cookie。
                            // 先把扫码前的 cookie 完整备份（撤销扫码用），再合并写入——
                            // 合并只更新登录字段，其余字段原样保留。
                            val existing = cookieStore.cookieHeader()
                            cookieStore.backupBeforeScan(existing)
                            val merged = LoginApi.mergeCookies(existing, loginCookies)
                            val saved = cookieStore.save(cookieStore.buvid3(), merged)
                            if (saved.isFailure) {
                                _state.value = _state.value.copy(
                                    phase = LoginPhase.ERROR,
                                    message = "登录成功但 cookie 存不下：${saved.exceptionOrNull()?.message}"
                                )
                                return@launch
                            }
                            _state.value = _state.value.copy(
                                phase = LoginPhase.CONFIRMED,
                                message = if (existing.isBlank()) "登录成功，cookie 已生效"
                                else "登录成功，已有 cookie 的其他字段保留不动",
                                dedeUserId = poll.url.substringAfter("DedeUserID=", "").substringBefore('&')
                            )
                            // 光靠页面文字容易被当成"没反应"，再给一条全局提示
                            com.tingbili.app.util.ErrorBus.post("扫码登录成功 · 设置页已显示「已登录」")
                            return@launch
                        }
                        QrPollCode.SCANNED -> _state.value =
                            _state.value.copy(phase = LoginPhase.SCANNED, message = "已扫码，请在手机上确认")
                        QrPollCode.EXPIRED -> { start(); return@launch }
                        else -> if (_state.value.phase != LoginPhase.WAITING) {
                            _state.value = _state.value.copy(phase = LoginPhase.WAITING, message = "")
                        }
                    }
                } catch (t: Throwable) {
                    // 单次轮询失败（网络抖动）不终止登录，下一轮再试
                    _state.value = _state.value.copy(message = "网络波动，重试中…")
                }
            }
        }
    }

    /**
     * 内置登录页（WebView）登录成功后收 cookie：只取登录字段合并，用户手贴的其它字段不动，
     * 并且沿用扫码那套「先备份、可在设置页一键退出」。没有 SESSDATA 就当没成功，不改任何东西。
     */
    fun adoptWebLogin(rawCookie: String) {
        val loginPart = LoginApi.loginFieldsOnly(rawCookie)
        if (!loginPart.contains("SESSDATA", ignoreCase = true)) return
        viewModelScope.launch {
            val existing = cookieStore.cookieHeader()
            cookieStore.backupBeforeScan(existing)
            val saved = cookieStore.save(cookieStore.buvid3(), LoginApi.mergeCookies(existing, loginPart))
            if (saved.isFailure) {
                _state.value = LoginUiState(
                    phase = LoginPhase.ERROR,
                    message = "登录成功但 cookie 存不下：${saved.exceptionOrNull()?.message}"
                )
                return@launch
            }
            _state.value = LoginUiState(
                phase = LoginPhase.CONFIRMED,
                message = if (existing.isBlank()) "登录成功，cookie 已写入"
                else "登录成功，已有 cookie 的其它字段保留不动"
            )
            com.tingbili.app.util.ErrorBus.post("登录成功 · 设置页已显示「已登录」")
        }
    }

    private suspend fun renderQr(content: String): Bitmap = withContext(Dispatchers.Default) {
        val size = 560
        val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size)
        val pixels = IntArray(size * size)
        for (y in 0 until size) for (x in 0 until size) {
            pixels[y * size + x] = if (matrix[x, y]) 0xFF1A1A1A.toInt() else 0xFFFFFFFF.toInt()
        }
        Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
    }

    override fun onCleared() { pollJob?.cancel() }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as BiliTingApplication
                LoginViewModel(LoginApi(app.container.biliService), app.cookieStore)
            }
        }
    }
}
